package com.gbrfix.randomyzik;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
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
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.support.v4.media.MediaBrowserCompat;
import androidx.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import androidx.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import java.util.List;

/**
 * Created by gab on 14.01.2018.
 */

public class MediaPlaybackService extends MediaBrowserServiceCompat implements MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {
    public final static int NOTIFICATION_ID = 1;
    private MediaSessionCompat session;
    private PlaybackStateCompat.Builder stateBuilder;
    private MediaMetadataCompat.Builder metaDataBuilder;
    private BecomingNoisyReceiver myNoisyAudioReceiver = new BecomingNoisyReceiver();
    private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private MediaPlayer player = null;
    private ProgressThread progress = null;
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

        // Set metadata builder
        metaDataBuilder = new MediaMetadataCompat.Builder();

        // Set session callback methods
        session.setCallback(new MediaSessionCallback());

        // Set session token so that client activities can communicate with it
        setSessionToken(session.getSessionToken());

        MediaProvider.getInstance(this).setTest(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Audiofocus compatibility
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(this)
                .build();
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
            MediaProvider.getInstance(this).setMode(extras.getInt("mode"));
        }

        if (action == "selectTrack") {
            MediaProvider.getInstance(this).setSelectId(extras.getInt("id"));
        }

        if (action == "restoreTrack") {
            MediaProvider.getInstance(this).setSelectId(extras.getInt("id"));
            MediaProvider.getInstance(this).setPosition(extras.getInt("position"));
        }

        if (action == "test") {
            MediaProvider.getInstance(this).setTest(true);
        }

        if (action == "stop") {
            session.getController().getTransportControls().stop();
        }

        super.onCustomAction(action, extras, result);
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
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
                break;
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
        MediaProvider provider = MediaProvider.getInstance(this);
        provider.updateState("read");

        player.stop();
        player.release();
        player = null;

        boolean last = provider.getTotalRead() == provider.getTotal() - 1;
        Bundle args = new Bundle();
        args.putBoolean("last", last);
        session.sendSessionEvent("onTrackRead", args);

        if (!last) {
            session.getController().getTransportControls().play();
        } else {
            session.getController().getTransportControls().stop();
        }
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        MediaProvider provider = MediaProvider.getInstance(MediaPlaybackService.this);
        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            KeyEvent ke = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

