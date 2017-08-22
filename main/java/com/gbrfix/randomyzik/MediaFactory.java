package com.gbrfix.randomyzik;

import android.media.MediaMetadataRetriever;

/**
 * Created by gab on 15.08.2017.
 */

public class MediaFactory extends MediaMetadataRetriever {
    public Media createMedia(String path) {
        setDataSource(path);

        Media media = new Media(path, "unread");

        media.setTrackNb(extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));
        media.setTitle(extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
        media.setAlbum(extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
        media.setArtist(extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));

        return media;
    }
}
