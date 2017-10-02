package com.gbrfix.randomyzik;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.sqlite.SQLiteCursor;
import android.os.IBinder;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Created by gab on 01.10.2017.
 */

@RunWith(AndroidJUnit4.class)
public class PlaylistTest {
    int currentTest = 0;
    DbService dbService = null;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            //DAOBase.NAME = "playlist-test.db";
            DbService.DbBinder binder = (DbService.DbBinder)iBinder;
            dbService = binder.getService();
            dbService.setDbSignalListener(new DbSignal() {
                @Override
                public void onScanCompleted(final boolean update) {
                    switch (currentTest) {
                        case 1:
                            MediaDAO dao = new MediaDAO(InstrumentationRegistry.getTargetContext());
                            dao.open();
                            SQLiteCursor cursor = dao.getAll();
                            assertEquals(2231, cursor.getCount());
                            dao.close();
                            break;
                    }
                }

                @Override
                public void onError(String msg) {
                    switch (currentTest) {
                        case 1:
                            assertFalse(true);
                            break;
                    }
                }
            });
            dbService.setBound(true);
            if (currentTest > 0) {
                dbService.start();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            dbService.setBound(false);
        }
    };

    public PlaylistTest() {
        DAOBase.NAME = "playlist-test.db";

        Context c = InstrumentationRegistry.getTargetContext();
        Intent intent = new Intent(c, DbService.class);
        c.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Test
    public void dropList() {
        Context c = InstrumentationRegistry.getTargetContext();
        MediaDAO dao = new MediaDAO(c);

        dao.open();
        dao.dropMedias();
        SQLiteCursor cursor = dao.getAll();
        assertEquals(0, cursor.getCount());
        dao.close();
    }

    @Test
    public void createList() {
        currentTest = 1;
    }
}
