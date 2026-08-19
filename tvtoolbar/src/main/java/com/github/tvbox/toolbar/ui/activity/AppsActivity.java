package com.github.tvbox.toolbar.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.BounceInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.toolbar.R;
import com.github.tvbox.toolbar.bean.AppInfo;
import com.github.tvbox.toolbar.util.FastClickCheckUtil;
import com.github.tvbox.toolbar.util.ToolbarConfig;
import com.github.tvbox.toolbar.widget.FocusRecyclerView;
import com.github.tvbox.toolbar.widget.GridSpacingItemDecoration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 应用抽屉页面 - 显示已安装的第三方应用
 */
public class AppsActivity extends Activity {

    private TextView tvDelTip;
    private ImageView tvDelete;
    private FocusRecyclerView mGridViewApps;
    private AppsAdapter appsAdapter;
    private boolean delMode = false;
    private String packageName = "";
    private boolean isUnInstallClicked;
    private int appPosition;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        hideSystemUI();
        setContentView(R.layout.toolbar_activity_apps);
        initView();
        initData();
    }

    private void hideSystemUI() {
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        uiOptions |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
        uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE;
        uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(uiOptions);
    }

    private void toggleDelMode() {
        delMode = !delMode;
        ToolbarConfig.deleteMode = delMode;
        appsAdapter.notifyDataSetChanged();
        tvDelTip.setVisibility(delMode ? View.VISIBLE : View.GONE);
    }

    private void initView() {
        tvDelTip = findViewById(R.id.tvDelTip);
        tvDelete = findViewById(R.id.tvDelete);
        mGridViewApps = findViewById(R.id.mGridViewApps);
        mGridViewApps.setHasFixedSize(true);
        mGridViewApps.setLayoutManager(new GridLayoutManager(this, 6));
        appsAdapter = new AppsAdapter();
        mGridViewApps.setAdapter(appsAdapter);
        tvDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDelMode();
            }
        });
        mGridViewApps.setOnBorderKeyListener(new FocusRecyclerView.OnBorderKeyListener() {
            @Override
            public boolean onBorderKey(int direction, View focused) {
                if (direction == View.FOCUS_UP) {
                    tvDelete.setFocusable(true);
                    tvDelete.requestFocus();
                    return true;
                }
                return false;
            }
        });
        mGridViewApps.setOnItemFocusChangeListener(new FocusRecyclerView.OnItemFocusChangeListener() {
            @Override
            public void onItemPreSelected(FocusRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f)
                        .setDuration(300)
                        .setInterpolator(new BounceInterpolator())
                        .start();
            }

            @Override
            public void onItemSelected(FocusRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.2f).scaleY(1.2f)
                        .setDuration(300)
                        .setInterpolator(new BounceInterpolator())
                        .start();
            }
        });
        int spacingPx = getResources().getDimensionPixelSize(R.dimen.toolbar_apps_grid_spacing);
        mGridViewApps.addItemDecoration(new GridSpacingItemDecoration(6, spacingPx, true));
    }

    private void initData() {
        List<AppInfo> appInfos = getInstallApps(getApplicationContext());
        if (appInfos == null) return;
        Collections.sort(appInfos, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        appsAdapter.setData(appInfos);
    }

    /** 供外部调用刷新应用列表（替代 EventBus） */
    public static void refreshApps() {
        // 静态标记，onResume 中检查
        sNeedRefresh = true;
    }

    private static boolean sNeedRefresh = false;

    public List<AppInfo> getInstallApps(Context context) {
        List<AppInfo> items = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null
                    && (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                items.add(AppInfo.get(context, app));
            }
        }
        return items;
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (isUnInstallClicked && !appInstalledOrNot(packageName)) {
            appsAdapter.remove(appPosition);
            isUnInstallClicked = false;
        }
        if (sNeedRefresh) {
            sNeedRefresh = false;
            initData();
        }
    }

    private boolean appInstalledOrNot(String uri) {
        PackageManager pm = getPackageManager();
        boolean app_installed;
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            app_installed = true;
        } catch (PackageManager.NameNotFoundException e) {
            app_installed = false;
        }
        return app_installed;
    }

    @Override
    public void onBackPressed() {
        if (delMode) {
            toggleDelMode();
            return;
        }
        super.onBackPressed();
    }

    // ========== 内部 Adapter（替代 BRVAH） ==========

    class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.VH> {

        private final List<AppInfo> mData = new ArrayList<>();

        void setData(List<AppInfo> data) {
            mData.clear();
            mData.addAll(data);
            notifyDataSetChanged();
        }

        void remove(int position) {
            if (position >= 0 && position < mData.size()) {
                mData.remove(position);
                notifyItemRemoved(position);
            }
        }

        List<AppInfo> getData() {
            return mData;
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.toolbar_item_apps, parent, false);
            final VH holder = new VH(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = holder.getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    FastClickCheckUtil.check(v);
                    AppInfo appInfo = mData.get(pos);
                    if (delMode) {
                        try {
                            Uri packageURI = Uri.parse("package:" + appInfo.getPack());
                            Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
                            uninstallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(uninstallIntent);
                            packageName = appInfo.getPack();
                            isUnInstallClicked = true;
                            appPosition = pos;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            startActivity(getPackageManager().getLaunchIntentForPackage(appInfo.getPack()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    tvDelete.setFocusable(true);
                    toggleDelMode();
                    return true;
                }
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            AppInfo item = mData.get(position);
            holder.delFrameLayout.setVisibility(ToolbarConfig.deleteMode ? View.VISIBLE : View.GONE);
            holder.appName.setText(item.getName());
            holder.ivApps.setImageDrawable(item.getIcon());
        }

        class VH extends RecyclerView.ViewHolder {
            FrameLayout delFrameLayout;
            TextView appName;
            ImageView ivApps;

            VH(View itemView) {
                super(itemView);
                delFrameLayout = itemView.findViewById(R.id.delFrameLayout);
                appName = itemView.findViewById(R.id.appName);
                ivApps = itemView.findViewById(R.id.ivApps);
            }
        }
    }
}