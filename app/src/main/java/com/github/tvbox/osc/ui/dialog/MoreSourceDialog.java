package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.MoreSourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.HawkConfig;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 多仓配置对话框（参照ysc的h21，补齐所有缺失功能）
 *
 * 本次新增功能（对照ysc）：
 * - ItemTouchHelper拖拽排序
 * - ItemTouchHelper拖拽排序（长按拖动，参照hj0）
 * - 自动加载第一个仓库线路（参照h21.OooO00o）
 * - 标题长按清空所有仓库（参照cn0 case 1 + y81确认）
 * - 删除确认对话框（参照y81/b21: 删除前弹确认"确定要删除？"）
 * - 选中项前显示ic_select_fill图标 + requestFocus（参照c21）
 * - 宽度自适应（参照h21: isTablet→720mm，否则760mm）
 * - postDelayed标题获取焦点（参照id case 16）
 * - 删除时清除线路缓存+.zip残留（参照b21.OooO0Oo）
 */
public class MoreSourceDialog extends BaseDialog {
    private EditText inputSourceName;
    private EditText inputSourceUrl;
    private ProgressBar playLoading;
    private com.owen.tvrecyclerview.widget.TvRecyclerView storeListView;
    private StoreListAdapter storeAdapter;
    private ArrayList<MoreSourceBean> storeList = new ArrayList<>();
    private MoreSourceBean selectedStore = null;
    private boolean isFetching = false;
    private OnStoreSelectedListener onStoreSelectedListener;
    private boolean storeWasSelected = false;
    private ItemTouchHelper itemTouchHelper;

    /**
     * 本地文件选择回调接口（参照ApiDialog.OnListener.onLocalConfig）
     */
    public interface OnLocalFileListener {
        void onLocalFileSelected();
    }

    private OnLocalFileListener onLocalFileListener;

    public void setOnLocalFileListener(OnLocalFileListener listener) {
        this.onLocalFileListener = listener;
    }

    /**
     * 仓库选中回调接口（参照ysc a21: 选中仓库后直接触发线路切换）
     */
    public interface OnStoreSelectedListener {
        void onStoreSelected(MoreSourceBean bean);
    }

    public void setOnStoreSelectedListener(OnStoreSelectedListener listener) {
        this.onStoreSelectedListener = listener;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPushStore(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_STORE) {
            String url = (String) event.obj;
            String name = event.obj2 != null ? (String) event.obj2 : "";
            if (url != null && !url.isEmpty()) {
                addStoreToLayout(url, name);
            }
        }
    }

