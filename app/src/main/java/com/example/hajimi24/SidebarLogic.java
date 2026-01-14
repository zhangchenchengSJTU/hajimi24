package com.example.hajimi24;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.List;

public class SidebarLogic {

    private final Activity activity;
    private final DrawerLayout drawerLayout;
    private final NavigationView navigationView;
    private final ProblemRepository repository;
    private final ActionCallback callback;
    private final GameModeSettings gameModeSettings;

    // 🚩 新增：记录当前是否为随机模式，默认为 true
    private boolean isCurrentModeRandom = true;

    public interface ActionCallback {
        void onRandomMode(int count);
        void onLoadFile(String fileName);
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
        return this.gameModeSettings;
    }

    public void setup() {
        refreshMenu();
        navigationView.setNavigationItemSelectedListener(item -> {
            String t = item.getTitle().toString();
            if (t.contains("游戏说明书")) {
                showHelpDialog();
            } else if (t.contains("从 GitHub 更新")) {
                syncFromGitHub();
            } else if (t.contains("模式设定")) {
                showModeSettingsDialog();
            } else {
                // --- 状态切换逻辑 ---
                if (t.contains("随机")) {
                    isCurrentModeRandom = true; // 标记为随机模式
                    if (t.contains("4数")) callback.onRandomMode(4);
                    else callback.onRandomMode(5);
                } else if (t.contains("📄")) {
                    isCurrentModeRandom = false; // 标记为文件模式
                    callback.onLoadFile(t.substring(t.indexOf(" ") + 1));
                }

                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return true;
        });
    }

    public void refreshMenu() {
        // (保持原有的菜单刷新代码不变)
        Menu menu = navigationView.getMenu();
        menu.clear();
        menu.add(Menu.NONE, 888, Menu.NONE, "📖 游戏说明书");
        menu.add(Menu.NONE, 999, Menu.NONE, "☁️ 从 GitHub 更新题库");
        menu.add(Menu.NONE, 777, Menu.NONE, "⚙️ 模式设定");
        menu.add(Menu.NONE, 0, Menu.NONE, "🎲 随机 (4数)");
        menu.add(Menu.NONE, 1, Menu.NONE, "🎲 随机 (5数)");
        List<String> files = repository.getAvailableFiles();
        if (files != null) {
            int id = 2;
            for (String f : files) menu.add(Menu.NONE, id++, Menu.NONE, "📄 " + f);
        }
    }

    private void syncFromGitHub() {
        // (保持原有的同步代码不变，略去以节省篇幅)
        Menu menu = navigationView.getMenu();
        MenuItem updateItem = menu.findItem(999);
        if (updateItem != null) updateItem.setTitle("⏳ 正在连接 GitHub...");
        repository.syncFromGitHub(new ProblemRepository.SyncCallback() {
            @Override public void onProgress(String fileName, int current, int total) { activity.runOnUiThread(() -> { if (updateItem != null) updateItem.setTitle("⬇️ " + current + "/" + total); }); }
            @Override public void onSuccess(int count) { activity.runOnUiThread(() -> { if (updateItem != null) updateItem.setTitle("✅ 完成"); Toast.makeText(activity, "更新完成", Toast.LENGTH_SHORT).show(); refreshMenu(); }); }
            @Override public void onFail(String error) { activity.runOnUiThread(() -> { if (updateItem != null) updateItem.setTitle("❌ 失败"); Toast.makeText(activity, error, Toast.LENGTH_SHORT).show(); }); }
        });
    }

    private void showHelpDialog() {
        // (保持原有的说明书代码不变)
        CharSequence helpContent = MarkdownUtils.loadMarkdownFromAssets(activity, "help.md");
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("游戏指南").setMessage(helpContent).setPositiveButton("开始挑战", null).create();
        dialog.show();
        TextView msgView = dialog.findViewById(android.R.id.message);
        if (msgView != null) msgView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }

    // --- 核心修改部分 ---
    private void showModeSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        LayoutInflater inflater = activity.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_mode_settings, null);
        builder.setView(dialogView);

        // 绑定控件
        SwitchCompat switchAvoidAddSub = dialogView.findViewById(R.id.switch_avoid_add_sub);
        SwitchCompat switchMustHaveDivision = dialogView.findViewById(R.id.switch_must_have_division);
        SwitchCompat switchAvoidTrivialMul = dialogView.findViewById(R.id.switch_avoid_trivial_mul);
        SwitchCompat switchRequireFrac = dialogView.findViewById(R.id.switch_require_fraction_calc);
        SwitchCompat switchRequireStorm = dialogView.findViewById(R.id.switch_require_division_storm);
        RadioGroup radioGroupBounds = dialogView.findViewById(R.id.radiogroup_bounds);

        // ⚠️ 请确保 layout 中有这个 TextView，如果没有会导致空指针崩溃
        // 如果还没有修改 layout，请暂时注释掉这两行
        TextView tvWarning = dialogView.findViewById(R.id.tv_warning_random);

