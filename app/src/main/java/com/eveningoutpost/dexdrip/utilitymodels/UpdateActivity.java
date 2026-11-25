package com.eveningoutpost.dexdrip.utilitymodels;

// jamorham

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import androidx.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.eveningoutpost.dexdrip.BaseAppCompatActivity;
import com.eveningoutpost.dexdrip.BuildConfig;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.xdrip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.val;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.eveningoutpost.dexdrip.alert.UpdateAvailable.XDRIP_UPDATE_NOTIFICATION_PENDING;
import static com.eveningoutpost.dexdrip.utilitymodels.Constants.XDRIP_UPDATE_NOTIFICATION_ID;
import static com.eveningoutpost.dexdrip.utilitymodels.PersistentStore.incrementLong;

public class UpdateActivity extends BaseAppCompatActivity {

    public static final String AUTO_UPDATE_PREFS_NAME = "auto_update_download";
    private static final String useInternalDownloaderPrefsName = "use_internal_downloader";
    private static final String last_update_check_time = "last_update_check_time";
    private static final String TAG = "jamorham update";
    private static final String UP_LOAD_BALANCER = "UP_LOAD_BALANCER";
    private static OkHttpClient httpClient = null;
    public static long last_check_time = 0;
    private static SharedPreferences prefs;
    private static long versionnumber = 0;
    private static long newversion = 0;
    private static String lastDigest = "";
    private final static int MY_PERMISSIONS_REQUEST_STORAGE_DOWNLOAD = 105;
    private static boolean downloading = false;
    private static final boolean debug = false;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView updateMessageText;
    private ScrollView mScrollView;
    private File dest_file;
    private static String DOWNLOAD_URL = "";
    private static int FILE_SIZE = -1;
    private static String MESSAGE = "";
    private static String CHECKSUM = "";

    public static void checkForAnUpdate(final Context context) {
        checkForAnUpdate(context, false);
    }

