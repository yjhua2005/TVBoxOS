package com.github.tvbox.osc.server;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 响应按键和输入
 */

public class InputRequestProcess implements RequestProcess {
    private RemoteServer remoteServer;

    public InputRequestProcess(RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        if (session.getMethod() == NanoHTTPD.Method.POST) {
            switch (fileName) {
                case "/action":
                    return true;
            }
        }
        return false;
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        DataReceiver mDataReceiver = remoteServer.getDataReceiver();
        switch (fileName) {
            case "/action":
                if (params.get("do") != null && mDataReceiver != null) {
                    String action = params.get("do");

                    switch (action) {
                        case "search": {
                            mDataReceiver.onTextReceived(params.get("word").trim());
                            break;
                        }
                        case "api": {
                            mDataReceiver.onApiReceived(params.get("url").trim());
                            break;
                        }
                        case "liveApi": {
                            mDataReceiver.onLiveApiReceived(params.get("url").trim());
                            break;
                        }
                        case "danmuApi": {
                            mDataReceiver.onDanmuApiReceived(params.get("url").trim());
                            break;
                        }
                        case "push": {
                            String url = params.get("url");
                            if (url != null && url.trim().length() > 0) {
                                mDataReceiver.onPushReceived(url.trim());
                            }
                            break;
                        }
                        case "pushStore": {
                            String storeName = params.get("pushStore_name");
                            String storeUrl = params.get("pushStore_url");
                            if (storeUrl != null && storeUrl.trim().length() > 0) {
                                mDataReceiver.onPushStoreReceived(
                                    storeName != null ? storeName.trim() : "",
                                    storeUrl.trim()
                                );
                            }
                            break;
                        }
                        case "livePush": {
                            String liveName = params.get("live_name");
                            String liveAddress = params.get("live_address");
                            if (liveAddress != null && liveAddress.trim().length() > 0) {
                                mDataReceiver.onLivePushReceived(
                                    liveName != null ? liveName.trim() : "",
                                    liveAddress.trim()
                                );
                            }
                            break;
                        }
                    }
                }
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok");
            default:
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found.");
        }
    }
}
