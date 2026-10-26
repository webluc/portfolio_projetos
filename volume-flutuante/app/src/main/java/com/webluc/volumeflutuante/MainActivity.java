package com.webluc.volumeflutuante;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private TextView permissionStatus;
    private Button grantButton;
    private Button toggleButton;
    private TextView alphaLabel;
    private TextView sizeLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = Prefs.get(this);

        permissionStatus = findViewById(R.id.permissionStatus);
        grantButton = findViewById(R.id.grantButton);
        toggleButton = findViewById(R.id.toggleButton);
        alphaLabel = findViewById(R.id.alphaLabel);
        sizeLabel = findViewById(R.id.sizeLabel);

        grantButton.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));

        toggleButton.setOnClickListener(v -> {
            if (FloatingVolumeService.isRunning) {
                stopService(new Intent(this, FloatingVolumeService.class));
            } else if (Settings.canDrawOverlays(this)) {
                startForegroundService(new Intent(this, FloatingVolumeService.class));
            } else {
                Toast.makeText(this, R.string.permission_missing, Toast.LENGTH_LONG).show();
                return;
            }
            toggleButton.postDelayed(this::refresh, 300);
        });

        // Transparência: a barra vai de 0 a 90 e representa 10% a 100% de opacidade.
        SeekBar alphaSeek = findViewById(R.id.alphaSeek);
        alphaSeek.setProgress(Prefs.alpha(prefs) - Prefs.MIN_ALPHA);
        updateAlphaLabel(Prefs.alpha(prefs));
        alphaSeek.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int alpha = progress + Prefs.MIN_ALPHA;
                prefs.edit().putInt(Prefs.KEY_ALPHA, alpha).apply();
                updateAlphaLabel(alpha);
            }
        });

        SeekBar sizeSeek = findViewById(R.id.sizeSeek);
        sizeSeek.setProgress(Prefs.size(prefs) - Prefs.MIN_SIZE);
        updateSizeLabel(Prefs.size(prefs));
        sizeSeek.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int size = progress + Prefs.MIN_SIZE;
                prefs.edit().putInt(Prefs.KEY_SIZE, size).apply();
                updateSizeLabel(size);
            }
        });

        Spinner streamSpinner = findViewById(R.id.streamSpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, Prefs.STREAM_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        streamSpinner.setAdapter(adapter);
        streamSpinner.setSelection(Prefs.streamIndex(prefs));
        streamSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(Prefs.KEY_STREAM, position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        CheckBox systemUiCheck = findViewById(R.id.systemUiCheck);
        systemUiCheck.setChecked(Prefs.systemUi(prefs));
        systemUiCheck.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(Prefs.KEY_SYSTEM_UI, checked).apply());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean granted = Settings.canDrawOverlays(this);
        permissionStatus.setText(granted ? R.string.permission_granted : R.string.permission_missing);
        grantButton.setVisibility(granted ? View.GONE : View.VISIBLE);
        toggleButton.setText(FloatingVolumeService.isRunning ? R.string.stop : R.string.start);
    }

    private void updateAlphaLabel(int alpha) {
        alphaLabel.setText(getString(R.string.transparency) + ": " + (100 - alpha)
                + "% (opacidade " + alpha + "%)");
    }

    private void updateSizeLabel(int size) {
        sizeLabel.setText(getString(R.string.size) + ": " + size + " dp");
    }

    abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
