package com.mvt.derpcast.castservice;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.RemoteViews;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.capability.VolumeControl;
import com.connectsdk.service.command.ServiceCommandError;
import com.mvt.derpcast.R;
import com.mvt.derpcast.activities.MainActivity;
import com.mvt.derpcast.device.DeviceAdapter;
import com.mvt.derpcast.media.MediaAdapter;
import com.mvt.derpcast.media.MediaInfo;

public class CastService extends IntentService {

    public static final String ACTION_START = "com.mvt.derpcast.action.START";
    public static final int PLAY_NOTIFICATION = 1;

    private static final String MEDIA_LOGO_URL = "https://googledrive.com/host/0BzRo13oMy82cbEJRSHM3VEVyUWc/app_logo.png";
    private static final String MEDIA_VIDEO_ART_URL = "https://googledrive.com/host/0BzRo13oMy82cbEJRSHM3VEVyUWc/video_art.png";

    private CastServiceBinder _castServiceBinder;
    private RemoteControlClient _remoteControlClient;
    private BroadcastReceiver _broadcastReceiver;
    private WifiManager.WifiLock _wifiLock;
    private ConnectableDevice _device;
    private MediaInfo _mediaInfo;
    private MediaPlayer.MediaLaunchObject _mediaLaunchObject;
    private MediaPlayer.LaunchListener _launchListener;

    private DeviceAdapter _deviceAdapter;
    private MediaAdapter _videoAdapter;
    private MediaAdapter _audioAdapter;

    private boolean _serviceStarted;
    private String _title;