    public static void checkForAnUpdate(final Context context, final boolean fromUi) {
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if ((last_check_time != -1) && (!prefs.getBoolean(AUTO_UPDATE_PREFS_NAME, true))) return;
        String channel = prefs.getString("update_channel", "beta");
        if (last_check_time == 0)
            last_check_time = prefs.getLong(last_update_check_time, 0);
            long checkFrequency = 3600_000;
     if (((JoH.tsl() - last_check_time) > checkFrequency) || (debug) || fromUi) {
         last_check_time = JoH.tsl();
         prefs.edit().putLong(last_update_check_time, last_check_time).apply();

            Log.i(TAG, "Checking for a software update, channel: " + channel);

            String subversion = "";
            if (!context.getString(R.string.app_name).equals("xDrip")) {
                subversion = context.getString(R.string.app_name).replaceAll("[^a-zA-Z0-9]", "");
                Log.d(TAG, "Using subversion: " + subversion);
            }

            final String CHECK_URL = "https://api.github.com/repos/lz2jvc/xDrip/releases/latest";

            DOWNLOAD_URL = "";
            newversion = 0;

            new Thread(() -> {
                try {
                    if (httpClient == null) {
                        httpClient = OkHttpWrapper.getClient().newBuilder()
                                .connectTimeout(30, TimeUnit.SECONDS)
                                .readTimeout(60, TimeUnit.SECONDS)
                                .writeTimeout(20, TimeUnit.SECONDS)
                                .build();
                    }
                    getVersionInformation(context);
                    if (versionnumber == 0) return;

                    String locale = "";
                    try {
                        locale = Locale.getDefault().toString();
                        if (locale == null) locale = "";
                    } catch (Exception e) {
                        // do nothing
                    }
                       final Request request = new Request.Builder()
                         .header("User-Agent", "xDrip2-Android-Updater")

                                .header("Accept", "application/vnd.github.v3+json")
                                .header("Connection", "close")
                                .url(CHECK_URL)
                                .build();

                        final Response response = httpClient.newCall(request).execute();
                        if (response.isSuccessful() && response.body() != null) {
                            final String jsonStr = response.body().string();
                            final org.json.JSONObject json = new org.json.JSONObject(jsonStr);
                            final String publishedAt = json.optString("published_at", "");
                            if (publishedAt.length() >= 19) {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
                            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                            java.util.Date parsedDate = sdf.parse(publishedAt.substring(0, 19));
                            if (parsedDate != null) {
                                newversion = parsedDate.getTime() / 1000L;
                            }
                        }

                            final org.json.JSONArray assets = json.optJSONArray("assets");
                            if (assets != null) {
                                for (int i = 0; i < assets.length(); i++) {
                                    final org.json.JSONObject asset = assets.getJSONObject(i);
                                    final String name = asset.optString("name", "");
                                    if (name.endsWith(".apk")) {
                                        DOWNLOAD_URL = asset.optString("browser_download_url", "");
                                        FILE_SIZE = asset.optInt("size", -1);
                                        break;
                                    }
                                }
                            }
                            MESSAGE = json.optString("name", "New xDrip 2 update available");
                            CHECKSUM = "";

                            if ((newversion > versionnumber) && DOWNLOAD_URL.startsWith("http")) {
                                Log.i(TAG, "Notifying user of new update available our version: " + versionnumber + " new: " + newversion);
                                final Intent intent = new Intent(context, UpdateActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);
                                if (!fromUi) {
                                    PersistentStore.setBoolean(XDRIP_UPDATE_NOTIFICATION_PENDING, true);
                                }
                            } else {
                                Log.i(TAG, "Our current version is the most recent: " + versionnumber + " vs " + newversion);
                                if (fromUi) {
                                    JoH.static_toast_long(xdrip.gs(R.string.current_version_is_up_to_date));
                                }
                            }
                            Log.i(TAG, "Success getting latest software version");
                        } else {
                            Log.d(TAG, "Failure getting update URL data: code: " + response.code());
                        }
                } catch (Exception e) {
                    UserError.Log.e(TAG, "Exception in reading http update version " + e.toString());
                }
                httpClient = null; // for GC
            }).start();
        }
    }
 public static void forceUpdateCheckNow() {
     JoH.static_toast_long(xdrip.gs(R.string.checking_for_update));
     UpdateActivity.last_check_time = -2;
     UpdateActivity.checkForAnUpdate(xdrip.getAppContext(), true);
 }
    public static boolean testAndSetNightly(final boolean set) {
        val update_channel = "update_channel";
        val nightly_channel = "nightly";
        val nightly = Pref.getString(update_channel, "").matches(nightly_channel);
        if (!nightly && set) {
            Pref.setString(update_channel, nightly_channel);
            UpdateActivity.forceUpdateCheckNow();
        }
        return nightly;
    }

