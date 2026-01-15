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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProblemRepository {
    // 指向 Stable 分支
    private static final String GITHUB_API_URL = "https://api.github.com/repos/zhangchenchengSJTU/hajimi24/contents/data?ref=Stable";
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
                if (jsonStr == null) throw new Exception("无法连接到GitHub，请检查网络");

                JSONArray jsonArray = new JSONArray(jsonStr);
                List<JSONObject> taskList = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject item = jsonArray.getJSONObject(i);
                    String name = item.getString("name");
                    if (name.endsWith(".txt")) taskList.add(item);
                }

                int total = taskList.size();
                int successCount = 0;
                for (int i = 0; i < total; i++) {
                    JSONObject item = taskList.get(i);
                    String name = item.getString("name");
                    String downloadUrl = item.getString("download_url");
                    if (callback != null) callback.onProgress(name, i + 1, total);
                    String content = downloadString(downloadUrl);
                    if (content != null) {
                        saveToInternalStorage(name, content);
                        successCount++;
                    }
                }
                if (callback != null) callback.onSuccess(successCount);
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) callback.onFail(e.getMessage());
            }
        }).start();
    }

    public List<String> getAvailableFiles() {
        List<String> fileList = new ArrayList<>();
        File directory = context.getFilesDir();
        if (directory == null || !directory.exists()) return fileList;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".txt")) fileList.add(file.getName());
            }
        }
        Collections.sort(fileList);
        return fileList;
    }

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

            if (!isProblemValid(line, fileName, settings) && !hasMod) {
                continue;
            }

            Problem p = parseLineToProblem(line);
            if (p != null) {
                problems.add(p);
            }
        }
        br.close();
        return problems;
    }

    private boolean isProblemValid(String line, String fileName, GameModeSettings settings) {
        String[] parts = line.split("->");
        if (parts.length < 2) return false;
        String solution = parts[1].trim();

        if (solution.matches(".*mod\\s*\\d+$")) return true;

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

        if (settings.avoidPureAddSub) {
            if (!solution.contains("*") && !solution.contains(" / ")) return false;
        }

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

    private static class FractionalOperationFoundException extends RuntimeException {}

    public boolean expressionContainsFractions(String expression) {
        if (expression.contains("mod")) return false;
        try {
            evaluateAndCheck(expression);
        } catch (FractionalOperationFoundException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private Fraction evaluateAndCheck(String subExpression) throws FractionalOperationFoundException {
        subExpression = subExpression.trim();
        if (subExpression.matches(".*mod\\s*\\d+$")) {
            subExpression = subExpression.replaceAll("mod\\s*\\d+$", "").trim();
        }

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
        return parseTokenToFraction(subExpression);
    }

    private int findMainOperatorIndex(String expression) {
        int balance = 0;
        String expr = expression.trim();
        while (expr.length() > 2 && expr.startsWith("(") && expr.endsWith(")")) {
            expr = expr.substring(1, expr.length() - 1).trim();
        }
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
        String[] parts = line.split("->");
        if (parts.length < 2) return null;

        String numberPart = parts[0];
        String solution = parts[1].trim();
        Integer modulus = null;

        Pattern modPattern = Pattern.compile("mod\\s*(\\d+)$");
        Matcher modMatcher = modPattern.matcher(solution);
        if (modMatcher.find()) {
            try {
                modulus = Integer.parseInt(modMatcher.group(1));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        Pattern pattern = Pattern.compile("\\['(.*?)'\\]");
        Matcher matcher = pattern.matcher(numberPart);

        if (matcher.find()) {
            try {
                String numbersString = matcher.group(1);
                String[] numberTokens = numbersString.split("', '");
                List<Fraction> fractions = new ArrayList<>();
                for (String token : numberTokens) {
                    fractions.add(parseTokenToFraction(token.replace("'", "").trim()));
                }
                return new Problem(fractions, solution, line, modulus);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }

    // [核心修复] 恢复复数解析逻辑
    private Fraction parseTokenToFraction(String token) {
        token = token.replace("(", "").replace(")", "");

        // 优先使用 Fraction 类自己的解析方法，如果它可用且健壮
        // 这里为了保险，恢复了之前手动解析的逻辑，你可以选择直接 return Fraction.parse(token);

        if (token.contains("i")) {
            long realPart = 0;
            long imagPart = 0;
            if (token.equals("i")) imagPart = 1;
            else if (token.equals("-i")) imagPart = -1;
            else if (!token.contains("+") && !token.substring(1).contains("-")) {
                imagPart = Long.parseLong(token.replace("i", ""));
            } else {
                int operatorIndex = Math.max(token.indexOf('+'), token.substring(1).indexOf('-') + 1);
                realPart = Long.parseLong(token.substring(0, operatorIndex));
                String imagStr = token.substring(operatorIndex).replace("i", "");
                if (imagStr.equals("+")) imagPart = 1;
                else if (imagStr.equals("-")) imagPart = -1;
                else imagPart = Long.parseLong(imagStr);
            }
            return new Fraction(realPart, imagPart, 1);
        } else if (token.contains("/")) {
            String[] fracParts = token.split("/");
            return new Fraction(Long.parseLong(fracParts[0]), Long.parseLong(fracParts[1]));
        } else {
            try {
                return new Fraction(Long.parseLong(token), 1);
            } catch (NumberFormatException e) {
                return new Fraction(0, 1);
            }
        }
    }

    private InputStream getFileInputStream(String fileName) throws IOException {
        File file = new File(context.getFilesDir(), fileName);
        if (file.exists()) return new FileInputStream(file);
        return null;
    }
    private void saveToInternalStorage(String fileName, String content) throws IOException {
        FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
        fos.write(content.getBytes());
        fos.close();
    }
    private String downloadString(String urlString) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            InputStream stream = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(stream));
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append("\n");
            }
            return buffer.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) connection.disconnect();
            if (reader != null) { try { reader.close(); } catch (IOException e) { e.printStackTrace(); } }
        }
    }
}
