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
import java.util.Stack;

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

// 在 ProblemRepository.java 中

// 在 ProblemRepository.java 中

    private boolean isProblemValid(String line, String fileName, GameModeSettings settings) {
        String[] parts = line.split("->");
        if (parts.length < 2) return false;
        String solution = parts[1].trim();

        // --- “除法风暴” 逻辑的正确实现 ---
        if (settings.requireDivisionStorm) {
            // 步骤 1: 计算 n (输入数字的数量)
            String numbersListString = parts[0];
            int n = 1;
            for (int i = 0; i < numbersListString.length(); i++) {
                if (numbersListString.charAt(i) == ',') {
                    n++;
                }
            }
            if (n < 2) return false;

            // 步骤 2: 精确计算 " / " (带空格的除法运算符) 的数量
            int divisionCount = 0;
            int lastIndex = 0;
            String divisionOperator = " / ";
            while ((lastIndex = solution.indexOf(divisionOperator, lastIndex)) != -1) {
                divisionCount++;
                lastIndex += divisionOperator.length(); // 从找到的运算符之后继续查找
            }

            // 步骤 3: 应用您的新规则
            if (divisionCount < n - 2) {
                return false;
            }
        }

        // --- 恢复并统一您现有的逻辑 ---

        // (您提供的这部分代码保持不变, 因为它已经包含了 `mustHaveDivision` 等检查)
        if (settings.avoidPureAddSub || settings.mustHaveDivision || settings.avoidTrivialFinalMultiply) {
            int mainOperatorIndex = findMainOperatorIndex(solution);
            char mainOperator = ' ';
            if (mainOperatorIndex != -1) {
                mainOperator = solution.charAt(mainOperatorIndex);
            }

            if (settings.avoidPureAddSub) {
                // 保持您原来的正确逻辑: 必须包含乘法或除法运算符
                if (!solution.contains("*") && !solution.contains(" / ")) {
                    return false;
                }
            }

            if (settings.mustHaveDivision) {
                // 保持您原来的正确逻辑: 必须包含除法运算符
                if (!solution.contains(" / ")) {
                    return false;
                }
            }

            if (settings.avoidTrivialFinalMultiply && mainOperator == '*') {
                String leftOperand = solution.substring(0, mainOperatorIndex).trim();
                String rightOperand = solution.substring(mainOperatorIndex + 1).trim();

                // 统一规则: 检查操作数内部是否也包含除法"运算符"
                if (!leftOperand.contains(" / ") && !rightOperand.contains(" / ")) {
                    return false;
                }
            }
        }

        // “必须包含分数运算” 的逻辑 (保持不变, 不受影响)
        if (settings.requireFractionCalc && !expressionContainsFractions(solution)) {
            return false;
        }

        // “数字上界” 的逻辑 (保持不变, 不受影响)
        if (fileName.contains("小于") && settings.numberBound > 0) {
            List<Integer> numericComponents = getIntegerComponents(parts[0]);
            for (int num : numericComponents) {
                if (num > settings.numberBound) {
                    return false;
                }
            }
        }

        // 如果通过了所有检查, 则该题目是合格的
        return true;
    }


    /**
     * 递归检查一个表达式字符串是否在计算过程中产生了分数 (非整数).
     * @param expression - 数学表达式字符串, 例如 "(((1 / 11) + 1) * 22)"
     * @return 如果有分数产生, 返回 true; 否则返回 false.
     */
// 在 ProblemRepository.java 中


    // 在 ProblemRepository.java 类内部

    /**
     * 一个私有的、轻量级的异常, 仅用作信号,
     * 当在递归计算中发现符合条件的加减法时, 立即中断所有计算.
     */
    private static class FractionalOperationFoundException extends RuntimeException {}
