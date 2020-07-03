package com.example.pushdemo;

import android.Manifest;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.github.faucamp.simplertmp.RtmpHandler;

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

    private Button btnLive;
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

        final EditText editUrl = (EditText) findViewById(R.id.edit_rtmp_url);
        editUrl.setText(rtmpUrl);

        btnLive = (Button) findViewById(R.id.button_live);
        btnPublish = (Button) findViewById(R.id.button_publish);
        btnSwitchCamera = (Button) findViewById(R.id.button_switch);
        mCameraView = (SrsCameraView) findViewById(R.id.camera_view);

        btnLive.setVisibility(View.GONE);

        mPublisher = new SrsPublisher(mCameraView);
        mPublisher.setEncodeHandler(new SrsEncodeHandler(this));
        mPublisher.setRtmpHandler(new RtmpHandler(this));
        mPublisher.setRecordHandler(new SrsRecordHandler(this));
        mPublisher.switchToHardEncoder();
        mPublisher.setPreviewResolution(mWidth, mHeight);
        mPublisher.setOutputResolution(mHeight, mWidth);
        mPublisher.setVideoHDMode();
        mPublisher.startCamera();

        mCameraView.setCameraCallbacksHandler(new SrsCameraView.CameraCallbacksHandler(){
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
                    if(rtmpUrl.isEmpty()) {
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
                    editUrl.setEnabled(false);
                    btnLive.setVisibility(View.VISIBLE);

                } else if (btnPublish.getText().toString().contentEquals(TEXT_UNPUBLISH)) {

                    openScreenOrientation();

                    mPublisher.stopPublish();
                    mPublisher.stopRecord();
                    mPublisher.startCamera();

                    btnPublish.setText(TEXT_PUBLISH);
                    editUrl.setEnabled(true);
                    btnLive.setVisibility(View.GONE);
                }
            }
        });

        btnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPublisher.switchCameraFace((mPublisher.getCameraId() + 1) % Camera.getNumberOfCameras());
            }
        });

    }


    private void handleException(Exception e) {
        try {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            mPublisher.stopPublish();
            mPublisher.stopRecord();
            btnPublish.setText(TEXT_PUBLISH);
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



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hideActionBar();
        keepScreenOn();

        setContentView(R.layout.activity_fullscreen);

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
        Toast.makeText(getApplicationContext(), "Network weak", Toast.LENGTH_SHORT).show();
    }

    // interface SrsEncodeListener
    @Override
    public void onNetworkResume() {
        Toast.makeText(getApplicationContext(), "Network resume", Toast.LENGTH_SHORT).show();
    }

    // interface SrsEncodeListener
    @Override
    public void onEncodeIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

}
