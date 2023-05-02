package com.gbrfix.randomyzik;

public interface AmpSignal {
    void onSelect(int duration, String title, String album, String artist);
    void onProgress(int state, int position);
    void onComplete(boolean last);
    void onError(String msg);
}
