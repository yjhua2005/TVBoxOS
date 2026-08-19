package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.LiveSourceBean;
import com.github.tvbox.osc.bean.MoreSourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.LiveSourceSelectAdapter;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 直播源配置对话框
 * 左侧：二维码推送
 * 右侧：直播源分支列表（选择+删除）
 * 底部：手动添加输入框
 */
public class LiveSourceDialog extends BaseDialog {
    private EditText inputSourceName;
    private EditText inputSourceUrl;
    private ProgressBar playLoading;
    private TvRecyclerView recyclerView;
    private LiveSourceSelectAdapter adapter;
    private JsonArray livesGroups;
    private ArrayList<String> nameList = new ArrayList<>();
    private OnListener listener;
    private OnBranchSelectListener branchSelectListener;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLivePush(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_LIVE_PUSH) {
            String address = (String) event.obj;
            String name = event.obj2 != null ? (String) event.obj2 : "";
            if (address != null && !address.isEmpty()) {
                // 推送的直播源，直接添加到列表
                JsonObject newObj = new JsonObject();
                newObj.addProperty("name", name != null ? name : "");
                newObj.addProperty("url", address);
                // 检查是否已存在
                boolean exists = false;
                for (int i = 0; i < livesGroups.size(); i++) {
                    String existUrl = livesGroups.get(i).getAsJsonObject().has("url") ? livesGroups.get(i).getAsJsonObject().get("url").getAsString() : "";
                    if (address.equals(existUrl)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    livesGroups.add(newObj);
                    Hawk.put(HawkConfig.LIVE_GROUP_LIST, livesGroups);
                    String storeName = getStoreName();
                    String displayName = buildDisplayName(name, storeName, nameList.size());
                    nameList.add(displayName);
                    if (adapter != null) {
                        adapter.setData(nameList, ApiConfig.getLiveGroupIndex());
                    }
                    Toast.makeText(getContext(), "直播源已推送并添加到列表", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "该直播源已存在", Toast.LENGTH_SHORT).show();
                }
            }
            if (playLoading != null) {
                playLoading.setVisibility(View.GONE);
            }
        }
    }

