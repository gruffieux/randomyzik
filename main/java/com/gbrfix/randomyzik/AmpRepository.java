package com.gbrfix.randomyzik;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;

public class AmpRepository implements LifecycleOwner {
    private AmpXmlParser parser;
    private MediaProvider provider;
    private Context context;
    private MainActivity activity;

    public MediaProvider getProvider() {
        return provider;
    }

    public AmpRepository(AmpXmlParser parser, Context context, MainActivity activity) {
        this.parser = parser;
        this.provider = new MediaProvider(context);
        this.context = context;
        this.activity = activity;
    }

    public String handshake() throws IOException, XmlPullParserException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=handshake&auth=a47b0979a17207e084f8b25f9b0a4440");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String authToken = parser.parseText(conn.getInputStream(), "auth");
        conn.disconnect();
        return authToken;
    }

    public ArrayList advanced_search(String auth) throws IOException, XmlPullParserException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=advanced_search&auth="+auth+"&operator=or&type=song&offset=0&limit=100&random=0&rule_1=Catalog&rule_1_operator=4&rule_1_input=gab");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        ArrayList list = (ArrayList<Media>)parser.parseSongs(conn.getInputStream());
        conn.disconnect();
        return list;
    }

    public String localplay_add(String auth, int oid) throws IOException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&auth="+auth+"&command=add&type=song&oid="+oid+"&clear=1");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public String localplay_pause(String auth) throws IOException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&auth="+auth+"&command=pause");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public String localplay_play(String auth) throws IOException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&auth="+auth+"&command=play");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public String localplay_stop(String auth) throws IOException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&auth="+auth+"&command=stop");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public void localplay_addAndPlay(int selectId) {
        if (selectId > 0) {
            provider.setSelectId(selectId);
        }
        try {
            Media media = provider.selectTrack();
            WorkRequest playWork = new OneTimeWorkRequest.Builder(PlayWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putInt("mediaId", media.getMediaId())
                                    .putInt("duration", media.getDuration())
                                    .build()
                    )
                    .build();
            WorkManager.getInstance(context).enqueueUniqueWork(
                    "play",
                    ExistingWorkPolicy.KEEP,
                    (OneTimeWorkRequest) playWork);
            WorkManager.getInstance(context).getWorkInfoByIdLiveData(playWork.getId())
                    .observeForever(new Observer<WorkInfo>() {
                        @Override
                        public void onChanged(WorkInfo workInfo) {
                            if (workInfo != null) {
                                Data progress = workInfo.getProgress();
                                int value = progress.getInt("progress", 0);
                                // Do something with progress
                                if (workInfo.getState().isFinished() && workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                                    provider.updateState("read");
                                    boolean last = provider.getTotalRead() == provider.getTotal() - 1;
                                    activity.onTrackRead(last);
                                    if (!last) {
                                        localplay_addAndPlay(0);
                                    }
                                }
                            }
                        }
                    });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void localplay_addAndPlay_old(int selectId) {
        try {
            if (selectId > 0) {
                provider.setSelectId(selectId);
            }
            Media media = provider.selectTrack();
            WorkRequest handshakeWork = new OneTimeWorkRequest.Builder(AmpWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putString("URL_SPEC", "https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=handshake&auth=a47b0979a17207e084f8b25f9b0a4440")
                                    .putString("PARSE_AUTH", "auth")
                                    .build()
                    ).build();
            WorkRequest stopWork = new OneTimeWorkRequest.Builder(AmpWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putString("URL_SPEC", "https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&command=stop")
                                    .build()
                    ).build();
            WorkRequest addWork = new OneTimeWorkRequest.Builder(AmpWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putString("URL_SPEC", "https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&command=add&type=song&oid="+media.getMediaId()+"&clear=1")
                                    .build()
                    ).build();
            WorkRequest playWork = new OneTimeWorkRequest.Builder(AmpWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putString("URL_SPEC", "https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&command=play")
                                    .putInt("duration", media.getDuration())
                                    .build()
                    )
                    .build();
            WorkManager.getInstance(context)
                    .beginWith((OneTimeWorkRequest)handshakeWork)
                    .then(Arrays.asList((OneTimeWorkRequest)stopWork, (OneTimeWorkRequest)addWork))
                    .then((OneTimeWorkRequest)playWork)
                    .enqueue();
            WorkManager.getInstance(context).getWorkInfoByIdLiveData(playWork.getId())
                .observe(this, info -> {
                    if (info != null && info.getState() == WorkInfo.State.SUCCEEDED) {
                        provider.updateState("read");
                        boolean last = provider.getTotalRead() == provider.getTotal() - 1;
                        activity.onTrackRead(last);
                        if (!last) {
                            localplay_addAndPlay(0);
                        }
                    }
                });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return activity.getLifecycle();
    }
}
