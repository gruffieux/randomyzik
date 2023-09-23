package com.gbrfix.randomyzik;

/**
 * Created by gab on 29.08.2017.
 */

interface DbSignal {
    void onError(String msg);
    void onScanStart();
    void onScanProgress(int catalogId, int total);
    void onScanCompleted(int catalogId, boolean update, boolean all);
}
