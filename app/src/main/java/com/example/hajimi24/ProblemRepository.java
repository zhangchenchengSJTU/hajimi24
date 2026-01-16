package com.example.hajimi24;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProblemRepository {
    // GitHub 相关配置
    private static final String GITHUB_TREE_URL = "https://api.github.com/repos/zhangchenchengSJTU/hajimi24/git/trees/Stable?recursive=1";
    private static final String GITHUB_RAW_BASE = "https://raw.githubusercontent.com/zhangchenchengSJTU/hajimi24/Stable/";

    private Context context;

    public ProblemRepository(Context context) {
        this.context = context;
    }

    // --- 数据结构与回调接口 ---
    public static class RemoteFile {
        public String path;
        public String name;
        public String sha;

        public RemoteFile(String path, String name, String sha) {
            this.path = path;
            this.name = name;
            this.sha = sha;
        }
    }

    public interface MenuDataCallback {
        void onSuccess(List<RemoteFile> files);
        void onFail(String error);
    }

    public interface FileDownloadCallback {
        void onProgress(int percent);
        void onSuccess(List<Problem> problems, String fileName);
        void onFail(String error);
    }

    // ==========================================
    //  Part 1: 在线逻辑 (Online Mode)
    // ==========================================

    public void fetchRemoteFileTree(MenuDataCallback callback) {
        new Thread(() -> {
            try {
                String jsonStr = downloadString(GITHUB_TREE_URL);
                JSONObject root = new JSONObject(jsonStr);
                JSONArray tree = root.getJSONArray("tree");
                List<RemoteFile> result = new ArrayList<>();
                for (int i = 0; i < tree.length(); i++) {
                    JSONObject item = tree.getJSONObject(i);
                    String path = item.getString("path");
                    if (path.startsWith("data/") && path.endsWith(".txt")) {
                        String name = path.substring(path.lastIndexOf('/') + 1);
                        String sha = item.getString("sha"); // 获取 SHA
                        result.add(new RemoteFile(path, name, sha));
                    }
                }
                if (callback != null) callback.onSuccess(result);
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    public void saveLocalFileSHA(String path, String sha) {
        context.getSharedPreferences("FileMeta", Context.MODE_PRIVATE)
                .edit().putString(path, sha).apply();
    }
    public boolean needsUpdate(String path, String remoteSha) {
        File file = new File(context.getFilesDir(), path);
        if (!file.exists()) return true; // 文件不存在，需要下载

        String localSha = context.getSharedPreferences("FileMeta", Context.MODE_PRIVATE)
                .getString(path, "");
        return !localSha.equals(remoteSha); // 如果 SHA 不一致，说明云端更新了
    }

    public void downloadFileContent(String filePath, GameModeSettings settings, FileDownloadCallback callback) {
        new Thread(() -> {
            try {
                String encodedPath = filePath.replace(" ", "%20");
                String url = GITHUB_RAW_BASE + encodedPath;

                String content = downloadStringWithProgress(url, callback);
                if (content == null) throw new Exception("文件内容下载失败");

                // --- 新增：保存到本地 ---
                saveFileToInternalStorage(filePath, content);
                // -----------------------

                List<Problem> problems = parseContentToProblems(content, filePath, settings);
                if (callback != null) callback.onSuccess(problems, filePath);
            } catch (Exception e) {
                if (callback != null) callback.onFail(e.getMessage());
            }
        }).start();
    }

    private void saveFileToInternalStorage(String filePath, String content) {
        try {
            File file = new File(context.getFilesDir(), filePath);
            // 如果包含文件夹路径，先创建父目录
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(content);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 1. 递归获取本地文件 (支持子文件夹)
    public void fetchLocalFileTree(MenuDataCallback callback) {
        new Thread(() -> {
            List<RemoteFile> result = new ArrayList<>();
            try {
                // 扫描内部存储
                File dataDir = new File(context.getFilesDir(), "data");
                if (dataDir.exists()) {
                    scanLocalDirectory(dataDir, "data/", result);
                }

                // 扫描 Assets (Assets 扫描比较特殊，仅扫描一级 data 目录作为演示，
                // 建议将下载的文件与内置文件路径统一)
                String[] assetFiles = context.getAssets().list("data");
                if (assetFiles != null) {
                    for (String fileName : assetFiles) {
                        if (fileName.endsWith(".txt")) {
                            String path = "data/" + fileName;
                            boolean exists = false;
                            for(RemoteFile rf : result) if(rf.path.equals(path)) exists = true;
                            if(!exists) result.add(new RemoteFile(path, fileName, ""));
                        }
                    }
                }

                if (callback != null) callback.onSuccess(result);
            } catch (Exception e) {
                if (callback != null) callback.onFail(e.getMessage());
            }
        }).start();
    }
    // 辅助方法：递归遍历文件夹
    private void scanLocalDirectory(File dir, String prefix, List<RemoteFile> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanLocalDirectory(f, prefix + f.getName() + "/", result);
            } else if (f.getName().endsWith(".txt")) {
                result.add(new RemoteFile(prefix + f.getName(), f.getName(), ""));
            }
        }
    }


    private List<Problem> parseContentToProblems(String content, String fileName, GameModeSettings settings) {
        List<Problem> list = new ArrayList<>();
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            // 调整：放宽 hasMod 和 hasBase 的识别，避免因末尾空格或格式微调导致 matches 失败
            boolean hasMod = line.contains("mod");
            boolean hasBase = line.contains("base");

            if (!isProblemValid(line, fileName, settings)) continue;

            Problem p = parseLineToProblem(line);
            if (p != null) list.add(p);
        }
        return list;
    }

    // ==========================================
    //  网络下载辅助
    // ==========================================
    private String downloadString(String urlString) {
        try {
            return downloadStringWithProgress(urlString, null);
        } catch (IOException e) {
            return null;
        }
    }
    private String downloadStringWithProgress(String urlString, FileDownloadCallback callback) throws IOException {
        HttpURLConnection connection = null;
        InputStream stream = null;
        ByteArrayOutputStream baos = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.connect();

            int length = connection.getContentLength(); // 注意：GitHub 可能返回 -1
            stream = connection.getInputStream();
            baos = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int totalBytesRead = 0;
            int bytesRead;

            while ((bytesRead = stream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                if (callback != null) {
                    if (length > 0) {
                        int percent = (int) ((totalBytesRead * 100L) / length);
                        callback.onProgress(percent);
                    } else {
                        // 如果拿不到长度，发送一个 -1 标志，让 UI 显示不确定进度
                        callback.onProgress(-1);
                    }
                }
            }
            // 结束时强制 100%
            if (callback != null) callback.onProgress(100);

            return baos.toString("UTF-8");
        } finally {
            if (connection != null) connection.disconnect();
            if (stream != null) try { stream.close(); } catch (Exception e) {}
            if (baos != null) try { baos.close(); } catch (Exception e) {}
        }
    }

    // ==========================================
    //  Part 2: 本地逻辑与解析逻辑
    // ==========================================

    public List<Problem> loadProblemSet(String fileName, GameModeSettings settings) throws Exception {
        List<Problem> problems = new ArrayList<>();
        InputStream is = getFileInputStream(fileName);
        if (is == null) throw new Exception("File not found: " + fileName);

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            boolean hasMod = line.contains("mod");
            boolean hasBase = line.contains("base");

            if (!isProblemValid(line, fileName, settings)) continue;
            Problem p = parseLineToProblem(line);
            if (p != null) problems.add(p);
        }
        br.close();
        return problems;
    }

    private boolean isProblemValid(String line, String fileName, GameModeSettings settings) {
        String[] parts = line.split("->");
        if (parts.length < 2) return false;
        String solution = parts[1].trim();
        String solLower = solution.toLowerCase();
        String fileLower = fileName.toLowerCase();

        // ==========================================
        // 基础全局规则 (对所有格式生效)
        // ==========================================
        // 1. 必须含有除法规则
        if (settings.mustHaveDivision && !solution.contains(" / ")) return false;

        // 2. 避免纯加减规则
        if (settings.avoidPureAddSub && !solution.contains(" * ") && !solution.contains(" / ")) return false;

        // 3. [核心修改] 除法风暴规则 (提升为全局规则)
        if (settings.requireDivisionStorm) {
            // 计算题目中的数字个数 n
            String numbersListString = parts[0];
            int n = 1;
            for (int i = 0; i < numbersListString.length(); i++) {
                if (numbersListString.charAt(i) == ',') n++;
            }

            // 统计解法中除号 " / " 出现的次数
            int divisionCount = 0;
            int lastIndex = 0;
            String op = " / ";
            while ((lastIndex = solution.indexOf(op, lastIndex)) != -1) {
                divisionCount++;
                lastIndex += op.length();
            }

            // 判定：如果除号数量少于 n - 2，则不合格
            if (divisionCount < n - 2) return false;
        }

        // ==========================================
        // 高级过滤保护：判断是否为特殊模式 (进制、取模、复数、分数题库)
        // ==========================================
        boolean isSpecialMode = solLower.contains("mod") || solLower.contains("base") || solLower.contains("i")
                || fileLower.contains("base") || fileLower.contains("进制")
                || fileLower.contains("mod") || fileLower.contains("模")
                || fileLower.contains("分数") || fileLower.contains("fraction");

        if (isSpecialMode) return true;

        // ==========================================
        // 4. 只有在非特殊模式下，才执行以下 10 进制特有过滤
        // ==========================================
        if (settings.avoidTrivialFinalMultiply) {
            int mainOpIdx = findMainOperatorIndex(solution);
            if (mainOpIdx != -1 && solution.charAt(mainOpIdx) == '*') {
                String left = solution.substring(0, mainOpIdx).trim();
                String right = solution.substring(mainOpIdx + 1).trim();
                while (left.startsWith("(") && left.endsWith(")")) left = left.substring(1, left.length() - 1).trim();
                while (right.startsWith("(") && right.endsWith(")")) right = right.substring(1, right.length() - 1).trim();

                String[] trivialNumbers = {"1", "2", "3", "4", "6", "8", "12", "24"};
                for (String num : trivialNumbers) {
                    if (left.equals(num) || right.equals(num)) return false;
                }
            }
        }

        return true;
    }




    public boolean expressionContainsFractions(String expression) {
        return expressionContainsFractions(expression, 10);
    }

    public boolean expressionContainsFractions(String expression, int radix) {
        if (expression.contains("mod")) return false;
        if (expression.contains("base")) return false;
        try { evaluateAndCheck(expression, radix); }
        catch (FractionalOperationFoundException e) { return true; }
        catch (Exception e) { return false; }
        return false;
    }

    private static class FractionalOperationFoundException extends RuntimeException {}

    private Fraction evaluateAndCheck(String subExpression) throws FractionalOperationFoundException {
        return evaluateAndCheck(subExpression, 10);
    }

    private Fraction evaluateAndCheck(String subExpression, int radix) throws FractionalOperationFoundException {
        subExpression = subExpression.trim();
        if (subExpression.matches(".*mod\\s*\\d+$")) subExpression = subExpression.replaceAll("mod\\s*\\d+$", "").trim();

        if (subExpression.startsWith("(") && subExpression.endsWith(")")) {
            int balance = 0;
            boolean isPaired = true;
            for (int i = 0; i < subExpression.length() - 1; i++) {
                if (subExpression.charAt(i) == '(') balance++;
                else if (subExpression.charAt(i) == ')') balance--;
                if (balance == 0) { isPaired = false; break; }
            }
            if (isPaired) return evaluateAndCheck(subExpression.substring(1, subExpression.length() - 1), radix);
        }

        int balance = 0;
        for (int i = subExpression.length() - 1; i >= 0; i--) {
            char c = subExpression.charAt(i);
            if (c == ')') balance++; else if (c == '(') balance--;
            if (balance == 0 && (c == '+' || c == '-') && i > 0) {
                String leftStr = subExpression.substring(0, i).trim();
                if (leftStr.isEmpty() || "+-*/(".indexOf(leftStr.charAt(leftStr.length() - 1)) != -1) continue;
                Fraction l = evaluateAndCheck(leftStr, radix);
                Fraction r = evaluateAndCheck(subExpression.substring(i + 1).trim(), radix);
                if (l.toString().contains("/") || r.toString().contains("/")) throw new FractionalOperationFoundException();
                return c == '+' ? l.add(r) : l.sub(r);
            }
        }
        balance = 0;
        for (int i = subExpression.length() - 1; i >= 0; i--) {
            char c = subExpression.charAt(i);
            if (c == ')') balance++; else if (c == '(') balance--;
            if (balance == 0 && (c == '*' || c == '/') && i > 0) {
                Fraction l = evaluateAndCheck(subExpression.substring(0, i).trim(), radix);
                Fraction r = evaluateAndCheck(subExpression.substring(i + 1).trim(), radix);
                if (c == '*') return l.multiply(r);
                else {
                    if (r.toString().equals("0")) throw new ArithmeticException("Div 0");
                    return l.divide(r);
                }
            }
        }
        return parseTokenToFraction(subExpression, radix);
    }

    private int findMainOperatorIndex(String expression) {
        int balance = 0;
        String expr = expression.trim();
        while (expr.length() > 2 && expr.startsWith("(") && expr.endsWith(")")) expr = expr.substring(1, expr.length() - 1).trim();
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') balance++; else if (c == '(') balance--;
            else if ((c == '+' || c == '-') && balance == 0) return expression.lastIndexOf(expr) + i;
        }
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') balance++; else if (c == '(') balance--;
            else if ((c == '*' || c == '/') && balance == 0) return expression.lastIndexOf(expr) + i;
        }
        return -1;
    }

    private List<Integer> getIntegerComponents(String problemPart) {
        List<Integer> numbers = new ArrayList<>();
        int currentRadix = 10;
        Pattern radixPattern = Pattern.compile("^\\[(\\d+)\\]");
        Matcher radixMatcher = radixPattern.matcher(problemPart.trim());
        String partToSearch = problemPart;
        if (radixMatcher.find()) {
            currentRadix = Integer.parseInt(radixMatcher.group(1));
            partToSearch = problemPart.substring(radixMatcher.end());
        }

        Pattern p = Pattern.compile("[0-9A-Fa-f]+");
        Matcher m = p.matcher(partToSearch);
        while (m.find()) {
            try {
                numbers.add(Integer.parseInt(m.group(), currentRadix));
            } catch (Exception e) { }
        }
        return numbers;
    }

    private Problem parseLineToProblem(String line) {
        try {
            String[] parts = line.split("->");
            if (parts.length < 2) return null;
            String numberPart = parts[0];
            String solution = parts[1].trim();
            Integer modulus = null;
            Integer radix = null;

            Pattern modPattern = Pattern.compile("mod\\s*(\\d+)$");
            Matcher modMatcher = modPattern.matcher(solution);
            if (modMatcher.find()) modulus = Integer.parseInt(modMatcher.group(1));

            Pattern basePattern = Pattern.compile("base\\s*(\\d+)$");
            Matcher baseMatcher = basePattern.matcher(solution);
            if (baseMatcher.find()) radix = Integer.parseInt(baseMatcher.group(1));

            Pattern pattern = Pattern.compile("\\['(.*?)'\\]");
            Matcher matcher = pattern.matcher(numberPart);

            if (matcher.find()) {
                String numbersString = matcher.group(1);
                String[] numberTokens = numbersString.split("', '");
                List<Fraction> fractions = new ArrayList<>();
                int currentRadix = (radix != null) ? radix : 10;

                for (String token : numberTokens) {
                    fractions.add(parseTokenToFraction(token.replace("'", "").trim(), currentRadix));
                }
                return new Problem(fractions, solution, line, modulus, radix);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private Fraction parseTokenToFraction(String token, int radix) {
        token = token.replace("(", "").replace(")", "");
        if (token.contains("i")) {
            long realPart = 0; long imagPart = 0;
            if (token.equals("i")) imagPart = 1;
            else if (token.equals("-i")) imagPart = -1;
            else if (!token.contains("+") && !token.substring(1).contains("-")) {
                imagPart = Long.parseLong(token.replace("i", ""), radix);
            } else {
                int operatorIndex = Math.max(token.indexOf('+'), token.substring(1).indexOf('-') + 1);
                realPart = Long.parseLong(token.substring(0, operatorIndex), radix);
                String imagStr = token.substring(operatorIndex).replace("i", "");
                if (imagStr.equals("+")) imagPart = 1;
                else if (imagStr.equals("-")) imagPart = -1;
                else imagPart = Long.parseLong(imagStr, radix);
            }
            // 使用 4 参数构造函数: (实部, 虚部, 分母, 进制)
            return new Fraction(realPart, imagPart, 1, radix);
        } else if (token.contains("/")) {
            String[] fp = token.split("/");
            // 使用 4 参数构造函数，虚部强制设为 0
            return new Fraction(Long.parseLong(fp[0], radix), 0, Long.parseLong(fp[1], radix), radix);
        } else {
            try {
                // 使用 4 参数构造函数，虚部设为 0，分母设为 1
                return new Fraction(Long.parseLong(token, radix), 0, 1, radix);
            } catch (Exception e) {
                return new Fraction(0, 0, 1, radix);
            }
        }
    }
    // 1. 检查本地是否已存在该文件
    public boolean isFileDownloaded(String filePath) {
        File file = new File(context.getFilesDir(), filePath);
        return file.exists() && file.length() > 0;
    }

    // 2. 同步下载方法 (供批量下载线程调用)
    public void downloadFileSync(String path, String sha) {
        try {
            String content = downloadString(GITHUB_RAW_BASE + path.replace(" ", "%20"));
            if (content != null) {
                saveFileToInternalStorage(path, content);
                saveLocalFileSHA(path, sha); // 保存版本标记
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private InputStream getFileInputStream(String fileName) throws IOException {
        // 1. 先尝试从应用内部存储空间读取 (用户下载的)
        File file = new File(context.getFilesDir(), fileName);
        if (file.exists()) {
            return new FileInputStream(file);
        }

        // 2. 如果不存在，尝试从 APK 的 assets 目录读取 (内置的)
        try {
            return context.getAssets().open(fileName);
        } catch (IOException e) {
            // 都不存在则返回 null
            return null;
        }
    }
}
