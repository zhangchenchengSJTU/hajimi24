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

            if (!isProblemValid(line, fileName, settings)) {
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
        if (parts.length < 2) return true;
        String solution = parts[1].trim();

        if (settings.avoidPureAddSub || settings.mustHaveDivision || settings.avoidTrivialFinalMultiply) {
            int mainOperatorIndex = findMainOperatorIndex(solution);
            char mainOperator = ' ';
            if (mainOperatorIndex != -1) {
                mainOperator = solution.charAt(mainOperatorIndex);
            }

            if (settings.avoidPureAddSub) {
                if (!solution.contains("*") && !solution.contains(" / ")) {
                    return false;
                }
            }

            if (settings.mustHaveDivision) {
                if (!solution.contains(" / ")) {
                    return false;
                }
            }

            if (settings.avoidTrivialFinalMultiply && mainOperator == '*') {
                String leftOperand = solution.substring(0, mainOperatorIndex).trim();
                String rightOperand = solution.substring(mainOperatorIndex + 1).trim();

                if (!leftOperand.contains("/") && !rightOperand.contains("/")) {
                    return false;
                }
            }
        }

        if (fileName.contains("小于") && settings.numberBound > 0) {
            List<Integer> numericComponents = getIntegerComponents(parts[0]);
            for (int num : numericComponents) {
                if (num > settings.numberBound) {
                    return false;
                }
            }
        }
        return true;
    }

    private int findMainOperatorIndex(String expression) {
        int balance = 0;
        int mainOpIndex = -1;

        String expr = expression.trim();
        while (expr.length() > 2 && expr.startsWith("(") && expr.endsWith(")")) {
            expr = expr.substring(1, expr.length() - 1).trim();
        }

        // 优先寻找最外层的加减法
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') balance++;
            else if (c == '(') balance--;
            else if ((c == '+' || c == '-') && balance == 0) {
                return expression.lastIndexOf(expr) + i;
            }
        }

        // 如果没有，再寻找最外层的乘除法
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') balance++;
            else if (c == '(') balance--;
            else if ((c == '*' || c == '/') && balance == 0) {
                return expression.lastIndexOf(expr) + i;
            }
        }

        return -1;
    }

    private List<Integer> getIntegerComponents(String problemPart) {
        List<Integer> numbers = new ArrayList<>();
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher(problemPart);
        while (m.find()) {
            numbers.add(Integer.parseInt(m.group()));
        }
        return numbers;
    }

    private Problem parseLineToProblem(String line) {
        String[] parts = line.split("->");
        if (parts.length < 2) return null;

        String numberPart = parts[0];
        String solution = parts[1].trim();

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
                return new Problem(fractions, solution);
            } catch (Exception e) {
                System.err.println("Failed to parse line: " + line);
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }

    private Fraction parseTokenToFraction(String token) {
        token = token.replace("(", "").replace(")", "");

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
            return new Fraction(Long.parseLong(token), 1);
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
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
