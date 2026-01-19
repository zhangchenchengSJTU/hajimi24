package com.example.hajimi24;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.res.ResourcesCompat;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import java.util.Collections;
import android.content.Intent;



import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    public static Problem sharedProblem = null;
    public static List<Problem> sharedProblemSet = null;
    public static boolean sharedIsRandomMode = true;
    private List<Problem> lastLoadedProblemSet = new ArrayList<>();
    private String currentLatexCode = "";
    private String currentPlainTextForLatex = "";

    private Problem mCurrentProblem;
    private String lastShownType = ""; // "", "struct", "answer"
    private String currentLoadedFile = null;
    private DrawerLayout drawerLayout;
    private int initialCount = 4;
    private TextView tvScore, tvTimer, tvAvgTime, tvMessage;
    private Button[] cardButtons = new Button[5];
    private Button btnAdd, btnSub, btnMul, btnDiv;
    private Button btnUndo, btnReset, btnRedo, btnMenu;
    private Button btnTry, btnHintStruct, btnAnswer, btnShare, btnSkip;
    private GameManager gameManager;
    private Toast mCurrentToast;
    private ProblemRepository repository;
    private GameTimer gameTimer;
    private SidebarLogic sidebarLogic;
    private long gameStartTime;
    private int selectedFirstIndex = -1;
    private String selectedOperator = null;
    private String currentFileName = "随机休闲(4数)";
    private String lastPlainTextSolution = "";
    private void checkOverlayPermissionAndStart() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                // 没有权限，引导用户去开启
                showCustomToast("请开启悬浮窗权限以使用此功能");
                android.content.Intent intent = new android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, 1234);
            } else {
                startFloatingService();
            }
        } else {
            startFloatingService();
        }
    }

    private void startFloatingService() {
        Intent intent = new Intent(this, FloatingWindowService.class);

        // 同步模式
        sharedIsRandomMode = currentFileName.startsWith("随机休闲");

        // 同步题目对象：如果是随机题目，手动包装，确保 Service 能拿到数字
        Problem current = gameManager.getCurrentProblem();
        if (current == null) {
            List<Fraction> nums = new ArrayList<>();
            for (Fraction f : gameManager.initialValues) if (f != null) nums.add(f);
            current = new Problem(nums, gameManager.currentLevelSolution, gameManager.getRawProblemLine(), null, 10);
        }
        sharedProblem = current;

        // 同步整个题库（如果是题库模式）
        sharedProblemSet = sharedIsRandomMode ? null : lastLoadedProblemSet;

        startService(intent);
        moveTaskToBack(true);
    }

    // 在 MainActivity.java 中

    public void applyTextWeight(boolean isBold) {
        // [核心修复]：每次都从资源加载原始字体族，确保 NORMAL 状态能准确还原
        android.graphics.Typeface tf = ResourcesCompat.getFont(this, R.font.app_default_font);
        int style = isBold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL;

        // 1. 设置五个数字卡片按钮
        for (Button b : cardButtons) {
            if (b != null) b.setTypeface(tf, style);
        }

        // 2. 设置加减乘除运算符按钮
        if (btnAdd != null) btnAdd.setTypeface(tf, style);
        if (btnSub != null) btnSub.setTypeface(tf, style);
        if (btnMul != null) btnMul.setTypeface(tf, style);
        if (btnDiv != null) btnDiv.setTypeface(tf, style);

        // 3. 提示区域
        if (tvMessage != null) tvMessage.setTypeface(tf, style);
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;

        decorView.setSystemUiVisibility(uiOptions);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
    }



    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }
    private void applySavedLayoutMargin() {
        SharedPreferences prefs = getSharedPreferences("AppConfig", MODE_PRIVATE);
        int marginTopDp = prefs.getInt("grid_margin_top", 40);
        int messageBottomDp = prefs.getInt("message_margin_bottom", 0);
        // 新增读取
        int layoutTopDp = prefs.getInt("layout_padding_top", 50);
        int layoutBottomDp = prefs.getInt("layout_padding_bottom", 30);

        findViewById(R.id.grid_cards).post(() -> {
            float density = getResources().getDisplayMetrics().density;

            // A. 设置整体 Padding (对主 ConstraintLayout)
            View mainContent = findViewById(R.id.btn_menu).getParent() instanceof View ?
                    (View)findViewById(R.id.btn_menu).getParent() : null;
            if (mainContent != null) {
                int side = (int)(16 * density);
                mainContent.setPadding(side, (int)(layoutTopDp * density), side, (int)(layoutBottomDp * density));
            }

            // B. 原有的信息区偏移逻辑
            if (tvMessage != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) tvMessage.getLayoutParams();
                lp.bottomMargin = (int) (messageBottomDp * density);
                tvMessage.setLayoutParams(lp);
            }
        });
    }

    // 核心修复：更新方法签名，增加 int depth 参数
    private void renderLatexInWebView(WebView wv, String content, int height) {

        // --- 获取当前主题颜色 (代码保持原样) ---
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
        int colorInt = typedValue.data;
        String colorHex = String.format("#%06X", (0xFFFFFF & colorInt));

        WebSettings settings = wv.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        wv.setVisibility(View.VISIBLE);
        wv.setAlpha(0.01f);

        // --- 基于垂直高度的缩放逻辑 ---
        String fontSize;
        if (height <= 2) {
            fontSize = "125%"; // 1:普通文字, 2:简单分数 (如 1/2)
        } else if (height == 3) {
            fontSize = "105%"; // 一侧嵌套 (如 1/(2/3))
        } else if (height == 4) {
            fontSize = "88%";  // 双侧嵌套 (如 (1/2)/(3/4)) -> 这是你要求的 4
        } else if (height == 5) {
            fontSize = "75%";  // 更复杂的层级
        } else {
            fontSize = "65%";  // 极限情况
        }

        wv.setBackgroundColor(0);

        adjustWebViewContainerHeight(wv, height);

        String escaped = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

        String html =
                "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                        "<style>body { margin: 0; padding: 0; display: flex; justify-content: center; align-items: center; " +
                        "height: 100vh; background: transparent; overflow: hidden; color: #000000; } " +
                        "@media (prefers-color-scheme: dark) { body { color: #FFFFE0; } } " +
                        "#math { font-size: " + fontSize + "; text-align: center; width: 100%; }</style>" +
                        "<script>window.MathJax = { tex: { inlineMath: [['$', '$']], displayMath: [['$$', '$$']] }, " +
                        "svg: { fontCache: 'global' }, startup: { ready: () => { MathJax.startup.defaultReady(); " +
                        "MathJax.startup.promise.then(() => { window.android.onRenderFinished(); }); } } };</script>" +
                        "<script id='MathJax-script' src='file:///android_asset/mathjax/tex-svg.js'></script></head>" +
                        "<body><div id='math'>$$\\displaystyle " + escaped + "$$</div></body></html>";

        wv.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void onRenderFinished() {
                wv.post(() -> wv.animate().alpha(1.0f).setDuration(100).start());
            }
        }, "android");

        wv.loadDataWithBaseURL("file:///android_asset/mathjax/", html, "text/html", "UTF-8", null);
    }

    private void adjustWebViewContainerHeight(WebView wv, int formulaHeight) {
        float density = getResources().getDisplayMetrics().density;
        int baseHeightDp = 100;
        int extraHeightPerUnit = 32;

        // 1. 先计算出临时的高度
        int calculatedHeight;
        if (formulaHeight <= 2) {
            calculatedHeight = baseHeightDp;
        } else {
            calculatedHeight = baseHeightDp + (formulaHeight - 2) * extraHeightPerUnit;
        }

        // 2. 【关键修复】：定义一个全新的 final 变量，并赋予最终值（包括 Math.min 限制）
        // 这样这个变量在初始化后就不会再变，符合 Lambda 的要求
        final int finalTotalHeight = Math.min(calculatedHeight, 300);

        // 3. 在 Lambda 中引用这个 final 变量
        wv.post(() -> {
            android.view.ViewGroup.LayoutParams params = wv.getLayoutParams();
            if (params != null) {
                // 使用 finalTotalHeight
                params.height = (int) (finalTotalHeight * density);
                wv.setLayoutParams(params);
            }
        });
    }


    // 3. 新增：带自定义高度的提示方法 (用于替换原来的 Toast)
    public void showCustomToast(String text) {
        if (text == null || text.isEmpty()) return;

        SharedPreferences prefs = getSharedPreferences("AppConfig", MODE_PRIVATE);
        int yOffsetDp = prefs.getInt("toast_y_offset", 64);
        float density = getResources().getDisplayMetrics().density;

        if (mCurrentToast != null) mCurrentToast.cancel();

        mCurrentToast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        mCurrentToast.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL,
                0, (int)(yOffsetDp * density));
        mCurrentToast.show();
    }
    private void showThemeSelectionDialog() {
        final String[] themes = {"跟随系统", "日间模式", "夜间模式"};

        // 获取当前保存的模式，用于确定默认选中哪一项
        SharedPreferences prefs = getSharedPreferences("AppConfig", MODE_PRIVATE);
        int currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        int checkedItem;
        switch (currentMode) {
            case AppCompatDelegate.MODE_NIGHT_NO: checkedItem = 1; break;   // 日间
            case AppCompatDelegate.MODE_NIGHT_YES: checkedItem = 2; break;  // 夜间
            default: checkedItem = 0; break;                                // 跟随系统
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("选择主题")
                .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
                    int selectedMode;
                    if (which == 1) {
                        selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
                    } else if (which == 2) {
                        selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
                    } else {
                        selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    }

                    // 1. 保存设置
                    prefs.edit().putInt("theme_mode", selectedMode).apply();

                    // 2. 应用设置 (会触发生命周期重启 Activity)
                    AppCompatDelegate.setDefaultNightMode(selectedMode);

                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. 读取保存的主题设置
        SharedPreferences prefs = getSharedPreferences("AppConfig", MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(themeMode);
        super.onCreate(savedInstanceState);

        // --- 核心修复 1：在加载布局前，彻底锁定全屏与透明状态栏 ---
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT); // 状态栏设为透明
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        // --- 核心修复：立即执行全屏锁定 ---
        hideSystemUI();

        setContentView(R.layout.activity_main);
        repository = new ProblemRepository(this);
        gameManager = new GameManager();

        // --- 修改点：判断是新开始还是从旋转中恢复 ---
        if (savedInstanceState != null) {
            gameStartTime = savedInstanceState.getLong("gameStartTime");
        } else {
            gameStartTime = System.currentTimeMillis();
        }
        // ----------------------------------------

        initViews();
        initHelpers();
        initListeners();
        gameStartTime = System.currentTimeMillis();
        switchToRandomMode(4);
        applySavedLayoutMargin();
        applyTextWeight(prefs.getBoolean("use_bold_text", false));
        if (prefs.getBoolean("reopen_layout_dialog", false)) {
            prefs.edit().putBoolean("reopen_layout_dialog", false).apply();
            // 延迟一小会儿弹出，等待 Activity 界面绘制完成
            new android.os.Handler().postDelayed(() -> {
                if (sidebarLogic != null) sidebarLogic.showLayoutAdjustmentDialog();
            }, 200);
        }

    }

// MainActivity.java

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();

        if (sharedProblem != null) {
            try {
                // 【核心修复 1】：获取传回题目的实际数字个数 (通常是 4)
                int incomingCount = sharedProblem.numbers.size();

                // 【核心修复 2】：强制同步 MainActivity 的布局计数器
                this.initialCount = incomingCount;
                this.gameManager.currentNumberCount = incomingCount;

                // 【核心修复 3】：如果是从 5 数降级回来的随机模式，修正模式名称
                // 防止回到主界面点“跳过”又跳回 5 数模式
                if (incomingCount == 4 && sharedIsRandomMode) {
                    this.currentFileName = "随机休闲(4数)";
                    updateMenuButtonText(this.currentFileName);
                }

                // 1. 锁定当前这道题目
                List<Problem> single = new ArrayList<>();
                single.add(sharedProblem);
                gameManager.setProblemSet(single);
                gameManager.startNewGame(false); // 加载这道特定题

                // 2. 恢复完整题库列表
                if (!sharedIsRandomMode && sharedProblemSet != null) {
                    this.lastLoadedProblemSet = sharedProblemSet;
                    gameManager.setProblemSet(sharedProblemSet);
                }

                // 3. 彻底重绘 UI
                resetSelection();
                refreshUI(); // 这一步会根据新的 initialCount 重新计算 2x2 还是 2x3 布局

            } catch (Exception e) {
                e.printStackTrace();
            }
            sharedProblem = null; // 消费掉
        }
    }


    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }
    private void updateMenuButtonText(String rawName) {
        // 1. 去掉 .txt 后缀
        String nameWithoutExt = rawName.replace(".txt", "");

        // 2. 核心：只取最后一个 '/' 之后的部分，从而彻底隐藏文件夹名称
        String cleanName = nameWithoutExt;
        int lastSlash = nameWithoutExt.lastIndexOf('/');
        if (lastSlash != -1) {
            cleanName = nameWithoutExt.substring(lastSlash + 1);
        }

        // 3. 处理横杠换行和括号逻辑
        String displayText;
        if (cleanName.contains("-")) {
            String[] parts = cleanName.split("-", 2);
            displayText = "☰ 模式: " + parts[0] + "\n(" + parts[1] + ")";
        } else {
            displayText = "☰ 模式: " + cleanName;
        }

        btnMenu.setText(displayText);
    }

    private void initHelpers() {
        NavigationView navView = findViewById(R.id.nav_view);
        // Correctly pass parameters
        sidebarLogic = new SidebarLogic(this, drawerLayout, navView, repository, new SidebarLogic.ActionCallback() {
            @Override
            public void onRandomMode(int count) {
                switchToRandomMode(count);
            }

            @Override
            public void onLoadProblems(List<Problem> problems, String title) {
                if (isLandscape()) {
                    List<Problem> filtered = new ArrayList<>();
                    for (Problem p : problems) {
                        if (p.numbers != null && p.numbers.size() == 4) filtered.add(p);
                    }
                    problems = filtered;
                    if (problems.isEmpty()) {
                        showCustomToast("横屏仅支持 4 数题目");
                        switchToRandomMode(4);
                        return;
                    }
                }
                // 【核心修复】：明确指向外部类的变量
                MainActivity.this.lastLoadedProblemSet = problems;

                gameManager.setProblemSet(problems);
                currentFileName = title;
                updateMenuButtonText(title);
                showCustomToast("加载成功!");
                startNewGameLocal();
            }

            @Override
            public void onSettingsChanged() {
                // [核心修改]：不再显示“下次生效”，而是直接刷新
                if (sidebarLogic.isCurrentModeRandom) {
                    // 如果是随机休闲模式，直接开始新的一局（随机休闲题目重新生成）
                    startNewGameLocal();
                } else {
                    // 如果是题库模式，获取当前记录的文件路径，重新调用加载方法
                    // 这会触发 Repository 重新读取文件并根据新设置进行过滤
                    String path = sidebarLogic.currentLoadedFileName;
                    if (path != null) {
                        loadProblemSet(path);
                    }
                }
            }
        });
        sidebarLogic.setup();
        gameTimer = new GameTimer(() -> {
            if (tvTimer != null) {
                tvTimer.setText(gameTimer.getElapsedSeconds() + "s");
            }
            updateScoreBoard(); // 这里会用到 gameStartTime
        });
        gameTimer.start();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        btnMenu = findViewById(R.id.btn_menu);
        tvScore = findViewById(R.id.tv_score);
        tvTimer = findViewById(R.id.tv_timer);
        tvAvgTime = findViewById(R.id.tv_avg_time);
        tvMessage = findViewById(R.id.tv_message_area);
        cardButtons[0] = findViewById(R.id.card_1); cardButtons[1] = findViewById(R.id.card_2); cardButtons[2] = findViewById(R.id.card_3); cardButtons[3] = findViewById(R.id.card_4); cardButtons[4] = findViewById(R.id.card_5);
        btnAdd = findViewById(R.id.btn_op_add); btnSub = findViewById(R.id.btn_op_sub); btnMul = findViewById(R.id.btn_op_mul); btnDiv = findViewById(R.id.btn_op_div);
        btnUndo = findViewById(R.id.btn_undo); btnReset = findViewById(R.id.btn_reset); btnRedo = findViewById(R.id.btn_redo); btnTry = findViewById(R.id.btn_try); btnHintStruct = findViewById(R.id.btn_hint_struct); btnAnswer = findViewById(R.id.btn_answer); btnShare = findViewById(R.id.btn_share); btnSkip = findViewById(R.id.btn_skip);
        btnMenu.setSingleLine(false);
        btnMenu.setMaxLines(2);
    }

    // 修改 loadProblemSet 方法
    public void loadProblemSet(String fileName) {
        try {
            List<Problem> problems = repository.loadProblemSet(fileName, sidebarLogic.getGameModeSettings());

            // --- 新增横屏过滤逻辑 ---
            if (isLandscape()) {
                List<Problem> filtered = new ArrayList<>();
                for (Problem p : problems) {
                    // 【修正】：使用 p.numbers
                    if (p.numbers != null && p.numbers.size() == 4) {
                        filtered.add(p);
                    }
                }
                problems = filtered;
                if (problems.isEmpty()) {
                    showCustomToast("该题库无 4 数题目，已切换至随机模式");
                    switchToRandomMode(4);
                    return;
                }
            }
            // -----------------------
            this.lastLoadedProblemSet = problems;
            gameManager.setProblemSet(problems);
            currentFileName = fileName;
            updateMenuButtonText(fileName);
            showCustomToast("加载成功!");
            startNewGameLocal();
        } catch (Exception e) {
            e.printStackTrace();
            switchToRandomMode(4);
        }
    }


    public void switchToRandomMode(int count) {
        if (isLandscape()) {
            count = 4; // 横屏模式强制锁定 4 个数字
        }
        gameManager.currentNumberCount = count;
        currentFileName = "随机休闲(" + count + "数)";
        updateMenuButtonText(currentFileName);
        startNewGameLocal();
    }
    private void startNewGameLocal() {
        gameManager.startNewGame(currentFileName.startsWith("随机休闲"));
        // 【新增】：记录本局初始卡片数量，用于固定布局
        initialCount = gameManager.currentNumberCount;

        if (gameTimer != null) gameTimer.start();
        resetSelection();
        refreshUI();
        updateDisplay("", null, false);
        lastShownType = "";
        lastPlainTextSolution = "";
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("gameStartTime", gameStartTime);
    }
    private void refreshUI() {
        if (gameManager == null || cardButtons == null) return;

        SharedPreferences prefs = getSharedPreferences("AppConfig", MODE_PRIVATE);
        int baseMarginTopDp = prefs.getInt("grid_margin_top", 40);

        Integer modulus = null;
        int currentRadix = 10;
        Problem p = gameManager.getCurrentProblem();
        if (p != null) {
            modulus = p.modulus;
            if (p.radix != null) currentRadix = p.radix;
        }

        int count = initialCount;
        float density = getResources().getDisplayMetrics().density;
        int btnSize = (int) (110 * density);
        int margin = (int) (5 * density);

        View gridView = findViewById(R.id.grid_cards);
        if (gridView != null) {
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams gridLp =
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) gridView.getLayoutParams();

            // 修正：这里仅处理整体边距，不计算 row/col
            if (isLandscape()) {
                gridLp.topMargin = (int) (10 * density); // 横屏只需要很小的顶边距
            } else {
                // 竖屏保持原样
                if (count <= 4) {
                    gridLp.topMargin = (int) ((baseMarginTopDp + 120) * density);
                } else {
                    gridLp.topMargin = (int) (baseMarginTopDp * density);
                }
            }
            gridView.setLayoutParams(gridLp);
        }

        for (int i = 0; i < 5; i++) {
            Button btn = cardButtons[i];
            if (btn == null) continue;

            if (i >= count) {
                btn.setVisibility(View.GONE);
            } else {
                int row, col;
                // 【核心修正】：坐标计算必须在循环内，且根据横竖屏调整
                if (count == 3) {
                    if (i == 0) { row = 0; col = 0; } // 3个数在横屏建议 1+2 布局
                    else { row = 1; col = i - 1; }
                } else if (count == 4) {
                    row = i / 2; // 直接使用 0, 1 行，不要加 1
                    col = i % 2;
                } else {
                    // 5个数
                    if (i == 0) { row = 0; col = 0; }
                    else { row = (i - 1) / 2 + 1; col = (i - 1) % 2; }
                }

                // --- 【核心修复：锁定位置，防止位移】 ---
                // 1. 在 spec 中指定对齐方式为 CENTER
                android.widget.GridLayout.Spec rowSpec = android.widget.GridLayout.spec(row, android.widget.GridLayout.CENTER);
                android.widget.GridLayout.Spec colSpec = android.widget.GridLayout.spec(col, android.widget.GridLayout.CENTER);

                android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams(rowSpec, colSpec);

                // 2. 显式设置 LayoutParams 的 gravity
                lp.setGravity(android.view.Gravity.CENTER);

                lp.width = btnSize;
                lp.height = btnSize;
                lp.setMargins(margin, margin, margin, margin);
                btn.setLayoutParams(lp);

                Fraction f = gameManager.cardValues[i];
                if (f != null) {
                    btn.setVisibility(View.VISIBLE);
                    String text = (modulus != null) ? f.toModString(modulus, currentRadix) : f.toString(currentRadix);
                    btn.setText(text);

                    // 保持您之前的选中变绿逻辑
                    if (i == selectedFirstIndex) {
                        btn.setBackgroundColor(Color.GREEN);
                    } else {
                        btn.setBackgroundColor(Color.parseColor("#CCCCCC"));
                    }
                } else {
                    btn.setVisibility(View.INVISIBLE);
                }
            }
        }
        updateScoreBoard();
    }


    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        boolean wasRunning = gameTimer != null;

        // 1. 重新加载布局（系统会自动根据当前横竖屏选择 layout 或 layout-land 文件夹下的 xml）
        setContentView(R.layout.activity_main);

        // 2. 因为布局重新加载了，必须重新初始化所有 View 引用和监听器
        initViews();

        initHelpers();
        initListeners();
        applySavedLayoutMargin(); // 应用您之前的边距设置

        // 3. 【核心逻辑】：
        // 如果当前题目正好是 4 个数，直接刷新 UI 显示当前进度，不开始新游戏
        if (gameManager.currentNumberCount == 4) {
            refreshUI();
        } else {
            // 如果当前是 3 或 5 个数（横屏不支持），则强制刷新到随机 4 数
            if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                switchToRandomMode(4);
            } else {
                refreshUI(); // 竖屏则直接显示
            }
        }
        refreshUI();
    }

    private void onCardClicked(int index) {
        if (gameManager == null) return;
        if (selectedFirstIndex == -1) selectCard(index);
        else if (selectedFirstIndex == index) resetSelection();
        else {
            if (selectedOperator == null) selectCard(index);
            else {
                try {
                    if (gameManager.performCalculation(selectedFirstIndex, index, selectedOperator)) {
                        // --- 修改开始 ---
                        // 计算成功后，将选中目标指向“结果”所在的卡片索引
                        selectedFirstIndex = index;
                        selectedOperator = null; // 清除选中的运算符
                        resetOpColors();         // 恢复运算符按钮颜色

                        refreshUI();             // 刷新界面，此时 refreshUI 会根据新的 selectedFirstIndex 涂绿
                        checkWin();
                        // --- 修改结束 ---
                    }
                } catch (ArithmeticException e) {
                    showCustomToast("除数不能为 0!");
                    resetSelection();
                }
            }
        }
    }


    private void checkWin() {
        if (gameManager != null && gameManager.checkWin()) {
            showCustomToast("计算正确!");
            gameManager.solvedCount++;
            if (gameTimer != null) gameTimer.stop();
            updateScoreBoard();
            new Handler().postDelayed(this::startNewGameLocal, 1200);
        }
    }

    private void selectCard(int index) {
        if (cardButtons == null) return;
        for(Button b : cardButtons) if (b != null) b.setBackgroundColor(Color.LTGRAY);
        selectedFirstIndex = index;
        if (index != -1 && cardButtons[index] != null) cardButtons[index].setBackgroundColor(Color.GREEN);
    }

    private void resetSelection() { selectCard(-1); selectedOperator = null; resetOpColors(); }

    private void updateScoreBoard() {
        if (tvScore == null || tvAvgTime == null || gameManager == null) return;
        tvScore.setText("已解: " + gameManager.solvedCount);
        long totalSeconds = (System.currentTimeMillis() - gameStartTime) / 1000;
        long avg = gameManager.solvedCount > 0 ? totalSeconds / gameManager.solvedCount : 0;
        tvAvgTime.setText("平均: " + avg + "s");
    }

