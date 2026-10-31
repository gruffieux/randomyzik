package com.gbrfix.randomyzik;

import android.app.Dialog;
import android.database.sqlite.SQLiteCursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

/**
 * Created by gab on 14.10.2017.
 */

public class SingleTrackDialogFragment extends AppCompatDialogFragment {
    protected int id;
    protected String dbName;
    protected MainActivity activity;

    public void setId(int id) {
        this.id = id;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        activity = (MainActivity)getActivity();
        assert activity != null;

        try (MediaDAO dao = new MediaDAO(getContext(), dbName)) {
            SQLiteCursor cursor = dao.getFromId(id);
            cursor.moveToFirst();
            String title = cursor.getString(4);
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setMessage(getText(R.string.edit_single_track_msg))
                    .setTitle(title)
                    .setPositiveButton(getText(R.string.dialog_yes), (dialog, which) -> {
                        try (MediaDAO dao2 = new MediaDAO(getContext(), dbName)) {
                            dao2.updateFlag(id, "unread");
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
