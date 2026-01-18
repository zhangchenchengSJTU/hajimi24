package com.example.hajimi24;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private GameManager gameManager;

    // 三个弹窗的 View 和 Params
    private View viewCards, viewOps, viewActions;
    private WindowManager.LayoutParams pCards, pOps, pActions;

    // 当前旋转状态 (0, 90, 180, 270)
    private int currentOrientation = 0;

    // 控件引用
    private Button[] cardButtons = new Button[4];
    private Button btnAdd, btnSub, btnMul, btnDiv;
    private WebView wvMath;

    // 游戏逻辑变量
    private int selectedFirstIndex = -1;
    private String selectedOperator = null;

    @Override
    public void onCreate() {
        super.onCreate();
        gameManager = new GameManager();
        gameManager.currentNumberCount = 4;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 初始加载 0 度布局
        switchLayout(0);
    }

    /**
     * 核心方法：切换布局实现“逻辑旋转”
     */
    private void switchLayout(int degrees) {
        // 1. 移除旧视图并保存当前位置（防止旋转后窗口跳回初始位置）
        removeOldViews();

        currentOrientation = degrees;
        String suffix = "_" + degrees;

        // 2. 加载对应的 XML
        viewCards = inflate(getResId("layout_float_cards" + suffix));
        viewOps = inflate(getResId("layout_float_ops" + suffix));
        viewActions = inflate(getResId("layout_float_actions" + suffix));

        // 3. 确保 LayoutParams 已初始化
        if (pCards == null) {
            pCards = createLayoutParams(100, 200);
            pOps = createLayoutParams(100, 600);
            pActions = createLayoutParams(100, 1000);
        }

        // 4. 使用 WRAP_CONTENT 添加到窗口
        windowManager.addView(viewCards, pCards);
        windowManager.addView(viewOps, pOps);
        windowManager.addView(viewActions, pActions);

        // 5. 重新绑定 ID 与监听器
        initViews();
        initListeners();
        refreshUI();
    }

    private void removeOldViews() {
        if (viewCards != null) {
            windowManager.removeView(viewCards);
            viewCards = null;
        }
        if (viewOps != null) {
            windowManager.removeView(viewOps);
            viewOps = null;
        }
        if (viewActions != null) {
            windowManager.removeView(viewActions);
            viewActions = null;
        }
    }

    private void initViews() {
        // 数字窗
        cardButtons[0] = viewCards.findViewById(R.id.card_1);
        cardButtons[1] = viewCards.findViewById(R.id.card_2);
        cardButtons[2] = viewCards.findViewById(R.id.card_3);
        cardButtons[3] = viewCards.findViewById(R.id.card_4);

        // 运算窗
        btnAdd = viewOps.findViewById(R.id.btn_op_add);
        btnSub = viewOps.findViewById(R.id.btn_op_sub);
        btnMul = viewOps.findViewById(R.id.btn_op_mul);
        btnDiv = viewOps.findViewById(R.id.btn_op_div);

        // 工具窗 WebView
        wvMath = viewActions.findViewById(R.id.wv_math_message);
        if (wvMath != null) {
            WebSettings settings = wvMath.getSettings();
            settings.setJavaScriptEnabled(true);
            wvMath.setBackgroundColor(0);
        }
    }

    private void initListeners() {
        // 绑定拖拽
        bindDrag(viewCards.findViewById(R.id.handle), viewCards, pCards);
        bindDrag(viewOps.findViewById(R.id.handle), viewOps, pOps);
        bindDrag(viewActions.findViewById(R.id.handle), viewActions, pActions);

        // 数字点击
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            if (cardButtons[i] != null) cardButtons[idx].setOnClickListener(v -> onCardClicked(idx));
        }

        // 运算符点击
        View.OnClickListener opListener = v -> {
            if (selectedFirstIndex == -1) return;
            resetOpColors();
            selectedOperator = (v.getId() == R.id.btn_op_add) ? "+" :
                    (v.getId() == R.id.btn_op_sub) ? "-" :
                            (v.getId() == R.id.btn_op_mul) ? "*" : "/";
            v.setBackgroundColor(Color.BLUE);
        };
        if (btnAdd != null) btnAdd.setOnClickListener(opListener);
        if (btnSub != null) btnSub.setOnClickListener(opListener);
        if (btnMul != null) btnMul.setOnClickListener(opListener);
        if (btnDiv != null) btnDiv.setOnClickListener(opListener);

        // 工具窗 12 按钮逻辑 (通过 safeBind 确保旋转后的新视图正确绑定)
        safeBind(viewActions, R.id.btn_undo, v -> { if(gameManager.undo()){ refreshUI(); resetSelection(); }});
        safeBind(viewActions, R.id.btn_reset, v -> { gameManager.resetCurrentLevel(); refreshUI(); resetSelection(); });
        safeBind(viewActions, R.id.btn_redo, v -> { if(gameManager.redo()){ refreshUI(); resetSelection(); }});
        safeBind(viewActions, R.id.btn_hint_struct, v -> updateDisplay(getFreshSolution(), true));
        safeBind(viewActions, R.id.btn_answer, v -> updateDisplay(getFreshSolution(), false));
        safeBind(viewActions, R.id.btn_share, v -> { /* 分享逻辑 */ });
        safeBind(viewActions, R.id.btn_skip, v -> startNewGame());
        safeBind(viewActions, R.id.btn_exit, v -> stopSelf());
        safeBind(viewActions, R.id.btn_problems, v -> { /* 选择题库逻辑 */ });
        safeBind(viewActions, R.id.btn_calculator, v -> { /* 打开计算器逻辑 */ });
        safeBind(viewActions, R.id.btn_settings, v -> { /* 透明度调节逻辑 */ });

        // 旋转功能：循环切换 (0->90->180->270)
        safeBind(viewActions, R.id.btn_rotate, v -> {
            int nextDeg = (currentOrientation + 90) % 360;
            switchLayout(nextDeg);
        });

        // 喵功能：回到主界面并关闭悬浮窗
        safeBind(viewActions, R.id.btn_home, v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            stopSelf();
        });
    }

    // --- 辅助方法 ---

    private void bindDrag(View handle, View root, WindowManager.LayoutParams params) {
        if (handle == null) return;
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(root, params);
                        return true;
                }
                return false;
            }
        });
    }

    private WindowManager.LayoutParams createLayoutParams(int x, int y) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, // 必须是自适应
                WindowManager.LayoutParams.WRAP_CONTENT, // 必须是自适应
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 2038 : 2002,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = x; p.y = y;
        return p;
    }

    private void safeBind(View root, int id, View.OnClickListener listener) {
        View v = root.findViewById(id);
        if (v != null) v.setOnClickListener(listener);
    }

    private int getResId(String name) {
        return getResources().getIdentifier(name, "layout", getPackageName());
    }

    private View inflate(int id) {
        return LayoutInflater.from(this).inflate(id, null);
    }

    // --- 游戏业务逻辑复刻 ---

    private void onCardClicked(int index) {
        if (selectedFirstIndex == -1) selectCard(index);
        else if (selectedFirstIndex == index) resetSelection();
        else {
            if (selectedOperator != null) {
                if (gameManager.performCalculation(selectedFirstIndex, index, selectedOperator)) {
                    selectedFirstIndex = index; selectedOperator = null;
                    resetOpColors(); refreshUI(); checkWin();
                }
            } else selectCard(index);
        }
    }

    private void selectCard(int index) {
        for(Button b : cardButtons) if(b != null) b.setBackgroundColor(Color.parseColor("#CC666666"));
        selectedFirstIndex = index;
        if (index != -1 && cardButtons[index] != null) cardButtons[index].setBackgroundColor(Color.GREEN);
    }

    private void refreshUI() {
        for (int i = 0; i < 4; i++) {
            Fraction f = gameManager.cardValues[i];
            if (cardButtons[i] == null) continue;
            if (f != null) {
                cardButtons[i].setVisibility(View.VISIBLE);
                cardButtons[i].setText(f.toString(10));
                cardButtons[i].setBackgroundColor(i == selectedFirstIndex ? Color.GREEN : Color.parseColor("#CC666666"));
            } else cardButtons[i].setVisibility(View.INVISIBLE);
        }
    }

    private void startNewGame() {
        gameManager.startNewGame(true);
        resetSelection(); refreshUI();
        if (wvMath != null) wvMath.setVisibility(View.GONE);
    }

    private void checkWin() {
        if (gameManager.checkWin()) {
            Toast.makeText(this, "正确!", Toast.LENGTH_SHORT).show();
            gameManager.solvedCount++;
            new Handler().postDelayed(this::startNewGame, 1000);
        }
    }

    private void resetSelection() { selectCard(-1); selectedOperator = null; resetOpColors(); }

    private void resetOpColors() {
        int c = Color.parseColor("#CC444444");
        if(btnAdd != null) btnAdd.setBackgroundColor(c);
        if(btnSub != null) btnSub.setBackgroundColor(c);
        if(btnMul != null) btnMul.setBackgroundColor(c);
        if(btnDiv != null) btnDiv.setBackgroundColor(c);
    }

    private String getFreshSolution() {
        List<Fraction> nums = new ArrayList<>();
        for (Fraction f : gameManager.cardValues) if (f != null) nums.add(f);
        List<String> sols = Solver.solveAll(nums, null, 24);
        return sols.isEmpty() ? null : sols.get(0);
    }

    private void updateDisplay(String sol, boolean isStruct) {
        if (sol == null || wvMath == null) return;
        wvMath.setVisibility(View.VISIBLE);
        List<Fraction> nums = new ArrayList<>();
        for (Fraction f : gameManager.cardValues) if (f != null) nums.add(f);
        String latex = ExpressionHelper.getAsLatex(sol, nums, isStruct, 1, 0);
        String html = "<html><head><script src='file:///android_asset/mathjax/tex-svg.js'></script></head>" +
                "<body style='background:transparent;color:white;display:flex;justify-content:center;'>" +
                "$$\\displaystyle " + latex + "$$</body></html>";
        wvMath.loadDataWithBaseURL("file:///android_asset/mathjax/", html, "text/html", "UTF-8", null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeOldViews();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
