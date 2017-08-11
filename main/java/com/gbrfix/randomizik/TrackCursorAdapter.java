package com.gbrfix.randomizik;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.view.View;
import android.widget.SimpleCursorAdapter;

/**
 * Created by gab on 11.08.2017.
 */

public class TrackCursorAdapter extends SimpleCursorAdapter {
    public TrackCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
        super(context, layout, c, from, to, 0);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        super.bindView(view, context, cursor);

        if (cursor.getString(2).equals("read")) {
            view.setBackgroundColor(Color.RED);
        }
        else {
            view.setBackgroundColor(Color.WHITE);
        }
    }
}
