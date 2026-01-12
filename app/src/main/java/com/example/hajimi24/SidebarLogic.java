package com.example.hajimi24;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
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

    // 回调接口，通知 Activity 切换模式
    public interface ActionCallback {
        void onRandomMode(int count);
        void onLoadFile(String fileName);
    }

    public SidebarLogic(Activity activity, DrawerLayout drawerLayout,
                        NavigationView navigationView, ProblemRepository repository,
                        ActionCallback callback) {
        this.activity = activity;
        this.drawerLayout = drawerLayout;
        this.navigationView = navigationView;
        this.repository = repository;
        this.callback = callback;
    }

    public void setup() {
        refreshMenu();
        navigationView.setNavigationItemSelectedListener(item -> {
            String t = item.getTitle().toString();
            if (t.contains("游戏说明书")) {
                showHelpDialog();
            } else if (t.contains("从 GitHub 更新")) {
                syncFromGitHub();
            } else {
                // 模式选择
                if (t.contains("随机 (4数)")) callback.onRandomMode(4);
                else if (t.contains("随机 (5数)")) callback.onRandomMode(5);
                else callback.onLoadFile(t.substring(t.indexOf(" ") + 1));

                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return true;
        });
    }

    public void refreshMenu() {
        Menu menu = navigationView.getMenu();
        menu.clear();
        menu.add(Menu.NONE, 888, Menu.NONE, "📖 游戏说明书");
        menu.add(Menu.NONE, 999, Menu.NONE, "☁️ 从 GitHub 更新题库");
        menu.add(Menu.NONE, 0, Menu.NONE, "🎲 随机 (4数)");
        menu.add(Menu.NONE, 1, Menu.NONE, "🎲 随机 (5数)");

        List<String> files = repository.getAvailableFiles();
        int id = 2;
        for (String f : files) menu.add(Menu.NONE, id++, Menu.NONE, "📄 " + f);
    }

    private void syncFromGitHub() {
        Menu menu = navigationView.getMenu();
        MenuItem updateItem = menu.findItem(999);
        if (updateItem != null) updateItem.setTitle("⏳ 正在连接 GitHub...");

        repository.syncFromGitHub(new ProblemRepository.SyncCallback() {
            @Override
            public void onProgress(String fileName, int current, int total) {
                activity.runOnUiThread(() -> {
                    if (updateItem != null) updateItem.setTitle("⬇️ 下载中: " + current + "/" + total);
                });
            }

            @Override
            public void onSuccess(int count) {
                activity.runOnUiThread(() -> {
                    if (updateItem != null) {
                        updateItem.setTitle("✅ 更新完成 (" + count + ")");
                        new Handler().postDelayed(() -> updateItem.setTitle("☁️ 从 GitHub 更新题库"), 2000);
                    }
                    Toast.makeText(activity, "更新完成！共下载 " + count + " 个文件", Toast.LENGTH_LONG).show();
                    refreshMenu(); // 刷新列表
                });
            }

            @Override
            public void onFail(String error) {
                activity.runOnUiThread(() -> {
                    if (updateItem != null) updateItem.setTitle("❌ 更新失败，点击重试");
                    Toast.makeText(activity, "错误: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showHelpDialog() {
        // 假设 MarkdownUtils 是您项目中已有的工具类
        CharSequence helpContent = MarkdownUtils.loadMarkdownFromAssets(activity, "help.md");
        AlertDialog dialog = new AlertDialog.Builder(activity)
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
}
