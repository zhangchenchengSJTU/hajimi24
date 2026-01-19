package com.example.hajimi24;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.webkit.WebView;
import android.webkit.WebSettings;
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
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;


public class SidebarLogic {
    private volatile boolean isBatchCancelled = false;      // 批量下载取消标记
    private volatile boolean isSingleDownloadCancelled = false; // 单个下载取消标记

    private void fetchLocalFilesAndShowDialog() {
        // 强制清除旧缓存，确保刚下载的文件能刷出来
        cachedLocalFiles = null;

        // 显式指定扫描 data 目录
        repository.fetchLocalFileTree("data/", new ProblemRepository.MenuDataCallback() {
            @Override
            public void onSuccess(List<ProblemRepository.RemoteFile> files) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    cachedLocalFiles = files;
                    currentExplorerPath = "data/";
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
        // 补全参数：只同步 data 目录下的 .txt 文件
        repository.fetchRemoteFileTree("data/", ".txt", new ProblemRepository.MenuDataCallback() {
            @Override
            public void onSuccess(List<ProblemRepository.RemoteFile> remoteFiles) {
                List<ProblemRepository.RemoteFile> filesToDownload = new ArrayList<>();
                for (ProblemRepository.RemoteFile rf : remoteFiles) {
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
                            .setMessage("共有 " + filesToDownload.size() + " 个文件需要同步，是否开始？")
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

    private void applyLayoutPadding(int topDp, int bottomDp) {
        // 通过寻找按钮的父布局来获取主界面的 ConstraintLayout
        View mainContent = activity.findViewById(R.id.btn_menu).getParent() instanceof View ?
                (View)activity.findViewById(R.id.btn_menu).getParent() : null;
        if (mainContent != null) {
            float density = activity.getResources().getDisplayMetrics().density;
            // 保持原本的左右内边距 (16dp)
            int sidePadding = (int)(16 * density);
            mainContent.setPadding(sidePadding, (int)(topDp * density), sidePadding, (int)(bottomDp * density));
        }
    }


    public void showLayoutAdjustmentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("📏 界面布局调整");

        final ScrollView scrollView = new ScrollView(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(75, 50, 75, 50);
        scrollView.addView(layout);

        SharedPreferences prefs = activity.getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        float density = activity.getResources().getDisplayMetrics().density;

        // 💡 操作提示
        TextView tvHint = new TextView(activity);
        tvHint.setText("\n按住对话框外区域可预览布局\n");
        tvHint.setTextSize(13);
        tvHint.setTextColor(android.graphics.Color.GRAY);
        tvHint.setGravity(android.view.Gravity.CENTER);
        tvHint.setPadding(0, 10, 0, 50);
        layout.addView(tvHint);

        // --- 1. 卡片区域顶部间距 ---
        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        TextView tv1Label = new TextView(activity);
        tv1Label.setText("🃏 卡片区域顶部间距");
        tv1Label.setTextSize(15);
        final TextView tv1Val = new TextView(activity);
        tv1Val.setTextSize(15);
        row1.addView(tv1Label, new LinearLayout.LayoutParams(0, -2, 1.0f));
        row1.addView(tv1Val, new LinearLayout.LayoutParams(-2, -2));
        layout.addView(row1);

        final android.widget.SeekBar sb1 = new android.widget.SeekBar(activity);
        int top = prefs.getInt("grid_margin_top", 40);
        tv1Val.setText(top + " dp");
        sb1.setPadding(0, 35, 0, 10);
        sb1.setMax(100); sb1.setProgress(top);
        sb1.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean b) {
                tv1Val.setText(p + " dp");
                applyGridMargin(p);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {
                prefs.edit().putInt("grid_margin_top", s.getProgress()).apply();
            }
        });
        layout.addView(sb1);
        layout.addView(new View(activity), new LinearLayout.LayoutParams(-1, (int)(25 * density)));

        // --- 2. 信息区底部偏移 ---
        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        TextView tv2Label = new TextView(activity);
        tv2Label.setText("💬 信息区底部偏移量");
        tv2Label.setTextSize(15);
        final TextView tv2Val = new TextView(activity);
        tv2Val.setTextSize(15);
        row2.addView(tv2Label, new LinearLayout.LayoutParams(0, -2, 1.0f));
        row2.addView(tv2Val, new LinearLayout.LayoutParams(-2, -2));
        layout.addView(row2);

        final android.widget.SeekBar sb2 = new android.widget.SeekBar(activity);
        int msgBottom = prefs.getInt("message_margin_bottom", 0);
        tv2Val.setText(msgBottom + " dp");
        sb2.setPadding(0, 35, 0, 10);
        sb2.setMax(400); sb2.setProgress(msgBottom + 200);
        sb2.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean b) {
                int val = p - 200;
                tv2Val.setText(val + " dp");
                View tvMsg = activity.findViewById(R.id.tv_message_area);
                if (tvMsg != null) {
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lpT = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) tvMsg.getLayoutParams();
                    lpT.bottomMargin = (int) (val * density);
                    tvMsg.setLayoutParams(lpT);
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
        layout.addView(new View(activity), new LinearLayout.LayoutParams(-1, (int)(25 * density)));

        // --- 3. 整体顶部留白 ---
        LinearLayout rowTopPadding = new LinearLayout(activity);
        rowTopPadding.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvTopPaddingLabel = new TextView(activity);
        tvTopPaddingLabel.setText("⏫ 顶部留白");
        tvTopPaddingLabel.setTextSize(15);
        final TextView tvTopPaddingVal = new TextView(activity);
        tvTopPaddingVal.setTextSize(15);
        rowTopPadding.addView(tvTopPaddingLabel, new LinearLayout.LayoutParams(0, -2, 1.0f));
        rowTopPadding.addView(tvTopPaddingVal, new LinearLayout.LayoutParams(-2, -2));
        layout.addView(rowTopPadding);

        final android.widget.SeekBar sbTopPadding = new android.widget.SeekBar(activity);
        int layoutTop = prefs.getInt("layout_padding_top", 50);
        tvTopPaddingVal.setText(layoutTop + " dp");
        sbTopPadding.setPadding(0, 35, 0, 10);
        sbTopPadding.setMax(100); sbTopPadding.setProgress(layoutTop);
        sbTopPadding.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean b) {
                tvTopPaddingVal.setText(p + " dp");
                applyLayoutPadding(p, prefs.getInt("layout_padding_bottom", 30));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {
                prefs.edit().putInt("layout_padding_top", s.getProgress()).apply();
            }
        });
        layout.addView(sbTopPadding);
        layout.addView(new View(activity), new LinearLayout.LayoutParams(-1, (int)(25 * density)));

        // --- 4. 整体底部留白 ---
        LinearLayout rowBottomPadding = new LinearLayout(activity);
        rowBottomPadding.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvBottomPaddingLabel = new TextView(activity);
        tvBottomPaddingLabel.setText("⏬ 底部留白");
        tvBottomPaddingLabel.setTextSize(15);
        final TextView tvBottomPaddingVal = new TextView(activity);
        tvBottomPaddingVal.setTextSize(15);
        rowBottomPadding.addView(tvBottomPaddingLabel, new LinearLayout.LayoutParams(0, -2, 1.0f));
        rowBottomPadding.addView(tvBottomPaddingVal, new LinearLayout.LayoutParams(-2, -2));
        layout.addView(rowBottomPadding);

        final android.widget.SeekBar sbBottomPadding = new android.widget.SeekBar(activity);
        int layoutBottom = prefs.getInt("layout_padding_bottom", 30);
        tvBottomPaddingVal.setText(layoutBottom + " dp");
        sbBottomPadding.setPadding(0, 35, 0, 10);
        sbBottomPadding.setMax(100); sbBottomPadding.setProgress(layoutBottom);
        sbBottomPadding.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean b) {
                tvBottomPaddingVal.setText(p + " dp");
                applyLayoutPadding(prefs.getInt("layout_padding_top", 50), p);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {
                prefs.edit().putInt("layout_padding_bottom", s.getProgress()).apply();
            }
        });
        layout.addView(sbBottomPadding);
        layout.addView(new View(activity), new LinearLayout.LayoutParams(-1, (int)(30 * density)));

        // --- 5. 加粗开关 ---
        LinearLayout row4 = new LinearLayout(activity);
        row4.setOrientation(LinearLayout.HORIZONTAL);
        row4.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView tv4Label = new TextView(activity);
        tv4Label.setText("✍️ 加粗数字与提示文本");
        tv4Label.setTextSize(15);
        row4.addView(tv4Label, new LinearLayout.LayoutParams(0, -2, 1.0f));
        androidx.appcompat.widget.SwitchCompat swBold = new androidx.appcompat.widget.SwitchCompat(activity);
        swBold.setChecked(prefs.getBoolean("use_bold_text", false));
        swBold.setOnCheckedChangeListener((v, c) -> {
            prefs.edit().putBoolean("use_bold_text", c).apply();
            if (activity instanceof MainActivity) ((MainActivity) activity).applyTextWeight(c);
        });
        row4.addView(swBold, new LinearLayout.LayoutParams(-2, -2));
        layout.addView(row4);
        layout.addView(new View(activity), new LinearLayout.LayoutParams(-1, (int)(30 * density)));

        // --- 6. 主题模式选择 ---
        LinearLayout rowTheme = new LinearLayout(activity);
        rowTheme.setOrientation(LinearLayout.HORIZONTAL);
        rowTheme.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView tvThemeLabel = new TextView(activity);
        tvThemeLabel.setText("🌓 主题模式");
        tvThemeLabel.setTextSize(15);
        rowTheme.addView(tvThemeLabel, new LinearLayout.LayoutParams(0, -2, 1.0f));
        android.widget.RadioGroup rgTheme = new android.widget.RadioGroup(activity);
        rgTheme.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        String[] themeNames = {"自动", "日", "夜"};
        int[] themeValues = {AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES};
        int currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        for (int i = 0; i < 3; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(activity);
            rb.setText(themeNames[i]);
            rb.setTextSize(13);
            rb.setId(i + 1000);
            rgTheme.addView(rb);
            if (currentMode == themeValues[i]) rb.setChecked(true);
        }
        rowTheme.addView(rgTheme);
        layout.addView(rowTheme);
        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedMode = themeValues[checkedId - 1000];
            if (selectedMode != prefs.getInt("theme_mode", -1)) {
                prefs.edit().putBoolean("reopen_layout_dialog", true).apply();
                prefs.edit().putInt("theme_mode", selectedMode).apply();
                AppCompatDelegate.setDefaultNightMode(selectedMode);
            }
        });

