package com.gbrfix.randomyzik;

import android.app.Dialog;
import android.content.Intent;
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

        builder.setMessage(String.format(getString(R.string.edit_all_tracks_msg), total))
                .setTitle(listName)
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
                .setNegativeButton("Play", (dialog, which) -> {
                    Intent intent = new Intent(activity, MediaPlaybackService.class);
                    intent.putExtra("mediaId", Integer.parseInt(listValue));
                    intent.setAction("play");
                    activity.startService(intent);
                })
                .setNeutralButton(getText(R.string.dialog_no), (dialog, which) -> {
                });

        return builder.create();
    }
}
