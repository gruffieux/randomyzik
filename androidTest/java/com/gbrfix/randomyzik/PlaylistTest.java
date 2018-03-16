package com.gbrfix.randomyzik;

import android.content.Context;
import android.database.sqlite.SQLiteCursor;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

import static junit.framework.Assert.assertTrue;

/**
 * Created by gab on 01.10.2017.
 */

@RunWith(AndroidJUnit4.class)
public class PlaylistTest {
    final static int TEST_PLAY_ALL_TRACKS = 1;
    final static int TEST_PLAY_ALL_ALBUMS = 2;
    final static int TEST_PLAY_LAST_TRACK = 3;
    final static int TEST_PLAY_ENDED_LIST = 4;

    PlaylistActivity activity;

    @Rule
    public ActivityTestRule<PlaylistActivity> activityActivityTestRule = new ActivityTestRule<PlaylistActivity>(PlaylistActivity.class);

    @Before
    public void setUp() throws Exception {
        activity = activityActivityTestRule.getActivity();

        activity.getSupportFragmentManager().beginTransaction();
    }

    public PlaylistTest() {
        DAOBase.NAME = "playlist-test.db";
    }

    @Test
    public void playAllTracks() throws Exception {
        activity.currentTest = TEST_PLAY_ALL_TRACKS;

        Context c = InstrumentationRegistry.getTargetContext();

        MediaDAO dao = new MediaDAO(c);
        dao.open();
        dao.updateFlagAll("unread");
        SQLiteCursor cursor = dao.getUnread();
        activity.trackTotal = cursor.getCount();
        dao.close();

        activity.mediaBrowser.connect();

        while (activity.currentTest != 0) {
        }

        activity.mediaBrowser.disconnect();
    }

    @Test
    public void playAllAlbums() throws Exception {
        activity.currentTest = TEST_PLAY_ALL_ALBUMS;

        Context c = InstrumentationRegistry.getTargetContext();

        MediaDAO dao = new MediaDAO(c);
        dao.open();
        dao.updateFlagAll("unread");
        SQLiteCursor cursor = dao.getUnread();
        activity.trackTotal = cursor.getCount();
        dao.close();

        activity.mediaBrowser.connect();

        while (activity.currentTest != 0) {
        }

        activity.mediaBrowser.disconnect();
    }

    @Test
    public void playLastTrack() throws Exception {
        activity.currentTest = TEST_PLAY_LAST_TRACK;

        Context c = InstrumentationRegistry.getTargetContext();

        MediaDAO dao = new MediaDAO(c);
        dao.open();
        dao.updateFlagAll("read");
        SQLiteCursor cursor = dao.getAll();
        Random random = new Random();
        int total = cursor.getCount();

        if (total > 1) {
            int pos = random.nextInt(total);
            cursor.moveToPosition(pos);
        }
        else if (total > 0) {
            cursor.moveToFirst();
        }
        else {
            throw new Exception("List is empty");
        }

        dao.updateFlag(cursor.getInt(0), "unread");
        activity.trackTotal = 1;
        dao.close();

        activity.mediaBrowser.connect();

        while (activity.currentTest != 0) {
        }

        activity.mediaBrowser.disconnect();
    }

    @Test
    public void playEndedList() throws Exception {
        activity.currentTest = TEST_PLAY_ENDED_LIST;

        Context c = InstrumentationRegistry.getTargetContext();

        MediaDAO dao = new MediaDAO(c);
        dao.open();
        dao.updateFlagAll("read");
        activity.trackTotal = 0;
        dao.close();

        activity.mediaBrowser.connect();

        while (activity.currentTest != 0) {
        }

        activity.mediaBrowser.disconnect();
    }
}
