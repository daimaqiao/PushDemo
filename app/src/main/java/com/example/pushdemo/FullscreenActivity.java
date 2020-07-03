package com.example.pushdemo;

import android.Manifest;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.github.faucamp.simplertmp.RtmpHandler;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import net.ossrs.yasea.SrsCameraView;
import net.ossrs.yasea.SrsEncodeHandler;
import net.ossrs.yasea.SrsPublisher;
import net.ossrs.yasea.SrsRecordHandler;

import java.io.IOException;
import java.net.SocketException;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity implements RtmpHandler.RtmpListener,
        SrsRecordHandler.SrsRecordListener, SrsEncodeHandler.SrsEncodeListener {

    private static final String TAG = "PushDemo";
    public final static int RC_CAMERA = 100;

    private static final String APP_NAME = "PushDemo";
    private static final String KEY_URL = "rtmpUrl";
    private static final String TEXT_PUBLISH = "publish";
    private static final String TEXT_UNPUBLISH = "stop";

    private EditText editUrl;
    private Button btnLive;
    private Button btnScan;
    private Button btnPublish;
    private Button btnSwitchCamera;

    private SharedPreferences sp;

    private String rtmpUrl = "";

    private SrsPublisher mPublisher;
    private SrsCameraView mCameraView;

    private int mWidth = 1280;
    private int mHeight = 720;
    private boolean isPermissionGranted = false;

    private void hideActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    private void keepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void openScreenOrientation() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }

    private void closeScreenOrientation() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= 23 && (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, RC_CAMERA);
        }else{
            isPermissionGranted = true;
            init();
        }
    }

    private boolean isLive() {
        if(btnPublish != null)
            return btnPublish.getText().toString().contentEquals(TEXT_UNPUBLISH);
        return false;
    }

    private void showLiveOk() {
        if(btnLive != null && isLive()) {
            btnLive.setVisibility(View.VISIBLE);
            btnLive.setTextColor(getResources().getColor(R.color.limegreen));
        }
    }

    private void showLiveWarn() {
        if(btnLive != null && isLive()) {
            btnLive.setVisibility(View.VISIBLE);
            btnLive.setTextColor(getResources().getColor(R.color.darkorange));
        }
    }

    private void showLiveError() {
        if(btnLive != null && isLive()) {
            btnLive.setVisibility(View.VISIBLE);
            btnLive.setTextColor(getResources().getColor(R.color.firebrick));
        }
    }

    private void hideLive() {
        if(btnLive != null)
            btnLive.setVisibility(View.GONE);
    }

    private void enableQrScan(boolean enabled) {
        if(btnScan != null)
            btnScan.setEnabled(enabled);

        if(editUrl != null)
            editUrl.setEnabled(enabled);
    }

    private void updateRtmpUrl(String url) {
        if(url == null || url.isEmpty())
            return;

        if(url.toLowerCase().startsWith("rtmp://") && editUrl != null)
            editUrl.setText(url);
        else
            Toast.makeText(this, "Bad URL - " + url, Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                isPermissionGranted = true;
                init();
            } else {
                finish();
            }
        }
    }

    private void init() {
        sp = getSharedPreferences(APP_NAME, MODE_PRIVATE);
        rtmpUrl = sp.getString(KEY_URL, rtmpUrl);

        editUrl = (EditText) findViewById(R.id.edit_rtmp_url);
        editUrl.setText(rtmpUrl);

        btnLive = (Button) findViewById(R.id.button_live);
        btnPublish = (Button) findViewById(R.id.button_publish);
        btnSwitchCamera = (Button) findViewById(R.id.button_switch);
        mCameraView = (SrsCameraView) findViewById(R.id.camera_view);

        hideLive();

        mPublisher = new SrsPublisher(mCameraView);
        mPublisher.setEncodeHandler(new SrsEncodeHandler(this));
        mPublisher.setRtmpHandler(new RtmpHandler(this));
        mPublisher.setRecordHandler(new SrsRecordHandler(this));
        mPublisher.switchToHardEncoder();
        mPublisher.setPreviewResolution(mWidth, mHeight);
        mPublisher.setOutputResolution(mHeight, mWidth);
        mPublisher.setVideoHDMode();
        mPublisher.startCamera();

        mCameraView.setCameraCallbacksHandler(new SrsCameraView.CameraCallbacksHandler() {
            @Override
            public void onCameraParameters(Camera.Parameters params) {
                // TODO setup camera
            }
        });

        btnPublish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnPublish.getText().toString().contentEquals(TEXT_PUBLISH)) {
                    rtmpUrl = editUrl.getText().toString();
                    if (rtmpUrl.isEmpty()) {
                        Toast.makeText(getApplicationContext(), "Bad URL!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    SharedPreferences.Editor editor = sp.edit();
                    editor.putString(KEY_URL, rtmpUrl);
                    editor.apply();

                    mPublisher.switchToHardEncoder();

                    mPublisher.startPublish(rtmpUrl);
                    mPublisher.startCamera();

                    btnPublish.setText(TEXT_UNPUBLISH);
                    closeScreenOrientation();

                    showLiveWarn();
                    enableQrScan(false);
                    delayedHide(AUTO_HIDE_DELAY_MILLIS);

                } else if (btnPublish.getText().toString().contentEquals(TEXT_UNPUBLISH)) {

                    openScreenOrientation();

                    mPublisher.stopPublish();
                    mPublisher.stopRecord();
                    mPublisher.startCamera();

                    btnPublish.setText(TEXT_PUBLISH);

                    hideLive();
                    enableQrScan(true);
                }
            }
        });

        btnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPublisher.switchCameraFace((mPublisher.getCameraId() + 1) % Camera.getNumberOfCameras());
            }
        });

        initQrScan();
    }


    private void initQrScan() {
        btnScan = (Button) findViewById(R.id.button_scan);

        final Activity that = this;
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IntentIntegrator integrator = new IntentIntegrator(that);
                integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
                integrator.setPrompt("从二维码中读取RTMP URL");
                integrator.setCameraId(0);
                integrator.setBeepEnabled(true);
                integrator.initiateScan();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null)
            updateRtmpUrl(result.getContents());

        super.onActivityResult(requestCode, resultCode, data);
    }


    private void handleException(Exception e) {
        try {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            mPublisher.stopPublish();
            mPublisher.stopRecord();
            btnPublish.setText(TEXT_PUBLISH);
            hideLive();
            enableQrScan(true);
            mPublisher.startCamera();
        } catch (Exception e1) {
            //
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        if(mPublisher.getCamera() == null && isPermissionGranted){
            //if the camera was busy and available again
            mPublisher.startCamera();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        final Button btn = (Button) findViewById(R.id.button_publish);
        btn.setEnabled(true);
        mPublisher.resumeRecord();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPublisher.pauseRecord();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPublisher.stopPublish();
        mPublisher.stopRecord();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mPublisher.stopEncode();
        mPublisher.stopRecord();
        mPublisher.setScreenOrientation(newConfig.orientation);
        if (btnPublish.getText().toString().contentEquals(TEXT_UNPUBLISH)) {
            mPublisher.startEncode();
        }
        mPublisher.startCamera();
    }


    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;

    private final Runnable mHidePart2Runnable = new Runnable() {
        @Override
        public void run() {
            mControlsView.setVisibility(View.GONE);
        }
    };

    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            mControlsView.setVisibility(View.VISIBLE);
        }
    };

    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        mVisible = false;

        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private void show() {
        mVisible = true;

        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hideActionBar();
        keepScreenOn();

        setContentView(R.layout.activity_fullscreen);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle();
            }
        });

        openScreenOrientation();
        requestPermission();
    }

    ////////////////////////////////////////
    // interface RtmpListener
    @Override
    public void onRtmpConnecting(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

    // interface RtmpListener
    @Override
    public void onRtmpConnected(String s) {
        showLiveOk();
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

    // interface RtmpListener
    @Override
    public void onRtmpVideoStreaming() {
    }

    // interface RtmpListener
    @Override
    public void onRtmpAudioStreaming() {
    }

    // interface RtmpListener
    @Override
    public void onRtmpStopped() {
        Toast.makeText(getApplicationContext(), "Stopped", Toast.LENGTH_SHORT).show();
    }

    // interface RtmpListener
    @Override
    public void onRtmpDisconnected() {
        hideLive();
        Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_SHORT).show();
    }

    // interface RtmpListener
    @Override
    public void onRtmpVideoFpsChanged(double v) {
        Log.i(TAG, String.format("Output Fps: %f", v));
    }

    // interface RtmpListener
    @Override
    public void onRtmpVideoBitrateChanged(double v) {
        int rate = (int) v;
        if (rate / 1000 > 0) {
            Log.i(TAG, String.format("Video bitrate: %f kbps", v / 1000));
        } else {
            Log.i(TAG, String.format("Video bitrate: %d bps", rate));
        }
    }

    // interface RtmpListener
    @Override
    public void onRtmpAudioBitrateChanged(double v) {
        int rate = (int) v;
        if (rate / 1000 > 0) {
            Log.i(TAG, String.format("Audio bitrate: %f kbps", v / 1000));
        } else {
            Log.i(TAG, String.format("Audio bitrate: %d bps", rate));
        }
    }

    // interface RtmpListener
    @Override
    public void onRtmpSocketException(SocketException e) {
        handleException(e);
    }

    // interface RtmpListener
    @Override
    public void onRtmpIOException(IOException e) {
        handleException(e);
    }

    // interface RtmpListener
    @Override
    public void onRtmpIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

    // interface RtmpListener
    @Override
    public void onRtmpIllegalStateException(IllegalStateException e) {
        handleException(e);
    }


    ////////////////////////////////////////
    // interface SrsRecordHandler
    @Override
    public void onRecordPause() {
        Toast.makeText(getApplicationContext(), "Record paused", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordResume() {
        Toast.makeText(getApplicationContext(), "Record resumed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordStarted(String msg) {
        Toast.makeText(getApplicationContext(), "Recording file: " + msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordFinished(String msg) {
        Toast.makeText(getApplicationContext(), "MP4 file saved: " + msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordIOException(IOException e) {
        handleException(e);
    }

    @Override
    public void onRecordIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }


    ////////////////////////////////////////
    // interface SrsEncodeListener
    @Override
    public void onNetworkWeak() {
        showLiveWarn();
        Toast.makeText(getApplicationContext(), "Network weak", Toast.LENGTH_SHORT).show();
    }

    // interface SrsEncodeListener
    @Override
    public void onNetworkResume() {
        showLiveOk();
        Toast.makeText(getApplicationContext(), "Network resume", Toast.LENGTH_SHORT).show();
    }

    // interface SrsEncodeListener
    @Override
    public void onEncodeIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

}
