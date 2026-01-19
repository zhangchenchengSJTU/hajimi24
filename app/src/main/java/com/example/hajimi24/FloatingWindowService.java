package com.example.hajimi24;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
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

    private View viewCards, viewOps, viewActions;
    private WindowManager.LayoutParams pCards, pOps, pActions;

    private int currentOrientation = 0;
    private String currentLatexCode = "";

    // 【核心新增】：记录当前模式和题库，保证“跳过”按钮逻辑正确
    private boolean isRandomMode = true;
    private List<Problem> currentProblemSet = null;

    private Button[] cardButtons = new Button[4];
    private Button btnAdd, btnSub, btnMul, btnDiv;
    private WebView wvMath;

    private int selectedFirstIndex = -1;
    private String selectedOperator = null;

    private final int COLOR_DEFAULT_GRAY = Color.parseColor("#CC666666");
    private final int COLOR_OP_GRAY = Color.parseColor("#CC444444");
    private final int COLOR_LAVENDER = Color.parseColor("#CCB8FA");
    private final int COLOR_LIGHT_GREEN = Color.parseColor("#A8E6CF");

    @Override
    public void onCreate() {
        super.onCreate();
        gameManager = new GameManager();
        // 【强制约束】：初始化时默认设为 4 个数
        gameManager.currentNumberCount = 4;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 1. 获取主界面传来的状态
        this.isRandomMode = MainActivity.sharedIsRandomMode;
        this.currentProblemSet = MainActivity.sharedProblemSet;

        // 2. 【核心拦截逻辑】：如果传过来的题目是 5 个数，强制“降级”为 4 数随机模式
        if (MainActivity.sharedProblem != null && MainActivity.sharedProblem.numbers.size() == 5) {
            this.isRandomMode = true;
            this.currentProblemSet = null;
            MainActivity.sharedProblem = null; // 丢弃该 5 数题目对象
            Toast.makeText(this, "悬浮窗仅支持 4 数，已切换至随机模式", Toast.LENGTH_SHORT).show();
        }

        // 3. 执行题目加载
        if (MainActivity.sharedProblem != null) {
            // 如果是合法的 4 数题目，进行同步加载
            List<Problem> single = new ArrayList<>();
            single.add(MainActivity.sharedProblem);
            gameManager.setProblemSet(single);
            gameManager.startNewGame(false); // 锁定这道题

            // 如果是题库模式，同步加载题库列表（MainActivity 已经过滤过 4 数了）
            if (!isRandomMode && currentProblemSet != null) {
                gameManager.setProblemSet(currentProblemSet);
            }
            MainActivity.sharedProblem = null; // 消费掉
        } else {
            // 否则（原本就是随机模式，或被强制降级了），生成 4 数随机题
            gameManager.startNewGame(true);
        }

        switchLayout(0);
    }


    private void switchLayout(int degrees) {
        removeOldViews();
        currentOrientation = degrees;
        String suffix = "_" + degrees;

        viewCards = inflate(getResId("layout_float_cards" + suffix));
        viewOps = inflate(getResId("layout_float_ops" + suffix));
        viewActions = inflate(getResId("layout_float_actions" + suffix));

        if (pCards == null) {
            pCards = createLayoutParams(100, 200);
            pOps = createLayoutParams(100, 600);
            pActions = createLayoutParams(100, 1000);
        }

        windowManager.addView(viewCards, pCards);
        windowManager.addView(viewOps, pOps);
        windowManager.addView(viewActions, pActions);

        initViews();
        initListeners();
        refreshUI();

        if (!currentLatexCode.isEmpty()) {
            int depth = ExpressionHelper.getLatexHeight(currentLatexCode);
            new Handler().postDelayed(() -> renderLatex(currentLatexCode, depth), 200);
        }
    }

    private void initViews() {
        cardButtons[0] = viewCards.findViewById(R.id.card_1);
        cardButtons[1] = viewCards.findViewById(R.id.card_2);
        cardButtons[2] = viewCards.findViewById(R.id.card_3);
        cardButtons[3] = viewCards.findViewById(R.id.card_4);
        btnAdd = viewOps.findViewById(R.id.btn_op_add);
        btnSub = viewOps.findViewById(R.id.btn_op_sub);
        btnMul = viewOps.findViewById(R.id.btn_op_mul);
        btnDiv = viewOps.findViewById(R.id.btn_op_div);
        wvMath = viewActions.findViewById(R.id.wv_math_message);

        if (wvMath != null) {
            WebSettings s = wvMath.getSettings();
            s.setJavaScriptEnabled(true);
            wvMath.setBackgroundColor(0);
            wvMath.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            wvMath.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void onRenderFinished() {
                    wvMath.post(() -> wvMath.animate().alpha(1.0f).setDuration(150).start());
                }
            }, "android");
        }
    }

    private void initListeners() {
        bindDrag(viewCards.findViewById(R.id.handle), viewCards, pCards);
        bindDrag(viewOps.findViewById(R.id.handle), viewOps, pOps);
        bindDrag(viewActions.findViewById(R.id.handle), viewActions, pActions);

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            if (cardButtons[idx] != null) cardButtons[idx].setOnClickListener(v -> onCardClicked(idx));
        }

        View.OnClickListener opListener = v -> {
            if (selectedFirstIndex == -1) return;
            resetOpColors();
            selectedOperator = (v.getId() == R.id.btn_op_add) ? "+" :
                    (v.getId() == R.id.btn_op_sub) ? "-" :
                            (v.getId() == R.id.btn_op_mul) ? "*" : "/";
            v.setBackgroundTintList(ColorStateList.valueOf(COLOR_LIGHT_GREEN));
        };
        if (btnAdd != null) btnAdd.setOnClickListener(opListener);
        if (btnSub != null) btnSub.setOnClickListener(opListener);
        if (btnMul != null) btnMul.setOnClickListener(opListener);
        if (btnDiv != null) btnDiv.setOnClickListener(opListener);

        safeBind(viewActions, R.id.btn_undo, v -> { if(gameManager.undo()){ refreshUI(); resetSelection(); }});
        safeBind(viewActions, R.id.btn_hint_struct, v -> updateDisplay(getFreshSolution(), true));
        safeBind(viewActions, R.id.btn_answer, v -> updateDisplay(getFreshSolution(), false));
        safeBind(viewActions, R.id.btn_share, v -> {
            String text = gameManager.getShareText();
            if (text != null && !text.isEmpty()) {
                ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("Hajimi24", text));
                Toast.makeText(this, "题目已复制", Toast.LENGTH_SHORT).show();
            }
        });

        // --- 【核心修复 2】：使用记录的模式进行刷新 ---
        safeBind(viewActions, R.id.btn_skip, v -> startNewGame());

        safeBind(viewActions, R.id.btn_rotate, v -> switchLayout((currentOrientation + 90) % 360));
        // --- 【核心修复 3】：喵功能同步回到主界面 ---
        safeBind(viewActions, R.id.btn_home, v -> {
            Problem current = gameManager.getCurrentProblem();
            if (current == null) {
                // 如果是随机出的题（currentProblem为null），封装一个 Problem 对象带走
                List<Fraction> nums = new ArrayList<>();
                for (Fraction f : gameManager.initialValues) if (f != null) nums.add(f);
                current = new Problem(nums, gameManager.currentLevelSolution, gameManager.getRawProblemLine(), null, 10);
            }
            MainActivity.sharedProblem = current;
            MainActivity.sharedIsRandomMode = this.isRandomMode;
            MainActivity.sharedProblemSet = this.currentProblemSet;

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            stopSelf();
        });
    }

    private void renderLatex(String latex, int depth) {
        if (wvMath == null) return;
        wvMath.setAlpha(0f);

        String fontSize = (depth <= 2) ? "120%" : (depth == 3) ? "105%" : (depth == 4) ? "90%" : "75%";
        float density = getResources().getDisplayMetrics().density;
        int targetDimPx = (int) ((85 + Math.max(0, depth - 2) * 32) * density);
        int fixedDimPx = (int) (330 * density);

        wvMath.post(() -> {
            android.view.ViewGroup.LayoutParams lp = wvMath.getLayoutParams();
            if (currentOrientation == 0 || currentOrientation == 180) {
                lp.height = targetDimPx; lp.width = fixedDimPx;
            } else {
                lp.width = targetDimPx; lp.height = fixedDimPx;
            }
            wvMath.setLayoutParams(lp);
            if (viewActions != null) windowManager.updateViewLayout(viewActions, pActions);
        });

        String cssRotation = currentOrientation + "deg";
        String html = "<html><head><style>" +
                "body { background: transparent !important; color: white; display: flex; justify-content: center; align-items: center; " +
                "margin: 0; padding: 0; width: 100vw; height: 100vh; overflow: hidden; }" +
                "#math { transform: translate(-50%, -50%) rotate(" + cssRotation + "); position: absolute; top: 50%; left: 50%; white-space: nowrap; font-size: " + fontSize + "; }" +
                "</style>" +
                "<script>window.MathJax = { startup: { ready: () => { " +
                "MathJax.startup.defaultReady(); MathJax.startup.promise.then(() => { if(window.android) window.android.onRenderFinished(); }); " +
                "} } };</script>" +
                "<script src='file:///android_asset/mathjax/tex-svg.js'></script></head>" +
                "<body><div id='math'>$$\\displaystyle " + latex + "$$</div></body></html>";

        wvMath.loadDataWithBaseURL("file:///android_asset/mathjax/", html, "text/html", "UTF-8", null);
    }

    private void startNewGame() {
        // 使用成员变量决定模式，不再写死 true
        gameManager.startNewGame(isRandomMode);
        resetSelection(); refreshUI();
        if (wvMath != null) wvMath.setVisibility(View.GONE);
    }

    // ... 其他辅助方法 (onCardClicked, refreshUI, resetOpColors, etc.) 保持稳定逻辑 ...

    private void onCardClicked(int index) {
        if (selectedFirstIndex == -1) selectCard(index);
        else if (selectedFirstIndex == index) resetSelection();
        else {
            if (selectedOperator != null) {
                if (gameManager.performCalculation(selectedFirstIndex, index, selectedOperator)) {
                    selectedFirstIndex = index; selectedOperator = null;
                    refreshUI(); checkWin();
                }
            } else selectCard(index);
        }
    }

    private void refreshUI() {
        for (int i = 0; i < 4; i++) {
            Fraction f = gameManager.cardValues[i];
            if (cardButtons[i] == null) continue;
            if (f != null) {
                cardButtons[i].setVisibility(View.VISIBLE);
                cardButtons[i].setText(f.toString(10));
                int color = (i == selectedFirstIndex) ? COLOR_LAVENDER : COLOR_DEFAULT_GRAY;
                cardButtons[i].setBackgroundTintList(ColorStateList.valueOf(color));
            } else cardButtons[i].setVisibility(View.INVISIBLE);
        }
        resetOpColors();
    }

    private void resetOpColors() {
        ColorStateList tint = ColorStateList.valueOf(COLOR_OP_GRAY);
        if(btnAdd != null) btnAdd.setBackgroundTintList(tint);
        if(btnSub != null) btnSub.setBackgroundTintList(tint);
        if(btnMul != null) btnMul.setBackgroundTintList(tint);
        if(btnDiv != null) btnDiv.setBackgroundTintList(tint);
    }

    private void removeOldViews() {
        if (viewCards != null) { windowManager.removeView(viewCards); viewCards = null; }
        if (viewOps != null) { windowManager.removeView(viewOps); viewOps = null; }
        if (viewActions != null) { windowManager.removeView(viewActions); viewActions = null; }
    }

    private void selectCard(int index) { selectedFirstIndex = index; refreshUI(); }
    private void resetSelection() { selectedFirstIndex = -1; selectedOperator = null; resetOpColors(); }
    private int getResId(String name) { return getResources().getIdentifier(name, "layout", getPackageName()); }
    private View inflate(int id) { return LayoutInflater.from(this).inflate(id, null); }
    private List<Fraction> getCurrentNumbers() {
        List<Fraction> list = new ArrayList<>();
        for (Fraction f : gameManager.cardValues) if (f != null) list.add(f);
        return list;
    }
