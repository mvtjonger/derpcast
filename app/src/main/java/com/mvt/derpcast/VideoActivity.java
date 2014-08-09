package com.mvt.derpcast;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.MediaController;
import android.widget.VideoView;

public class VideoActivity extends Activity {
    private static final int AUTO_HIDE_DELAY_MILLIS = 2000;
    private View _decorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video);

        Intent intent = getIntent();
        String videoUrl = intent.getStringExtra("videoUrl");

        final MediaController mediaController = new MediaController(this);
        final VideoView videoView = (VideoView)findViewById(R.id.video_view);
        videoView.setVideoURI(Uri.parse(videoUrl));
        videoView.start();
        videoView.setMediaController(mediaController);
        videoView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    VideoActivity.this.finish();
                    return true;
                }
                return false;
            }
        });
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                finish();
            }
        });

        _decorView = getWindow().getDecorView();
        _decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                setImmersiveMode();
            }
        });

        setImmersiveMode();
    }

    @Override
    protected void onResume() {
        super.onResume();

        VideoView videoView = (VideoView)findViewById(R.id.video_view);
        videoView.start();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setImmersiveMode();
    }

    protected void setImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            _decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}