// MainActivity.java 内部 getFreshSolution 方法重构

    private String getFreshSolution() {
        List<Fraction> currentNums = getCurrentNumbers();
        if (currentNums.isEmpty()) return null;

        // 1. 获取当前环境参数 (模数、进制、目标值)
        Integer modulus = null;
        int radix = 10;
        int targetValue = 24;
        Problem p = gameManager.getCurrentProblem();
        if (p != null) {
            modulus = p.modulus;
            if (p.radix != null) {
                radix = p.radix;
                targetValue = 2 * radix + 4;
            }
        }

        // 2. 【关键修复】：由 solve 改为 solveAll，获取该数字组合的所有可行解
        // 对于 4-5 个数字，Solver.solveAll 在移动端的性能通常在 100ms 以内
        List<String> allRawSolutions = Solver.solveAll(currentNums, modulus, targetValue);
        if (allRawSolutions.isEmpty()) return null;

        // 3. 构造模式后缀 (例如 " mod 13" 或 " base 12")
        // 这样可以让 Normalizer 识别出上下文，并返回与题库格式一致的规范化字符串
        String suffix = "";
        if (modulus != null) {
            suffix = " mod " + modulus;
        } else if (radix != 10) {
            suffix = " base " + radix;
        }

        List<String> candidates = new ArrayList<>();
        for (String s : allRawSolutions) {
            candidates.add(s + suffix);
        }

        // 4. 调用 SolutionNormalizer 进行去重和择优
        // 它会基于 AST 识别出 (3+1)*6 和 6*(1+3) 是同一个解，并保留最短的一个
        List<String> distinctSolutions = SolutionNormalizer.distinct(candidates);

        // 5. 最终排序：从过滤后的解中选出最优解（最短、字典序最小）
        Collections.sort(distinctSolutions, (s1, s2) -> {
            if (s1.length() != s2.length()) return Integer.compare(s1.length(), s2.length());
            return s1.compareTo(s2);
        });

        return distinctSolutions.get(0);
    }




    private List<Fraction> getCurrentNumbers() {
        List<Fraction> currentNums = new ArrayList<>();
        if (gameManager == null || gameManager.cardValues == null) return currentNums;
        for (Fraction f : gameManager.cardValues) if (f != null) currentNums.add(f);
        return currentNums;
    }

    private void initListeners() {
        // 1. 获取侧边栏视图
        NavigationView navView = findViewById(R.id.nav_view);
        if (navView != null) {
            // 2. 获取 HeaderView (索引通常为 0)
            View headerView = navView.getHeaderView(0);
            if (headerView != null) {
                // 3. 找到 Header 里的图标
                View logoIcon = headerView.findViewById(R.id.imageView);
                if (logoIcon != null) {
                    // 4. 设置长按监听器
                    logoIcon.setOnLongClickListener(v -> {
                        checkOverlayPermissionAndStart(); // 调用您之前的权限检查逻辑
                        // 开启后自动关闭侧边栏，体验更好
                        if (drawerLayout != null) {
                            drawerLayout.closeDrawer(GravityCompat.START);
                        }
                        return true;
                    });
                }
            }
        }
        if (tvTimer != null) {
            tvTimer.setOnLongClickListener(v -> {
                if (gameTimer != null) {
                    gameTimer.reset(); // 重置计时器内部时间
                    showCustomToast("计时已清零");
                }
                return true; // 返回 true 表示消费了长按事件，不触发短按
            });
        }
        // 侧边栏菜单
        if (btnMenu != null)
            btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        WebView wvMath = findViewById(R.id.wv_math_message);
        if (wvMath != null) {
            wvMath.setOnLongClickListener(v -> {
                SharedPreferences prefs = getSharedPreferences("AppConfig", MODE_PRIVATE);
                int mode = prefs.getInt("latex_long_press_mode", 0);

                if (mode == 2) return false; // 模式 2：保持原生，不拦截

                String textToCopy;
                if (mode == 0) {
                    // --- 核心优化：直接剥离 \text{...} 结构 ---
                    textToCopy = currentLatexCode;
                    if (textToCopy != null) {
                        // 使用正则表达式匹配 \text{内容} 并替换为 内容
                        // \\\\text\\{  -> 匹配 \text{
                        // ([^{}]*)      -> 捕获组：匹配不含大括号的内容（即最内层文本）
                        // \\}           -> 匹配结尾的 }
                        // 使用循环确保处理所有嵌套可能（虽然本应用中通常只有一层）
                        String lastResult;
                        do {
                            lastResult = textToCopy;
                            textToCopy = textToCopy.replaceAll("\\\\text\\{([^{}]*)\\}", "$1");
                        } while (!textToCopy.equals(lastResult));

                        textToCopy = textToCopy.trim();
                    }
                } else {
                    textToCopy = currentPlainTextForLatex;
                }

                if (textToCopy != null && !textToCopy.isEmpty()) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Hajimi24-LaTeX", textToCopy);
                    clipboard.setPrimaryClip(clip);
                    showCustomToast("已复制" + (mode == 0 ? " LaTeX 代码" : "计算式文本"));
                }
                return true; // 拦截事件，防止弹出原生菜单
            });
        }

        // 消息区域长按复制
        if (tvMessage != null) {
            tvMessage.setOnLongClickListener(v -> {
                String textToCopy = lastPlainTextSolution;
                if (textToCopy == null || textToCopy.isEmpty())
                    textToCopy = tvMessage.getText().toString();
                if (textToCopy.isEmpty()) return true;

                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Hajimi24-Result", textToCopy);
                clipboard.setPrimaryClip(clip);
                showCustomToast("已复制到剪贴板");
                return true;
            });
        }

        // 卡片按钮点击
        if (cardButtons != null) {
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                if (cardButtons[i] != null) cardButtons[i].setOnClickListener(v -> onCardClicked(idx));
            }
        }

        // 四则运算符号选择
        View.OnClickListener opListener = v -> {
            String op = (v.getId() == R.id.btn_op_add) ? "+" : (v.getId() == R.id.btn_op_sub) ? "-" : (v.getId() == R.id.btn_op_mul) ? "*" : "/";
            if (selectedFirstIndex == -1) return;
            resetOpColors();
            if (op.equals(selectedOperator)) {
                selectedOperator = null;
            } else {
                selectedOperator = op;
                v.setBackgroundColor(Color.BLUE);
            }
        };
        if (btnAdd != null) btnAdd.setOnClickListener(opListener);
        if (btnSub != null) btnSub.setOnClickListener(opListener);
        if (btnMul != null) btnMul.setOnClickListener(opListener);
        if (btnDiv != null) btnDiv.setOnClickListener(opListener);

        // 撤销、重做、重置、跳过
        if (btnUndo != null) btnUndo.setOnClickListener(v -> {
            if(gameManager.undo()) {
                refreshUI();
                resetSelection();
                // [新增逻辑]：若界面显示“无解”，撤销操作成功后文字消失
                if (tvMessage != null && "无解".equals(tvMessage.getText().toString())) {
                    tvMessage.setText("");
                    lastPlainTextSolution = "";
                }
            }
        });
        if (btnRedo != null) btnRedo.setOnClickListener(v -> {
            if(gameManager.redo()) {
                refreshUI();
                resetSelection();
                // [新增逻辑]：若界面显示“无解”，重做操作成功后文字消失
                if (tvMessage != null && "无解".equals(tvMessage.getText().toString())) {
                    tvMessage.setText("");
                    lastPlainTextSolution = "";
                }
            }
        });
        if (btnReset != null) btnReset.setOnClickListener(v -> {
            if (gameManager != null) gameManager.resetCurrentLevel();
            refreshUI();
            resetSelection();
            // [逻辑确认]：重置按钮会清空所有消息（包括“无解”），确保重置时消失
            if (tvMessage != null) tvMessage.setText("");
            lastPlainTextSolution = "";
            showCustomToast("已重置");
        });
        if (btnSkip != null) btnSkip.setOnClickListener(v -> startNewGameLocal());

        // 提示功能 (Hint/Try) - 核心修复：采用数值匹配方案
        if (btnTry != null) btnTry.setOnClickListener(v -> {
            String sol = getFreshSolution();
            if (sol == null) {
                updateDisplay("无解", null, false); // 使用统一方法清空公式并显示无解
                return;
            }

            // 获取当前题目的进制 (默认为 10)
            int currentRadix = 10;
            if (gameManager.getCurrentProblem() != null && gameManager.getCurrentProblem().radix != null) {
                currentRadix = gameManager.getCurrentProblem().radix;
            }

            // 1. 移除 mod 和 base 后缀，确保正则能匹配到括号
            String cleanSol = sol.replaceAll(" (mod|base) \\d+", "");

            // 2. 寻找最内层括号的表达式
            Pattern pattern = Pattern.compile("\\(([^()]+)\\)");
            Matcher matcher = pattern.matcher(cleanSol);
            String innerExpr = null;
            if (matcher.find()) innerExpr = matcher.group(1).trim();

            if (innerExpr == null) {
                if (tvMessage != null) tvMessage.setText("无法找到提示");
                return;
            }

            String[] parts = innerExpr.split(" ");
            if (parts.length < 3) return;

            try {
                // 3. 核心：将解法中的 Token 解析为数值对象进行比对，彻底避开 A 与 10 的字符串差异
                Fraction leftTarget = Fraction.parse(parts[0], currentRadix);
                Fraction rightTarget = Fraction.parse(parts[2], currentRadix);

                int firstButtonIndex = -1;
                int secondButtonIndex = -1;

                // 4. 遍历当前卡片，通过数值判断（实部、虚部、分母）寻找匹配按钮
                for (int i = 0; i < gameManager.currentNumberCount; i++) {
                    Fraction cardF = gameManager.cardValues[i];
                    if (cardF == null) continue;

                    if (firstButtonIndex == -1 &&
                            cardF.getRe() == leftTarget.getRe() &&
                            cardF.getDe() == leftTarget.getDe() &&
                            cardF.getIm() == leftTarget.getIm()) {
                        firstButtonIndex = i;
                    } else if (secondButtonIndex == -1 &&
                            cardF.getRe() == rightTarget.getRe() &&
                            cardF.getDe() == rightTarget.getDe() &&
                            cardF.getIm() == rightTarget.getIm()) {
                        secondButtonIndex = i;
                    }
                }

                if (firstButtonIndex != -1 && secondButtonIndex != -1) {
                    cardButtons[firstButtonIndex].setBackgroundColor(Color.rgb(255, 192, 203)); // 粉色高亮
                    cardButtons[secondButtonIndex].setBackgroundColor(Color.rgb(255, 192, 203));
                } else {
                    if (tvMessage != null) tvMessage.setText("提示匹配失败");
                }
            } catch (Exception e) {
                if (tvMessage != null) tvMessage.setText("解析提示步骤出错");
            }
        });

        // 结构提示功能