        builder.setView(scrollView);

        // 底部按钮设置
        builder.setPositiveButton("完成", (d, w) -> {
            View tvMsg = activity.findViewById(R.id.tv_message_area);
            if (tvMsg != null) ((TextView)tvMsg).setText("");
        });

        // 1. 设置中立按钮，但先不传监听器（防止自动关闭）
        builder.setNeutralButton("重置布局", null);

        final AlertDialog dialog = builder.create();
        dialog.show();

        // 2. 【核心修复】：手动接管重置按钮点击事件，使其不触发 dismiss()
        android.widget.Button btnResetLayout = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        btnResetLayout.setOnClickListener(v -> {
            // 执行重置逻辑
            prefs.edit()
                    .putInt("grid_margin_top", 20)
                    .putInt("message_margin_bottom", 0)
                    .putInt("layout_padding_top", 40)
                    .putInt("layout_padding_bottom", 30)
                    .apply();

            // 更新滑动条状态（这会通过监听器自动触发实时预览更新）
            sb1.setProgress(20);
            sb2.setProgress(200); // 200 代表偏移量 0
            sbTopPadding.setProgress(20);
            sbBottomPadding.setProgress(20);

            android.widget.Toast.makeText(activity, "已恢复默认布局参数", android.widget.Toast.LENGTH_SHORT).show();

            // 注意：此处不调用 dialog.dismiss()，所以对话框会保持显示
        });

