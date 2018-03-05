package com.gbrfix.randomyzik;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.io.File;
import java.util.List;

/**
 * Created by gab on 14.01.2018.
 */

public class MediaPlaybackService extends MediaBrowserServiceCompat {
    private MediaSessionCompat session;
    private PlaybackStateCompat.Builder stateBuilder;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;
    private BecomingNoisyReceiver myNoisyAudioReceiver = new BecomingNoisyReceiver();
    private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private MediaPlayer player = null;
    private MediaProvider provider;

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
        session = new MediaSessionCompat(this, MediaPlaybackService.class.getSimpleName());

        // Enable callbacks from media buttons and transport controls
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Set an initial playback state with ACTION_PLAY, so media buttons can start the player
        stateBuilder = new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE);
        session.setPlaybackState(stateBuilder.build());

        // Set session callback methods
        session.setCallback(new MediaSessionCallback());

        // Set session token so that client activities can communicate with it
        setSessionToken(session.getSessionToken());

        provider = new MediaProvider(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(session, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            int state = session.getController().getPlaybackState().getState();

            if (state == PlaybackStateCompat.STATE_PLAYING) {
                session.getController().getTransportControls().pause();
            } else {
                session.getController().getTransportControls().play();
            }

            return super.onMediaButtonEvent(mediaButtonEvent);
        }

        @Override
        public void onPlay() {
            AudioManager manager = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            int res = manager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                session.setActive(true);
                startService(new Intent(getApplicationContext(), MediaPlaybackService.class));
                registerReceiver(myNoisyAudioReceiver, intentFilter);
                if (player == null || provider.getSelectId() > 0) {
                    if (player != null) {
                        player.release();
                        player = null;
                    }
                    try {
                        Bundle bundle = provider.selectTrack();
                        File file = new File(bundle.getString("path"));
                        player = MediaPlayer.create(getApplicationContext(), Uri.fromFile(file));
                        player.start();
                        createNotification();
                        bundle.putInt("duration", player.getDuration());
                        session.sendSessionEvent("selectTrack", bundle);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    player.start();
                }
                stateBuilder = new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PLAYING, player.getCurrentPosition(), 0);
                session.setPlaybackState(stateBuilder.build());
            }
        }

        @Override
        public void onPause() {
            player.pause();
            stateBuilder = new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PAUSED, player.getCurrentPosition(), 0);
            session.setPlaybackState(stateBuilder.build());

            AudioManager manager = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            manager.abandonAudioFocus(afChangeListener);
            unregisterReceiver(myNoisyAudioReceiver);
        }
    }

    public void createNotification() {
        MediaControllerCompat controller = session.getController();
        //MediaMetadataCompat mediaMetadata = controller.getMetadata();
        //MediaDescriptionCompat description = mediaMetadata.getDescription();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        builder
            // Add the metadata for the currently playing track
            .setContentTitle("hello world")
            .setContentText("teste")
            .setSubText("This is a hello world test")

            // Enable launching the player by clicking the notification
            .setContentIntent(controller.getSessionActivity())

            // Stop the service when the notification is swiped away
            .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))

            // Make the transport controls visible on the lockscreen
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            // Add an app icon and set its accent color
            // Be careful about the color
            .setSmallIcon(R.drawable.ic_stat_audio)

            // Add a pause button
            .addAction(new NotificationCompat.Action(
                R.drawable.ic_action_pause, "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)))

            // Take advantage of MediaStyle features
            .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0)

                // Add a cancel button
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)));

        // Display the notification and place the service in the foreground
        startForeground(1, builder.build());
    }

    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                player.pause();
            }
        }
    }
}
