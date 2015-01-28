package com.bestbuy.speedtest;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.apache.http.util.ByteArrayBuffer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;


public class MainActivity extends ActionBarActivity {

    TextView statusTxtVw;
    Button startButton;
    int BUFFER_BYTES = 1024;
    double BYTE_TO_KILOBIT = 0.008;
    double KILOBIT_TO_MEGABIT = 0.001;
    double MILLISECOND_TO_SECOND = 0.001;

    private final int MSG_UPDATE = 1;
    private final int MSG_COMPLETE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTxtVw = (TextView)findViewById(R.id.statusTxtVw);
        startButton = (Button)findViewById(R.id.startButton);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startButton.setEnabled(false);
                startButton.setText("Running...");
                statusTxtVw.setText("Please wait, tests are running...");
                new Thread(runTests).start();
            }
        });
    }

    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(final Message msg) {
            switch(msg.what) {
                case MSG_COMPLETE:
                    SpeedTestInfo results1 = (SpeedTestInfo) msg.obj;
                    updateResults(results1.downloadMbps, results1.downloadTimeSeconds, true);
                    break;
                case MSG_UPDATE:
                    SpeedTestInfo results2 = (SpeedTestInfo) msg.obj;
                    updateResults(results2.downloadMbps, results2.downloadTimeSeconds, false);
                    break;
            }
        }
    };

    private final Runnable runTests = new Runnable() {

        @Override
        public void run() {
            String downloadURL = "http://cdn.speedof.me/sample16384k.html?r=0.6121995334979147";
            //String downloadURL = "https://wordpress.org/plugins/about/readme.txt";
            //String downloadURL = "https://dl.dropboxusercontent.com/u/9028585/100mb.test";

            long testStartTime = System.currentTimeMillis();
            long connectionLatencyTime = 0;
            long testEndTime = testStartTime;
            long downloadTime = testStartTime;

            InputStream inputStream = null;

            try {
                URL url = new URL(downloadURL);
                URLConnection urlConnection = url.openConnection();
                connectionLatencyTime = System.currentTimeMillis() - testStartTime;

                inputStream = urlConnection.getInputStream();
                byte[] buffer = new byte[BUFFER_BYTES];

                int current = 0;
                int loops = 1;

                while ((current = inputStream.read(buffer)) != -1) {
                    Log.d("Read:", (1024 * loops)*.001 + " KBs");

                    long updateTime = System.currentTimeMillis();
                    long updateDownloadTime = updateTime - testStartTime;

                    //SEND UPDATED STATUS
                    SpeedTestInfo results = results(testStartTime, connectionLatencyTime, updateTime, updateDownloadTime, loops);
                    Message msg = Message.obtain(mHandler, MSG_UPDATE, results);
                    mHandler.sendMessage(msg);

                    loops += 1;
                }

                testEndTime = System.currentTimeMillis();
                downloadTime = testEndTime - testStartTime;

                SpeedTestInfo results = results(testStartTime, connectionLatencyTime, testEndTime, downloadTime, loops);

                //SEND COMPLETE STATUS
                Message msg = Message.obtain(mHandler, MSG_COMPLETE, results);
                mHandler.sendMessage(msg);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    public SpeedTestInfo results(long startTime, long latencyTime, long endTime, long downloadTime, int loops) {
        SpeedTestInfo info = new SpeedTestInfo();
        info.startTime = startTime;
        info.latencyTime = latencyTime;
        info.endTime = endTime;
        info.downloadTime = downloadTime;
        info.loops = loops;

        //convert to Mbps
        long totalBytes = loops * BUFFER_BYTES;
        double kiloBits = totalBytes * BYTE_TO_KILOBIT;
        double megaBits = kiloBits * KILOBIT_TO_MEGABIT;
        double seconds = downloadTime * MILLISECOND_TO_SECOND;
        double mbps = Math.round(megaBits / seconds);

        //set download time values
        info.downloadMbps = mbps;
        info.downloadTimeSeconds = Math.round(seconds);

        return info;
    }

    public void updateResults(double downloadMbps, double downloadTime, boolean finished) {
        TextView downloadTxtVw = (TextView)findViewById(R.id.downloadTxtVw);
        TextView downloadTimeTxtVw = (TextView)findViewById(R.id.downloadTimeTxtVw);
        TextView latencyTxtVw = (TextView)findViewById(R.id.latencyTxtVw);

        downloadTxtVw.setText(downloadMbps + " Mbps");
        downloadTimeTxtVw.setText(downloadTime + " seconds");

        if (finished) {
            statusTxtVw.setText("Finished.");
            startButton.setText("RESTART");
            startButton.setEnabled(true);
        }
    }

    private static class SpeedTestInfo {
        public long startTime;
        public long latencyTime;
        public long endTime;
        public long downloadTime;
        public int loops;
        public double downloadMbps;
        public double downloadTimeSeconds;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
