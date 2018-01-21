package com.gbrfix.randomyzik;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.sqlite.SQLiteCursor;
import android.os.IBinder;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Created by gab on 01.10.2017.
 */

@RunWith(AndroidJUnit4.class)
public class PlaylistDbTest {
    final static int TEST_CREATE_LIST = 1;
    final static int TEST_CHANGE_FILES = 2;

    int currentTest;
    DbService dbService = null;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            DbService.DbBinder binder = (DbService.DbBinder)iBinder;
            dbService = binder.getService();
            final int expected = 717;
            final Context context = InstrumentationRegistry.getTargetContext();
            final MediaDAO dao = new MediaDAO(context);
            dbService.setDbSignalListener(new DbSignal() {
                @Override
                public void onScanCompleted(final boolean update) {
                    SQLiteCursor cursor;
                    switch (currentTest) {
                        case TEST_CREATE_LIST:
                            dao.open();
                            cursor = dao.getAll();
                            assertEquals(expected, cursor.getCount());
                            dao.close();
                            currentTest = 0;
                            break;
                        case TEST_CHANGE_FILES:
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Thread.currentThread().sleep(30000); // Copiez, déplacez, supprimez des fichiers pendant le temps de pause.
                                        dao.open();
                                        SQLiteCursor cursor = dao.getAll();
                                        assertEquals(expected, cursor.getCount()); // Modifiez en fonction de ce que vous voulez obtenir
                                        dao.close();
                                        currentTest = 0;
                                    }
                                    catch (Exception e) {
                                        assertFalse(true);
                                    }
                                }
                            }).start();
                            break;
                    }

                }

                @Override
                public void onError(String msg) {
                    switch (currentTest) {
                        case TEST_CREATE_LIST:
                        case TEST_CHANGE_FILES:
                            assertFalse(true);
                            break;
                    }
                    currentTest = 0;
                }
            });
            dbService.setBound(true);
            switch (currentTest) {
                case TEST_CREATE_LIST:
                case TEST_CHANGE_FILES:
                    dbService.start();
                    break;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            dbService.setBound(false);
        }
    };

    public PlaylistDbTest() {
        currentTest = 0;
        DAOBase.NAME = "playlist-test.db";
    }

    @Test
    public void createList() {
        currentTest = TEST_CREATE_LIST;

        Context c = InstrumentationRegistry.getTargetContext();

        MediaDAO dao = new MediaDAO(c);

        dao.open();
        dao.getDb().delete("medias", null, null);
        SQLiteCursor cursor = dao.getAll();
        assertEquals(0, cursor.getCount());
        dao.close();

        Intent intent = new Intent(c, DbService.class);
        c.bindService(intent, connection, Context.BIND_AUTO_CREATE);

        while (currentTest != 0) {
        }

        Log.v("createList", "end");
    }

    @Test
    public void changeFiles() {
        currentTest = TEST_CHANGE_FILES;

        Context c = InstrumentationRegistry.getTargetContext();

        Intent intent = new Intent(c, DbService.class);
        c.bindService(intent, connection, Context.BIND_AUTO_CREATE);

        while (currentTest != 0) {
        }

        Log.v("changeFiles", "end");
    }
}
