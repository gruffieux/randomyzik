package com.gbrfix.randomyzik;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by gab on 11.08.2017.
 */

public class TrackCursorAdapter extends RecyclerView.Adapter<TrackCursorAdapter.ViewHolder> {
    private String[] localDataSet;

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder)
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nb;
        private final TextView title;
        private final TextView album;
        private final TextView artist;

        public ViewHolder(View view) {
            super(view);
            // Define click listener for the ViewHolder's View

            nb = (TextView) view.findViewById(R.id.track_nb);
            title = (TextView) view.findViewById(R.id.title);
            album = (TextView) view.findViewById(R.id.album);
            artist = (TextView) view.findViewById(R.id.artist);
        }

        public TextView getNb() {
            return nb;
        }
    }

    /**
     * Initialize the dataset of the Adapter
     *
     * @param dataSet String[] containing the data to populate views to be used
     * by RecyclerView
     */
    public TrackCursorAdapter(String[] dataSet) {
        localDataSet = dataSet;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.track, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        holder.getNb().setText(localDataSet[position]);
    }

    @Override
    public int getItemCount() {
        return localDataSet.length;
    }

    public int findView(int id) {
        for (int i = 0; i < getItemCount(); i++) {
            if (getItemId(i) == id) {
                return i;
            }
        }

        return -1;
    }
}
