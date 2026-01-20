package com.example.hajimi24;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver; // 补全导入
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter; // 补全导入
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build; // 补全导入
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.FileProvider; // 补全导入

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File; // 补全导入
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {

    private static final String UPDATE_URL = "https://raw.githubusercontent.com/zhangchenchengSJTU/hajimi24/Stable/update.json";
    private final Context mContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private long mDownloadId; // 【核心修正】必须声明此变量

    public UpdateManager(Context context) {
        this.mContext = context;
    }

    public void checkUpdate() {
        executor.execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(UPDATE_URL).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                int remoteVersionCode = json.getInt("versionCode");
                String versionName = json.getString("versionName");
                String content = json.getString("updateContent");
                String apkUrl = json.getString("apkUrl");

                if (remoteVersionCode > getCurrentVersionCode()) {
                    mainHandler.post(() -> showUpdateDialog(versionName, content, apkUrl));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private int getCurrentVersionCode() {
        try {
            PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) pInfo.getLongVersionCode();
            } else {
                return pInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private void showUpdateDialog(String name, String content, String url) {
        new AlertDialog.Builder(mContext)
                .setTitle("发现新版本: " + name)
                .setMessage(content)
                .setPositiveButton("立即更新", (dialog, which) -> startDownload(url))
                .setNegativeButton("稍后再说", null)
                .setCancelable(false)
                .show();
    }

    private void startDownload(String url) {
        String rawUrl = url.replace("/blob/", "/raw/");

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(rawUrl));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle("哈基米 24 点新版本");
        // 确保文件名固定，方便安装时寻找
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "hajimi24_update.apk");

        DownloadManager manager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            mDownloadId = manager.enqueue(request);

            // 注册广播监听
            mContext.registerReceiver(onDownloadComplete,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (mDownloadId == id) {
                installApk(context);
                // 建议安装调起后取消注册，防止重复触发
                try {
                    context.unregisterReceiver(this);
                } catch (Exception ignored) {}
            }
        }
    };

    private void installApk(Context context) {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "hajimi24_update.apk");

        if (!file.exists()) return;

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 这里的 authorities 必须与 Manifest 中完全一致
            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
        }
        context.startActivity(intent);
    }
}
