package com.github.tvbox.osc.drive;

import android.content.Context;

import com.github.tvbox.osc.drive.callback.DriveCallback;

/**
 * 存储空间模块的入口类。
 * <p>
 * 模块在 DriveActivity.onCreate() 中自动调用 {@link #init(Context)} 进行延迟初始化，
 * 宿主 App 无需在 Application.onCreate() 中做任何调用。
 * <p>
 * 宿主集成步骤（低耦合模式）：
 * <pre>
 *   DriveModule.init(this);                              // 1. 延迟初始化（也可不调用，DriveActivity 内部会调）
 *   DriveModule.setDriveCallback(new DriveCallback() {   // 2. 注入播放回调
 *       &#64;Override public void onPlayFile(String name, String url, String headers, String playFlag) {
 *           // 用宿主自己的播放器播放 url
 *       }
 *       &#64;Override public int getThemeColor() { return 0xFFD81F26; }
 *   });
 *   startActivity(new Intent(this, DriveActivity.class)); // 3. 启动浏览界面
 * </pre>
 * <p>
 * 兼容说明：若宿主未注入 {@link DriveCallback}，DriveActivity 会回退到
 * {@code setResult + finish} 的隐式 Intent 返回模式，宿主可通过
 * {@code startActivityForResult} 接收播放信息，两套接口二选一即可。
 */
public final class DriveModule {

    /** 启动 DriveActivity 的 Action 常量，宿主可直接引用或使用字符串字面量 */
    public static final String ACTION_OPEN = "com.github.tvbox.osc.drive.ACTION_OPEN";

    private static volatile boolean initialized = false;
    private static Context appContext;
    /** 宿主注入的播放回调，可为 null（表示走 setResult 模式） */
    private static volatile DriveCallback driveCallback;

    private DriveModule() {
    }

    /**
     * 初始化模块（数据库、网络客户端等）。
     * 支持重复调用（幂等），由 DriveActivity.onCreate() 自动调用。
     *
     * @param context Application Context
     */
    public static void init(Context context) {
        if (initialized) return;
        appContext = context.getApplicationContext();
        com.github.tvbox.osc.drive.data.DriveDataManager.init(appContext);
        com.github.tvbox.osc.drive.util.DriveOkHttpHelper.init(appContext);
        initialized = true;
    }

    public static Context getAppContext() {
        return appContext;
    }

    /**
     * 注入宿主播放回调。在启动 DriveActivity 之前调用。
     * 传 null 可清除已注入的回调，DriveActivity 会回退到 setResult 模式。
     *
     * @param callback 宿主实现的 {@link DriveCallback}，可为 null
     */
    public static void setDriveCallback(DriveCallback callback) {
        driveCallback = callback;
    }

    /**
     * 获取宿主注入的播放回调。供 DriveActivity 内部调用。
     *
     * @return 已注入的回调，未注入时返回 null
     */
    public static DriveCallback getDriveCallback() {
        return driveCallback;
    }
}
