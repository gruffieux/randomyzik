package com.gbrfix.randomizik;

import android.database.sqlite.SQLiteCursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.content.Context;

import java.util.Random;

/**
 * Created by gab on 16.07.2017.
 */
public class MediaController {
    protected static MediaPlayer player = null;
    protected AudioManager manager;
    protected SQLiteCursor cursor;
    protected MediaDAO dao;
    private Context context;
    private boolean isPaused;

    public MediaController(Context context) {
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        cursor = null;
        dao = new MediaDAO(context);
        this.context = context;
        isPaused = false;
    }

    public boolean selectTrack() {
        dao.open();

        cursor = dao.getFromFlag("unread");
        int total = cursor.getCount();

        dao.close();

        if (total == 0) {
            return false;
        }

        Random random = new Random();
        int pos = random.nextInt(total - 1);
        cursor.moveToPosition(pos);
        int id = cursor.getInt(0);
        final String path = cursor.getString(1);

        try {
            if (player != null) {
                player.release();
            }
            manager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        player = MediaPlayer.create(context, Uri.parse(path));
                        //player.seekTo(player.getDuration() - 10000);
                        player.start();

                        // On traite lorsque la lecture est complétée
                        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            public void onCompletion(MediaPlayer mp) {
                                updateState("read");
                                selectTrack();
                            }
                        });
                    }
                }
            }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        }
        catch (Exception e) {

        }

        return true;
    }

    public void rewind() {
        try {
            player.stop();
            player.prepare();
            player.seekTo(0);
            player.start();
        }
        catch (Exception e) {

        }
    }

    public void resume() {
        if (player == null) {
            selectTrack();
        }
        else {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.start();
            }
        }
    }

    public void resume2() {
        if (isPaused) {
            player.start();
            isPaused = false;
        }
        else if (isPlaying()) {
            player.pause();
            isPaused = true;
        }
    }

    public void updateState(String flag) {
        dao.open();

        if (!dao.getDb().isReadOnly()) {
            int id = cursor.getInt(0);
            dao.update(id, flag);
        }

        dao.close();
    }

    public void destroy() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }
}
