package com.gbrfix.randomyzik;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

/**
 * Created by gab on 14.10.2017.
 */

public class RescanDialogFragment extends DialogFragment {
    private int id;

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MainActivity activity = (MainActivity)getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        String[] entries = prefs.getString("amp_catalog_entries", "").split(";");
        String[] values = prefs.getString("amp_catalog_values", "").split(";");
        String catId = String.valueOf(id);
        String catName = "";
        for (int i = 0; i < entries.length; i++) {
            if (catId.equals(values[i])) {
                catName = entries[i];
                break;
            }
        }

        String finalCatName = catName.isEmpty() ? activity.getString(R.string.auto_item1_folder) : catName;
        builder.setTitle(finalCatName)
                .setMessage(getText(R.string.rescan_msg))
                .setPositiveButton(getText(R.string.dialog_yes), (dialog, which) -> {
                    if (id == 0) {
                        activity.dbService.scanCollection();
                    } else {
                        activity.dbService.scanCatalog(catId, finalCatName);
                    }
                })
                .setNegativeButton(getText(R.string.dialog_no), (dialog, which) -> {

                });

        return builder.create();
    }
}
