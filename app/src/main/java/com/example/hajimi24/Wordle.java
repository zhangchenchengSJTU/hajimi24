package com.example.hajimi24;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

public class Wordle {
    private static final String ALPHABET = "0123456789+-*/()";
    private static final BigInteger P = new BigInteger("5867078670042662046905880468373349642503");
    private static final BigInteger Q = new BigInteger("3717457930205814765494542026447968936707");
    private static final BigInteger E = new BigInteger("65537");

    private final List<String> bdsPrefixList = new ArrayList<>(); // 逻辑长度=length-1 的 BDS 前缀（未解压）
    private volatile boolean prefetching = false;
    private final Object prefetchLock = new Object();

    private final Activity activity;
    private final Dialog dialog;
    private int length;
    private final boolean isBracketsMode;
    private TextView[][] grid;
    private int currentRow = 0, selectedCol = 0, totalRows;

    private String targetExpr;
    private String nextTargetExpr;

    // Step2：rep.txt 解压字典（密文 char -> 明文字符串，通常长度=2）
    private final Map<Character, String> repDict = new HashMap<>();

    // w*.txt 原始条目（按逗号分隔，保持字典序）
    private final List<String> wordleEntries = new ArrayList<>();

    private long lastClickTime = 0;
    private int lastClickCol = -1;

    public Wordle(Activity activity, int length, boolean isBracketsMode, String customTarget) {
        this.activity = activity;
        this.length = length;
        this.isBracketsMode = isBracketsMode;
        this.dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.dialog.setContentView(R.layout.dialog_wordle);

        if (customTarget != null) {
            this.targetExpr = customTarget.toUpperCase();
            this.length = targetExpr.length();
            initUI();
        } else {
            Toast.makeText(activity, "正在初始化 24点 题库...", Toast.LENGTH_SHORT).show();
            loadGameData();
        }
    }

    /**
     * Step2：读取 rep.txt，构建解压字典
     * rep.txt 形如：a)*,b+1,c-1,d*1,...
     * 每一段：首字符=加密字符，后面=解压后的字符串（通常2个字符）
     */
    private void loadRepDict() throws Exception {
        repDict.clear();
        String dictText = readAssetAllText("wordle/rep.txt").trim();
        if (dictText.isEmpty()) return;

        String[] segs = dictText.split(",");
        for (String seg : segs) {
            if (seg == null) continue;
            seg = seg.trim();
            if (seg.length() < 2) continue;
            char key = seg.charAt(0);
            String val = seg.substring(1); // 后续都是明文（通常2位）
            repDict.put(key, val);
        }
    }

    /**
     * Step1：读取 w{length}.txt，并按逗号拆成条目列表
     */
    private void loadWordleEntries() throws Exception {
        wordleEntries.clear();
        String path = "wordle/w" + length + ".txt";
        String text = readAssetAllText(path);
        // 防御：去掉空白
        text = text.replace("\n", "").replace("\r", "").trim();
        if (text.isEmpty()) return;

        String[] parts = text.split(",");
        for (String p : parts) {
            if (p == null) continue;
            p = p.trim();
            if (!p.isEmpty()) wordleEntries.add(p);
        }
    }

