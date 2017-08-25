package com.gbrfix.randomyzik;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteCursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import java.util.Random;

/**
 * Created by gab on 16.07.2017.
 */
public class MediaController implements MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener, Runnable {
    private MediaPlayer player;
    private AudioManager manager;
    private MediaDAO dao;
    private Context context;
    private UpdateSignal updateSignalListener;
    private int currentId;
    private IntentFilter intentFilter;
    private BecomingNoisyReceiver myNoisyAudioReceiver;

    public MediaController(Context context) {
        player = null;
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        dao = new MediaDAO(context);
        this.context = context;
        currentId = 0;
        intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        myNoisyAudioReceiver = new BecomingNoisyReceiver();

    }

    public int getCurrentId() {
        return currentId;
    }

    public boolean savePlayer(Bundle bundle) {
        if (player == null) {
            return false;
        }

        bundle.putBoolean("isPlaying", player.isPlaying());
        bundle.putInt("currentId", currentId);
        bundle.putInt("currentPosition", player.getCurrentPosition());
        bundle.putInt("duration", player.getDuration());

        return true;
    }

    public void restorePlayer(int id, int position) {
        try {
            dao.open();
            SQLiteCursor cursor = dao.getFromId(id);
            cursor.moveToFirst();
            currentId = id;
            String path = cursor.getString(1);
            player = MediaPlayer.create(context, Uri.parse(path));
            player.seekTo(position);
            player.setOnCompletionListener(this);
            dao.close();
        }
        catch (Exception e) {
            Log.v("Exception", e.getMessage());
        }
    }

    public void setUpdateSignalListener(UpdateSignal listener) {
        updateSignalListener = listener;
    }

    public String getTrackLabel(int id) {
        try {
            dao.open();
            SQLiteCursor cursor = dao.getFromId(id);
            cursor.moveToFirst();
            String label = cursor.getString(4) + " - " + cursor.getString(5) + " - " + cursor.getString(6);
            dao.close();
            return label;
        }
        catch (Exception e) {
            return "";
        }
    }

    public void selectTrack() throws Exception {
        dao.open();

        SQLiteCursor cursor = dao.getFromFlag("unread");
        int total = cursor.getCount();

        dao.close();

        if (total == 0) {
            currentId = 0;
            throw new PlayEndException("Playlist was completed and needs new mp3 loaded");
        }

        if (total > 1) {
            Random random = new Random();
            int pos = random.nextInt(total - 1);
            cursor.moveToPosition(pos);
        }
        else {
            cursor.moveToFirst();
        }

        currentId = cursor.getInt(0);
        String path = cursor.getString(1);

        if (player != null) {
            player.release();
        }

        int result = manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            player = MediaPlayer.create(context, Uri.parse(path));
            //player.seekTo(player.getDuration() - 10000);
            player.start();
            player.setOnCompletionListener(this);
            updateSignalListener.onTrackSelect(currentId, player.getDuration());
        }
    }

    public void rewind() throws Exception {
        if (player != null && player.isPlaying()) {
            player.stop();
            player.prepare();
            player.seekTo(0);
            player.start();
        }
    }

    public void forward() throws Exception {
        if (player != null && player.isPlaying()) {
            player.stop();
            manager.abandonAudioFocus(this);
            selectTrack();
        }
    }

    public void resume() throws Exception {
        if (player == null) {
            selectTrack();
            context.registerReceiver(myNoisyAudioReceiver, intentFilter);
        }
        else {
            if (player.isPlaying()) {
                player.pause();
                manager.abandonAudioFocus(this);
                context.unregisterReceiver(myNoisyAudioReceiver);
            }
            else {
                int result = manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    player.start();
                    context.registerReceiver(myNoisyAudioReceiver, intentFilter);
                }
            }
        }
    }

    public void updateState(String flag) {
        dao.open();

        if (!dao.getDb().isReadOnly()) {
            dao.update(currentId, flag);
        }

        dao.close();
    }

    public void destroy() {
        manager.abandonAudioFocus(this);

        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        updateState("read");
        manager.abandonAudioFocus(this);

        try {
            selectTrack();
            updateSignalListener.onTrackRead(false);
        }
        catch (PlayEndException e) {
            context.unregisterReceiver(myNoisyAudioReceiver);
            updateSignalListener.onTrackRead(true);
        }
        catch (Exception e) {
            Log.v("Exception", e.getMessage());
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (player == null) {
            return;
        }

        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // Your app has been granted audio focus again
                // Raise volume to normal, restart playback if necessary
                player.setVolume(1f, 1f);
                updateSignalListener.onTrackResume(true);
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                updateSignalListener.onTrackResume(true);
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                player.setVolume(1f, 1f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Permanent loss of audio focus
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Pause playback
                updateSignalListener.onTrackResume(false);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lower the volume, keep playing
                player.setVolume(0.5f, 0.5f);
                break;
        }
    }

    @Override
    public void run() {
        if (player == null) {
            return;
        }

        int currentPosition = 0;
        int total = player.getDuration();

        while (currentPosition < total) {
            try {
                Thread.sleep(1000);
                currentPosition = player.getCurrentPosition();
            }
            catch (Exception e) {
                return;
            }
            updateSignalListener.onTrackProgress(currentPosition);
        }
    }

    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                updateSignalListener.onTrackResume(false);
            }
        }
    }
}
