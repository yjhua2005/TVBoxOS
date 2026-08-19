package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.MoreSourceBean;
import com.github.tvbox.osc.ui.adapter.LineSelectAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 线路切换对话框（从主界面直接打开，参照配置对话框的模式）
 * <p>
 * 将 ModelSettingFragment 中的线路切换逻辑（showLineSwitchDialog / fetchStoreLinesAndShow /
 * showLineSelectDialog / showLineSelectDialogWithCacheInfo 等方法）提取为独立 Dialog，
 * 使主界面的"线路"按钮可以像"配置"按钮一样直接弹出小对话框，而无需跳转设置页。
 */
public class LineSwitchDialog extends BaseDialog {

    private Activity mActivity;
    private long currentLineCacheTime = -1;
    private String currentLineCacheKey = "";

    /** 线路切换完成后的回调（可选），用于通知宿主刷新UI */
    public interface OnLineSwitchedListener {
        void onLineSwitched(String newApiUrl);
    }

    private OnLineSwitchedListener mListener;

    public LineSwitchDialog(@NonNull Activity activity) {
        super(activity);
        mActivity = activity;
    }

    public void setOnLineSwitchedListener(OnLineSwitchedListener listener) {
        mListener = listener;
    }

    /**
     * 不显示自身窗口，直接触发内层对话框
     * <p>
     * LineSwitchDialog 本身没有 contentView，只是逻辑调度器，
     * 调用 super.show() 会创建空窗口导致需要按两次返回键才能关闭。
     */
    @Override
    public void show() {
        if (mActivity.isFinishing()
                || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1
                && mActivity.isDestroyed())) {
            return;
        }
        showLineSwitchDialog();
    }

    // ===================== 线路切换主入口（参照 ModelSettingFragment.showLineSwitchDialog） =====================

    /**
     * 线路切换对话框主逻辑
     * 1. 如果有多仓配置，先展示多仓选择列表
     * 2. 选中仓库后，从仓库URL动态获取线路列表
     * 3. 展示线路选择对话框
     */
    private void showLineSwitchDialog() {
        ArrayList<MoreSourceBean> storeList = getStoreHouseList();
        ArrayList<MoreSourceBean> lineHistory = getApiHistoryList();

        // 将仓库中本地保存的线路合并到历史列表（参照ysc y11.OooO0O0）
        // 注意：只合并线路用于展示，不删除仓库（仓库保留在列表中供用户切换）
        if (!storeList.isEmpty()) {
            for (MoreSourceBean store : storeList) {
                if (store.getLocalLineUrls() != null) {
                    lineHistory.addAll(store.getLocalLineUrls());
                }
            }
        }

        // 如果历史线路列表为空且没有仓库配置
        if (lineHistory.isEmpty() && storeList.isEmpty()) {
            ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
            if (!apiLines.isEmpty()) {
                ArrayList<MoreSourceBean> lineBeans = new ArrayList<>();
                for (String line : apiLines) {
                    MoreSourceBean bean = new MoreSourceBean();
                    bean.setSourceName(HistoryHelper.getApiLineName(line));
                    bean.setSourceUrl(HistoryHelper.getApiLineUrl(line));
                    bean.setShowDelete(true);
                    lineBeans.add(bean);
                }
                showLineSelectDialog(lineBeans);
                return;
            }
            MoreSourceBean selectedStore = getSelectedStoreHouse();
            if (selectedStore == null || selectedStore.getSourceUrl() == null || selectedStore.getSourceUrl().isEmpty()) {
                // 线路为空且没有选中仓库 → 弹出多仓配置对话框
                showStoreHouseDialog();
                return;
            }
        }

        // 获取当前选中的仓库
        MoreSourceBean selectedStore = getSelectedStoreHouse();

        // 如果有选中的仓库，直接从该仓库获取线路
        if (selectedStore != null && selectedStore.getSourceUrl() != null && !selectedStore.getSourceUrl().isEmpty()) {
            fetchStoreLinesAndShow(selectedStore);
            return;
        }

        // 如果有历史线路但没有选中仓库，直接展示历史线路
        if (!lineHistory.isEmpty()) {
            for (MoreSourceBean bean : lineHistory) {
                bean.setShowDelete(true);
            }
            showLineSelectDialog(lineHistory);
            return;
        }

        // 如果有仓库列表，显示仓库选择对话框
        if (!storeList.isEmpty()) {
            showStoreSelectDialog(storeList);
            return;
        }

        // 兜底：尝试从 STORE_API 获取
        String storeApi = Hawk.get(HawkConfig.STORE_API, "");
        if (!storeApi.isEmpty()) {
            MoreSourceBean storeBean = new MoreSourceBean();
            storeBean.setSourceName(Hawk.get(HawkConfig.STORE_API_NAME, ""));
            storeBean.setSourceUrl(storeApi);
            fetchStoreLinesAndShow(storeBean);
        } else {
            showStoreHouseDialog();
        }
    }

    // ===================== 仓库/线路选择对话框 =====================

    /**
     * 显示仓库选择对话框
     */
    private void showStoreSelectDialog(ArrayList<MoreSourceBean> storeList) {
        String selectedUrl = Hawk.get(HawkConfig.STORE_API, "");
        int idx = 0;
        for (int i = 0; i < storeList.size(); i++) {
            if (selectedUrl.equals(storeList.get(i).getSourceUrl())) {
                idx = i;
                break;
            }
        }
        SelectDialog<MoreSourceBean> dialog = new SelectDialog<>(mActivity);
        dialog.setTip("选择仓库");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<MoreSourceBean>() {
            @Override
            public void click(MoreSourceBean value, int pos) {
                dialog.dismiss();
                saveSelectedStoreHouse(value);
                Hawk.put(HawkConfig.STORE_API, value.getSourceUrl());
                if (value.getSourceName() != null && !value.getSourceName().isEmpty()) {
                    Hawk.put(HawkConfig.STORE_API_NAME, value.getSourceName());
                }
                Toast.makeText(mActivity, "正在获取线路，请稍候...", Toast.LENGTH_SHORT).show();
                fetchStoreLinesAndShow(value);
            }

            @Override
            public String getDisplay(MoreSourceBean val) {
                String name = val.getSourceName();
                if (name == null || name.isEmpty()) {
                    name = val.getSourceUrl();
                }
                return name;
            }
        }, new DiffUtil.ItemCallback<MoreSourceBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull MoreSourceBean oldItem, @NonNull @NotNull MoreSourceBean newItem) {
                return oldItem.getUniKey().equals(newItem.getUniKey());
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull MoreSourceBean oldItem, @NonNull @NotNull MoreSourceBean newItem) {
                return oldItem.getUniKey().equals(newItem.getUniKey());
            }
        }, storeList, idx);
        dialog.show();
    }

    /**
     * 显示多仓配置对话框（回退到 MoreSourceDialog）
     */
    private void showStoreHouseDialog() {
        try {
            MoreSourceDialog dialog = new MoreSourceDialog(mActivity);
            dialog.setOnStoreSelectedListener(new MoreSourceDialog.OnStoreSelectedListener() {
                @Override
                public void onStoreSelected(MoreSourceBean bean) {
                    // 选中仓库后，获取线路并显示
                    Toast.makeText(mActivity, "正在获取线路，请稍候...", Toast.LENGTH_SHORT).show();
                    fetchStoreLinesAndShow(bean);
                }
            });
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示线路选择对话框（参照 ModelSettingFragment.showLineSelectDialog）
     */
    private void showLineSelectDialog(ArrayList<MoreSourceBean> lineBeans) {
        String current = Hawk.get(HawkConfig.API_URL, "");
        int idx = 0;
        for (int i = 0; i < lineBeans.size(); i++) {
            if (current.equals(lineBeans.get(i).getSourceUrl())) {
                idx = i;
                break;
            }
        }
        final int selectIdx = idx;
        SelectDialog<MoreSourceBean> dialog = new SelectDialog<>(mActivity);
        dialog.setTip("选择线路");
        final LineSelectAdapter[] adapterHolder = new LineSelectAdapter[1];
        adapterHolder[0] = new LineSelectAdapter(new LineSelectAdapter.OnLineClickListener() {
            @Override
            public void onLineClick(MoreSourceBean bean, int position) {
                String newApi = bean.getSourceUrl();
                String oldApi = Hawk.get(HawkConfig.API_URL, "");
                if (newApi == null || newApi.isEmpty()) return;
                Hawk.put(HawkConfig.API_URL, newApi);
                Hawk.put(HawkConfig.LIVE_API_URL, newApi);
                HistoryHelper.setLiveApiHistory(newApi);
                HistoryHelper.setApiHistory(newApi);
                dialog.dismiss();
                if (!oldApi.equals(newApi)) {
                    restartAppAfterConfigChanged();
                }
                if (mListener != null) {
                    mListener.onLineSwitched(newApi);
                }
            }

            @Override
            public void onLineDelete(MoreSourceBean bean, int position) {
                ArrayList<MoreSourceBean> historyList = getApiHistoryList();
                for (int i = 0; i < historyList.size(); i++) {
                    if (bean.getSourceUrl() != null && bean.getSourceUrl().equals(historyList.get(i).getSourceUrl())) {
                        historyList.remove(i);
                        break;
                    }
                }
                saveApiHistoryList(historyList);
                ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
                for (int i = 0; i < apiLines.size(); i++) {
                    if (bean.getSourceUrl() != null && bean.getSourceUrl().equals(HistoryHelper.getApiLineUrl(apiLines.get(i)))) {
                        apiLines.remove(i);
                        break;
                    }
                }
                Hawk.put(HawkConfig.API_LINE_LIST, apiLines);
                adapterHolder[0].getData().remove(position);
                adapterHolder[0].notifyItemRemoved(position);
            }
        });
        LineSelectAdapter adapter = adapterHolder[0];
        adapter.setData(lineBeans, selectIdx);
        TvRecyclerView tvRecyclerView = dialog.findViewById(R.id.list);
        tvRecyclerView.setAdapter(adapter);
        tvRecyclerView.setSelectedPosition(selectIdx);
        if (selectIdx < 10) {
            tvRecyclerView.setSelection(selectIdx);
        }
        tvRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                if (selectIdx >= 10) {
                    tvRecyclerView.smoothScrollToPosition(selectIdx);
                    tvRecyclerView.setSelectionWithSmooth(selectIdx);
                }
            }
        });
        dialog.show();
    }

    /**
     * 显示带缓存信息的线路选择对话框
     */
    private void showLineSelectDialogWithCacheInfo(ArrayList<MoreSourceBean> lineBeans) {
        String current = Hawk.get(HawkConfig.API_URL, "");
        int idx = 0;
        for (int i = 0; i < lineBeans.size(); i++) {
            if (current.equals(lineBeans.get(i).getSourceUrl())) {
                idx = i;
                break;
            }
        }
        final int selectIdx = idx;
        SelectDialog<MoreSourceBean> dialog = new SelectDialog<>(mActivity);
        String cacheInfoTitle = buildCacheInfoTitle();
        dialog.setTip(cacheInfoTitle);
        final LineSelectAdapter[] adapterHolder = new LineSelectAdapter[1];
        adapterHolder[0] = new LineSelectAdapter(new LineSelectAdapter.OnLineClickListener() {
            @Override
            public void onLineClick(MoreSourceBean bean, int position) {
                String newApi = bean.getSourceUrl();
                String oldApi = Hawk.get(HawkConfig.API_URL, "");
                if (newApi == null || newApi.isEmpty()) return;
                Hawk.put(HawkConfig.API_URL, newApi);
                Hawk.put(HawkConfig.LIVE_API_URL, newApi);
                HistoryHelper.setLiveApiHistory(newApi);
                HistoryHelper.setApiHistory(newApi);
                dialog.dismiss();
                if (!oldApi.equals(newApi)) {
                    restartAppAfterConfigChanged();
                }
                if (mListener != null) {
                    mListener.onLineSwitched(newApi);
                }
            }

            @Override
            public void onLineDelete(MoreSourceBean bean, int position) {
                ArrayList<MoreSourceBean> historyList = getApiHistoryList();
                for (int i = 0; i < historyList.size(); i++) {
                    if (bean.getSourceUrl() != null && bean.getSourceUrl().equals(historyList.get(i).getSourceUrl())) {
                        historyList.remove(i);
                        break;
                    }
                }
                saveApiHistoryList(historyList);
                ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
                for (int i = 0; i < apiLines.size(); i++) {
                    if (bean.getSourceUrl() != null && bean.getSourceUrl().equals(HistoryHelper.getApiLineUrl(apiLines.get(i)))) {
                        apiLines.remove(i);
                        break;
                    }
                }
                Hawk.put(HawkConfig.API_LINE_LIST, apiLines);
                adapterHolder[0].getData().remove(position);
                adapterHolder[0].notifyItemRemoved(position);
            }
        });
        LineSelectAdapter adapter = adapterHolder[0];
        adapter.setData(lineBeans, selectIdx);
        TvRecyclerView tvRecyclerView = dialog.findViewById(R.id.list);
        tvRecyclerView.setAdapter(adapter);
        tvRecyclerView.setSelectedPosition(selectIdx);
        if (selectIdx < 10) {
            tvRecyclerView.setSelection(selectIdx);
        }
        tvRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                if (selectIdx >= 10) {
                    tvRecyclerView.smoothScrollToPosition(selectIdx);
                    tvRecyclerView.setSelectionWithSmooth(selectIdx);
                }
            }
        });
        dialog.show();
    }

    // ===================== 网络获取线路 =====================

    /**
     * 从仓库URL获取线路并显示线路选择对话框
     */
    private void fetchStoreLinesAndShow(MoreSourceBean storeBean) {
        String storeUrl = storeBean.getSourceUrl();
        if (storeUrl == null || storeUrl.isEmpty()) {
            Toast.makeText(mActivity, "仓库地址为空", Toast.LENGTH_SHORT).show();
            return;
        }
        // 复用 ApiConfig.configUrl() 统一处理URL（file://转换、;pk;提取、clan://转换、http补全）
        // 与 MoreSourceDialog.addStoreToLayout 保持一致的URL预处理
        ApiConfig apiConfig = ApiConfig.get();
        final String rawUrl = storeUrl;
        String fetchUrl = apiConfig.configUrl(storeUrl);
        final String configKey = apiConfig.getTempKey();

        final String finalFetchUrl = fetchUrl;

        String cacheKey = "LINE_KEY" + fetchUrl;
        currentLineCacheKey = cacheKey;

        int cacheDays = Hawk.get(HawkConfig.CACHE_TIME, 1);
        if (cacheDays == -1) {
            currentLineCacheTime = -1L;
        } else {
            currentLineCacheTime = (long) cacheDays * 24 * 60 * 60 * 1000;
        }

        // -1 URL特殊处理
        if (finalFetchUrl.contains("-1")) {
            handleMinusOneUrl(storeBean, finalFetchUrl, configKey, rawUrl);
            return;
        }

        // ========== 检查本地缓存（参照ysc: FIRST_CACHE_THEN_REQUEST模式） ==========
        // 本地地址(127.0.0.1)不使用缓存（参照ysc: !b51.OooooOO(strClanToAddress, "http://127.0.0.1"））
        boolean useCache = !finalFetchUrl.startsWith("http://127.0.0.1") && cacheDays != 0;

        if (useCache) {
            String cachedBody = readLineCache(cacheKey);
            if (cachedBody != null && !cachedBody.isEmpty()) {
                boolean cacheValid = isLineCacheValid(cacheKey, currentLineCacheTime);
                if (cacheValid) {
                    // 缓存有效，解密后再使用（缓存存的是原始响应）
                    String decryptedCache = ApiConfig.FindResult(cachedBody, configKey);
                    if (rawUrl != null && rawUrl.startsWith("clan")) {
                        decryptedCache = ApiConfig.get().clanContentFix(finalFetchUrl, decryptedCache);
                    }
                    decryptedCache = ApiConfig.get().fixContentPath(rawUrl != null ? rawUrl : finalFetchUrl, decryptedCache);
                    parseAndShowLinesFromJson(decryptedCache, finalFetchUrl, storeBean, configKey, rawUrl);
                    fetchLineFromNetwork(finalFetchUrl, storeBean, cacheKey, currentLineCacheTime, true, configKey, rawUrl);
                    return;
                }
            }
        }

        // 无缓存或缓存无效，直接请求网络
        fetchLineFromNetwork(finalFetchUrl, storeBean, cacheKey, currentLineCacheTime, false, configKey, rawUrl);
    }

    /**
     * 从网络获取线路
     */
    private void fetchLineFromNetwork(final String fetchUrl, final MoreSourceBean storeBean,
                                        final String cacheKey, final long cacheTimeMs,
                                        final boolean isBackgroundUpdate,
                                        final String configKey, final String rawUrl) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                            .followRedirects(true).followSslRedirects(true).build();
                    okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder().url(fetchUrl);
                    if (fetchUrl.startsWith("https://gitcode")) {
                        requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                        requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                    } else {
                        requestBuilder.header("User-Agent", "Mozilla/5.0");
                    }
                    okhttp3.Response response = client.newCall(requestBuilder.build()).execute();
                    if (response.body() != null) {
                        String body = response.body().string();
                        response.close();
                        // 写入缓存（缓存原始响应，解密后缓存无意义）
                        if (cacheTimeMs != 0 && !fetchUrl.startsWith("http://127.0.0.1")) {
                            writeLineCache(cacheKey, body, cacheTimeMs);
                        }
                        // 复用 ApiConfig.FindResult 解密 + 后处理（与 MoreSourceDialog.addStoreToLayout 完全一致）
                        ApiConfig apiConfig = ApiConfig.get();
                        body = ApiConfig.FindResult(body, configKey);
                        if (rawUrl != null && rawUrl.startsWith("clan")) {
                            body = apiConfig.clanContentFix(fetchUrl, body);
                        }
                        body = apiConfig.fixContentPath(rawUrl != null ? rawUrl : fetchUrl, body);
                        if (isBackgroundUpdate) {
                            try {
                                org.json.JSONObject jsonObject = new org.json.JSONObject(body);
                                if (jsonObject.has("urls")) {
                                    org.json.JSONArray urls = jsonObject.getJSONArray("urls");
                                    ArrayList<MoreSourceBean> lines = new ArrayList<>();
                                    for (int i = 0; i < urls.length(); i++) {
                                        org.json.JSONObject item = urls.getJSONObject(i);
                                        MoreSourceBean lb = new MoreSourceBean();
                                        lb.setSourceUrl(item.optString("url", ""));
                                        lb.setSourceName(item.optString("name", ""));
                                        lines.add(lb);
                                    }
                                    storeBean.setLocalLineUrls(lines);
                                    saveStoreHouseList(getStoreHouseList());
                                }
                            } catch (Exception ignored) {}
                        } else {
                            parseAndShowLinesFromJson(body, fetchUrl, storeBean, configKey, rawUrl);
                        }
                    } else {
                        response.close();
                        if (!isBackgroundUpdate) {
                            String cachedBody = readLineCache(cacheKey);
                            if (cachedBody != null && !cachedBody.isEmpty()) {
                                mActivity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(mActivity, "网络失败，使用缓存数据", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                // 缓存数据也需要解密
                                String decrypted = ApiConfig.FindResult(cachedBody, configKey);
                                if (rawUrl != null && rawUrl.startsWith("clan")) {
                                    decrypted = ApiConfig.get().clanContentFix(fetchUrl, decrypted);
                                }
                                decrypted = ApiConfig.get().fixContentPath(rawUrl != null ? rawUrl : fetchUrl, decrypted);
                                parseAndShowLinesFromJson(decrypted, fetchUrl, storeBean, configKey, rawUrl);
                            } else {
                                showLineFetchError(null);
                            }
                        }
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    if (!isBackgroundUpdate) {
                        String cachedBody = readLineCache(cacheKey);
                        if (cachedBody != null && !cachedBody.isEmpty()) {
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mActivity, "网络异常，使用缓存数据", Toast.LENGTH_SHORT).show();
                                }
                            });
                            String decrypted = ApiConfig.FindResult(cachedBody, configKey);
                            if (rawUrl != null && rawUrl.startsWith("clan")) {
                                decrypted = ApiConfig.get().clanContentFix(fetchUrl, decrypted);
                            }
                            decrypted = ApiConfig.get().fixContentPath(rawUrl != null ? rawUrl : fetchUrl, decrypted);
                            parseAndShowLinesFromJson(decrypted, fetchUrl, storeBean, configKey, rawUrl);
                        } else {
                            showLineFetchError(e);
                        }
                    }
                }
            }
        }).start();
    }

    /**
     * -1 URL特殊处理
     */
    private void handleMinusOneUrl(final MoreSourceBean storeBean, final String fetchUrl, final String configKey, final String rawUrl) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                            .followRedirects(true).followSslRedirects(true).build();
                    okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder().url(fetchUrl);
                    if (fetchUrl.startsWith("https://gitcode")) {
                        requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                        requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                    } else {
                        requestBuilder.header("User-Agent", "Mozilla/5.0");
                    }
                    okhttp3.Response response = client.newCall(requestBuilder.build()).execute();
                    boolean success = response.isSuccessful();
                    String body = null;
                    if (response.body() != null) {
                        body = response.body().string();
                        response.close();
                    }
                    if (body != null && !body.isEmpty()) {
                        // 复用 ApiConfig.FindResult 解密
                        body = ApiConfig.FindResult(body, configKey);
                        if (rawUrl != null && rawUrl.startsWith("clan")) {
                            body = ApiConfig.get().clanContentFix(fetchUrl, body);
                        }
                        body = ApiConfig.get().fixContentPath(rawUrl != null ? rawUrl : fetchUrl, body);
                        try {
                            final String finalBody = body;
                            org.json.JSONObject jsonObject = new org.json.JSONObject(finalBody);
                            if (jsonObject.has("urls")) {
                                final boolean finalSuccess = success;
                                mActivity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!finalSuccess) {
                                            currentLineCacheTime = 0;
                                        }
                                        showLineSelectDialogWithCacheInfo(parseUrlsFromJson(finalBody));
                                    }
                                });
                                return;
                            }
                        } catch (Exception ignored) {}
                    }
                    if (!mActivity.isFinishing()) {
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mActivity, "线路获取失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    String cachedBody = readLineCache(currentLineCacheKey);
                    if (cachedBody != null && !cachedBody.isEmpty()) {
                        String decrypted = ApiConfig.FindResult(cachedBody, configKey);
                        final String finalDecrypted = decrypted;
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                currentLineCacheTime = 0;
                                showLineSelectDialogWithCacheInfo(parseUrlsFromJson(finalDecrypted));
                            }
                        });
                    } else {
                        showLineFetchError(e);
                    }
                }
            }
        }).start();
    }

    /**
     * 解析JSON并显示线路选择
     */
    private void parseAndShowLinesFromJson(String body, String fetchUrl, MoreSourceBean storeBean, String configKey, String rawUrl) {
        ArrayList<String> apiLines = parseStoreLines(body);
        // 检测是否为storeHouse格式（递归标记）
        if (apiLines.size() == 1) {
            String lineName = HistoryHelper.getApiLineName(apiLines.get(0));
            String lineUrl = HistoryHelper.getApiLineUrl(apiLines.get(0));
            if ((lineName == null || lineName.isEmpty()) && lineUrl != null && !lineUrl.isEmpty()) {
                final String redirectUrl = lineUrl;
                if (mActivity != null && !mActivity.isFinishing()) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MoreSourceBean firstStore = new MoreSourceBean();
                            firstStore.setSourceUrl(redirectUrl);
                            fetchStoreLinesAndShow(firstStore);
                        }
                    });
                }
                return;
            }
        }
        // urls格式：合并线路
        ArrayList<MoreSourceBean> historyList = getApiHistoryList();
        ArrayList<MoreSourceBean> allLines = new ArrayList<>();
        try {
            org.json.JSONObject jsonObject = new org.json.JSONObject(body);
            if (jsonObject.has("urls")) {
                org.json.JSONArray urls = jsonObject.getJSONArray("urls");
                for (int i = 0; i < urls.length(); i++) {
                    org.json.JSONObject item = urls.getJSONObject(i);
                    MoreSourceBean lineBean = new MoreSourceBean();
                    lineBean.setSourceUrl(item.optString("url", ""));
                    lineBean.setSourceName(item.optString("name", ""));
                    lineBean.setShowDelete(true);
                    allLines.add(lineBean);
                }
            }
        } catch (Exception ignored) {}
        // 合并历史线路中不重复的
        for (MoreSourceBean historyBean : historyList) {
            boolean found = false;
            for (MoreSourceBean lineBean : allLines) {
                if (historyBean.getSourceUrl() != null && historyBean.getSourceUrl().equals(lineBean.getSourceUrl())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (historyBean.getSourceName() == null || historyBean.getSourceName().isEmpty()) {
                    historyBean.setSourceName("自定义配置地址" + (allLines.size() + 1));
                }
                historyBean.setShowDelete(true);
                allLines.add(historyBean);
            }
        }
        // 保存localLineUrls到storeBean
        ArrayList<MoreSourceBean> lineUrlsOnly = new ArrayList<>();
        for (MoreSourceBean line : allLines) {
            if (line.getSourceUrl() != null && !line.getSourceUrl().isEmpty()) {
                MoreSourceBean copy = new MoreSourceBean();
                copy.setSourceUrl(line.getSourceUrl());
                copy.setSourceName(line.getSourceName());
                lineUrlsOnly.add(copy);
            }
        }
        storeBean.setLocalLineUrls(lineUrlsOnly);
        // 更新仓库列表中的localLineUrls
        ArrayList<MoreSourceBean> storeList = getStoreHouseList();
        for (int i = 0; i < storeList.size(); i++) {
            if (storeList.get(i).getUniKey().equals(storeBean.getUniKey())) {
                storeList.set(i, storeBean);
                break;
            }
        }
        saveStoreHouseList(storeList);
        // 保存到API_LINE_LIST
        ArrayList<String> finalLines = new ArrayList<>();
        for (MoreSourceBean bean : allLines) {
            if (bean.getSourceUrl() != null && !bean.getSourceUrl().isEmpty()) {
                finalLines.add(HistoryHelper.buildApiLine(bean.getSourceName(), bean.getSourceUrl()));
            }
        }
        if (!finalLines.isEmpty()) {
            Hawk.put(HawkConfig.API_LINE_LIST, finalLines);
            Hawk.put(HawkConfig.API_LINE_SOURCE, fetchUrl);
        }
        // 在主线程显示对话框
        if (mActivity != null && !mActivity.isFinishing()) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (allLines.isEmpty()) {
                        // urls和storeHouse都没检测到，判断是否为直接配置（sites格式等）
                        // 如果body包含有效JSON且不是空对象，说明该URL本身就是一条线路
                        try {
                            org.json.JSONObject checkObj = new org.json.JSONObject(body);
                            boolean isDirectConfig = checkObj.has("sites") || checkObj.has("spider")
                                    || checkObj.has("lives") || checkObj.has("wallpaper");
                            if (isDirectConfig) {
                                // 该URL是直接配置线路，用其自身作为唯一线路
                                MoreSourceBean directLine = new MoreSourceBean();
                                directLine.setSourceUrl(rawUrl != null ? rawUrl : fetchUrl);
                                directLine.setSourceName(storeBean.getSourceName() != null && !storeBean.getSourceName().isEmpty()
                                        ? storeBean.getSourceName() : "直接配置");
                                directLine.setShowDelete(true);
                                allLines.add(directLine);
                                storeBean.setLocalLineUrls(allLines);
                                Hawk.put(HawkConfig.API_URL, directLine.getSourceUrl());
                                Hawk.put(HawkConfig.API_LINE_SOURCE, fetchUrl);
                                // 直接加载该配置并重启
                                HistoryHelper.setApiHistory(directLine.getSourceUrl());
                                restartAppAfterConfigChanged();
                                return;
                            }
                        } catch (Exception ignored) {}
                        Toast.makeText(mActivity, "未获取到线路，请检查仓库地址", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showLineSelectDialogWithCacheInfo(allLines);
                }
            });
        }
    }

    // ===================== JSON解析辅助 =====================

    /**
     * 从JSON解析urls为MoreSourceBean列表
     */
    private ArrayList<MoreSourceBean> parseUrlsFromJson(String body) {
        ArrayList<MoreSourceBean> lines = new ArrayList<>();
        try {
            org.json.JSONObject jsonObject = new org.json.JSONObject(body);
            if (jsonObject.has("urls")) {
                org.json.JSONArray urls = jsonObject.getJSONArray("urls");
                for (int i = 0; i < urls.length(); i++) {
                    org.json.JSONObject item = urls.getJSONObject(i);
                    MoreSourceBean lineBean = new MoreSourceBean();
                    lineBean.setSourceUrl(item.optString("url", ""));
                    lineBean.setSourceName(item.optString("name", ""));
                    lineBean.setShowDelete(true);
                    lines.add(lineBean);
                }
            }
        } catch (Exception ignored) {}
        return lines;
    }

    /**
     * 解析仓库内容获取线路列表
     */
    private ArrayList<String> parseStoreLines(String jsonStr) {
        ArrayList<String> apiLines = new ArrayList<>();
        try {
            String json = jsonStr.trim();
            int start = json.indexOf("{");
            int end = json.lastIndexOf("}");
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            org.json.JSONObject jsonObject = new org.json.JSONObject(json);
            if (jsonObject.has("urls") && jsonObject.get("urls") instanceof org.json.JSONArray) {
                org.json.JSONArray urls = jsonObject.getJSONArray("urls");
                for (int i = 0; i < urls.length(); i++) {
                    String name = "";
                    String url = "";
                    Object element = urls.get(i);
                    if (element instanceof org.json.JSONObject) {
                        org.json.JSONObject item = (org.json.JSONObject) element;
                        name = item.optString("name", "");
                        url = item.optString("url", "");
                        if (url.isEmpty()) {
                            url = item.optString("api", "");
                        }
                    } else if (element instanceof String) {
                        url = (String) element;
                    }
                    if (!url.isEmpty()) {
                        apiLines.add(HistoryHelper.buildApiLine(name, url));
                    }
                }
            }
            if (jsonObject.has("storeHouse") && jsonObject.get("storeHouse") instanceof org.json.JSONArray) {
                org.json.JSONArray storeHouse = jsonObject.getJSONArray("storeHouse");
                ArrayList<MoreSourceBean> stores = new ArrayList<>();
                LinkedHashMap<String, MoreSourceBean> linkedMap = new LinkedHashMap<>();
                for (int i = 0; i < storeHouse.length(); i++) {
                    org.json.JSONObject storeItem = storeHouse.getJSONObject(i);
                    String storeName = storeItem.optString("sourceName", "");
                    String storeUrl = storeItem.optString("sourceUrl", "");
                    MoreSourceBean existBean = linkedMap.get(storeUrl);
                    if (existBean == null) {
                        MoreSourceBean storeBean = new MoreSourceBean();
                        storeBean.setSourceName(storeName);
                        storeBean.setSourceUrl(storeUrl);
                        storeBean.setShowDelete(true);
                        linkedMap.put(storeUrl, storeBean);
                    } else {
                        existBean.setSourceName(storeName);
                    }
                }
                stores = new ArrayList<>(linkedMap.values());
                if (!stores.isEmpty()) {
                    saveStoreHouseList(stores);
                }
                if (apiLines.isEmpty() && !stores.isEmpty()) {
                    String firstStoreUrl = stores.get(0).getSourceUrl();
                    if (firstStoreUrl != null && !firstStoreUrl.isEmpty()) {
                        apiLines.add(HistoryHelper.buildApiLine("", firstStoreUrl));
                    }
                }
            }
        } catch (Exception ignored) {}
        return apiLines;
    }

    // ===================== 数据存储辅助方法 =====================

    private ArrayList<MoreSourceBean> getStoreHouseList() {
        try {
            ArrayList<MoreSourceBean> list = (ArrayList<MoreSourceBean>) Hawk.get(HawkConfig.CUSTOM_STORE_HOUSE, new ArrayList<MoreSourceBean>());
            return list != null ? list : new ArrayList<MoreSourceBean>();
        } catch (Exception e) {
            Hawk.delete(HawkConfig.CUSTOM_STORE_HOUSE);
            return new ArrayList<MoreSourceBean>();
        }
    }

    private void saveStoreHouseList(ArrayList<MoreSourceBean> list) {
        Hawk.put(HawkConfig.CUSTOM_STORE_HOUSE, list);
    }

    private MoreSourceBean getSelectedStoreHouse() {
        try {
            String json = Hawk.get(HawkConfig.CUSTOM_STORE_HOUSE_SELECTED, "");
            if (json == null || json.isEmpty()) return null;
            Gson gson = new Gson();
            return gson.fromJson(json, MoreSourceBean.class);
        } catch (Exception e) {
            Hawk.delete(HawkConfig.CUSTOM_STORE_HOUSE_SELECTED);
            return null;
        }
    }

    private void saveSelectedStoreHouse(MoreSourceBean bean) {
        Gson gson = new Gson();
        Hawk.put(HawkConfig.CUSTOM_STORE_HOUSE_SELECTED, gson.toJson(bean));
    }

    private ArrayList<MoreSourceBean> getApiHistoryList() {
        try {
            ArrayList<MoreSourceBean> list = (ArrayList<MoreSourceBean>) Hawk.get(HawkConfig.API_HISTORY_LIST, new ArrayList<MoreSourceBean>());
            return list != null ? list : new ArrayList<MoreSourceBean>();
        } catch (Exception e) {
            Hawk.delete(HawkConfig.API_HISTORY_LIST);
            return new ArrayList<MoreSourceBean>();
        }
    }

    private void saveApiHistoryList(ArrayList<MoreSourceBean> list) {
        Hawk.put(HawkConfig.API_HISTORY_LIST, list);
    }

    // ===================== 缓存方法 =====================

    private void writeLineCache(String cacheKey, String data, long cacheTimeMs) {
        try {
            File cacheDir = mActivity.getCacheDir();
            String fileName = cacheKey.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
            File cacheFile = new File(cacheDir, fileName + ".cache");
            long expireTime = cacheTimeMs == -1 ? Long.MAX_VALUE : (System.currentTimeMillis() + cacheTimeMs);
            FileOutputStream fos = new FileOutputStream(cacheFile);
            String content = data + "\n---CACHE_SPLIT---\n" + expireTime;
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String readLineCache(String cacheKey) {
        try {
            File cacheDir = mActivity.getCacheDir();
            String fileName = cacheKey.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
            File cacheFile = new File(cacheDir, fileName + ".cache");
            if (!cacheFile.exists()) return null;
            BufferedReader reader = new BufferedReader(new FileReader(cacheFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("---CACHE_SPLIT---")) break;
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isLineCacheValid(String cacheKey, long cacheTimeMs) {
        if (cacheTimeMs == -1) return true;
        try {
            File cacheDir = mActivity.getCacheDir();
            String fileName = cacheKey.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
            File cacheFile = new File(cacheDir, fileName + ".cache");
            if (!cacheFile.exists()) return false;
            BufferedReader reader = new BufferedReader(new FileReader(cacheFile));
            String line;
            long expireTime = 0;
            while ((line = reader.readLine()) != null) {
                if (line.contains("---CACHE_SPLIT---")) {
                    String nextLine = reader.readLine();
                    if (nextLine != null) {
                        expireTime = Long.parseLong(nextLine.trim());
                    }
                    break;
                }
            }
            reader.close();
            return System.currentTimeMillis() < expireTime;
        } catch (Exception e) {
            return false;
        }
    }

    private long getLineCacheExpireTime(String cacheKey) {
        try {
            File cacheDir = mActivity.getCacheDir();
            String fileName = cacheKey.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
            File cacheFile = new File(cacheDir, fileName + ".cache");
            if (!cacheFile.exists()) return 0;
            BufferedReader reader = new BufferedReader(new FileReader(cacheFile));
            String line;
            long expireTime = 0;
            while ((line = reader.readLine()) != null) {
                if (line.contains("---CACHE_SPLIT---")) {
                    String nextLine = reader.readLine();
                    if (nextLine != null) {
                        expireTime = Long.parseLong(nextLine.trim());
                    }
                    break;
                }
            }
            reader.close();
            return expireTime;
        } catch (Exception e) {
            return 0;
        }
    }

    // ===================== UI辅助 =====================

    private void showLineFetchError(final Exception e) {
        if (mActivity != null && !mActivity.isFinishing()) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mActivity, "线路获取失败" + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private String buildCacheInfoTitle() {
        if (currentLineCacheTime == -1) {
            return "选择线路\n线路缓存永久生效";
        }
        long localExpire = getLineCacheExpireTime(currentLineCacheKey);
        if (localExpire <= 0) {
            return "选择线路\n当前使用接口数据";
        }
        long remaining = (currentLineCacheTime + localExpire) - System.currentTimeMillis();
        if (remaining <= 0) {
            return "选择线路\n当前使用接口数据";
        }
        return "选择线路\n线路缓存将于" + formatCacheTime(remaining) + "后更新";
    }

    private String formatCacheTime(long millis) {
        String[] units = {"天", "小时", "分钟", "秒", "毫秒"};
        int[] divisors = {86400000, 3600000, 60000, 1000, 1};
        int maxUnits = Math.min(2, 5);
        StringBuilder sb = new StringBuilder();
        if (millis == 0) {
            return "0" + units[0];
        }
        if (millis < 0) {
            sb.append("-");
            millis = -millis;
        }
        for (int i = 0; i < maxUnits; i++) {
            long div = divisors[i];
            if (millis >= div) {
                long count = millis / div;
                millis -= div * count;
                sb.append(count).append(units[i]);
            }
        }
        return sb.toString();
    }

    /**
     * 配置切换后重启应用（对话框版本：直接重启，不需要返回设置页）
     */
    private void restartAppAfterConfigChanged() {
        Toast.makeText(mActivity, "配置已切换,即将重新加载!", Toast.LENGTH_SHORT).show();
        try {
            SourceViewModel.clearRuntimeCache();
            LOG.i("echo-clear-config-switch-cache");
        } catch (Exception e) {
            LOG.i("echo-clear-config-switch-cache-error:" + e.getMessage());
            e.printStackTrace();
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mActivity != null && !mActivity.isFinishing()) {
                    // 直接重启应用
                    Intent intent = mActivity.getPackageManager().getLaunchIntentForPackage(mActivity.getPackageName());
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        mActivity.startActivity(intent);
                        System.exit(0);
                    }
                }
            }
        }, 2500);
    }
}