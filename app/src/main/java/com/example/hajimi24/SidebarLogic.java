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
    private volatile boolean isBatchCancelled = false;      // 批量下载取消标记
    private volatile boolean isSingleDownloadCancelled = false; // 单个下载取消标记

    private void fetchLocalFilesAndShowDialog() {
        // 强制清除旧缓存，确保刚下载的文件能刷出来
        cachedLocalFiles = null;

        repository.fetchLocalFileTree(new ProblemRepository.MenuDataCallback() {
            @Override
            public void onSuccess(List<ProblemRepository.RemoteFile> files) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    cachedLocalFiles = files;
                    currentExplorerPath = "data/"; // 统一重置到根目录
                    showFileExplorerDialog();
                });
            }
            @Override
            public void onFail(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(activity, "本地列表为空或读取失败", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private void startBatchDownload() {
        Toast.makeText(activity, "正在同步题库列表...", Toast.LENGTH_SHORT).show();

        repository.fetchRemoteFileTree(new ProblemRepository.MenuDataCallback() {
            @Override
            public void onSuccess(List<ProblemRepository.RemoteFile> remoteFiles) {
                cachedRemoteFiles = remoteFiles;

                // 过滤出本地不存在的文件
                List<ProblemRepository.RemoteFile> filesToDownload = new ArrayList<>();
                for (ProblemRepository.RemoteFile rf : remoteFiles) {
                    if (!repository.isFileDownloaded(rf.path)) {
                        filesToDownload.add(rf);
                    }
                }

                if (filesToDownload.isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(activity, "题库已是最新，无需下载", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 弹出确认对话框
                new Handler(Looper.getMainLooper()).post(() -> {
                    new AlertDialog.Builder(activity)
                            .setTitle("同步题库")
                            .setMessage("发现 " + filesToDownload.size() + " 个新文件，是否开始下载？")
                            .setPositiveButton("开始", (d, w) -> executeBatchDownload(filesToDownload))
                            .setNegativeButton("取消", null)
                            .show();
                });
            }

            @Override
            public void onFail(String error) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(activity, "同步失败: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void executeBatchDownload(List<ProblemRepository.RemoteFile> files) {
        isBatchCancelled = false;
        // 复用你之前的自定义 AlertDialog 逻辑
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("同步题库中...");
        // --- 允许取消和返回键 ---
        builder.setCancelable(true);
        builder.setNegativeButton("停止", (dialog, which) -> {
            isBatchCancelled = true;
            dialog.dismiss();
        });
        builder.setOnCancelListener(dialog -> { // 处理返回键
            isBatchCancelled = true;
            Toast.makeText(activity, "同步已中断", Toast.LENGTH_SHORT).show();
        });

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        // 进度条
        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(files.size());
        progressBar.setProgress(0);
        layout.addView(progressBar);

        // 文字提示
        TextView tvStatus = new TextView(activity);
        tvStatus.setText("准备开始...");
        tvStatus.setPadding(0, 20, 0, 0);
        tvStatus.setGravity(android.view.Gravity.CENTER);
        layout.addView(tvStatus);

        builder.setView(layout);
        AlertDialog progressDialog = builder.create();
        progressDialog.show();

        new Thread(() -> {
            int count = 0;
            for (ProblemRepository.RemoteFile rf : files) {
                // 关键点：每次循环前检查用户是否点击了取消或返回
                if (isBatchCancelled) {
                    break;
                }

                count++;
                final int currentCount = count;
                final String fileName = rf.name;

                new Handler(Looper.getMainLooper()).post(() -> {
                    tvStatus.setText("正在同步 (" + currentCount + "/" + files.size() + ")\n" + fileName);
                    progressBar.setProgress(currentCount);
                });

                repository.downloadFileSync(rf.path);
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                progressDialog.dismiss();
                if (isBatchCancelled) {
                    Toast.makeText(activity, "已停止同步", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "✅ 题库同步完成！", Toast.LENGTH_SHORT).show();
                }
                if (isExploringLocal) fetchLocalFilesAndShowDialog();
            });
        }).start();
    }



    private final Activity activity;
    private final DrawerLayout drawerLayout;
    private final NavigationView navigationView;
    private final ProblemRepository repository;
    private final ActionCallback callback;
    private final GameModeSettings gameModeSettings;

    private List<ProblemRepository.RemoteFile> cachedRemoteFiles = null;
    private List<ProblemRepository.RemoteFile> cachedLocalFiles = null; // 新增：本地文件缓存
    private boolean isExploringLocal = false; // 新增：标记当前资源管理器模式
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
                isExploringLocal = false; // 必须重置为 false
                if (cachedRemoteFiles == null) {
                    fetchRemoteFilesAndShowDialog();
                } else {
                    currentExplorerPath = "data/"; // 确保路径重置
                    showFileExplorerDialog();
                }
                return true;
            }

            if (id == 3000) { // 本地题库按钮
                isExploringLocal = true; // 设置为 true
                fetchLocalFilesAndShowDialog();
                return true;
            }

            // 在 setup() 的 setNavigationItemSelectedListener 中
            if (id == 999) {
                startBatchDownload();
                return true;
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

        menu.add(Menu.NONE, 2000, Menu.NONE, "🌐 在线题库 (浏览与下载)");
        menu.add(Menu.NONE, 3000, Menu.NONE, "📂 本地题库 (已下载)"); // 新增
        menu.add(Menu.NONE, 999, Menu.NONE, "📥 一键同步 (下载所有题目)");

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
        builder.setTitle(isExploringLocal ? "本地题库" : "在线题库");

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView tvPath = new TextView(activity);
        tvPath.setPadding(40, 30, 40, 10);
        layout.addView(tvPath);

        ListView listView = new ListView(activity);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listView.setLayoutParams(listParams);
        layout.addView(listView);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);

        builder.setView(layout);
        builder.setNegativeButton("关闭", null);
        AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String itemText = adapter.getItem(position);
            if (itemText == null) return;

            // 返回上一级
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

            // 进入文件夹
            if (itemText.startsWith("📁 ")) {
                String folderName = itemText.replace("📁 ", "");
                currentExplorerPath += folderName + "/";
                updateExplorerView(tvPath, adapter);
                return;
            }

            // 点击文件：区分本地和在线
            if (itemText.startsWith("📄 ")) {
                String fileName = itemText.replace("📄 ", "");
                String fullPath = currentExplorerPath + fileName;
                dialog.dismiss();

                if (isExploringLocal) {
                    // 如果是本地模式，直接调用加载方法
                    loadLocalProblemSet(fullPath);
                } else {
                    // 如果是在线模式，才调用下载方法
                    startDownloadWithProgress(fullPath, fileName);
                }
            }
        });

        dialog.show();
        updateExplorerView(tvPath, adapter);
    }

    private void updateExplorerView(TextView tvPath, ArrayAdapter<String> adapter) {
        tvPath.setText("当前位置: " + (isExploringLocal ? "本地/" : "远程/") + currentExplorerPath);

        List<String> items = new ArrayList<>();
        Set<String> folders = new HashSet<>();
        List<String> files = new ArrayList<>();

        // 根据模式选择数据源
        List<ProblemRepository.RemoteFile> dataSource = isExploringLocal ? cachedLocalFiles : cachedRemoteFiles;

        if (dataSource != null) {
            for (ProblemRepository.RemoteFile f : dataSource) {
                if (f.path.startsWith(currentExplorerPath)) {
                    String relativePath = f.path.substring(currentExplorerPath.length());
                    int slashIndex = relativePath.indexOf('/');
                    if (slashIndex == -1) files.add(relativePath);
                    else folders.add(relativePath.substring(0, slashIndex));
                }
            }
        }

        if (!currentExplorerPath.equals("data/")) items.add(".. (返回上一级)");

        List<String> sortedFolders = new ArrayList<>(folders);
        Collections.sort(sortedFolders);
        for (String f : sortedFolders) items.add("📁 " + f);

        Collections.sort(files);
        for (String f : files) items.add("📄 " + f);

        adapter.clear();
        adapter.addAll(items);
        adapter.notifyDataSetChanged();
    }
    // ==========================================
    //  新增：直接从本地加载题库
    // ==========================================
    private void loadLocalProblemSet(String filePath) {
        try {
            List<Problem> problems = repository.loadProblemSet(filePath, gameModeSettings);
            isCurrentModeRandom = false;
            currentLoadedFileName = filePath;
            callback.onLoadProblems(problems, filePath);
            drawerLayout.closeDrawer(GravityCompat.START);
            Toast.makeText(activity, "本地加载成功", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(activity, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    //  下载与进度条逻辑
    // ==========================================

    private void startDownloadWithProgress(String path, String fileName) {
        isSingleDownloadCancelled = false;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("正在下载 " + fileName);

        builder.setCancelable(true);
        builder.setNegativeButton("取消", (dialog, which) -> {
            isSingleDownloadCancelled = true;
            dialog.dismiss();
        });
        builder.setOnCancelListener(dialog -> { // 处理 Android 三角键/返回键
            isSingleDownloadCancelled = true;
        });

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
                if (isSingleDownloadCancelled) return;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (percent == -1) {
                        // 如果长度未知，让进度条进入动画模式（走马灯）
                        progressBar.setIndeterminate(true);
                        tvPercent.setText("正在下载...");
                    } else {
                        progressBar.setIndeterminate(false);
                        progressBar.setProgress(percent);
                        tvPercent.setText(percent + "%");
                    }
                });
            }

            @Override
            public void onSuccess(List<Problem> problems, String fileName) {
                if (isSingleDownloadCancelled) return;
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
                if (isSingleDownloadCancelled) return;
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity, "下载失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private int selectedRadix = 10;

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

        // --- 模式选择区域 ---
        LinearLayout modeLayout = new LinearLayout(activity);
        modeLayout.setOrientation(LinearLayout.HORIZONTAL);
        modeLayout.setPadding(0, 20, 0, 20);
        modeLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // 1. 模式选择 Spinner
        Spinner spinnerMode = new Spinner(activity);
        String[] modes = {"常规模式", "同余模式", "进制模式"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMode.setAdapter(modeAdapter);
        modeLayout.addView(spinnerMode);

        // 2. 细节选择 Spinner (用于选择 Mod 数或 Radix 进制)
        Spinner spinnerDetail = new Spinner(activity);
        spinnerDetail.setVisibility(View.GONE);
        modeLayout.addView(spinnerDetail);

        layout.addView(modeLayout);

        // 数据源定义
        Integer[] radixValues = {5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        ArrayAdapter<Integer> modAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, MOD_PRIMES);
        ArrayAdapter<Integer> radixAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, radixValues);
        modAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        radixAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // 切换模式时的交互逻辑
        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) { // 常规
                    spinnerDetail.setVisibility(View.GONE);
                    etInput.setHint("请输入数字 (例如 3 3 8 8)");
                } else if (position == 1) { // 同余
                    spinnerDetail.setVisibility(View.VISIBLE);
                    spinnerDetail.setAdapter(modAdapter);
                    etInput.setHint("请输入 0 到 Mod-1 之间的整数");
                } else { // 进制
                    spinnerDetail.setVisibility(View.VISIBLE);
                    spinnerDetail.setAdapter(radixAdapter);
                    spinnerDetail.setSelection(5); // 默认 10 进制 (索引5)
                    etInput.setHint("请输入对应进制的数字 (支持 A-F)");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // ------------------

        LinearLayout buttonLayout = new LinearLayout(activity);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button btnCalcAll = new Button(activity); btnCalcAll.setText("计算所有解");
        Button btnCalc10 = new Button(activity); btnCalc10.setText("计算前 10 个");
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

        View.OnClickListener calcListener = v -> {
            int modeIdx = spinnerMode.getSelectedItemPosition();
            Integer modulus = null;
            int radix = 10;
            int target = 24;

            if (modeIdx == 1) { // 同余
                modulus = (Integer) spinnerDetail.getSelectedItem();
            } else if (modeIdx == 2) { // 进制
                radix = (Integer) spinnerDetail.getSelectedItem();
                target = 2 * radix + 4; // 计算进制下的目标值 (如 12进制下是 28)
            }

            boolean limit10 = (v == btnCalc10);
            performCalculation(etInput.getText().toString(), limit10, tvResult, modulus, radix, target);
        };

        btnCalcAll.setOnClickListener(calcListener);
        btnCalc10.setOnClickListener(calcListener);

        builder.setView(layout);
        builder.setNegativeButton("关闭", null);
        builder.create().show();
    }

    private void performCalculation(String input, boolean limit10, TextView tvResult, Integer modulus, int radix, int target) {
        try {
            // 使用当前选定的进制解析输入
            List<Fraction> nums = parseInputString(input, radix);

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
                // 核心：传递 nums, modulus 和动态计算的 target
                List<String> rawSolutions = Solver.solveAll(nums, modulus, target);
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
                        sb.append(finalIsTruncated ? "展示前 10 个解 (共 " : "共找到 ").append(solutions.size()).append(finalIsTruncated ? " 个):\n\n" : " 种解法:\n\n");
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

    private List<Fraction> parseInputString(String input, int radix) throws Exception {
        List<Fraction> list = new ArrayList<>();
        // 分隔出所有可能的数字/分数 Token
        String[] parts = input.split("[^0-9A-Fa-f+\\-*/iIjJ.]+");

        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) continue;

            // --- 进制合法性检查逻辑 ---
            // 移除正负号、分号、虚数单位等干扰字符，只保留数字和 A-F 部分
            String numericPart = p.replaceAll("[iIjJ+\\-*/().]", "");

            for (char c : numericPart.toCharArray()) {
                // Character.digit 会返回字符在对应进制下的数值，如果字符非法则返回 -1
                int digitValue = Character.digit(c, radix);

                if (digitValue == -1 || digitValue >= radix) {
                    // 如果字符不合法，直接抛出异常，会被 performCalculation 的 try-catch 捕获并显示
                    throw new Exception("数字 '" + p + "' 含有非法字符 '" + c + "' (不属于 " + radix + " 进制)");
                }
            }
            // -----------------------

            // 校验通过，进行解析
            list.add(Fraction.parse(p, radix));
        }
        return list;
    }




    private Fraction parseTokenToFraction(String token) {
        token = token.replace("(", "").replace(")", "").replace("I", "i");
        if (token.contains("i")) {
            return Fraction.parse(token, 10);
        } else if (token.contains("/")) {
            String[] fp = token.split("/");
            // 确保使用 (实部, 虚部, 分母, 进制) 构造函数
            return new Fraction(Long.parseLong(fp[0]), 0, Long.parseLong(fp[1]), 10);
        } else {
            try {
                return new Fraction(Long.parseLong(token), 0, 1, 10);
            } catch (Exception e) {
                return new Fraction(0, 0, 1, 10);
            }
        }
    }

}
