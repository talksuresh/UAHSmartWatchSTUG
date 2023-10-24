package com.example.stug;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends WearableActivity {

    final String TAG = "MainActivity";
    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.text);

        // Enables Always-on
        setAmbientEnabled();
        checkPermission();
    }

    private void checkPermission() {

        // Check Runtime Permission for BODY_SENSORS
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, 1);
        } else {
            Log.d(TAG, "BODY SENSOR PERMISSIONS ALREADY GRANTED");
        }
        // Check Runtime Permission for EXTERNAL_STORAGE
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else {
            Log.d(TAG, "WRITE STORAGE PERMISSIONS ALREADY GRANTED");
        }
    }

    public void startRecord(View view) {
        Intent intent = new Intent(this, DisplayMessageActivity.class);
        startActivity(intent);
        finish();
    }
}
