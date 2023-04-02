package com.gbrfix.randomyzik;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class AmpWorker extends Worker {
    public AmpWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String spec = getInputData().getString("URL_SPEC");
        String parseAuth = getInputData().getString("PARSE_AUTH");
        String auth = getInputData().getString("auth");
        int duration = getInputData().getInt("duration", 0);
        try {
            if (auth != null) {
                spec += "&auth=" + auth;
            }
            URL url = new URL(spec);
            HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
            if (parseAuth != null) {
                AmpXmlParser parser = new AmpXmlParser();
                auth = parser.parseText(conn.getInputStream(), parseAuth);
            }
            Data output = new Data.Builder()
                    .putString("auth", auth)
                    .putString("msg", conn.getResponseMessage())
                    .build();
            conn.disconnect();
            if (duration > 0) {
                int counter = 0;
                //duration = 10; // Testes
                while (counter < duration) {
                    Thread.sleep(1000);
                    counter++;
                }
            }
            return Result.success(output);
        } catch (IOException | XmlPullParserException | InterruptedException e) {
            Data output = new Data.Builder()
                    .putString("msg", e.getMessage())
                    .build();
            return Result.failure(output);
        }
    }
}