    private String readAssetAllText(String assetPath) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(activity.getAssets().open(assetPath)));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append("\n");
        br.close();
        return sb.toString();
    }

    /**
     * 总入口：加载 rep.txt + w*.txt，然后按 Step1~Step4 选题
     */
    private void loadGameData() {
        new Thread(() -> {
            try {
                loadRepDict();
                loadWordleEntries();
                buildBdsPrefixList();

                targetExpr = pickAndRestore(); // Step1~4
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (targetExpr != null) {
                        initUI();
                        fillNextSlotAsync();
                    } else {
                        Toast.makeText(activity, "题库解析失败", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(dialog::dismiss);
            }
        }).start();
    }

    private void buildBdsPrefixList() {
        bdsPrefixList.clear();
        String currentBDS = "";
        int targetLogicalLen = this.length - 1;

        for (String suffix : wordleEntries) {
            if (suffix == null) continue;
            suffix = suffix.trim();
            if (suffix.isEmpty()) continue;

            int suffixUnit = getLogicalLen(suffix);
            int prefixUnit = targetLogicalLen - suffixUnit;
            if (prefixUnit < 0) continue; // 防御：异常条目直接跳过

            currentBDS = getLogicalPrefix(currentBDS, prefixUnit) + suffix;

            // 无括号模式：提前过滤掉“解压后含括号”的前缀（不用 expand 全串也行）
            if (!isBracketsMode && bdsContainsBracket(currentBDS)) continue;

            bdsPrefixList.add(currentBDS);
        }
    }

    private boolean bdsContainsBracket(String bds) {
        for (int i = 0; i < bds.length(); i++) {
            char c = bds.charAt(i);
            String rep = repDict.get(c);
            if (rep != null) {
                if (rep.indexOf('(') >= 0 || rep.indexOf(')') >= 0) return true;
            } else {
                if (c == '(' || c == ')') return true;
            }
        }
        return false;
    }

    /**
     * Step1~Step4：随机抽取一个索引；按字典序复原该题；解压；穷举末位使=24
     */
    private String pickAndRestore() {
        if (bdsPrefixList.isEmpty()) return null;
        Random r = new Random();

        int tries = 120; // 现在不用很大
        for (int t = 0; t < tries; t++) {
            String bds = bdsPrefixList.get(r.nextInt(bdsPrefixList.size()));
            String expandedPrefix = expandByRep(bds);
            String full = bruteForceLastChar(expandedPrefix);
            if (full == null) continue;

            if (!isBracketsMode && (full.contains("(") || full.contains(")"))) continue;
            return full;
        }
        return null;
    }


    /**
     * Step3：依照字典顺序从 0 读到 idx，使用 BDS 差分规则复原“未解压”的前缀串（逻辑长度=length-1）
     */
    private String restoreBdsPrefixAtIndex(int idx) {
        if (idx < 0 || idx >= wordleEntries.size()) return null;

        String currentBDS = "";
        int targetLogicalLen = this.length - 1; // 题目要先复原到 length-1（最后一位留给 Step4）

        for (int i = 0; i <= idx; i++) {
            String suffix = wordleEntries.get(i);
            if (suffix == null) suffix = "";
            suffix = suffix.trim();

            // suffix 自身的“逻辑长度”（解压后字符长度的贡献）
            int suffixUnit = getLogicalLen(suffix);

            // 当前项的前缀需要保留的逻辑长度
            int prefixUnit = targetLogicalLen - suffixUnit;

            currentBDS = getLogicalPrefix(currentBDS, prefixUnit) + suffix;
        }
        return currentBDS;
    }

    /**
     * 逻辑长度计算：
     * - 若字符在 repDict 中：逻辑长度 = 解压后的长度（通常2）
     * - 否则：逻辑长度 = 1*/
    private int getLogicalLen(String s) {
        int len = 0;
        for (char c : s.toCharArray()) {
            String rep = repDict.get(c);
            len += (rep != null) ? rep.length() : 1;
        }
        return len;
    }

    /**
     * 从 BDS 串 s 中截取“逻辑长度”为 targetUnits 的前缀（按上面的逻辑长度规则走）
     */
    private String getLogicalPrefix(String s, int targetUnits) {
        if (targetUnits <= 0) return "";
        int units = 0, i = 0;
        while (i < s.length() && units < targetUnits) {
            char c = s.charAt(i++);
            String rep = repDict.get(c);
            units += (rep != null) ? rep.length() : 1;
        }
        return s.substring(0, i);
    }

    /**
     * Step2：解压第一层（rep.txt）
     */
    private String expandByRep(String bds) {
        StringBuilder sb = new StringBuilder();
        for (char c : bds.toCharArray()) {
            sb.append(repDict.getOrDefault(c, String.valueOf(c)));
        }
        return sb.toString();
    }

    /**
     * Step4：穷举最后一位，使表达式合法且值=24
     * - 无括号模式：只能用数字结尾
     * - 括号模式：允许用数字或 ')' 结尾
     */
    private String bruteForceLastChar(String prefix) {
        if (prefix == null) return null;

        String candidates = isBracketsMode ? "0123456789)" : "0123456789";
        for (char c : candidates.toCharArray()) {
            String test = prefix + c;

            // 长度必须匹配
            if (test.length() != this.length) continue;

            // 先用 isInvalid 做快速过滤，再 evaluate
            try {
                if (isInvalidSilent(test)) continue;
                double v = evaluate(test);
                if (Double.isNaN(v) || Double.isInfinite(v)) continue;
                if (Math.abs(v - 24.0) < 0.001) return test;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean isInvalidSilent(String s) {
        int bal = 0;
        for (char c : s.toCharArray()) {
            if (c == '(') bal++;
            if (c == ')') bal--;
            if (bal < 0) return true;
        }
        if (bal != 0 || s.contains("()")) return true;
        if ("+-*/".contains(s.substring(0,1)) || "+-*/".contains(s.substring(s.length()-1))) return true;

        String[] nums = s.split("[\\+\\-\\*\\/\\(\\)]");
        for (String n : nums) if (!n.isEmpty()) {
            try { if (Integer.parseInt(n) > 13) return true; }
            catch (Exception e) { return true; }
        }
        return false;
    }

    private boolean isInvalidWithToast(String s) {
        int bal = 0;
        for (char c : s.toCharArray()) {
            if (c == '(') bal++;
            if (c == ')') bal--;
            if (bal < 0) { Toast.makeText(activity, "括号错误", Toast.LENGTH_SHORT).show(); return true; }
        }
        if (bal != 0 || s.contains("()")) { Toast.makeText(activity, "括号错误", Toast.LENGTH_SHORT).show(); return true; }
        if ("+-*/".contains(s.substring(0,1)) || "+-*/".contains(s.substring(s.length()-1))) { Toast.makeText(activity, "首尾运算符", Toast.LENGTH_SHORT).show(); return true; }

        String[] nums = s.split("[\\+\\-\\*\\/\\(\\)]");
        for (String n : nums) if (!n.isEmpty()) {
            try { if (Integer.parseInt(n) > 13) { Toast.makeText(activity, "数字>13", Toast.LENGTH_SHORT).show(); return true; } }
            catch (Exception e) { Toast.makeText(activity, "数字错误", Toast.LENGTH_SHORT).show(); return true; }
        }
        return false;
    }

    private void fillNextSlotAsync() {
        synchronized (prefetchLock) {
            if (prefetching) return;
            prefetching = true;
        }
        new Thread(() -> {
            try {
                nextTargetExpr = pickAndRestore();
            } finally {
                synchronized (prefetchLock) { prefetching = false; }
            }
        }).start();
    }


    private void prepareNewLevel() {
        targetExpr = (nextTargetExpr != null) ? nextTargetExpr : pickAndRestore();
        if (targetExpr == null) { dialog.dismiss(); return; }
        currentRow = 0; selectedCol = 0;
        for (int r = 0; r <= totalRows; r++) {
            for (int c = 0; c < length; c++) {
                grid[r][c].setText("");
                if (r == totalRows) grid[r][c].setVisibility(View.INVISIBLE);
                updateBoxStyle(grid[r][c], r, c, (r == 0 && c == 0));
            }
        }
        fillNextSlotAsync();
    }

    private void initUI() {
        float density = activity.getResources().getDisplayMetrics().density;
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int boxSize = (int) Math.min(48 * density, (screenWidth - 100 * density) / (float) length);
        totalRows = (screenHeight - (int)(280 * density)) / (boxSize + (int)(8 * density)) - 1;
        if (totalRows < 5) totalRows = 5;

        GridLayout gridLayout = dialog.findViewById(R.id.wordle_grid);
        gridLayout.removeAllViews();
        gridLayout.setColumnCount(length);
        gridLayout.setRowCount(totalRows + 1);
        grid = new TextView[totalRows + 1][length];

        for (int r = 0; r <= totalRows; r++) {
            for (int c = 0; c < length; c++) {
                TextView tv = new TextView(activity);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(r), GridLayout.spec(c));
                lp.width = boxSize; lp.height = boxSize; lp.setMargins(2, 2, 2, 2);
                tv.setLayoutParams(lp); tv.setGravity(Gravity.CENTER);
                tv.setTextSize(length > 9 ? 14 : 18); tv.setTextColor(Color.WHITE);
                tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                if (r == totalRows) tv.setVisibility(View.INVISIBLE);
                updateBoxStyle(tv, r, c, (r == 0 && c == 0));
                final int fr = r, fc = c; tv.setOnClickListener(v -> onBoxClicked(fr, fc));
                grid[r][c] = tv; gridLayout.addView(tv);
            }
        }

        LinearLayout kb = dialog.findViewById(R.id.keyboard_container);
        kb.removeAllViews();
        int unitW = (int)(38 * density), marginW = (int)(2 * density);
        int leftW = 6 * (unitW + marginW * 2), rightW = 2 * (unitW + marginW * 2);
        String[][] keys = isBracketsMode ?
                new String[][]{{"1","2","3","4","5","(","+","-"},{"6","7","8","9","0",")","*","/"},{"跳过","答案","回删","确定"}} :
                new String[][]{{"1","2","3","4","5","GAP","+","-"},{"6","7","8","9","0","GAP","*","/"},{"跳过","答案","回删","确定"}};

        for (int i = 0; i < keys.length; i++) {
            LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER);
            for (String k : keys[i]) {
                if (k.equals("GAP")) { row.addView(new View(activity), new LinearLayout.LayoutParams(unitW + marginW * 2, 1)); continue; }
                Button b = new Button(activity); b.setText(k.replace("*","×").replace("/","÷"));
                b.setPadding(0,0,0,0); b.setAllCaps(false);
                int bw = (i == 2) ? (k.equals("确定") ? rightW - marginW * 2 : (leftW / 3) - marginW * 2) : unitW;
                row.addView(b, new LinearLayout.LayoutParams(bw, (int)(55*density)));
                b.setOnClickListener(v -> onKeyPress(k));
            }
            kb.addView(row);
        }

        dialog.show();
    }

    private void onKeyPress(String key) {
        if (key.equals("确定")) submitGuess();
        else if (key.equals("回删")) {
            grid[currentRow][selectedCol].setText("");
            if (selectedCol > 0) { updateBoxStyle(grid[currentRow][selectedCol], currentRow, selectedCol, false); selectedCol--; updateBoxStyle(grid[currentRow][selectedCol], currentRow, selectedCol, true); }
        } else if (key.equals("跳过")) prepareNewLevel();
        else if (key.equals("答案")) showAnswerInGrid();
        else { grid[currentRow][selectedCol].setText(key); if (selectedCol < length - 1) onBoxClicked(currentRow, selectedCol + 1); }
    }

    private void submitGuess() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) sb.append(grid[currentRow][i].getText());
        String guess = sb.toString();
        if (guess.length() < length) { Toast.makeText(activity, "未填满", Toast.LENGTH_SHORT).show(); return; }
        if (isInvalidWithToast(guess)) return;
        try {
            if (Math.abs(evaluate(guess) - 24.0) > 0.001) {
                Toast.makeText(activity, "结果非 24", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(activity, "算式错误", Toast.LENGTH_SHORT).show();
            return;
        }
        performColoring(guess);
        if (guess.equals(targetExpr)) showWinDialog();
        else if (++currentRow >= totalRows) { showAnswerInGrid(); new Handler().postDelayed(this::prepareNewLevel, 3000); }
        else { selectedCol = 0; onBoxClicked(currentRow, 0); }
    }

    private void showWinDialog() {
        new AlertDialog.Builder(activity).setTitle("🎉 挑战成功!")
                .setMessage("正确算式: " + targetExpr + "\n你仅用了 " + (currentRow + 1) + " 次尝试")
                .setCancelable(false)
                .setPositiveButton("分享", (d, w) -> {
                    String code = encryptRSA(targetExpr);
                    String shareText = "我用 " + (currentRow + 1) + " 次就解密了哈基米的 wordle 题目: #" + code + "#, 你也来试试吧";
                    ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE))
                            .setPrimaryClip(ClipData.newPlainText("W", shareText));
                    Toast.makeText(activity, "已复制分享语", Toast.LENGTH_SHORT).show();
                    prepareNewLevel();
                })
                .setNegativeButton("继续", (d, w) -> prepareNewLevel()).show();
    }

    public static String encryptRSA(String expr) {
        try {
            BigInteger n = P.multiply(Q);
            BigInteger m = BigInteger.valueOf(1);
            for (char c : expr.toCharArray()) m = m.shiftLeft(4).add(BigInteger.valueOf(ALPHABET.indexOf(c)));
            return m.modPow(E, n).toString(36).toUpperCase();
        } catch (Exception e) { return null; }
    }

    public static String decryptRSA(String code) {
        try {
            BigInteger n = P.multiply(Q);
            BigInteger phi = P.subtract(BigInteger.ONE).multiply(Q.subtract(BigInteger.ONE));
            BigInteger d = E.modInverse(phi);
            BigInteger m = new BigInteger(code.toLowerCase(), 36).modPow(d, n);
            StringBuilder sb = new StringBuilder();
            while (m.compareTo(BigInteger.valueOf(1)) > 0) {
                sb.append(ALPHABET.charAt(m.and(BigInteger.valueOf(15)).intValue()));
                m = m.shiftRight(4);
            }
            return sb.reverse().toString();
        } catch (Exception e) { return null; }
    }

    public boolean isInvalid(String s) {
        int bal = 0;
        for (char c : s.toCharArray()) {
            if (c == '(') bal++;
            if (c == ')') bal--;
            if (bal < 0) { Toast.makeText(activity, "括号错误", Toast.LENGTH_SHORT).show(); return true; }
        }
        if (bal != 0 || s.contains("()")) { Toast.makeText(activity, "括号错误", Toast.LENGTH_SHORT).show(); return true; }
        if ("+-*/".contains(s.substring(0,1)) || "+-*/".contains(s.substring(s.length()-1))) { Toast.makeText(activity, "首尾运算符", Toast.LENGTH_SHORT).show(); return true; }
        String[] nums = s.split("[\\+\\-\\*\\/\\(\\)]");
        for (String n : nums) if (!n.isEmpty()) {
            try { if (Integer.parseInt(n) > 13) { Toast.makeText(activity, "数字>13", Toast.LENGTH_SHORT).show(); return true; } }
            catch (Exception e) { Toast.makeText(activity, "数字错误", Toast.LENGTH_SHORT).show(); return true; }
        }
        return false;
    }

    public double evaluate(String s) {
        Stack<Double> nums = new Stack<>();
        Stack<Character> ops = new Stack<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                StringBuilder sb = new StringBuilder();
                while (i < s.length() && Character.isDigit(s.charAt(i))) sb.append(s.charAt(i++));
                nums.push(Double.parseDouble(sb.toString()));
                i--;
            } else if (c == '(') ops.push(c);
            else if (c == ')') {
                while (ops.peek() != '(') nums.push(applyOp(ops.pop(), nums.pop(), nums.pop()));
                ops.pop();
            } else if ("+-*/".indexOf(c) != -1) {
                while (!ops.isEmpty() && hasPrecedence(c, ops.peek()))
                    nums.push(applyOp(ops.pop(), nums.pop(), nums.pop()));
                ops.push(c);
            }
        }
        while (!ops.isEmpty()) nums.push(applyOp(ops.pop(), nums.pop(), nums.pop()));
        return nums.pop();
    }

    private boolean hasPrecedence(char op1, char op2) {
        if (op2 == '(' || op2 == ')') return false;
        return !((op1 == '*' || op1 == '/') && (op2 == '+' || op2 == '-'));
    }

    private double applyOp(char op, double b, double a) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/': return a / b;
        }
        return 0;
    }

    private void onBoxClicked(int r, int c) {
        if (r != currentRow) return;
        long ct = System.currentTimeMillis();
        if (c == lastClickCol && (ct - lastClickTime) < 300) grid[r][c].setText("");
        else {
            updateBoxStyle(grid[currentRow][selectedCol], currentRow, selectedCol, false);
            selectedCol = c;
            updateBoxStyle(grid[currentRow][selectedCol], currentRow, selectedCol, true);
        }
        lastClickTime = ct; lastClickCol = c;
    }

    private void updateBoxStyle(TextView tv, int r, int c, boolean sel) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(8);
        if (r < currentRow) return;
        if (sel && r == currentRow) { gd.setStroke(4, Color.YELLOW); gd.setColor(Color.parseColor("#4A4A4C")); }
        else { gd.setStroke(2, Color.GRAY); gd.setColor(Color.parseColor("#3A3A3C")); }
        tv.setBackground(gd);
    }

    private void performColoring(String guess) {
        boolean[] used = new boolean[length];
        int[] colors = new int[length];
        for (int i=0; i<length; i++) if (guess.charAt(i) == targetExpr.charAt(i)) { colors[i] = Color.parseColor("#538D4E"); used[i] = true; }
        for (int i=0; i<length; i++) {
            if (colors[i] != 0) continue;
            colors[i] = Color.parseColor("#333333");
            for (int j=0; j<length; j++) if (!used[j] && guess.charAt(i) == targetExpr.charAt(j)) { colors[i] = Color.parseColor("#B59F3B"); used[j] = true; break; }
        }
        for (int i=0; i<length; i++) {
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(8);
            gd.setColor(colors[i]);
            grid[currentRow][i].setBackground(gd);
        }
    }

    private void showAnswerInGrid() {
        if (targetExpr == null) return;
        for (int i = 0; i < length; i++) {
            TextView tv = grid[totalRows][i];
            if (i < targetExpr.length()) tv.setText(String.valueOf(targetExpr.charAt(i)));
            tv.setVisibility(View.VISIBLE);
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(8);
            gd.setColor(Color.parseColor("#538D4E"));
            tv.setBackground(gd);
        }
    }

    public void show() {}
}
