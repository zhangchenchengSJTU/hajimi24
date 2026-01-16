package com.example.hajimi24;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class SidebarLogic {

    private final Activity activity;
    private final DrawerLayout drawerLayout;
    private final NavigationView navigationView;
    private final ProblemRepository repository;
    private final ActionCallback callback;
    private final GameModeSettings gameModeSettings;

    private List<ProblemRepository.RemoteFile> cachedRemoteFiles = null;
    private String currentExplorerPath = "data/";

    public boolean isCurrentModeRandom = true;
    public String currentLoadedFileName = null;

    private static final Integer[] MOD_PRIMES = {29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97};
    private int selectedModulus = 29;

    public interface ActionCallback {
        void onRandomMode(int count);
        void onLoadProblems(List<Problem> problems, String title);
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
        return gameModeSettings;
    }

    public void setup() {
        refreshMenu();

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            String title = item.getTitle().toString();

            if (id == 2000) { // 在线题库按钮
                if (cachedRemoteFiles == null) {
                    fetchRemoteFilesAndShowDialog();
                } else {
                    showFileExplorerDialog();
                }
                return true;
            }

            // 新增：刷新按钮逻辑
            if (id == 999) {
                fetchRemoteFilesAndShowDialog(); // 直接刷新并显示
            }

            else if (id == 888) { showHelpDialog(); }
            else if (id == 777) { showModeSettingsDialog(); }
            else if (id == 666) { showCalculatorDialog(); }
            else if (id == 555) { showThemeSelectionDialog(); }
            else if (title.contains("随机") || title.contains("Random")) {
                isCurrentModeRandom = true;
                currentLoadedFileName = null;
                if (title.contains("3")) callback.onRandomMode(3);
                else if (title.contains("4")) callback.onRandomMode(4);
                else callback.onRandomMode(5);
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return true;
        });
    }

    private void refreshMenu() {
        Menu menu = navigationView.getMenu();
        menu.clear();

        menu.add(Menu.NONE, 2000, Menu.NONE, "📂 在线题库 (浏览与下载)");
        menu.add(Menu.NONE, 999, Menu.NONE, "🔄 刷新目录"); // 新增刷新选项

        menu.add(Menu.NONE, 888, Menu.NONE, "📖 游戏说明书");
        menu.add(Menu.NONE, 777, Menu.NONE, "⚙️ 模式设定");
        menu.add(Menu.NONE, 666, Menu.NONE, "🧮 24点计算器");
        menu.add(Menu.NONE, 555, Menu.NONE, "🎨 主题设置");

        SubMenu randomGroup = menu.addSubMenu("🎲 随机练习");
        randomGroup.add(Menu.NONE, 103, Menu.NONE, "随机 (3数)");
        randomGroup.add(Menu.NONE, 104, Menu.NONE, "随机 (4数)");
        randomGroup.add(Menu.NONE, 105, Menu.NONE, "随机 (5数)");
    }

    // ==========================================
    //  核心逻辑：文件资源管理器 (File Explorer)
    // ==========================================

    private void fetchRemoteFilesAndShowDialog() {
        Toast.makeText(activity, "正在刷新目录...", Toast.LENGTH_SHORT).show();
        repository.fetchRemoteFileTree(new ProblemRepository.MenuDataCallback() {
            @Override
            public void onSuccess(List<ProblemRepository.RemoteFile> files) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    cachedRemoteFiles = files;
                    currentExplorerPath = "data/"; // 刷新后重置目录
                    showFileExplorerDialog();
                    Toast.makeText(activity, "目录已更新", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFail(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(activity, "刷新失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showFileExplorerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);

        // 顶部路径显示
        TextView tvPath = new TextView(activity);
        tvPath.setPadding(40, 30, 40, 10);
        tvPath.setTextSize(14);
        tvPath.setTextColor(activity.getResources().getColor(android.R.color.darker_gray));
        layout.addView(tvPath);

        // 文件列表
        ListView listView = new ListView(activity);
        // 使用 Weight 让 ListView 占据剩余空间，给底部按钮留位置
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listView.setLayoutParams(listParams);
        layout.addView(listView);

        // 底部刷新按钮 (新增)
        Button btnRefresh = new Button(activity);
        btnRefresh.setText("刷新目录");
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRefresh.setLayoutParams(btnParams);
        layout.addView(btnRefresh);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);

        builder.setView(layout);
        builder.setTitle("选择题库文件");
        builder.setNegativeButton("关闭", null);

        AlertDialog dialog = builder.create();

        // 刷新按钮点击事件
        btnRefresh.setOnClickListener(v -> {
            dialog.dismiss();
            fetchRemoteFilesAndShowDialog();
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String itemText = adapter.getItem(position);

            if (itemText.equals(".. (返回上一级)")) {
                if (currentExplorerPath.endsWith("/")) {
                    String temp = currentExplorerPath.substring(0, currentExplorerPath.length() - 1);
                    int lastSlash = temp.lastIndexOf('/');
                    if (lastSlash != -1) {
                        currentExplorerPath = temp.substring(0, lastSlash + 1);
                        updateExplorerView(tvPath, adapter);
                    }
                }
                return;
            }

            if (itemText.startsWith("📁 ")) {
                String folderName = itemText.replace("📁 ", "");
                currentExplorerPath += folderName + "/";
                updateExplorerView(tvPath, adapter);
                return;
            }

            if (itemText.startsWith("📄 ")) {
                String fileName = itemText.replace("📄 ", "");
                String fullPath = currentExplorerPath + fileName;
                dialog.dismiss();
                startDownloadWithProgress(fullPath, fileName);
            }
        });

        dialog.show();
        updateExplorerView(tvPath, adapter);
    }

    private void updateExplorerView(TextView tvPath, ArrayAdapter<String> adapter) {
        tvPath.setText("当前路径: " + currentExplorerPath);

        List<String> items = new ArrayList<>();
        Set<String> folders = new HashSet<>();
        List<String> files = new ArrayList<>();

        if (cachedRemoteFiles != null) {
            for (ProblemRepository.RemoteFile f : cachedRemoteFiles) {
                if (f.path.startsWith(currentExplorerPath)) {
                    String relativePath = f.path.substring(currentExplorerPath.length());
                    int slashIndex = relativePath.indexOf('/');

                    if (slashIndex == -1) {
                        files.add(relativePath);
                    } else {
                        folders.add(relativePath.substring(0, slashIndex));
                    }
                }
            }
        }

        if (!currentExplorerPath.equals("data/")) {
            items.add(".. (返回上一级)");
        }

        List<String> sortedFolders = new ArrayList<>(folders);
        Collections.sort(sortedFolders);
        for (String folder : sortedFolders) items.add("📁 " + folder);

        Collections.sort(files);
        for (String file : files) items.add("📄 " + file);

        adapter.clear();
        adapter.addAll(items);
        adapter.notifyDataSetChanged();
    }

    // ==========================================
    //  下载与进度条逻辑
    // ==========================================

    private void startDownloadWithProgress(String path, String fileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("正在下载 " + fileName);
        builder.setCancelable(false);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        layout.addView(progressBar);

        TextView tvPercent = new TextView(activity);
        tvPercent.setText("0%");
        tvPercent.setGravity(android.view.Gravity.CENTER);
        layout.addView(tvPercent);

        builder.setView(layout);
        AlertDialog progressDialog = builder.create();
        progressDialog.show();

        repository.downloadFileContent(path, gameModeSettings, new ProblemRepository.FileDownloadCallback() {
            @Override
            public void onProgress(int percent) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setProgress(percent);
                    tvPercent.setText(percent + "%");
                });
            }

            @Override
            public void onSuccess(List<Problem> problems, String fileName) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressDialog.dismiss();
                    isCurrentModeRandom = false;
                    currentLoadedFileName = fileName;
                    callback.onLoadProblems(problems, fileName);
                    drawerLayout.closeDrawer(GravityCompat.START);
                    Toast.makeText(activity, "加载成功", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFail(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity, "下载失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ==========================================
    //  其他固定对话框 (保持不变)
    // ==========================================
    // 请保留 showModeSettingsDialog, showThemeSelectionDialog, showHelpDialog, showCalculatorDialog 等方法
    // (代码略，与上文一致)

    // [Restored] showModeSettingsDialog
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
                    tvWarning.setText("🚫 高质量出题仅在加载题库文件时可用");
                }
                return;
            }
            if (tvWarning != null) tvWarning.setVisibility(View.GONE);
            boolean isMod = currentLoadedFileName != null && (currentLoadedFileName.toLowerCase().contains("mod") || currentLoadedFileName.contains("模"));
            switchAvoidAddSub.setVisibility(View.VISIBLE);
            boolean layer1Active = switchAvoidAddSub.isChecked();
            int layer2Visibility = layer1Active ? View.VISIBLE : View.GONE;
            switchMustHaveDivision.setVisibility(layer2Visibility);
            if (isMod) switchAvoidTrivialMul.setVisibility(View.GONE); else switchAvoidTrivialMul.setVisibility(layer2Visibility);
            boolean mustDiv = switchMustHaveDivision.isChecked();
            boolean avoidTrivial = switchAvoidTrivialMul.isChecked();
            int layer3Visibility = (layer1Active && mustDiv && avoidTrivial) ? View.VISIBLE : View.GONE;
            if (isMod) switchRequireFrac.setVisibility(View.GONE); else switchRequireFrac.setVisibility(layer3Visibility);
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
                    if (callback != null) callback.onSettingsChanged();
                })
                .setNegativeButton("取消", (dialog, id) -> dialog.cancel());
        builder.create().show();
    }

    // [Restored] showThemeSelectionDialog
    private void showThemeSelectionDialog() {
        Context ctx = navigationView.getContext();
        final String[] themes = {"跟随系统", "日间模式", "夜间模式"};
        SharedPreferences prefs = ctx.getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        int currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        int checkedItem;
        if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) checkedItem = 1;
        else if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) checkedItem = 2;
        else checkedItem = 0;

        new AlertDialog.Builder(ctx)
                .setTitle("主题设置")
                .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
                    int selectedMode;
                    if (which == 1) selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
                    else if (which == 2) selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
                    else selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

                    prefs.edit().putInt("theme_mode", selectedMode).apply();
                    AppCompatDelegate.setDefaultNightMode(selectedMode);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null).show();
    }

    // [Restored] showHelpDialog
    private void showHelpDialog() {
        try {
            CharSequence helpContent = MarkdownUtils.loadMarkdownFromAssets(activity, "help.md");
            AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("游戏指南").setMessage(helpContent).setPositiveButton("开始挑战", null).create();
            dialog.show();
            TextView msgView = dialog.findViewById(android.R.id.message);
            if (msgView != null) msgView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        } catch (Exception e) {
            new AlertDialog.Builder(activity).setTitle("游戏指南").setMessage("暂无说明").setPositiveButton("确定", null).show();
        }
    }

    // [Restored] showCalculatorDialog
    private void showCalculatorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("24点计算器");

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = 40;
        layout.setPadding(padding, padding, padding, padding);

        final EditText etInput = new EditText(activity);
        etInput.setHint("请输入数字 (例如 3 3 8 8)\n支持复数 (3i, 1+2i)");
        etInput.setMinLines(2);
        layout.addView(etInput);

        // Mod Control
        LinearLayout modLayout = new LinearLayout(activity);
        modLayout.setOrientation(LinearLayout.HORIZONTAL);
        modLayout.setPadding(0, 20, 0, 20);
        modLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        Switch switchMod = new Switch(activity);
        switchMod.setText("开启 Mod 运算  ");
        modLayout.addView(switchMod);

        Spinner spinnerMod = new Spinner(activity);
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, MOD_PRIMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMod.setAdapter(adapter);
        spinnerMod.setVisibility(View.GONE);
        modLayout.addView(spinnerMod);

        layout.addView(modLayout);

        LinearLayout buttonLayout = new LinearLayout(activity);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);

        Button btnCalcAll = new Button(activity);
        btnCalcAll.setText("计算所有解");
        Button btnCalc10 = new Button(activity);
        btnCalc10.setText("计算前 10 个");

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnParams.setMargins(5, 0, 5, 0);
        buttonLayout.addView(btnCalcAll, btnParams);
        buttonLayout.addView(btnCalc10, btnParams);
        layout.addView(buttonLayout);

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 500);
        scrollParams.topMargin = 20;
        scrollView.setLayoutParams(scrollParams);
        final TextView tvResult = new TextView(activity);
        tvResult.setTextIsSelectable(true);
        tvResult.setPadding(10, 10, 10, 10);
        scrollView.addView(tvResult);
        layout.addView(scrollView);

        switchMod.setOnCheckedChangeListener((buttonView, isChecked) -> {
            spinnerMod.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            etInput.setHint(isChecked ? "请输入 0 到 Mod-1 之间的整数" : "请输入数字 (例如 3 3 8 8)\n支持复数 (3i, 1+2i)");
        });

        spinnerMod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { selectedModulus = MOD_PRIMES[position]; }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        View.OnClickListener calcListener = v -> {
            boolean isModEnabled = switchMod.isChecked();
            Integer modVal = isModEnabled ? selectedModulus : null;
            boolean limit10 = (v == btnCalc10);
            performCalculation(etInput.getText().toString(), limit10, tvResult, modVal);
        };

        btnCalcAll.setOnClickListener(calcListener);
        btnCalc10.setOnClickListener(calcListener);

        builder.setView(layout);
        builder.setNegativeButton("关闭", null);
        builder.create().show();
    }

    private void performCalculation(String input, boolean limit10, TextView tvResult, Integer modulus) {
        try {
            List<Fraction> nums = parseInputString(input);

            if (nums.isEmpty()) {
                tvResult.setText("请输入有效的数字");
                return;
            }
            if (nums.size() > 5) {
                tvResult.setText("❌ 错误: 最多只允许输入 5 个数");
                return;
            }
            if (modulus != null) {
                for (Fraction f : nums) {
                    if (f.toString().contains("/") || f.toString().contains("i")) {
                        tvResult.setText("❌ 错误: Mod 模式下只能输入整数。");
                        return;
                    }
                }
            }

            tvResult.setText("正在计算...");

            new Thread(() -> {
                List<String> rawSolutions = Solver.solveAll(nums, modulus);
                List<String> solutions = SolutionNormalizer.distinct(rawSolutions);
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
                        StringBuilder sb = new StringBuilder();
                        if (finalIsTruncated) sb.append("展示前 10 个解 (共 ").append(solutions.size()).append(" 个):\n\n");
                        else sb.append("共找到 ").append(solutions.size()).append(" 种解法:\n\n");

                        for(int i=0; i<displayList.size(); i++) {
                            sb.append("[").append(i+1).append("] ").append(displayList.get(i)).append("\n");
                        }
                        tvResult.setText(sb.toString());
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
            if (!p.isEmpty()) list.add(parseTokenToFraction(p));
        }
        return list;
    }

    private Fraction parseTokenToFraction(String token) {
        token = token.replace("(", "").replace(")", "").replace("I", "i");
        if (token.contains("i")) {
            if(token.equals("i")) return new Fraction(0,1,1);
            return new Fraction(0,1,1);
        } else if (token.contains("/")) {
            String[] fp = token.split("/");
            return new Fraction(Long.parseLong(fp[0]), Long.parseLong(fp[1]));
        } else {
            try { return new Fraction(Long.parseLong(token), 1); } catch (Exception e) { return new Fraction(0,1); }
        }
    }
}
