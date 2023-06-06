package com.gbrfix.randomyzik;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.sqlite.SQLiteCursor;
import android.os.IBinder;
import android.provider.MediaStore;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * Created by gab on 01.10.2017.
 */

@RunWith(AndroidJUnit4.class)
public class PlaylistDbTest {
    final static int TEST_CREATE_LIST = 1;

    int currentTest;
    int mediaTotalExcepted;
    DbService dbService = null;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            DbService.DbBinder binder = (DbService.DbBinder)iBinder;
            dbService = binder.getService();
            final Context context = InstrumentationRegistry.getTargetContext();
            final MediaDAO dao = new MediaDAO(context, MediaDAOTest.TEST_DBNAME);
            dbService.setDbSignalListener(new DbSignal() {
                @Override
                public void onScanStart() {

                }

                @Override
                public void onScanCompleted(final boolean update) {
                    SQLiteCursor cursor;
                    switch (currentTest) {
                        case TEST_CREATE_LIST:
                            dao.open();
                            cursor = dao.getAll();
                            assertEquals(mediaTotalExcepted, cursor.getCount());
                            dao.close();
                            currentTest = 0;
                            break;
                    }

                }

                @Override
                public void onError(String msg) {
                    switch (currentTest) {
                        case TEST_CREATE_LIST:
                    }
                    currentTest = 0;
                }
            });
            dbService.setBound(true);
            Intent intent = new Intent(dbService, DbService.class);
            InstrumentationRegistry.getContext().startService(intent);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            dbService.setBound(false);
        }
    };

    public PlaylistDbTest() {
        Context c = InstrumentationRegistry.getTargetContext();
        mediaTotalExcepted = c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[] {MediaStore.Audio.Media._ID},"is_music=1", null, null).getCount();
        currentTest = 0;
    }

    @Test
    public void createList() {
        currentTest = TEST_CREATE_LIST;

        Context c = InstrumentationRegistry.getTargetContext();

        MediaDAO dao = new MediaDAO(c, MediaDAOTest.TEST_DBNAME);

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
}
