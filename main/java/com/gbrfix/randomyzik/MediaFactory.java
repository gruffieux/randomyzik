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

        if (title != null ? title.isEmpty() : true) {
            File file = new File(path);
            String filename = file.getName();
            int lastIndex = filename.lastIndexOf('.');
            title = lastIndex > 0 ? filename.substring(0, lastIndex) : filename;
        }

        media.setTrackNb(extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));
        media.setTitle(title);
        media.setAlbum(extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
        media.setArtist(extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));

        return media;
    }
}
