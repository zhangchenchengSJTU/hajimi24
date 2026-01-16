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

        public RemoteFile(String path, String name) {
            this.path = path;
            this.name = name;
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
                if (jsonStr == null) throw new Exception("网络请求失败");

                JSONObject root = new JSONObject(jsonStr);
                JSONArray tree = root.getJSONArray("tree");

                List<RemoteFile> result = new ArrayList<>();
                for (int i = 0; i < tree.length(); i++) {
                    JSONObject item = tree.getJSONObject(i);
                    String path = item.getString("path");
                    if (path.startsWith("data/") && path.endsWith(".txt")) {
                        String name = path.substring(path.lastIndexOf('/') + 1);
                        result.add(new RemoteFile(path, name));
                    }
                }
                if (callback != null) callback.onSuccess(result);
            } catch (Exception e) {
                if (callback != null) callback.onFail(e.getMessage());
            }
        }).start();
    }

    public void downloadFileContent(String filePath, GameModeSettings settings, FileDownloadCallback callback) {
        new Thread(() -> {
            try {
                String encodedPath = filePath.replace(" ", "%20");
                String url = GITHUB_RAW_BASE + encodedPath;

                String content = downloadStringWithProgress(url, callback);
                if (content == null) throw new Exception("文件内容下载失败");

                List<Problem> problems = parseContentToProblems(content, filePath, settings);
                if (callback != null) callback.onSuccess(problems, filePath);
            } catch (Exception e) {
                if (callback != null) callback.onFail(e.getMessage());
            }
        }).start();
    }

    private List<Problem> parseContentToProblems(String content, String fileName, GameModeSettings settings) {
        List<Problem> list = new ArrayList<>();
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            // 校验逻辑：如果是 mod 或 base 题目，通常直接放行
            boolean hasMod = line.matches(".*mod\\s*\\d+$");
            boolean hasBase = line.matches(".*base\\s*\\d+$");

            if (!isProblemValid(line, fileName, settings) && !hasMod && !hasBase) continue;

            Problem p = parseLineToProblem(line);
            if (p != null) list.add(p);
        }
        return list;
    }

    // ==========================================
    //  网络下载辅助
    // ==========================================

    private String downloadString(String urlString) {
        try { return downloadStringWithProgress(urlString, null); }
        catch (IOException e) { return null; }
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

            int length = connection.getContentLength();
            stream = connection.getInputStream();
            baos = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int totalBytesRead = 0;
            int bytesRead;

            while ((bytesRead = stream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                if (length > 0 && callback != null) {
                    int percent = (int) ((totalBytesRead * 100L) / length);
                    callback.onProgress(percent);
                }
            }
            return baos.toString("UTF-8");
        } finally {
            if (connection != null) connection.disconnect();
            if (stream != null) stream.close();
            if (baos != null) baos.close();
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

            boolean hasMod = line.matches(".*mod\\s*\\d+$");
            boolean hasBase = line.matches(".*base\\s*\\d+$");

            if (!isProblemValid(line, fileName, settings) && !hasMod && !hasBase) continue;

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

        // 包含 mod 或 base 的题目跳过复杂过滤，直接视为有效
        if (solution.matches(".*mod\\s*\\d+$")) return true;
        if (solution.matches(".*base\\s*\\d+$")) return true;

        if (settings.requireDivisionStorm) {
            String numbersListString = parts[0];
            int n = 1;
            for (int i = 0; i < numbersListString.length(); i++) if (numbersListString.charAt(i) == ',') n++;
            if (n < 2) return false;
            int divisionCount = 0;
            int lastIndex = 0;
            String divisionOperator = " / ";
            while ((lastIndex = solution.indexOf(divisionOperator, lastIndex)) != -1) {
                divisionCount++;
                lastIndex += divisionOperator.length();
            }
            if (divisionCount < n - 2) return false;
        }

        if (settings.avoidPureAddSub && !solution.contains("*") && !solution.contains(" / ")) return false;
        if (settings.mustHaveDivision && !solution.contains(" / ")) return false;

        if (settings.avoidTrivialFinalMultiply) {
            int mainOpIdx = findMainOperatorIndex(solution);
            if (mainOpIdx != -1 && solution.charAt(mainOpIdx) == '*') {
                String left = solution.substring(0, mainOpIdx);
                String right = solution.substring(mainOpIdx + 1);
                if (!left.contains(" / ") && !right.contains(" / ")) return false;
            }
        }

        if (settings.requireFractionCalc && !expressionContainsFractions(solution)) return false;

        if (fileName.contains("小于") && settings.numberBound > 0) {
            for (int num : getIntegerComponents(parts[0])) {
                if (num > settings.numberBound) return false;
            }
        }
        return true;
    }

    public boolean expressionContainsFractions(String expression) {
        if (expression.contains("mod")) return false;
        if (expression.contains("base")) return false; // base 题目不检查分数特性
        try { evaluateAndCheck(expression); }
        catch (FractionalOperationFoundException e) { return true; }
        catch (Exception e) { return false; }
        return false;
    }

    private static class FractionalOperationFoundException extends RuntimeException {}

    private Fraction evaluateAndCheck(String subExpression) throws FractionalOperationFoundException {
        subExpression = subExpression.trim();
        if (subExpression.matches(".*mod\\s*\\d+$")) subExpression = subExpression.replaceAll("mod\\s*\\d+$", "").trim();
        // 如果这里还有 base 字符串，理论上也应该去除，但前面的 expressionContainsFractions 已经拦截了 base 题目

        if (subExpression.startsWith("(") && subExpression.endsWith(")")) {
            int balance = 0;
            boolean isPaired = true;
            for (int i = 0; i < subExpression.length() - 1; i++) {
                if (subExpression.charAt(i) == '(') balance++;
                else if (subExpression.charAt(i) == ')') balance--;
                if (balance == 0) { isPaired = false; break; }
            }
            if (isPaired) return evaluateAndCheck(subExpression.substring(1, subExpression.length() - 1));
        }

        int balance = 0;
        for (int i = subExpression.length() - 1; i >= 0; i--) {
            char c = subExpression.charAt(i);
            if (c == ')') balance++; else if (c == '(') balance--;
            if (balance == 0 && (c == '+' || c == '-') && i > 0) {
                String leftStr = subExpression.substring(0, i).trim();
                if (leftStr.isEmpty() || "+-*/(".indexOf(leftStr.charAt(leftStr.length() - 1)) != -1) continue;
                Fraction l = evaluateAndCheck(leftStr);
                Fraction r = evaluateAndCheck(subExpression.substring(i + 1).trim());
                if (l.toString().contains("/") || r.toString().contains("/")) throw new FractionalOperationFoundException();
                return c == '+' ? l.add(r) : l.sub(r);
            }
        }
        balance = 0;
        for (int i = subExpression.length() - 1; i >= 0; i--) {
            char c = subExpression.charAt(i);
            if (c == ')') balance++; else if (c == '(') balance--;
            if (balance == 0 && (c == '*' || c == '/') && i > 0) {
                Fraction l = evaluateAndCheck(subExpression.substring(0, i).trim());
                Fraction r = evaluateAndCheck(subExpression.substring(i + 1).trim());
                if (c == '*') return l.multiply(r);
                else {
                    if (r.toString().equals("0")) throw new ArithmeticException("Div 0");
                    return l.divide(r);
                }
            }
        }
        return parseTokenToFraction(subExpression, 10); // 默认校验时使用10进制
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
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher(problemPart);
        while (m.find()) numbers.add(Integer.parseInt(m.group()));
        return numbers;
    }

    private Problem parseLineToProblem(String line) {
        try {
            String[] parts = line.split("->");
            if (parts.length < 2) return null;
            String numberPart = parts[0];
            String solution = parts[1].trim();
            Integer modulus = null;
            Integer radix = null; // 新增：进制

            Pattern modPattern = Pattern.compile("mod\\s*(\\d+)$");
            Matcher modMatcher = modPattern.matcher(solution);
            if (modMatcher.find()) modulus = Integer.parseInt(modMatcher.group(1));

            // 新增：提取 base
            Pattern basePattern = Pattern.compile("base\\s*(\\d+)$");
            Matcher baseMatcher = basePattern.matcher(solution);
            if (baseMatcher.find()) radix = Integer.parseInt(baseMatcher.group(1));

            Pattern pattern = Pattern.compile("\\['(.*?)'\\]");
            Matcher matcher = pattern.matcher(numberPart);

            if (matcher.find()) {
                String numbersString = matcher.group(1);
                String[] numberTokens = numbersString.split("', '");
                List<Fraction> fractions = new ArrayList<>();
                // 确定使用的进制，默认为 10
                int currentRadix = (radix != null) ? radix : 10;

                for (String token : numberTokens) {
                    // 传入进制进行解析
                    fractions.add(parseTokenToFraction(token.replace("'", "").trim(), currentRadix));
                }
                // 调用包含 radix 的新构造函数
                return new Problem(fractions, solution, line, modulus, radix);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // 默认方法保持兼容
    private Fraction parseTokenToFraction(String token) {
        return parseTokenToFraction(token, 10);
    }

    // 新增：支持进制解析
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
            return new Fraction(realPart, imagPart, 1);
        } else if (token.contains("/")) {
            String[] fp = token.split("/");
            return new Fraction(Long.parseLong(fp[0], radix), Long.parseLong(fp[1], radix));
        } else {
            try { return new Fraction(Long.parseLong(token, radix), 1); } catch (Exception e) { return new Fraction(0, 1); }
        }
    }

    private InputStream getFileInputStream(String fileName) throws IOException {
        File file = new File(context.getFilesDir(), fileName);
        if (file.exists()) return new FileInputStream(file);
        return null;
    }
}
