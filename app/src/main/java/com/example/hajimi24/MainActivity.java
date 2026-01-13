package com.example.hajimi24;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;

import android.text.Spanned;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;
import java.util.Arrays;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

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

        try {
            List<String> files = repository.getAvailableFiles();
            if (files != null && !files.isEmpty()) {
                gameManager.setProblemSet(repository.loadProblemSet(files.get(0)));
            }
        } catch (Exception e) { e.printStackTrace(); }

        initViews();
        initHelpers();
        initListeners();

        gameStartTime = System.currentTimeMillis();
        switchToRandomMode(4);
    }

    private void initHelpers() {
        NavigationView navView = findViewById(R.id.nav_view);
        sidebarLogic = new SidebarLogic(this, drawerLayout, navView, repository, new SidebarLogic.ActionCallback() {
            @Override public void onRandomMode(int count) { switchToRandomMode(count); }
            @Override public void onLoadFile(String fileName) { loadProblemSet(fileName); }
            @Override public void onShowInstructions() { Toast.makeText(MainActivity.this, "显示说明书...", Toast.LENGTH_SHORT).show(); }
            @Override public void onSyncFromGithub() {
                Toast.makeText(MainActivity.this, "开始从 Github 同步...", Toast.LENGTH_LONG).show();
                repository.syncFromGitHub(new ProblemRepository.SyncCallback() {
                    @Override public void onProgress(String fileName, int current, int total) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "下载: " + fileName, Toast.LENGTH_SHORT).show());}
                    @Override public void onSuccess(int count) { runOnUiThread(() -> { Toast.makeText(MainActivity.this, "更新成功: " + count + "个文件", Toast.LENGTH_LONG).show(); sidebarLogic.setup(); });}
                    @Override public void onFail(String error) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "更新失败: " + error, Toast.LENGTH_LONG).show());}
                });
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
            List<Problem> problems = repository.loadProblemSet(fileName);
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
        gameManager.startNewGame(settings);
        if (gameTimer != null) gameTimer.start();
        resetSelection();
        refreshUI();
        if (tvMessage != null) tvMessage.setText("");
        lastPlainTextSolution = "";
    }

    public boolean isRandomModeActive() {
        return currentFileName.startsWith("随机");
    }

    private void refreshUI() {
        if (gameManager == null || cardButtons == null) return;
        if (gameManager.currentNumberCount == 0) {
            for(Button b : cardButtons) if (b != null) b.setVisibility(View.INVISIBLE);
            if (tvMessage != null) tvMessage.setText(gameManager.currentLevelSolution);
            return;
        }
        if (gameManager.currentNumberCount == 4) cardButtons[4].setVisibility(View.GONE); else cardButtons[4].setVisibility(View.VISIBLE);
        for (int i = 0; i < 5; i++) {
            if (cardButtons[i] == null) continue;
            if (gameManager.currentNumberCount <= i) { cardButtons[i].setVisibility(View.INVISIBLE); continue; }
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

    private String getFreshSolution() { return gameManager.currentLevelSolution; }

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

        // --- 修复后的 btnTry 监听器 ---
        if (btnTry != null) btnTry.setOnClickListener(v -> {
            String sol = getFreshSolution();
            if (sol == null || sol.contains("无符合")) {
                if (tvMessage != null) tvMessage.setText("无解");
                return;
            }

            // 1. 提取最内层括号内的表达式 (即当前运算步骤)
            // 例如 "((1+2i)+5)" -> "1+2i+5"
            // 这种提取方式不依赖运算符，纯粹基于括号结构，对 Gauss 整数很安全
            Pattern pattern = Pattern.compile("\\(([^()]+)\\)");
            Matcher matcher = pattern.matcher(sol);
            String innerExpr = null;
            if (matcher.find()) {
                innerExpr = matcher.group(1).trim();
            } else {
                innerExpr = sol; // 简单步骤可能无括号
            }

            if (innerExpr == null || innerExpr.isEmpty()) {
                if (tvMessage != null) tvMessage.setText("无法获取提示");
                return;
            }

            // 2. 智能匹配：按长度降序寻找匹配的卡片
            // 关键：优先匹配 "1+2i" 这样较长的串，防止被误判为 "1"
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < gameManager.currentNumberCount; i++) {
                if (cardButtons[i] != null) indices.add(i);
            }
            Collections.sort(indices, (i1, i2) -> {
                String s1 = cardButtons[i1].getText().toString();
                String s2 = cardButtons[i2].getText().toString();
                return s2.length() - s1.length();
            });

            int idx1 = -1, idx2 = -1;
            int start1 = -1, end1 = -1;
            int start2 = -1, end2 = -1;

            // 查找第一个数在字符串中的位置
            for (int i : indices) {
                String val = cardButtons[i].getText().toString();
                // 使用 indexOf 字面量查找，完全忽略 "+" 是否为正则特殊字符的问题
                int p = innerExpr.indexOf(val);
                if (p != -1) {
                    idx1 = i;
                    start1 = p;
                    end1 = p + val.length();
                    break;
                }
            }

            // 查找第二个数 (查找位置不能与第一个数重叠)
            if (idx1 != -1) {
                for (int i : indices) {
                    if (i == idx1) continue;
                    String val = cardButtons[i].getText().toString();

                    int p = -1;
                    int searchIndex = 0;
                    // 循环查找，直到找到一个不重叠的位置
                    while (searchIndex < innerExpr.length()) {
                        int found = innerExpr.indexOf(val, searchIndex);
                        if (found == -1) break;

                        // 检查是否与第一个找到的区间 [start1, end1) 重叠
                        int foundEnd = found + val.length();
                        if (foundEnd <= start1 || found >= end1) {
                            p = found;
                            break;
                        }
                        searchIndex = found + 1;
                    }

                    if (p != -1) {
                        idx2 = i;
                        start2 = p;
                        end2 = p + val.length();
                        break;
                    }
                }
            }

            if (idx1 != -1 && idx2 != -1) {
                // 3. 高亮按钮 (粉色)
                for(Button b : cardButtons) if (b != null) b.setBackgroundColor(Color.LTGRAY);
                cardButtons[idx1].setBackgroundColor(Color.rgb(255, 192, 203));
                cardButtons[idx2].setBackgroundColor(Color.rgb(255, 192, 203));

                // 4. 构建带绿色运算符的 HTML 文本
                // 确定两个数字在字符串中的先后顺序
                int firstEnd, secondStart;
                if (start1 < start2) {
                    firstEnd = end1;
                    secondStart = start2;
                } else {
                    firstEnd = end2;
                    secondStart = start1;
                }

                // 截取三段：左边数字、中间运算符、右边数字
                String leftPart = innerExpr.substring(0, firstEnd);
                String operatorPart = innerExpr.substring(firstEnd, secondStart);
                String rightPart = innerExpr.substring(secondStart);

                // 将中间的运算符部分染成绿色 (#228B22)
                String html = leftPart + "<font color='#228B22'>" + operatorPart + "</font>" + rightPart;

                if (tvMessage != null) {
                    tvMessage.setText(Html.fromHtml("提示: " + html, Html.FROM_HTML_MODE_LEGACY));
                }
            } else {
                if (tvMessage != null) tvMessage.setText("提示步骤匹配失败: " + innerExpr);
            }
        });



        if (btnHintStruct != null) btnHintStruct.setOnClickListener(v -> {
            String sol = getFreshSolution();
            if (sol != null && !sol.contains("无符合")) {
                if (tvMessage != null) {
                    tvMessage.setText("结构: ");
                    tvMessage.append(ExpressionHelper.formatStructure(sol, getCurrentNumbers()));
                    lastPlainTextSolution = ExpressionHelper.getStructureAsPlainText(sol, getCurrentNumbers());
                }
            } else {
                if (tvMessage != null) tvMessage.setText(sol != null ? sol : "无解");
                lastPlainTextSolution = sol != null ? sol : "无解";
            }
        });

        if (btnAnswer != null) btnAnswer.setOnClickListener(v -> {
            String sol = getFreshSolution();
            if (sol != null && !sol.contains("无符合")) {
                if (tvMessage != null) {
                    tvMessage.setText("答案: ");
                    tvMessage.append(ExpressionHelper.formatAnswer(sol, getCurrentNumbers()));
                    lastPlainTextSolution = ExpressionHelper.getAnswerAsPlainText(sol, getCurrentNumbers());
                }
            } else {
                if (tvMessage != null) tvMessage.setText(sol != null ? sol : "无解");
                lastPlainTextSolution = sol != null ? sol : "无解";
            }
        });

        if (btnShare != null) btnShare.setOnClickListener(v -> {/*...Share 逻辑...*/});
    }

    private void resetOpColors() {
        if (btnAdd != null) btnAdd.setBackgroundColor(Color.LTGRAY); if (btnSub != null) btnSub.setBackgroundColor(Color.LTGRAY); if (btnMul != null) btnMul.setBackgroundColor(Color.LTGRAY); if (btnDiv != null) btnDiv.setBackgroundColor(Color.LTGRAY);
    }
}
