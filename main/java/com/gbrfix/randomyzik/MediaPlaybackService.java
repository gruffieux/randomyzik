package com.gbrfix.randomyzik;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.support.v4.media.MediaBrowserCompat;
import androidx.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.preference.PreferenceManager;

import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by gab on 14.01.2018.
 */

public class MediaPlaybackService extends MediaBrowserServiceCompat implements MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {
    public final static int NOTIFICATION_ID = 1;
    final static String NOTIFICATION_CHANNEL = "MediaPlayback channel";
    private MediaSessionCompat session;
    private PlaybackStateCompat.Builder stateBuilder;
    private MediaMetadataCompat.Builder metaDataBuilder;
    private BecomingNoisyReceiver myNoisyAudioReceiver = new BecomingNoisyReceiver();
    private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private MediaPlayer player = null;
    private MediaProvider provider = null;
    private ProgressThread progress = null;
    private PowerManager.WakeLock wakeLock = null;
    private AudioFocusRequest focusRequest;
    private MediaSessionCallback mediaSessionCallback;
    private AmpSessionCallback ampSessionCallback;
    private  boolean changeFocus = true;
    private boolean streaming = false;

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
        mediaSessionCallback = new MediaSessionCallback();
        ampSessionCallback = new AmpSessionCallback();
        session.setCallback(mediaSessionCallback);

        // Set session token so that client activities can communicate with it
        setSessionToken(session.getSessionToken());

