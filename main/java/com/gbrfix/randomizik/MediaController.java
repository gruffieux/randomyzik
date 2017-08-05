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
    protected AudioManager.OnAudioFocusChangeListener focusChangeListener;
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

    public void setFocusChangeListener(AudioManager.OnAudioFocusChangeListener fChangeListener) {
        focusChangeListener = fChangeListener;
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
            int result = manager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                player = MediaPlayer.create(context, Uri.parse(path));
                //player.seekTo(player.getDuration() - 10000);
                player.start();

                // On traite lorsque la lecture est complétée
                player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    public void onCompletion(MediaPlayer mp) {
                        updateState("read");
                        manager.abandonAudioFocus(focusChangeListener);
                        selectTrack();
                    }
                });
            }

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

    public void forward() {
        manager.abandonAudioFocus(focusChangeListener);
        selectTrack();
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
