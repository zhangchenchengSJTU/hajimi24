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
    private static final String GITHUB_API_URL = "https://api.github.com/repos/zhangchenchengSJTU/LA-2025-2026-1--MATH1205H-04/contents/assets/24";
    private Context context;

    public ProblemRepository(Context context) {
        this.context = context;
    }

    public interface SyncCallback {
        void onSuccess(int count);
        void onFail(String error);
    }

    // 从 GitHub 同步
    public void syncFromGitHub(SyncCallback callback) {
        new Thread(() -> {
            try {
                String jsonStr = downloadString(GITHUB_API_URL);
                if (jsonStr == null) throw new Exception("无法获取列表");
                JSONArray jsonArray = new JSONArray(jsonStr);
                int downloadCount = 0;
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject item = jsonArray.getJSONObject(i);
                    String name = item.getString("name");
                    String downloadUrl = item.getString("download_url");
                    if (name.endsWith(".txt")) {
                        String content = downloadString(downloadUrl);
                        if (content != null) {
                            saveToInternalStorage(name, content);
                            downloadCount++;
                        }
                    }
                }
                callback.onSuccess(downloadCount);
            } catch (Exception e) {
                e.printStackTrace();
                callback.onFail(e.getMessage());
            }
        }).start();
    }

    // 加载题库
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

    // --- 私有辅助方法 ---
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