// 结构提示功能
        if (btnHintStruct != null) btnHintStruct.setOnClickListener(v -> {
            String sol = getFreshSolution();
            if (sol == null) {
                updateDisplay("无解", null, false);
                return;
            }
            // 如果已经在显示结构，则隐藏
            if ("struct".equals(lastShownType)) {
                updateDisplay("", null, false);
                lastShownType = "";
            } else {
                updateDisplay("结构: ", sol, true);
                lastShownType = "struct";
            }
        });

// 完整答案功能
        if (btnAnswer != null) btnAnswer.setOnClickListener(v -> {
            String sol = getFreshSolution();
            if (sol == null) {
                updateDisplay("无解", null, false);
                return;
            }
            // 如果已经在显示答案，则隐藏
            if ("answer".equals(lastShownType)) {
                updateDisplay("", null, false);
                lastShownType = "";
            } else {
                updateDisplay("答案: ", sol, false);
                lastShownType = "answer";
            }
        });


        // 题目分享
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> {
                String textToCopy = gameManager.getShareText();
                if (textToCopy != null && !textToCopy.isEmpty()) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Hajimi24 Question", textToCopy);
                    clipboard.setPrimaryClip(clip);
                    showCustomToast("题目已复制到剪贴板"); // 修改此处
                } else {
                    showCustomToast("当前没有可分享的题目"); // 修改此处
                }
            });
        }
    }



    private void resetOpColors() {
        if (btnAdd != null) btnAdd.setBackgroundColor(Color.LTGRAY); if (btnSub != null) btnSub.setBackgroundColor(Color.LTGRAY); if (btnMul != null) btnMul.setBackgroundColor(Color.LTGRAY); if (btnDiv != null) btnDiv.setBackgroundColor(Color.LTGRAY);
    }
    public void updateDisplay(String prefix, String rawSolution, boolean isStructure) {
        SharedPreferences prefs = getSharedPreferences("AppConfig", MODE_PRIVATE);
        boolean useLatex = prefs.getBoolean("use_latex_mode", false);

        // --- 读取显示配置 ---
        // 乘法模式 (0: times, 1: cdot, 2: omit)，默认为 1 (cdot)
        int mulMode = prefs.getInt("latex_mul_mode", 1);
        // 除法模式 (0: fraction line, 1: division symbol)，默认为 0 (分数线)
        int divMode = prefs.getInt("latex_div_mode", 0);
        // ------------------

        WebView wvMath = findViewById(R.id.wv_math_message);

        // 清空显示或无解情况
        if (prefix.isEmpty() || "无解".equals(prefix)) {
            wvMath.setVisibility(View.GONE);
            tvMessage.setVisibility(View.VISIBLE);
            tvMessage.setText(prefix);
            return;
        }

        if (useLatex) {
            tvMessage.setVisibility(View.GONE);

            // 1. 生成最终的 LaTeX 字符串
            String latexBody = ExpressionHelper.getAsLatex(rawSolution, getCurrentNumbers(), isStructure, mulMode, divMode);

            // 2. 【核心修改】判断生成的字符串中 \cfrac 的深度
            int depth = ExpressionHelper.getLatexHeight(latexBody);

            // 3. 传入 depth 进行渲染
            renderLatexInWebView(wvMath, latexBody, depth);

            // --- 新增：保存用于复制的文本 ---
            currentLatexCode = latexBody;
            currentPlainTextForLatex = isStructure ?
                    ExpressionHelper.getStructureAsPlainText(rawSolution, getCurrentNumbers()) :
                    ExpressionHelper.getAnswerAsPlainText(rawSolution, getCurrentNumbers());
            // ---------------------------

            renderLatexInWebView(wvMath, latexBody, depth);
        } else {
            // 普通模式逻辑保持不变
            wvMath.setVisibility(View.GONE);
            tvMessage.setVisibility(View.VISIBLE);
            tvMessage.setText(prefix);
            if (isStructure) {
                tvMessage.append(ExpressionHelper.formatStructure(rawSolution, getCurrentNumbers()));
            } else {
                tvMessage.append(ExpressionHelper.formatAnswer(rawSolution, getCurrentNumbers()));
            }
        }
    }

}
