package com.example.hajimi24;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SidebarLogic {

    private final Activity activity;
    private final DrawerLayout drawerLayout;
    private final NavigationView navigationView;
    private final ProblemRepository repository;
    private final ActionCallback callback;
    private final GameModeSettings gameModeSettings;

    private boolean isCurrentModeRandom = true;

    public interface ActionCallback {
        void onRandomMode(int count);
        void onLoadFile(String fileName);
        void onSettingsChanged();
    }

    public SidebarLogic(Activity activity, DrawerLayout drawerLayout,
                        NavigationView navigationView, ProblemRepository repository,
                        ActionCallback callback) {
        this.activity = activity;
        this.drawerLayout = drawerLayout;
        this.navigationView = navigationView;
        this.repository = repository;
        this.callback = callback;
        this.gameModeSettings = new GameModeSettings();
    }

    public GameModeSettings getGameModeSettings() {
        return this.gameModeSettings;
    }

    public void setup() {
        refreshMenu();
        navigationView.setNavigationItemSelectedListener(item -> {
            String t = item.getTitle().toString();
            if (t.contains("游戏说明书")) {
                showHelpDialog();
            } else if (t.contains("从 GitHub 更新")) {
                syncFromGitHub();
            } else if (t.contains("模式设定")) {
                showModeSettingsDialog();
            } else if (t.contains("24点计算器")) {
                showCalculatorDialog();
            } else {
                if (t.contains("随机")) {
                    isCurrentModeRandom = true;
                    if (t.contains("4数")) callback.onRandomMode(4);
                    else callback.onRandomMode(5);
                } else if (t.contains("📄")) {
                    isCurrentModeRandom = false;
                    callback.onLoadFile(t.substring(t.indexOf(" ") + 1));
                }
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return true;
        });
    }

    public void refreshMenu() {
        Menu menu = navigationView.getMenu();
        menu.clear();
        menu.add(Menu.NONE, 888, Menu.NONE, "📖 游戏说明书");
        menu.add(Menu.NONE, 999, Menu.NONE, "☁️ 从 GitHub 更新题库");
        menu.add(Menu.NONE, 777, Menu.NONE, "⚙️ 模式设定");
        menu.add(Menu.NONE, 666, Menu.NONE, "🧮 24点计算器");
        menu.add(Menu.NONE, 0, Menu.NONE, "🎲 随机 (4数)");
        menu.add(Menu.NONE, 1, Menu.NONE, "🎲 随机 (5数)");
        List<String> files = repository.getAvailableFiles();
        if (files != null) {
            int id = 2;
            for (String f : files) menu.add(Menu.NONE, id++, Menu.NONE, "📄 " + f);
        }
    }

    // --- 计算器弹窗逻辑 ---
    private void showCalculatorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("24点计算器");

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = 40;
        layout.setPadding(padding, padding, padding, padding);

        final EditText etInput = new EditText(activity);
        etInput.setHint("请输入数字 (例如 3 3 8 8)\n支持复数 (3i, i3, 1+2i)");
        etInput.setMinLines(2);
        layout.addView(etInput);

        LinearLayout buttonLayout = new LinearLayout(activity);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setPadding(0, 20, 0, 0);

        Button btnCalcAll = new Button(activity);
        btnCalcAll.setText("计算所有解");

        Button btnCalc10 = new Button(activity);
        btnCalc10.setText("计算前 10 个");

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnParams.setMargins(5, 0, 5, 0);

        buttonLayout.addView(btnCalcAll, btnParams);
        buttonLayout.addView(btnCalc10, btnParams);

        layout.addView(buttonLayout);

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 500);
        scrollParams.topMargin = 20;
        scrollView.setLayoutParams(scrollParams);

        final TextView tvResult = new TextView(activity);
        tvResult.setTextIsSelectable(true);
        tvResult.setPadding(10, 10, 10, 10);
        scrollView.addView(tvResult);

        layout.addView(scrollView);

        builder.setView(layout);
        builder.setNegativeButton("关闭", null);

        btnCalcAll.setOnClickListener(v -> performCalculation(etInput.getText().toString(), false, tvResult));
        btnCalc10.setOnClickListener(v -> performCalculation(etInput.getText().toString(), true, tvResult));

        builder.create().show();
    }

    // --- 统一的计算执行逻辑 ---
    private void performCalculation(String input, boolean limit10, TextView tvResult) {
        try {
            List<Fraction> nums = parseInputString(input);

            if (nums.isEmpty()) {
                tvResult.setText("请输入有效的数字");
                return;
            }

            if (nums.size() > 5) {
                tvResult.setText("❌ 错误: 最多只允许输入 5 个数\n当前检测到 " + nums.size() + " 个数");
                return;
            }

            tvResult.setText("正在计算...");

            new Thread(() -> {
                // 1. 获取所有原始解 (使用 rawSolutions 变量名)
                List<String> rawSolutions = Solver.solveAll(nums);

                // 2. 调用去重逻辑 (结果赋值给 solutions)
                List<String> solutions = SolutionNormalizer.distinct(rawSolutions);

                // 3. 排序
                Collections.sort(solutions, (s1, s2) -> Integer.compare(s1.length(), s2.length()));

                final List<String> displayList;
                boolean isTruncated = false;

                if (limit10 && solutions.size() > 10) {
                    displayList = solutions.subList(0, 10);
                    isTruncated = true;
                } else {
                    displayList = solutions;
                }

                boolean finalIsTruncated = isTruncated;
                activity.runOnUiThread(() -> {
                    if (displayList.isEmpty()) {
                        tvResult.setText("无解");
                    } else {
                        SpannableStringBuilder ssb = new SpannableStringBuilder();
                        if (finalIsTruncated) {
                            ssb.append("展示前 10 个解 (共 ").append(String.valueOf(solutions.size())).append(" 个):\n\n");
                        } else {
                            ssb.append("共找到 ").append(String.valueOf(solutions.size())).append(" 种解法:\n\n");
                        }

                        for(int i=0; i<displayList.size(); i++) {
                            String s = displayList.get(i);
                            ssb.append("[").append(String.valueOf(i+1)).append("] ");

                            Spanned styledSol = ExpressionHelper.formatAnswer(s, nums);
                            ssb.append(styledSol);
                            ssb.append("\n");
                        }
                        tvResult.setText(ssb);
                    }
                });
            }).start();

        } catch (Exception e) {
            tvResult.setText("输入解析错误: " + e.getMessage());
        }
    }

    private List<Fraction> parseInputString(String input) {
        List<Fraction> list = new ArrayList<>();
        String[] parts = input.split("[^0-9+\\-*/iIjJ]+");
        for (String p : parts) {
            p = p.trim();
            if (!p.isEmpty()) {
                list.add(parseTokenToFraction(p));
            }
        }
        return list;
    }

    private Fraction parseTokenToFraction(String token) {
        token = token.replace("(", "").replace(")", "").replace("（", "").replace("）", "");
        token = token.replace("[", "").replace("]", "");
        token = token.replace("{", "").replace("}", "");
        token = token.replace("【", "").replace("】", "");
        token = token.replace("I", "i").replace("j", "i").replace("J", "i");
        token = token.replace("*", "");

        if (token.contains("i")) {
            long realPart = 0;
            long imagPart = 0;

            if (token.equals("i")) {
                imagPart = 1;
            } else if (token.equals("-i")) {
                imagPart = -1;
            } else {
                boolean hasRealPart = false;
                int splitIndex = -1;
                for (int k = 1; k < token.length(); k++) {
                    char c = token.charAt(k);
                    if (c == '+' || c == '-') {
                        hasRealPart = true;
                        splitIndex = k;
                        break;
                    }
                }

                if (!hasRealPart) {
                    String numStr = token.replace("i", "");
                    if (numStr.isEmpty()) imagPart = 1;
                    else if (numStr.equals("+")) imagPart = 1;
                    else if (numStr.equals("-")) imagPart = -1;
                    else imagPart = Long.parseLong(numStr);
                } else {
                    String realStr = token.substring(0, splitIndex);
                    String imagStr = token.substring(splitIndex);
                    realPart = Long.parseLong(realStr);
                    String iValStr = imagStr.replace("i", "");
                    if (iValStr.equals("+")) imagPart = 1;
                    else if (iValStr.equals("-")) imagPart = -1;
                    else imagPart = Long.parseLong(iValStr);
                }
            }
            return new Fraction(realPart, imagPart, 1);
        } else if (token.contains("/")) {
            String[] fracParts = token.split("/");
            return new Fraction(Long.parseLong(fracParts[0]), Long.parseLong(fracParts[1]));
        } else {
            return new Fraction(Long.parseLong(token), 1);
        }
    }

    private void syncFromGitHub() {
        Menu menu = navigationView.getMenu();
        MenuItem updateItem = menu.findItem(999);
        if (updateItem != null) updateItem.setTitle("⏳ 正在连接 GitHub...");
        repository.syncFromGitHub(new ProblemRepository.SyncCallback() {
            @Override public void onProgress(String fileName, int current, int total) { activity.runOnUiThread(() -> { if (updateItem != null) updateItem.setTitle("⬇️ " + current + "/" + total); }); }
            @Override public void onSuccess(int count) { activity.runOnUiThread(() -> { if (updateItem != null) updateItem.setTitle("✅ 完成"); Toast.makeText(activity, "更新完成", Toast.LENGTH_SHORT).show(); refreshMenu(); }); }
            @Override public void onFail(String error) { activity.runOnUiThread(() -> { if (updateItem != null) updateItem.setTitle("❌ 失败"); Toast.makeText(activity, error, Toast.LENGTH_SHORT).show(); }); }
        });
    }

    private void showHelpDialog() {
        CharSequence helpContent = MarkdownUtils.loadMarkdownFromAssets(activity, "help.md");
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("游戏指南").setMessage(helpContent).setPositiveButton("开始挑战", null).create();
        dialog.show();
        TextView msgView = dialog.findViewById(android.R.id.message);
        if (msgView != null) msgView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }

    private void showModeSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        LayoutInflater inflater = activity.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_mode_settings, null);
        builder.setView(dialogView);

        SwitchCompat switchAvoidAddSub = dialogView.findViewById(R.id.switch_avoid_add_sub);
        SwitchCompat switchMustHaveDivision = dialogView.findViewById(R.id.switch_must_have_division);
        SwitchCompat switchAvoidTrivialMul = dialogView.findViewById(R.id.switch_avoid_trivial_mul);
        SwitchCompat switchRequireFrac = dialogView.findViewById(R.id.switch_require_fraction_calc);
        SwitchCompat switchRequireStorm = dialogView.findViewById(R.id.switch_require_division_storm);
        TextView tvWarning = dialogView.findViewById(R.id.tv_warning_random);

        switchAvoidAddSub.setChecked(gameModeSettings.avoidPureAddSub);
        switchMustHaveDivision.setChecked(gameModeSettings.mustHaveDivision);
        switchAvoidTrivialMul.setChecked(gameModeSettings.avoidTrivialFinalMultiply);
        switchRequireFrac.setChecked(gameModeSettings.requireFractionCalc);
        switchRequireStorm.setChecked(gameModeSettings.requireDivisionStorm);

        Runnable updateVisibility = () -> {
            if (isCurrentModeRandom) {
                switchAvoidAddSub.setVisibility(View.GONE);
                switchMustHaveDivision.setVisibility(View.GONE);
                switchAvoidTrivialMul.setVisibility(View.GONE);
                switchRequireFrac.setVisibility(View.GONE);
                switchRequireStorm.setVisibility(View.GONE);

                if (tvWarning != null) {
                    tvWarning.setVisibility(View.VISIBLE);
                    tvWarning.setText("🚫 高质量出题仅在加载题库文件时可用, 请先从侧边栏选择一个文件");
                }
                return;
            }

            if (tvWarning != null) tvWarning.setVisibility(View.GONE);

            switchAvoidAddSub.setVisibility(View.VISIBLE);
            boolean layer1Active = switchAvoidAddSub.isChecked();
            int layer2Visibility = layer1Active ? View.VISIBLE : View.GONE;
            switchMustHaveDivision.setVisibility(layer2Visibility);
            switchAvoidTrivialMul.setVisibility(layer2Visibility);
            boolean mustDiv = switchMustHaveDivision.isChecked();
            boolean avoidTrivial = switchAvoidTrivialMul.isChecked();
            int layer3Visibility = (layer1Active && mustDiv && avoidTrivial) ? View.VISIBLE : View.GONE;
            switchRequireFrac.setVisibility(layer3Visibility);
            switchRequireStorm.setVisibility(layer3Visibility);
        };

        switchAvoidAddSub.setOnCheckedChangeListener((b, c) -> updateVisibility.run());
        switchMustHaveDivision.setOnCheckedChangeListener((b, c) -> updateVisibility.run());
        switchAvoidTrivialMul.setOnCheckedChangeListener((b, c) -> updateVisibility.run());
        updateVisibility.run();

        builder.setTitle("模式设定")
                .setPositiveButton("确定", (dialog, id) -> {
                    gameModeSettings.avoidPureAddSub = switchAvoidAddSub.isChecked();
                    gameModeSettings.mustHaveDivision = switchMustHaveDivision.isChecked();
                    gameModeSettings.avoidTrivialFinalMultiply = switchAvoidTrivialMul.isChecked();
                    gameModeSettings.requireFractionCalc = switchRequireFrac.isChecked();
                    gameModeSettings.requireDivisionStorm = switchRequireStorm.isChecked();
                    if (callback != null) {
                        callback.onSettingsChanged();
                    }
                })
                .setNegativeButton("取消", (dialog, id) -> dialog.cancel());
        builder.create().show();
    }
}
