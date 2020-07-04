package com.example.pushdemo;

import android.Manifest;

import androidx.annotation.NonNull;
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
import android.widget.TextView;
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
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

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
    private static final String TEXT_HARD_ENCODER= "hard encoder";
    private static final String TEXT_SOFT_ENCODER= "soft encoder";

    private EditText editUrl;
    private Button btnLive;
    private Button btnScan;
    private Button btnPublish;
    private Button btnSwitchCamera;
    private Button btnEncoder;

    private SharedPreferences sp;

    private String rtmpUrl = "";

    private SrsPublisher mPublisher;
    private SrsCameraView mCameraView;

    private int mWidth = 1280;
    private int mHeight = 720;
    private boolean isPermissionGranted = false;

    private final Handler statHandler = new Handler();
    private TextView textNet, textFps;
    private double bpsAudio, bpsVideo, fpsVideo;
    private void initStat() {
        textNet = (TextView) findViewById(R.id.text_net);
        textFps = (TextView) findViewById(R.id.text_fps);

        textNet.setText("");
        textFps.setText("");

        bpsAudio = bpsVideo = fpsVideo = 0;
    }

    private final Runnable statUpdate = new Runnable() {
        @Override
        public void run() {
            int bps = (int) (bpsAudio + bpsVideo);
            int fps = (int) fpsVideo;
            String unit = "";
            if (bps >= 1024) {
                unit = "k";
                bps /= 1024;
            }
            String outNet = String.format(Locale.getDefault(),
                    "bps: %d%s", bps, unit);
            String outFps = String.format(Locale.getDefault(),
                    "fps: %d", fps);

            textNet.setText(outNet);
            textFps.setText(outFps);

            Log.i(TAG, String.format("%s, %s", outNet, outFps));

            statHandler.postDelayed(statUpdate, 3000);
        }
    };

    private void startStat() {
        bpsAudio = bpsVideo = fpsVideo = 0;

        statHandler.removeCallbacks(statUpdate);
        statHandler.postDelayed(statUpdate, 3000);
    }

    private void stopStat() {
        statHandler.removeCallbacks(statUpdate);
        statHandler.post(new Runnable() {
            @Override
            public void run() {
                textNet.setText("");
                textFps.setText("");
            }
        });
    }

    private void setupFullscreen() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
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

    private void startPublish() {
        rtmpUrl = editUrl.getText().toString();
        if (rtmpUrl.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Bad URL!", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = sp.edit();
        editor.putString(KEY_URL, rtmpUrl);
        editor.apply();

        promptEncoder();

        mPublisher.startPublish(rtmpUrl);
        mPublisher.startCamera();

        btnPublish.setText(TEXT_UNPUBLISH);
        closeScreenOrientation();

        btnEncoder.setEnabled(false);
        showLiveWarn();
        enableQrScan(false);
        delayedHide(AUTO_HIDE_DELAY_MILLIS);
        startStat();
    }

    private void stopPublish() {
        openScreenOrientation();

        mPublisher.stopPublish();
        mPublisher.startCamera();

        btnPublish.setText(TEXT_PUBLISH);

        btnEncoder.setEnabled(true);
        hideLive();
        enableQrScan(true);
        stopStat();
    }

    private void resetPublish() {

        stopPublish();

    }

    private void promptEncoder() {
        String encoder;
        switch(btnEncoder.getText().toString()) {
            case TEXT_SOFT_ENCODER:
                encoder = TEXT_HARD_ENCODER;
                break;

            case TEXT_HARD_ENCODER:
                encoder = TEXT_SOFT_ENCODER;
                break;

            default:
                encoder = "undefined encoder";
                break;
        }// switch
        Toast.makeText(getApplicationContext(), "Using " + encoder, Toast.LENGTH_SHORT).show();
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
        btnEncoder = (Button) findViewById(R.id.button_encoder);
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
        mPublisher.setVideoFpsLower();
        mPublisher.setScreenOrientation(getResources().getConfiguration().orientation);
        mPublisher.startCamera();

        btnPublish.setText(TEXT_PUBLISH);
        btnEncoder.setText(TEXT_SOFT_ENCODER);
        mPublisher.switchToHardEncoder();

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

                    startPublish();

                } else if (btnPublish.getText().toString().contentEquals(TEXT_UNPUBLISH)) {

                    stopPublish();

                }
            }
        });

        btnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPublisher.switchCameraFace((mPublisher.getCameraId() + 1) % Camera.getNumberOfCameras());
            }
        });

        btnEncoder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnEncoder.getText().toString().contentEquals(TEXT_SOFT_ENCODER)) {
                    mPublisher.switchToSoftEncoder();
                    btnEncoder.setText(TEXT_HARD_ENCODER);
                } else if (btnEncoder.getText().toString().contentEquals(TEXT_HARD_ENCODER)) {
                    mPublisher.switchToHardEncoder();
                    btnEncoder.setText(TEXT_SOFT_ENCODER);
                }
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
        Log.e(TAG, "handle exception", e);
        try {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();

            resetPublish();

        } catch (Exception e2) {
            Log.e(TAG, "handle exception 2", e2);
        }
    }


    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();

        if(mPublisher.getCamera() == null && isPermissionGranted){
            mPublisher.startCamera();
        }
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestory");
        super.onDestroy();

        resetPublish();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Log.i(TAG, "onConfigurationChanged orientation=" + newConfig.orientation);

        super.onConfigurationChanged(newConfig);
        mPublisher.stopEncode();
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

        setupFullscreen();
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

        initStat();
    }

    ////////////////////////////////////////
    // interface RtmpListener
    @Override
    public void onRtmpConnecting(String s) {
        Log.i(TAG, "on rtmp connecting");
    }

    // interface RtmpListener
    @Override
    public void onRtmpConnected(String s) {
        Log.i(TAG, "on rtmp connected " + s);

        showLiveOk();
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
        Log.i(TAG, "on rtmp stopped");
    }

    // interface RtmpListener
    @Override
    public void onRtmpDisconnected() {
        Log.i(TAG, "on rtmp disconnected");

        hideLive();
        stopStat();
    }

    // interface RtmpListener
    @Override
    public void onRtmpVideoFpsChanged(double v) {
        fpsVideo = v;

        Log.i(TAG, String.format("Output Fps: %f", v));
    }

    // interface RtmpListener
    @Override
    public void onRtmpVideoBitrateChanged(double v) {
        bpsVideo = v;

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
        bpsAudio = v;

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
        Log.e(TAG, "on network weak");

        showLiveError();
    }

    // interface SrsEncodeListener
    @Override
    public void onNetworkResume() {
        Log.e(TAG, "on network resume");

        showLiveOk();
    }

    // interface SrsEncodeListener
    @Override
    public void onEncodeIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

}
