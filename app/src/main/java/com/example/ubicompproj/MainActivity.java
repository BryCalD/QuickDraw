package com.example.ubicompproj;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Random;

public class MainActivity extends AppCompatActivity implements com.example.ubicompproj.BLEListener {

    TextView countdownTV;
    TextView resultTV;
    com.example.ubicompproj.BLEService service;
    boolean mBound = false;
    boolean isGameRunning = false;
    long score;
    int countdownDuration;
    long shakeTime;
    long gameStartTime = 0;
    private DatabaseReference mDatabase;

    // MediaPlayer for background music
    private MediaPlayer mediaPlayer;

    //-----permissions------
    int PERMISSION_ALL = 1;
    String[] PERMISSIONS = {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
    };
    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //firebase link!
        mDatabase = FirebaseDatabase.getInstance("https://micro-quickdraw-default-rtdb.europe-west1.firebasedatabase.app").getReference();
        countdownTV = findViewById(R.id.countdownText);
        resultTV = findViewById(R.id.resultText);

        Button startButton = findViewById(R.id.startButton);
        Button leaderboardButton = findViewById(R.id.leaderboardButton);

        //if permissions arent set up, request
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

        //start game button
        startButton.setOnClickListener(v -> startGame());
        //leaderboard button
        leaderboardButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LeaderboardActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        //binds bluetooth when activity starts
        Intent intent = new Intent(this, com.example.ubicompproj.BLEService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        //background music
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.cowboy_standoff);
            mediaPlayer.start();
        }
    }

    private void startGame() {
        View backgroundView = findViewById(R.id.main_background);
        Button startButton = findViewById(R.id.startButton);
        Button leaderboardButton = findViewById(R.id.leaderboardButton);

        if (!isGameRunning) {       //sets the game to running if its not
            isGameRunning = true;   //already so no multiple instance play
            resultTV.setText("");   //clear previous score when new game is made
            gameStartTime = 0;      //reset the game start time

            //hides elements when game starts
            countdownTV.setVisibility(View.INVISIBLE);
            startButton.setVisibility(View.INVISIBLE);
            leaderboardButton.setVisibility(View.INVISIBLE);

            //change to red when game starts
            backgroundView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light));

            //make random countdown between 2 and 5 seconds
            Random random = new Random();
            countdownDuration = 2000 + random.nextInt(3000);

            new CountDownTimer(countdownDuration, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                }

                @Override
                public void onFinish() {
                    //change to green when countdown finishes and play its high noon! when countdown finishes
                    countdownTV.setText("Go!");
                    gameStartTime = System.currentTimeMillis();
                    backgroundView.setBackgroundColor(ContextCompat.getColor(MainActivity.this, android.R.color.holo_green_light));
                    mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.its_high_noon);
                    mediaPlayer.start();
                    //show the Start Game and Leaderboard buttons again after the game starts
                    startButton.setVisibility(View.VISIBLE);
                    leaderboardButton.setVisibility(View.VISIBLE);
                }
            }.start();
        }
    }
    //data is recieved from the microbit and processed into the mechanics here
    @Override
    public void dataReceived(float xG, float yG, float zG, float pitch, float roll) {
        //if it detects sufficient y (up and down) movement it does the following:
        if (yG > 2000) {
            //on a thread  to make the first instance of the program work
            runOnUiThread(() -> {
                                                                    //1. If before the game starts score
                if (isGameRunning && gameStartTime == 0) {          //   is invalid and forces restart
                    isGameRunning = false;                          //2. If game is started, counts the
                    resultTV.setText("Too Early!!! Try again...."); //   score and gives result
                    mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.revolver);
                    mediaPlayer.start();
                } else if (isGameRunning) {
                    isGameRunning = false;
                    //point logic
                    shakeTime = System.currentTimeMillis();
                    score = shakeTime - gameStartTime;

                    // Play a sound when the condition is met and show points
                    mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.revolver);
                    mediaPlayer.start();
                    resultTV.setText("Ya wrangled up " + score + " points!");
                    showUsername(score);
                }
            });
        }
    }

    //popup to enter username
    private void showUsername(long score) {
        //run on main thread
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Your Score: " + score + " Enter Your Username");

            //input field
            final EditText input = new EditText(MainActivity.this);
            input.setHint("Username");
            builder.setView(input);

            //buttons
            builder.setPositiveButton("OK", (dialog, which) -> {
                String username = input.getText().toString().trim();
                saveToFirebase(username, score);
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();
        });

    }
    // ------------firebase saving method-------------
    private void saveToFirebase(String username, long score) {
        String userId = mDatabase.push().getKey();
        if (userId != null) {
            mDatabase.child("users").child(userId).setValue(new User(username, score))
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(MainActivity.this, "Score saved successfully!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to save score. Try again.", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    // ------------Bluetooth-----------------
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder iBinder) {
            com.example.ubicompproj.BLEService.BLEBinder binder = (com.example.ubicompproj.BLEService.BLEBinder) iBinder;
            service = binder.getService();
            service.startScan();
            service.addBLEListener(MainActivity.this);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
}
