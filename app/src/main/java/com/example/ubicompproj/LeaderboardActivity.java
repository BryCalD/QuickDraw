package com.example.ubicompproj;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LeaderboardActivity extends AppCompatActivity {

    private ListView leaderboardListView;
    private DatabaseReference mDatabase;
    private List<String> leaderboardData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboards);

        leaderboardListView = findViewById(R.id.leaderboardListView);

        // Initialize Firebase Database reference
        mDatabase = FirebaseDatabase.getInstance("https://micro-quickdraw-default-rtdb.europe-west1.firebasedatabase.app").getReference("users");

        // Initialize data list
        leaderboardData = new ArrayList<>();

        // Load leaderboard data
        loadLeaderboardData();
    }

    private void loadLeaderboardData() {
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                leaderboardData.clear(); // Clear existing data
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String username = userSnapshot.child("username").getValue(String.class);
                    Long score = userSnapshot.child("score").getValue(Long.class);

                    if (username != null && score != null) {
                        leaderboardData.add(username + ": " + score + " ms");
                    }
                }

                // Sort data by score (ascending order, smallest time is best)
                Collections.sort(leaderboardData, new Comparator<String>() {
                    @Override
                    public int compare(String o1, String o2) {
                        int score1 = Integer.parseInt(o1.split(": ")[1].split(" ")[0]);
                        int score2 = Integer.parseInt(o2.split(": ")[1].split(" ")[0]);
                        return Integer.compare(score1, score2);
                    }
                });

                // Update ListView with sorted data
                ArrayAdapter<String> adapter = new ArrayAdapter<>(LeaderboardActivity.this, android.R.layout.simple_list_item_1, leaderboardData);
                leaderboardListView.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(LeaderboardActivity.this, "Failed to load leaderboard data.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
