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
    int listLevel;
    private String listName;
    private String listValue;

    public void setList(int level, String name, String value) {
        listLevel = level;
        listName = name;
        listValue = value;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        activity = (MainActivity)getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        dao = new MediaDAO(getContext(), activity.dbName);

        dao.open();
        SQLiteCursor cursor;
        switch (listLevel) {
            case 2:
                cursor = dao.getAlbumTracks(listValue);
                break;
            case 1:
                cursor = dao.getFromArtist(listValue);
                break;
            default:
                cursor = dao.getAll();
                break;
        }
        int total = cursor.getCount();
        dao.close();

        builder.setMessage(getText(R.string.edit_all_tracks_msg))
                .setTitle(String.format(getString(R.string.edit_all_tracks_title), listName, total))
                .setPositiveButton(getText(R.string.dialog_yes), (dialog, which) -> {
                    dao.open();
                    switch (listLevel) {
                        case 2:
                            dao.updateFlagAlbum(listValue, "unread");
                            break;
                        case 1:
                            dao.updateFlagArtist(listValue, "unread");
                            break;
                        default:
                            dao.updateFlagAll("unread");
                            break;
                    }
                    dao.close();
                    updateUi();
                })
                /*.setNegativeButton(R.string.dialog_album, (dialog, which) -> {
                    dao.open();
                    SQLiteCursor cursor1 = dao.getFromId(id);
                    cursor1.moveToFirst();
                    String albumKey = cursor1.getString(cursor1.getColumnIndex("album_key"));
                    dao.updateFlagAlbum(albumKey, "unread");
                    dao.close();
                    updateUi();
                })*/
                .setNeutralButton(getText(R.string.dialog_no), (dialog, which) -> {
                });

        return builder.create();
    }
}