            if (ke != null && ke.getAction() == KeyEvent.ACTION_UP) {
                switch (ke.getKeyCode()) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        session.getController().getTransportControls().play();
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        session.getController().getTransportControls().pause();
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                    case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
                        session.getController().getTransportControls().skipToNext();
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD:
                        session.getController().getTransportControls().rewind();
                        return true;
                }
            }

            return super.onMediaButtonEvent(mediaButtonEvent);
        }

        @Override
        public void onStop() {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }

            if (progress != null) {
                progress.stop();
                progress = null;
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

            // On annule la sauvegarde de piste en cours
            Bundle args = new Bundle();
            args.putInt("id", 0);
            args.putInt("position", 0);
            session.sendSessionEvent("onTrackSave", args);

            super.onStop();
        }

        @Override
        public void onPlay() {
            Media media = null;
            int res = changeFocus ? requestAudioFocus() : AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

            if (res != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                return;
            }

            try {
                session.setActive(true);
                startService(new Intent(getApplicationContext(), MediaPlaybackService.class));
                registerReceiver(myNoisyAudioReceiver, intentFilter);

                if (player == null || provider.getSelectId() > 0) {
                    if (progress != null) {
                        progress.stop();
                        progress = null;
                    }

                    if (player != null) {
                        player.release();
                        player = null;
                    }

                    progress = new ProgressThread();
                    media = provider.selectTrack();
                    Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, media.getMediaId());
                    player = MediaPlayer.create(getApplicationContext(), uri);
                    int position = provider.getPosition();

                    if (provider.isTest()) {
                        player.seekTo(player.getDuration() - 10);
                    } else if (position > 0) {
                        player.seekTo(position);
                        provider.setPosition(0);
                    }

                    metaDataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, String.valueOf(media.getId()))
                            .putString(MediaMetadata.METADATA_KEY_TITLE, media.getTitle())
                            .putString(MediaMetadata.METADATA_KEY_ALBUM, media.getAlbum())
                            .putString(MediaMetadata.METADATA_KEY_ARTIST, media.getArtist())
                            .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, provider.getTotalRead()+1) // A tester
                            .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, provider.getTotal()) // A tester
                            .putLong(MediaMetadata.METADATA_KEY_DURATION, player.getDuration());
                    session.setMetadata(metaDataBuilder.build());

                    progress.start();
                    player.setOnCompletionListener(MediaPlaybackService.this);
                    player.start();

                    Bundle bundle = new Bundle();
                    bundle.putInt("id", media.getId());
                    bundle.putString("title", media.getTitle());
                    bundle.putString("album", media.getAlbum());
                    bundle.putString("artist", media.getArtist());
                    bundle.putInt("duration", player.getDuration());
                    session.sendSessionEvent("onTrackSelect", bundle);
                } else {
                    player.start();
                    if (progress != null && progress.isThreadSuspended()) {
                        progress.resume();
                    }
                }

                // Upddate state
                stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, player.getCurrentPosition(), 0);
                session.setPlaybackState(stateBuilder.build());

                showNotification();
            } catch (PlayEndException e) {
                Bundle args = new Bundle();
                args.putString("message", e.getMessage());
                session.getController().getTransportControls().stop();
                session.sendSessionEvent("onError", args);
            } catch (Exception e) {
                Bundle args = new Bundle();
                args.putString("message", e.getMessage());
                if (media != null) {
                    args.putInt("media_id", media.getMediaId());
                }
                session.sendSessionEvent("onError", args);
            }
        }

        @Override
        public void onPause() {
            player.pause();
            progress.suspend();
            stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, player.getCurrentPosition(), 0);
            session.setPlaybackState(stateBuilder.build());

            showNotification();
            abandonAudioFocus();
            unregisterReceiver(myNoisyAudioReceiver);

            // On sauvegarde la position de piste en cours
            Bundle args = new Bundle();
            args.putInt("id", provider.getCurrentId());
            args.putInt("position", player.getCurrentPosition());
            session.sendSessionEvent("onTrackSave", args);
        }

        @Override
        public void onRewind() {
            provider.setSelectId(provider.getCurrentId());
            session.getController().getTransportControls().play();
        }

        @Override
        public void onSkipToNext() {
            provider.updateState("skip");
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }
            session.getController().getTransportControls().play();
        }
    }

    private void showNotification() {
        MediaControllerCompat controller = session.getController();
        MediaMetadataCompat mediaMetadata = controller.getMetadata();
        String title = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String album = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
        String artist = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
        String contentTitle = MediaProvider.getTrackLabel(title, "", "");
        String contentText = MediaProvider.getTrackLabel("", album, artist);

        // Create an explicit intent for an Activity in your app
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

        // Create an action intent for stopping the service
        Intent stopIntent = new Intent(this, MediaPlaybackService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 0);

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MainActivity.NOTIFICATION_CHANNEL);

        builder
            // Add the metadata for the currently playing track
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSubText(MediaProvider.getInstance(this).getSummary())

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
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
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

    private class ProgressThread implements Runnable {
        private Thread blinker;
        private boolean threadSuspended;
        private int currentPosition;
        private int total;

        public void start() {
            currentPosition = 0;
            total = player.getDuration();
            threadSuspended = false;
            blinker = new Thread(this);
            blinker.start();
        }

        public void suspend() {
            threadSuspended = true;
        }

        public void resume() {
            threadSuspended = false;
            synchronized (blinker) {
                blinker.notify();
            }
        }

        public void stop() {
            blinker = null;
        }

        public boolean isThreadSuspended() {
            return threadSuspended;
        }

        @Override
        public void run() {
            Thread thisThread = Thread.currentThread();
            Bundle bundle = new Bundle();

            while (currentPosition < total && blinker == thisThread) {
                try {
                    Thread.sleep(1000);
                    currentPosition = player.getCurrentPosition();
                    bundle.putInt("position", currentPosition);
                    session.sendSessionEvent("onTrackProgress", bundle);
                    session.setExtras(bundle);
                    if (threadSuspended) {
                        synchronized (blinker) {
                            while (threadSuspended) {
                                blinker.wait();
                            }
                        }
                    }
                } catch (Exception e) {
                    return;
                }
            }
        }
    }
}