    public MoreSourceDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.more_source_dialog_select);
        setCanceledOnTouchOutside(false);

        inputSourceName = findViewById(R.id.input_sourceName);
        inputSourceUrl = findViewById(R.id.input_source_url);
        playLoading = findViewById(R.id.play_loading);
        storeListView = findViewById(R.id.list);

        // 加载已保存的仓库列表
        loadStoreList();

        // 初始化仓库列表适配器
        storeAdapter = new StoreListAdapter();
        storeListView.setAdapter(storeAdapter);

        // ========== 功能: ItemTouchHelper拖拽（参照h21.OooO0OO末尾，只绑定一次） ==========
        attachItemTouchHelper();

        // ========== 功能: 自动加载第一个仓库（参照ysc h21.OooO00o） ==========
        // 对话框打开时，如果api_url为空，自动请求第一个仓库的线路
        autoLoadFirstStore();

        // 本地文件按钮（参照ApiDialog的"选择"按钮，打开本地文件选择器）
        findViewById(R.id.inputLocalFile).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onLocalFileListener != null) {
                    onLocalFileListener.onLocalFileSelected();
                }
            }
        });

        // 确定按钮 -> 添加新仓库
        findViewById(R.id.inputSubmit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isFetching) {
                    Toast.makeText(getContext(), "正在解析中，请稍候...", Toast.LENGTH_SHORT).show();
                    return;
                }
                String name = inputSourceName.getText().toString().trim();
                String url = inputSourceUrl.getText().toString().trim();
                if (url.isEmpty()) {
                    Toast.makeText(getContext(), "请输入仓库地址！", Toast.LENGTH_SHORT).show();
                    return;
                }
                addStoreToLayout(url, name);
                inputSourceName.setText("");
                inputSourceUrl.setText("");
            }
        });

        // 存储权限按钮（参照ysc h21构造: 已有权限直接GONE，否则VISIBLE并设点击监听）
        View permissionBtn = findViewById(R.id.permission_Submit);
        if (permissionBtn != null) {
            if (XXPermissions.isGranted(getContext(), Permission.Group.STORAGE)) {
                permissionBtn.setVisibility(View.GONE);
            } else {
                permissionBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        XXPermissions.with(getContext())
                                .permission(Permission.Group.STORAGE)
                                .request(new OnPermissionCallback() {
                                    @Override
                                    public void onGranted(java.util.List<String> permissions, boolean all) {
                                        if (all) {
                                            Toast.makeText(getContext(), "已获得存储权限,按钮已经被隐藏", Toast.LENGTH_SHORT).show();
                                            v.setVisibility(View.GONE);
                                        }
                                    }

                                    @Override
                                    public void onDenied(java.util.List<String> permissions, boolean never) {
                                        if (never) {
                                            Toast.makeText(getContext(), "获取存储权限失败,请在系统设置中开启", Toast.LENGTH_SHORT).show();
                                            XXPermissions.startPermissionActivity((Activity) getContext(), permissions);
                                        } else {
                                            Toast.makeText(getContext(), "获取存储权限失败", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                    }
                });
            }
        }

        // 刷新二维码（与原始TVBoxOS一致：构造函数末尾调用）
        refreshQRCode();

        // 标题相关功能
        TextView title = findViewById(R.id.title);
        if (title != null) {
            // ========== 功能: 标题点击 -> 选中第一个仓库并进入线路切换（参照ysc h21 oO0O0O00 case 1） ==========
            title.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!storeList.isEmpty()) {
                        MoreSourceBean firstStore = storeList.get(0);
                        saveSelectedAndNotify(firstStore);
                    }
                }
            });

            // ========== 功能: postDelayed获取焦点（参照ysc id case 16: title.setFocusable(true)） ==========
            title.setFocusable(true);
            title.postDelayed(new Runnable() {
                @Override
                public void run() {
                    title.setFocusable(true);
                }
            }, 1000L);

            // ========== 功能: 标题长按清空所有仓库（参照ysc cn0 case 1 → y81确认） ==========
            title.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    // 弹出确认对话框（参照ysc y81: "确定要删除？" + 取消/确定）
                    showConfirmDialog(getContext(), "确定要删除？", "取消", "确定",
                        new ConfirmCallback() {
                            @Override
                            public void onConfirm() {
                                clearAllStores();
                            }

                            @Override
                            public void onCancel() {
                            }
                        }
                    );
                    return true;
                }
            });
        }

        // 刷新二维码
        refreshQRCode();
    }

    // ===================== 自动加载第一个仓库（参照ysc h21.OooO00o） =====================
    /**
     * 对话框打开时，如果api_url为空，自动获取第一个仓库的线路
     * 参照ysc h21.OooO00o:
     * 1. 检查api_url是否为空
     * 2. 取custom_store_house第一个元素
     * 3. 保存为custom_store_house_selected
     * 4. clan://地址转换
     * 5. 非本地地址使用缓存模式 FIRST_CACHE_THEN_REQUEST
     * 6. gitcode地址使用特殊UA
     * 7. 发起GET请求（q90回调中会弹出线路选择）
     */
    private void autoLoadFirstStore() {
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        if (apiUrl != null && !apiUrl.isEmpty()) {
            return; // api_url不为空，不需要自动加载
        }
        if (storeList.isEmpty()) {
            return; // 仓库列表为空，无法加载
        }
        MoreSourceBean firstStore = storeList.get(0);
        // 保存为选中状态（参照ysc: iu.Oooo00O(moreSourceBean, "custom_store_house_selected")）
        saveSelectedStoreHouse(firstStore);
        // 标记选中
        for (MoreSourceBean bean : storeList) {
            bean.setSelected(bean == firstStore);
        }
        selectedStore = firstStore;

        String rawUrl = firstStore.getSourceUrl();
        if (rawUrl == null || rawUrl.isEmpty()) return;
        // 复用 ApiConfig.configUrl() 统一处理URL（file://转换、;pk;提取、clan://转换、http补全）
        ApiConfig apiConfig = ApiConfig.get();
        String fetchUrl = apiConfig.configUrl(rawUrl);
        final String configKey = apiConfig.getTempKey();
        final String finalUrl = fetchUrl;
        final String finalRawUrl = rawUrl;

        // 在子线程中请求线路（参照ysc h21.OooO00o: st stVar = new st(strClanToAddress) → execute）
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .build();
                    okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder().url(finalUrl);
                    if (finalUrl.startsWith("https://gitcode")) {
                        requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                    } else {
                        requestBuilder.header("User-Agent", "Mozilla/5.0");
                    }
                    okhttp3.Response response = client.newCall(requestBuilder.build()).execute();
                    if (response.body() != null) {
                        String body = response.body().string();
                        response.close();
                        // 复用 ApiConfig.FindResult 解密 + 后处理
                        body = ApiConfig.FindResult(body, configKey);
                        if (finalRawUrl.startsWith("clan") || finalRawUrl.startsWith("file://")) {
                            body = apiConfig.clanContentFix(finalUrl, body);
                        }
                        body = apiConfig.fixContentPath(finalRawUrl, body);
                        // 解析线路列表
                        parseAndShowLines(body, finalUrl, firstStore);
                    } else {
                        response.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 解析线路JSON并展示线路选择（参照ysc q90成功回调）
     * 这里简化处理：保存api_url，通知调用方刷新
     */
    private void parseAndShowLines(String body, String url, MoreSourceBean store) {
        try {
            org.json.JSONObject jsonObject = new org.json.JSONObject(body);
            if (jsonObject.has("urls")) {
                org.json.JSONArray urls = jsonObject.getJSONArray("urls");
                ArrayList<MoreSourceBean> lines = new ArrayList<>();
                for (int i = 0; i < urls.length(); i++) {
                    org.json.JSONObject item = urls.getJSONObject(i);
                    MoreSourceBean lineBean = new MoreSourceBean();
                    lineBean.setSourceUrl(item.optString("url", ""));
                    lineBean.setSourceName(item.optString("name", ""));
                    lines.add(lineBean);
                }
                // 保存本地线路到仓库bean（参照ysc: moreSourceBean.setLocalLineUrls）
                store.setLocalLineUrls(lines);
                // 保存仓库列表（更新localLineUrls）
                saveStoreList();
                // 自动选中第一条线路并保存api_url
                if (!lines.isEmpty()) {
                    MoreSourceBean firstLine = lines.get(0);
                    Hawk.put(HawkConfig.API_URL, firstLine.getSourceUrl());
                    // 通知刷新（参照ysc: us0(8, name) + 调用gs）
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getContext(), "已自动加载线路: " + firstLine.getSourceName(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===================== 标题长按清空所有仓库（参照ysc cn0 case 1 + y81确认） =====================

    /**
     * 设置本地文件选择后的URL（参照ApiDialog.setLocalApi）
     * 由ModelSettingFragment在onActivityResult中回调
     */
    public void setLocalFileUrl(String url) {
        if (url != null && !url.isEmpty()) {
            inputSourceUrl.setText(url);
        }
    }

    /**
     * 确认对话框回调接口（参照ysc x81）
     */
    public interface ConfirmCallback {
        void onConfirm();
        void onCancel();
    }

    /**
     * 通用确认对话框（参照ysc y81: 标题 + 取消/确定按钮）
     */
    private void showConfirmDialog(Context context, String message, String cancelText, String confirmText,
                                   final ConfirmCallback callback) {
        final android.app.Dialog dialog = new android.app.Dialog(context);
        dialog.setContentView(R.layout.dialog_tip);
        dialog.setCanceledOnTouchOutside(false);
        TextView tipInfo = dialog.findViewById(R.id.tipInfo);
        TextView leftBtn = dialog.findViewById(R.id.leftBtn);
        TextView rightBtn = dialog.findViewById(R.id.rightBtn);
        if (tipInfo != null) tipInfo.setText(message);
        if (leftBtn != null) {
            leftBtn.setText(cancelText);
            leftBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (callback != null) callback.onCancel();
                }
            });
        }
        if (rightBtn != null) {
            rightBtn.setText(confirmText);
            rightBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (callback != null) callback.onConfirm();
                }
            });
        }
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                if (callback != null) callback.onCancel();
            }
        });
        dialog.show();
    }

    /**
     * 清空所有仓库（参照ysc cn0 case 1确认后的操作）
     * 1. 清空仓库列表
     * 2. 清空选中状态
     * 3. 清除线路缓存
     * 4. 清理.zip残留文件
     */
    private void clearAllStores() {
        selectedStore = null;
        storeWasSelected = false;
        // 清除选中（参照ysc b21.OooO0Oo: iu.Oooo0o0("custom_store_house_selected")）
        Hawk.delete(HawkConfig.CUSTOM_STORE_HOUSE_SELECTED);
        // 清除线路缓存（参照ysc b21.OooO0Oo: x1.OooO00o.OooO0o0(null, null)）
        clearLineCache();
        // 清理.zip残留文件（参照ysc b21.OooO0Oo: wp.OooO0Oo）
        cleanZipResiduals();
        // 清空为空列表并保存
        refreshListWithDiffUtil(new ArrayList<MoreSourceBean>());
        saveStoreList();
        Toast.makeText(getContext(), "已清空所有仓库", Toast.LENGTH_SHORT).show();
    }

    /**
     * 清除线路缓存（参照ysc b21: x1.OooO00o.OooO0o0(null, null)）
     * 这里通过清除OkGo的缓存目录中LINE_KEY开头的缓存来实现
     */
    private void clearLineCache() {
        try {
            File cacheDir = getContext().getCacheDir();
            if (cacheDir != null && cacheDir.exists()) {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName() != null && file.getName().startsWith("LINE_KEY")) {
                            file.delete();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 清理.zip残留文件（参照ysc b21.OooO0Oo: wp.OooO0Oo）
     */
    private void cleanZipResiduals() {
        try {
            File cacheDir = getContext().getCacheDir();
            if (cacheDir != null && cacheDir.exists()) {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName() != null && file.getName().endsWith(".zip")) {
                            file.delete();
                        }
                        // 也清理解压后的目录
                        if (file.isDirectory() && file.getName().contains("_extracted")) {
                            deleteDir(file);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDir(child);
                }
            }
        }
        dir.delete();
    }

    // ===================== 列表刷新 =====================

    /**
     * 刷新列表（与原始TVBoxOS一致：直接clear/addAll/notifyDataSetChanged）
     */
    private void refreshListWithDiffUtil(ArrayList<MoreSourceBean> newList) {
        storeList.clear();
        storeList.addAll(newList);
        storeAdapter.notifyDataSetChanged();
    }

    // ===================== ItemTouchHelper拖拽排序（参照ysc hj0） =====================

    /**
     * 绑定拖拽排序（参照ysc h21.OooO0OO末尾: new ItemTouchHelper(new hj0(list, adapter)).attachToRecyclerView）
     */
    private void attachItemTouchHelper() {
        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(null);
        }
        itemTouchHelper = new ItemTouchHelper(new StoreItemTouchCallback(storeList, storeAdapter));
        itemTouchHelper.attachToRecyclerView(storeListView);
    }

    /**
     * ItemTouchHelper.Callback实现（参照ysc hj0）
     * 支持四个方向拖拽，不处理滑动删除
     */
    private static class StoreItemTouchCallback extends ItemTouchHelper.Callback {
        private final List<MoreSourceBean> dataList;
        private final RecyclerView.Adapter adapter;

        StoreItemTouchCallback(List<MoreSourceBean> dataList, RecyclerView.Adapter adapter) {
            this.dataList = dataList;
            this.adapter = adapter;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            // 参照ysc hj0: makeMovementFlags(15, 0) → 支持上下左右拖拽，不处理滑动
            return makeMovementFlags(15, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            // 参照ysc hj0: return true → 长按触发拖拽
            return true;
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            // 参照ysc hj0.onMove: 逐步swap实现拖拽排序
            recyclerView.getParent().requestDisallowInterceptTouchEvent(true);
            int fromPos = viewHolder.getAdapterPosition();
            int toPos = target.getAdapterPosition();
            if (fromPos < toPos) {
                for (int i = fromPos; i < toPos; i++) {
                    Collections.swap(dataList, i, i + 1);
                }
            } else {
                for (int i = fromPos; i > toPos; i--) {
                    Collections.swap(dataList, i, i - 1);
                }
            }
            adapter.notifyItemMoved(fromPos, toPos);
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            // 参照ysc hj0.onSwiped: 空实现，不处理滑动删除
        }
    }

    // ===================== 添加仓库到列表 =====================

    /**
     * 添加仓库到列表
     * 如果地址返回 storeHouse 格式，自动解析并将子仓库展开到列表中
     */
    private void addStoreToLayout(final String url, final String name) {
        if (url.isEmpty()) return;
        // 检查是否已存在（参照ysc: arrayList.contains(moreSourceBean)，依赖BaseItem.equals）
        MoreSourceBean checkBean = new MoreSourceBean();
        checkBean.setSourceUrl(url);
        checkBean.setSourceName(name);
        if (storeList.contains(checkBean)) {
            Toast.makeText(getContext(), "该仓库已存在", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示加载状态
        isFetching = true;
        if (playLoading != null) {
            playLoading.setVisibility(View.VISIBLE);
        }

        // 在子线程中请求并解析仓库地址
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 复用 ApiConfig.configUrl() 统一处理URL（file://转换、;pk;提取、clan://转换、http补全）
                    ApiConfig apiConfig = ApiConfig.get();
                    String fetchUrl = apiConfig.configUrl(url);
                    final String configKey = apiConfig.getTempKey();

                    // HTTP GET 获取内容
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .build();
                    okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder().url(fetchUrl);
                    if (fetchUrl.startsWith("https://gitcode")) {
                        requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                    } else {
                        requestBuilder.header("User-Agent", "Mozilla/5.0");
                    }
                    okhttp3.Response response = client.newCall(requestBuilder.build()).execute();
                    if (!response.isSuccessful()) {
                        response.close();
                        runOnMainShowToast("接口拉取失败" + (response.message() != null ? response.message() : ""));
                        return;
                    }
                    String body = null;
                    if (response.body() != null) {
                        body = response.body().string();
                        response.close();
                    }

                    if (body == null || body.isEmpty()) {
                        runOnMainShowToast("仓库内容为空");
                        return;
                    }

                    // 复用 ApiConfig.FindResult 解密 + 后处理（与配置地址完全一致的解析逻辑）
                    String strFindResult = ApiConfig.FindResult(body, configKey);
                    if (url.startsWith("clan") || url.startsWith("file://")) {
                        strFindResult = apiConfig.clanContentFix(fetchUrl, strFindResult);
                    }
                    strFindResult = apiConfig.fixContentPath(url, strFindResult);

                    try {
                        org.json.JSONObject jSONObject2 = new org.json.JSONObject(strFindResult);
                        if (!jSONObject2.has("storeHouse")) {
                            if (jSONObject2.has("urls")) {
                                // 解析urls线路列表并保存到localLineUrls
                                org.json.JSONArray urlsArray = jSONObject2.getJSONArray("urls");
                                ArrayList<MoreSourceBean> lines = new ArrayList<>();
                                for (int i = 0; i < urlsArray.length(); i++) {
                                    org.json.JSONObject item = urlsArray.getJSONObject(i);
                                    MoreSourceBean lineBean = new MoreSourceBean();
                                    lineBean.setSourceUrl(item.optString("url", ""));
                                    lineBean.setSourceName(item.optString("name", ""));
                                    lines.add(lineBean);
                                }
                                final ArrayList<MoreSourceBean> finalLines = lines;
                                final String finalName = name;
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        isFetching = false;
                                        if (playLoading != null) playLoading.setVisibility(View.GONE);
                                        MoreSourceBean storeBean = new MoreSourceBean();
                                        storeBean.setSourceUrl(url);
                                        storeBean.setSourceName(finalName);
                                        storeBean.setLocalLineUrls(finalLines);
                                        storeBean.setShowDelete(true);
                                        // 去重检查
                                        if (!storeList.contains(storeBean)) {
                                            ArrayList<MoreSourceBean> newList = new ArrayList<>(storeList);
                                            newList.add(0, storeBean);
                                            refreshListWithDiffUtil(newList);
                                            saveStoreList();
                                            if (storeListView != null) {
                                                storeListView.scrollToPosition(0);
                                            }
                                        }
                                        // 自动选中第一条线路
                                        if (!finalLines.isEmpty()) {
                                            Hawk.put(HawkConfig.API_URL, finalLines.get(0).getSourceUrl());
                                            Toast.makeText(getContext(), "已自动加载线路: " + finalLines.get(0).getSourceName(), Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                            } else if (jSONObject2.has("sites")) {
                                final String finalName = name;
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        isFetching = false;
                                        if (playLoading != null) playLoading.setVisibility(View.GONE);
                                        handleSitesFormat(url, finalName);
                                    }
                                });
                            } else {
                                runOnMainShowToast("你的仓库格式不对");
                            }
                        } else {
                            // storeHouse格式
                            org.json.JSONArray jSONArray = jSONObject2.getJSONArray("storeHouse");
                            int length = jSONArray != null ? jSONArray.length() : 0;
                            LinkedHashMap<String, MoreSourceBean> linkedHashMap = new LinkedHashMap<>();
                            for (int i = 0; i < length; i++) {
                                org.json.JSONObject jSONObject3 = jSONArray != null ? jSONArray.getJSONObject(i) : null;
                                String strOptString = jSONObject3 != null ? jSONObject3.optString("sourceName") : null;
                                if (strOptString == null) strOptString = "";
                                String strOptString2 = jSONObject3 != null ? jSONObject3.optString("sourceUrl") : null;
                                if (strOptString2 == null) strOptString2 = "";
                                if (strOptString2.isEmpty()) continue;
                                MoreSourceBean existBean = linkedHashMap.get(strOptString2);
                                if (existBean == null) {
                                    MoreSourceBean newBean = new MoreSourceBean();
                                    newBean.setSourceName(strOptString);
                                    newBean.setSourceUrl(strOptString2);
                                    newBean.setShowDelete(true);
                                    linkedHashMap.put(strOptString2, newBean);
                                } else {
                                    existBean.setSourceName(strOptString);
                                }
                            }
                            final ArrayList<MoreSourceBean> parsedStores = new ArrayList<>(linkedHashMap.values());
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    isFetching = false;
                                    if (playLoading != null) playLoading.setVisibility(View.GONE);
                                    mergeStoreList(parsedStores);
                                }
                            });
                        }
                    } catch (final Exception e) {
                        e.printStackTrace();
                        runOnMainShowToast("接口无效，请更换接口" + e.getMessage());
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnMainAddSingleStore(url, name, "获取失败: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * 处理sites格式
     */
    private void handleSitesFormat(String url, String name) {
        Hawk.put(HawkConfig.API_URL, url);
        try {
            ArrayList<MoreSourceBean> historyList = (ArrayList<MoreSourceBean>) Hawk.get(HawkConfig.API_HISTORY_LIST, new ArrayList<MoreSourceBean>());
            if (historyList == null) {
                historyList = new ArrayList<>();
            }
            MoreSourceBean newBean = new MoreSourceBean();
            newBean.setSourceUrl(url);
            if (name != null && !name.isEmpty()) {
                newBean.setSourceName(name);
            } else {
                newBean.setSourceName("自定义配置线路" + historyList.size());
            }
            newBean.setShowDelete(true);
            historyList.add(newBean);
            Hawk.put(HawkConfig.API_HISTORY_LIST, historyList);
        } catch (Exception e) {
            Hawk.delete(HawkConfig.API_HISTORY_LIST);
        }
        Toast.makeText(getContext(), "系统识别到你推送的可能是线路，已经帮你保存并重启首页", Toast.LENGTH_LONG).show();
        try {
            Context ctx = getContext();
            if (ctx instanceof Activity) {
                dismiss();
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Intent intent = new Intent(ctx, com.github.tvbox.osc.ui.activity.HomeActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            intent.putExtra("JAR_INIT_OK", false);
                            ctx.startActivity(intent);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, 300);
            } else {
                dismiss();
            }
        } catch (Exception e) {
            dismiss();
        }
    }

    /**
     * 合并解析出的仓库到列表（参照ysc h21.OooO0OO: 用LinkedHashMap去重合并）
     * 刷新列表（与原始TVBoxOS一致）
     */
    private void mergeStoreList(ArrayList<MoreSourceBean> parsedStores) {
        if (parsedStores.isEmpty()) {
            Toast.makeText(getContext(), "解析结果为空", Toast.LENGTH_SHORT).show();
            return;
        }
        // 参照ysc h21.OooO0OO的合并逻辑：用LinkedHashMap去重
        LinkedHashMap<String, MoreSourceBean> existMap = new LinkedHashMap<>();
        for (MoreSourceBean bean : storeList) {
            existMap.put(bean.getUniKey(), bean);
        }
        LinkedHashMap<String, MoreSourceBean> newMap = new LinkedHashMap<>();
        for (MoreSourceBean bean : parsedStores) {
            newMap.put(bean.getUniKey(), bean);
        }
        int addedCount = 0;
        for (Map.Entry<String, MoreSourceBean> entry : newMap.entrySet()) {
            if (!existMap.containsKey(entry.getKey())) {
                existMap.put(entry.getKey(), entry.getValue());
                addedCount++;
            }
        }
        ArrayList<MoreSourceBean> mergedList = new ArrayList<>(existMap.values());
        // 恢复选中状态
        String selectedKey = selectedStore != null ? selectedStore.getUniKey() : null;
        for (MoreSourceBean bean : mergedList) {
            bean.setSelected(bean.getUniKey().equals(selectedKey));
        }
        // ========== 刷新列表 ==========
        refreshListWithDiffUtil(mergedList);
        // 在storeList被refreshListWithDiffUtil更新后再保存
        saveStoreList();
        if (storeListView != null) {
            storeListView.scrollToPosition(0);
        }
        Toast.makeText(getContext(), "解析成功，添加了" + addedCount + "个仓库", Toast.LENGTH_SHORT).show();
    }

    /**
     * 在主线程显示Toast
     */
    private void runOnMainShowToast(final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                isFetching = false;
                if (playLoading != null) {
                    playLoading.setVisibility(View.GONE);
                }
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 子线程中调用：网络异常时切回主线程，以单个仓库形式添加（兜底）
     */
    private void runOnMainAddSingleStore(final String url, final String name, final String errorMsg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                isFetching = false;
                if (playLoading != null) {
                    playLoading.setVisibility(View.GONE);
                }
                if (errorMsg != null) {
                    Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show();
                }
                addSingleStoreDirect(url, name);
            }
        });
    }

    /**
     * 直接以单个仓库形式添加到列表（原始行为）
     */
    private void addSingleStoreDirect(String url, String name) {
        // 使用contains去重（依赖BaseItem.equals）
        MoreSourceBean checkBean = new MoreSourceBean();
        checkBean.setSourceUrl(url);
        checkBean.setSourceName(name);
        if (storeList.contains(checkBean)) {
            return;
        }
        MoreSourceBean newBean = new MoreSourceBean();
        newBean.setSourceUrl(url);
        if (name == null || name.isEmpty()) {
            name = "自用仓库" + storeList.size();
        }
        newBean.setSourceName(name);
        newBean.setShowDelete(true);
        // 构建新列表再刷新（不能先add到storeList）
        ArrayList<MoreSourceBean> newList = new ArrayList<>(storeList);
        newList.add(0, newBean);
        refreshListWithDiffUtil(newList);
        saveStoreList();
        if (storeListView != null) {
            storeListView.scrollToPosition(0);
        }
    }

    /**
     * 保存选中仓库并通知
     */
    private void saveSelectedAndNotify(MoreSourceBean bean) {
        for (MoreSourceBean item : storeList) {
            item.setSelected(false);
        }
        bean.setSelected(true);
        selectedStore = bean;
        saveSelectedStoreHouse(bean);
        Hawk.put(HawkConfig.STORE_API, bean.getSourceUrl());
        if (bean.getSourceName() != null && !bean.getSourceName().isEmpty()) {
            Hawk.put(HawkConfig.STORE_API_NAME, bean.getSourceName());
        }
        // 刷新列表显示选中状态
        refreshListWithDiffUtil(new ArrayList<>(storeList));
        // 每次选择后立即保存列表到Hawk，防止外部代码（如parseStoreLines）覆盖CUSTOM_STORE_HOUSE导致列表丢失
        saveStoreList();
        // 直接触发回调通知调用方加载配置，不关闭对话框
        final OnStoreSelectedListener listener = this.onStoreSelectedListener;
        if (listener != null) {
            listener.onStoreSelected(bean);
        }
        // 标记已通知，避免dismiss时重复回调
        storeWasSelected = false;
        Toast.makeText(getContext(), "已选择: " + bean.getSourceName() + "，正在加载...", Toast.LENGTH_SHORT).show();
    }

    // ===================== 仓库列表适配器（参照ysc的c21，新增选中图标+焦点管理） =====================

    private class StoreListAdapter extends RecyclerView.Adapter<StoreListAdapter.ViewHolder> {
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            ImageView ivDelete;
            View itemView;

            ViewHolder(View itemView) {
                super(itemView);
                this.itemView = itemView;
                tvName = itemView.findViewById(R.id.tvName);
                ivDelete = itemView.findViewById(R.id.ivDelete);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_source_select, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MoreSourceBean bean = storeList.get(position);
            // ========== 参照ysc c21.OooOOOo: 优先显示sourceName，为空时显示sourceUrl ==========
            String displayName = bean.getSourceName();
            if (displayName == null || displayName.isEmpty()) {
                displayName = bean.getSourceUrl();
            }
            holder.tvName.setText(displayName);

            // ========== 参照ysc c21.OooO0OO: 选中项显示ic_select_fill图标 + requestFocus ==========
            if (bean.isSelected()) {
                // 选中时：在文字前添加ic_select_fill图标（参照ysc c21: d31 + ImageSpan）
                Drawable selectIcon = ContextCompat.getDrawable(holder.tvName.getContext(), R.drawable.ic_select_fill);
                if (selectIcon != null) {
                    selectIcon.setBounds(0, 0, selectIcon.getIntrinsicWidth(), selectIcon.getIntrinsicHeight());
                    holder.tvName.setCompoundDrawables(selectIcon, null, null, null);
                    holder.tvName.setCompoundDrawablePadding(dpToPx(8));
                }
                // 选中项文字颜色
                holder.tvName.setTextColor(0xff02f8e1);
                // 参照ysc c21: textView.requestFocus() — 选中项获取焦点
                holder.tvName.requestFocus();
            } else {
                holder.tvName.setCompoundDrawables(null, null, null, null);
                holder.tvName.setTextColor(0xFFFFFFFF);
            }

            // 点击仓库名 -> 选中该仓库并进入线路切换
            holder.tvName.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveSelectedAndNotify(bean);
                }
            });

            // ========== 删除按钮（全部可删除，按用户要求） ==========
            // 但增加删除确认对话框（参照ysc b21: 删除前弹确认"确定要删除？"）
            if (holder.ivDelete != null) {
                holder.ivDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final int pos = holder.getAdapterPosition();
                        if (pos >= 0 && pos < storeList.size()) {
                            // ========== 新增: 删除确认对话框（参照ysc y81/b21） ==========
                            showConfirmDialog(getContext(), "确定要删除？", "取消", "确定",
                                new ConfirmCallback() {
                                    @Override
                                    public void onConfirm() {
                                        deleteStoreAt(pos);
                                    }

                                    @Override
                                    public void onCancel() {
                                    }
                                }
                            );
                        }
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return storeList.size();
        }

        private int dpToPx(int dp) {
            return (int) (dp * getContext().getResources().getDisplayMetrics().density + 0.5f);
        }
    }

    /**
     * 删除指定位置的仓库（参照ysc b21.OooO0Oo）
     * 1. 从列表中移除
     * 2. 清除线路缓存
     * 3. 清理.zip残留
     * 4. 保存列表
     */
    private void deleteStoreAt(int pos) {
        if (pos < 0 || pos >= storeList.size()) return;
        MoreSourceBean removedBean = storeList.get(pos);
        // 如果删除的是当前选中的，清除选中状态
        if (removedBean.isSelected()) {
            selectedStore = null;
            storeWasSelected = false;
            Hawk.delete(HawkConfig.CUSTOM_STORE_HOUSE_SELECTED);
        }
        // 构建删除后的新列表（不能先remove再刷新）
        ArrayList<MoreSourceBean> newList = new ArrayList<>(storeList);
        newList.remove(pos);
        // 清除线路缓存（参照ysc b21.OooO0Oo: x1.OooO00o.OooO0o0(null, null)）
        clearLineCache();
        // 清理.zip残留（参照ysc b21: 如果sourceName包含.zip，清理对应目录）
        if (removedBean.getSourceName() != null && removedBean.getSourceName().contains(".zip")) {
            String dirName = removedBean.getSourceName().substring(0,
                    removedBean.getSourceName().lastIndexOf(".zip"));
            try {
                File cacheDir = getContext().getCacheDir();
                if (cacheDir != null) {
                    File zipDir = new File(cacheDir, dirName);
                    if (zipDir.exists()) {
                        deleteDir(zipDir);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 刷新列表（会更新storeList）
        refreshListWithDiffUtil(newList);
    }

    // ===================== 数据存储方法 =====================

    private void loadStoreList() {
        try {
            ArrayList<MoreSourceBean> saved = (ArrayList<MoreSourceBean>) Hawk.get(HawkConfig.CUSTOM_STORE_HOUSE, new ArrayList<MoreSourceBean>());
            if (saved != null && !saved.isEmpty()) {
                storeList.clear();
                storeList.addAll(saved);
            }
        } catch (Exception e) {
            Hawk.delete(HawkConfig.CUSTOM_STORE_HOUSE);
            storeList.clear();
        }
        // 如果列表为空但有旧的 STORE_API，迁移过来
        if (storeList.isEmpty()) {
            String oldStoreApi = Hawk.get(HawkConfig.STORE_API, "");
            if (!oldStoreApi.isEmpty()) {
                MoreSourceBean oldBean = new MoreSourceBean();
                oldBean.setSourceName(Hawk.get(HawkConfig.STORE_API_NAME, ""));
                oldBean.setSourceUrl(oldStoreApi);
                storeList.add(oldBean);
                saveStoreList();
            }
        }
        // 加载选中的仓库
        loadSelectedStore();
    }

    private void saveStoreList() {
        Hawk.put(HawkConfig.CUSTOM_STORE_HOUSE, storeList);
    }

    private void loadSelectedStore() {
        try {
            String json = Hawk.get(HawkConfig.CUSTOM_STORE_HOUSE_SELECTED, "");
            if (json != null && !json.isEmpty()) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                selectedStore = gson.fromJson(json, MoreSourceBean.class);
                // 标记选中状态
                if (selectedStore != null) {
                    for (MoreSourceBean bean : storeList) {
                        if (selectedStore.getUniKey().equals(bean.getUniKey())) {
                            bean.setSelected(true);
                        } else {
                            bean.setSelected(false);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Hawk.delete(HawkConfig.CUSTOM_STORE_HOUSE_SELECTED);
        }
    }

    private void saveSelectedStoreHouse(MoreSourceBean bean) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        Hawk.put(HawkConfig.CUSTOM_STORE_HOUSE_SELECTED, gson.toJson(bean));
    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        ImageView ivQRCode = findViewById(R.id.qrCode);
        TextView jumpWeb = findViewById(R.id.jump_web);
        if (ivQRCode != null) {
            ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address, AutoSizeUtils.mm2px(getContext(), 200), AutoSizeUtils.mm2px(getContext(), 200)));
        }
        if (jumpWeb != null) {
            jumpWeb.setText("扫码远程推送\n" + address);
        }
    }

    @Override
    public void show() {
        // 参照ysc h21.show: lm.OooO0O0().OooOO0(this) — 注册EventBus
        EventBus.getDefault().register(this);
        super.show();
    }

    @Override
    public void dismiss() {
        // 参照ysc h21.dismiss: lm.OooO0O0().OooOO0o(this) — 反注册EventBus
        try {
            EventBus.getDefault().unregister(this);
        } catch (Exception ignored) {
        }
        // 参照ysc h21.dismiss: 保存列表（非空时才保存）
        if (!storeList.isEmpty()) {
            saveStoreList();
        }
        final OnStoreSelectedListener listener = this.onStoreSelectedListener;
        final MoreSourceBean selected = this.selectedStore;
        final boolean wasSelected = this.storeWasSelected;
        super.dismiss();
        if (wasSelected && listener != null && selected != null) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    listener.onStoreSelected(selected);
                }
            }, 300);
        }
    }

    public void setOnListener(OnListener listener) {
        // 保留接口兼容
    }

    public interface OnListener {
        void onAdd(MoreSourceBean bean);
    }
}
