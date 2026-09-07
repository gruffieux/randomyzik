package com.gbrfix.randomyzik;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteCursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.net.MalformedURLException;
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
                .inflate(R.layout.item, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        Media media = localDataSet.get(position);

        // Bouton flag de l'élément
        ImageButton flagBtn = holder.itemView.findViewById(R.id.flagBtn);
        flagBtn.setOnClickListener(view -> {
            switch (listLevel) {
                case 3:
                    SingleTrackDialogFragment dialog3 = new SingleTrackDialogFragment();
                    dialog3.setId(media.getId());
                    dialog3.show(activity.getSupportFragmentManager(), "singleTrackFlagEditor");
                    break;
                case 2:
                    AllTracksDialogFragment dialog2 = new AllTracksDialogFragment();
                    dialog2.setList(2, media.getAlbum(), media.getAlbumKey());
                    dialog2.show(activity.getSupportFragmentManager(), "albumTrackFlagEditor");
                    break;
                case 1:
                    AllTracksDialogFragment dialog1 = new AllTracksDialogFragment();
                    dialog1.setList(1, media.getArtist(), media.getArtist());
                    dialog1.show(activity.getSupportFragmentManager(), "artistTrackFlagEditor");
                    break;
                default:
                    changeDb(media.getId());
                    AllTracksDialogFragment dialog = new AllTracksDialogFragment();
                    dialog.setList(0, media.getTitle(), String.valueOf(media.getId()));
                    dialog.show(activity.getSupportFragmentManager(), "allTrackFlagEditor");
                    break;
            }
        });

        // Bouton play de l'élément
        ImageButton playBtn = holder.itemView.findViewById(R.id.playlistBtn);
        playBtn.setOnClickListener(view -> {
            Intent intent = new Intent(activity, MediaPlaybackService.class);
            intent.putExtra("mediaId", media.getId());
            intent.setAction("play");
            activity.startService(intent);
        });

        // Affichage élément
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
                playBtn.setVisibility(View.INVISIBLE);
                break;
            case 2:
                holder.getTitle().setText(media.getAlbum());
                holder.itemView.setAlpha(1f);
                playBtn.setVisibility(View.INVISIBLE);
                break;
            case 1:
                holder.getTitle().setText(media.getArtist());
                holder.itemView.setAlpha(1f);
                playBtn.setVisibility(View.INVISIBLE);
                break;
            default:
                holder.getTitle().setText(media.getTitle());
                holder.itemView.setAlpha(1f);
                playBtn.setVisibility(View.VISIBLE);
                break;
        }

        // Création de liste dynamique
        holder.itemView.setOnClickListener(view -> {
            switch (listLevel) {
                case 3:

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
                    changeDb(media.getId());
                    listLevel = 1;
                    getArtists();
                    break;
            }
        });

        // Bouton de navigation de retour
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

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        String[] entries = prefs.getString("amp_catalog_entries", "").split(";");
        String[] values = prefs.getString("amp_catalog_values", "").split(";");
        for (int i = 0; i < entries.length; i++) {
            Media catalog = new Media();
            catalog.setId(Integer.parseInt(values[i]));
            catalog.setTitle(entries[i]);
            localDataSet.add(catalog);
        }

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

    private void changeDb(int id) {
        if (id == 0) {
            activity.dbName = DAOBase.DEFAULT_NAME;
        } else {
            try {
                activity.dbName = AmpSession.getInstance(activity).dbName();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
