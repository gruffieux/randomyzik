package com.gbrfix.randomyzik;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by gab on 14.01.2018.
 */

public class MediaPlaybackService extends MediaBrowserServiceCompat implements MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {
    public final static int NOTIFICATION_ID = 1;
    public final static String NOTIFICATION_CHANNEL = "Randomyzik channel";
    private MediaSessionCompat session;
    private PlaybackStateCompat.Builder stateBuilder;
    private BecomingNoisyReceiver myNoisyAudioReceiver = new BecomingNoisyReceiver();
    private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private MediaPlayer player = null;
    private MediaProvider provider;
    private AudioFocusRequest focusRequest;
    private  boolean changeFocus = true;

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
        //provider.setTest(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .build();
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL, "Control notification", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() == "STOP") {
            session.getController().getTransportControls().stop();
        }

        MediaButtonReceiver.handleIntent(session, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCustomAction(@NonNull String action, Bundle extras, @NonNull Result<Bundle> result) {
        if (action == "changeMode") {
            provider.setMode(extras.getInt("mode"));
        }

        if (action == "selectTrack") {
            provider.setSelectId(extras.getInt("id"));
        }

        if (action == "test") {
            provider.setTest(true);
        }

        super.onCustomAction(action, extras, result);
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            manager.deleteNotificationChannel(NOTIFICATION_CHANNEL);
        }

        super.onDestroy();
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN: // Your app has been granted audio focus again
                // Raise volume to normal, restart playback if necessary
                if (player != null) {
                    player.setVolume(1f, 1f);
                }
                changeFocus = true;
                session.getController().getTransportControls().play();
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                changeFocus = false;
                session.getController().getTransportControls().play();
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                if (player != null) {
                    player.setVolume(1f, 1f);
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS: // Permanent loss of audio focus
                changeFocus = true;
                session.getController().getTransportControls().pause();
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: // Pause playback
                changeFocus = false;
                session.getController().getTransportControls().pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: // Lower the volume, keep playing
                if (player != null) {
                    player.setVolume(0.5f, 0.5f);
                }
                break;
        }
    }

    private int requestAudioFocus() {
        if (!changeFocus) {
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }

        AudioManager manager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return manager.requestAudioFocus(focusRequest);
        }

        return manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    private void abandonAudioFocus() {
        if (!changeFocus) {
            return;
        }

        AudioManager manager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.abandonAudioFocusRequest(focusRequest);
        }

        manager.abandonAudioFocus(this);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Bundle args = new Bundle();

        try {
            provider.updateState("read");
            startNewTrack();
            showNotification();
            args.putBoolean("last", false);
            session.sendSessionEvent("onTrackRead", args);
        }
        catch (PlayEndException e) {
            session.getController().getTransportControls().stop();
            args.putBoolean("last", true);
            session.sendSessionEvent("onTrackRead", args);
        }
        catch (Exception e) {
            Log.v("Exception", e.getMessage());
        }
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            return super.onMediaButtonEvent(mediaButtonEvent);
        }

        @Override
        public void onStop() {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }

            try {
                abandonAudioFocus();
                unregisterReceiver(myNoisyAudioReceiver);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            // Upddate state
            stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, 0, 0);
            session.setPlaybackState(stateBuilder.build());
            session.setActive(false);

            stopForeground(true);
            stopSelf();

            super.onStop();
        }

        @Override
        public void onPlay() {
            int res = changeFocus ? requestAudioFocus() : AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                try {
                    session.setActive(true);
                    startService(new Intent(getApplicationContext(), MediaPlaybackService.class));
                    registerReceiver(myNoisyAudioReceiver, intentFilter);

                    if (player == null || provider.getSelectId() > 0) {
                        startNewTrack();
                    } else {
                        player.start();
                    }

                    // Upddate state
                    stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, player.getCurrentPosition(), 0);
                    session.setPlaybackState(stateBuilder.build());

                    showNotification();
                } catch (Exception e) {
                    Bundle args = new Bundle();
                    args.putString("message", e.getMessage());
                    session.sendSessionEvent("onError", args);
                }
            }

        }

        @Override
        public void onPause() {
            player.pause();
            stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, player.getCurrentPosition(), 0);
            session.setPlaybackState(stateBuilder.build());

            showNotification();
            abandonAudioFocus();
            unregisterReceiver(myNoisyAudioReceiver);
        }

        @Override
        public void onRewind() {
            try {
                player.stop();
                player.prepare();
                player.seekTo(0);
                player.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSkipToNext() {
            try {
                provider.updateState("skip");
                player.stop();
                startNewTrack();
                showNotification();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startNewTrack() throws Exception {
        if (player != null) {
            player.release();
            player = null;
        }

        Media media = provider.selectTrack();
        File file = new File(media.getPath());
        player = MediaPlayer.create(getApplicationContext(), Uri.fromFile(file));

        if (provider.isTest()) {
            player.seekTo(player.getDuration() - 10);
        }

        player.start();
        player.setOnCompletionListener(MediaPlaybackService.this);

        // Progress thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                int currentPosition = 0;
                int total = player.getDuration();
                Bundle bundle = new Bundle();
                while (currentPosition < total) {
                    try {
                        Thread.sleep(1000);
                        currentPosition = player.getCurrentPosition();
                        stateBuilder.setBufferedPosition(currentPosition);
                        session.setPlaybackState(stateBuilder.build());
                    } catch (Exception e) {
                        return;
                    }
                    bundle.putInt("position", currentPosition);
                    session.sendSessionEvent("onTrackProgress", bundle);
                }
            }
        }).start();

        MediaMetadataCompat.Builder metaDataBuilder = new MediaMetadataCompat.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, String.valueOf(media.getId()))
            .putString(MediaMetadata.METADATA_KEY_TITLE, media.getTitle())
            .putString(MediaMetadata.METADATA_KEY_ALBUM, media.getAlbum())
            .putString(MediaMetadata.METADATA_KEY_ARTIST, media.getArtist())
            .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, provider.getTotalRead()+1) // A tester
            .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, provider.getTotal()) // A tester
            .putLong(MediaMetadata.METADATA_KEY_DURATION, player.getDuration());

        session.setMetadata(metaDataBuilder.build());

        Bundle bundle = new Bundle();
        bundle.putInt("id", media.getId());
        bundle.putString("title", media.getTitle());
        bundle.putString("album", media.getAlbum());
        bundle.putString("artist", media.getArtist());
        bundle.putInt("duration", player.getDuration());
        session.sendSessionEvent("onTrackSelect", bundle);
    }

    private void showNotification() {
        MediaControllerCompat controller = session.getController();
        MediaMetadataCompat mediaMetadata = controller.getMetadata();
        MediaDescriptionCompat description = mediaMetadata.getDescription();
        String title = description.getTitle() != null ? description.getTitle().toString() : "";
        String artist = description.getSubtitle() != null ? description.getSubtitle().toString() : "";
        String contentTitle = MediaProvider.getTrackLabel(title, "", artist);
        String contentText = description.getDescription() != null ? description.getDescription().toString() : "";

        // Create an explicit intent for an Activity in your app
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

        // Create an action intent for stopping the service
        Intent stopIntent = new Intent(this, MediaPlaybackService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 0);

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL);

        builder
            // Add the metadata for the currently playing track
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSubText(provider.getSummary())

            // Enable launching the app by clicking the notification
            .setContentIntent(pendingIntent)

            // Stop the service when the notification is swiped away
            //.setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
            .setDeleteIntent(stopPendingIntent)

            // Make the transport controls visible on the lockscreen
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            // Add an app icon and set its accent color
            // Be careful about the color
            .setSmallIcon(R.drawable.ic_stat_audio)

            // Add a pause button
            .addAction(new NotificationCompat.Action(
                controller.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING ? R.drawable.ic_action_pause : R.drawable.ic_action_play, "Resume",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)))

            // Add a cancel button
            .addAction(new NotificationCompat.Action(
                R.drawable.ic_action_cancel, "Stop", stopPendingIntent))

            // Take advantage of MediaStyle features
            .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1));

        // Display the notification and place the service in the foreground
        startForeground(NOTIFICATION_ID, builder.build());
    }

    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                session.getController().getTransportControls().pause();
            }
        }
    }
}
