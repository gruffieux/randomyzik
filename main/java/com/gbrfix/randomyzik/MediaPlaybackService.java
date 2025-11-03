package com.gbrfix.randomyzik;

import static android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_BROWSABLE;
import static android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.sqlite.SQLiteCursor;
import android.graphics.Bitmap;
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

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.preference.PreferenceManager;

import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.KeyEvent;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by gab on 14.01.2018.
 */

public class MediaPlaybackService extends MediaBrowserServiceCompat implements MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {
    public final static int NOTIFICATION_ID = 1;
    public static int TEST_DURATION = 100;
    final static String NOTIFICATION_CHANNEL = "MediaPlayback channel";
    private MediaSessionCompat session;
    private PlaybackStateCompat.Builder stateBuilder;
    private MediaMetadataCompat.Builder metaDataBuilder;
    private MediaPlayer player = null;
    private MediaProvider provider = null;
    private ProgressThread progress = null;
    private PowerManager.WakeLock wakeLock = null;
    private AudioFocusRequest focusRequest;
    private MediaSessionCallback mediaSessionCallback;
    private AmpSessionCallback ampSessionCallback;
    private boolean changeFocus = true;
    private boolean streaming = false;

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot(getString(R.string.app_name)+"_ROOT_ID", null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        //  Browsing not allowed
        if (TextUtils.equals(getString(R.string.app_name)+"_EMPTY_ROOT_ID", parentId)) {
            result.sendResult(null);
            return;
        }

        // Assume for example that the music catalog is already loaded/cached.
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

        // Check if this is the root menu:
        if (TextUtils.equals(getString(R.string.app_name)+"_ROOT_ID", parentId)) {
            // Build the MediaItem objects for the top level,
            // and put them in the mediaItems list...
            MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                    .setMediaId(getString(R.string.app_name)+"_PLAYLIST")
                    .setTitle(provider.getDbName())
                    .build();
            MediaBrowserCompat.MediaItem item = new MediaBrowserCompat.MediaItem(description, FLAG_BROWSABLE);
            mediaItems.add(item);
        } else {
            // Examine the passed parentMediaId to see which submenu we're at,
            // and put the children of that menu in the mediaItems list...
            if (TextUtils.equals(getString(R.string.app_name)+"_PLAYLIST", parentId)) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                int currentId = prefs.getInt("currentId_" + provider.getDbName(), 0);
                MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                        .setMediaId(String.valueOf(currentId))
                        .setTitle("Play")
                        .build();
                MediaBrowserCompat.MediaItem item = new MediaBrowserCompat.MediaItem(description, FLAG_PLAYABLE);
                mediaItems.add(item);
            }
        }

        result.sendResult(mediaItems);
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

        // Set session token so that client activities can communicate with it
        setSessionToken(session.getSessionToken());

        progress = new ProgressThread();
        player = new MediaPlayer();
        provider = new MediaProvider(this, DAOBase.DEFAULT_NAME);
        mediaSessionCallback = new MediaSessionCallback();
        ampSessionCallback = new AmpSessionCallback();