        // 初始化开关状态
        switchAvoidAddSub.setChecked(gameModeSettings.avoidPureAddSub);
        switchMustHaveDivision.setChecked(gameModeSettings.mustHaveDivision);
        switchAvoidTrivialMul.setChecked(gameModeSettings.avoidTrivialFinalMultiply);
        switchRequireFrac.setChecked(gameModeSettings.requireFractionCalc);
        switchRequireStorm.setChecked(gameModeSettings.requireDivisionStorm);

        // --- 核心逻辑: 可见性联动 ---
        Runnable updateVisibility = () -> {
            // 规则 1: 如果是随机模式，隐藏所有“高质量”开关，显示警告文字
            if (isCurrentModeRandom) {
                switchAvoidAddSub.setVisibility(View.GONE);
                switchMustHaveDivision.setVisibility(View.GONE);
                switchAvoidTrivialMul.setVisibility(View.GONE);
                switchRequireFrac.setVisibility(View.GONE);
                switchRequireStorm.setVisibility(View.GONE);

                if (tvWarning != null) {
                    tvWarning.setVisibility(View.VISIBLE);
                    tvWarning.setText("🚫 高质量出题仅在加载题库文件时可用\n请先从侧边栏选择一个文件");
                }
                return; // 直接结束，不再处理后续逻辑
            }

            // 如果不是随机模式，隐藏警告
            if (tvWarning != null) tvWarning.setVisibility(View.GONE);

            // 规则 2: 第一层开关 - 避免纯加减
            switchAvoidAddSub.setVisibility(View.VISIBLE); // 永远显示第一层

            boolean layer1Active = switchAvoidAddSub.isChecked();

            // 规则 2: 打开 '避免纯加减' 才会出现 '必须有除法' 和 '避免平凡乘法'
            int layer2Visibility = layer1Active ? View.VISIBLE : View.GONE;
            switchMustHaveDivision.setVisibility(layer2Visibility);
            switchAvoidTrivialMul.setVisibility(layer2Visibility);

            // 规则 3: 先打开 '必须有除法' 和 '避免平凡乘法'，才有 '包含分数' 和 '除法风暴'
            boolean mustDiv = switchMustHaveDivision.isChecked();
            boolean avoidTrivial = switchAvoidTrivialMul.isChecked();

            // 只有 Layer 1 开启，且 Layer 2 的两个都开启时，Layer 3 才显示
            int layer3Visibility = (layer1Active && mustDiv && avoidTrivial) ? View.VISIBLE : View.GONE;

            switchRequireFrac.setVisibility(layer3Visibility);
            switchRequireStorm.setVisibility(layer3Visibility);
        };

        // 绑定监听器
        switchAvoidAddSub.setOnCheckedChangeListener((b, c) -> updateVisibility.run());
        switchMustHaveDivision.setOnCheckedChangeListener((b, c) -> updateVisibility.run());
        switchAvoidTrivialMul.setOnCheckedChangeListener((b, c) -> updateVisibility.run());

        // 初始化运行一次，设定初始状态
        updateVisibility.run();

        // 绑定数字范围逻辑 (保持不变)
        int bound = gameModeSettings.numberBound;
        if (bound == 9) radioGroupBounds.check(R.id.radio_bound_9);
        else if (bound == 10) radioGroupBounds.check(R.id.radio_bound_10);
        else if (bound == 13) radioGroupBounds.check(R.id.radio_bound_13);
        else if (bound == 20) radioGroupBounds.check(R.id.radio_bound_20);
        else radioGroupBounds.check(R.id.radio_bound_unlimited);

        builder.setTitle("模式设定")
                .setPositiveButton("确定", (dialog, id) -> {
                    gameModeSettings.avoidPureAddSub = switchAvoidAddSub.isChecked();
                    gameModeSettings.mustHaveDivision = switchMustHaveDivision.isChecked();
                    gameModeSettings.avoidTrivialFinalMultiply = switchAvoidTrivialMul.isChecked();
                    gameModeSettings.requireFractionCalc = switchRequireFrac.isChecked();
                    gameModeSettings.requireDivisionStorm = switchRequireStorm.isChecked();

                    int selectedRadioId = radioGroupBounds.getCheckedRadioButtonId();
                    if (selectedRadioId == R.id.radio_bound_9) gameModeSettings.numberBound = 9;
                    else if (selectedRadioId == R.id.radio_bound_10) gameModeSettings.numberBound = 10;
                    else if (selectedRadioId == R.id.radio_bound_13) gameModeSettings.numberBound = 13;
                    else if (selectedRadioId == R.id.radio_bound_20) gameModeSettings.numberBound = 20;
                    else gameModeSettings.numberBound = -1;
                    if (callback != null) {
                        callback.onSettingsChanged();
                    }
                })
                .setNegativeButton("取消", (dialog, id) -> dialog.cancel());
        builder.create().show();
    }
}
