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


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private Problem mCurrentProblem;

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
    private String currentFileName = "随机休闲(4数)";
    private String lastPlainTextSolution = "";
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
        setContentView(R.layout.activity_main);
        repository = new ProblemRepository(this);
        gameManager = new GameManager();
        initViews();
        initHelpers();
        initListeners();
        gameStartTime = System.currentTimeMillis();
        switchToRandomMode(4);
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
                gameManager.setProblemSet(problems);
                currentFileName = title; // 存完整路径用于刷新
                updateMenuButtonText(title); // 内部会自动处理路径和换行
                Toast.makeText(MainActivity.this, "加载成功", Toast.LENGTH_SHORT).show();
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
        btnMenu.setSingleLine(false);
        btnMenu.setMaxLines(2);
    }

    public void loadProblemSet(String fileName) {
        try {
            List<Problem> problems = repository.loadProblemSet(fileName, sidebarLogic.getGameModeSettings());
            gameManager.setProblemSet(problems);
            currentFileName = fileName;
            updateMenuButtonText(fileName); // 使用统一样式更新

            Toast.makeText(this, "加载成功", Toast.LENGTH_SHORT).show();
            startNewGameLocal();
        } catch (Exception e) { e.printStackTrace(); switchToRandomMode(4); }
    }



    public void switchToRandomMode(int count) {
        gameManager.currentNumberCount = count;
        currentFileName = "随机休闲(" + count + "数)";
        updateMenuButtonText(currentFileName); // 使用统一样式更新
        startNewGameLocal();
    }


    private void startNewGameLocal() {
        gameManager.startNewGame(currentFileName.startsWith("随机休闲"));
        if (gameTimer != null) gameTimer.start();
        resetSelection();
        refreshUI();
        if (tvMessage != null) tvMessage.setText("");
        lastPlainTextSolution = "";
    }

    private void refreshUI() {
        if (gameManager == null || cardButtons == null) return;

        // 获取当前的模数和进制
        Integer modulus = null;
        int currentRadix = 10;
        Problem p = gameManager.getCurrentProblem();
        if (p != null) {
            modulus = p.modulus;
            if (p.radix != null) currentRadix = p.radix;
        }

        int count = gameManager.currentNumberCount;
        for (int i = 0; i < 5; i++) {
            if (cardButtons[i] == null) continue;
            if (i >= count) {
                cardButtons[i].setVisibility(View.GONE);
            } else {
                Fraction f = gameManager.cardValues[i];
                if (f != null) {
                    cardButtons[i].setVisibility(View.VISIBLE);

                    // 核心修改：确保调用带 radix 参数的方法，防止 A-F 被转换回数字
                    String text;
                    if (modulus != null) {
                        text = f.toModString(modulus, currentRadix);
                    } else {
                        text = f.toString(currentRadix);
                    }
                    cardButtons[i].setText(text);

                    cardButtons[i].setBackgroundColor(Color.parseColor("#CCCCCC"));
                } else {
                    cardButtons[i].setVisibility(View.INVISIBLE);
                }
            }
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
        if (currentNums.isEmpty()) return null;

        Integer modulus = null;
        int targetValue = 24; // 默认

        Problem p = gameManager.getCurrentProblem();
        if (p != null) {
            modulus = p.modulus;
            // 核心修复：进制模式下目标值动态化
            if (p.radix != null) {
                targetValue = 2 * p.radix + 4;
            }
        }

        // 调用支持 targetValue 的 Solver 方法
        return Solver.solve(currentNums, modulus, targetValue);
    }



    private List<Fraction> getCurrentNumbers() {
        List<Fraction> currentNums = new ArrayList<>();
        if (gameManager == null || gameManager.cardValues == null) return currentNums;
        for (Fraction f : gameManager.cardValues) if (f != null) currentNums.add(f);
        return currentNums;
    }

    private void initListeners() {
        // 侧边栏菜单
        if (btnMenu != null) btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // 消息区域长按复制
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
        if (btnUndo != null) btnUndo.setOnClickListener(v -> { if(gameManager.undo()) { refreshUI(); resetSelection(); } });
        if (btnRedo != null) btnRedo.setOnClickListener(v -> { if(gameManager.redo()) { refreshUI(); resetSelection(); } });
        if (btnReset != null) btnReset.setOnClickListener(v -> {
            if (gameManager != null) gameManager.resetCurrentLevel();
            refreshUI();
            resetSelection();
            if (tvMessage != null) tvMessage.setText("");
            Toast.makeText(this, "已重置", Toast.LENGTH_SHORT).show();
        });
        if (btnSkip != null) btnSkip.setOnClickListener(v -> startNewGameLocal());

        // 提示功能 (Hint/Try) - 核心修复：采用数值匹配方案
        if (btnTry != null) btnTry.setOnClickListener(v -> {
            String sol = getFreshSolution();
            if (sol == null) {
                if (tvMessage != null) tvMessage.setText("无解");
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
            // [核心修复]：直接判断前缀。如果当前已经显示“结构”，则点击后直接消失并返回。
            if (tvMessage != null && tvMessage.getText().toString().startsWith("结构: ")) {
                tvMessage.setText("");
                lastPlainTextSolution = "";
                return;
            }

            String sol = getFreshSolution();
            if (sol != null) {
                if (tvMessage != null) {
                    tvMessage.setText("结构: ");
                    tvMessage.append(ExpressionHelper.formatStructure(sol, getCurrentNumbers()));
                    lastPlainTextSolution = ExpressionHelper.getStructureAsPlainText(sol, getCurrentNumbers());
                }
            } else {
                // 无解状态切换逻辑：点一下出现“无解”，再点一下消失
                if (tvMessage != null && "无解".equals(tvMessage.getText().toString())) {
                    tvMessage.setText("");
                    lastPlainTextSolution = "";
                } else if (tvMessage != null) {
                    tvMessage.setText("无解");
                    lastPlainTextSolution = "无解";
                }
            }
        });

// 完整答案功能
        if (btnAnswer != null) btnAnswer.setOnClickListener(v -> {
            // [核心修复]：直接判断前缀。如果当前已经显示“答案”，则点击后直接消失并返回。
            if (tvMessage != null && tvMessage.getText().toString().startsWith("答案: ")) {
                tvMessage.setText("");
                lastPlainTextSolution = "";
                return;
            }

            String sol = getFreshSolution();
            if (sol != null) {
                if (tvMessage != null) {
                    tvMessage.setText("答案: ");
                    tvMessage.append(ExpressionHelper.formatAnswer(sol, getCurrentNumbers()));
                    lastPlainTextSolution = ExpressionHelper.getAnswerAsPlainText(sol, getCurrentNumbers());
                }
            } else {
                // 无解状态切换逻辑：点一下出现“无解”，再点一下消失
                if (tvMessage != null && "无解".equals(tvMessage.getText().toString())) {
                    tvMessage.setText("");
                    lastPlainTextSolution = "";
                } else if (tvMessage != null) {
                    tvMessage.setText("无解");
                    lastPlainTextSolution = "无解";
                }
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
                    Toast.makeText(MainActivity.this, "题目已复制到剪贴板", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "当前没有可分享的题目", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }



    private void resetOpColors() {
        if (btnAdd != null) btnAdd.setBackgroundColor(Color.LTGRAY); if (btnSub != null) btnSub.setBackgroundColor(Color.LTGRAY); if (btnMul != null) btnMul.setBackgroundColor(Color.LTGRAY); if (btnDiv != null) btnDiv.setBackgroundColor(Color.LTGRAY);
    }
}
