package com.example.hajimi24;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import java.util.List;

public class SidebarLogic {

    public interface ActionCallback {
        void onRandomMode(int count);
        void onLoadFile(String fileName);
        // 我们可以为说明书、更新等添加回调
        void onShowInstructions();
        void onSyncFromGithub();
    }

    private final MainActivity context;
    private final DrawerLayout drawerLayout;
    private final NavigationView navView;
    private final ProblemRepository repository;
    private final ActionCallback callback;
    private final GameModeSettings gameModeSettings;
    private final Menu menu;

    public SidebarLogic(MainActivity context, DrawerLayout drawerLayout, NavigationView navView, ProblemRepository repository, ActionCallback callback) {
        this.context = context;
        this.drawerLayout = drawerLayout;
        this.navView = navView;
        this.repository = repository;
        this.callback = callback;
        this.gameModeSettings = new GameModeSettings();
        this.menu = navView.getMenu();
    }

    public GameModeSettings getGameModeSettings() {
        return this.gameModeSettings;
    }

    public void setup() {
        // 清空可能由XML加载的旧菜单项
        menu.clear();

        // --- 恢复您原有的动态菜单逻辑 ---
        menu.add("说明书").setOnMenuItemClickListener(item -> {
            // callback.onShowInstructions(); // 触发显示说明书的回调
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
        menu.add("从 Github 更新题库").setOnMenuItemClickListener(item -> {
            // callback.onSyncFromGithub(); // 触发更新的回调
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // --- 新增：“模式设定”入口 ---
        menu.add("模式设定").setOnMenuItemClickListener(item -> {
            showModeSettingsDialog();
            return true;
        });

        Menu randomMenu = menu.addSubMenu("随机模式");
        randomMenu.add("随机(4数)").setOnMenuItemClickListener(item -> {
            callback.onRandomMode(4);
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
        randomMenu.add("随机(5数)").setOnMenuItemClickListener(item -> {
            callback.onRandomMode(5);
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        Menu fileMenu = menu.addSubMenu("加载文件");
        List<String> files = repository.getAvailableFiles();
        if (files != null && !files.isEmpty()) {
            for (String file : files) {
                fileMenu.add(file).setOnMenuItemClickListener(item -> {
                    callback.onLoadFile(file);
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return true;
                });
            }
        }
    }

    private void showModeSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = context.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_mode_settings, null);
        builder.setView(dialogView);

        // --- 找到弹窗内的所有UI控件 ---
        SwitchCompat switchAvoidAddSub = dialogView.findViewById(R.id.switch_avoid_add_sub);
        SwitchCompat switchMustHaveDivision = dialogView.findViewById(R.id.switch_must_have_division);
        SwitchCompat switchAvoidTrivialMul = dialogView.findViewById(R.id.switch_avoid_trivial_mul);
        SwitchCompat switchRequireFrac = dialogView.findViewById(R.id.switch_require_fraction_calc);
        SwitchCompat switchRequireStorm = dialogView.findViewById(R.id.switch_require_division_storm);
        RadioGroup radioGroupBounds = dialogView.findViewById(R.id.radiogroup_bounds);

        // --- 用当前设置填充UI ---
        switchAvoidAddSub.setChecked(gameModeSettings.avoidPureAddSub);
        switchMustHaveDivision.setChecked(gameModeSettings.mustHaveDivision);
        switchAvoidTrivialMul.setChecked(gameModeSettings.avoidTrivialFinalMultiply);
        switchRequireFrac.setChecked(gameModeSettings.requireFractionCalc);
        switchRequireStorm.setChecked(gameModeSettings.requireDivisionStorm);

        // 动态显隐逻辑
        Runnable updateVisibility = () -> {
            boolean mustDiv = switchMustHaveDivision.isChecked();
            boolean avoidTrivial = switchAvoidTrivialMul.isChecked();
            boolean reqFrac = switchRequireFrac.isChecked();
            switchRequireFrac.setVisibility(mustDiv && avoidTrivial ? View.VISIBLE : View.GONE);
            switchRequireStorm.setVisibility(mustDiv && avoidTrivial && reqFrac ? View.VISIBLE : View.GONE);
        };
        switchMustHaveDivision.setOnCheckedChangeListener((b, c) -> updateVisibility.run());
        switchAvoidTrivialMul.setOnCheckedChangeListener((b, c) -> updateVisibility.run());
        switchRequireFrac.setOnCheckedChangeListener((b, c) -> updateVisibility.run());
        updateVisibility.run(); // 初始检查

        // 填充数字上界
        int bound = gameModeSettings.numberBound;
        if (bound == 9) radioGroupBounds.check(R.id.radio_bound_9);
        else if (bound == 10) radioGroupBounds.check(R.id.radio_bound_10);
        else if (bound == 13) radioGroupBounds.check(R.id.radio_bound_13);
        else if (bound == 20) radioGroupBounds.check(R.id.radio_bound_20);
        else radioGroupBounds.check(R.id.radio_bound_unlimited);

        builder.setTitle("模式设定")
                .setPositiveButton("确定", (dialog, id) -> {
                    // --- 点击“确定”，保存所有设置 ---
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
                })
                .setNegativeButton("取消", (dialog, id) -> dialog.cancel());

        builder.create().show();
    }
}