        // 预览逻辑
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.getDecorView().setOnTouchListener((v, event) -> {
                float rawX = event.getRawX(); float rawY = event.getRawY();
                int[] loc = new int[2]; scrollView.getLocationOnScreen(loc);
                boolean isInside = rawX >= loc[0] && rawX <= (loc[0] + scrollView.getWidth()) &&
                        rawY >= loc[1] && rawY <= (loc[1] + scrollView.getHeight());
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && !isInside) {
                    window.getDecorView().setAlpha(0f); window.setDimAmount(0f); return true;
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP ||
                        event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    if (window.getDecorView().getAlpha() < 1f) {
                        window.getDecorView().setAlpha(1f); window.setDimAmount(0.5f); return true;
                    }
                }
                return false;
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

            if (id == 2000) { // 在线题库
                isExploringLocal = false;
                isExploringDocs = false; // [关键修复]：重置文档标记

                // [关键修复]：检查缓存内容是否匹配当前需要的目录
                boolean isCacheValid = (cachedRemoteFiles != null && !cachedRemoteFiles.isEmpty()
                        && cachedRemoteFiles.get(0).path.startsWith("data/"));

                if (!isCacheValid) {
                    fetchRemoteFilesAndShowDialog(); // 重新抓取 data/ 目录
                } else {
                    currentExplorerPath = "data/";
                    showFileExplorerDialog();
                }
                return true;
            }

            if (id == 555) {
                showLatexSettingsDialog(); // 修改为调用新方法
                return true;
            }

            if (id == 3000) { // 本地题库
                isExploringLocal = true;
                isExploringDocs = false; // [关键修复]：重置文档标记
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
            if (id == 4000) { // 在线文档
                isExploringDocs = true;
                isExploringLocal = false;
                currentExplorerPath = "files/";
                fetchFilesAndShow(currentExplorerPath, ".md");
                return true;
            }
            if (id == 5000) { // 本地文档
                isExploringDocs = true;
                isExploringLocal = true;
                currentExplorerPath = "files/";

                // 核心修复：必须先扫描本地 files 目录
                repository.fetchLocalFileTree("files/", new ProblemRepository.MenuDataCallback() {
                    @Override
                    public void onSuccess(List<ProblemRepository.RemoteFile> files) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            cachedLocalFiles = files;
                            showFileExplorerDialog();
                        });
                    }
                    @Override
                    public void onFail(String error) {
                        new Handler(Looper.getMainLooper()).post(() ->
                                Toast.makeText(activity, "本地文档目录为空", Toast.LENGTH_SHORT).show());
                    }
                });
                return true;
            }
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

        // --- 第一组：资源中心 ---
        SubMenu problemGroup = menu.addSubMenu(getStyledTitle("资源管理 / DATABASE"));
        problemGroup.add(Menu.NONE, 2000, Menu.NONE, "🌐  在线题库");
        problemGroup.add(Menu.NONE, 3000, Menu.NONE, "📂  本地题库");
        problemGroup.add(Menu.NONE, 999, Menu.NONE, "📥  一键同步");

        // --- 第二组：帮助文档 ---
        SubMenu docGroup = menu.addSubMenu(getStyledTitle("使用指南 / GUIDES"));
        docGroup.add(Menu.NONE, 4000, Menu.NONE, "🛜  在线文档");
        docGroup.add(Menu.NONE, 5000, Menu.NONE, "📑  本地缓存");

        // --- 第三组：系统工具 ---
        SubMenu settingsGroup = menu.addSubMenu(getStyledTitle("工具设定 / SETTINGS"));
        settingsGroup.add(Menu.NONE, 444, Menu.NONE, "📏  界面布局调整");
        settingsGroup.add(Menu.NONE, 777, Menu.NONE, "⚙️  游戏模式设定");
        settingsGroup.add(Menu.NONE, 666, Menu.NONE, "🧮  24点计算器");
        settingsGroup.add(Menu.NONE, 555, Menu.NONE, "💲  LaTeX 显示设置");

        // --- 第四组：快速开始 ---
        SubMenu randomGroup = menu.addSubMenu(getStyledTitle("随机模式 / RANDOM"));
        randomGroup.add(Menu.NONE, 103, Menu.NONE, "3️⃣ 随机休闲 (3数)");
        randomGroup.add(Menu.NONE, 104, Menu.NONE, "4️⃣ 随机休闲 (4数)");
        randomGroup.add(Menu.NONE, 105, Menu.NONE, "5️⃣ 随机休闲 (5数)");
    }

    /**
     * 辅助方法：生成一个看起来像“副标题”的样式字符串
     */
    private android.text.SpannableString getStyledTitle(String text) {
        // 在标题前后增加装饰线，使其更像分隔符
        String decoratedText = "──  " + text;
        android.text.SpannableString s = new android.text.SpannableString(decoratedText);

        // 1. 设置颜色为中灰色 (避开正文的纯黑/纯白)，产生层级感
        s.setSpan(new android.text.style.ForegroundColorSpan(0xFF888888), 0, decoratedText.length(), 0);

        // 2. 缩小字号 (0.8倍)，让分类标题不抢眼
        s.setSpan(new android.text.style.RelativeSizeSpan(0.8f), 0, decoratedText.length(), 0);

        // 3. 设置加粗
        s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, decoratedText.length(), 0);

        return s;
    }


    private void showLatexSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("📐 LaTeX 渲染设置");

        final ScrollView scrollView = new ScrollView(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        // 设置更大的左右边距和上下边距
        layout.setPadding(70, 50, 70, 50);
        scrollView.addView(layout);

        SharedPreferences prefs = activity.getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        float density = activity.getResources().getDisplayMetrics().density;

        // --- 1. 渲染总开关 ---
        androidx.appcompat.widget.SwitchCompat swLatex = new androidx.appcompat.widget.SwitchCompat(activity);
        swLatex.setText("启用 MathJax 高质量渲染");
        swLatex.setTextSize(16);
        // 增加开关的垂直间距
        swLatex.setPadding(0, 20, 0, 20);
        swLatex.setChecked(prefs.getBoolean("use_latex_mode", false));
        layout.addView(swLatex);

        // 分隔线 (带有较大的上下外边距)
        View divider = new View(activity);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, (int)(1.5 * density));
        divLp.setMargins(0, 30, 0, 40);
        divider.setLayoutParams(divLp);
        divider.setBackgroundColor(android.graphics.Color.LTGRAY);
        layout.addView(divider);

        // --- 2. 乘法符号 ---
        TextView tvMul = new TextView(activity);
        tvMul.setText("✖️ 乘法符号显示风格");
        tvMul.setTypeface(null, android.graphics.Typeface.BOLD); // 加粗
        tvMul.setTextSize(15);
        layout.addView(tvMul);

        Spinner spMul = new Spinner(activity);
        spMul.setPadding(0, 20, 0, 30); // 增加下方间距
        String[] mulOptions = {"使用叉号 (×)", "使用点号 (•)", "智能省略 (点号/括号)"};
        ArrayAdapter<String> mulAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, mulOptions);
        mulAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMul.setAdapter(mulAdapter);
        spMul.setSelection(prefs.getInt("latex_mul_mode", 1));
        layout.addView(spMul);

        // 增加组间距
        View space1 = new View(activity);
        layout.addView(space1, new LinearLayout.LayoutParams(-1, (int)(25 * density)));

        // --- 3. 除法符号 ---
        TextView tvDiv = new TextView(activity);
        tvDiv.setText("➗ 除法/分数显示风格");
        tvDiv.setTypeface(null, android.graphics.Typeface.BOLD); // 加粗
        tvDiv.setTextSize(15);
        layout.addView(tvDiv);

        Spinner spDiv = new Spinner(activity);
        spDiv.setPadding(0, 20, 0, 30);
        String[] divOptions = {"仅除法使用分数线", "除法与分数均使用分数线", "保持传统除号 (÷)"};
        ArrayAdapter<String> divAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, divOptions);
        divAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDiv.setAdapter(divAdapter);
        spDiv.setSelection(prefs.getInt("latex_div_mode", 0));
        layout.addView(spDiv);

        // 增加组间距
        View space2 = new View(activity);
        layout.addView(space2, new LinearLayout.LayoutParams(-1, (int)(25 * density)));

        // --- 4. 交互行为 ---
        TextView tvLP = new TextView(activity);
        tvLP.setText("🖱️ 公式长按交互行为");
        tvLP.setTypeface(null, android.graphics.Typeface.BOLD); // 加粗
        tvLP.setTextSize(15);
        layout.addView(tvLP);

        Spinner spLP = new Spinner(activity);
        spLP.setPadding(0, 20, 0, 20);
        String[] lpOptions = {"复制 LaTeX 源码", "复制纯文本算式", "MathJax 原生菜单"};
        ArrayAdapter<String> lpAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, lpOptions);
        lpAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLP.setAdapter(lpAdapter);
        spLP.setSelection(prefs.getInt("latex_long_press_mode", 0));
        layout.addView(spLP);

        // --- 逻辑绑定 ---
        Runnable updateAlpha = () -> {
            boolean enabled = swLatex.isChecked();
            float alpha = enabled ? 1.0f : 0.35f;
            tvMul.setAlpha(alpha); spMul.setEnabled(enabled); spMul.setAlpha(alpha);
            tvDiv.setAlpha(alpha); spDiv.setEnabled(enabled); spDiv.setAlpha(alpha);
            tvLP.setAlpha(alpha);  spLP.setEnabled(enabled);  spLP.setAlpha(alpha);
        };

        swLatex.setOnCheckedChangeListener((v, c) -> {
            prefs.edit().putBoolean("use_latex_mode", c).apply();
            updateAlpha.run();
            if (activity instanceof MainActivity) ((MainActivity) activity).updateDisplay("", null, false);
        });

        spMul.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (prefs.getInt("latex_mul_mode", -1) != pos) {
                    prefs.edit().putInt("latex_mul_mode", pos).apply();
                    if (activity instanceof MainActivity) ((MainActivity) activity).updateDisplay("", null, false);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        spDiv.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (prefs.getInt("latex_div_mode", -1) != pos) {
                    prefs.edit().putInt("latex_div_mode", pos).apply();
                    if (activity instanceof MainActivity) ((MainActivity) activity).updateDisplay("", null, false);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        spLP.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                prefs.edit().putInt("latex_long_press_mode", pos).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        updateAlpha.run();
        builder.setView(scrollView);
        builder.setPositiveButton("完成", null);
        builder.create().show();
    }



    private boolean isExploringDocs = false;

    private void fetchRemoteFilesAndShowDialog() {
        Toast.makeText(activity, "正在刷新目录...", Toast.LENGTH_SHORT).show();
        // 核心修复：补全 "data/" 和 ".txt" 两个参数
        repository.fetchRemoteFileTree("data/", ".txt", new ProblemRepository.MenuDataCallback() {
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
        builder.setTitle(isExploringLocal ? "📂 本地资源" : "🌐 在线资源");
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView tvPath = new TextView(activity);
        tvPath.setPadding(45, 30, 45, 10);
        layout.addView(tvPath);
        ListView listView = new ListView(activity);
        listView.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1.0f));
        layout.addView(listView);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, 0, new ArrayList<>()) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                if (convertView == null || !(convertView instanceof LinearLayout)) {
                    LinearLayout itemLayout = new LinearLayout(activity);
                    itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                    itemLayout.setPadding(45, 40, 45, 40);
                    TextView tvName = new TextView(activity);
                    tvName.setTextSize(16);
                    tvName.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
                    TextView tvCount = new TextView(activity);
                    tvCount.setTextSize(13);
                    itemLayout.addView(tvName); itemLayout.addView(tvCount);
                    convertView = itemLayout;
                }
                LinearLayout container = (LinearLayout) convertView;
                TextView tvName = (TextView) container.getChildAt(0);
                TextView tvCount = (TextView) container.getChildAt(1);
                String itemText = getItem(position);
                tvName.setText(itemText);
                // 简单处理计数显示
                tvCount.setVisibility(itemText.startsWith("📁") ? View.VISIBLE : View.GONE);
                return convertView;
            }
        };
        listView.setAdapter(adapter);
        builder.setView(layout).setNegativeButton("关闭", null);

        final AlertDialog dialog = builder.create(); // 声明为 final 以供内部调用

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String itemText = adapter.getItem(position);
            if (itemText == null) return;

            // 1. 处理返回上一级
            if (itemText.equals("🔙 返回上一级")) {
                String temp = currentExplorerPath.substring(0, currentExplorerPath.length() - 1);
                int lastSlash = temp.lastIndexOf('/');
                if (lastSlash != -1) {
                    currentExplorerPath = temp.substring(0, lastSlash + 1);
                    updateExplorerView(tvPath, adapter);
                }
                return;
            }

            // 2. 处理进入文件夹
            if (itemText.startsWith("📁 ")) {
                currentExplorerPath += itemText.replace("📁 ", "") + "/";
                updateExplorerView(tvPath, adapter);
                return;
            }

            // 3. 处理文件点击
            if (itemText.startsWith("📄 ")) {
                String fileName = itemText.replace("📄 ", "");
                String fullPath = currentExplorerPath + fileName;
                dialog.dismiss();

                if (isExploringDocs) {
                    List<String> allDocsInFolder = new ArrayList<>();
                    // 新增：记录文件名到 SHA 的映射
                    java.util.Map<String, String> nameToShaMap = new java.util.HashMap<>();

                    for (int i = 0; i < adapter.getCount(); i++) {
                        String text = adapter.getItem(i);
                        if (text != null && text.startsWith("📄 ")) {
                            String name = text.replace("📄 ", "");
                            allDocsInFolder.add(name);

                            // 从缓存的远程文件列表中寻找 SHA
                            if (!isExploringLocal && cachedRemoteFiles != null) {
                                for (ProblemRepository.RemoteFile rf : cachedRemoteFiles) {
                                    if (rf.name.equals(name) && rf.path.contains(currentExplorerPath)) {
                                        nameToShaMap.put(name, rf.sha);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    Collections.sort(allDocsInFolder);
                    int initialIndex = allDocsInFolder.indexOf(fileName);

                    // 传入映射表
                    showScrollingDocsDialog(allDocsInFolder, initialIndex, nameToShaMap);
                } else {
                    // 题库逻辑保持不变
                    if (isExploringLocal) loadLocalProblemSet(fullPath);
                    else startDownloadWithProgress(fullPath, fileName);
                }
            }
        });

        dialog.show();
        updateExplorerView(tvPath, adapter);
    }


    // SidebarLogic.java

    private void showScrollingDocsDialog(List<String> docNames, int startIndex, java.util.Map<String, String> shaMap) {
        // 1. 直接创建 Dialog 对象，使用系统自带的无状态栏全屏主题
        final android.app.Dialog docDialog = new android.app.Dialog(activity, android.R.style.Theme_NoTitleBar_Fullscreen);

        // 2. 使用 FrameLayout 作为容器
        android.widget.FrameLayout root = new android.widget.FrameLayout(activity);
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        // 显式设置根布局为铺满
        root.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1, -1));

        // 3. ViewPager2 铺满全屏
        ViewPager2 viewPager = new ViewPager2(activity);
        viewPager.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, -1));

        viewPager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
                WebView wv = new WebView(activity);
                wv.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1, -1));
                WebSettings s = wv.getSettings();
                s.setJavaScriptEnabled(true);
                s.setAllowFileAccess(true);
                s.setAllowUniversalAccessFromFileURLs(true);
                s.setDomStorageEnabled(true);
                return new RecyclerView.ViewHolder(wv) {};
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                WebView wv = (WebView) holder.itemView;
                String fileName = docNames.get(position);
                String path = currentExplorerPath + fileName;
                String remoteSha = (shaMap != null) ? shaMap.get(fileName) : null;

                new Thread(() -> {
                    try {
                        String content;
                        boolean needsUpdate = !isExploringLocal && remoteSha != null && repository.needsUpdate(path, remoteSha);
                        if (isExploringLocal || (!needsUpdate && repository.isFileDownloaded(path))) {
                            java.io.File file = new java.io.File(activity.getFilesDir(), path);
                            java.io.FileInputStream fis = new java.io.FileInputStream(file);
                            byte[] data = new byte[(int) file.length()];
                            fis.read(data); fis.close();
                            content = new String(data, "UTF-8");
                        } else {
                            content = repository.downloadRawText(path);
                            saveDocToLocal(path, content, remoteSha);
                        }
                        String html = MarkdownUtils.renderMarkdown(content);
                        activity.runOnUiThread(() -> wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null));
                    } catch (Exception e) {
                        activity.runOnUiThread(() -> wv.loadData("<html><body>加载失败</body></html>", "text/html", "UTF-8"));
                    }
                }).start();
            }
            @Override public int getItemCount() { return docNames.size(); }
        });

        // 4. 创建半透明悬浮关闭按钮
