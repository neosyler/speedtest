package com.bestbuy.speedtest;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends ActionBarActivity {

    TextView statusTxtVw;
    Button startButton;
    int BUFFER_BYTES = 1024;
    double BYTE_TO_KILOBIT = 0.008;
    double KILOBIT_TO_MEGABIT = 0.001;
    double MILLISECOND_TO_SECOND = 0.001;

    private final int MSG_UPDATE = 1;
    private final int MSG_COMPLETE = 2;
    private final int MSG_PING = 3;
    private final int MSG_PING_START = 4;

    public static String pingError = null;

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
                case MSG_PING:
                    String pingResults = (String) msg.obj;
                    updateResults(pingResults);
                    break;
                case MSG_PING_START:
                    TextView pingTextVw = (TextView)findViewById(R.id.pingResultsTxtVw);
                    pingTextVw.setText("Pinging...");
                    break;
            }
        }
    };

    private final Runnable runTests = new Runnable() {

        @Override
        public void run() {
            String downloadURL = "http://cdn.speedof.me/sample16384k.html?r=" + System.currentTimeMillis();
            //String downloadURL = "https://wordpress.org/plugins/about/readme.txt";
            //String downloadURL = "https://dl.dropboxusercontent.com/u/9028585/100mb.test";

            long testStartTime = System.currentTimeMillis();
            long connectionLatencyTime = 0;
            long testEndTime = testStartTime;
            long downloadTime = testStartTime;

            InputStream inputStream = null;

            try {
                //test ping
                Message msg0 = Message.obtain(mHandler, MSG_PING_START);
                mHandler.sendMessage(msg0);

                String pingResults = ping("cdn.speedof.me");

                if (pingResults != null) {
                    Log.d("Ping Results:", pingResults);
                    Message msg = Message.obtain(mHandler, MSG_PING, pingResults);
                    mHandler.sendMessage(msg);
                } else {
                    Message msg = Message.obtain(mHandler, MSG_PING, pingError);
                    mHandler.sendMessage(msg);
                }

                //test download
                URL url = new URL(downloadURL);
                URLConnection urlConnection = url.openConnection();
                connectionLatencyTime = System.currentTimeMillis() - testStartTime;

                inputStream = urlConnection.getInputStream();
                byte[] buffer = new byte[BUFFER_BYTES];

                int current = 0;
                int loops = 1;

                while ((current = inputStream.read(buffer)) != -1) {
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

    private void updateResults(String pingResults) {
        TextView pingResultsTxtVw = (TextView)findViewById(R.id.pingResultsTxtVw);
        pingResultsTxtVw.setText(pingResults);
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

    public static int pingHost(String host) throws IOException, InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        Process proc = runtime.exec("ping -c 1 " + host);
        proc.waitFor();

        return proc.exitValue();
    }

    public static String ping(String host) {
        StringBuffer echo = new StringBuffer();
        //Runtime runtime = Runtime.getRuntime();
        Log.v("Network", "About to ping using runtime.exec");

        try {
            Log.d("PING", "Sending PING request to: " + host);
            Process process = new ProcessBuilder()
                    .command("/system/bin/ping", "-c", "8", host)
                    .redirectErrorStream(true)
                    .start();

            //Process proc = runtime.exec("/system/bin/ping -c 8 " + host);
//        proc.waitFor();
//        int exit = proc.exitValue();
//        if (exit == 0) {
            InputStreamReader reader = new InputStreamReader(process.getInputStream());
            BufferedReader buffer = new BufferedReader(reader);
            String line = "";

            while ((line = buffer.readLine()) != null) {
                echo.append(line + "\n");
            }

            return getPingStats(echo.toString());
        } catch (IOException e) {
            pingError = "ping error - 1";
            e.printStackTrace();
            return null;
        }
//        } else if (exit == 1) {
//            pingError = "failed, exit = 1";
//            return null;
//        } else {
//            pingError = "error, exit = 2";
//            return null;
//        }
    }

    public static String getPingStats(String s) {
        Log.d("PING RESULTS", s);

        String ms = "";
        Pattern pattern = Pattern.compile("time (\\d+)ms");
        Matcher m = pattern.matcher(s);
        if(m.find()) {
            ms = m.group(1);
            Log.d("Ping Stat:", m.group(1));
        }

        return ms + " ms";
//        if (s.contains("0% packet loss")) {
//            int start = s.indexOf("/mdev = ");
//            int end = s.indexOf(" ms\n", start);
//            s = s.substring(start + 8, end);
//            String stats[] = s.split("/");
//            return stats[2];
//        } else if (s.contains("100% packet loss")) {
//            pingError = "100% packet loss";
//            return null;
//        } else if (s.contains("% packet loss")) {
//            pingError = "partial packet loss";
//            return null;
//        } else if (s.contains("unknown host")) {
//            pingError = "unknown host";
//            return null;
//        } else {
//            pingError = "unknown error in getPingStats";
//            return null;
//        }
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
