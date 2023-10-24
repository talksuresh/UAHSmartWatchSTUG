package com.example.stug;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class Result extends WearableActivity {

    private TextView stepTextView;
    private TextView timeTextView;
    private TextView symTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        timeTextView = (TextView) findViewById(R.id.textView1);
        stepTextView = (TextView) findViewById(R.id.textView3);
        symTextView = (TextView) findViewById(R.id.textView5);

        // Enables Always-on
        setAmbientEnabled();

        Intent mIntent = getIntent();
        int steps = mIntent.getExtras().getInt("steps");
        float time = mIntent.getExtras().getFloat("time");
        float ratioTSLTSR = mIntent.getExtras().getFloat("ratioTSLTSR");

        String tt = String.format("%.2f", time);
        String ratioLR = String.format("%.3f", ratioTSLTSR);

        Log.e("UAH", "time " + tt + " steps " + steps + " TSL/TSR " + ratioTSLTSR);
        timeTextView.setText(tt);
        stepTextView.setText("" + steps);
        symTextView.setText(ratioLR);
    }

    public void gohome(View view) {
        finish();
    }
}
