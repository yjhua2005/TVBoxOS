package com.github.tvbox.osc.drive.viewmodel;

import android.os.Handler;
import android.os.Looper;

import com.github.tvbox.osc.drive.bean.DriveFolderFile;
import com.github.tvbox.osc.drive.util.DriveOkHttpHelper;
import com.github.tvbox.osc.drive.util.UA;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Alist 网页存储 ViewModel。
 * 已从 OkGo 迁移到标准 OkHttpClient。
 */
public class AlistDriveViewModel extends AbstractDriveViewModel {

    private static final MediaType JSON = MediaType.parse("application/json;charset=UTF-8");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Call currentCall;

    @Override
    public void cancel() {
        if (currentCall != null) {
            currentCall.cancel();
            currentCall = null;
        }
    }

    private Headers.Builder buildHeaders(String origin) {
        Headers.Builder hb = new Headers.Builder()
                .add("User-Agent", UA.random())
                .add("accept", "application/json, text/plain, */*")
                .add("content-type", "application/json;charset=UTF-8");
        if (origin != null && !origin.isEmpty()) {
            if (origin.endsWith("/")) origin = origin.substring(0, origin.length() - 1);
            hb.add("origin", origin);
            hb.add("Referer", origin);
        }
        return hb;
    }

    public final String getUrl(String str) {
        if (str != null) {
            try {
                URL url = new URL(str);
                String str2 = url.getPort() > 0 ? ":" + url.getPort() : "";
                return url.getProtocol() + "://" + url.getHost() + str2;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    @Override
    public String loadData(LoadDataCallback callback) {
        JsonObject config = currentDrive.getConfig();
        if (currentDriveNote == null) {
            currentDriveNote = new DriveFolderFile(null,
                    config.has("initPath") ? config.get("initPath").getAsString() : "", 0, false, null, null);
        }
        String targetPath = currentDriveNote.getAccessingPathStr() + currentDriveNote.name;

        if (currentDriveNote.getChildren() == null) {
            new Thread() {
                public void run() {
                    try {
                        String webLink = getUrl(config.get("url").getAsString());

                        // -------- 检测 Alist 版本 --------
                        if (currentDrive.version == 0) {
                            Request settingsReq = new Request.Builder()
                                    .url(webLink + "/api/public/settings")
                                    .headers(buildHeaders(null).build())
                                    .get()
                                    .build();
                            try (Response settingsResp = DriveOkHttpHelper.getClient().newCall(settingsReq).execute()) {
                                String settingsBody = settingsResp.body() != null ? settingsResp.body().string() : "";
                                JSONObject opt = new JSONObject(settingsBody);
                                Object obj = new JSONTokener(opt.optString("data")).nextValue();
                                if (obj instanceof JSONObject) {
                                    currentDrive.version = 3;
                                } else if (obj instanceof JSONArray) {
                                    currentDrive.version = 2;
                                }
                            }
                        }

                        // -------- Alist v2 --------
                        if (currentDrive.version == 2) {
                            JSONObject requestBody = new JSONObject();
                            requestBody.put("path", targetPath.isEmpty() ? "/" : targetPath);
                            requestBody.put("password", currentDrive.getConfig().get("password").getAsString());
                            requestBody.put("page_num", 1);
                            requestBody.put("page_size", 200);

                            Request request = new Request.Builder()
                                    .url(webLink + "/api/public/path")
                                    .post(RequestBody.create(JSON, requestBody.toString()))
                                    .headers(buildHeaders(webLink).build())
                                    .build();
                            currentCall = DriveOkHttpHelper.getClient().newCall(request);
                            currentCall.enqueue(new Callback() {
                                @Override
                                public void onFailure(Call call, java.io.IOException e) {
                                    mainHandler.post(() -> {
                                        if (callback != null) callback.fail("无法访问，请注意地址格式");
                                    });
                                }

                                @Override
                                public void onResponse(Call call, Response response) throws java.io.IOException {
                                    String respBody = response.body() != null ? response.body().string() : "";
                                    try {
                                        JsonObject respData = JsonParser.parseString(respBody).getAsJsonObject();
                                        List<DriveFolderFile> items = new ArrayList<>();
                                        if (respData.get("code").getAsInt() == 200) {
                                            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                                            for (JsonElement file : respData.get("data").getAsJsonObject().get("files").getAsJsonArray()) {
                                                JsonObject fileObj = file.getAsJsonObject();
                                                String fileName = fileObj.get("name").getAsString();
                                                int extNameStartIndex = fileName.lastIndexOf(".");
                                                boolean isFile = fileObj.get("type").getAsInt() != 1;
                                                String fileUrl = null;
                                                if (fileObj.has("url") && !fileObj.get("url").getAsString().isEmpty())
                                                    fileUrl = fileObj.get("url").getAsString();
                                                try {
                                                    DriveFolderFile driveFile = new DriveFolderFile(currentDriveNote, fileName, currentDrive.version, isFile,
                                                            isFile && extNameStartIndex >= 0 && extNameStartIndex < fileName.length() ?
                                                                    fileName.substring(extNameStartIndex + 1) : null,
                                                            dateFormat.parse(fileObj.get("updated_at").getAsString()).getTime());
                                                    if (fileUrl != null) driveFile.fileUrl = fileUrl;
                                                    items.add(driveFile);
                                                } catch (ParseException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                        sortData(items);
                                        DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
                                        backItem.parentFolder = backItem;
                                        items.add(0, backItem);
                                        currentDriveNote.setChildren(items);
                                        List<DriveFolderFile> finalItems = items;
                                        mainHandler.post(() -> {
                                            if (callback != null) callback.callback(finalItems, false);
                                        });
                                    } catch (Exception ex) {
                                        mainHandler.post(() -> {
                                            if (callback != null) callback.fail("无法访问，请注意地址格式");
                                        });
                                    }
                                }
                            });
                        }

                        // -------- Alist v3 --------
                        else if (currentDrive.version == 3) {
                            JSONObject requestBody = new JSONObject();
                            requestBody.put("path", targetPath.isEmpty() ? "/" : targetPath);
                            requestBody.put("password", currentDrive.getConfig().get("password").getAsString());
                            requestBody.put("page", 1);
                            requestBody.put("per_page", 200);
                            requestBody.put("refresh", false);

                            Request request = new Request.Builder()
                                    .url(webLink + "/api/fs/list")
                                    .post(RequestBody.create(JSON, requestBody.toString()))
                                    .headers(buildHeaders(webLink).build())
                                    .build();
                            currentCall = DriveOkHttpHelper.getClient().newCall(request);
                            currentCall.enqueue(new Callback() {
                                @Override
                                public void onFailure(Call call, java.io.IOException e) {
                                    mainHandler.post(() -> {
                                        if (callback != null) callback.fail("无法访问，请注意地址格式");
                                    });
                                }

                                @Override
                                public void onResponse(Call call, Response response) throws java.io.IOException {
                                    String respBody = response.body() != null ? response.body().string() : "";
                                    try {
                                        JsonObject respData = JsonParser.parseString(respBody).getAsJsonObject();
                                        List<DriveFolderFile> items = new ArrayList<>();
                                        if (respData.get("code").getAsInt() == 200) {
                                            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                                            for (JsonElement file : respData.get("data").getAsJsonObject().get("content").getAsJsonArray()) {
                                                JsonObject fileObj = file.getAsJsonObject();
                                                String fileName = fileObj.get("name").getAsString();
                                                int extNameStartIndex = fileName.lastIndexOf(".");
                                                boolean isFile = !fileObj.get("is_dir").getAsBoolean();

                                                try {
                                                    DriveFolderFile driveFile = new DriveFolderFile(currentDriveNote, fileName, currentDrive.version, isFile,
                                                            isFile && extNameStartIndex >= 0 && extNameStartIndex < fileName.length() ?
                                                                    fileName.substring(extNameStartIndex + 1) : null,
                                                            dateFormat.parse(fileObj.get("modified").getAsString()).getTime());
                                                    items.add(driveFile);
                                                } catch (ParseException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                        sortData(items);
                                        DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
                                        backItem.parentFolder = backItem;
                                        items.add(0, backItem);
                                        currentDriveNote.setChildren(items);
                                        List<DriveFolderFile> finalItems = items;
                                        mainHandler.post(() -> {
                                            if (callback != null) callback.callback(finalItems, false);
                                        });
                                    } catch (Exception ex) {
                                        mainHandler.post(() -> {
                                            if (callback != null) callback.fail("无法访问，请注意地址格式");
                                        });
                                    }
                                }
                            });
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        mainHandler.post(() -> {
                            if (callback != null) callback.fail("无法访问，请注意地址格式");
                        });
                    }
                }
            }.start();
            return targetPath;
        } else {
            sortData(currentDriveNote.getChildren());
            if (callback != null)
                callback.callback(currentDriveNote.getChildren(), true);
        }
        return targetPath;
    }

    public void loadFile(DriveFolderFile targetFile, LoadFileCallback callback) {
        JsonObject config = currentDrive.getConfig();
        String webLink = getUrl(config.get("url").getAsString());
        String targetPath = targetFile.getAccessingPathStr() + targetFile.name;
        try {
            if (callback != null) {
                if (targetFile.fileUrl != null && !targetFile.fileUrl.isEmpty()) {
                    callback.callback(targetFile.fileUrl);
                } else {
                    callback.callback(URLDecoder.decode(webLink + "/d" + targetPath, "UTF-8"));
                }
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            callback.fail(e.getMessage());
        }
    }

    public interface LoadFileCallback {
        void callback(String fileUrl);
        void fail(String msg);
    }
}