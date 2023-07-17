package com.gbrfix.randomyzik;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class RescanDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MainActivity activity = (MainActivity)getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        builder.setTitle(getText(R.string.rescan_title))
                .setMessage(getText(R.string.rescan_msg))
                .setNegativeButton(getText(R.string.rescan_current), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                        String catalog = prefs.getString("amp_catalog", "0");
                        activity.dbService.scan(false, catalog);
                    }
                })
                .setNeutralButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setPositiveButton(getText(R.string.rescan_all), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.dbService.scan(false,"0");
                    }
                });

        return builder.create();
    }
}