        init();

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
            player.setAudioAttributes(attributes);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL, "MediaPlayback notification", NotificationManager.IMPORTANCE_LOW);
            channel.setVibrationPattern(null);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Arrêt forcé, on sauve la piste en cours
        if (intent.getAction() == "stop") {
            int id = provider.getCurrentId();
            if (id > 0) {
                saveTrack(id, (int)session.getController().getPlaybackState().getPosition());
            }
            init();
            session.getController().getTransportControls().stop();
        }

        // Arrêt intentionnel, on annule la sauvegarde
        if (intent.getAction() == "close") {
            saveTrack(0, 0);
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

        if (action.equals("singleTrack")) {
            progress.stop();
            provider.setSelectId(extras.getInt("id"));
            provider.setPosition(0);
            session.getController().getTransportControls().play();
        }

        if (action.equals("stop")) {
            session.getController().getTransportControls().stop();
        }

        super.onCustomAction(action, extras, result);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        session.release();
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
                player.setVolume(1f, 1f);
                changeFocus = true;
                session.getController().getTransportControls().play();
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                changeFocus = false;
                session.getController().getTransportControls().play();
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                player.setVolume(1f, 1f);
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
                player.setVolume(0.5f, 0.5f);
                break;
        }
    }

    private void init() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean test = prefs.getBoolean("test", false);
        boolean amp = prefs.getBoolean("amp", false);

        // Set session callback and provider db
        if (amp) {
            streaming = prefs.getBoolean("amp_streaming", false);
            session.setCallback(streaming ? mediaSessionCallback : ampSessionCallback);
            try {
                provider.setDbName(AmpSession.getInstance(getApplicationContext()).dbName());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            streaming = false;
            session.setCallback(mediaSessionCallback);
            provider.setDbName(test ? "test-" + DAOBase.DEFAULT_NAME : DAOBase.DEFAULT_NAME);
        }

        // Restore current track and play mode
        int currentId = prefs.getInt("currentId_" + provider.getDbName(), 0);
        int position = prefs.getInt("position_" + provider.getDbName(), 0);
        int mode = prefs.getInt("mode", MediaProvider.MODE_TRACK);
        provider.setMode(mode);
        if (provider.checkMediaId(currentId)) {
            provider.setSelectId(currentId);
            provider.setPosition(position);
        }
    }

    private void prepareMediaStreaming(int mediaId) throws IOException {
        AmpSession ampSession = AmpSession.getInstance(getApplicationContext());
        String url = ampSession.streaming_url(mediaId, 0);
        player.setDataSource(url);
        player.prepareAsync();
        stateBuilder.setState(PlaybackStateCompat.STATE_BUFFERING, 0, 0);
        session.setPlaybackState(stateBuilder.build());
    }

    private void saveTrack(int id, int position) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();

        // Sauve piste en cours
        editor.putInt("currentId_" + provider.getDbName(), id);
        editor.putInt("position_" + provider.getDbName(), position);
        editor.commit();
    }

    private int requestAudioFocus() {
        if (!changeFocus) {
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }

        AudioManager manager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return manager.requestAudioFocus(focusRequest);
        }

        return manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    private void abandonAudioFocus() {
        if (!changeFocus) {
            return;
        }

        AudioManager manager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.abandonAudioFocusRequest(focusRequest);
        }

        manager.abandonAudioFocus(this);
    }

    private void keepAwake() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return; // Avoid multiple wake lock
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Randomyzik::AmpWakelock");
        wakeLock.acquire();
    }

    private void letSleep() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private Media mediaFromMetadata() {
        Media media = new Media();

        MediaMetadataCompat metadata = session.getController().getMetadata();

        media.setId(Integer.valueOf(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)));
        media.setTitle(metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
        media.setAlbum(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM));
        media.setArtist(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
        media.setDuration((int)metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));

        return media;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.setOnSeekCompleteListener(this);
        mp.setOnCompletionListener(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean test = prefs.getBoolean("test", false);

        // Seek position
        if (test) {
            mp.seekTo(mp.getDuration() - TEST_DURATION);
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
        stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, mp.getCurrentPosition(), 1.0f);
        session.setPlaybackState(stateBuilder.build());

        showNotification();
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        progress.start(mp);
        mp.start();

        // Upddate state
        stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, mp.getCurrentPosition(), 1.0f);
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
            saveTrack(0, 0);
            session.getController().getTransportControls().stop();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        String msg = getString(R.string.err_media_player, what, extra);
        Log.v("MediaPlayer error", msg);

        Bundle args = new Bundle();
        args.putInt("code", 1);
        args.putString("message", msg);
        session.sendSessionEvent("onError", args);
        
        session.getController().getTransportControls().skipToNext();

        return true;
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        private BecomingNoisyReceiver myNoisyAudioReceiver = new BecomingNoisyReceiver();
        private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        private boolean myNoisyAudioRegistred = false;
        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            KeyEvent ke = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

            // Handle custom event
            if (ke != null && ke.getAction() == KeyEvent.ACTION_UP) {
                switch (ke.getKeyCode()) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        session.getController().getTransportControls().play();
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        session.getController().getTransportControls().pause();
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
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
                startService(new Intent(MediaPlaybackService.this, MediaPlaybackService.class));
                registerReceiver(myNoisyAudioReceiver, intentFilter);
                myNoisyAudioRegistred = true;

                if (!progress.isStarted()) {
                    player.reset();
                    player.setOnPreparedListener(MediaPlaybackService.this);
                    player.setOnErrorListener(MediaPlaybackService.this);

                    Media media = provider.selectTrack();
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    Handler handler = new Handler(Looper.getMainLooper());

                    session.setActive(true);

                    // Prepare media player
                    if (streaming) {
                        AmpSession ampSession = AmpSession.getInstance(getApplicationContext());
                        if (ampSession.hasValidAuth()) {
                            prepareMediaStreaming(media.getMediaId());
                            String url = ampSession.get_art_url(media.getMediaId());
                            metaDataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, null)
                                    .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, url);
                        } else {
                            executor.execute(() -> {
                                try {
                                    ampSession.connect();
                                } catch (Exception e) {
                                    Bundle args = new Bundle();
                                    args.putString("message", e.getMessage());
                                    session.sendSessionEvent("onError", args);
                                    return;
                                }
                                handler.post(() -> {
                                    try {
                                        prepareMediaStreaming(media.getMediaId());
                                        String url = ampSession.get_art_url(media.getMediaId());
                                        metaDataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, null)
                                                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, url);
                                        session.setMetadata(metaDataBuilder.build());
                                    } catch (IOException e) {
                                        Bundle args = new Bundle();
                                        args.putString("message", e.getMessage());
                                        session.sendSessionEvent("onError", args);
                                        return;
                                    }
                                });
                            });
                            stateBuilder.setState(PlaybackStateCompat.STATE_CONNECTING, 0, 0);
                            session.setPlaybackState(stateBuilder.build());
                        }
                    } else {
                        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, media.getMediaId());
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                Bitmap thumbnail = getContentResolver().loadThumbnail(uri, new Size(300, 300), null);
                                metaDataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, null)
                                        .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, thumbnail);
                            } catch (IOException e) {
                                Bundle args = new Bundle();
                                args.putInt("code", 2);
                                args.putString("message", e.getMessage());
                                session.sendSessionEvent("onError", args);
                            }
                        }
                        player.setDataSource(MediaPlaybackService.this, uri);
                        player.prepare();
                    }

                    int duration = streaming ? media.getDuration() * 1000 : player.getDuration();

                    // Set session MediaMetadata
                    metaDataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, String.valueOf(media.getId()))
                            .putString(MediaMetadata.METADATA_KEY_TITLE, media.getTitle())
                            .putString(MediaMetadata.METADATA_KEY_ALBUM, media.getAlbum())
                            .putString(MediaMetadata.METADATA_KEY_ARTIST, media.getArtist())
                            .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, provider.getTotalRead()+1)
                            .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, provider.getTotal())
                            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
                    session.setMetadata(metaDataBuilder.build());

                    // Send session event
                    Bundle bundle = new Bundle();
                    bundle.putInt("id", media.getId());
                    bundle.putString("title", media.getTitle());
                    bundle.putString("album", media.getAlbum());
                    bundle.putString("artist", media.getArtist());
                    bundle.putInt("duration", duration);
                    bundle.putString("albumKey", media.getAlbumKey());
                    session.sendSessionEvent("onTrackSelect", bundle);
                } else {
                    player.start();
                    progress.resume();
                    stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, player.getCurrentPosition(), 1.0f);
                    session.setPlaybackState(stateBuilder.build());
                    showNotification();
                }
            } catch (PlayEndException e) {
                Bundle args = new Bundle();
                args.putInt("code", 2);
                args.putString("message", e.getMessage());
                session.sendSessionEvent("onError", args);
                session.getController().getTransportControls().stop();
            } catch (Exception e) {
                Bundle args = new Bundle();
                args.putString("message", e.getMessage());
                session.sendSessionEvent("onError", args);
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            session.getController().getTransportControls().play();
        }

        @Override
        public void onPause() {
            player.pause();
            progress.suspend();

            abandonAudioFocus();

            if (myNoisyAudioRegistred) {
                unregisterReceiver(myNoisyAudioReceiver);
                myNoisyAudioRegistred = false;
            }

            stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, player.getCurrentPosition(), 0);
            session.setPlaybackState(stateBuilder.build());

            showNotification();

            // On sauvegarde la position de piste en cours
            saveTrack(provider.getCurrentId(), player.getCurrentPosition());
        }

        @Override
        public void onRewind() {
            saveTrack(0, 0);
            progress.stop();
            provider.setSelectId(provider.getCurrentId());
            session.getController().getTransportControls().play();
        }

        @Override
        public void onSkipToNext() {
            saveTrack(0, 0);
            progress.stop();
            provider.updateState("skip");
            session.getController().getTransportControls().play();
        }

        @Override
        public void onStop() {
            if (player.isPlaying()) {
                player.stop();
            }
            if (progress.isStarted()) {
                progress.stop();
            }

            abandonAudioFocus();

            if (myNoisyAudioRegistred) {
                unregisterReceiver(myNoisyAudioReceiver);
                myNoisyAudioRegistred = false;
            }

            if (session.isActive()) {
                Executors.newSingleThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        AmpSession ampSession = AmpSession.getInstance(getApplicationContext());
                        try {
                            ampSession.unconnect();
                        } catch (Exception e) {
                            Bundle args = new Bundle();
                            args.putString("message", e.getMessage());
                            session.sendSessionEvent("onError", args);
                        }
                    }
                });
            }

            // Upddate state
            stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, 0, 0);
            session.setPlaybackState(stateBuilder.build());
            session.setActive(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }

            stopSelf();
        }
    }

    private class AmpSessionCallback extends MediaSessionCallback {
        @Override
        public void onPlay() {
            try {
                Intent intent = new Intent(MediaPlaybackService.this, MediaPlaybackService.class);
                startService(intent);

                AmpSession ampSession = AmpSession.getInstance(getApplicationContext());
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Handler handler = new Handler(Looper.getMainLooper());

                if (!progress.isStarted()) {
                    final Media media = provider.selectTrack();
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MediaPlaybackService.this);
                    boolean test = prefs.getBoolean("test", false);
                    final int duration = test ? TEST_DURATION : media.getDuration() * 1000;

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
                        try {
                            if (!ampSession.hasValidAuth()) {
                                ampSession.connect();
                            }
                            if (!session.isActive()) {
                                ampSession.checkAction("stop", null);
                            }
                            ampSession.localplay_add(media.getMediaId());
                            ampSession.localplay_play();
                            progress.start(duration, MediaPlaybackService.this);
                            keepAwake();
                        } catch (Exception e) {
                            Bundle args = new Bundle();
                            args.putString("message", e.getMessage());
                            session.sendSessionEvent("onError", args);
                            return;
                        }
                        handler.post(() -> {
                            session.setActive(true);

                            // Send session event
                            Bundle bundle = new Bundle();
                            bundle.putInt("id", media.getId());
                            bundle.putString("title", media.getTitle());
                            bundle.putString("album", media.getAlbum());
                            bundle.putString("artist", media.getArtist());
                            bundle.putInt("duration", duration);
                            session.sendSessionEvent("onTrackSelect", bundle);

                            // Update state and notif
                            stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f);
                            session.setPlaybackState(stateBuilder.build());
                            showNotification();
                        });
                    });
                } else {
                    long position = session.getController().getPlaybackState().getPosition();
                    executor.execute(() -> {
                        try {
                            if (!ampSession.hasValidAuth()) {
                                ampSession.connect();
                            }
                            ampSession.checkAction("pause", mediaFromMetadata());
                            ampSession.localplay_play();
                            progress.resume();
                            keepAwake();
                        } catch (Exception e) {
                            Bundle args = new Bundle();
                            args.putString("message", e.getMessage());
                            session.sendSessionEvent("onError", args);
                            return;
                        }
                        handler.post(() -> {
                            stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, position, 1.0f);
                            session.setPlaybackState(stateBuilder.build());
                            showNotification();
                        });
                    });
                }
            } catch (PlayEndException e) {
                Bundle args = new Bundle();
                args.putInt("code", 2);
                args.putString("message", e.getMessage());
                session.sendSessionEvent("onError", args);
                session.getController().getTransportControls().stop();
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
            AmpSession ampSession = AmpSession.getInstance(getApplicationContext());
            executor.execute(() -> {
                try {
                    if (!ampSession.hasValidAuth()) {
                        ampSession.connect();
                    }
                    ampSession.checkAction("play", mediaFromMetadata());
                    ampSession.localplay_pause();
                    progress.suspend();
                    letSleep();
                } catch (Exception e) {
                    Bundle args = new Bundle();
                    args.putString("message", e.getMessage());
                    session.sendSessionEvent("onError", args);
                    return;
                }
                handler.post(() -> {
                    int position = (int)session.getController().getPlaybackState().getPosition();
                    stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, position, 0);
                    session.setPlaybackState(stateBuilder.build());
                    showNotification();
                    saveTrack(provider.getCurrentId(), position);
                });
            });
        }

        @Override
        public void onStop() {
            progress.stop();

            if (session.isActive()) {
                Executors.newSingleThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        AmpSession ampSession = AmpSession.getInstance(getApplicationContext());
                        try {
                            if (!ampSession.hasValidAuth()) {
                                ampSession.connect();
                            }
                            ampSession.checkAction(null, mediaFromMetadata());
                            ampSession.localplay_stop();
                            ampSession.unconnect();
                        } catch (Exception e) {
                            Bundle args = new Bundle();
                            args.putString("message", e.getMessage());
                            session.sendSessionEvent("onError", args);
                        }
                    }
                });
            }

            stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, 0, 0);
            session.setPlaybackState(stateBuilder.build());
            session.setActive(false);

            letSleep();
            stopForeground(true);
            stopSelf();
        }
    }

    private void showNotification() {
        MediaControllerCompat controller = session.getController();
        MediaMetadataCompat mediaMetadata = controller.getMetadata();
        String title = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String album = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
        String artist = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
        Bitmap thumbnail = mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
        String contentTitle = MediaProvider.getTrackLabel(title, "", "");
        String contentText = MediaProvider.getTrackLabel("", album, artist);

        // Create an explicit intent for an Activity in your app
        Intent intent = new Intent(MediaPlaybackService.this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(MediaPlaybackService.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // Create an action intent for stopping the service
        Intent stopIntent = new Intent(this, MediaPlaybackService.class);
        stopIntent.setAction("close");
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
            .setLargeIcon(thumbnail)

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, builder.build());
        }
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
        private Thread blinker = null;
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
            if (blinker != null) {
                blinker.interrupt();
                blinker = null;
            }
        }

        public boolean isStarted() {
            return blinker != null && !blinker.isInterrupted();
        }

        @Override
        public void run() {
            Bundle extras = new Bundle();

            while (currentPosition < total) {
                try {
                    Thread.sleep(1000);
                    currentPosition = mp != null ? mp.getCurrentPosition() : currentPosition + 1000;
                    PlaybackStateCompat state = session.getController().getPlaybackState();
                    stateBuilder.setState(state.getState(), currentPosition, state.getPlaybackSpeed());
                    session.setPlaybackState(stateBuilder.build());
                    extras.putInt("position", currentPosition);
                    session.sendSessionEvent("onTrackProgress", extras);
                    session.setExtras(extras);
                    if (threadSuspended) {
                        synchronized (blinker) {
                            blinker.wait();
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
