package com.github.tvbox.toolbar;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.toolbar.ui.activity.AppsActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * TV 顶部工具栏组件
 * 包含 5 个功能按钮：tvWifi（网络）、tbStyle（布局切换）、tvDraw（应用抽屉）、tvMenu（系统设置）、tvDate（日期时间）
 *
 * 使用方式：
 * 在布局 XML 中添加：
 *   &lt;com.github.tvbox.toolbar.TvToolbarView
 *       android:id="@+id/tvToolbar"
 *       android:layout_width="match_parent"
 *       android:layout_height="wrap_content" /&gt;
 *
 * 在 Activity/Fragment 中：
 *   tvToolbar.setSelfPackageName(getPackageName());
 *   tvToolbar.setOnStyleChangeListener(new TvToolbarView.OnStyleChangeListener() {
 *       @Override
 *       public void onStyleChanged(boolean isGridStyle) {
 *           // isGridStyle == true  -> 网格布局（上下图标）
 *           // isGridStyle == false -> 横排布局（左右图标）
 *       }
 *   });
 *   tvToolbar.setStyleGrid(true);  // 设置初始布局模式（可选，默认横排）
 *   tvToolbar.onStart();  // 在 onResume 中调用
 *   tvToolbar.onStop();   // 在 onPause 中调用
 *   tvToolbar.onDestroy(); // 在 onDestroy 中调用
 */
public class TvToolbarView extends LinearLayout {

    private ImageView tvWifi;
    private ImageView tbStyle;
    private ImageView tvDraw;
    private ImageView tvMenu;
    private TextView tvDate;

    private final Handler mHandler = new Handler();
    private String selfPackageName = "";
    private boolean isStarted = false;

    /** 当前是否为网格布局模式 */
    private boolean isGridStyle = false;

    /** 布局切换监听器 */
    private OnStyleChangeListener onStyleChangeListener;

    /**
     * 布局切换事件监听接口
     */
    public interface OnStyleChangeListener {
        /**
         * 布局模式发生变化时回调
         * @param isGridStyle true = 网格布局（上下），false = 横排布局（左右）
         */
        void onStyleChanged(boolean isGridStyle);
    }

