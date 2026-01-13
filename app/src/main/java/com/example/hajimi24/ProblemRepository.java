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
    private static final String GITHUB_API_URL = "https://api.github.com/repos/zhangchenchengSJTU/hajimi24/contents/data";
    private Context context;

    public ProblemRepository(Context context) {
        this.context = context;
    }

    public interface SyncCallback {
        void onProgress(String fileName, int current, int total);
        void onSuccess(int count);
        void onFail(String error);
    }

    public void syncFromGitHub(SyncCallback callback) {
        new Thread(() -> {
            try {
                String jsonStr = downloadString(GITHUB_API_URL);
                if (jsonStr == null) throw new Exception("无法连接到 GitHub，请检查网络");

                JSONArray jsonArray = new JSONArray(jsonStr);
                List<JSONObject> taskList = new ArrayList<>();

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject item = jsonArray.getJSONObject(i);
                    String name = item.getString("name");
                    if (name.endsWith(".txt")) {
                        taskList.add(item);
                    }
                }

                int total = taskList.size();
                int successCount = 0;

                for (int i = 0; i < total; i++) {
                    JSONObject item = taskList.get(i);
                    String name = item.getString("name");
                    String downloadUrl = item.getString("download_url");

                    callback.onProgress(name, i + 1, total);

                    String content = downloadString(downloadUrl);
                    if (content != null) {
                        saveToInternalStorage(name, content);
                        successCount++;
                    }
                }
                callback.onSuccess(successCount);

            } catch (Exception e) {
                e.printStackTrace();
                callback.onFail(e.getMessage());
            }
        }).start();
    }

    /**
     * 修改后的读取方法：
     * 1. 能够跳过 [119] 这种索引标签，正确读取后面的题目数组。
     * 2. 自动去除 (6+i) 中的括号。
     * 3. 增加容错，防止单行错误导致整个文件读取失败。
     */
    public List<Problem> loadProblemSet(String fileName) throws Exception {
        List<Problem> problems = new ArrayList<>();
        InputStream is = getFileInputStream(fileName);
        if (is == null) throw new Exception("File not found");

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;

        // 修改点1：使用更通用的正则，匹配任意方括号内容，不强制要求单引号
        Pattern listPattern = Pattern.compile("\\[(.*?)\\]");

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("->");
            if (parts.length < 2) continue;

            Matcher m = listPattern.matcher(parts[0]);

            // 修改点2：使用 while 循环查找。
            // 例如 "[119] ['i', ...]"：
            // 第1次匹配 [119]，解析后长度为1，跳过。
            // 第2次匹配 ['i', ...]，解析后长度符合，添加题目并 break。
            while (m.find()) {
                String content = m.group(1);
                String[] rawNums = content.split(",");
                List<Fraction> fracs = new ArrayList<>();
                boolean parseSuccess = true;

                for (String s : rawNums) {
                    try {
                        // 修改点3：清洗数据，去除引号和括号 (6+i) -> 6+i
                        String cleanS = s.trim()
                                .replace("\'", "")
                                .replace("(", "")
                                .replace(")", "");
                        fracs.add(Fraction.parse(cleanS));
                    } catch (Exception e) {
                        // 如果某个数字解析失败（例如 Fraction 不支持 i），标记失败并尝试下一个匹配
                        parseSuccess = false;
                        break;
                    }
                }

                if (parseSuccess && fracs.size() >= 3 && fracs.size() <= 5) {
                    problems.add(new Problem(fracs, parts[1].trim()));
                    break; // 找到有效的一组数据后，停止处理当前行，进入下一行
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
