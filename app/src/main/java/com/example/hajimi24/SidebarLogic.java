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
    private Handler toastHandler = new Handler(Looper.getMainLooper());
    private Runnable toastRunnable;
    private void startBatchDownload() {
        Toast.makeText(activity, "正在同步题库列表...", Toast.LENGTH_SHORT).show();

        repository.fetchRemoteFileTree(new ProblemRepository.MenuDataCallback() {
            @Override
            public void onSuccess(List<ProblemRepository.RemoteFile> remoteFiles) {
                List<ProblemRepository.RemoteFile> filesToDownload = new ArrayList<>();

                for (ProblemRepository.RemoteFile rf : remoteFiles) {
                    // 关键点：使用 needsUpdate 替代 isFileDownloaded
                    if (repository.needsUpdate(rf.path, rf.sha)) {
                        filesToDownload.add(rf);
                    }
                }

                if (filesToDownload.isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(activity, "所有题库已是最新", Toast.LENGTH_SHORT).show());
                    return;
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    new AlertDialog.Builder(activity)
                            .setTitle("发现更新")
                            .setMessage("共有 " + filesToDownload.size() + " 个文件需要同步（包含新文件或已修改的文件），是否开始？")
                            .setPositiveButton("同步", (d, w) -> executeBatchDownload(filesToDownload))
                            .setNegativeButton("稍后", null)
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

    private void showLayoutAdjustmentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("📏 界面布局调整");

        final ScrollView scrollView = new ScrollView(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 40);
        scrollView.addView(layout);

        SharedPreferences prefs = activity.getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        float density = activity.getResources().getDisplayMetrics().density;

        // 💡 操作提示
        TextView tvHint = new TextView(activity);
        tvHint.setText("💡 提示：按住对话框外区域可预览布局");
        tvHint.setTextSize(13);
        tvHint.setTextColor(android.graphics.Color.GRAY);
        tvHint.setPadding(0, 0, 0, 30);
        layout.addView(tvHint);

        // --- 1. 卡片顶部间距 ---
        int top = prefs.getInt("grid_margin_top", 40);
        final TextView tv1 = new TextView(activity);
        tv1.setText("卡片顶部间距: " + top + " dp");
        layout.addView(tv1);
        android.widget.SeekBar sb1 = new android.widget.SeekBar(activity);
        sb1.setMax(250); sb1.setProgress(top);
        sb1.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean b) {
                tv1.setText("卡片顶部间距: " + p + " dp");
                applyGridMargin(p);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {
                prefs.edit().putInt("grid_margin_top", s.getProgress()).apply();
            }
        });
        layout.addView(sb1);

        // --- 2. 信息区底部偏移 ---
        int msgBottom = prefs.getInt("message_margin_bottom", 0);
        final TextView tv2 = new TextView(activity);
        tv2.setText("\n信息区底部偏移: " + msgBottom + " dp");
        layout.addView(tv2);
        android.widget.SeekBar sb2 = new android.widget.SeekBar(activity);
        sb2.setMax(400); sb2.setProgress(msgBottom + 200); // 映射 -200 到 200
        sb2.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean b) {
                int val = p - 200;
                tv2.setText("\n信息区底部偏移: " + val + " dp");
                View tvMsg = activity.findViewById(R.id.tv_message_area);
                if (tvMsg != null) {
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) tvMsg.getLayoutParams();
                    lp.bottomMargin = (int) (val * density);
                    tvMsg.setLayoutParams(lp);
                    tvMsg.setVisibility(View.VISIBLE);
                    ((TextView)tvMsg).setText("预览：底部信息区位置");
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {
                prefs.edit().putInt("message_margin_bottom", s.getProgress() - 200).apply();
            }
        });
        layout.addView(sb2);

        // --- 3. 粗体切换 ---
        View divider = new View(activity);
        LinearLayout.LayoutParams dpLp = new LinearLayout.LayoutParams(-1, 2); dpLp.setMargins(0, 40, 0, 40);
        divider.setLayoutParams(dpLp); divider.setBackgroundColor(android.graphics.Color.LTGRAY);
        layout.addView(divider);

        androidx.appcompat.widget.SwitchCompat swBold = new androidx.appcompat.widget.SwitchCompat(activity);
        swBold.setText("加粗数字和符号");
        swBold.setChecked(prefs.getBoolean("use_bold_text", false));
        swBold.setOnCheckedChangeListener((v, c) -> {
            prefs.edit().putBoolean("use_bold_text", c).apply();
            if (activity instanceof MainActivity) ((MainActivity) activity).applyTextWeight(c);
        });
        layout.addView(swBold);

        builder.setView(scrollView);
        builder.setPositiveButton("完成", (d, w) -> {
            View tvMsg = activity.findViewById(R.id.tv_message_area);
            if (tvMsg != null) ((TextView)tvMsg).setText("");
        });

        final AlertDialog dialog = builder.create();
        dialog.show();

        // [核心修复]：窥屏逻辑
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.getDecorView().setOnTouchListener((v, event) -> {
                float rawX = event.getRawX();
                float rawY = event.getRawY();

                // 判定是否点击在中间白色对话框区域内
                int[] loc = new int[2];
                scrollView.getLocationOnScreen(loc);
                boolean isInside = rawX >= loc[0] && rawX <= (loc[0] + scrollView.getWidth()) &&
                        rawY >= loc[1] && rawY <= (loc[1] + scrollView.getHeight());

                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    if (!isInside) {
                        // 按住背景：全透明且移除阴影
                        window.getDecorView().setAlpha(0f);
                        window.setDimAmount(0f);
                        return true; // 拦截事件以确保能收到 UP
                    }
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP ||
                        event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    // 无论在哪里松手，只要是透明状态就恢复
                    if (window.getDecorView().getAlpha() < 1f) {
                        window.getDecorView().setAlpha(1f);
                        window.setDimAmount(0.5f);
                        return true;
                    }
                }
                return false; // 允许正常滑动 SeekBar
            });
        }
    }


    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }


    // 辅助方法：动态修改 LayoutParams
    private void applyGridMargin(int dpValue) {
        View gridCards = activity.findViewById(R.id.grid_cards);
        if (gridCards != null && gridCards.getLayoutParams() instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) gridCards.getLayoutParams();

            // 将 dp 转换为 px
            float density = activity.getResources().getDisplayMetrics().density;
            lp.topMargin = (int) (dpValue * density);

            gridCards.setLayoutParams(lp);
        }
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

                repository.downloadFileSync(rf.path, rf.sha);
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
            if (id == 444) {
                // [新增]：在弹出调整对话框前，先缩回侧边栏
                drawerLayout.closeDrawer(GravityCompat.START);

                // 建议稍微延迟 200ms 弹出对话框，避开侧边栏动画，视觉更顺滑
                new Handler(Looper.getMainLooper()).postDelayed(this::showLayoutAdjustmentDialog, 200);
                return true;
            }
            else if (id == 888) { showHelpDialog(); }
            else if (id == 777) { showModeSettingsDialog(); }
            else if (id == 666) { showCalculatorDialog(); }
            else if (id == 555) { showThemeSelectionDialog(); }
            else if (title.contains("随机休闲") || title.contains("Random")) {
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
        menu.add(Menu.NONE, 444, Menu.NONE, "📏 调整布局");
        menu.add(Menu.NONE, 888, Menu.NONE, "📖 游戏说明书");
        menu.add(Menu.NONE, 777, Menu.NONE, "⚙️ 模式设定");
        menu.add(Menu.NONE, 666, Menu.NONE, "🧮 24点计算器");
        menu.add(Menu.NONE, 555, Menu.NONE, "🎨 主题设置");

        SubMenu randomGroup = menu.addSubMenu("🎲 随机休闲练习");
        randomGroup.add(Menu.NONE, 103, Menu.NONE, "随机休闲 (3数)");
        randomGroup.add(Menu.NONE, 104, Menu.NONE, "随机休闲 (4数)");
        randomGroup.add(Menu.NONE, 105, Menu.NONE, "随机休闲 (5数)");
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
        builder.setTitle(isExploringLocal ? "📂 本地题库" : "🌐 在线题库");

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView tvPath = new TextView(activity);
        tvPath.setPadding(45, 30, 45, 10);
        tvPath.setTextSize(13);
        layout.addView(tvPath);

        ListView listView = new ListView(activity);
        listView.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1.0f));
        layout.addView(listView);

        // [核心修复]：不再依赖系统的 simple_list_item_1，完全手动构建视图
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, 0, new ArrayList<>()) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                // 1. 视图复用逻辑：确保类型一致
                if (convertView == null || !(convertView instanceof LinearLayout)) {
                    LinearLayout itemLayout = new LinearLayout(activity);
                    itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                    itemLayout.setPadding(45, 40, 45, 40);

                    TextView tvName = new TextView(activity);
                    tvName.setTextSize(16);
                    // 根据系统主题适配颜色
                    int textColor = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                            == android.content.res.Configuration.UI_MODE_NIGHT_YES ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;
                    tvName.setTextColor(textColor);
                    tvName.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));

                    TextView tvCount = new TextView(activity);
                    tvCount.setTextSize(13);
                    tvCount.setGravity(android.view.Gravity.END);

                    itemLayout.addView(tvName);
                    itemLayout.addView(tvCount);
                    convertView = itemLayout;
                }

                // 2. 提取组件
                LinearLayout container = (LinearLayout) convertView;
                TextView tvName = (TextView) container.getChildAt(0);
                TextView tvCount = (TextView) container.getChildAt(1);

                String itemText = getItem(position);
                tvName.setText(itemText);
                tvCount.setVisibility(View.VISIBLE);

                // 获取当前模式的数据源（用于文件夹计数）
                List<ProblemRepository.RemoteFile> dataSource = isExploringLocal ? cachedLocalFiles : cachedRemoteFiles;

                if (itemText == null || itemText.equals(".. (返回上一级)")) {
                    tvCount.setVisibility(View.GONE);
                }
                else if (itemText.startsWith("📁 ")) {
                    // --- 文件夹逻辑：统计该目录下包含的文件总数 ---
                    String folderName = itemText.replace("📁 ", "");
                    String folderPath = currentExplorerPath + folderName + "/";
                    int totalItems = 0;
                    if (dataSource != null) {
                        for (ProblemRepository.RemoteFile f : dataSource) {
                            if (f.path.startsWith(folderPath)) {
                                totalItems++;
                            }
                        }
                    }
                    tvCount.setText(totalItems + " 份文档");
                    tvCount.setAlpha(0.35f); // 文件夹计数显示较淡
                }
                else if (itemText.startsWith("📄 ")) {
                    // --- 文件逻辑：显示题目数量 ---
                    String fileName = itemText.replace("📄 ", "");
                    String fullPath = currentExplorerPath + fileName;

                    // 核心优化：即便在云端模式，如果本地已下载，也显示题目数量
                    if (repository.isFileDownloaded(fullPath)) {
                        int count = repository.getLocalFileLineCount(fullPath);
                        tvCount.setText(count + " 题");
                        tvCount.setAlpha(0.65f); // 题目数量显示较清晰
                    } else {
                        // 尚未下载的云端文件
                        tvCount.setText("云端");
                        tvCount.setAlpha(0.4f);
                    }
                }
                else {
                    tvCount.setVisibility(View.GONE);
                }

                return convertView;
            }

        };

        listView.setAdapter(adapter);

        builder.setView(layout);
        builder.setNegativeButton("关闭", null);

        if (isExploringLocal) {
            builder.setNeutralButton("清空本地", (d, w) -> {
                new AlertDialog.Builder(activity)
                        .setTitle("确认清空？")
                        .setMessage("这将删除所有已下载的题库文件。")
                        .setPositiveButton("确定", (d2, w2) -> {
                            deleteRecursive(new java.io.File(activity.getFilesDir(), "data"));
                            fetchLocalFilesAndShowDialog();
                        })
                        .setNegativeButton("取消", null).show();
            });
        }

        AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String itemText = adapter.getItem(position);
            if (itemText == null) return;

            if (itemText.equals(".. (返回上一级)")) {
                String temp = currentExplorerPath.substring(0, currentExplorerPath.length() - 1);
                int lastSlash = temp.lastIndexOf('/');
                if (lastSlash != -1) {
                    currentExplorerPath = temp.substring(0, lastSlash + 1);
                    updateExplorerView(tvPath, adapter);
                }
                return;
            }

            if (itemText.startsWith("📁 ")) {
                currentExplorerPath += itemText.replace("📁 ", "") + "/";
                updateExplorerView(tvPath, adapter);
                return;
            }

            if (itemText.startsWith("📄 ")) {
                String fileName = itemText.replace("📄 ", "");
                String fullPath = currentExplorerPath + fileName;
                dialog.dismiss();
                if (isExploringLocal) loadLocalProblemSet(fullPath);
                else startDownloadWithProgress(fullPath, fileName);
            }
        });

        dialog.show();
        updateExplorerView(tvPath, adapter);
    }


    // 辅助递归删除（放在 SidebarLogic 类末尾即可）
    private void deleteRecursive(java.io.File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (java.io.File child : fileOrDirectory.listFiles()) deleteRecursive(child);
        }
        fileOrDirectory.delete();
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
            public void onProgress(int percent, long currentBytes, long totalBytes) {
                if (isSingleDownloadCancelled) return;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (percent == -1) {
                        progressBar.setIndeterminate(true);
                        // 长度未知时，仅显示已下载大小
                        tvPercent.setText("正在下载: " + formatFileSize(currentBytes));
                    } else {
                        progressBar.setIndeterminate(false);
                        progressBar.setProgress(percent);
                        // [核心修改]：显示 "12 KB / 100 KB (12%)"
                        String sizeInfo = formatFileSize(currentBytes) + " / " + formatFileSize(totalBytes);
                        tvPercent.setText(sizeInfo + " (" + percent + "%)");
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
        SwitchCompat switchRequireStorm = dialogView.findViewById(R.id.switch_require_division_storm);
        TextView tvWarning = dialogView.findViewById(R.id.tv_warning_random);

        // 初始化选中状态
        switchAvoidAddSub.setChecked(gameModeSettings.avoidPureAddSub);
        switchMustHaveDivision.setChecked(gameModeSettings.mustHaveDivision);
        switchAvoidTrivialMul.setChecked(gameModeSettings.avoidTrivialFinalMultiply);
        switchRequireStorm.setChecked(gameModeSettings.requireDivisionStorm);

        Runnable updateVisibility = () -> {
            // 1. 随机休闲模式处理
            if (isCurrentModeRandom) {
                switchAvoidAddSub.setVisibility(View.GONE);
                switchMustHaveDivision.setVisibility(View.GONE);
                switchAvoidTrivialMul.setVisibility(View.GONE);
                switchRequireStorm.setVisibility(View.GONE);
                if (tvWarning != null) {
                    tvWarning.setVisibility(View.VISIBLE);
                    tvWarning.setText("🚫 高质量出题仅在加载题库文件时可用");
                }
                return;
            }

            if (tvWarning != null) tvWarning.setVisibility(View.GONE);

            // 2. 特殊模式判定
            String fName = currentLoadedFileName != null ? currentLoadedFileName.toLowerCase() : "";
            boolean isSpecialMode = fName.contains("mod") || fName.contains("模") ||
                    fName.contains("base") || fName.contains("进制");

            // 3. 层级显示逻辑
            // 第一层：避免纯加减 (layer1)
            switchAvoidAddSub.setVisibility(View.VISIBLE);
            boolean layer1Active = switchAvoidAddSub.isChecked();
            int layerVisibilityVal = layer1Active ? View.VISIBLE : View.GONE;

            // 第二层：必须有除法
            switchMustHaveDivision.setVisibility(layerVisibilityVal);
            boolean hasDivision = switchMustHaveDivision.isChecked();

            // 4. 高级选项控制

            // [平凡乘法]：逻辑较复杂，依然仅在非特殊模式下显示
            if (isSpecialMode) {
                switchAvoidTrivialMul.setVisibility(View.GONE);
            } else {
                switchAvoidTrivialMul.setVisibility(layerVisibilityVal);
            }

            // [除法风暴]：逻辑简单且通用，提升为全局规则（不再受 isSpecialMode 限制）
            // 规则：只有在 layer1 开启 且 勾选了除法时才显示。
            switchRequireStorm.setVisibility((layer1Active && hasDivision) ? View.VISIBLE : View.GONE);

            // 状态联动：如果关闭了除法，强制取消风暴开关的选中状态
            if (!hasDivision) {
                switchRequireStorm.setChecked(false);
            }
        };

        // 设置监听器处理 UI 刷新和状态联动
        switchAvoidAddSub.setOnCheckedChangeListener((b, c) -> updateVisibility.run());

        switchMustHaveDivision.setOnCheckedChangeListener((b, c) -> {
            // 核心联动：如果关闭了除法，自动关闭风暴开关
            if (!c) {
                switchRequireStorm.setChecked(false);
            }
            updateVisibility.run();
        });

        switchAvoidTrivialMul.setOnCheckedChangeListener((b, c) -> updateVisibility.run());

        updateVisibility.run();

        builder.setTitle("模式设定")
                .setPositiveButton("确定", (dialog, id) -> {
                    gameModeSettings.avoidPureAddSub = switchAvoidAddSub.isChecked();
                    gameModeSettings.mustHaveDivision = switchMustHaveDivision.isChecked();
                    gameModeSettings.avoidTrivialFinalMultiply = switchAvoidTrivialMul.isChecked();
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

    private boolean isHelpFullScreen = false;

    private void showHelpDialog() {
        if (activity == null) return;

        try {
            final String htmlContent = MarkdownUtils.loadMarkdownFromAssets(activity, "help.md");

            // 1. 创建 Dialog 并彻底去掉标题和默认背景
            final android.app.Dialog dialog = new android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

            // 2. 根布局：全屏透明，点击阴影可以关闭（可选）
            android.widget.RelativeLayout root = new android.widget.RelativeLayout(activity);
            root.setBackgroundColor(android.graphics.Color.parseColor("#80000000")); // 半透明遮罩背景

            // 3. 内容容器：这才是那个白色的“纸张”
            final android.widget.LinearLayout contentBox = new android.widget.LinearLayout(activity);
            contentBox.setOrientation(android.widget.LinearLayout.VERTICAL);
            contentBox.setBackgroundColor(android.graphics.Color.WHITE);

            // 4. 顶部控制栏
            android.widget.RelativeLayout controlBar = new android.widget.RelativeLayout(activity);
            controlBar.setPadding(30, 20, 30, 20);
            controlBar.setBackgroundColor(android.graphics.Color.parseColor("#f6f8fa"));

            final android.widget.Button btnFull = new android.widget.Button(activity);
            btnFull.setText("全屏显示");
            btnFull.setAllCaps(false);
            btnFull.setBackground(null);
            btnFull.setTextColor(android.graphics.Color.parseColor("#0366d6"));
            controlBar.addView(btnFull);

            android.widget.Button btnClose = new android.widget.Button(activity);
            btnClose.setText("关闭");
            btnClose.setAllCaps(false);
            btnClose.setBackground(null);
            btnClose.setTextColor(android.graphics.Color.GRAY);
            android.widget.RelativeLayout.LayoutParams lpClose = new android.widget.RelativeLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            lpClose.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
            controlBar.addView(btnClose, lpClose);

            contentBox.addView(controlBar);

            // 5. WebView
            final android.webkit.WebView webView = new android.webkit.WebView(activity);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null);
            contentBox.addView(webView, new android.widget.LinearLayout.LayoutParams(-1, -1));

            // 将白色容器放入透明根布局
            root.addView(contentBox);
            dialog.setContentView(root);

            // 6. 核心逻辑：切换全屏
            Runnable updateLayout = () -> {
                android.widget.RelativeLayout.LayoutParams params;
                if (isHelpFullScreen) {
                    // 真正全屏：无边距，占满屏幕
                    params = new android.widget.RelativeLayout.LayoutParams(-1, -1);
                    btnFull.setText("退出全屏");
                } else {
                    // 窗口模式：设置宽度并居中，高度占 75%
                    int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9);
                    int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.75);
                    params = new android.widget.RelativeLayout.LayoutParams(width, height);
                    params.addRule(android.widget.RelativeLayout.CENTER_IN_PARENT);
                    btnFull.setText("全屏显示");
                }
                contentBox.setLayoutParams(params);
            };

            btnFull.setOnClickListener(v -> {
                isHelpFullScreen = !isHelpFullScreen;
                updateLayout.run();
            });

            btnClose.setOnClickListener(v -> dialog.dismiss());

            // 初始状态
            isHelpFullScreen = false;
            updateLayout.run();

            dialog.show();

            // 确保 Window 级别也是全屏的，防止黑边
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            }

        } catch (Exception e) {
            e.printStackTrace();
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
        etInput.setHint("请输入数字 (例如 3 3 8 8)");
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
                    etInput.setHint("Mod n 模式下, 请输入 0 到 n-1 之间的整数");
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
