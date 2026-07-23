package com.gbrfix.randomyzik;

import android.app.Dialog;
import android.database.sqlite.SQLiteCursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

/**
 * Created by gab on 14.10.2017.
 */

public class AllTracksDialogFragment extends SingleTrackDialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        activity = (MainActivity)getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        dao = new MediaDAO(getContext(), activity.dbName);

        dao.open();
        SQLiteCursor cursor = dao.getAll();
        int total = cursor.getCount();
        dao.close();

        builder.setMessage(getText(R.string.edit_all_tracks_msg))
                .setTitle(String.format(getString(R.string.edit_all_tracks_title), total))
                .setPositiveButton(getText(R.string.dialog_yes), (dialog, which) -> {
                    dao.open();
                    dao.updateFlagAll("unread");
                    dao.close();
                    updateUi();
                })
                .setNegativeButton(R.string.dialog_album, (dialog, which) -> {
                    dao.open();
                    SQLiteCursor cursor1 = dao.getFromId(id);
                    cursor1.moveToFirst();
                    String albumKey = cursor1.getString(cursor1.getColumnIndex("album_key"));
                    dao.updateFlagAlbum(albumKey, "unread");
                    dao.close();
                    updateUi();
                })
                .setNeutralButton(getText(R.string.dialog_no), (dialog, which) -> {
                });

        return builder.create();
    }
}