    private static String getDownloadFolder() {
        return Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).toString();

    }

    private static void getVersionInformation(Context context) {
        // try {
        if (versionnumber == 0) {
        versionnumber = BuildConfig.buildTimestamp / 1000L;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        setContentView(R.layout.activity_update);
        JoH.fixActionBar(this);

        progressText = (TextView) findViewById(R.id.progresstext);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressText.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.INVISIBLE);
        mScrollView = (ScrollView) findViewById(R.id.updateScrollView);
        updateMessageText = (TextView) findViewById(R.id.updatemessage);

        Switch autoUpdateSwitch = (Switch) findViewById(R.id.autoupdate);
        autoUpdateSwitch.setChecked(prefs.getBoolean(AUTO_UPDATE_PREFS_NAME, true));
        autoUpdateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(AUTO_UPDATE_PREFS_NAME, isChecked).commit();
                Log.d(TAG, "Auto Updates IsChecked:" + isChecked);
            }
        });

        CheckBox useInternalDownloader = (CheckBox) findViewById(R.id.internaldownloadercheckBox);
        useInternalDownloader.setChecked(prefs.getBoolean(useInternalDownloaderPrefsName, true));
        useInternalDownloader.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(useInternalDownloaderPrefsName, isChecked).commit();
                Log.d(TAG, "Use internal downloader IsChecked:" + isChecked);
            }
        });

        TextView detail = (TextView) findViewById(R.id.updatedetail);
        detail.setText(getString(R.string.new_version_date_colon) + JoH.dateTimeText(newversion * 1000L) + "\n" + getString(R.string.old_version_date_colon) + JoH.dateTimeText(versionnumber * 1000L));
        TextView channel = (TextView) findViewById(R.id.update_channel);
        channel.setText(getString(R.string.update_channel_colon_space) + JoH.ucFirst(prefs.getString("update_channel", "beta")));

        updateMessageText.setText(MESSAGE);
    }

    public void closeActivity(View myview) {
        downloading = false;
        finish();
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_STORAGE_DOWNLOAD);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_STORAGE_DOWNLOAD) {
            if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                downloadNow(null);
            } else {
                JoH.static_toast_long(this, "Cannot download without storage permission");
            }
        }
    }

    public void downloadNow(View myview) {
        if (DOWNLOAD_URL.length() > 0) {
            if (prefs.getBoolean(useInternalDownloaderPrefsName, true)) {
                if (checkPermissions()) {
                    if (downloading) {
                        JoH.static_toast_long(this, "Already downloading!");
                    } else {
                        downloading = true;
                        JoH.static_toast_long(this, "Attempting background download...");
                        mScrollView.post(new Runnable() {
                            public void run() {
                                mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                        new AsyncDownloader().executeOnExecutor(xdrip.executor);

                    }
                } else {
                    JoH.static_toast_long(this, "Need permission to download file");
                }
            } else {
                viewIntentDownload(DOWNLOAD_URL);
                finish();
            }

        } else {
            Log.e(TAG, "Download button pressed but no download URL");
        }

        PersistentStore.setBoolean(XDRIP_UPDATE_NOTIFICATION_PENDING, false); // clear notification trigger
        JoH.cancelNotification(XDRIP_UPDATE_NOTIFICATION_ID);
    }

    private void viewIntentDownload(final String DOWNLOAD_URL) {
        final Intent downloadActivity = new Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL + "?rr=" + JoH.tsl()));
        startActivity(downloadActivity);
    }

    // TODO WebAppHelper could/should implement these features too or we could use the download manager
    private class AsyncDownloader extends AsyncTask<Void, Long, Boolean> {
        private final String URL = DOWNLOAD_URL + "?rr=" + JoH.tsl();

        private final OkHttpClient client = OkHttpWrapper.getClient().newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        private String filename;

        @Override
        protected Boolean doInBackground(Void... params) {

            final Request request = new Request.Builder()
                    .header("User-Agent", "Mozilla/5.0 (jamorham)")
                    .header("Accept-Encoding", "")
                    .header("Connection", "close")
                    .url(URL)
                    .build();


            try {
                final Response response = client.newCall(request).execute();
                filename = response.header("Content-Disposition", "");
                final Matcher matcher = Pattern.compile("attachment;filename=\"(.*?)\"").matcher(filename);
                if (matcher.find()) {
                    filename = matcher.group(1);
                } else {
                    filename = "";
                    final Matcher matcher2 = Pattern.compile("/([^/]*?.apk)").matcher(URL);
                    if (matcher2.find()) {
                        filename = matcher2.group(1);
                    }
                }
                if (filename.length() < 5) {
                    filename = "xDrip-plus-" + Long.toString(newversion) + ".apk";
               }

                Log.d(TAG, "Filename: " + filename);
                if (response.code() == 200) {
                    lastDigest = "";
                    InputStream inputStream = null;
                    FileOutputStream outputStream = null;
                    try {

                        dest_file = new File(getDownloadFolder(), filename);
                        try {
                            if (dest_file.exists())
                                dest_file.delete();
                        } catch (Exception e) {
                            Log.e(TAG, "Got exception deleting existing file: " + e);
                        }

                        outputStream = new FileOutputStream(dest_file);
                        inputStream = response.body().byteStream();
                        MessageDigest messageDigest = null;
                        DigestInputStream digestInputStream = null;
                        try {
                            messageDigest = MessageDigest.getInstance("SHA256");
                            digestInputStream = new DigestInputStream(inputStream, messageDigest);
                        } catch (NoSuchAlgorithmException e) {
                            //
                        }
                        byte[] buff = new byte[1024 * 4];
                        long downloaded = 0;
                        long target = response.body().contentLength();
                        if (target == -1)
                            target = FILE_SIZE; // get this from update server alternately
                        publishProgress(0L, target);
                        while (true) {

                            int last_read = (digestInputStream != null) ? digestInputStream.read(buff) : inputStream.read(buff);
                            if (last_read == -1) {
                                break;
                            }
                            outputStream.write(buff, 0, last_read);
                            downloaded += last_read;
                            publishProgress(downloaded, target);
                            if (isCancelled() || !downloading) {
                                return false;
                            }
                        }
                        if (messageDigest != null)
                            lastDigest = JoH.bytesToHex(messageDigest.digest()).toLowerCase();
                        return downloaded == target;

                    } catch (IOException e) {
                        Log.e(TAG, "Download error: " + e.toString());
                        JoH.static_toast_long(getApplicationContext(), "Data error: ");
                        return false;
                    } finally {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                        if (outputStream != null) {
                            outputStream.close();
                        }
                    }
                } else {
                    return false;
                }
            } catch (SocketTimeoutException e) {
                JoH.static_toast_long(getApplicationContext(), "Download timeout!");
                return false;
            } catch (IOException e) {
                Log.e(TAG, "Exception in download: " + e);
                if (e instanceof javax.net.ssl.SSLHandshakeException) {
                    if (JoH.ratelimit("internal-update-fallback", 15)) {
                        JoH.static_toast_long("Internal problems - trying with android system");
                        viewIntentDownload(URL);
                    }
                }
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(Long... values) {
            progressText.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setMax(values[1].intValue());
            progressBar.setProgress(values[0].intValue());

            long kbprogress = values[0] / 1024;
            long kbmax = values[1] / 1024;
            if (values[1] > 0) {
                progressText.setText(String.format("%d / %d KB", kbprogress, kbmax));
            } else {
                progressText.setText(String.format("%d KB", kbprogress));
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            progressText.setText(result ? "Downloaded" : "Failed");
            downloading = false;
            if (result) {
                if ((filename != null) && (filename.length() > 5) && (dest_file != null)) {
                    if ((CHECKSUM.length() == 0) || (lastDigest.length() == 0) || (CHECKSUM.equals(lastDigest))) {
                        try {
                            try {
                                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                                dm.addCompletedDownload(filename, "xDrip update version " + newversion, false, "application/vnd.android.package-archive", getDownloadFolder() + "/" + filename, FILE_SIZE, true);
                            } catch (Exception e) {
                                Log.e(TAG, "Download manager error: " + e);
                            }
                            final String mimeType = "application/vnd.android.package-archive";
                            final Intent installapk = new Intent(Intent.ACTION_VIEW);
                            installapk.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            // despite reference documentation to the contrary this is required
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                installapk.setDataAndType(FileProvider.getUriForFile(getApplicationContext(),
                                        BuildConfig.APPLICATION_ID + ".provider", dest_file), mimeType);
                            } else {
                                installapk.setDataAndType(Uri.fromFile(dest_file), mimeType);
                            }
                            installapk.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            UserError.Log.d(TAG,"Attempting to install application");
                            startActivity(installapk);
                            finish();
                        } catch (Exception e) {
                            Log.e(TAG, "Got exception trying to install apk: " + e);
                            JoH.static_toast_long(getApplicationContext(), "Update is in your downloads folder");
                        }
                    } else {
                        Log.e(TAG, "Checksum doesn't match: " + lastDigest + " vs " + CHECKSUM);
                        try {
                            dest_file.delete();
                        } catch (Exception e) {
                            Log.e(TAG, "Got exception deleting corrupt file: " + e);
                        }
                        JoH.static_toast_long("File appears corrupt!");
                        finish();
                    }
                }
            } else {
                JoH.static_toast_long(getApplicationContext(), "Failed!");
                try {
                    if ((dest_file != null) && dest_file.exists())
                        dest_file.delete();
                } catch (Exception e) {
                    Log.e(TAG, "Got exception deleting existing file: " + e);
                }
            }

        }
    }

    public static void clearLastCheckTime() {
        Pref.setLong(last_update_check_time,0L);
    }
}
