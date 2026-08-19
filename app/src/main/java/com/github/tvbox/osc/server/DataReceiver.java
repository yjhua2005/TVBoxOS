package com.github.tvbox.osc.server;

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
public interface DataReceiver {

    /**
     * @param text
     */
    void onTextReceived(String text);


    void onApiReceived(String url);

    void onLiveApiReceived(String url);

    void onDanmuApiReceived(String url);

    void onPushReceived(String url);

    void onPushStoreReceived(String name, String url);

    void onLivePushReceived(String name, String address);
}
