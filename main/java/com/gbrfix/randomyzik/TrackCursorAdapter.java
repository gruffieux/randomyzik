package com.gbrfix.randomyzik;

import android.database.sqlite.SQLiteCursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

/**
 * Created by gab on 11.08.2017.
 * TODO: Rename class
 */

public class TrackCursorAdapter extends RecyclerView.Adapter<TrackCursorAdapter.ViewHolder> {
    private int listLevel;
    private final ArrayList<Media> localDataSet;
    private String artist, album;
    private final MainActivity activity;

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder)
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;

        public ViewHolder(View view) {
            super(view);

            title = view.findViewById(R.id.title);
        }

        public TextView getTitle() {
            return title;
        }
    }

    public TrackCursorAdapter(MainActivity activity) {
        listLevel = 0;
        localDataSet = new ArrayList<>();
        this.activity = activity;
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
        switch (listLevel) {
            case 3:
                String nb = "";
                if (media.getTrackNb() != null) {
                    nb = media.getTrackNb() + ". ";
                }
                String title = nb + media.getTitle();
                holder.getTitle().setText(title);
                holder.itemView.setId(media.getId());
                if (media.getFlag().equals("read")) {
                    holder.itemView.setAlpha(0.5f);
                }
                else {
                    holder.itemView.setAlpha(1f);
                }
                break;
            case 2:
                holder.getTitle().setText(media.getAlbum());
                holder.itemView.setAlpha(1f);
                break;
            case 1:
                holder.getTitle().setText(media.getArtist());
                holder.itemView.setAlpha(1f);
                break;
            default:
                holder.getTitle().setText(media.getTitle());
                holder.itemView.setAlpha(1f);
                break;
        }

        // Dialogue d'édition du flag pour une piste
        holder.itemView.setOnClickListener(view -> {
            switch (listLevel) {
                case 3:
                    SingleTrackDialogFragment dialog = new SingleTrackDialogFragment();
                    dialog.setId(media.getId());
                    dialog.show(activity.getSupportFragmentManager(), "singleTrackFlagEditor");
                    break;
                case 2:
                    listLevel = 3;
                    getAlbumTracks(media.getAlbumKey());
                    break;
                case 1:
                    listLevel = 2;
                    getAlbums(media.getArtist());
                    break;
                default:
                    listLevel = 1;
                    getArtists();
                    break;
            }
        });

        // Dialogue d'édition du flag de toutes les pistes
        holder.itemView.setOnLongClickListener(view -> {
            switch (listLevel) {
                case 3:
                    AllTracksDialogFragment dialog = new AllTracksDialogFragment();
                    dialog.setId(media.getId());
                    dialog.show(activity.getSupportFragmentManager(), "allTrackFlagEditor");
                    break;
                default:
                    break;
            }
            return true;
        });

        FloatingActionButton navBack = activity.findViewById(R.id.navBack);
        navBack.setOnClickListener(view -> {
            switch (listLevel) {
                case 3:
                    listLevel = 2;
                    getAlbums(artist);
                    break;
                case 2:
                    listLevel = 1;
                    getArtists();
                    break;
                case 1:
                    listLevel = 0;
                    getRoot();
                    break;
                default:
                    break;
            }
        });
    }

    @Override
    public int getItemCount() {
        return localDataSet.size();
    }

    public int findView(int id) {
        for (int i = 0; i < getItemCount(); i++) {
            if (getItemId(i) == id) {
                return i;
            }
        }

        return -1;
    }

    public void getAlbumTracks(String album) {
        this.album = album;
        FloatingActionButton navBack = activity.findViewById(R.id.navBack);
        navBack.show();
        MediaDAO dao = new MediaDAO(activity, activity.dbName);
        dao.open();
        SQLiteCursor cursor = dao.getAlbumTracks(album);
        localDataSet.clear();
        while (cursor.moveToNext()) {
            Media media = new Media();
            media.setId(cursor.getInt(0));
            media.setFlag(cursor.getString(1));
            media.setTrackNb(cursor.getString(2));
            media.setTitle(cursor.getString(3));
            localDataSet.add(media);
        }
        dao.close();
        notifyDataSetChanged();
    }

    public void getAlbums(String artist) {
        this.artist = artist;
        FloatingActionButton navBack = activity.findViewById(R.id.navBack);
        navBack.show();
        MediaDAO dao = new MediaDAO(activity, activity.dbName);
        dao.open();
        SQLiteCursor cursor = dao.getAlbums(artist);
        localDataSet.clear();
        while (cursor.moveToNext()) {
            Media media = new Media();
            media.setAlbum(cursor.getString(0));
            media.setAlbumKey(cursor.getString(1));
            localDataSet.add(media);
        }
        dao.close();
        notifyDataSetChanged();
    }

    public void getArtists() {
        FloatingActionButton navBack = activity.findViewById(R.id.navBack);
        navBack.show();
        MediaDAO dao = new MediaDAO(activity, activity.dbName);
        dao.open();
        SQLiteCursor cursor = dao.getArtists();
        localDataSet.clear();
        while (cursor.moveToNext()) {
            Media media = new Media();
            media.setArtist(cursor.getString(0));
            localDataSet.add(media);
        }
        dao.close();
        notifyDataSetChanged();
    }

    public void getRoot() {
        FloatingActionButton navBack = activity.findViewById(R.id.navBack);
        navBack.hide();
        localDataSet.clear();
        Media musicFolder = new Media();
        musicFolder.setId(0);
        musicFolder.setTitle(activity.getString(R.string.auto_item1_folder));
        localDataSet.add(musicFolder);
        notifyDataSetChanged();
    }

    public void getCurrent() {
        switch (listLevel) {
            case 3:
                getAlbumTracks(album);
                break;
            case 2:
                getAlbums(artist);
                break;
            case 1:
                getArtists();
                break;
            default:
                getRoot();
                break;
        }
    }
}
