package com.example.ubicompproj;

public interface BLEListener {
    void dataReceived(float xG, float yG, float zG, float pitch, float roll);
}