    public LiveSourceDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.live_source_dialog_select);
        setCanceledOnTouchOutside(false);

        inputSourceName = findViewById(R.id.input_sourceName);
        inputSourceUrl = findViewById(R.id.input_source_url);
        playLoading = findViewById(R.id.play_loading);
        recyclerView = findViewById(R.id.list);

        // 初始化直播源分支列表
        initLiveBranchList();

        // 确定按钮
        findViewById(R.id.inputSubmit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = inputSourceName.getText().toString().trim();
                String address = inputSourceUrl.getText().toString().trim();
                if (address.isEmpty()) {
                    Toast.makeText(getContext(), "请输入直播源地址！", Toast.LENGTH_SHORT).show();
                    return;
                }
                LiveSourceBean bean = new LiveSourceBean();
                bean.setSourceName(name);
                bean.setSourceUrl(address);
                if (listener != null) {
                    listener.onAdd(bean);
                }
                inputSourceName.setText("");
                inputSourceUrl.setText("");
            }
        });

        // 存储权限按钮
        View permissionBtn = findViewById(R.id.permission_Submit);
        if (permissionBtn != null) {
            permissionBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (XXPermissions.isGranted(getContext(), Permission.Group.STORAGE)) {
                        Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                        v.setVisibility(View.GONE);
                    } else {
                        XXPermissions.with(getContext())
                                .permission(Permission.Group.STORAGE)
                                .request(new OnPermissionCallback() {
                                    @Override
                                    public void onGranted(java.util.List<String> permissions, boolean all) {
                                        if (all) {
                                            Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
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
                }
            });
        }

        // 刷新二维码
        refreshQRCode();
    }

    /**
     * 初始化直播源分支列表
     */
    private void initLiveBranchList() {
        livesGroups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        int currentIndex = ApiConfig.getLiveGroupIndex();
        if (currentIndex >= livesGroups.size()) currentIndex = 0;
        final int selectIdx = currentIndex;

        String storeName = getStoreName();

        // 构建显示名称列表
        nameList.clear();
        for (int i = 0; i < livesGroups.size(); i++) {
            JsonObject obj = livesGroups.get(i).getAsJsonObject();
            String entryName = obj.has("name") ? obj.get("name").getAsString().trim() : "";
            nameList.add(buildDisplayName(entryName, storeName, i));
        }

        // 设置适配器
        adapter = new LiveSourceSelectAdapter(
            new LiveSourceSelectAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(String name, int position) {
                    // 切换直播分支
                    ApiConfig.setLiveGroupIndex(position);
                    // 重新加载直播配置
                    ApiConfig.get().loadLiveConfig(false, new ApiConfig.LoadConfigCallback() {
                        @Override
                        public void success() {
                            Toast.makeText(getContext(), "已切换到: " + name, Toast.LENGTH_SHORT).show();
                            if (branchSelectListener != null) {
                                branchSelectListener.onBranchSelected(name);
                            }
                        }

                        @Override
                        public void error(String error) {
                            Toast.makeText(getContext(), "直播源切换失败: " + error, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void notice(String msg) {
                        }
                    });
                }
            },
            new LiveSourceSelectAdapter.OnDeleteClickListener() {
                @Override
                public void onDeleteClick(int position) {
                    livesGroups.remove(position);
                    nameList.remove(position);
                    if (livesGroups.size() == 0) {
                        Hawk.delete(HawkConfig.LIVE_GROUP_LIST);
                        ApiConfig.setLiveGroupIndex(0);
                    } else {
                        Hawk.put(HawkConfig.LIVE_GROUP_LIST, livesGroups);
                        int curIdx = ApiConfig.getLiveGroupIndex();
                        if (curIdx >= livesGroups.size()) {
                            curIdx = livesGroups.size() - 1;
                            ApiConfig.setLiveGroupIndex(curIdx);
                        }
                    }
                    adapter.setData(nameList, ApiConfig.getLiveGroupIndex());
                    Toast.makeText(getContext(), "已删除", Toast.LENGTH_SHORT).show();
                }
            }
        );

        if (recyclerView != null) {
            recyclerView.setAdapter(adapter);
            adapter.setData(nameList, selectIdx);
            recyclerView.setSelectedPosition(selectIdx);
            if (selectIdx < 10) {
                recyclerView.setSelection(selectIdx);
            }
        }
    }

    /**
     * 构建直播源显示名称（参照ysc o0oOo0O0.OooO0oO 第145-151行）
     */
    private String buildDisplayName(String entryName, String storeName, int index) {
        if (entryName == null) entryName = "";
        if (storeName == null || storeName.isEmpty()) storeName = "";
        // 分支名为空/http/clan → "仓库名直播"
        if (entryName.isEmpty() || entryName.startsWith("http") || entryName.startsWith("clan")) {
            if (!storeName.isEmpty()) return storeName + "直播";
            return "线路" + (index + 1);
        }
        // 否则 → "分支名直播仓库名"
        if (!storeName.isEmpty()) return entryName + "直播" + storeName;
        return entryName;
    }

    /**
     * 获取当前仓库名
     */
    private String getStoreName() {
        String name = Hawk.get(HawkConfig.STORE_API_NAME, "");
        if (name != null && !name.isEmpty()) return name;
        try {
            String json = Hawk.get(HawkConfig.CUSTOM_STORE_HOUSE_SELECTED, "");
            if (json != null && !json.isEmpty()) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                MoreSourceBean selected = gson.fromJson(json, MoreSourceBean.class);
                if (selected != null && selected.getSourceName() != null) {
                    return selected.getSourceName();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
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

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    public void setOnBranchSelectListener(OnBranchSelectListener listener) {
        this.branchSelectListener = listener;
    }

    public interface OnListener {
        void onAdd(LiveSourceBean bean);
    }

    public interface OnBranchSelectListener {
        void onBranchSelected(String displayName);
    }
}