    private final Runnable mClockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClock();
            mHandler.postDelayed(this, 1000);
        }
    };

    public TvToolbarView(Context context) {
        super(context);
        init(context);
    }

    public TvToolbarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TvToolbarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int paddingHorizontal = context.getResources().getDimensionPixelSize(R.dimen.toolbar_padding_h);
        int paddingVertical = context.getResources().getDimensionPixelSize(R.dimen.toolbar_padding_v);
        setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
        setBackgroundColor(0x00000000);

        LayoutInflater.from(context).inflate(R.layout.toolbar_topbar_content, this, true);

        tvWifi = findViewById(R.id.tbWifi);
        tbStyle = findViewById(R.id.tbStyle);
        tvDraw = findViewById(R.id.tbDrawer);
        tvMenu = findViewById(R.id.tbMenu);
        tvDate = findViewById(R.id.tbDate);

        // --- tvWifi: 网络状态图标 + 点击进入 WiFi 设置 ---
        updateNetworkIcon();
        tvWifi.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    v.getContext().startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } catch (Exception ignored) {
                }
            }
        });

        // --- tbStyle: 布局切换（网格/横排） ---
        updateStyleIcon();
        tbStyle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // 切换布局模式
                isGridStyle = !isGridStyle;
                updateStyleIcon();
                // 显示 Toast 提示
                if (isGridStyle) {
                    Toast.makeText(v.getContext(), R.string.toolbar_style_grid, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(v.getContext(), R.string.toolbar_style_line, Toast.LENGTH_SHORT).show();
                }
                // 通知宿主应用
                if (onStyleChangeListener != null) {
                    onStyleChangeListener.onStyleChanged(isGridStyle);
                }
            }
        });

        // --- tvDraw: 应用抽屉 ---
        tvDraw.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                v.getContext().startActivity(new Intent(v.getContext(), AppsActivity.class));
            }
        });

        // --- tvMenu: 系统设置（点击） + 应用详情（长按）---
        tvMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // 打开系统设置
                try {
                    v.getContext().startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {
                }
            }
        });
        tvMenu.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // 打开当前应用详情
                try {
                    v.getContext().startActivity(new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", selfPackageName, null)));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        // --- tvDate: 实时时钟 + 点击进入日期设置 ---
        tvDate.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    v.getContext().startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
                } catch (Exception ignored) {
                }
            }
        });
    }

    /**
     * 设置宿主应用的包名（用于排除自身显示在应用抽屉中）
     */
    public void setSelfPackageName(String packageName) {
        this.selfPackageName = packageName;
    }

    /**
     * 设置布局切换监听器
     * @param listener 监听器实例
     */
    public void setOnStyleChangeListener(OnStyleChangeListener listener) {
        this.onStyleChangeListener = listener;
    }

    /**
     * 设置当前布局模式并更新图标
     * @param gridStyle true = 网格布局（上下图标），false = 横排布局（左右图标）
     */
    public void setStyleGrid(boolean gridStyle) {
        this.isGridStyle = gridStyle;
        updateStyleIcon();
    }

    /**
     * 获取当前布局模式
     * @return true = 网格布局，false = 横排布局
     */
    public boolean isStyleGrid() {
        return isGridStyle;
    }

    /**
     * 在宿主 Activity.onResume() 中调用
     */
    public void onStart() {
        if (!isStarted) {
            isStarted = true;
            // 只有日期可见时才启动时钟刷新
            if (tvDate.getVisibility() == VISIBLE) {
                mHandler.post(mClockRunnable);
            }
        }
        updateNetworkIcon();
    }

    /**
     * 在宿主 Activity.onPause() 中调用
     */
    public void onStop() {
        isStarted = false;
        mHandler.removeCallbacks(mClockRunnable);
    }

    /**
     * 在宿主 Activity.onDestroy() 中调用
     */
    public void onDestroy() {
        onStop();
    }

    /**
     * 控制网络图标的可见性
     */
    public void setWifiVisible(boolean visible) {
        tvWifi.setVisibility(visible ? VISIBLE : GONE);
    }

    /**
     * 控制布局切换按钮的可见性
     */
    public void setStyleVisible(boolean visible) {
        tbStyle.setVisibility(visible ? VISIBLE : GONE);
    }

    /**
     * 控制应用抽屉图标的可见性
     */
    public void setDrawerVisible(boolean visible) {
        tvDraw.setVisibility(visible ? VISIBLE : GONE);
    }

    /**
     * 控制设置图标的可见性
     */
    public void setMenuVisible(boolean visible) {
        tvMenu.setVisibility(visible ? VISIBLE : GONE);
    }

    /**
     * 控制日期的可见性
     */
    public void setDateVisible(boolean visible) {
        tvDate.setVisibility(visible ? VISIBLE : GONE);
        if (visible && isStarted) {
            // 恢复时钟刷新
            mHandler.removeCallbacks(mClockRunnable);
            mHandler.post(mClockRunnable);
        } else {
            // 隐藏时停掉时钟 Runnable，避免无谓的每秒刷新
            mHandler.removeCallbacks(mClockRunnable);
        }
    }

    /**
     * 控制所有按钮的 focusable 状态（用于顶部栏隐藏/显示动画）
     */
    public void setAllButtonsFocusable(boolean focusable) {
        tvWifi.setFocusable(focusable);
        tbStyle.setFocusable(focusable);
        tvDraw.setFocusable(focusable);
        tvMenu.setFocusable(focusable);
    }

    /**
     * 将焦点移到工具栏的第一个可聚焦按钮
     * <p>
     * 供宿主 Activity 在合适的时机调用（例如：下方列表按上键到边界时），
     * 让遥控器焦点能进入工具栏子按钮。
     */
    public void focusFirstButton() {
        if (tvWifi != null && tvWifi.isFocusable()) {
            tvWifi.requestFocus();
        } else if (tbStyle != null && tbStyle.isFocusable()) {
            tbStyle.requestFocus();
        } else if (tvDraw != null && tvDraw.isFocusable()) {
            tvDraw.requestFocus();
        } else if (tvMenu != null && tvMenu.isFocusable()) {
            tvMenu.requestFocus();
        }
    }

    // ========== 内部方法 ==========

    /**
     * 更新布局切换按钮的图标
     */
    private void updateStyleIcon() {
        if (isGridStyle) {
            tbStyle.setImageResource(R.drawable.toolbar_ic_up_down);
        } else {
            tbStyle.setImageResource(R.drawable.toolbar_ic_left_right);
        }
    }

    private void updateNetworkIcon() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                tvWifi.setImageResource(R.drawable.toolbar_ic_wifi_no);
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) {
                    tvWifi.setImageResource(R.drawable.toolbar_ic_wifi_no);
                    return;
                }
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    tvWifi.setImageResource(R.drawable.toolbar_ic_wifi_no);
                    return;
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    tvWifi.setImageResource(R.drawable.toolbar_ic_wifi);
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    tvWifi.setImageResource(R.drawable.toolbar_ic_mobile);
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    tvWifi.setImageResource(R.drawable.toolbar_ic_lan);
                } else {
                    tvWifi.setImageResource(R.drawable.toolbar_ic_wifi_no);
                }
            } else {
                // 兼容 API 21-22（minSdk 21）
                android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                if (info != null && info.isConnectedOrConnecting()) {
                    int type = info.getType();
                    if (type == ConnectivityManager.TYPE_WIFI) {
                        tvWifi.setImageResource(R.drawable.toolbar_ic_wifi);
                    } else if (type == ConnectivityManager.TYPE_MOBILE) {
                        tvWifi.setImageResource(R.drawable.toolbar_ic_mobile);
                    } else if (type == ConnectivityManager.TYPE_ETHERNET) {
                        tvWifi.setImageResource(R.drawable.toolbar_ic_lan);
                    } else {
                        tvWifi.setImageResource(R.drawable.toolbar_ic_wifi_no);
                    }
                } else {
                    tvWifi.setImageResource(R.drawable.toolbar_ic_wifi_no);
                }
            }
        } catch (Exception e) {
            tvWifi.setImageResource(R.drawable.toolbar_ic_wifi_no);
        }
    }

    private void updateClock() {
        try {
            Date date = new Date();
            Locale locale = Locale.getDefault();
            boolean isChinese = locale.getLanguage().startsWith("zh");
            String pattern;
            if (isChinese) {
                pattern = "MM\u6708dd\u65E5 | EE HH:mm";
            } else {
                pattern = "dd MMM | EE HH:mm";
            }
            SimpleDateFormat timeFormat = new SimpleDateFormat(pattern, locale);
            tvDate.setText(timeFormat.format(date));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========== 状态保存与恢复 ==========

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.isStarted = this.isStarted;
        ss.isGridStyle = this.isGridStyle;
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        this.isStarted = ss.isStarted;
        this.isGridStyle = ss.isGridStyle;
        updateStyleIcon();
    }

    static class SavedState extends BaseSavedState {
        boolean isStarted;
        boolean isGridStyle;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel in) {
            super(in);
            isStarted = in.readByte() != 0;
            isGridStyle = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeByte((byte) (isStarted ? 1 : 0));
            out.writeByte((byte) (isGridStyle ? 1 : 0));
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
