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

    com.example.ubicompproj.GraphView graphView;
    TextView countdownTV;
    TextView resultTV;
    com.example.ubicompproj.BLEService service;
    boolean mBound = false;
    boolean isGameRunning = false;
    long score;
    int countdownDuration;
    long shakeTime;
    long gameStartTime = 0; // Stores the time when the "Go!" signal is displayed
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

        // Initialize Firebase Database reference
        mDatabase = FirebaseDatabase.getInstance("https://quickdrawwithmicrobit-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
        countdownTV = findViewById(R.id.countdownText);
        resultTV = findViewById(R.id.resultText);

        Button startButton = findViewById(R.id.startButton);
        Button leaderboardButton = findViewById(R.id.leaderboardButton);

        // Request permissions if not granted
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

        // Start Game button
        startButton.setOnClickListener(v -> startGame());

        // Navigate to Leaderboard button
        leaderboardButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LeaderboardActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Binds to Bluetooth when activity starts
        Intent intent = new Intent(this, com.example.ubicompproj.BLEService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void startGame() {
        // Get the root layout (background view)
        View backgroundView = findViewById(R.id.main_background);
        Button startButton = findViewById(R.id.startButton);
        Button leaderboardButton = findViewById(R.id.leaderboardButton);

        if (!isGameRunning) {
            isGameRunning = true;
            resultTV.setText("");
            gameStartTime = 0;

            // Hide countdown text
            countdownTV.setVisibility(View.INVISIBLE);

            // Hide the Start Game and Leaderboard buttons when the game starts
            startButton.setVisibility(View.INVISIBLE);
            leaderboardButton.setVisibility(View.INVISIBLE);

            // Change background color to red when the game starts
            backgroundView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light));

            // Play background music when the game starts
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(this, R.raw.cowboy_standoff); // Replace with your file name
                mediaPlayer.setLooping(true); // Loop the music
                mediaPlayer.start();
            }

            // Generate a random countdown between 2 and 5 seconds
            Random random = new Random();
            countdownDuration = 2000 + random.nextInt(3000);

            new CountDownTimer(countdownDuration, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    countdownTV.setText("Starting in: " + (millisUntilFinished / 1000));
                }

                @Override
                public void onFinish() {
                    // Stop the current music
                    if (mediaPlayer != null) {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                        mediaPlayer = null;
                    }

                    // Play a different music after the countdown finishes
                    mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.its_high_noon); // Replace with the new file name
                    mediaPlayer.start();

                    // Change background color to green when countdown ends
                    backgroundView.setBackgroundColor(ContextCompat.getColor(MainActivity.this, android.R.color.holo_green_light));
                    countdownTV.setText("Go!");
                    gameStartTime = System.currentTimeMillis();

                    // Show the Start Game and Leaderboard buttons again after the game starts
                    startButton.setVisibility(View.VISIBLE);
                    leaderboardButton.setVisibility(View.VISIBLE);
                }
            }.start();
        }
    }


    @Override
    public void dataReceived(float xG, float yG, float zG, float pitch, float roll) {
        if (yG > 2000) {
            // Play a sound when the condition is met
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.revolver); // Replace with your sound file
            }
            mediaPlayer.start(); // Play the sound

            if (isGameRunning && gameStartTime == 0) {
                isGameRunning = false;
                resultTV.setText("Too Early!!! Try again....");
            } else if (isGameRunning) {
                isGameRunning = false;
                shakeTime = System.currentTimeMillis();
                score = shakeTime - gameStartTime;

                resultTV.setText("Win! Your score: " + score + " ms");
                showUsername(score);
            }
        }
        graphView.updateGraph(new float[]{xG, yG, zG}, false, true, false, false);
    }


    private void showUsername(long score) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Enter Your Username");

            // Input field
            final EditText input = new EditText(MainActivity.this);
            input.setHint("Username");
            builder.setView(input);

            // Dialog buttons
            builder.setPositiveButton("OK", (dialog, which) -> {
                String username = input.getText().toString().trim();
                saveToFirebase(username, score);
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();
        });
    }

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

    // Bluetooth connection
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

    // Release MediaPlayer resources when the activity is destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
