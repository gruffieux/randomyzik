package com.gbrfix.randomyzik;

import android.content.Context;
import android.database.Cursor;
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

        int id = cursor.getInt(cursor.getColumnIndex("_id"));
        view.setId(id);

        if (cursor.getString(cursor.getColumnIndex("flag")).equals("read")) {
            view.setAlpha(0.5f);
        }
        else {
            view.setAlpha(1f);
        }
    }

    public int findView(int id) {
        for (int i = 0; i < getCount(); i++) {
            if (getItemId(i) == id) {
                return i;
            }
        }

        return -1;
    }
}
