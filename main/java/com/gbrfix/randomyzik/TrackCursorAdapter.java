package com.gbrfix.randomyzik;

import android.content.Context;
import android.database.sqlite.SQLiteCursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * Created by gab on 11.08.2017.
 * TODO: Rename class
 */

public class TrackCursorAdapter extends RecyclerView.Adapter<TrackCursorAdapter.ViewHolder> {
    private final ArrayList<Media> localDataSet;
    private final FragmentManager fm;

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

            nb = view.findViewById(R.id.track_nb);
            title = view.findViewById(R.id.title);
            album = view.findViewById(R.id.album);
            artist = view.findViewById(R.id.artist);
        }

        public TextView getNb() {
            return nb;
        }

        public TextView getTitle() {
            return title;
        }

        public TextView getAlbum() {
            return album;
        }

        public TextView getArtist() {
            return artist;
        }
    }

    public TrackCursorAdapter(FragmentManager fm) {
        localDataSet = new ArrayList<Media>();
        this.fm = fm;
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
        Media media = localDataSet.get(position);
        holder.getNb().setText(media.getTrackNb());
        holder.getTitle().setText(media.getTitle());
        holder.getAlbum().setText(media.getAlbum());
        holder.getArtist().setText(media.getArtist());

        holder.itemView.setId(media.getId());

        if (media.getFlag().equals("read")) {
            holder.itemView.setAlpha(0.5f);
        }
        else {
            holder.itemView.setAlpha(1f);
        }

        // Dialogue d'édition du flag pour une piste
        holder.itemView.setOnClickListener(view -> {
            SingleTrackDialogFragment dialog = new SingleTrackDialogFragment();
            dialog.setId(media.getId());
            dialog.show(fm, "singleTrackFlagEditor");
        });

        // Dialogue d'édition du flag de toutes les pistes
        holder.itemView.setOnLongClickListener(view -> {
            AllTracksDialogFragment dialog = new AllTracksDialogFragment();
            dialog.setId(media.getId());
            dialog.show(fm, "allTrackFlagEditor");
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return localDataSet.size();
    }

    public ArrayList<Media> getList() {
        return localDataSet;
    }

    public int findView(int id) {
        for (int i = 0; i < getItemCount(); i++) {
            if (getItemId(i) == id) {
                return i;
            }
        }

        return -1;
    }

    public void allTracks(Context context, String dbName) {
        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();
        SQLiteCursor cursor = dao.getAllOrdered();
        localDataSet.clear();
        while (cursor.moveToNext()) {
            Media media = new Media();
            media.setId(cursor.getInt(0));
            media.setFlag(cursor.getString(1));
            media.setTrackNb(cursor.getString(2));
            media.setTitle(cursor.getString(4));
            media.setAlbum(cursor.getString(5));
            media.setArtist(cursor.getString(6));
            localDataSet.add(media);
        }
        dao.close();
        notifyDataSetChanged();
    }
}
