package com.gbrfix.randomyzik;

/**
 * Created by gab on 29.08.2017.
 */

interface DbSignal {
    void onError(String msg);
    void onEmpty(String defaultCat, String defaultDbName);
    void onScanStart();
    void onScanCompleted(boolean update);
}
