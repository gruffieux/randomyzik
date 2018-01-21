package com.gbrfix.randomyzik;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.util.List;

/**
 * Created by gab on 14.01.2018.
 */

public class MediaPlaybackService extends MediaBrowserServiceCompat {
    private MediaSessionCompat session;
    private PlaybackStateCompat.Builder stateBuilder;
    private BecomingNoisyReceiver myNoisyAudioReceiver = new BecomingNoisyReceiver();
    private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    AudioService audioService;

    private ServiceConnection audioConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            final AudioService.AudioBinder audioBinder = (AudioService.AudioBinder)iBinder;
            audioService = audioBinder.getService();
            audioService.setBound(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            audioService.setBound(false);
        }
    };

    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                audioService.pause();
            }
        }
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot(getString(R.string.app_name), null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(null);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Create a media session
        session = new MediaSessionCompat(this, AudioService.class.getSimpleName());

        // Enable callbacks from media buttons and transport controls
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Set an initial playback state with ACTION_PLAY, so media buttons can start the player
        stateBuilder = new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE);
        session.setPlaybackState(stateBuilder.build());

        // Set session callback methods
        session.setCallback(new MediaSessionCallback());

        // Set session token so that client activities can communicate with it
        setSessionToken(session.getSessionToken());

        // On se connecte au service audio
        Intent intent = new Intent(this, AudioService.class);
        bindService(intent, audioConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(session, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            AudioManager manager = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            int res = manager.requestAudioFocus(audioService, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                if (audioService.playerIsActive()) {
                    audioService.play();
                }
                else {
                    try {
                        audioService.startPlay();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                registerReceiver(myNoisyAudioReceiver, intentFilter);
            }
        }

        @Override
        public void onPause() {
            if (audioService.playerIsActive()) {
                audioService.pause();
            }

            AudioManager manager = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            manager.abandonAudioFocus(audioService);
            unregisterReceiver(myNoisyAudioReceiver);
        }
    }
}
