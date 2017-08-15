package com.gbrfix.randomizik;

/**
 * Created by gab on 15.08.2017.
 */

public class Media {
    private String path;
    private String flag;
    private String track_nb;
    private String title;
    private String album;
    private String artist;

    public Media(String path, String flag) {
        this.path = path;
        this.flag = flag;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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
}
