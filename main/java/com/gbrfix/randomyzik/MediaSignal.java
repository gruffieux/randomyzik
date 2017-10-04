package com.gbrfix.randomyzik;

/**
 * Created by gab on 09.08.2017.
 */

public interface MediaSignal {
    void onTrackRead(boolean last);
    void onTrackResume(boolean allowed);
    void onTrackSelect(int id, int duration, int total, int totalRead);
    void onTrackProgress(int position);
}
