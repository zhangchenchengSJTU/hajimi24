package com.example.hajimi24;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spanned;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private String currentLoadedFile = null;
    private DrawerLayout drawerLayout;
    private TextView tvScore, tvTimer, tvAvgTime, tvMessage;
    private Button[] cardButtons = new Button[5];
    private Button btnAdd, btnSub, btnMul, btnDiv;
    private Button btnUndo, btnReset, btnRedo, btnMenu;
    private Button btnTry, btnHintStruct, btnAnswer, btnShare, btnSkip;
    private GameManager gameManager;
    private ProblemRepository repository;
    private GameTimer gameTimer;
    private SidebarLogic sidebarLogic;
    private long gameStartTime;
    private int selectedFirstIndex = -1;
    private String selectedOperator = null;
    private String currentFileName = "随机(4数)";
    private String lastPlainTextSolution = "";

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
        switchToRandomMode(4);
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
            // [核心修复] 实现新的回调方法
            @Override
            public void onSettingsChanged() {
                // 如果当前是文件模式，则重新加载该文件
                if (currentFileName != null && !currentFileName.startsWith("随机")) {
                    loadProblemSet(currentFileName + ".txt");
                }
            }

        });
        sidebarLogic.setup();
        gameTimer = new GameTimer(() -> {
            if (tvTimer != null) tvTimer.setText(gameTimer.getElapsedSeconds() + "s");
            updateScoreBoard();
        });
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
    }

    public void loadProblemSet(String fileName) {
        try {
            List<Problem> problems = repository.loadProblemSet(fileName, sidebarLogic.getGameModeSettings());
            gameManager.setProblemSet(problems);
            currentFileName = fileName.replace(".txt", "");
            btnMenu.setText("☰ 模式: " + currentFileName);
            Toast.makeText(this, "加载成功", Toast.LENGTH_SHORT).show();
            startNewGameLocal();
        } catch (Exception e) { e.printStackTrace(); switchToRandomMode(4); }
    }

    public void switchToRandomMode(int count) {
        gameManager.currentNumberCount = count;
        currentFileName = "随机(" + count + "数)";
        btnMenu.setText("☰ 模式: " + currentFileName);
        startNewGameLocal();
    }

    private void startNewGameLocal() {
        GameModeSettings settings = sidebarLogic.getGameModeSettings();
        // TODO: 下一步将 settings 传给 GameManager
        gameManager.startNewGame(currentFileName.startsWith("随机")/*, settings*/);
        if (gameTimer != null) gameTimer.start();
        resetSelection();
        refreshUI();
        if (tvMessage != null) tvMessage.setText("");
        lastPlainTextSolution = "";
    }

    private void refreshUI() {
        if (gameManager == null || cardButtons == null) return;
        if (gameManager.currentNumberCount == 4) cardButtons[4].setVisibility(View.GONE); else cardButtons[4].setVisibility(View.VISIBLE);
        for (int i = 0; i < 5; i++) {
            if (cardButtons[i] == null) continue;
            if (gameManager.currentNumberCount == 4 && i == 4) continue;
            if (gameManager.cardValues[i] != null) {
                cardButtons[i].setVisibility(View.VISIBLE);
                cardButtons[i].setText(gameManager.cardValues[i].toString());
                cardButtons[i].setBackgroundColor(Color.parseColor("#CCCCCC"));
            } else { cardButtons[i].setVisibility(View.INVISIBLE); }
        }
        updateScoreBoard();
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
                        resetSelection(); refreshUI(); selectCard(index); checkWin();
                    }
                } catch (ArithmeticException e) { Toast.makeText(this, "除数不能为0", Toast.LENGTH_SHORT).show(); }
            }
        }
    }

    private void checkWin() {
        if (gameManager != null && gameManager.checkWin()) {
            Toast.makeText(this, "成功！", Toast.LENGTH_SHORT).show();
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

    private String getFreshSolution() {
        List<Fraction> currentNums = getCurrentNumbers();
        return (currentNums.isEmpty()) ? null : Solver.solve(currentNums);
    }

    private List<Fraction> getCurrentNumbers() {
        List<Fraction> currentNums = new ArrayList<>();
        if (gameManager == null || gameManager.cardValues == null) return currentNums;
        for (Fraction f : gameManager.cardValues) if (f != null) currentNums.add(f);
        return currentNums;
    }

    private void initListeners() {
        if (btnMenu != null) btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        if (tvMessage != null) {
            tvMessage.setOnLongClickListener(v -> {
                String textToCopy = lastPlainTextSolution;
                if (textToCopy == null || textToCopy.isEmpty()) textToCopy = tvMessage.getText().toString();
                if (textToCopy.isEmpty()) return true;

                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Hajimi24-Result", textToCopy);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        if (cardButtons != null) for (int i = 0; i < 5; i++) {
            final int idx = i;
            if (cardButtons[i] != null) cardButtons[i].setOnClickListener(v -> onCardClicked(idx));
        }

        View.OnClickListener opListener = v -> {
            String op = (v.getId() == R.id.btn_op_add) ? "+" : (v.getId() == R.id.btn_op_sub) ? "-" : (v.getId() == R.id.btn_op_mul) ? "*" : "/";
            if (selectedFirstIndex == -1) return; resetOpColors();
            if (op.equals(selectedOperator)) selectedOperator = null;
            else { selectedOperator = op; v.setBackgroundColor(Color.BLUE); }
        };
        if (btnAdd != null) btnAdd.setOnClickListener(opListener); if (btnSub != null) btnSub.setOnClickListener(opListener); if (btnMul != null) btnMul.setOnClickListener(opListener); if (btnDiv != null) btnDiv.setOnClickListener(opListener);
        if (btnUndo != null) btnUndo.setOnClickListener(v -> { if(gameManager.undo()) { refreshUI(); resetSelection(); } });
        if (btnRedo != null) btnRedo.setOnClickListener(v -> { if(gameManager.redo()) { refreshUI(); resetSelection(); } });
        if (btnReset != null) btnReset.setOnClickListener(v -> { if (gameManager != null) gameManager.resetCurrentLevel(); refreshUI(); resetSelection(); if (tvMessage != null) tvMessage.setText(""); Toast.makeText(this, "已重置", Toast.LENGTH_SHORT).show(); });
        if (btnSkip != null) btnSkip.setOnClickListener(v -> startNewGameLocal());

        // --- 核心修复：btnTry 的监听器 ---
        if (btnTry != null) btnTry.setOnClickListener(v -> {
            String sol = getFreshSolution();
            if (sol == null) {
                if (tvMessage != null) tvMessage.setText("无解");
                return;
            }

            // 1. 找到最内层的括号表达式
            Pattern pattern = Pattern.compile("\\(([^()]+)\\)");
            Matcher matcher = pattern.matcher(sol);
            String innerExpr = null;
            if (matcher.find()) {
                innerExpr = matcher.group(1).trim();
            }

            if (innerExpr == null) {
                if (tvMessage != null) tvMessage.setText("无法找到提示");
                return;
            }

            // 2. 解析表达式，找到左右数字
            String[] parts = innerExpr.split(" ");
            if (parts.length < 3) {
                if (tvMessage != null) tvMessage.setText("无法解析提示");
                return;
            }
            String leftNum = parts[0];
            String rightNum = parts[2];

            // 3. 查找并高亮对应的按钮
            int firstButtonIndex = -1;
            int secondButtonIndex = -1;

            for (int i = 0; i < gameManager.currentNumberCount; i++) {
                if (cardButtons[i].getText().toString().equals(leftNum)) {
                    firstButtonIndex = i;
                    break;
                }
            }

            for (int i = 0; i < gameManager.currentNumberCount; i++) {
                // 确保不与第一个按钮重复
                if (i != firstButtonIndex && cardButtons[i].getText().toString().equals(rightNum)) {
                    secondButtonIndex = i;
                    break;
                }
            }

            if (firstButtonIndex != -1 && secondButtonIndex != -1) {
                cardButtons[firstButtonIndex].setBackgroundColor(Color.rgb(255, 192, 203)); // Pink
                cardButtons[secondButtonIndex].setBackgroundColor(Color.rgb(255, 192, 203)); // Pink
            } else {
                if (tvMessage != null) tvMessage.setText("提示步骤匹配失败");
            }
        });

        if (btnHintStruct != null) btnHintStruct.setOnClickListener(v -> {
            String sol = getFreshSolution();
            if (sol != null) {
                if (tvMessage != null) {
                    tvMessage.setText("结构: ");
                    tvMessage.append(ExpressionHelper.formatStructure(sol, getCurrentNumbers()));
                    lastPlainTextSolution = ExpressionHelper.getStructureAsPlainText(sol, getCurrentNumbers());
                }
            } else {
                if (tvMessage != null) tvMessage.setText("无解");
                lastPlainTextSolution = "无解";
            }
        });

        if (btnAnswer != null) btnAnswer.setOnClickListener(v -> {
            String sol = getFreshSolution();
            if (sol != null) {
                if (tvMessage != null) {
                    tvMessage.setText("答案: ");
                    tvMessage.append(ExpressionHelper.formatAnswer(sol, getCurrentNumbers()));
                    lastPlainTextSolution = ExpressionHelper.getAnswerAsPlainText(sol, getCurrentNumbers());
                }
            } else {
                if (tvMessage != null) tvMessage.setText("无解");
                lastPlainTextSolution = "无解";
            }
        });

        if (btnShare != null) btnShare.setOnClickListener(v -> {/*...Share 逻辑...*/});
    }

    private void resetOpColors() {
        if (btnAdd != null) btnAdd.setBackgroundColor(Color.LTGRAY); if (btnSub != null) btnSub.setBackgroundColor(Color.LTGRAY); if (btnMul != null) btnMul.setBackgroundColor(Color.LTGRAY); if (btnDiv != null) btnDiv.setBackgroundColor(Color.LTGRAY);
    }
}
