package com.example.hajimi24;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;

public class Wordle {
    private final Activity activity;
    private final Dialog dialog;
    private final int length;
    private final boolean isBracketsMode;
    private TextView[][] grid;
    private int currentRow = 0;
    private int selectedCol = 0;
    private String targetExpr;
    private int totalRows;

    private long lastClickTime = 0;
    private int lastClickCol = -1;

    public Wordle(Activity activity, int length, boolean isBracketsMode) {
        this.activity = activity;
        this.length = length;
        this.isBracketsMode = isBracketsMode;
        this.dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.dialog.setContentView(R.layout.dialog_wordle);

        loadTargetFromAssets();
        initUI();
    }

    private void loadTargetFromAssets() {
        try {
            String suffix = isBracketsMode ? "_b.txt" : ".txt";
            String fileName = "wordle/wd" + length + suffix;
            BufferedReader br = new BufferedReader(new InputStreamReader(activity.getAssets().open(fileName)));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) lines.add(line.trim());
            }
            br.close();
            if (!lines.isEmpty()) {
                targetExpr = lines.get(new Random().nextInt(lines.size()));
            } else {
                targetExpr = isBracketsMode ? "(6*4)+0" : "6*4+0"; // 回退
            }
        } catch (Exception e) {
            targetExpr = isBracketsMode ? "(6*4)+0" : "6*4+0";
        }
    }

    private void initUI() {
        TextView tvTitle = dialog.findViewById(R.id.wordle_title);
        tvTitle.setText(isBracketsMode ? "24-WORDLE (括号版)" : "24-WORDLE (" + length + ")");

        float density = activity.getResources().getDisplayMetrics().density;
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;

        // 【修改点 4】：大幅优化大长度适配，确保 11 个格子也不超出
        int safeMargin = (int) (40 * density);
        int boxSize = (int) Math.min(48 * density, (screenWidth - safeMargin) / (float) length);

        int reservedHeight = (int) (280 * density);
        totalRows = (screenHeight - reservedHeight) / (boxSize + (int) (8 * density)) - 1;
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
                lp.width = boxSize;
                lp.height = boxSize;
                lp.setMargins(2, 2, 2, 2);
                tv.setLayoutParams(lp);
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(length > 8 ? 14 : 18);
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

                if (r == totalRows) tv.setVisibility(View.INVISIBLE);
                updateBoxStyle(tv, r, c, (r == 0 && c == 0));

                final int fr = r, fc = c;
                tv.setOnClickListener(v -> onBoxClicked(fr, fc));
                grid[r][c] = tv;
                gridLayout.addView(tv);
            }
        }

        // 【修改点 1】：键盘布局对齐
        LinearLayout kb = dialog.findViewById(R.id.keyboard_container);
        kb.removeAllViews();

        int unitW = (int) (38 * density);
        int marginW = (int) (2 * density);
        int gapW = (int) (15 * density);

        // 计算组宽
        int leftGroupW = 6 * (unitW + marginW * 2);
        int rightGroupW = 2 * (unitW + marginW * 2);

        String[][] keyLayout = isBracketsMode ?
                new String[][]{{"1","2","3","4","5","(","+","-"},{"6","7","8","9","0",")","*","/"},{"跳过","答案","回删","GAP","确定"}} :
                new String[][]{{"1","2","3","4","5","GAP","+","-"},{"6","7","8","9","0","GAP","*","/"},{"跳过","答案","回删","GAP","确定"}};

        for (int i = 0; i < keyLayout.length; i++) {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER);
            for (String k : keyLayout[i]) {
                if (k.equals("GAP")) {
                    row.addView(new View(activity), new LinearLayout.LayoutParams(gapW, 1));
                    continue;
                }
                Button b = new Button(activity);
                b.setText(k.replace("*","×").replace("/","÷"));
                b.setPadding(0, 0, 0, 0);
                b.setAllCaps(false);

                int bw = unitW;
                if (i == 2) { // 第三行对齐
                    if (k.equals("确定")) bw = rightGroupW + gapW - marginW * 2;
                    else bw = (leftGroupW / 3) - marginW * 2;
                }

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(bw, (int)(55*density));
                lp.setMargins(marginW, (int)(2*density), marginW, (int)(2*density));
                row.addView(b, lp);
                b.setOnClickListener(v -> onKeyPress(k));
            }
            kb.addView(row);
        }
        dialog.findViewById(R.id.btn_close_wordle).setVisibility(View.GONE);
    }

    private void onKeyPress(String key) {
        if (key.equals("确定")) {
            submitGuess();
        } else if (key.equals("回删")) {
            grid[currentRow][selectedCol].setText("");
            if (selectedCol > 0) {
                updateBoxStyle(grid[currentRow][selectedCol], currentRow, selectedCol, false);
                selectedCol--;
                updateBoxStyle(grid[currentRow][selectedCol], currentRow, selectedCol, true);
            }
        } else if (key.equals("跳过")) {
            prepareNewLevel();
        } else if (key.equals("答案")) {
            showAnswerInGrid();
        } else {
            grid[currentRow][selectedCol].setText(key);
            if (selectedCol < length - 1) onBoxClicked(currentRow, selectedCol + 1);
        }
    }

    private void showAnswerInGrid() {
        for (int i = 0; i < length; i++) {
            TextView tv = grid[totalRows][i];
            tv.setText(String.valueOf(targetExpr.charAt(i)));
            tv.setVisibility(View.VISIBLE);
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(8);
            gd.setColor(Color.parseColor("#538D4E"));
            tv.setBackground(gd);
        }
    }

    private void prepareNewLevel() {
        currentRow = 0; selectedCol = 0;
        loadTargetFromAssets();
        for (int r = 0; r <= totalRows; r++) {
            for (int c = 0; c < length; c++) {
                grid[r][c].setText("");
                if (r == totalRows) grid[r][c].setVisibility(View.INVISIBLE);
                updateBoxStyle(grid[r][c], r, c, (r==0 && c==0));
            }
        }
    }

    private void submitGuess() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(grid[currentRow][i].getText());
        String guess = sb.toString();
        if (guess.length() < length) { Toast.makeText(activity, "未填满", Toast.LENGTH_SHORT).show(); return; }
        if (isInvalid(guess)) return;
        try {
            if (Math.abs(evaluate(guess) - 24.0) > 0.001) {
                Toast.makeText(activity, "结果不等于 24", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) { Toast.makeText(activity, "表达式错误", Toast.LENGTH_SHORT).show(); return; }

        performColoring(guess);
        if (guess.equals(targetExpr)) {
            Toast.makeText(activity, "🎉 正确!", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(this::prepareNewLevel, 1500);
        } else if (++currentRow >= totalRows) {
            showAnswerInGrid();
            new Handler().postDelayed(this::prepareNewLevel, 3000);
        } else {
            selectedCol = 0; onBoxClicked(currentRow, 0);
        }
    }

    private boolean isInvalid(String s) {
        // 括号校验
        int bal = 0;
        for (char c : s.toCharArray()) {
            if (c == '(') bal++; if (c == ')') bal--;
            if (bal < 0) { Toast.makeText(activity, "括号顺序错误", Toast.LENGTH_SHORT).show(); return true; }
        }
        if (bal != 0) { Toast.makeText(activity, "括号未闭合", Toast.LENGTH_SHORT).show(); return true; }
        if (s.contains("()")) { Toast.makeText(activity, "存在空括号", Toast.LENGTH_SHORT).show(); return true; }

        if ("+-*/".contains(s.substring(0,1)) || "+-*/".contains(s.substring(s.length()-1))) {
            Toast.makeText(activity, "首尾不能是符号", Toast.LENGTH_SHORT).show(); return true;
        }
        String[] nums = s.split("[\\+\\-\\*\\/\\(\\)]");
        for (String n : nums) if (!n.isEmpty() && Integer.parseInt(n) > 13) {
            Toast.makeText(activity, "存在 >13 的数", Toast.LENGTH_SHORT).show(); return true;
        }
        return false;
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
        for (int i=0; i<length; i++) if (guess.charAt(i) == targetExpr.charAt(i)) {
            colors[i] = Color.parseColor("#538D4E"); used[i] = true;
        }
        for (int i=0; i<length; i++) {
            if (colors[i] != 0) continue;
            colors[i] = Color.parseColor("#333333");
            for (int j=0; j<length; j++) if (!used[j] && guess.charAt(i) == targetExpr.charAt(j)) {
                colors[i] = Color.parseColor("#B59F3B"); used[j] = true; break;
            }
        }
        for (int i=0; i<length; i++) {
            GradientDrawable gd = new GradientDrawable(); gd.setCornerRadius(8);
            gd.setColor(colors[i]); grid[currentRow][i].setBackground(gd);
        }
    }

    private double evaluate(String s) {
        Stack<Double> nums = new Stack<>();
        Stack<Character> ops = new Stack<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                StringBuilder sb = new StringBuilder();
                while (i < s.length() && Character.isDigit(s.charAt(i))) sb.append(s.charAt(i++));
                nums.push(Double.parseDouble(sb.toString())); i--;
            } else if (c == '(') ops.push(c);
            else if (c == ')') {
                while (ops.peek() != '(') nums.push(applyOp(ops.pop(), nums.pop(), nums.pop()));
                ops.pop();
            } else if ("+-*/".indexOf(c) != -1) {
                while (!ops.isEmpty() && hasPrecedence(c, ops.peek())) nums.push(applyOp(ops.pop(), nums.pop(), nums.pop()));
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
            case '+': return a + b; case '-': return a - b;
            case '*': return a * b; case '/': return a / b;
        }
        return 0;
    }

    public void show() { dialog.show(); }
}
