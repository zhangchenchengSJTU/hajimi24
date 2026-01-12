package com.example.hajimi24;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProblemRepository {
    // 确保 URL 是正确的 data 目录
    private static final String GITHUB_API_URL = "https://api.github.com/repos/zhangchenchengSJTU/hajimi24/contents/data";
    private Context context;

    public ProblemRepository(Context context) {
        this.context = context;
    }

    // --- 修改 1: 更新接口，增加进度回调 ---
    public interface SyncCallback {
        // 新增：汇报进度 (文件名, 当前第几个, 总共几个)
        void onProgress(String fileName, int current, int total);
        void onSuccess(int count);
        void onFail(String error);
    }

    // --- 修改 2: 更新同步逻辑，计算总量并汇报进度 ---
    public void syncFromGitHub(SyncCallback callback) {
        new Thread(() -> {
            try {
                // 第一步：先获取文件列表
                String jsonStr = downloadString(GITHUB_API_URL);
                if (jsonStr == null) throw new Exception("无法连接到 GitHub，请检查网络");

                JSONArray jsonArray = new JSONArray(jsonStr);
                List<JSONObject> taskList = new ArrayList<>();

                // 第二步：筛选出所有 .txt 文件，计算总数
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject item = jsonArray.getJSONObject(i);
                    String name = item.getString("name");
                    if (name.endsWith(".txt")) {
                        taskList.add(item);
                    }
                }

                int total = taskList.size();
                int successCount = 0;

                // 第三步：逐个下载，并实时汇报进度
                for (int i = 0; i < total; i++) {
                    JSONObject item = taskList.get(i);
                    String name = item.getString("name");
                    String downloadUrl = item.getString("download_url");

                    // -> 关键点：调用 onProgress 告诉界面当前状态
                    callback.onProgress(name, i + 1, total);

                    String content = downloadString(downloadUrl);
                    if (content != null) {
                        saveToInternalStorage(name, content);
                        successCount++;
                    }
                }

                // 全部完成后调用 onSuccess
                callback.onSuccess(successCount);

            } catch (Exception e) {
                e.printStackTrace();
                callback.onFail(e.getMessage());
            }
        }).start();
    }

    // ... (剩下的 loadProblemSet, getAvailableFiles, downloadString 等方法保持不变) ...
    // 为了完整性，这里列出没变的方法，你可以直接保留你原来的代码

    public List<Problem> loadProblemSet(String fileName) throws Exception {
        List<Problem> problems = new ArrayList<>();
        InputStream is = getFileInputStream(fileName);
        if (is == null) throw new Exception("File not found");

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        Pattern listPattern = Pattern.compile("\\[\'(.*?)\'\\]");

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("->");
            if (parts.length < 2) continue;

            Matcher m = listPattern.matcher(parts[0]);
            if (m.find()) {
                String[] rawNums = m.group(1).split(",");
                List<Fraction> fracs = new ArrayList<>();
                for (String s : rawNums) fracs.add(Fraction.parse(s.trim().replace("\'", "")));
                if (fracs.size() == 4 || fracs.size() == 5) {
                    problems.add(new Problem(fracs, parts[1].trim()));
                }
            }
        }
        br.close();
        return problems;
    }

    public List<String> getAvailableFiles() {
        Set<String> fileSet = new HashSet<>();
        try {
            String[] assets = context.getAssets().list("");
            if (assets != null) for (String f : assets) if (f.endsWith(".txt")) fileSet.add(f);
            String[] downloaded = context.fileList();
            if (downloaded != null) for (String f : downloaded) if (f.endsWith(".txt")) fileSet.add(f);
        } catch (Exception e) {}
        List<String> sortedFiles = new ArrayList<>(fileSet);
        Collections.sort(sortedFiles);
        return sortedFiles;
    }

    private String downloadString(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        if (conn.getResponseCode() == 200) {
            try (InputStream is = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                return sb.toString();
            }
        }
        return null;
    }

    private void saveToInternalStorage(String fileName, String content) throws IOException {
        try (FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE)) {
            fos.write(content.getBytes());
        }
    }

    private InputStream getFileInputStream(String fileName) {
        try {
            File file = new File(context.getFilesDir(), fileName);
            if (file.exists()) return new FileInputStream(file);
            return context.getAssets().open(fileName);
        } catch (Exception e) { return null; }
    }
}
