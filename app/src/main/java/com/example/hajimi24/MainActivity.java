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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private TextView tvScore, tvTimer, tvAvgTime, tvMessage; // 新增 tvMessage
    private Button[] cardButtons = new Button[5];
    private Button btnAdd, btnSub, btnMul, btnDiv;
    private Button btnUndo, btnReset, btnRedo, btnMenu;
    private Button btnTry, btnHintStruct, btnAnswer, btnShare, btnSkip;

    // 逻辑组件
    private GameManager gameManager;
    private ProblemRepository repository;
    private GameTimer gameTimer;
    private SidebarLogic sidebarLogic;

    // 状态
    private long gameStartTime;
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
        initHelpers();
        initListeners();

        gameStartTime = System.currentTimeMillis();
        loadFirstAvailableFile();
    }

    private void initHelpers() {
        NavigationView navView = findViewById(R.id.nav_view);
        sidebarLogic = new SidebarLogic(this, drawerLayout, navView, repository, new SidebarLogic.ActionCallback() {
            @Override
            public void onRandomMode(int count) {
                switchToRandomMode(count);
            }

            @Override
            public void onLoadFile(String fileName) {
                loadProblemSet(fileName);
            }
        });
        sidebarLogic.setup();

        gameTimer = new GameTimer(() -> {
            tvTimer.setText(gameTimer.getElapsedSeconds() + "s");
            updateScoreBoard();
        });
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        btnMenu = findViewById(R.id.btn_menu);
        tvScore = findViewById(R.id.tv_score);
        tvTimer = findViewById(R.id.tv_timer);
        tvAvgTime = findViewById(R.id.tv_avg_time);

        // 新增：绑定消息显示区域
        tvMessage = findViewById(R.id.tv_message_area);

        cardButtons[0] = findViewById(R.id.card_1);
        cardButtons[1] = findViewById(R.id.card_2);
        cardButtons[2] = findViewById(R.id.card_3);
        cardButtons[3] = findViewById(R.id.card_4);
        cardButtons[4] = findViewById(R.id.card_5);

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

    private void startNewGameLocal() {
        gameManager.startNewGame(currentFileName.startsWith("随机"));
        gameTimer.start();
        resetSelection();
        refreshUI();
        tvMessage.setText(""); // 清空提示区域
    }

    private void refreshUI() {
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
                cardButtons[i].setBackgroundColor(Color.parseColor("#CCCCCC")); // 重置颜色，避免保留粉色
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
                        refreshUI();
                        selectCard(index);
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
            gameTimer.stop();
            updateScoreBoard();
            new Handler().postDelayed(() -> {
                startNewGameLocal();
            }, 1200);
        }
    }

    private void selectCard(int index) {
        for(Button b : cardButtons) {
            // 保持粉色提示（如果已经变粉），否则灰色
            // 简化逻辑：选中时覆盖一切颜色为绿色/灰色，点击重置或刷新UI时恢复
            b.setBackgroundColor(Color.LTGRAY);
        }
        selectedFirstIndex = index;
        if (index != -1) cardButtons[index].setBackgroundColor(Color.GREEN);
    }

    private void resetSelection() {
        selectCard(-1);
        selectedOperator = null;
        resetOpColors();
    }

    private void updateScoreBoard() {
        tvScore.setText("已解: " + gameManager.solvedCount);
        long totalSeconds = (System.currentTimeMillis() - gameStartTime) / 1000;
        long avg = gameManager.solvedCount > 0 ? totalSeconds / gameManager.solvedCount : 0;
        tvAvgTime.setText("平均: " + avg + "s");
    }

    private void initListeners() {
        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            cardButtons[i].setOnClickListener(v -> onCardClicked(idx));
        }

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

        btnUndo.setOnClickListener(v -> { if(gameManager.undo()) { refreshUI(); resetSelection(); } });
        btnRedo.setOnClickListener(v -> { if(gameManager.redo()) { refreshUI(); resetSelection(); } });
        btnReset.setOnClickListener(v -> {
            gameManager.resetCurrentLevel();
            refreshUI();
            resetSelection();
            tvMessage.setText("");
            Toast.makeText(this, "已重置", Toast.LENGTH_SHORT).show();
        });

        btnSkip.setOnClickListener(v -> startNewGameLocal());

        // --- 核心修改逻辑 ---

        // 1. 尝试：标粉色
        btnTry.setOnClickListener(v -> {
            String sol = gameManager.getOrCalculateSolution();
            if (sol == null) {
                tvMessage.setText("无解");
                return;
            }
            // 正则匹配第一个 "数字 运算 数字" 结构
            Matcher m = Pattern.compile("(\\d+)\\s*[+\\-*/]\\s*(\\d+)").matcher(sol);
            if (m.find()) {
                String n1 = m.group(1);
                String n2 = m.group(2); // 注意 group(2) 是第二个数字，group(1) 是第一个数字
                // 这里的正则可能需调整：(\\d+)\\s*([+\\-*/])\\s*(\\d+) -> 1=num, 2=op, 3=num
                // 修正 Pattern
                m = Pattern.compile("(\\d+)\\s*[+\\-*/]\\s*(\\d+)").matcher(sol);
                if(m.find()) {
                    // Java Regex group 索引取决于括号。这里没有括号捕获 op，所以 1=num1, 2=num2
                    // 稍等，为了稳妥，用带 op 的正则
                }
            }

            // 重新编写更稳健的逻辑
            Matcher mComplete = Pattern.compile("(\\d+)\\s*([+\\-*/])\\s*(\\d+)").matcher(sol);
            if (mComplete.find()) {
                String n1 = mComplete.group(1);
                String n2 = mComplete.group(3);

                boolean[] used = new boolean[5];
                int idx1 = -1, idx2 = -1;

                // 查找匹配的卡片索引
                for (int i = 0; i < 5; i++) {
                    if (gameManager.cardValues[i] != null && !used[i] && gameManager.cardValues[i].toString().equals(n1)) {
                        idx1 = i; used[i] = true; break;
                    }
                }
                for (int i = 0; i < 5; i++) {
                    if (gameManager.cardValues[i] != null && !used[i] && gameManager.cardValues[i].toString().equals(n2)) {
                        idx2 = i; used[i] = true; break;
                    }
                }

                if (idx1 != -1 && idx2 != -1) {
                    cardButtons[idx1].setBackgroundColor(Color.rgb(255, 192, 203)); // Pink
                    cardButtons[idx2].setBackgroundColor(Color.rgb(255, 192, 203)); // Pink
                } else {
                    Toast.makeText(this, "请参考答案", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 2. 结构：显示 🐱
        btnHintStruct.setOnClickListener(v -> {
            String sol = gameManager.getOrCalculateSolution();
            if (sol != null) {
                String struct = sol.replaceAll("\\d+", "🐱");
                tvMessage.setText("结构: " + struct);
            } else {
                tvMessage.setText("无解");
            }
        });

        // 3. 答案：显示在下方文本框
        btnAnswer.setOnClickListener(v -> {
            String sol = gameManager.getOrCalculateSolution();
            tvMessage.setText("答案: " + (sol != null ? sol : "无解"));
        });

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
