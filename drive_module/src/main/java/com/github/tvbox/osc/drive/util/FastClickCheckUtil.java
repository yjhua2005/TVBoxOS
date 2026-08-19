package com.github.tvbox.osc.drive.util;

import android.view.View;

/**
 * 防止短时间内重复点击
 */
public class FastClickCheckUtil {
    private static final int MIN_CLICK_DELAY_TIME = 500;
    private static long lastClickTime = 0;

    public static boolean check(View view) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime > MIN_CLICK_DELAY_TIME) {
            lastClickTime = currentTime;
            return false;
        }
        return true;
    }
}