        progress = new ProgressThread();
        player = new MediaPlayer();
        provider = new MediaProvider(this, DAOBase.DEFAULT_NAME);
        //provider.setTest(true);

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
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL, "MediaPlayback notification", NotificationManager.IMPORTANCE_LOW);
            channel.setVibrationPattern(null);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() == "stop") {
            session.getController().getTransportControls().stop();
        }

        MediaButtonReceiver.handleIntent(session, intent);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCustomAction(@NonNull String action, Bundle extras, @NonNull Result<Bundle> result) {
        if (action.equals("changeMode")) {
            provider.setMode(extras.getInt("mode"));
        }

        if (action.equals("selectTrack")) {
            progress.stop();
            provider.setSelectId(extras.getInt("id"));
        }

        if (action.equals("restoreTrack")) {
            progress.stop();
            provider.setSelectId(extras.getInt("id"));
            provider.setPosition(extras.getInt("position"));
        }

        if (action.equals("test")) {
            provider.setTest(true);
        }

        if (action.equals("stop")) {
            session.getController().getTransportControls().stop();
        }

        if (action.equals("streaming")) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean amp = prefs.getBoolean("amp", false);
            if (amp) {
                AmpSession ampSession = AmpSession.getInstance();
                streaming = prefs.getBoolean("amp_streaming", false);
                String server = prefs.getString("amp_server", "");
                String catalog = prefs.getString("amp_catalog", "0");
                session.setCallback(streaming ? mediaSessionCallback : ampSessionCallback);
                Executors.newSingleThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            provider.setDbName(AmpRepository.dbName(server, catalog));
                            ampSession.connect(prefs);
                        } catch (Exception e) {
                            Bundle args = new Bundle();
                            args.putString("message", e.getMessage());
                            session.sendSessionEvent("onError", args);
                        }
                    }
                });
            } else {
                streaming = false;
                session.setCallback(mediaSessionCallback);
            }
        }

        super.onCustomAction(action, extras, result);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        player.release();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            manager.deleteNotificationChannel(NOTIFICATION_CHANNEL);
        }
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
    public void onPrepared(MediaPlayer mp) {
        mp.setOnSeekCompleteListener(this);
        mp.setOnCompletionListener(this);

        // Seek position
        if (provider.isTest()) {
            mp.seekTo(mp.getDuration() - 10);
            return;
        } else if (provider.getPosition() > 0) {
            mp.seekTo(provider.getPosition());
            provider.setPosition(0);
            return;
        }

        // Start player
        progress.start(mp);
        mp.start();

        // Upddate state
        stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, player.getCurrentPosition(), 0);
        session.setPlaybackState(stateBuilder.build());

        showNotification();
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        progress.start(mp);
        mp.start();

        // Upddate state
        stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, player.getCurrentPosition(), 0);
        session.setPlaybackState(stateBuilder.build());

        showNotification();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        provider.updateState("read");

        boolean last = provider.getTotalRead() == provider.getTotal() - 1;
        Bundle args = new Bundle();
        args.putBoolean("last", last);
        session.sendSessionEvent("onTrackRead", args);

        if (!last) {
            progress.stop();
            session.getController().getTransportControls().play();
        } else {
            session.getController().getTransportControls().stop();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        session.getController().getTransportControls().stop();

        Bundle args = new Bundle();
        args.putString("message", "Media player error");
        session.sendSessionEvent("onError", args);

        return false;
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
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
        public void onPlay() {
            int res = changeFocus ? requestAudioFocus() : AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

            if (res != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                return;
            }

            try {
                session.setActive(true);

                startService(new Intent(getApplicationContext(), MediaPlaybackService.class));
                registerReceiver(myNoisyAudioReceiver, intentFilter);

                if (!progress.isStarted()) {
                    player.reset();
                    player.setOnPreparedListener(MediaPlaybackService.this);
                    player.setOnErrorListener(MediaPlaybackService.this);

                    Media media = provider.selectTrack();

                    // Prepare media player
                    int duration;
                    if (streaming) {
                        AmpSession ampSession = AmpSession.getInstance();
                        String url = ampSession.streaming_url(media.getMediaId(), 0);
                        player.setDataSource(url);
                        player.prepareAsync();
                        duration = media.getDuration() * 1000;
                    } else {
                        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, media.getMediaId());
                        player.setDataSource(getApplicationContext(), uri);
                        player.prepare();
                        duration = player.getDuration();
                    }

                    // Set session MediaMetadata
                    metaDataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, String.valueOf(media.getId()))
                            .putString(MediaMetadata.METADATA_KEY_TITLE, media.getTitle())
                            .putString(MediaMetadata.METADATA_KEY_ALBUM, media.getAlbum())
                            .putString(MediaMetadata.METADATA_KEY_ARTIST, media.getArtist())
                            .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, provider.getTotalRead()+1) // A tester
                            .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, provider.getTotal()) // A tester
                            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
                    session.setMetadata(metaDataBuilder.build());

                    // Send session event
                    Bundle bundle = new Bundle();
                    bundle.putInt("id", media.getId());
                    bundle.putString("title", media.getTitle());
                    bundle.putString("album", media.getAlbum());
                    bundle.putString("artist", media.getArtist());
                    bundle.putInt("duration", duration);
                    session.sendSessionEvent("onTrackSelect", bundle);
                } else {
                    player.start();
                    progress.resume();
                    stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, player.getCurrentPosition(), 0);
                    session.setPlaybackState(stateBuilder.build());
                    showNotification();
                }
            } catch (PlayEndException e) {
                session.getController().getTransportControls().stop();
                Bundle args = new Bundle();
                args.putString("message", e.getMessage());
                session.sendSessionEvent("onError", args);
            } catch (Exception e) {
                Bundle args = new Bundle();
                args.putString("message", e.getMessage());
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
            progress.stop();
            provider.setSelectId(provider.getCurrentId());
            session.getController().getTransportControls().play();
        }

        @Override
        public void onSkipToNext() {
            progress.stop();
            provider.updateState("skip");
            session.getController().getTransportControls().play();
        }

        @Override
        public void onStop() {
            player.stop();
            progress.stop();

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
        }
    }

    private class AmpSessionCallback extends MediaSessionCallback {
        @Override
        public void onPlay() {
            try {
                session.setActive(true);

                Intent intent = new Intent(getApplicationContext(), MediaPlaybackService.class);
                startService(intent);

                ExecutorService executor = Executors.newSingleThreadExecutor();
                Handler handler = new Handler(Looper.getMainLooper());

                if (!progress.isStarted()) {
                    final Media media = provider.selectTrack();
                    final int duration = media.getDuration() * 1000;
                    //final int duration = 10 * 1000; // Teste

                    // Keep CPU awake
                    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Randomyzik::AmpWakelock");
                    wakeLock.acquire();

                    // Set session MediaMetadata
                    metaDataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, String.valueOf(media.getId()))
                            .putString(MediaMetadata.METADATA_KEY_TITLE, media.getTitle())
                            .putString(MediaMetadata.METADATA_KEY_ALBUM, media.getAlbum())
                            .putString(MediaMetadata.METADATA_KEY_ARTIST, media.getArtist())
                            .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, provider.getTotalRead()+1) // A tester
                            .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, provider.getTotal()) // A tester
                            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
                    session.setMetadata(metaDataBuilder.build());

                    // Start localplay
                    executor.execute(() -> {
                        AmpSession ampSession = AmpSession.getInstance();
                        try {
                            ampSession.localplay_add(media.getMediaId());
                            ampSession.localplay_play();
                        } catch (IOException e) {
                            session.getController().getTransportControls().stop();
                            Bundle args = new Bundle();
                            args.putString("message", e.getMessage());
                            session.sendSessionEvent("onError", args);
                            return;
                        }
                        handler.post(() -> {
                            progress.start(duration, MediaPlaybackService.this);
                            stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0, 0);
                            session.setPlaybackState(stateBuilder.build());
                            showNotification();
                        });
                    });

                    // Send session event
                    Bundle bundle = new Bundle();
                    bundle.putInt("id", media.getId());
                    bundle.putString("title", media.getTitle());
                    bundle.putString("album", media.getAlbum());
                    bundle.putString("artist", media.getArtist());
                    bundle.putInt("duration", duration);
                    session.sendSessionEvent("onTrackSelect", bundle);
                } else {
                    long position = session.getController().getPlaybackState().getPosition();
                    executor.execute(() -> {
                        try {
                            AmpSession.getInstance().localplay_play();
                        } catch (IOException e) {
                            session.getController().getTransportControls().stop();
                            Bundle args = new Bundle();
                            args.putString("message", e.getMessage());
                            session.sendSessionEvent("onError", args);
                            return;
                        }
                        handler.post(() -> {
                            progress.resume();
                            stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, position, 0);
                            session.setPlaybackState(stateBuilder.build());
                            showNotification();
                        });
                    });
                }
            } catch (PlayEndException e) {
                session.getController().getTransportControls().stop();
                Bundle args = new Bundle();
                args.putString("message", e.getMessage());
                session.sendSessionEvent("onError", args);
            } catch (Exception e) {
                Bundle args = new Bundle();
                args.putString("message", e.getMessage());
                session.sendSessionEvent("onError", args);
            }
        }

        @Override
        public void onPause() {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.execute(() -> {
                try {
                    AmpSession.getInstance().localplay_pause();
                } catch (IOException e) {
                    session.getController().getTransportControls().stop();
                    Bundle args = new Bundle();
                    args.putString("message", e.getMessage());
                    session.sendSessionEvent("onError", args);
                    return;
                }
                handler.post(() -> {
                    progress.suspend();
                    stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, session.getController().getPlaybackState().getPosition(), 0);
                    session.setPlaybackState(stateBuilder.build());
                    showNotification();
                });
            });
        

        @Override
        public void onStop() {
            progress.stop();
            
            Executors.newSingleThreadExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        AmpSession.getInstance().localplay_stop();
                    } catch (IOException e) {
                        Bundle args = new Bundle();
                        args.putString("message", e.getMessage());
                        session.sendSessionEvent("onError", args);
                    }
                }
            });

            stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, 0, 0);
            session.setPlaybackState(stateBuilder.build());
            session.setActive(false);

            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }

            stopForeground(true);
            stopSelf();

            // On annule la sauvegarde de piste en cours
            Bundle args = new Bundle();
            args.putInt("id", 0);
            args.putInt("position", 0);
            session.sendSessionEvent("onTrackSave", args);
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
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // Create an action intent for stopping the service
        Intent stopIntent = new Intent(this, MediaPlaybackService.class);
        stopIntent.setAction("stop");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

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

            /*.addAction(new NotificationCompat.Action(R.drawable.ic_action_rewind, "Rewind",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_REWIND)))*/

            /*addAction(new NotificationCompat.Action(R.drawable.ic_action_skip, "Skip",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))*/

            /*.addAction(new NotificationCompat.Action(R.drawable.ic_action_cancel, "Stop",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)))*/

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
        private MediaPlayer mp;
        private MediaPlayer.OnCompletionListener callback;

        public void start(MediaPlayer mp) {
            currentPosition = 0;
            this.mp = mp;
            total = mp.getDuration();
            threadSuspended = false;
            blinker = new Thread(this);
            blinker.start();
            this.callback = null;
        }

        public void start(int duration, MediaPlayer.OnCompletionListener callback) {
            currentPosition = 0;
            total = duration;
            threadSuspended = false;
            blinker = new Thread(this);
            blinker.start();
            mp = null;
            this.callback = callback;
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

        public boolean isStarted() {
            return blinker != null;
        }

        @Override
        public void run() {
            Thread thisThread = Thread.currentThread();
            Bundle bundle = new Bundle();

            while (currentPosition < total) {
                if (blinker != thisThread) {
                    return; // Thread aborted
                }
                try {
                    Thread.sleep(1000);
                    currentPosition = mp != null ? mp.getCurrentPosition() : currentPosition + 1000;
                    stateBuilder.setState(session.getController().getPlaybackState().getState(), currentPosition, 0);
                    session.setPlaybackState(stateBuilder.build());
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

            if (callback != null) {
                callback.onCompletion(null);
            }
        }
    }
}
