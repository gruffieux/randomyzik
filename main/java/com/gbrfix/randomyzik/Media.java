package com.gbrfix.randomyzik;

/**
 * Created by gab on 15.08.2017.
 */

public class Media {
    private int id;
    private int media_id;
    private String flag;
    private String track_nb;
    private String title;
    private String album;
    private String artist;
    private String album_key;

    public Media() {
        id = 0;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getMediaId() {
        return media_id;
    }

    public void setMediaId(int media_id) {
        this.media_id = media_id;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getTrackNb() {
        return track_nb;
    }

    public void setTrackNb(String track_nb) {
        this.track_nb = track_nb;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbumKey() {
        return album_key;
    }

    public void setAlbumKey(String album_key) {
        this.album_key = album_key;
    }
}
