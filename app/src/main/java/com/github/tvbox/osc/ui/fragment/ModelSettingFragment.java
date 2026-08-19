package com.github.tvbox.osc.ui.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.activity.LocalFileActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.AboutDialog;
import com.github.tvbox.osc.ui.dialog.ApiDialog;
import com.github.tvbox.osc.ui.dialog.ApiHistoryDialog;
import com.github.tvbox.osc.ui.dialog.BackupDialog;
import com.github.tvbox.osc.ui.dialog.DanmuApiDialog;
import com.github.tvbox.osc.ui.dialog.SearchRemoteTvDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.XWalkInitDialog;
import com.github.tvbox.osc.util.DanmuHelper;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import com.github.tvbox.osc.bean.LiveSourceBean;
import com.github.tvbox.osc.bean.MoreSourceBean;
import com.github.tvbox.osc.ui.adapter.LineSelectAdapter;
import com.github.tvbox.osc.ui.dialog.LiveSourceDialog;
import com.github.tvbox.osc.ui.dialog.MoreSourceDialog;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONArray;

import okhttp3.HttpUrl;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class ModelSettingFragment extends BaseLazyFragment {
    private static final int REQUEST_LOCAL_CONFIG = 1001;
    private TextView tvDebugOpen;
    private TextView tvMediaCodec;
    private TextView tvParseWebView;
    private TextView tvPlay;
    private TextView tvRender;
    private TextView tvScale;
    private TextView tvApi;
    private TextView tvApiLine;
    private View llApi;
    private View llApiHistory;
    private View llApiLine;
    private TextView tvHomeApi;
    private TextView tvDns;
    private TextView tvHomeRec;
    private TextView tvHistoryNum;
    private TextView tvHistoryMerge;
    private TextView tvSearchView;
    private TextView tvShowPreviewText;
    private TextView tvFastSearchText;
    private TextView tvm3u8AdText;
    private TextView tvAutoSwitchLineText;
    private TextView tvRecStyleText;
    private TextView tvIjkCachePlay;
    private TextView tvHomeDefaultShow;
    private ApiDialog apiDialog;
    private boolean selectLocalLive;
    private TextView tvDanmuOpenText;
    private TextView tvDanmuApiText;
    private TextView tvStoreApi;
    private TextView tvLiveApiText;
    // ========== 线路缓存相关 ==========
    private long currentLineCacheTime = -1;
    private String currentLineCacheKey = "";

    public static ModelSettingFragment newInstance() {
        return new ModelSettingFragment().setArguments();
    }

    public ModelSettingFragment setArguments() {
        return this;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_model;
    }

    @Override
    protected void init() {
        tvFastSearchText = findViewById(R.id.showFastSearchText);
        tvFastSearchText.setText(Hawk.get(HawkConfig.FAST_SEARCH_MODE, true) ? "开启" : "关闭");
        tvm3u8AdText = findViewById(R.id.m3u8AdText);
        tvm3u8AdText.setText(Hawk.get(HawkConfig.M3U8_PURIFY, false) ? "开启" : "关闭");
        tvDanmuOpenText = findViewById(R.id.danmuOpenText);
        tvDanmuOpenText.setText(DanmuHelper.isOpen() ? "开启" : "关闭");
        tvDanmuApiText = findViewById(R.id.danmuApiText);
        refreshDanmuApiText();
        tvAutoSwitchLineText = findViewById(R.id.autoSwitchLineText);
        tvAutoSwitchLineText.setText(Hawk.get(HawkConfig.AUTO_SWITCH_LINE, true) ? "开启" : "关闭");
        tvRecStyleText = findViewById(R.id.showRecStyleText);
        tvRecStyleText.setText(Hawk.get(HawkConfig.HOME_REC_STYLE, false) ? "是" : "否");
        tvShowPreviewText = findViewById(R.id.showPreviewText);
        tvShowPreviewText.setText(Hawk.get(HawkConfig.SHOW_PREVIEW, true) ? "开启" : "关闭");
        tvDebugOpen = findViewById(R.id.tvDebugOpen);
        tvParseWebView = findViewById(R.id.tvParseWebView);
        tvMediaCodec = findViewById(R.id.tvMediaCodec);
        tvPlay = findViewById(R.id.tvPlay);
        tvRender = findViewById(R.id.tvRenderType);
        tvScale = findViewById(R.id.tvScaleType);
        llApi = findViewById(R.id.llApi);
        llApiHistory = findViewById(R.id.llApiHistory);
        llApiLine = findViewById(R.id.llApiLine);
        tvApi = findViewById(R.id.tvApi);
        tvApiLine = findViewById(R.id.tvApiLine);
        tvHomeApi = findViewById(R.id.tvHomeApi);
        tvDns = findViewById(R.id.tvDns);
        tvHomeRec = findViewById(R.id.tvHomeRec);
        tvHistoryNum = findViewById(R.id.tvHistoryNum);
        tvHistoryMerge = findViewById(R.id.tvHistoryMerge);
        tvSearchView = findViewById(R.id.tvSearchView);
        tvIjkCachePlay = findViewById(R.id.tvIjkCachePlay);
        tvMediaCodec.setText(Hawk.get(HawkConfig.IJK_CODEC, "硬解码"));
        tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
        tvParseWebView.setText(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
        tvApi.setText(Hawk.get(HawkConfig.API_URL, ""));
        refreshApiLineText();
        // 配置多仓地址右侧：显示当前选中的仓库名
        tvStoreApi = findViewById(R.id.tvStoreApi);
        refreshStoreApiText();
        // 直播地址右侧：显示当前直播源名称
        tvLiveApiText = findViewById(R.id.text_immersive_switch);
        refreshLiveApiText();
        // 线路切换右侧：显示当前选中的线路名
        final TextView tvMoreSourceApi = findViewById(R.id.tvMoreSourceApi);
        if (tvMoreSourceApi != null) {
            ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
            String current = Hawk.get(HawkConfig.API_URL, "");
            String lineName = "";
            for (String apiLine : apiLines) {
                if (current.equals(HistoryHelper.getApiLineUrl(apiLine))) {
                    lineName = HistoryHelper.getApiLineName(apiLine);
                    break;
                }
            }
            tvMoreSourceApi.setText(lineName);
        }
        // 配置多仓地址 -> 多仓列表对话框
        findViewById(R.id.default_more_store).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                showStoreHouseDialog();
            }
        });
        // 线路切换 -> 从多仓URL动态获取线路
        findViewById(R.id.more_source).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                showLineSwitchDialog();
            }
        });

        // 直播地址（优先显示直播源分支选择列表）
        findViewById(R.id.ll_immersive_switch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                showLiveSourceDialog();
            }
        });

        tvDns.setText(OkGoHelper.dnsHttpsList.get(Hawk.get(HawkConfig.DOH_URL, 0)));
        tvHomeRec.setText(getHomeRecName(Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC)));
        tvHistoryNum.setText(HistoryHelper.getHistoryNumName(Hawk.get(HawkConfig.HISTORY_NUM, 0)));
        tvHistoryMerge.setText(Hawk.get(HawkConfig.HISTORY_MERGE, false) ? "开启" : "关闭");
        tvSearchView.setText(getSearchView(Hawk.get(HawkConfig.SEARCH_VIEW, 0)));
        tvHomeApi.setText(ApiConfig.get().getHomeSourceBean().getName());
        tvScale.setText(PlayerHelper.getScaleName(Hawk.get(HawkConfig.PLAY_SCALE, 0)));
        tvPlay.setText(PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, 0)));
        tvRender.setText(PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, 0)));
        tvIjkCachePlay.setText(Hawk.get(HawkConfig.IJK_CACHE_PLAY, false) ? "开启" : "关闭");
        tvHomeDefaultShow = findViewById(R.id.tvHomeText);
        tvHomeDefaultShow.setText(Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false) ? "直播" : "点播");
        TextView homePageText = findViewById(R.id.home_page_text);
        homePageText.setText(Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false) ? "直播" : "点播");
        // 首选项（直播/点播切换）
        findViewById(R.id.home_page).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.DEFAULT_LOAD_LIVE, !Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false));
                tvHomeDefaultShow.setText(Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false) ? "直播" : "点播");
                homePageText.setText(Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false) ? "直播" : "点播");
            }
        });
        findViewById(R.id.llDebug).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.DEBUG_OPEN, !Hawk.get(HawkConfig.DEBUG_OPEN, false));
                tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
            }
        });
        findViewById(R.id.llParseWebVew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean useSystem = !Hawk.get(HawkConfig.PARSE_WEBVIEW, true);
                Hawk.put(HawkConfig.PARSE_WEBVIEW, useSystem);
                tvParseWebView.setText(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
                if (!useSystem) {
                    Toast.makeText(mContext, "注意: XWalkView只适用于部分低Android版本，Android5.0以上推荐使用系统自带", Toast.LENGTH_LONG).show();
                    XWalkInitDialog dialog = new XWalkInitDialog(mContext);
                    dialog.setOnListener(new XWalkInitDialog.OnListener() {
                        @Override
                        public void onchange() {
                        }
                    });
                    dialog.show();
                }
            }
        });
        findViewById(R.id.llBackup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                BackupDialog dialog = new BackupDialog(mActivity);
                dialog.show();
            }
        });
        findViewById(R.id.llAbout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                AboutDialog dialog = new AboutDialog(mActivity);
                dialog.show();
            }
        });
        findViewById(R.id.llWp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                if (!ApiConfig.get().wallpaper.isEmpty())
                    OkGo.<File>get(ApiConfig.get().wallpaper).execute(new FileCallback(requireActivity().getFilesDir().getAbsolutePath(), "wp") {
                        @Override
                        public void onSuccess(Response<File> response) {
                            ((BaseActivity) requireActivity()).changeWallpaper(true);
                        }

                        @Override
                        public void onError(Response<File> response) {
                            super.onError(response);
                        }

                        @Override
                        public void downloadProgress(Progress progress) {
                            super.downloadProgress(progress);
                        }
                    });
            }
        });
        findViewById(R.id.llWpRecovery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                File wp = new File(requireActivity().getFilesDir().getAbsolutePath() + "/wp");
                if (wp.exists())
                    wp.delete();
                ((BaseActivity) requireActivity()).changeWallpaper(true);
            }
        });
        findViewById(R.id.llHomeApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
                if (sites.size() > 0) {
                    SelectDialog<SourceBean> dialog = new SelectDialog<>(mActivity);
                    dialog.setTip("请选择首页数据源");
                    int select = sites.indexOf(ApiConfig.get().getHomeSourceBean());
                    if (select<0) select = 0;
                    dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
                        @Override
                        public void click(SourceBean value, int pos) {
                            dialog.dismiss();
                            ApiConfig.get().setSourceBean(value);
                            tvHomeApi.setText(ApiConfig.get().getHomeSourceBean().getName());
                            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HOME_SOURCE_CHANGE));
                        }

                        @Override
                        public String getDisplay(SourceBean val) {
                            return val.getName();
                        }
                    }, new DiffUtil.ItemCallback<SourceBean>() {
                        @Override
                        public boolean areItemsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                            return oldItem == newItem;
                        }

                        @Override
                        public boolean areContentsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                            return oldItem.getKey().equals(newItem.getKey());
                        }
                    }, sites, select);
                    dialog.show();
                }
            }
        });
        findViewById(R.id.llDns).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int dohUrl = Hawk.get(HawkConfig.DOH_URL, 0);

                SelectDialog<String> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择安全DNS");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
                    @Override
                    public void click(String value, int pos) {
                        tvDns.setText(OkGoHelper.dnsHttpsList.get(pos));
                        Hawk.put(HawkConfig.DOH_URL, pos);
//                        String url = OkGoHelper.getDohUrl(pos);
//                        OkGoHelper.dnsOverHttps.setUrl(url.isEmpty() ? null : HttpUrl.get(url));
                        OkGoHelper.reloadDns();
                        IjkMediaPlayer.toggleDotPort(pos > 0);
                    }

                    @Override
                    public String getDisplay(String val) {
                        return val;
                    }
                }, new DiffUtil.ItemCallback<String>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                        return oldItem.equals(newItem);
                    }
                }, OkGoHelper.dnsHttpsList, dohUrl);
                dialog.show();
            }
        });
        findViewById(R.id.llApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                apiDialog = new ApiDialog(mActivity);
                ApiDialog dialog = apiDialog;
                EventBus.getDefault().register(dialog);
                dialog.setOnListener(new ApiDialog.OnListener() {
                    @Override
                    public void onchange(String api) {
                        String oldApi = Hawk.get(HawkConfig.API_URL, "");
                        Hawk.put(HawkConfig.API_URL, api);
                        if (!HistoryHelper.isApiLineHistory(api)) {
                            HistoryHelper.clearApiLineList();
                        }
                        tvApi.setText(api);
                        refreshApiLineText();
                        if (!oldApi.equals(api)) {
                            restartAppAfterConfigChanged();
                        }
                    }

                    @Override
                    public void onLocalConfig(boolean live) {
                        openLocalConfig(live);
                    }
                });
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        ((BaseActivity) mActivity).hideSysBar();
                        EventBus.getDefault().unregister(dialog);
                        apiDialog = null;
                    }
                });
                dialog.show();
            }
        });

        findViewById(R.id.llApiHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
                if (history.isEmpty())
                    return;
                String current = Hawk.get(HawkConfig.API_URL, "");
                int idx = 0;
                if (history.contains(current))
                    idx = history.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(mActivity);
                dialog.setTip("历史配置列表");
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String value) {
                        String oldApi = Hawk.get(HawkConfig.API_URL, "");
                        if (!HistoryHelper.isApiLineHistory(value)) {
                            HistoryHelper.clearApiLineList();
                        }
                        Hawk.put(HawkConfig.API_URL, value);
                        Hawk.put(HawkConfig.LIVE_API_URL, value);
                        HistoryHelper.setLiveApiHistory(value);
                        tvApi.setText(value);
                        refreshApiLineText();
                        dialog.dismiss();
                        if (!oldApi.equals(value)) {
                            restartAppAfterConfigChanged();
                        }
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        Hawk.put(HawkConfig.API_HISTORY, data);
                    }
                }, history, idx);
                dialog.show();
            }
        });

        findViewById(R.id.llApiLine).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
                if (apiLines.isEmpty()) {
                    Toast.makeText(mContext, "线路列表为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                String current = Hawk.get(HawkConfig.API_URL, "");
                int idx = 0;
                for (int i = 0; i < apiLines.size(); i++) {
                    if (current.equals(HistoryHelper.getApiLineUrl(apiLines.get(i)))) {
                        idx = i;
                        break;
                    }
                }
                SelectDialog<String> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("线路选择");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
                    @Override
                    public void click(String value, int pos) {
                        String newApi = HistoryHelper.getApiLineUrl(value);
                        String oldApi = Hawk.get(HawkConfig.API_URL, "");
                        if (newApi.isEmpty()) {
                            return;
                        }
                        Hawk.put(HawkConfig.API_URL, newApi);
                        Hawk.put(HawkConfig.LIVE_API_URL, newApi);
                        HistoryHelper.setLiveApiHistory(newApi);
                        tvApi.setText(newApi);
                        refreshApiLineText();
                        dialog.dismiss();
                        if (!oldApi.equals(newApi)) {
                            restartAppAfterConfigChanged();
                        }
                    }

                    @Override
                    public String getDisplay(String val) {
                        return HistoryHelper.getApiLineName(val);
                    }
                }, SelectDialogAdapter.stringDiff, apiLines, idx);
                dialog.show();
            }
        });


        findViewById(R.id.llMediaCodec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<IJKCode> ijkCodes = ApiConfig.get().getIjkCodes();
                if (ijkCodes == null || ijkCodes.size() == 0)
                    return;
                FastClickCheckUtil.check(v);

                int defaultPos = 0;
                String ijkSel = Hawk.get(HawkConfig.IJK_CODEC, "硬解码");
                for (int j = 0; j < ijkCodes.size(); j++) {
                    if (ijkSel.equals(ijkCodes.get(j).getName())) {
                        defaultPos = j;
                        break;
                    }
                }

                SelectDialog<IJKCode> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择IJK解码");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<IJKCode>() {
                    @Override
                    public void click(IJKCode value, int pos) {
                        value.selected(true);
                        tvMediaCodec.setText(value.getName());
                    }

                    @Override
                    public String getDisplay(IJKCode val) {
                        return val.getName();
                    }
                }, new DiffUtil.ItemCallback<IJKCode>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull IJKCode oldItem, @NonNull @NotNull IJKCode newItem) {
                        return oldItem == newItem;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull IJKCode oldItem, @NonNull @NotNull IJKCode newItem) {
                        return oldItem.getName().equals(newItem.getName());
                    }
                }, ijkCodes, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llScale).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_SCALE, 0);
                ArrayList<Integer> players = new ArrayList<>();
                players.add(0);
                players.add(1);
                players.add(2);
                players.add(3);
                players.add(4);
                players.add(5);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认画面缩放");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_SCALE, value);
                        tvScale.setText(PlayerHelper.getScaleName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getScaleName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, players, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llPlay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
                int defaultPos = 0;
                ArrayList<Integer> players = PlayerHelper.getExistPlayerTypes();
                ArrayList<Integer> renders = new ArrayList<>();
                for(int p = 0; p<players.size(); p++) {
                    renders.add(p);
                    if (players.get(p) == playerType) {
                        defaultPos = p;
                    }
                }
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认播放器");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Integer thisPlayerType = players.get(pos);
                        Hawk.put(HawkConfig.PLAY_TYPE, thisPlayerType);
                        tvPlay.setText(PlayerHelper.getPlayerName(thisPlayerType));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        Integer playerType = players.get(val);
                        return PlayerHelper.getPlayerName(playerType);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llRender).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_RENDER, 0);
                ArrayList<Integer> renders = new ArrayList<>();
                renders.add(0);
                renders.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认渲染方式");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_RENDER, value);
                        tvRender.setText(PlayerHelper.getRenderName(value));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getRenderName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llHomeRec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择首页列表数据");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HOME_REC, value);
                        tvHomeRec.setText(getHomeRecName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getHomeRecName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llSearchView).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.SEARCH_VIEW, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择搜索视图");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.SEARCH_VIEW, value);
                        tvSearchView.setText(getSearchView(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getSearchView(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        SettingActivity.callback = new SettingActivity.DevModeCallback() {
            @Override
            public void onChange() {
                findViewById(R.id.llDebug).setVisibility(View.VISIBLE);
            }
        };

        findViewById(R.id.showPreview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.SHOW_PREVIEW, !Hawk.get(HawkConfig.SHOW_PREVIEW, true));
                tvShowPreviewText.setText(Hawk.get(HawkConfig.SHOW_PREVIEW, true) ? "开启" : "关闭");
            }
        });
        findViewById(R.id.llHistoryNum).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HISTORY_NUM, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("保留历史记录数量");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HISTORY_NUM, value);
                        tvHistoryNum.setText(HistoryHelper.getHistoryNumName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return HistoryHelper.getHistoryNumName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.showFastSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.FAST_SEARCH_MODE, !Hawk.get(HawkConfig.FAST_SEARCH_MODE, true));
                tvFastSearchText.setText(Hawk.get(HawkConfig.FAST_SEARCH_MODE, true) ? "开启" : "关闭");
            }
        });
        findViewById(R.id.m3u8Ad).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean is_purify=Hawk.get(HawkConfig.M3U8_PURIFY, false);
                Hawk.put(HawkConfig.M3U8_PURIFY, !is_purify);
                tvm3u8AdText.setText(!is_purify ? "开启" : "关闭");
            }
        });
        findViewById(R.id.danmuOpen).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean open = !DanmuHelper.isOpen();
                DanmuHelper.setOpen(open);
                tvDanmuOpenText.setText(open ? "开启" : "关闭");
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, open));
            }
        });
        findViewById(R.id.danmuApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                DanmuApiDialog dialog = new DanmuApiDialog(mActivity);
                dialog.setOnListener(new DanmuApiDialog.OnListener() {
                    @Override
                    public void onChange(String api) {
                        refreshDanmuApiText();
                    }
                });
                dialog.show();
            }
        });
        findViewById(R.id.autoSwitchLine).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean enable = !Hawk.get(HawkConfig.AUTO_SWITCH_LINE, true);
                Hawk.put(HawkConfig.AUTO_SWITCH_LINE, enable);
                tvAutoSwitchLineText.setText(enable ? "开启" : "关闭");
            }
        });
        findViewById(R.id.llHomeRecStyle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.HOME_REC_STYLE, !Hawk.get(HawkConfig.HOME_REC_STYLE, false));
                tvRecStyleText.setText(Hawk.get(HawkConfig.HOME_REC_STYLE, false) ? "是" : "否");
            }
        });

        findViewById(R.id.llHistoryMerge).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean historyMerge = !Hawk.get(HawkConfig.HISTORY_MERGE, false);
                Hawk.put(HawkConfig.HISTORY_MERGE, historyMerge);
                tvHistoryMerge.setText(historyMerge ? "开启" : "关闭");
            }
        });

        //下次进入
        findViewById(R.id.tvHomeLive).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.DEFAULT_LOAD_LIVE, !Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false));
                tvHomeDefaultShow.setText(Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false) ? "直播" : "点播");
            }
        });

        findViewById(R.id.llIjkCachePlay).setOnClickListener((view -> onClickIjkCachePlay(view)));
        findViewById(R.id.llClearCache).setOnClickListener((view -> onClickClearCache(view)));
    }

    private void restartAppAfterConfigChanged() {
        Toast.makeText(mContext, "配置已切换,即将重新加载!", Toast.LENGTH_SHORT).show();
        clearConfigSwitchCache();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mActivity != null && !mActivity.isFinishing()) {
                    mActivity.onBackPressed();
                }
            }
        }, 2500);
    }

    private void clearConfigSwitchCache() {
        try {
            SourceViewModel.clearRuntimeCache();
//            FileUtils.clearSpiderCacheFiles();
            LOG.i("echo-clear-config-switch-cache");
        } catch (Exception e) {
            LOG.i("echo-clear-config-switch-cache-error:" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void restartAppAfterCacheCleared() {
        Toast.makeText(mContext, "缓存已清空,即将回到主页!", Toast.LENGTH_LONG).show();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                restartApp();
            }
        }, 2500);
    }

    private void refreshApiLineText() {
        if (tvApiLine == null) return;
        ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        String current = Hawk.get(HawkConfig.API_URL, "");
        boolean showLine = HistoryHelper.isApiLineUrl(current);
        if (llApiLine != null) {
            llApiLine.setVisibility(showLine ? View.VISIBLE : View.GONE);
        }
        updateApiRowWeight(showLine);
        String lineName = "";
        if (showLine) {
            for (String apiLine : apiLines) {
                if (current.equals(HistoryHelper.getApiLineUrl(apiLine))) {
                    lineName = HistoryHelper.getApiLineName(apiLine);
                    break;
                }
            }
        }
        tvApiLine.setText(lineName);
    }

    private void refreshDanmuApiText() {
        if (tvDanmuApiText == null) return;
        if (DanmakuApi.isUseDefault()) {
            tvDanmuApiText.setText("默认");
            return;
        }
        String custom = Hawk.get(HawkConfig.DANMU_API, "");
        if (!custom.isEmpty()) {
            tvDanmuApiText.setText("自定义");
            return;
        }
        String config = ApiConfig.get().getDanmaku();
        tvDanmuApiText.setText(config.isEmpty() ? "默认" : "接口");
    }

    private void updateApiRowWeight(boolean showLine) {
        if (llApi == null) return;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) llApi.getLayoutParams();
        params.weight = showLine ? 1.0f : 3.08f;
        llApi.setLayoutParams(params);
        if (llApiHistory != null) {
            LinearLayout.LayoutParams historyParams = (LinearLayout.LayoutParams) llApiHistory.getLayoutParams();
            int margin = showLine ? getResources().getDimensionPixelSize(R.dimen.vs_5) : 0;
            historyParams.rightMargin = margin;
            historyParams.setMarginEnd(margin);
            llApiHistory.setLayoutParams(historyParams);
        }
    }

    private void restartApp() {
        if (mContext == null) return;
        Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            System.exit(0);
        }
    }

    private void onClickIjkCachePlay(View v) {
        FastClickCheckUtil.check(v);
        Hawk.put(HawkConfig.IJK_CACHE_PLAY, !Hawk.get(HawkConfig.IJK_CACHE_PLAY, false));
        tvIjkCachePlay.setText(Hawk.get(HawkConfig.IJK_CACHE_PLAY, false) ? "开启" : "关闭");
    }

    private void openLocalConfig(boolean live) {
        selectLocalLive = live;
        if (!XXPermissions.isGranted(mContext, Permission.Group.STORAGE)) {
            Toast.makeText(getContext(), "请选择文件前需要先授予存储权限", Toast.LENGTH_SHORT).show();
            XXPermissions.with(mActivity)
                    .permission(Permission.Group.STORAGE)
                    .request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all) {
                                Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                                openLocalFileActivity(selectLocalLive);
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            if (never) {
                                Toast.makeText(getContext(), "获取存储权限失败,请在系统设置中开启", Toast.LENGTH_SHORT).show();
                                XXPermissions.startPermissionActivity(mActivity, permissions);
                            } else {
                                Toast.makeText(getContext(), "获取存储权限失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
            return;
        }
        openLocalFileActivity(live);
    }

    private void openLocalFileActivity(boolean live) {
        Intent intent = new Intent(mContext, LocalFileActivity.class);
        intent.putExtra(LocalFileActivity.EXTRA_LIVE, live);
        startActivityForResult(intent, REQUEST_LOCAL_CONFIG);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_LOCAL_CONFIG || resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        String api = localConfigToApi(data.getData());
        if (api == null || api.isEmpty()) {
            Toast.makeText(getContext(), "读取本地配置失败", Toast.LENGTH_SHORT).show();
            return;
        }
        if (apiDialog != null) {
            apiDialog.setLocalApi(api, selectLocalLive);
        }
    }

    private String localConfigToApi(Uri uri) {
        String path = getPathFromUri(uri);
        if (path == null || path.isEmpty()) {
            path = copyUriToLocalConfig(uri);
        }
        if (path == null || path.isEmpty()) {
            return "";
        }
        String storageRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (path.startsWith(storageRoot)) {
            return "clan://localhost/" + path.substring(storageRoot.length()).replaceFirst("^/+", "");
        }
        path = copyUriToLocalConfig(uri);
        if (path != null && path.startsWith(storageRoot)) {
            return "clan://localhost/" + path.substring(storageRoot.length()).replaceFirst("^/+", "");
        }
        return "";
    }

    private String getPathFromUri(Uri uri) {
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            }
            if (DocumentsContract.isDocumentUri(mContext, uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                    String[] split = docId.split(":");
                    if (split.length > 1 && "primary".equalsIgnoreCase(split[0])) {
                        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + split[1];
                    }
                }
                if ("com.android.providers.downloads.documents".equals(uri.getAuthority()) && docId.startsWith("raw:")) {
                    return docId.substring(4);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String copyUriToLocalConfig(Uri uri) {
        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = mContext.getContentResolver().openInputStream(uri);
            if (input == null) return "";
            File dir = new File(FileUtils.getExternalCachePath(), "config");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, getDisplayName(uri));
            output = new FileOutputStream(file);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return file.getAbsolutePath();
        } catch (Throwable th) {
            th.printStackTrace();
            return "";
        } finally {
            try {
                if (output != null) output.close();
            } catch (Throwable ignored) {
            }
            try {
                if (input != null) input.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private String getDisplayName(Uri uri) {
        String name = "local_config.json";
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String displayName = cursor.getString(index);
                    if (displayName != null && !displayName.isEmpty()) {
                        name = displayName;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return name;
    }

    private void onClickClearCache(View v) {
        FastClickCheckUtil.check(v);
        String cachePath = FileUtils.getCachePath();
        File cacheDir = new File(cachePath);
        new Thread(() -> {
            try {
                ApiConfig.get().clearSpiderCache();
                if(cacheDir.exists())FileUtils.cleanDirectory(cacheDir);
                FileUtils.clearSpiderCacheFiles();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (mActivity != null) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            restartAppAfterCacheCleared();
                        }
                    });
                }
            }
        }).start();
    }

    public static SearchRemoteTvDialog loadingSearchRemoteTvDialog;
    public static List<String> remoteTvHostList;
    public static boolean foundRemoteTv;

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        SettingActivity.callback = null;
    }

    String getHomeRecName(int type) {
        if (type == 1) {
            return "站点推荐";
        } else if (type == 2) {
            return "观看历史";
        } else {
            return "豆瓣热播";
        }
    }

    String getSearchView(int type) {
        if (type == 0) {
            return "文字列表";
        } else {
            return "缩略图";
        }
    }

    // ===================== 多仓配置/线路切换相关方法 =====================

    /**
     * 显示多仓配置对话框（参照ysc的h21）
     * 展示已配置的仓库列表，支持新增、删除、选择仓库
     * 选中仓库后自动弹出线路切换对话框（参照ysc a21: dismiss后调用y11.OooO0O0）
     */
    private void showStoreHouseDialog() {
        MoreSourceDialog dialog = new MoreSourceDialog(mActivity);
        // EventBus注册由dialog.show()内部自行处理，此处不再重复注册
        // 参照ysc a21: 选中仓库后dismiss，然后直接弹出线路切换
        dialog.setOnStoreSelectedListener(new MoreSourceDialog.OnStoreSelectedListener() {
            @Override
            public void onStoreSelected(MoreSourceBean bean) {
                showLineSwitchDialog();
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                ((BaseActivity) mActivity).hideSysBar();
                // EventBus反注册由dialog.dismiss()内部自行处理，此处不再重复
            }
        });
        dialog.show();
    }

    /**
     * 线路切换对话框（参照ysc的y11）
     * 1. 如果有多仓配置，先展示多仓选择列表
     * 2. 选中仓库后，从仓库URL动态获取线路列表
     * 3. 展示线路选择对话框
     */
    private void showLineSwitchDialog() {
        // 获取已配置的仓库列表
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

        // 如果历史线路列表为空且没有仓库配置（参照ysc y11.OooO0O0: 弹出多仓配置对话框而非Toast）
        if (lineHistory.isEmpty() && storeList.isEmpty()) {
            // 尝试从已保存的 API_LINE_LIST 加载（兼容旧数据，转为MoreSourceBean）
            ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
            if (!apiLines.isEmpty()) {
                ArrayList<MoreSourceBean> lineBeans = new ArrayList<>();
                for (String line : apiLines) {
                    MoreSourceBean bean = new MoreSourceBean();
                    bean.setSourceName(HistoryHelper.getApiLineName(line));
                    bean.setSourceUrl(HistoryHelper.getApiLineUrl(line));
                    bean.setShowDelete(true); // 旧数据都是历史线路，可删除
                    lineBeans.add(bean);
                }
                showLineSelectDialog(lineBeans);
                return;
            }
            // 获取当前选中的仓库
            MoreSourceBean selectedStore = getSelectedStoreHouse();
            if (selectedStore == null || selectedStore.getSourceUrl() == null || selectedStore.getSourceUrl().isEmpty()) {
                // 线路为空且没有选中仓库 → 直接弹出多仓配置对话框（参照ysc: new h21(activity).show()）
                showStoreHouseDialog();
                return;
            }
            // 有选中仓库，继续往下走获取线路
        }

        // 获取当前选中的仓库
        MoreSourceBean selectedStore = getSelectedStoreHouse();

        // 如果有选中的仓库，直接从该仓库获取线路
        if (selectedStore != null && selectedStore.getSourceUrl() != null && !selectedStore.getSourceUrl().isEmpty()) {
            fetchStoreLinesAndShow(selectedStore);
            return;
        }

        // 如果有历史线路但没有选中仓库，直接展示历史线路（都是用户手动添加的，showDelete=true）
        if (!lineHistory.isEmpty()) {
            // 确保所有历史线路都有showDelete=true
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
            // 兜底也弹多仓配置对话框（参照ysc: new h21(activity).show()）
            showStoreHouseDialog();
        }
    }

    /**
     * 显示仓库选择对话框（参照ysc中从h21选择仓库后进入y11）
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
                // 保存选中的仓库
                saveSelectedStoreHouse(value);
                Hawk.put(HawkConfig.STORE_API, value.getSourceUrl());
                if (value.getSourceName() != null && !value.getSourceName().isEmpty()) {
                    Hawk.put(HawkConfig.STORE_API_NAME, value.getSourceName());
                }
                // 从选中的仓库获取线路
                Toast.makeText(mContext, "正在获取线路，请稍候...", Toast.LENGTH_SHORT).show();
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
     * 从仓库URL获取线路并显示线路选择对话框
     * 参照ysc y11.OooO0O0: 支持缓存机制 CACHE_TIME/cacheMode/cacheKey
     */
    private void fetchStoreLinesAndShow(MoreSourceBean storeBean) {
        String storeUrl = storeBean.getSourceUrl();
        if (storeUrl == null || storeUrl.isEmpty()) {
            Toast.makeText(mContext, "仓库地址为空", Toast.LENGTH_SHORT).show();
            return;
        }
        // 复用 ApiConfig.configUrl() 统一处理URL（file://转换、;pk;提取、clan://转换、http补全）
        // 与 MoreSourceDialog.addStoreToLayout 保持一致的URL预处理
        ApiConfig apiConfig = ApiConfig.get();
        final String rawUrl = storeUrl;
        String fetchUrl = apiConfig.configUrl(storeUrl);
        final String configKey = apiConfig.getTempKey();

        // ========== 新增: 线路缓存机制（参照ysc y11.OooO0O0 + h21.OooO00o） ==========
        // 缓存Key格式（参照ysc: "LINE_KEY" + url）
        String cacheKey = "LINE_KEY" + fetchUrl;
        currentLineCacheKey = cacheKey;

        // 读取CACHE_TIME配置（参照ysc: iu.OooOOOo("CACHE_TIME", 1)）
        int cacheDays = Hawk.get(HawkConfig.CACHE_TIME, 1);
        // 缓存时间转换为毫秒（参照ysc: cacheTime = N * 24 * 60 * 60 * 1000）
        // -1 = 永久缓存，0 = 不缓存
        if (cacheDays == -1) {
            currentLineCacheTime = -1L;
        } else {
            currentLineCacheTime = (long) cacheDays * 24 * 60 * 60 * 1000;
        }

        // ========== 新增: -1 URL特殊处理（参照ysc y11: URL含"-1"时走u11回调） ==========
        if (fetchUrl.contains("-1")) {
            handleMinusOneUrl(storeBean, fetchUrl, configKey, rawUrl);
            return;
        }

        // ========== 新增: 检查本地缓存（参照ysc: FIRST_CACHE_THEN_REQUEST模式） ==========
        // 本地地址(127.0.0.1)不使用缓存（参照ysc: !b51.OooooOO(strClanToAddress, "http://127.0.0.1"））
        boolean useCache = !fetchUrl.startsWith("http://127.0.0.1") && cacheDays != 0;

        if (useCache) {
            String cachedBody = readLineCache(cacheKey);
            if (cachedBody != null && !cachedBody.isEmpty()) {
                boolean cacheValid = isLineCacheValid(cacheKey, currentLineCacheTime);
                if (cacheValid) {
                    // 缓存有效，解密后再使用（缓存存的是原始响应）
                    String decryptedCache = ApiConfig.FindResult(cachedBody, configKey);
                    if (rawUrl != null && rawUrl.startsWith("clan")) {
                        decryptedCache = ApiConfig.get().clanContentFix(fetchUrl, decryptedCache);
                    }
                    decryptedCache = ApiConfig.get().fixContentPath(rawUrl != null ? rawUrl : fetchUrl, decryptedCache);
                    parseAndShowLinesFromJson(decryptedCache, fetchUrl, storeBean, configKey, rawUrl);
                    fetchLineFromNetwork(fetchUrl, storeBean, cacheKey, currentLineCacheTime, true, configKey, rawUrl);
                    return;
                }
            }
        }

        // 无缓存或缓存无效，直接请求网络
        fetchLineFromNetwork(fetchUrl, storeBean, cacheKey, currentLineCacheTime, false, configKey, rawUrl);
    }

    /**
     * 从网络获取线路（参照ysc y11.OooO0O0 + h21.OooO00o的st请求）
     * @param isBackgroundUpdate 是否为后台静默更新（缓存有效时后台刷新）
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
                    // gitcode特殊处理（参照ysc: headers("User-Agent", m0.OooOoo0()).headers("Accept", ...)）
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
                            // 后台静默更新：只更新localLineUrls，不弹对话框
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
                                        Toast.makeText(mContext, "网络失败，使用缓存数据", Toast.LENGTH_SHORT).show();
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
                                    Toast.makeText(mContext, "网络异常，使用缓存数据", Toast.LENGTH_SHORT).show();
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
     * 解析JSON并显示线路选择（从fetchStoreLinesAndShow提取的公共方法）
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
        // urls格式：合并线路（参照ysc y11.OooO0Oo）
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
        // 合并历史线路中不重复的（参照ysc y11第167-181行）
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
        // 保存localLineUrls到storeBean（参照ysc: moreSourceBean.setLocalLineUrls）
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
        // 保存到API_LINE_LIST（兼容旧数据）
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
        // 在主线程显示对话框（使用带缓存信息的版本）
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
                                if (tvApi != null) tvApi.setText(directLine.getSourceUrl());
                                restartAppAfterConfigChanged();
                                return;
                            }
                        } catch (Exception ignored) {}
                        Toast.makeText(mContext, "未获取到线路，请检查仓库地址", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showLineSelectDialogWithCacheInfo(allLines);
                }
            });
        }
    }

    /**
     * -1 URL特殊处理（参照ysc y11 + u11）
     * ysc: URL含"-1"时，请求成功和失败都解析urls，区别在于标题显示
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
                                Toast.makeText(mContext, "线路获取失败", Toast.LENGTH_SHORT).show();
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
     * 从JSON解析urls为MoreSourceBean列表（辅助方法）
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

    private void showLineFetchError(final Exception e) {
        if (mActivity != null && !mActivity.isFinishing()) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, "线路获取失败" + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * 解析仓库内容获取线路列表（参照ysc y11.OooO0Oo 和 ApiConfig.parseApiCollection）
     * 支持两种格式：
     * 1. {"urls": [{"url":"...","name":"..."}, ...]} 直接线路
     * 2. {"storeHouse": [{"sourceName":"...","sourceUrl":"..."}, ...]} 多仓格式 → 保存仓库列表，并返回第一个仓库的URL供调用方继续获取线路
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
            // 格式1: 直接urls数组
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
            // 格式2: storeHouse多仓格式（不覆盖CUSTOM_STORE_HOUSE，仓库列表由MoreSourceDialog统一管理）
            if (jsonObject.has("storeHouse") && jsonObject.get("storeHouse") instanceof org.json.JSONArray) {
                org.json.JSONArray storeHouse = jsonObject.getJSONArray("storeHouse");
                ArrayList<MoreSourceBean> stores = new ArrayList<>();
                java.util.LinkedHashMap<String, MoreSourceBean> linkedMap = new java.util.LinkedHashMap<>();
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
                // 不再调用saveStoreHouseList/updateMoreSourceApiText，避免覆盖MoreSourceDialog管理的仓库列表
                // 如果urls也为空（纯storeHouse格式），返回特殊标记让调用方继续获取第一个仓库的线路
                if (apiLines.isEmpty() && !stores.isEmpty()) {
                    String firstStoreUrl = stores.get(0).getSourceUrl();
                    if (firstStoreUrl != null && !firstStoreUrl.isEmpty()) {
                        apiLines.add(HistoryHelper.buildApiLine("", firstStoreUrl));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return apiLines;
    }

    /**
     * 显示线路选择对话框（参照ysc y11.OooO0o0）
     * 使用MoreSourceBean + LineSelectAdapter，支持删除按钮显隐
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
                tvApi.setText(newApi);
                refreshApiLineText();
                dialog.dismiss();
                if (!oldApi.equals(newApi)) {
                    restartAppAfterConfigChanged();
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
     * 显示带缓存信息的线路选择对话框（参照ysc y11.OooO00o标题显示缓存时间）
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
        // 标题显示缓存信息（参照ysc y11.OooO00o）
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
                tvApi.setText(newApi);
                refreshApiLineText();
                dialog.dismiss();
                if (!oldApi.equals(newApi)) {
                    restartAppAfterConfigChanged();
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
     * 更新配置多仓地址和线路切换右侧的文字（storeHouse解析后调用）
     */
    private void updateMoreSourceApiText(ArrayList<MoreSourceBean> stores) {
        // 更新配置多仓地址右侧：显示仓库名
        refreshStoreApiText();
        // 更新线路切换右侧：显示当前线路名
        refreshMoreSourceApiText();
    }

    // ===================== 多仓数据存储辅助方法（兼容ysc的custom_store_house格式） =====================

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
            com.google.gson.Gson gson = new com.google.gson.Gson();
            return gson.fromJson(json, MoreSourceBean.class);
        } catch (Exception e) {
            Hawk.delete(HawkConfig.CUSTOM_STORE_HOUSE_SELECTED);
            return null;
        }
    }

    private void saveSelectedStoreHouse(MoreSourceBean bean) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
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

    /**
     * 刷新配置多仓地址右侧的仓库名显示
     */
    private void refreshStoreApiText() {
        if (tvStoreApi == null) return;
        String storeName = Hawk.get(HawkConfig.STORE_API_NAME, "");
        if (storeName == null || storeName.isEmpty()) {
            // 尝试从已保存的仓库列表中查找选中仓库名
            try {
                String json = Hawk.get(HawkConfig.CUSTOM_STORE_HOUSE_SELECTED, "");
                if (json != null && !json.isEmpty()) {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    MoreSourceBean selected = gson.fromJson(json, MoreSourceBean.class);
                    if (selected != null && selected.getSourceName() != null) {
                        storeName = selected.getSourceName();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        tvStoreApi.setText(storeName);
    }

    /**
     * 刷新线路切换右侧的线路名显示
     */
    private void refreshMoreSourceApiText() {
        TextView tvMoreSourceApi = findViewById(R.id.tvMoreSourceApi);
        if (tvMoreSourceApi == null) return;
        ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        String current = Hawk.get(HawkConfig.API_URL, "");
        String lineName = "";
        for (String apiLine : apiLines) {
            if (current.equals(HistoryHelper.getApiLineUrl(apiLine))) {
                lineName = HistoryHelper.getApiLineName(apiLine);
                break;
            }
        }
        tvMoreSourceApi.setText(lineName);
    }

    // ===================== 线路缓存读写方法（参照ysc zj0/x1缓存核心） =====================

    /**
     * 写入线路缓存
     * @param cacheKey 缓存Key（"LINE_KEY" + url）
     * @param data 缓存数据（JSON字符串）
     * @param cacheTimeMs 缓存时间(毫秒)，-1为永久
     */
    private void writeLineCache(String cacheKey, String data, long cacheTimeMs) {
        try {
            java.io.File cacheDir = mActivity.getCacheDir();
            String fileName = cacheKey.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
            java.io.File cacheFile = new java.io.File(cacheDir, fileName + ".cache");
            long expireTime = cacheTimeMs == -1 ? Long.MAX_VALUE : (System.currentTimeMillis() + cacheTimeMs);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(cacheFile);
            String content = data + "\n---CACHE_SPLIT---\n" + expireTime;
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取线路缓存
     * @return 缓存的JSON数据，无缓存返回null
     */
    private String readLineCache(String cacheKey) {
        try {
            java.io.File cacheDir = mActivity.getCacheDir();
            String fileName = cacheKey.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
            java.io.File cacheFile = new java.io.File(cacheDir, fileName + ".cache");
            if (!cacheFile.exists()) return null;
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(cacheFile));
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

    /**
     * 检查缓存是否有效
     */
    private boolean isLineCacheValid(String cacheKey, long cacheTimeMs) {
        if (cacheTimeMs == -1) return true;
        try {
            java.io.File cacheDir = mActivity.getCacheDir();
            String fileName = cacheKey.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
            java.io.File cacheFile = new java.io.File(cacheDir, fileName + ".cache");
            if (!cacheFile.exists()) return false;
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(cacheFile));
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

    /**
     * 获取缓存过期时间戳
     */
    private long getLineCacheExpireTime(String cacheKey) {
        try {
            java.io.File cacheDir = mActivity.getCacheDir();
            String fileName = cacheKey.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
            java.io.File cacheFile = new java.io.File(cacheDir, fileName + ".cache");
            if (!cacheFile.exists()) return 0;
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(cacheFile));
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

    /**
     * 清除线路缓存（在MoreSourceDialog删除/清空时调用）
     */
    public static void clearAllLineCache(android.content.Context context) {
        try {
            java.io.File cacheDir = context.getCacheDir();
            if (cacheDir != null && cacheDir.exists()) {
                java.io.File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        if (file.getName() != null && (file.getName().startsWith("LINE_KEY") || file.getName().endsWith(".cache"))) {
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
     * 构建带缓存信息的标题（参照ysc y11.OooO00o）
     * 永久缓存: "选择线路\n线路缓存永久生效"
     * 无缓存: "选择线路\n当前使用接口数据"
     * 有缓存: "选择线路\n线路缓存将于X天X小时后更新"
     */
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

    /**
     * 格式化缓存剩余时间（参照ysc y11.OooO00o: 天/小时/分钟/秒）
     */
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

    // ===================== 直播源相关方法 =====================
    private void showLiveSourceDialog() {
        LiveSourceDialog dialog = new LiveSourceDialog(mActivity);
        EventBus.getDefault().register(dialog);
        dialog.setOnListener(new LiveSourceDialog.OnListener() {
            @Override
            public void onAdd(LiveSourceBean bean) {
                Hawk.put(HawkConfig.LIVE_API_URL, bean.getSourceUrl());
                Toast.makeText(mContext, "直播源已保存", Toast.LENGTH_SHORT).show();
                refreshLiveApiText();
            }
        });
        dialog.setOnBranchSelectListener(new LiveSourceDialog.OnBranchSelectListener() {
            @Override
            public void onBranchSelected(String displayName) {
                if (tvLiveApiText != null) {
                    tvLiveApiText.setText(displayName);
                }
                refreshLiveApiText();
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                ((BaseActivity) mActivity).hideSysBar();
                EventBus.getDefault().unregister(dialog);
            }
        });
        dialog.show();
    }

    private void refreshLiveApiText() {
        if (tvLiveApiText == null) return;
        String liveName = "";
        com.google.gson.JsonArray livesGroups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new com.google.gson.JsonArray());
        if (livesGroups.size() > 0) {
            int idx = ApiConfig.getLiveGroupIndex();
            if (idx >= livesGroups.size()) idx = 0;
            com.google.gson.JsonObject obj = livesGroups.get(idx).getAsJsonObject();
            String entryName = obj.has("name") ? obj.get("name").getAsString().trim() : "";
            String storeName = getSelectedStoreHouseName();
            liveName = buildLiveSourceDisplayName(entryName, storeName, idx);
        }
        if (liveName.startsWith("线路") && livesGroups.size() <= 1) {
            String liveUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
            if (!liveUrl.isEmpty()) {
                ArrayList<String> liveHistory = Hawk.get(HawkConfig.LIVE_API_HISTORY, new ArrayList<String>());
                for (String item : liveHistory) {
                    String itemUrl = HistoryHelper.getApiLineUrl(item);
                    if (liveUrl.equals(itemUrl)) {
                        String historyName = HistoryHelper.getApiLineName(item);
                        if (historyName != null && !historyName.isEmpty()) {
                            liveName = historyName;
                            break;
                        }
                    }
                }
            }
        }
        tvLiveApiText.setText(liveName);
    }

    private String buildLiveSourceDisplayName(String entryName, String storeName, int index) {
        if (entryName == null) entryName = "";
        if (storeName == null || storeName.isEmpty()) storeName = "";
        if (entryName.isEmpty() || entryName.startsWith("http") || entryName.startsWith("clan")) {
            if (!storeName.isEmpty()) return storeName + "直播";
            return "线路" + (index + 1);
        }
        if (!storeName.isEmpty()) return entryName + "直播" + storeName;
        return entryName;
    }

    private String getSelectedStoreHouseName() {
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
        try {
            ArrayList<MoreSourceBean> apiHistory = getApiHistoryList();
            String currentApi = Hawk.get(HawkConfig.API_URL, "");
            for (MoreSourceBean bean : apiHistory) {
                if (currentApi.equals(bean.getSourceUrl()) && bean.getSourceName() != null && !bean.getSourceName().isEmpty()) {
                    return bean.getSourceName();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
