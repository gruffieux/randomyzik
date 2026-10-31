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
        assert activity != null;

        try (MediaDAO dao = new MediaDAO(getContext(), dbName)) {
            SQLiteCursor cursor = switch (listLevel) {
                case 2 -> dao.getFromAlbum(listValue, null);
                case 1 -> dao.getFromArtist(listValue);
                default -> dao.getAll();
            };
            int total = cursor.getCount();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setMessage(String.format(getString(R.string.edit_all_tracks_msg), total))
                .setTitle(listName)
                .setPositiveButton(getText(R.string.dialog_yes), (dialog, which) -> {
                    try (MediaDAO dao2 = new MediaDAO(getContext(), dbName)) {
                        switch (listLevel) {
                            case 2:
                                dao2.updateFlagAlbum(listValue, "unread");
                                break;
                            case 1:
                                dao2.updateFlagArtist(listValue, "unread");
                                break;
                            default:
                                dao2.updateFlagAll("unread");
                                break;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    activity.reloadList();
                })
                .setNegativeButton(getText(R.string.dialog_no), (dialog, which) -> {
                });
            return builder.create();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
