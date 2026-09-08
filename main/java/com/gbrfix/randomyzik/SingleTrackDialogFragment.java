package com.gbrfix.randomyzik;

import android.app.Dialog;
import android.database.sqlite.SQLiteCursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by gab on 14.10.2017.
 */

public class SingleTrackDialogFragment extends AppCompatDialogFragment {
    protected int id;
    protected MediaDAO dao;
    protected String dbName;
    protected MainActivity activity;

    public void setId(int id) {
        this.id = id;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    private void resetFlag() {
        dao.open();
        dao.updateFlag(id, "unread");
        dao.close();
        updateUi();
    }

    protected void updateUi() {
        activity.runOnUiThread(() -> {
            RecyclerView listView = activity.findViewById(R.id.playlist);
            TrackCursorAdapter adapter = (TrackCursorAdapter) listView.getAdapter();
            if (adapter != null) {
                adapter.getCurrent();
            }
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        activity = (MainActivity)getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        dao = new MediaDAO(getContext(), dbName);

        dao.open();
        SQLiteCursor cursor = dao.getFromId(id);
        cursor.moveToFirst();
        String title = cursor.getString(4);
        dao.close();

        builder.setMessage(getText(R.string.edit_single_track_msg))
            .setTitle(title)
            .setPositiveButton(getText(R.string.dialog_yes), (dialog, which) -> resetFlag())
            /*.setPositiveButton(R.string.dialog_yes_play, (dialog, which) -> {
                resetFlag();
                Bundle args = new Bundle();
                args.putInt("id", id);
                activity.currentId = id;
                activity.mediaBrowser.sendCustomAction("singleTrack", args, null);
            })*/
            .setNegativeButton(getText(R.string.dialog_no), (dialog, which) -> {
            });

        return builder.create();
    }
}
