package com.gbrfix.randomyzik;

import android.app.Dialog;
import android.content.DialogInterface;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.os.Bundle;
import android.support.v4.media.session.MediaControllerCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.widget.ListView;

/**
 * Created by gab on 14.10.2017.
 */

public class SingleTrackDialogFragment extends AppCompatDialogFragment {
    protected int id;
    protected MediaDAO dao;
    protected MainActivity activity;

    public void setId(int id) {
        this.id = id;
    }

    private void resetFlag() {
        dao.open();
        dao.updateFlag(id, "unread");
        dao.close();
        updateUi();
    }

    protected void updateUi() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    dao.open();
                    SQLiteCursor cursor = dao.getAllOrdered();
                    ListView listView = activity.findViewById(R.id.playlist);
                    TrackCursorAdapter adapter = (TrackCursorAdapter) listView.getAdapter();
                    adapter.changeCursor(cursor);
                    dao.close();
                } catch (SQLException e) {
                    Log.v("SQLException", e.getMessage());
                }
            }
        });
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        activity = (MainActivity)getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        dao = new MediaDAO(getContext());

        dao.open();
        SQLiteCursor cursor = dao.getFromId(id);
        cursor.moveToFirst();
        String title = cursor.getString(4);
        dao.close();

        builder.setMessage(getText(R.string.edit_single_track_msg))
            .setTitle(title)
            .setPositiveButton(getText(R.string.dialog_yes), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    resetFlag();
                }
            })
            .setNeutralButton(R.string.dialog_yes_play, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    resetFlag();
                    if (activity.ampRemote()) {
                        activity.ampService.selectAndPlay(id);
                    } else {
                        Bundle args = new Bundle();
                        args.putInt("id", id);
                        activity.mediaBrowser.sendCustomAction("selectTrack", args, null);
                        MediaControllerCompat.getMediaController(activity).getTransportControls().play();
                    }
                }
            })
            .setNegativeButton(getText(R.string.dialog_no), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

        return builder.create();
    }
}