// 在 ProblemRepository.java 中

    public boolean expressionContainsFractions(String expression) {
        try {
            // 尝试对整个表达式进行递归求值和检查
            evaluateAndCheck(expression);
        } catch (FractionalOperationFoundException e) {
            // 如果在任何计算步骤中捕获到了我们自定义的 "已找到" 信号,
            // 说明该表达式符合规范.
            return true;
        } catch (Exception e) {
            // 如果发生其他任何计算或解析错误, 判定为不合格.
            return false;
        }
        // 如果计算顺利完成, 且没有抛出 "已找到" 信号, 说明不符合规范.
        return false;
    }

    /**
     * [全新的、基于递归的解析器]
     * 递归地解析并计算表达式, 并在每一步进行检查.
     * @param subExpression 当前要计算的子表达式字符串.
     * @return 计算结果的 Fraction 对象.
     * @throws FractionalOperationFoundException 如果在计算中发现符合条件的加减法.
     */
    private Fraction evaluateAndCheck(String subExpression) throws FractionalOperationFoundException {
        subExpression = subExpression.trim();

        // 基础情况: 如果表达式被一对最外层的括号包围, 去掉括号后递归处理
        if (subExpression.startsWith("(") && subExpression.endsWith(")")) {
            int balance = 0;
            boolean isPaired = true;
            for (int i = 0; i < subExpression.length() - 1; i++) {
                if (subExpression.charAt(i) == '(') balance++;
                else if (subExpression.charAt(i) == ')') balance--;
                if (balance == 0) {
                    isPaired = false;
                    break;
                }
            }
            if (isPaired) {
                return evaluateAndCheck(subExpression.substring(1, subExpression.length() - 1));
            }
        }

        // 寻找主运算符: 从右到左, 先找 + 和 - (低优先级)
        int balance = 0;
        for (int i = subExpression.length() - 1; i >= 0; i--) {
            char c = subExpression.charAt(i);
            if (c == ')') balance++;
            else if (c == '(') balance--;

            // 如果当前字符不在任何括号内, 并且是 '+' 或 '-'
            if (balance == 0 && (c == '+' || c == '-') && i > 0) {
                String leftStr = subExpression.substring(0, i).trim();
                String rightStr = subExpression.substring(i + 1).trim();

                // 确保它不是一元负号 (例如 "-5")
                if (leftStr.isEmpty() || "+-*/(".indexOf(leftStr.charAt(leftStr.length() - 1)) != -1) {
                    continue;
                }

                Fraction leftFrac = evaluateAndCheck(leftStr);
                Fraction rightFrac = evaluateAndCheck(rightStr);

                // --- 在这里进行核心检查! ---
                if (leftFrac.toString().contains("/") || rightFrac.toString().contains("/")) {
                    throw new FractionalOperationFoundException(); // 发现目标, 立即中断!
                }

                if (c == '+') return leftFrac.add(rightFrac);
                else return leftFrac.sub(rightFrac);
            }
        }

        // 如果没有 + 或 -, 再从右到左找 * 和 / (高优先级)
        balance = 0;
        for (int i = subExpression.length() - 1; i >= 0; i--) {
            char c = subExpression.charAt(i);
            if (c == ')') balance++;
            else if (c == '(') balance--;

            if (balance == 0 && (c == '*' || c == '/') && i > 0) {
                String leftStr = subExpression.substring(0, i).trim();
                String rightStr = subExpression.substring(i + 1).trim();

                Fraction leftFrac = evaluateAndCheck(leftStr);
                Fraction rightFrac = evaluateAndCheck(rightStr);

                if (c == '*') return leftFrac.multiply(rightFrac);
                else {
                    if(rightFrac.toString().equals("0")) throw new ArithmeticException("Division by zero");
                    return leftFrac.divide(rightFrac);
                }
            }
        }

        // 基础情况: 如果上面都没有找到操作符, 说明它是一个数字, 直接解析
        return parseTokenToFraction(subExpression);
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
                // 修改此处：传入 line 参数
                return new Problem(fractions, solution, line);
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
