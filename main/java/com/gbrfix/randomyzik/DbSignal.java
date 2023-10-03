package com.gbrfix.randomyzik;

/**
 * Created by gab on 29.08.2017.
 */

interface DbSignal {
    void onError(String msg);
    void onScanStart();
    void onScanCompleted(int catalogId, int total, boolean update, boolean all);
}
