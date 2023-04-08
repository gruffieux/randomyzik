package com.gbrfix.randomyzik;

import android.content.Context;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

public class AmpRepository implements Observer<WorkInfo> {
    private AmpXmlParser parser;
    private MediaProvider provider;
    private Context context;
    private MainActivity activity;
    private SimpleDateFormat dateFormat;

    public AmpRepository(AmpXmlParser parser, Context context, MainActivity activity) {
        this.parser = parser;
        this.provider = new MediaProvider(context);
        this.context = context;
        this.activity = activity;
        dateFormat = new SimpleDateFormat("mm:ss");
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
        TextView positionLabel =  activity.findViewById(R.id.position);
        TextView durationLabel =  activity.findViewById(R.id.duration);
        ProgressBar progressBar =  activity.findViewById(R.id.progressBar);

        if (selectId > 0) {
            provider.setSelectId(selectId);
        }

        try {
            Media media = provider.selectTrack();
            String label = MediaProvider.getTrackLabel(media.getTitle(), media.getAlbum(), media.getArtist());
            int color = activity.fetchColor(activity, R.attr.colorPrimaryDark);
            activity.infoMsg(label, color);
            positionLabel.setText(dateFormat.format(new Date(0)));
            durationLabel.setText(dateFormat.format(new Date(media.getDuration()*1000)));
            progressBar.setProgress(0);
            progressBar.setMax(media.getDuration());
            String contentTitle = MediaProvider.getTrackLabel(media.getTitle(), "", "");
            String contentText = MediaProvider.getTrackLabel("", media.getAlbum(), media.getArtist());
            String subText = provider.getSummary();
            WorkRequest playWork = new OneTimeWorkRequest.Builder(PlayWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putInt("mediaId", media.getMediaId())
                                    .putInt("duration", media.getDuration())
                                    .putString("contentTitle", contentTitle)
                                    .putString("contentText", contentText)
                                    .putString("subText", subText)
                                    .build()
                    )
                    .build();
            WorkManager.getInstance(context).enqueueUniqueWork(
                    "play",
                    ExistingWorkPolicy.KEEP,
                    (OneTimeWorkRequest) playWork);
            WorkManager.getInstance(context).getWorkInfoByIdLiveData(playWork.getId()).observeForever(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onChanged(WorkInfo workInfo) {
        TextView positionLabel =  activity.findViewById(R.id.position);
        TextView durationLabel =  activity.findViewById(R.id.duration);
        ProgressBar progressBar =  activity.findViewById(R.id.progressBar);

        if (workInfo != null) {
            Data progress = workInfo.getProgress();
            int position = progress.getInt("progress", 0);
            positionLabel.setText(dateFormat.format(new Date(position*1000)));
            progressBar.setProgress(position);
            if (workInfo.getState().isFinished()) {
                WorkManager.getInstance(context).getWorkInfoByIdLiveData(workInfo.getId()).removeObserver(this);
                if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                    provider.updateState("read");
                    boolean last = provider.getTotalRead() == provider.getTotal() - 1;
                    activity.onTrackRead(last);
                    if (!last) {
                        localplay_addAndPlay(0);
                    }
                }
            }
        }
    }
}
