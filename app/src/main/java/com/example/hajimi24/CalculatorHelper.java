package com.example.hajimi24;

import android.app.Activity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CalculatorHelper {

    private final Activity activity;
    private static final Integer[] MOD_PRIMES = {29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97};

    public CalculatorHelper(Activity activity) {
        this.activity = activity;
    }


    private Set<Character> extractAllowedOps(String input) {
        Set<Character> ops = new HashSet<>();
        // 匹配最后两个 # 之间的内容
        int lastHash = input.lastIndexOf('#');
        int prevHash = input.lastIndexOf('#', lastHash - 1);

        if (lastHash != -1 && prevHash != -1 && lastHash > prevHash) {
            String config = input.substring(prevHash + 1, lastHash).toLowerCase();
            Set<Character> validChars = new HashSet<>(Arrays.asList('s', 'm', 'x', 'y', 'p'));
            for (char c : config.toCharArray()) {
                if (validChars.contains(c)) {
                    ops.add(c); // 自动去重，且符合“仅处理首个合法字符”的逻辑
                }
            }
        }

        // 如果没有配置或配置为空，默认使用常规四则运算
        if (ops.isEmpty()) {
            ops.add('s'); ops.add('m'); ops.add('x'); ops.add('y');
        }
        return ops;
    }

    public void showCalculatorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("24点计算器");

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = 40;
        layout.setPadding(padding, padding, padding, padding);

        // 输入框
        final EditText etInput = new EditText(activity);
        etInput.setHint("请输入数字 (例如 3 3 8 8)");
        etInput.setMinLines(2);
        layout.addView(etInput);

        // --- 模式选择 (常规/同余/进制) ---
        LinearLayout modeLayout = new LinearLayout(activity);
        modeLayout.setOrientation(LinearLayout.HORIZONTAL);
        modeLayout.setPadding(0, 20, 0, 20);
        modeLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        Spinner spinnerMode = new Spinner(activity);
        String[] modes = {"常规模式", "同余模式", "进制模式"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMode.setAdapter(modeAdapter);
        modeLayout.addView(spinnerMode);
        layout.addView(modeLayout);

        // --- 滑块调节区域 ---
        LinearLayout sliderContainer = new LinearLayout(activity);
        sliderContainer.setOrientation(LinearLayout.VERTICAL);
        sliderContainer.setVisibility(View.GONE);
        sliderContainer.setPadding(0, 20, 0, 20);

        final TextView tvSliderValue = new TextView(activity);
        tvSliderValue.setTextSize(14);
        tvSliderValue.setPadding(10, 0, 0, 5);

        final android.widget.SeekBar seekBar = new android.widget.SeekBar(activity);

        sliderContainer.addView(tvSliderValue);
        sliderContainer.addView(seekBar);
        layout.addView(sliderContainer);

        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    sliderContainer.setVisibility(View.GONE);
                    etInput.setHint("请输入数字 (例如 3 3 8 8)");
                } else if (position == 1) {
                    sliderContainer.setVisibility(View.VISIBLE);
                    seekBar.setMax(MOD_PRIMES.length - 1);
                    seekBar.setProgress(0);
                    tvSliderValue.setText("模数 (n): " + MOD_PRIMES[0]);
                    etInput.setHint("请输入 0 到 n-1 之间的整数");
                } else {
                    sliderContainer.setVisibility(View.VISIBLE);
                    seekBar.setMax(11);
                    seekBar.setProgress(5);
                    tvSliderValue.setText("显示进制: 10");
                    etInput.setHint("请输入对应进制的数字 (支持 A-F)");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean b) {
                if (spinnerMode.getSelectedItemPosition() == 1) {
                    tvSliderValue.setText("模数 (n): " + MOD_PRIMES[p]);
                } else {
                    tvSliderValue.setText("显示进制: " + (p + 5));
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });

        // 按钮布局
        LinearLayout buttonLayout = new LinearLayout(activity);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button btnCalcAll = new Button(activity); btnCalcAll.setText("计算所有解");
        Button btnCalc10 = new Button(activity); btnCalc10.setText("计算前 10 个");
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        btnParams.setMargins(5, 0, 5, 0);
        buttonLayout.addView(btnCalcAll, btnParams);
        buttonLayout.addView(btnCalc10, btnParams);
        layout.addView(buttonLayout);

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 500);
        scrollParams.topMargin = 20;
        scrollView.setLayoutParams(scrollParams);
        final TextView tvResult = new TextView(activity);
        tvResult.setTextIsSelectable(true);
        tvResult.setPadding(10, 10, 10, 10);
        scrollView.addView(tvResult);
        layout.addView(scrollView);

        View.OnClickListener calcListener = v -> {
            int modeIdx = spinnerMode.getSelectedItemPosition();
            Integer modulus = null; int radix = 10; int target = 24;
            if (modeIdx == 1) {
                modulus = MOD_PRIMES[seekBar.getProgress()];
            } else if (modeIdx == 2) {
                radix = seekBar.getProgress() + 5;
                target = 2 * radix + 4;
            }
            performCalculation(etInput.getText().toString(), (v == btnCalc10), tvResult, modulus, radix, target);
        };
        btnCalcAll.setOnClickListener(calcListener);
        btnCalc10.setOnClickListener(calcListener);

        builder.setView(layout);
        builder.setNegativeButton("关闭", null);
        builder.create().show();
    }

    private void performCalculation(String input, boolean limit10, TextView tvResult, Integer modulus, int radix, int target) {
        Set<Character> allowedOps = extractAllowedOps(input);
        try {
            List<Fraction> nums = parseInputString(input, modulus, radix);
            if (nums.isEmpty()) {
                tvResult.setText("请输入有效的数字");
                return;
            }
            if (nums.size() > 5) {
                tvResult.setText("❌ 错误: 最多只允许输入 5 个数");
                return;
            }

            tvResult.setText("正在计算...");
            new Thread(() -> {
                List<String> rawSolutions = Solver.solveAll(nums, modulus, target);
                List<String> solutions = SolutionNormalizer.distinct(rawSolutions);
                Collections.sort(solutions, (s1, s2) -> Integer.compare(s1.length(), s2.length()));

                final List<String> displayList = (limit10 && solutions.size() > 10) ? solutions.subList(0, 10) : solutions;
                boolean isTruncated = limit10 && solutions.size() > 10;

                activity.runOnUiThread(() -> {
                    if (displayList.isEmpty()) {
                        tvResult.setText("无解");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append(isTruncated ? "展示前 10 个解 (共 " : "共找到 ").append(solutions.size()).append(isTruncated ? " 个):\n\n" : " 种解法:\n\n");
                        for(int i=0; i<displayList.size(); i++) {
                            sb.append("[").append(i+1).append("] ").append(displayList.get(i)).append("\n");
                        }
                        tvResult.setText(sb.toString());
                    }
                });
            }).start();
        } catch (Exception e) {
            tvResult.setText("解析错误: " + e.getMessage());
        }
    }

    private List<Fraction> parseInputString(String input, Integer modulus, int radix) throws Exception {
        List<Fraction> list = new ArrayList<>();
        String validCharsRegex;
        boolean isNormalMode = (modulus == null && radix == 10);

        if (isNormalMode) {
            validCharsRegex = "[^0-9a-zA-Z+\\-*/.]+";
            input = input.replaceAll("(?i)\\bA\\b", " 1 ")
                    .replaceAll("(?i)\\bJ\\b", " 11 ")
                    .replaceAll("(?i)\\bQ\\b", " 12 ")
                    .replaceAll("(?i)\\bK\\b", " 13 ");
        } else if (modulus != null) {
            validCharsRegex = "[^0-9+\\-*/.]+";
        } else {
            validCharsRegex = "[^0-9a-fA-F+\\-*/.]+";
        }

        String[] parts = input.split(validCharsRegex);
        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (!isNormalMode && p.toLowerCase().contains("i")) throw new Exception("该模式不支持虚数 (i)");

            String numericOnly = p.replaceAll("[iI+\\-*/().]", "");
            for (char c : numericOnly.toCharArray()) {
                int digitValue = Character.digit(c, radix);
                if (digitValue == -1 || digitValue >= radix) throw new Exception("字符 '" + c + "' 超出范围");
            }
            list.add(Fraction.parse(p, radix));
        }
        return list;
    }
}
