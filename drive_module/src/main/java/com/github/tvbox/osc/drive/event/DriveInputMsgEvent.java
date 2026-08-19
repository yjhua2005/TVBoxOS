package com.github.tvbox.osc.drive.event;

/**
 * 模块内部输入事件，替代原项目的 InputMsgEvent
 */
public class DriveInputMsgEvent {
    private final String text;

    public DriveInputMsgEvent(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}