    public CastService() {
        super("CastService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (!_serviceStarted) {
            _serviceStarted = true;
            Log.i("CastService", "onStartCommand");

            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            _wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "DerpCastWifiLock");

            _deviceAdapter = new DeviceAdapter(CastService.this);
            _videoAdapter = new MediaAdapter();
            _audioAdapter = new MediaAdapter();

            _castServiceBinder = new CastServiceBinder(CastService.this);
            _broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    KeyEvent keyEvent = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
                    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                        switch (keyEvent.getKeyCode()) {
                            case KeyEvent.KEYCODE_MEDIA_PLAY:
                                play();
                                break;
                            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                                pause();
                                break;
                            case KeyEvent.KEYCODE_MEDIA_STOP:
                                stop();
                                break;
                            case KeyEvent.KEYCODE_VOLUME_UP:
                            case KeyEvent.KEYCODE_VOLUME_DOWN:
                                changeVolume(keyEvent.getKeyCode());
                                break;
                        }
                    }
                }
            };
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return _castServiceBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (_mediaInfo == null) {
            // Stop service when no clients are bound and no media is playing
            stopSelf();
        }

        return super.onUnbind(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
    }

    @Override
    public void onDestroy() {
        stop();
        DiscoveryManager.destroy();
        super.onDestroy();
    }

    public void setLaunchListener(MediaPlayer.LaunchListener launchListener) {
        _launchListener = launchListener;
    }

    public void play(ConnectableDevice device, MediaInfo mediaInfo, String title) {

        if (device == null ||
                device.getMediaControl() == null ||
                device.getMediaPlayer() == null ||
                mediaInfo == null) {
            return;
        }

        _mediaInfo = mediaInfo;
        _device = device;
        _title = title;

        String imageUrl = mediaInfo.format.startsWith("video/") ? MEDIA_VIDEO_ART_URL : MEDIA_LOGO_URL;
        MediaPlayer mediaPlayer = device.getMediaPlayer();
        mediaPlayer.playMedia(mediaInfo.url, mediaInfo.format, title, mediaInfo.title, imageUrl, false, new MediaPlayer.LaunchListener() {
            @Override
            public void onSuccess(MediaPlayer.MediaLaunchObject mediaLaunchObject) {
                _mediaLaunchObject = mediaLaunchObject;
                if (_launchListener != null) {
                    _launchListener.onSuccess(_mediaLaunchObject);
                }
            }

            @Override
            public void onError(ServiceCommandError error) {
                _mediaLaunchObject = null;
                if (_launchListener != null) {
                    _launchListener.onError(error);
                }
            }
        });

        LocalBroadcastManager
                .getInstance(CastService.this)
                .registerReceiver(_broadcastReceiver, new IntentFilter(Intent.ACTION_MEDIA_BUTTON));

        Context context = getApplicationContext();
        setLockScreenControls(context, title);
        Notification notification = getNotification(context, title);
        startForeground(PLAY_NOTIFICATION, notification);
    }

    public ConnectableDevice getPlayingDevice() {
        return _device;
    }

    public MediaInfo getPlayingMediaInfo() {
        return _mediaInfo;
    }

    public DeviceAdapter getDeviceAdapter() {
        return _deviceAdapter;
    }

    public MediaAdapter getVideoAdapter() {
        return _videoAdapter;
    }

    public MediaAdapter getAudioAdapter() {
        return _audioAdapter;
    }

    public String getTitle() {
        return _title;
    }

    public void play() {
        if (_remoteControlClient != null) {
            _remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
        }

        if (_device != null && _device.getMediaControl() != null) {
            _device.getMediaControl().play(null);
        }
    }

    public void pause() {
        if (_remoteControlClient != null) {
            _remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
        }

        if (_device != null && _device.getMediaControl() != null) {
            _device.getMediaControl().pause(null);
        }
    }

    public void stop() {
        _mediaInfo = null;
        _mediaLaunchObject = null;
        _title = null;

        if (_device != null && _device.getMediaControl() != null) {
            _device.getMediaControl().stop(null);
            _device = null;
        }

        if (_wifiLock.isHeld()) {
            _wifiLock.release();
        }

        LocalBroadcastManager
                .getInstance(CastService.this)
                .unregisterReceiver(_broadcastReceiver);
        removeLockScreenControls(getApplicationContext());
        stopForeground(true);
    }

    public void changeVolume(int keyCode) {
        if (_device != null && _device.hasCapability(VolumeControl.Volume_Up_Down)) {
            VolumeControl volumeControl = _device.getVolumeControl();
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                volumeControl.volumeUp(null);
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                volumeControl.volumeDown(null);
            }
        }
    }

    private void setLockScreenControls(Context context, String title) {
        ComponentName eventReceiver = new ComponentName(context, MediaButtonEventReceiver.class);

        if (_remoteControlClient == null) {
            Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            mediaButtonIntent.setComponent(eventReceiver);
            PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(context, 0, mediaButtonIntent, 0);

            _remoteControlClient = new RemoteControlClient(mediaPendingIntent);
            _remoteControlClient.setTransportControlFlags(
                    RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE);
        }

        _remoteControlClient
                .editMetadata(false)
                .putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title)
                .apply();

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        audioManager.registerMediaButtonEventReceiver(eventReceiver);
        audioManager.registerRemoteControlClient(_remoteControlClient);

        _remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
    }

    private void removeLockScreenControls(Context context) {
        if (_remoteControlClient != null) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioManager.unregisterRemoteControlClient(_remoteControlClient);
            audioManager.abandonAudioFocus(null);
        }
    }

    private Notification getNotification(Context context, String title) {
        Intent mainActivityIntent = new Intent(context, MainActivity.class);
        mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        ComponentName eventReceiver = new ComponentName(context, MediaButtonEventReceiver.class);
        Intent playIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        playIntent.setComponent(eventReceiver);
        playIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
        PendingIntent playPendingIntent = PendingIntent.getBroadcast(context, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent pauseIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        pauseIntent.setComponent(eventReceiver);
        pauseIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        PendingIntent pausePendingIntent = PendingIntent.getBroadcast(context, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        stopIntent.setComponent(eventReceiver);
        stopIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP));
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(context, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification);
        contentView.setTextViewText(R.id.title_text_view, title);
        contentView.setOnClickPendingIntent(R.id.play_button, playPendingIntent);
        contentView.setOnClickPendingIntent(R.id.pause_button, pausePendingIntent);
        contentView.setOnClickPendingIntent(R.id.stop_button, stopPendingIntent);

        Notification.Builder notificationBuilder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.main_icon)
                .setContentIntent(contentIntent)
                .setContent(contentView)
                .setOngoing(true);

        return notificationBuilder.build();
    }
}