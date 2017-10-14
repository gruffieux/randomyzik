package com.gbrfix.randomyzik;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.util.Log;
import android.widget.ListView;

/**
 * Created by gab on 14.10.2017.
 */

public class SingleTrackDialogFragment extends AppCompatDialogFragment {
    int id;
    String label;

    public void setId(int id) {
        this.id = id;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        builder.setMessage(String.format(getString(R.string.edit_single_track_msg), label))
            .setTitle(getText(R.string.edit_single_track_title))
            .setPositiveButton(getText(R.string.dialog_yes), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final MediaDAO dao = new MediaDAO(getContext());
                    dao.open();
                    dao.updateFlag(id, "unread");
                    dao.close();
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
            })
            .setNegativeButton(getText(R.string.dialog_no), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

        return builder.create();
    }
}
