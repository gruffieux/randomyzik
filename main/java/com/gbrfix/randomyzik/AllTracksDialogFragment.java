package com.gbrfix.randomyzik;

import android.app.Dialog;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteCursor;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;

/**
 * Created by gab on 14.10.2017.
 */

public class AllTracksDialogFragment extends SingleTrackDialogFragment {
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
                .setPositiveButton(getText(R.string.dialog_yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dao.open();
                        dao.updateFlagAll("unread");
                        dao.close();
                        updateUi();
                    }
                })
                .setNegativeButton(R.string.dialog_album, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dao.open();
                        SQLiteCursor cursor = dao.getFromId((int)id);
                        cursor.moveToFirst();
                        String albumKey = cursor.getString(cursor.getColumnIndex("album_key"));
                        dao.updateFlagAlbum(albumKey, "unread");
                        dao.close();
                        updateUi();
                    }
                })
                .setNeutralButton(getText(R.string.dialog_no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });

        return builder.create();
    }
}
