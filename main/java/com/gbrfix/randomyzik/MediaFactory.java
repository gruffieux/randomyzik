package com.gbrfix.randomyzik;

import android.media.MediaMetadataRetriever;

import java.io.File;

/**
 * Created by gab on 15.08.2017.
 */

public class MediaFactory extends MediaMetadataRetriever {
    public Media createMedia(String path) {
        setDataSource(path);

        Media media = new Media(path, "unread");
        String title = extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        String artist = extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);

        // Si pas trouvé de titre
        if (title != null ? title.isEmpty() : true) {
            File file = new File(path);
            String filename = file.getName();
            int lastIndex = filename.lastIndexOf('.');
            title = lastIndex > 0 ? filename.substring(0, lastIndex) : filename;
        }

        // Si pas trouvé d'artiste d'album
        if (artist != null ? artist.isEmpty() : true) {
            artist = extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        }

        media.setTrackNb(extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));
        media.setTitle(title);
        media.setAlbum(extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
        media.setArtist(artist);

        return media;
    }
}