// FloatingWindowService.java 内部修改

    private String getFreshSolution() {
        List<Fraction> currentNums = getCurrentNumbers();
        if (currentNums.isEmpty()) return null;

        // --- 【核心修复】：同步环境参数 ---
        Integer modulus = null;
        int radix = 10;
        int targetValue = 24;

        // 从当前题目中获取真正的模数和目标值
        Problem p = gameManager.getCurrentProblem();
        if (p != null) {
            modulus = p.modulus;
            if (p.radix != null) {
                radix = p.radix;
                targetValue = 2 * radix + 4; // 计算该进制下的“24”
            }
        }

        // 调用求解器
        List<String> allRawSolutions = Solver.solveAll(currentNums, modulus, targetValue);
        if (allRawSolutions.isEmpty()) return null;

        // 构造规范化后缀
        String suffix = "";
        if (modulus != null) suffix = " mod " + modulus;
        else if (radix != 10) suffix = " base " + radix;

        List<String> candidates = new ArrayList<>();
        for (String s : allRawSolutions) candidates.add(s + suffix);

        // 调用规范化工具（确保和主界面显示的答案一致）
        List<String> distinctSolutions = SolutionNormalizer.distinct(candidates);

        // 排序选出最优解
        java.util.Collections.sort(distinctSolutions, (s1, s2) -> {
            if (s1.length() != s2.length()) return Integer.compare(s1.length(), s2.length());
            return s1.compareTo(s2);
        });

        return distinctSolutions.get(0);
    }

    private void updateDisplay(String sol, boolean isStruct) {
        if (sol == null || wvMath == null) { if (wvMath != null) wvMath.setVisibility(View.GONE); return; }
        wvMath.setVisibility(View.VISIBLE);
        currentLatexCode = ExpressionHelper.getAsLatex(sol, getCurrentNumbers(), isStruct, 1, 0);
        renderLatex(currentLatexCode, ExpressionHelper.getLatexHeight(currentLatexCode));
    }
    private void checkWin() {
        if (gameManager.checkWin()) {
            Toast.makeText(this, "正确!", Toast.LENGTH_SHORT).show();
            gameManager.solvedCount++;
            new Handler().postDelayed(this::startNewGame, 1000);
        }
    }
    private void bindDrag(View handle, View root, WindowManager.LayoutParams p) {
        if (handle == null) return;
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int iX, iY; private float iTX, iTY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) { iX = p.x; iY = p.y; iTX = e.getRawX(); iTY = e.getRawY(); return true; }
                if (e.getAction() == MotionEvent.ACTION_MOVE) { p.x = iX + (int)(e.getRawX()-iTX); p.y = iY + (int)(e.getRawY()-iTY); windowManager.updateViewLayout(root, p); return true; }
                return false;
            }
        });
    }
    private void safeBind(View r, int id, View.OnClickListener l) { View v = r.findViewById(id); if (v != null) v.setOnClickListener(l); }
    private WindowManager.LayoutParams createLayoutParams(int x, int y) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 2038 : 2002, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START; p.x = x; p.y = y; return p;
    }
    @Override public void onDestroy() { super.onDestroy(); removeOldViews(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
