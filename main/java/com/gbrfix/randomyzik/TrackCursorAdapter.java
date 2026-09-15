package com.gbrfix.randomyzik;

import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by gab on 11.08.2017.
 * TODO: A renommer
 */

public class TrackCursorAdapter extends RecyclerView.Adapter<TrackCursorAdapter.ViewHolder> {
    private int listLevel;
    private int rootId;
    private String artist;
    private String album;
    private String dbName;
    private final ArrayList<Media> localDataSet;
    private final MainActivity activity;

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder)
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;

        private final TextView subtitle;

        private final ImageView mediaIcon;

        public ViewHolder(View view) {
            super(view);

            title = view.findViewById(R.id.title);
            subtitle = view.findViewById(R.id.subtitle);
            mediaIcon = view.findViewById(R.id.mediaIcon);
        }

        public TextView getTitle() {
            return title;
        }

        public TextView getSubtitle() {
            return subtitle;
        }

        public ImageView getMediaIcon() {
            return mediaIcon;
        }

        public void loadThumbnail(int mediaId, int rootId, MainActivity fragment) {
            if (rootId == 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId
                    );
                    try {
                        Bitmap thumbnail = fragment.getContentResolver().loadThumbnail(uri, new Size(48, 48), null);
                        mediaIcon.setImageBitmap(thumbnail);
                        mediaIcon.setVisibility(View.VISIBLE);
                    } catch (IOException e) {
                        mediaIcon.setVisibility(View.INVISIBLE);
                    }
                }
            } else {
                AmpSession ampSession = AmpSession.getInstance(fragment);
                ExecutorService executor = Executors.newSingleThreadExecutor();
                try {
                    if (ampSession.hasValidAuth()) {
                        String url = ampSession.get_art_url(mediaId);
                        Glide.with(fragment).load(url).into(mediaIcon);
                        mediaIcon.setVisibility(View.VISIBLE);
                    } else {
                        Handler handler = new Handler(Looper.getMainLooper());
                        executor.execute(() -> {
                            try {
                                ampSession.connect();
                            } catch (Exception e) {
                                mediaIcon.setVisibility(View.INVISIBLE);
                                return;
                            }
                            handler.post(() -> {
                                try {
                                    String url = ampSession.get_art_url(mediaId);
                                    Glide.with(fragment).load(url).into(mediaIcon);
                                    mediaIcon.setVisibility(View.VISIBLE);
                                } catch (Exception e) {
                                    mediaIcon.setVisibility(View.INVISIBLE);
                                }
                            });
                        });
                    }
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
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
                    dialog3.setDbName(dbName);
                    dialog3.show(activity.getSupportFragmentManager(), "singleTrackFlagEditor");
                    break;
                case 2:
                    AllTracksDialogFragment dialog2 = new AllTracksDialogFragment();
                    dialog2.setList(2, media.getAlbum(), media.getAlbumKey());
                    dialog2.setDbName(dbName);
                    dialog2.show(activity.getSupportFragmentManager(), "albumTrackFlagEditor");
                    break;
                case 1:
                    AllTracksDialogFragment dialog1 = new AllTracksDialogFragment();
                    dialog1.setList(1, media.getArtist(), media.getArtist());
                    dialog1.setDbName(dbName);
                    dialog1.show(activity.getSupportFragmentManager(), "artistTrackFlagEditor");
                    break;
                default:
                    changeDb(media.getId());
                    AllTracksDialogFragment dialog = new AllTracksDialogFragment();
                    dialog.setDbName(dbName);
                    dialog.setList(0, media.getTitle(), String.valueOf(media.getId()));
                    dialog.show(activity.getSupportFragmentManager(), "allTrackFlagEditor");
                    break;
            }
        });

        // Bouton play de l'élément
        ImageButton playBtn = holder.itemView.findViewById(R.id.playlistBtn);
        playBtn.setEnabled(activity.mediaBrowser != null);
        playBtn.setOnClickListener(view -> {
            switch (listLevel) {
                case 3:
                    Intent intent3 = new Intent(activity, MediaPlaybackService.class);
                    intent3.putExtra("mediaId", rootId);
                    intent3.putExtra("selectId", media.getId());
                    intent3.setAction("play");
                    activity.startService(intent3);
                    break;
                case 0:
                    Intent intent = new Intent(activity, MediaPlaybackService.class);
                    intent.putExtra("mediaId", media.getId());
                    intent.setAction("play");
                    activity.startService(intent);
                    break;
            }
        });

        // Bouton scan de l'élément
        ImageButton rescanBtn = holder.itemView.findViewById(R.id.rescanBtn);
        rescanBtn.setEnabled(activity.dbService != null);
        rescanBtn.setOnClickListener(view -> {
            if (media.getId() == 0) {
                activity.dbService.scanCollection();
            } else {
                activity.dbService.scanCatalog(String.valueOf(media.getId()), media.getTitle());
            }
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
                holder.getSubtitle().setText(MediaProvider.getTrackLabel("", media.getAlbum(), media.getArtist()));
                holder.itemView.setId(media.getId());
                if (media.getFlag().equals("read")) {
                    holder.itemView.setAlpha(0.5f);
                    playBtn.setVisibility(View.INVISIBLE);
                }
                else {
                    holder.itemView.setAlpha(1f);
                    playBtn.setVisibility(View.VISIBLE);
                }
                rescanBtn.setVisibility(View.INVISIBLE);
                holder.getMediaIcon().setVisibility(View.INVISIBLE);
                break;
            case 2:
                holder.getTitle().setText(media.getAlbum());
                holder.getSubtitle().setText(activity.getString(R.string.switch_mode_album));
                MediaDAO dao2 = new MediaDAO(activity, dbName);
                dao2.open();
                SQLiteCursor cursor2 = dao2.getFlagFromAlbum("unread", media.getAlbumKey());
                if (cursor2.getCount() > 0) {
                    holder.itemView.setAlpha(1f);
                } else {
                    holder.itemView.setAlpha(0.5f);
                }
                dao2.close();
                playBtn.setVisibility(View.INVISIBLE);
                rescanBtn.setVisibility(View.INVISIBLE);
                holder.loadThumbnail(media.getMediaId(), rootId, activity);
                break;
            case 1:
                holder.getTitle().setText(media.getArtist());
                holder.getSubtitle().setText(activity.getString(R.string.item_artist));
                MediaDAO dao1 = new MediaDAO(activity, dbName);
                dao1.open();
                SQLiteCursor cursor1 = dao1.getFlagFromArtist("unread", media.getArtist());
                if (cursor1.getCount() > 0) {
                    holder.itemView.setAlpha(1f);
                } else {
                    holder.itemView.setAlpha(0.5f);
                }dao1.close();
                playBtn.setVisibility(View.INVISIBLE);
                rescanBtn.setVisibility(View.INVISIBLE);
                holder.loadThumbnail(media.getMediaId(), rootId, activity);
                break;
            default:
                holder.getTitle().setText(media.getTitle());
                if (media.getId() == 0) {
                    holder.getSubtitle().setText(activity.getString(R.string.auto_item1));
                } else {
                    holder.getSubtitle().setText(activity.getString(R.string.item_catalog));
                }
                holder.itemView.setAlpha(1f);
                playBtn.setVisibility(View.VISIBLE);
                rescanBtn.setVisibility(View.VISIBLE);
                holder.getMediaIcon().setVisibility(View.INVISIBLE);
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
                    rootId = media.getId();
                    changeDb(rootId);
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
        MediaDAO dao = new MediaDAO(activity, dbName);
        dao.open();
        SQLiteCursor cursor = dao.getAlbumTracks(album);
        localDataSet.clear();
        while (cursor.moveToNext()) {
            Media media = new Media();
            media.setId(cursor.getInt(0));
            media.setFlag(cursor.getString(1));
            media.setTrackNb(cursor.getString(2));
            media.setTitle(cursor.getString(3));
            media.setAlbum(cursor.getString(4));
            media.setArtist(cursor.getString(5));
            localDataSet.add(media);
        }
        dao.close();
        notifyDataSetChanged();
    }

    public void getAlbums(String artist) {
        this.artist = artist;
        FloatingActionButton navBack = activity.findViewById(R.id.navBack);
        navBack.show();
        MediaDAO dao = new MediaDAO(activity, dbName);
        dao.open();
        SQLiteCursor cursor = dao.getAlbums(artist);
        localDataSet.clear();
        while (cursor.moveToNext()) {
            Media media = new Media();
            media.setAlbum(cursor.getString(0));
            media.setAlbumKey(cursor.getString(1));
            media.setMediaId(cursor.getInt(2));
            localDataSet.add(media);
        }
        dao.close();
        notifyDataSetChanged();
    }

    public void getArtists() {
        FloatingActionButton navBack = activity.findViewById(R.id.navBack);
        navBack.show();
        MediaDAO dao = new MediaDAO(activity, dbName);
        dao.open();
        SQLiteCursor cursor = dao.getArtists();
        localDataSet.clear();
        while (cursor.moveToNext()) {
            Media media = new Media();
            media.setArtist(cursor.getString(0));
            media.setMediaId(cursor.getInt(1));
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
        if (entries.length > 0 && !entries[0].isEmpty()) {
            for (int i = 0; i < entries.length; i++) {
                Media catalog = new Media();
                catalog.setId(Integer.parseInt(values[i]));
                catalog.setTitle(entries[i]);
                localDataSet.add(catalog);
            }
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
            dbName = DAOBase.DEFAULT_NAME;
        } else {
            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                String server = prefs.getString("amp_server", "");
                dbName = AmpRepository.dbName(server, String.valueOf(id));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }
    public String getAlbum() {
        return album;
    }

    public String getArtist() {
        return artist;
    }

    public String getDbName() {
        return dbName;
    }

    public int getListLevel() {
        return listLevel;
    }

    public int getRootId() {
        return rootId;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public void setListLevel(int listLevel) {
        this.listLevel = listLevel;
    }

    public void setRootId(int rootId) {
        this.rootId = rootId;
    }
}
