package com.example.hajimi24;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

public class GameTimer {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private long startTime;
    private final Runnable onTickCallback; // 每次跳秒时执行的回调

    public GameTimer(Runnable onTickCallback) {
        this.onTickCallback = onTickCallback;
    }

    public void start() {
        stop(); // 防止重复启动
        startTime = System.currentTimeMillis();
        runnable = new Runnable() {
            @Override
            public void run() {
                if (onTickCallback != null) {
                    onTickCallback.run();
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(runnable);
    }

    public void stop() {
        if (runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }
    // 在 GameTimer.java 中添加以下方法
    public void reset() {
        this.startTime = System.currentTimeMillis();
        // 立即执行一次回调，让 UI 瞬间刷新显示 0s
        if (onTickCallback != null) {
            onTickCallback.run();
        }
    }

    // 获取当前这局游戏开始了多少秒
    public long getElapsedSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }
}
