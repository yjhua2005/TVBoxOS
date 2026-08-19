package com.github.tvbox.toolbar.util;

import android.view.View;

public class FastClickCheckUtil {
    private static final int INTERVAL = 500;
    private static volatile long lastClickTime = 0;

    public static void check(View view) {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < INTERVAL) {
            view.setClickable(false);
            view.postDelayed(new Runnable() {
                @Override
                public void run() {
                    view.setClickable(true);
                }
            }, INTERVAL);
        } else {
            lastClickTime = now;
        }
    }
}