package com.github.tvbox.osc.drive.event;

/**
 * 模块内部事件，替代原项目的 RefreshEvent / InputMsgEvent
 */
public class DriveEvent {

    /** 刷新存储盘列表 */
    public static final int TYPE_DRIVE_REFRESH = 1000;

    public final int type;

    public DriveEvent(int type) {
        this.type = type;
    }
}