//        Button btnClose = new Button(activity);
//        btnClose.setText("✕");
//        btnClose.setTextSize(18);
//        btnClose.setTextColor(0xFFFFFFFF);
//        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
//        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
//        shape.setColor(0x66000000);
//        btnClose.setBackground(shape);

        float d = activity.getResources().getDisplayMetrics().density;
        android.widget.FrameLayout.LayoutParams btnParams = new android.widget.FrameLayout.LayoutParams((int)(40*d), (int)(40*d));
        btnParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        btnParams.topMargin = (int)(20 * d);
        btnParams.rightMargin = (int)(20 * d);

        // 5. 将组件加入 root
        root.addView(viewPager);
//        root.addView(btnClose, btnParams);

        // 6. 设置 Dialog 内容并处理 Window 属性
        docDialog.setContentView(root);
//        btnClose.setOnClickListener(v -> docDialog.dismiss());

        if (docDialog.getWindow() != null) {
            // 强制隐藏状态栏
            docDialog.getWindow().setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
            // 关键修复：强制设置 Window 宽高为 MATCH_PARENT，并去除背景限制
            docDialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            docDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        }

        viewPager.setCurrentItem(startIndex, false);
        docDialog.show();

        // show 之后再次确认布局大小，适配部分机型
        docDialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
    }




    // 辅助递归删除（放在 SidebarLogic 类末尾即可）
    private void deleteRecursive(java.io.File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (java.io.File child : fileOrDirectory.listFiles()) deleteRecursive(child);
        }
        fileOrDirectory.delete();
    }

    private void handleDocSelection(String path, String name) {
        // 注：有了 showScrollingDocsDialog 后，此方法通常只作为单个文档打开的回退
        // 这里我们也需要匹配 3 参数的 saveDocToLocal
        new Thread(() -> {
            try {
                String content = repository.downloadRawText(path);
                // 暂时传 null，因为单选模式很难直接获取 SHA 列表，
                // 建议统一走 showScrollingDocsDialog 逻辑。
                saveDocToLocal(path, content, null);

                final String html = MarkdownUtils.renderMarkdown(content);
                activity.runOnUiThread(() -> showMarkdownWebViewDialog(name, html));
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(activity, "加载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }


    private void showMarkdownWebViewDialog(String title, String html) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        WebView wv = new WebView(activity);
        // --- 核心修复：开启 WebView 的脚本执行能力 ---
        WebSettings settings = wv.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true); // 允许访问 assets
        settings.setDomStorageEnabled(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        // ------------------------------------------
        wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
        b.setView(wv);
        b.setPositiveButton("关闭", null);
        b.setTitle(title);
        b.show();
    }

    private void updateExplorerView(TextView tvPath, ArrayAdapter<String> adapter) {
        tvPath.setText("当前位置: " + (isExploringLocal ? "本地/" : "远程/") + currentExplorerPath);

        List<String> items = new ArrayList<>();
        Set<String> folders = new HashSet<>();
        List<String> files = new ArrayList<>();

        // 根据模式选择数据源
        List<ProblemRepository.RemoteFile> dataSource = isExploringLocal ? cachedLocalFiles : cachedRemoteFiles;
        if (dataSource == null || dataSource.isEmpty()) {
            Toast.makeText(activity, "暂无可用资源，请尝试刷新", Toast.LENGTH_SHORT).show();
            // 如果是在线模式且为空，自动触发一次刷新 (可选)
            if (!isExploringLocal) {
                fetchRemoteFilesAndShowDialog();
                return;
            }
        }

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

        if (currentExplorerPath.contains("/") && currentExplorerPath.length() > 6) {
            // 这里的 6 是为了避开 "data/" 或 "files/"
            items.add(0, "🔙 返回上一级");
        }

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

        builder.setTitle("⚙️ 游戏模式设定")
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

    // [Restored] showCalculatorDialog
    // SidebarLogic.java 中的计算器对话框完整实现
    private void showCalculatorDialog() {
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

        // --- 滑块调节区域 (用于选择 Mod 数或 进制) ---
        LinearLayout sliderContainer = new LinearLayout(activity);
        sliderContainer.setOrientation(LinearLayout.VERTICAL);
        sliderContainer.setVisibility(View.GONE); // 初始隐藏
        sliderContainer.setPadding(0, 20, 0, 20);

        final TextView tvSliderValue = new TextView(activity);
        tvSliderValue.setTextSize(14);
        tvSliderValue.setPadding(10, 0, 0, 5);

        final android.widget.SeekBar seekBar = new android.widget.SeekBar(activity);

        sliderContainer.addView(tvSliderValue);
        sliderContainer.addView(seekBar);
        layout.addView(sliderContainer);

        // 模式切换监听：调整滑块的范围和文案
        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) { // 常规
                    sliderContainer.setVisibility(View.GONE);
                    etInput.setHint("请输入数字 (例如 3 3 8 8)");
                } else if (position == 1) { // 同余
                    sliderContainer.setVisibility(View.VISIBLE);
                    seekBar.setMax(MOD_PRIMES.length - 1);
                    seekBar.setProgress(0);
                    tvSliderValue.setText("模数 (n): " + MOD_PRIMES[0]);
                    etInput.setHint("请输入 0 到 n-1 之间的整数");
                } else { // 进制
                    sliderContainer.setVisibility(View.VISIBLE);
                    seekBar.setMax(11); // 5-16进制，共12个档位
                    seekBar.setProgress(5); // 默认 10 进制
                    tvSliderValue.setText("显示进制: 10");
                    etInput.setHint("请输入对应进制的数字 (支持 A-F)");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 滑块滑动实时更新文字
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

        // --- 按钮和结果显示 (保持不变) ---
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
        try {
            // 使用当前选定的进制解析输入
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

    private List<Fraction> parseInputString(String input, Integer modulus, int radix) throws Exception {
        List<Fraction> list = new ArrayList<>();

        // --- 准备正则表达式：定义当前模式下的有效字符 ---
        String validCharsRegex;
        boolean isNormalMode = (modulus == null && radix == 10);

        if (isNormalMode) {
            // 通常模式有效字符：数字、运算符、点号、AJQK、虚数 i
            // [^...] 表示“除了这些以外都是分隔符”
            validCharsRegex = "[^0-9a-zA-Z+\\-*/.]+";

            // --- 执行 AJQK 替换 ---
            // 匹配独立单词，两边自动留空格防止数字粘连
            input = input.replaceAll("(?i)\\bA\\b", " 1 ")
                    .replaceAll("(?i)\\bJ\\b", " 11 ")
                    .replaceAll("(?i)\\bQ\\b", " 12 ")
                    .replaceAll("(?i)\\bK\\b", " 13 ");
        } else if (modulus != null) {
            // 同余模式：仅数字、运算符、点号有效。严禁任何字母。
            validCharsRegex = "[^0-9+\\-*/.]+";
        } else {
            // 进制模式：数字、该进制允许的字母 (A-F)、运算符、点号有效。虚数 i 无效。
            validCharsRegex = "[^0-9a-fA-F+\\-*/.]+";
        }

        // --- 分割字符串 ---
        String[] parts = input.split(validCharsRegex);

        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) continue;

            String pLower = p.toLowerCase();

            // --- 模式校验 ---
            if (!isNormalMode) {
                // 在同余或进制模式下，如果含有 i/I，报错
                if (pLower.contains("i")) {
                    throw new Exception("该模式不支持虚数 (i)");
                }
            }

            // --- 进制合法性深度检查 ---
            // 移除所有辅助字符（运算符、括号、点号、虚数单位）
            String numericOnly = p.replaceAll("[iI+\\-*/().]", "");
            for (char c : numericOnly.toCharArray()) {
                int digitValue = Character.digit(c, radix);
                if (digitValue == -1 || digitValue >= radix) {
                    throw new Exception("字符 '" + c + "' 超出当前 " + radix + " 进制范围");
                }
            }

            // 校验通过，调用解析器
            list.add(Fraction.parse(p, radix));
        }
        return list;
    }


    // 1. 新增：通用的远程文件抓取并打开资源管理器方法
    private void fetchFilesAndShow(String rootDir, String extension) {
        if (cachedRemoteFiles != null && !cachedRemoteFiles.isEmpty()
                && !cachedRemoteFiles.get(0).path.startsWith(rootDir)) {
            cachedRemoteFiles = null;
        }
        Toast.makeText(activity, "正在同步目录...", Toast.LENGTH_SHORT).show();
        repository.fetchRemoteFileTree(rootDir, extension, new ProblemRepository.MenuDataCallback() {
            @Override
            public void onSuccess(List<ProblemRepository.RemoteFile> files) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    cachedRemoteFiles = files;
                    currentExplorerPath = rootDir;
                    showFileExplorerDialog();
                });
            }
            @Override
            public void onFail(String error) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(activity, "同步失败: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    // 2. 新增：保存文档到本地
// SidebarLogic.java

    private void saveDocToLocal(String path, String content, String sha) {
        try {
            java.io.File file = new java.io.File(activity.getFilesDir(), path);
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            java.io.FileWriter fw = new java.io.FileWriter(file);
            fw.write(content);
            fw.close();

            // 存入 SHA 标记，确保版本刷新逻辑生效
            if (sha != null && !sha.isEmpty()) {
                repository.saveLocalFileSHA(path, sha);
            }
        } catch (Exception e) { e.printStackTrace(); }
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
