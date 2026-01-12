package com.example.hajimi24;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView tvScore, tvTimer, tvAvgTime;
    private TextView tvStatus;
    private Button[] cardButtons = new Button[5];
    private Button btnAdd, btnSub, btnMul, btnDiv;
    private Button btnUndo, btnReset, btnRedo, btnMenu;
    private Button btnTry, btnHintStruct, btnAnswer, btnShare, btnSkip;

    // 核心组件
    private GameManager gameManager;
    private ProblemRepository repository;

    // UI 状态
    private long startTime, gameStartTime;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private int selectedFirstIndex = -1;
    private String selectedOperator = null;
    private String currentFileName = "随机(4数)";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repository = new ProblemRepository(this);
        gameManager = new GameManager();

        initViews();
        initSidebar();
        initListeners();

        gameStartTime = System.currentTimeMillis();
        loadFirstAvailableFile(); // 初始加载逻辑稍作调整调用 Repository
        startTimer();
    }

    // --- 初始化 UI ---
    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        btnMenu = findViewById(R.id.btn_menu);
        tvScore = findViewById(R.id.tv_score);
        tvTimer = findViewById(R.id.tv_timer);
        tvAvgTime = findViewById(R.id.tv_avg_time);
        tvStatus = findViewById(R.id.tv_status);

        cardButtons[0] = findViewById(R.id.card_1);
        cardButtons[1] = findViewById(R.id.card_2);
        cardButtons[2] = findViewById(R.id.card_3);
        cardButtons[3] = findViewById(R.id.card_4);
        cardButtons[4] = findViewById(R.id.card_5);
        // ... 其他按钮 findViewById (省略部分重复代码) ...
        // 请保留原有的所有 findViewById 代码
        btnAdd = findViewById(R.id.btn_op_add);
        btnSub = findViewById(R.id.btn_op_sub);
        btnMul = findViewById(R.id.btn_op_mul);
        btnDiv = findViewById(R.id.btn_op_div);

        btnUndo = findViewById(R.id.btn_undo);
        btnReset = findViewById(R.id.btn_reset);
        btnRedo = findViewById(R.id.btn_redo);

        btnTry = findViewById(R.id.btn_try);
        btnHintStruct = findViewById(R.id.btn_hint_struct);
        btnAnswer = findViewById(R.id.btn_answer);
        btnShare = findViewById(R.id.btn_share);
        btnSkip = findViewById(R.id.btn_skip);
    }

    // --- 逻辑与 UI 的桥梁 ---

    private void refreshUI() {
        // 更新卡片显示
        if (gameManager.currentNumberCount == 4) {
            cardButtons[4].setVisibility(View.GONE);
        } else {
            cardButtons[4].setVisibility(View.VISIBLE);
        }
        for (int i = 0; i < 5; i++) {
            if (gameManager.currentNumberCount == 4 && i == 4) continue;
            if (gameManager.cardValues[i] != null) {
                cardButtons[i].setVisibility(View.VISIBLE);
                cardButtons[i].setText(gameManager.cardValues[i].toString());
            } else {
                cardButtons[i].setVisibility(View.INVISIBLE);
            }
        }
        updateScoreBoard();
    }

    private void onCardClicked(int index) {
        if (selectedFirstIndex == -1) {
            selectCard(index);
        } else if (selectedFirstIndex == index) {
            resetSelection();
        } else {
            if (selectedOperator == null) {
                selectCard(index);
            } else {
                try {
                    boolean success = gameManager.performCalculation(selectedFirstIndex, index, selectedOperator);
                    if (success) {
                        resetSelection();
                        refreshUI(); // 刷新数据
                        selectCard(index); // 选中结果
                        checkWin();
                    }
                } catch (ArithmeticException e) {
                    Toast.makeText(this, "除数不能为0", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void checkWin() {
        if (gameManager.checkWin()) {
            Toast.makeText(this, "成功！", Toast.LENGTH_SHORT).show();
            gameManager.solvedCount++;
            updateScoreBoard();
            new Handler().postDelayed(() -> {
                gameManager.startNewGame(currentFileName.startsWith("随机"));
                resetSelection();
                startTime = System.currentTimeMillis();
                refreshUI();
            }, 1200);
        }
    }

    private void startNewGameLocal() {
        gameManager.startNewGame(currentFileName.startsWith("随机"));
        startTime = System.currentTimeMillis();
        resetSelection();
        refreshUI();
    }

    // --- 侧边栏与数据加载 ---
    private void initSidebar() {
        Menu menu = navigationView.getMenu();
        menu.clear();
        menu.add(Menu.NONE, 888, Menu.NONE, "📖 游戏说明书");
        menu.add(Menu.NONE, 999, Menu.NONE, "☁️ 从 GitHub 更新题库");
        menu.add(Menu.NONE, 0, Menu.NONE, "🎲 随机 (4数)");
        menu.add(Menu.NONE, 1, Menu.NONE, "🎲 随机 (5数)");

        List<String> files = repository.getAvailableFiles();
        int id = 2;
        for (String f : files) menu.add(Menu.NONE, id++, Menu.NONE, "📄 " + f);

        navigationView.setNavigationItemSelectedListener(item -> {
            String t = item.getTitle().toString();
            if (t.contains("游戏说明书")) {
                showHelpDialog();
            } else if (t.contains("从 GitHub 更新")) {
                syncFromGitHub();
            } else {
                if (t.contains("随机 (4数)")) switchToRandomMode(4);
                else if (t.contains("随机 (5数)")) switchToRandomMode(5);
                else loadProblemSet(t.substring(t.indexOf(" ") + 1));
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return true;
        });
    }

    // ... 在 MainActivity 类中 ...

    private void syncFromGitHub() {
        // 1. 获取菜单项引用 (ID 999 对应之前的 "从 GitHub 更新题库")
        Menu menu = navigationView.getMenu();
        MenuItem updateItem = menu.findItem(999);

        // 2. 更改状态为“连接中”
        if (updateItem != null) {
            updateItem.setTitle("⏳ 正在连接 GitHub...");
            // 如果希望菜单保持打开状态看进度，通常不需要做额外操作，
            // 但如果用户误触关闭了抽屉，进度仍在后台继续。
        }

        repository.syncFromGitHub(new ProblemRepository.SyncCallback() {
            @Override
            public void onProgress(String fileName, int current, int total) {
                runOnUiThread(() -> {
                    // 3. 实时更新菜单文字
                    if (updateItem != null) {
                        updateItem.setTitle("⬇️ 下载中: " + current + "/" + total);
                    }
                });
            }

            @Override
            public void onSuccess(int count) {
                runOnUiThread(() -> {
                    // 4. 完成后恢复文字或显示结果
                    if (updateItem != null) {
                        updateItem.setTitle("✅ 更新完成 (" + count + ")");
                        // 2秒后恢复成原始文字
                        new Handler().postDelayed(() ->
                                updateItem.setTitle("☁️ 从 GitHub 更新题库"), 2000);
                    }
                    Toast.makeText(MainActivity.this, "更新完成！共下载 " + count + " 个文件", Toast.LENGTH_LONG).show();
                    initSidebar(); // 刷新文件列表
                });
            }

            @Override
            public void onFail(String error) {
                runOnUiThread(() -> {
                    if (updateItem != null) {
                        updateItem.setTitle("❌ 更新失败，点击重试");
                    }
                    Toast.makeText(MainActivity.this, "错误: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }


    private void loadProblemSet(String fileName) {
        try {
            List<Problem> problems = repository.loadProblemSet(fileName);
            gameManager.setProblemSet(problems);
            currentFileName = fileName.replace(".txt", "");
            btnMenu.setText("☰ 模式: " + currentFileName);
            Toast.makeText(this, "加载成功", Toast.LENGTH_SHORT).show();
            startNewGameLocal();
        } catch (Exception e) {
            e.printStackTrace();
            switchToRandomMode(4);
        }
    }

    private void loadFirstAvailableFile() {
        List<String> files = repository.getAvailableFiles();
        if (!files.isEmpty()) {
            loadProblemSet(files.get(0));
        } else {
            switchToRandomMode(4);
        }
    }

    private void switchToRandomMode(int count) {
        gameManager.currentNumberCount = count;
        currentFileName = "随机(" + count + "数)";
        btnMenu.setText("☰ 模式: " + currentFileName);
        startNewGameLocal();
    }

    // --- 其他 UI 辅助方法 ---
    private void showHelpDialog() {
        CharSequence helpContent = MarkdownUtils.loadMarkdownFromAssets(this, "help.md");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("游戏指南")
                .setMessage(helpContent)
                .setPositiveButton("开始挑战", null)
                .create();
        dialog.show();
        TextView msgView = dialog.findViewById(android.R.id.message);
        if (msgView != null) {
            msgView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            msgView.setLinkTextColor(Color.BLUE);
        }
    }

    private void selectCard(int index) {
        for(Button b : cardButtons) b.setBackgroundColor(Color.LTGRAY);
        selectedFirstIndex = index;
        if (index != -1) cardButtons[index].setBackgroundColor(Color.GREEN);
    }

    private void resetSelection() {
        selectCard(-1);
        selectedOperator = null;
        btnAdd.setBackgroundColor(Color.LTGRAY);
        btnSub.setBackgroundColor(Color.LTGRAY);
        btnMul.setBackgroundColor(Color.LTGRAY);
        btnDiv.setBackgroundColor(Color.LTGRAY);
    }

    private void updateScoreBoard() {
        tvScore.setText("已解: " + gameManager.solvedCount);
        long totalSeconds = (System.currentTimeMillis() - gameStartTime) / 1000;
        long avg = gameManager.solvedCount > 0 ? totalSeconds / gameManager.solvedCount : 0;
        tvAvgTime.setText("平均: " + avg + "s");
    }

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long levelSeconds = (now - startTime) / 1000;
                tvTimer.setText(levelSeconds + "s");
                updateScoreBoard(); // 复用 updateScoreBoard 里的平均时间计算
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    // --- 监听器绑定 (简化版) ---
    private void initListeners() {
        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            cardButtons[i].setOnClickListener(v -> onCardClicked(idx));
        }

        // 运算符
        View.OnClickListener opListener = v -> {
            String op = "+";
            if (v == btnSub) op = "-";
            else if (v == btnMul) op = "*";
            else if (v == btnDiv) op = "/";

            if (selectedFirstIndex == -1) return;
            resetOpColors();
            if (op.equals(selectedOperator)) selectedOperator = null;
            else {
                selectedOperator = op;
                v.setBackgroundColor(Color.BLUE);
            }
        };
        btnAdd.setOnClickListener(opListener);
        btnSub.setOnClickListener(opListener);
        btnMul.setOnClickListener(opListener);
        btnDiv.setOnClickListener(opListener);

        // 功能按钮
        btnUndo.setOnClickListener(v -> { if(gameManager.undo()) { refreshUI(); resetSelection(); } });
        btnRedo.setOnClickListener(v -> { if(gameManager.redo()) { refreshUI(); resetSelection(); } });
        btnReset.setOnClickListener(v -> { gameManager.resetCurrentLevel(); refreshUI(); resetSelection(); Toast.makeText(this, "已重置", Toast.LENGTH_SHORT).show(); });

        btnSkip.setOnClickListener(v -> startNewGameLocal());
        btnAnswer.setOnClickListener(v -> {
            String sol = gameManager.getOrCalculateSolution();
            new AlertDialog.Builder(this).setTitle("答案").setMessage(sol!=null?sol:"无解").setPositiveButton("OK", null).show();
        });

        // Share, Try, Hint 等可参考上面的模式，从 GameManager 获取数据后显示
        btnShare.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder("24点挑战:\n");
            for (Fraction f : gameManager.cardValues) if (f!=null) sb.append("🐈").append(f).append("\n");
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("24Game", sb.toString()));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        });
    }

    private void resetOpColors() {
        btnAdd.setBackgroundColor(Color.LTGRAY);
        btnSub.setBackgroundColor(Color.LTGRAY);
        btnMul.setBackgroundColor(Color.LTGRAY);
        btnDiv.setBackgroundColor(Color.LTGRAY);
    }
}
