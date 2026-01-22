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

public class Wordle {
    private final Activity activity;
    private final Dialog dialog;
    private final int length;
    private TextView[][] grid;
    private int currentRow = 0;
    private int selectedCol = 0;
    private String targetExpr;
    private int totalRows;

    private long lastClickTime = 0;
    private int lastClickCol = -1;

    public Wordle(Activity activity, int length) {
        this.activity = activity;
        this.length = length;
        this.dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.dialog.setContentView(R.layout.dialog_wordle);

        loadTargetFromAssets();
        initUI();
    }

    private void loadTargetFromAssets() {
        try {
            String fileName = "wordle/wd" + length + ".txt";
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
                targetExpr = "6*4+5-5".substring(0, Math.min(length, 7));
            }
        } catch (Exception e) {
            targetExpr = "6*4+0".substring(0, Math.min(length, 5));
        }
    }

    private void initUI() {
        TextView tvTitle = dialog.findViewById(R.id.wordle_title);
        tvTitle.setText("24-WORDLE (" + length + ")");

        float density = activity.getResources().getDisplayMetrics().density;
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;

        // 【修改点 4】：自适应宽度计算，增加边距余量防止 length > 7 时溢出
        int horizontalPadding = (int) (80 * density); // 总水平边距
        int boxSize = (int) Math.min(48 * density, (screenWidth - horizontalPadding) / (float) length);

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
                lp.setMargins(4, 4, 4, 4);
                tv.setLayoutParams(lp);
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(length > 9 ? 15 : 18);
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

                if (r == totalRows) {
                    tv.setVisibility(View.INVISIBLE); // 答案行默认隐藏
                }
                updateBoxStyle(tv, r, c, (r == 0 && c == 0));

                final int finalR = r, finalC = c;
                tv.setOnClickListener(v -> onBoxClicked(finalR, finalC));

                grid[r][c] = tv;
                gridLayout.addView(tv);
            }
        }

        // 【修改点 1】：键盘布局紧凑对齐
        LinearLayout kb = dialog.findViewById(R.id.keyboard_container);
        kb.removeAllViews();

        int unitW = (int) (42 * density); // 单个键宽
        int gapW = (int) (20 * density);  // 间距
        int marginW = (int) (3 * density); // 键左右 margin

        // 计算组宽度
        int numGroupW = 5 * (unitW + marginW * 2);
        int opGroupW = 2 * (unitW + marginW * 2);

        String[][] keyLayout = {
                {"1", "2", "3", "4", "5", "GAP", "+", "-"},
                {"6", "7", "8", "9", "0", "GAP", "*", "/"},
                {"跳过", "答案", "回删", "GAP", "确定"}
        };

        for (int rowIndex = 0; rowIndex < keyLayout.length; rowIndex++) {
            String[] row = keyLayout[rowIndex];
            LinearLayout rowLayout = new LinearLayout(activity);
            rowLayout.setGravity(Gravity.CENTER);
            for (String k : row) {
                if (k.equals("GAP")) {
                    View spacer = new View(activity);
                    rowLayout.addView(spacer, new LinearLayout.LayoutParams(gapW, 1));
                    continue;
                }

                Button b = new Button(activity);
                b.setText(k.equals("*") ? "×" : k.equals("/") ? "÷" : k);
                b.setAllCaps(false);
                b.setPadding(0, 0, 0, 0);

                int btnW;
                if (rowIndex == 2) {
                    // 第三行特殊对齐逻辑
                    if (k.equals("确定")) btnW = opGroupW - marginW * 2;
                    else btnW = (numGroupW / 3) - marginW * 2;
                } else {
                    btnW = unitW;
                }

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnW, (int) (55 * density));
                lp.setMargins(marginW, (int)(2*density), marginW, (int)(2*density));
                rowLayout.addView(b, lp);
                b.setOnClickListener(v -> onKeyPress(k));
            }
            kb.addView(rowLayout);
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
            // 【修改点 2】：跳过切换新题
            prepareNewLevel();
            Toast.makeText(activity, "已换题", Toast.LENGTH_SHORT).show();
        } else if (key.equals("答案")) {
            // 【修改点 5】：答案直接显示在最后一行
            showAnswerInGrid();
        } else {
            grid[currentRow][selectedCol].setText(key);
            if (selectedCol < length - 1) {
                onBoxClicked(currentRow, selectedCol + 1);
            }
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
            tv.setTextColor(Color.WHITE);
        }
    }

    private void prepareNewLevel() {
        currentRow = 0;
        selectedCol = 0;
        loadTargetFromAssets();
        for (int r = 0; r <= totalRows; r++) {
            for (int c = 0; c < length; c++) {
                grid[r][c].setText("");
                if (r == totalRows) grid[r][c].setVisibility(View.INVISIBLE);
                updateBoxStyle(grid[r][c], r, c, (r == 0 && c == 0));
            }
        }
    }

    private void submitGuess() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            String val = grid[currentRow][i].getText().toString();
            if (val.isEmpty()) {
                Toast.makeText(activity, "未填满", Toast.LENGTH_SHORT).show();
                return;
            }
            sb.append(val);
        }
        String guess = sb.toString();
        if (isInvalid(guess)) return;

        try {
            if (Math.abs(evaluate(guess) - 24.0) > 0.001) {
                Toast.makeText(activity, "结果非 24", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(activity, "语法错误", Toast.LENGTH_SHORT).show();
            return;
        }

        performColoring(guess);
        if (guess.equals(targetExpr)) {
            Toast.makeText(activity, "🎉 正确!", Toast.LENGTH_SHORT).show();
            // 【修改点 3】：答案正确后，开启新题
            new Handler().postDelayed(this::prepareNewLevel, 1500);
        } else {
            if (++currentRow >= totalRows) {
                showAnswerInGrid();
                new Handler().postDelayed(this::prepareNewLevel, 3000);
            } else {
                selectedCol = 0;
                onBoxClicked(currentRow, 0);
            }
        }
    }

    private boolean isInvalid(String s) {
        if ("+-*/".contains(s.substring(0, 1)) || "+-*/".contains(s.substring(s.length() - 1))) {
            Toast.makeText(activity, "首尾不能是符号", Toast.LENGTH_SHORT).show();
            return true;
        }
        String[] nums = s.split("[\\+\\-\\*/]");
        for (String n : nums) {
            if (!n.isEmpty() && Integer.parseInt(n) > 13) {
                Toast.makeText(activity, "存在 >13 的数", Toast.LENGTH_SHORT).show();
                return true;
            }
        }
        for (int i = 0; i < s.length() - 1; i++) {
            if ("+-*/".contains(s.substring(i, i + 1)) && "+-*/".contains(s.substring(i + 1, i + 2))) {
                Toast.makeText(activity, "连续符号非法", Toast.LENGTH_SHORT).show();
                return true;
            }
        }
        return false;
    }

    private void onBoxClicked(int r, int c) {
        if (r != currentRow) return;
        long currentTime = System.currentTimeMillis();
        if (c == lastClickCol && (currentTime - lastClickTime) < 300) {
            grid[r][c].setText("");
        } else {
            updateBoxStyle(grid[currentRow][selectedCol], currentRow, selectedCol, false);
            selectedCol = c;
            updateBoxStyle(grid[currentRow][selectedCol], currentRow, selectedCol, true);
        }
        lastClickTime = currentTime;
        lastClickCol = c;
    }

    private void updateBoxStyle(TextView tv, int r, int c, boolean isSelected) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(8);
        if (r < currentRow) return;
        if (isSelected && r == currentRow) {
            gd.setStroke(4, Color.YELLOW);
            gd.setColor(Color.parseColor("#4A4A4C"));
        } else {
            gd.setStroke(2, Color.GRAY);
            gd.setColor(Color.parseColor("#3A3A3C"));
        }
        tv.setBackground(gd);
    }

    private void performColoring(String guess) {
        boolean[] used = new boolean[length];
        int[] colors = new int[length];
        for (int i = 0; i < length; i++) {
            if (guess.charAt(i) == targetExpr.charAt(i)) {
                colors[i] = Color.parseColor("#538D4E");
                used[i] = true;
            }
        }
        for (int i = 0; i < length; i++) {
            if (colors[i] != 0) continue;
            colors[i] = Color.parseColor("#333333");
            for (int j = 0; j < length; j++) {
                if (!used[j] && guess.charAt(i) == targetExpr.charAt(j)) {
                    colors[i] = Color.parseColor("#B59F3B");
                    used[j] = true;
                    break;
                }
            }
        }
        for (int i = 0; i < length; i++) {
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(8);
            gd.setColor(colors[i]);
            grid[currentRow][i].setBackground(gd);
        }
    }

    private double evaluate(String expr) {
        List<Double> nums = new ArrayList<>();
        List<Character> ops = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (char c : expr.toCharArray()) {
            if (Character.isDigit(c)) buf.append(c);
            else {
                nums.add(Double.parseDouble(buf.toString()));
                buf.setLength(0);
                ops.add(c);
            }
        }
        nums.add(Double.parseDouble(buf.toString()));
        for (int i = 0; i < ops.size(); i++) {
            char op = ops.get(i);
            if (op == '*' || op == '/') {
                double r = (op == '*') ? nums.get(i) * nums.get(i + 1) : nums.get(i) / nums.get(i + 1);
                nums.set(i, r);
                nums.remove(i + 1);
                ops.remove(i);
                i--;
            }
        }
        double total = nums.get(0);
        for (int i = 0; i < ops.size(); i++) {
            total = (ops.get(i) == '+') ? total + nums.get(i + 1) : total - nums.get(i + 1);
        }
        return total;
    }

    public void show() { dialog.show(); }
}
