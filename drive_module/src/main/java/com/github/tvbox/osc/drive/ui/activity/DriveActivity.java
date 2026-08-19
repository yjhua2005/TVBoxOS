package com.github.tvbox.osc.drive.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.tvbox.osc.drive.DriveModule;
import com.github.tvbox.osc.drive.R;
import com.github.tvbox.osc.drive.bean.DriveFolderFile;
import com.github.tvbox.osc.drive.cache.StorageDrive;
import com.github.tvbox.osc.drive.data.DriveDataManager;
import com.github.tvbox.osc.drive.event.DriveEvent;
import com.github.tvbox.osc.drive.ui.adapter.DriveAdapter;
import com.github.tvbox.osc.drive.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.drive.ui.dialog.AlistDriveDialog;
import com.github.tvbox.osc.drive.ui.dialog.ArchiveExtractDialog;
import com.github.tvbox.osc.drive.ui.dialog.FolderPickerDialog;
import com.github.tvbox.osc.drive.ui.dialog.FtpDriveDialog;
import com.github.tvbox.osc.drive.ui.dialog.FtpServerConfigDialog;
import com.github.tvbox.osc.drive.ui.dialog.SmbDriveDialog;
import com.github.tvbox.osc.drive.ui.dialog.SelectDialog;
import com.github.tvbox.osc.drive.ui.dialog.WebdavDialog;
import com.github.tvbox.osc.drive.util.ArchiveHelper;
import com.github.tvbox.osc.drive.viewmodel.ArchiveBrowseViewModel;
import com.github.tvbox.osc.drive.util.DriveConfig;
import com.github.tvbox.osc.drive.util.FastClickCheckUtil;
import com.github.tvbox.osc.drive.util.StorageDriveType;
import com.github.tvbox.osc.drive.util.StringUtils;
import com.github.tvbox.osc.drive.viewmodel.AlistDriveViewModel;
import com.github.tvbox.osc.drive.viewmodel.AbstractDriveViewModel;
import com.github.tvbox.osc.drive.viewmodel.FtpDriveViewModel;
import com.github.tvbox.osc.drive.viewmodel.LocalDriveViewModel;
import com.github.tvbox.osc.drive.viewmodel.SmbDriveViewModel;
import com.github.tvbox.osc.drive.viewmodel.WebDAVDriveViewModel;
import com.github.tvbox.osc.drive.widget.DriveTvRecyclerView;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 存储空间浏览 Activity。
 */
public class DriveActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSION = 1;

    private TextView txtTitle;
    private DriveTvRecyclerView mGridView;
    private ImageButton btnAddServer;
    private ImageButton btnRemoveServer;
    private ImageButton btnSort;
    private ProgressBar progressBar;
    private DriveAdapter adapter = new DriveAdapter();
    private List<DriveFolderFile> drives = null;
    List<DriveFolderFile> searchResult = null;
    private AbstractDriveViewModel viewModel = null;
    private AbstractDriveViewModel backupViewModel = null;
    private int sortType = 0;
    private View footLoading;
    private boolean isInSearch = false;
    private boolean delMode = false;
    /** 压缩包浏览模式 */
    private boolean archiveMode = false;
    private AbstractDriveViewModel savedViewModel = null;
    private File archiveLocalFile = null;
    private String savedTitleText = "";
    /** [修复#7] 使用静态内部类 + 弱引用避免内存泄漏 */
    private static class SafeHandler extends android.os.Handler {
        SafeHandler() { super(Looper.getMainLooper()); }
    }
    private final android.os.Handler mHandler = new SafeHandler();
    private SharedPreferences sp;

    /** [新增] U 盘插拔广播接收器 */
    private BroadcastReceiver usbReceiver;
    private boolean usbReceiverRegistered = false;
    /** U盘路径匹配正则 */
    private static final Pattern USB_PATH_PATTERN = Pattern.compile("/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}");

    // ==================== 生命周期 ====================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 延迟初始化：宿主无需在 Application.onCreate() 中调用 DriveModule.init()
        DriveModule.init(getApplicationContext());
        setContentView(R.layout.drive_activity_drive);
        sp = getSharedPreferences("drive_module", Context.MODE_PRIVATE);
        initView();
        initData();
        checkStoragePermissionOnEntry();
    }

    /** [修复] 返回键逻辑与原版 Box 对齐：
     *  浏览中(viewModel!=null) → 返回上一级目录
     *  删除模式 → 退出删除模式
     *  否则 → 关闭 Activity */
    @Override
    public void onBackPressed() {
        if (viewModel != null) {
            cancel();
            returnPreviousFolder();
            return;
        }
        if (delMode) {
            toggleDelMode();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerUsbReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterUsbReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        EventBus.getDefault().unregister(this);
        unregisterUsbReceiver();
    }

    /** 权限申请回调（WRITE_EXTERNAL_STORAGE） */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFolderPicker();
            } else {
                Toast.makeText(this, getString(R.string.drive_perm_denied), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    // ==================== 初始化 ====================

    private void initView() {
        EventBus.getDefault().register(this);
        this.txtTitle = findViewById(R.id.textView);
        this.btnAddServer = findViewById(R.id.btnAddServer);
        this.mGridView = findViewById(R.id.mGridView);
        this.btnRemoveServer = findViewById(R.id.btnRemoveServer);
        this.btnSort = findViewById(R.id.btnSort);
        this.progressBar = findViewById(R.id.progressBar);

        footLoading = getLayoutInflater().inflate(R.layout.drive_item_search_lite, null);
        footLoading.findViewById(R.id.tvName).setVisibility(View.GONE);
        this.btnRemoveServer.setColorFilter(ContextCompat.getColor(this, R.color.drive_color_FFFFFF));

        // 返回按钮
        this.btnRemoveServer.setOnClickListener(v -> toggleDelMode());
        findViewById(R.id.btnHome).setOnClickListener(v -> DriveActivity.super.onBackPressed());

        // 排序按钮
        this.btnSort.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            openSortDialog();
        });

        // 添加存储盘
        this.btnAddServer.setOnClickListener(v -> {
            // 压缩包浏览模式 → 解压当前目录全部文件
            if (archiveMode) {
                openExtractAllPicker();
                return;
            }
            FastClickCheckUtil.check(v);
            StorageDriveType.TYPE[] types = StorageDriveType.TYPE.values();
            SelectDialog<StorageDriveType.TYPE> dialog = new SelectDialog<>(DriveActivity.this);
            dialog.setTip(getString(R.string.drive_choose_type));
            dialog.setItemCheckDisplay(false);
            String[] typeNames = StorageDriveType.getTypeNames(this);
            dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<StorageDriveType.TYPE>() {
                @Override
                public void click(StorageDriveType.TYPE value, int pos) {
                    if (value == StorageDriveType.TYPE.LOCAL) {
                        if (checkAndRequestStoragePermission()) return;
                        openFolderPicker();
                        dialog.dismiss();
                    } else if (value == StorageDriveType.TYPE.WEBDAV) {
                        openWebdavDialog(null);
                        dialog.dismiss();
                    } else if (value == StorageDriveType.TYPE.ALISTWEB) {
                        openAlistDriveDialog(null);
                        dialog.dismiss();
                    } else if (value == StorageDriveType.TYPE.FTP) {
                        openFtpDialog(null);
                        dialog.dismiss();
                    } else if (value == StorageDriveType.TYPE.FTP_SERVER) {
                        openFtpServerConfigDialog();
                        dialog.dismiss();
                    } else if (value == StorageDriveType.TYPE.SMB) {
                        openSmbDialog(null);
                        dialog.dismiss();
                    }
                }

                @Override
                public String getDisplay(StorageDriveType.TYPE val) {
                    return typeNames[val.ordinal()];
                }
            }, new DiffUtil.ItemCallback<StorageDriveType.TYPE>() {
                @Override
                public boolean areItemsTheSame(@NonNull StorageDriveType.TYPE oldItem, @NonNull StorageDriveType.TYPE newItem) {
                    return oldItem.equals(newItem);
                }

                @Override
                public boolean areContentsTheSame(@NonNull StorageDriveType.TYPE oldItem, @NonNull StorageDriveType.TYPE newItem) {
                    return oldItem.equals(newItem);
                }
            }, Arrays.asList(types), 0);
            dialog.show();
        });

        this.mGridView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        this.mGridView.setAdapter(this.adapter);

        this.mGridView.setOnItemListener(new DriveTvRecyclerView.OnItemListener() {
            /** [修复] 仅更新数据模型，不触发 notifyItemChanged。
             *  原 notifyItemChanged 会导致完整 rebind，重建 click listener，
             *  在遥控器快速操作时丢失 DPAD_CENTER/ENTER 事件。 */
            public void onItemPreSelected(DriveTvRecyclerView tvRecyclerView, View view, int position) {
                if (position >= 0 && position < adapter.getData().size()) {
                    adapter.getData().get(position).isSelected = false;
                }
            }

            public void onItemSelected(DriveTvRecyclerView tvRecyclerView, View view, int position) {
                if (position >= 0 && position < adapter.getData().size()) {
                    adapter.getData().get(position).isSelected = true;
                }
            }

            @Override
            public void onItemClick(DriveTvRecyclerView parent, View itemView, int position) {
                if (delMode) {
                    DriveFolderFile selectedDrive = drives.get(position);
                    StorageDrive driveData = selectedDrive.getDriveData();
                    if (driveData == null) {
                        Toast.makeText(DriveActivity.this, getString(R.string.drive_delete_fail_data), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // [P1修复] 使用异步删除，避免主线程访问数据库
                    DriveDataManager.deleteDrive(driveData.getId(), deletedRows -> {
                        if (deletedRows > 0) {
                            Toast.makeText(DriveActivity.this, getString(R.string.drive_deleted), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(DriveActivity.this, getString(R.string.drive_delete_fail_not_found, driveData.getId()), Toast.LENGTH_SHORT).show();
                        }
                        EventBus.getDefault().post(new DriveEvent(DriveEvent.TYPE_DRIVE_REFRESH));
                    });
                    return;
                }
                btnAddServer.setVisibility(View.GONE);
                btnRemoveServer.setVisibility(View.GONE);
                DriveFolderFile selectedItem = DriveActivity.this.adapter.getItem(position);
                if (selectedItem == null) return;

                if ((selectedItem == selectedItem.parentFolder || selectedItem.parentFolder == null) && selectedItem.name == null) {
                    returnPreviousFolder();
                    return;
                }
                // 压缩包内文件夹导航
                if (archiveMode && !selectedItem.isFile) {
                    ArchiveBrowseViewModel archiveVM = (ArchiveBrowseViewModel) viewModel;
                    String newPath = archiveVM.getFullPathForItem(selectedItem.name);
                    viewModel.setCurrentDriveNote(selectedItem);
                    archiveVM.navigateTo(newPath);
                    loadDriveData();
                    return;
                }
                if (viewModel == null) {
                    if (selectedItem.getDriveType() == StorageDriveType.TYPE.LOCAL) {
                        viewModel = new LocalDriveViewModel();
                    } else if (selectedItem.getDriveType() == StorageDriveType.TYPE.WEBDAV) {
                        viewModel = new WebDAVDriveViewModel();
                    } else if (selectedItem.getDriveType() == StorageDriveType.TYPE.ALISTWEB) {
                        viewModel = new AlistDriveViewModel();
                    } else if (selectedItem.getDriveType() == StorageDriveType.TYPE.FTP) {
                        viewModel = new FtpDriveViewModel();
                    } else if (selectedItem.getDriveType() == StorageDriveType.TYPE.SMB) {
                        viewModel = new SmbDriveViewModel();
                    }
                    viewModel.setCurrentDrive(selectedItem);
                    if (!selectedItem.isFile) {
                        loadDriveData();
                        return;
                    }
                }

                if (!selectedItem.isFile) {
                    viewModel.setCurrentDriveNote(selectedItem);
                    loadDriveData();
                } else {
                    handleFileClick(selectedItem);
                }
            }
        });
    }

    // ==================== 权限处理 ====================

    /**
     * 进入页面时主动检查存储权限，未授权则弹窗提示用户授权。
     */
    private void checkStoragePermissionOnEntry() {
        boolean needPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
        if (needPermission) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.drive_perm_dialog_title))
                    .setMessage(getString(R.string.drive_perm_dialog_msg))
                    .setPositiveButton(getString(R.string.drive_perm_dialog_go), (dialog, which) -> checkAndRequestStoragePermission())
                    .setNegativeButton(getString(R.string.drive_perm_dialog_later), null)
                    .show();
        }
    }

    /**
     * 纯检查权限状态，不发起申请。
     */
    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 检查并请求存储权限（WRITE_EXTERNAL_STORAGE）。
     * @return true 表示权限尚未获取（已发起申请，调用方应 return）
     */
    private boolean checkAndRequestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_PERMISSION);
            return true;
        }
        return false;
    }

    // ==================== 文件点击 → 播放 / 归档浏览 ====================

    private void handleFileClick(DriveFolderFile selectedItem) {
        // 1) 压缩包浏览模式：解压后播放或提示
        if (archiveMode) {
            handleArchiveFileClick(selectedItem);
            return;
        }
        // 2) 非压缩包模式：检测是否为本地压缩包文件 → 弹出解压对话框
        if (ArchiveHelper.isArchiveFile(selectedItem.name)) {
            if (viewModel.getCurrentDrive().getDriveType() == StorageDriveType.TYPE.LOCAL) {
                openExtractDialog(selectedItem);
            } else {
                Toast.makeText(this, getString(R.string.drive_no_remote_extract), Toast.LENGTH_SHORT).show();
            }
            return;
        }
        // 3) 原始逻辑：仅视频文件播放
        if (!StorageDriveType.isVideoType(selectedItem.fileType)) {
            Toast.makeText(DriveActivity.this, "Media Unsupported", Toast.LENGTH_SHORT).show();
            return;
        }

        DriveFolderFile currentDrive = viewModel.getCurrentDrive();
        if (currentDrive.getDriveType() == StorageDriveType.TYPE.LOCAL) {
            // [修复] 与原版 Box 一致用字符串拼接。不能用 new File(parent, child),
            // 因为 getAccessingPathStr() 可能返回以 "/" 开头的字符串（根节点 name="" 导致），
            // new File 会把它当绝对路径而忽略 parent！
            String fileUrl = new File(currentDrive.name + selectedItem.getAccessingPathStr() + selectedItem.name).getAbsolutePath();
            finishWithPlayFile(selectedItem.name, fileUrl, null);

        } else if (currentDrive.getDriveType() == StorageDriveType.TYPE.WEBDAV) {
            JsonObject config = currentDrive.getConfig();
            String targetPath = selectedItem.getAccessingPathStr() + selectedItem.name;
            String fileUrl = config.get("url").getAsString() + targetPath;
            String headers = null;
            String credential = currentDrive.getWebDAVBase64Credential();
            if (credential != null) {
                JsonArray headersArr = new JsonArray();
                JsonObject authHeader = new JsonObject();
                authHeader.addProperty("name", "authorization");
                authHeader.addProperty("value", "Basic " + credential);
                headersArr.add(authHeader);
                JsonObject headersObj = new JsonObject();
                headersObj.add("headers", headersArr);
                headers = headersObj.toString();
            }
            finishWithPlayFile(selectedItem.name, fileUrl, headers);

        } else if (currentDrive.getDriveType() == StorageDriveType.TYPE.ALISTWEB) {
            AlistDriveViewModel alistVM = (AlistDriveViewModel) viewModel;
            alistVM.loadFile(selectedItem, new AlistDriveViewModel.LoadFileCallback() {
                @Override
                public void callback(String fileUrl) {
                    mHandler.post(() -> {
                        finishWithPlayFile(selectedItem.name, fileUrl, null);
                    });
                }

                @Override
                public void fail(String msg) {
                    mHandler.post(() -> Toast.makeText(DriveActivity.this, msg, Toast.LENGTH_SHORT).show());
                }
            });
        } else if (currentDrive.getDriveType() == StorageDriveType.TYPE.FTP) {
            JsonObject config = currentDrive.getConfig();
            String targetPath = selectedItem.getAccessingPathStr() + selectedItem.name;
            if (!targetPath.startsWith("/")) targetPath = "/" + targetPath;
            String initPath = config.has("initPath") ? config.get("initPath").getAsString() : "/";
            String fullFtpPath = initPath + targetPath;
            String fileUrl = FtpDriveViewModel.buildFtpFileUrl(config, fullFtpPath);
            finishWithPlayFile(selectedItem.name, fileUrl, null);
        } else if (currentDrive.getDriveType() == StorageDriveType.TYPE.SMB) {
            JsonObject config = currentDrive.getConfig();
            String shareName = config.get("shareName").getAsString();
            String relativePath = selectedItem.getAccessingPathStr() + selectedItem.name;
            String smbPath = "/" + shareName + "/" + relativePath.replace("\\", "/");
            smbPath = smbPath.replaceAll("/+", "/");
            String fileUrl = SmbDriveViewModel.buildSmbFileUrl(config, smbPath);
            finishWithPlayFile(selectedItem.name, fileUrl, null);
        }
    }

    /**
     * 将播放信息回传给宿主。优先使用宿主注入的 {@link com.github.tvbox.osc.drive.callback.DriveCallback}，
     * 若宿主未注入回调，则回退到 setResult + finish 模式，宿主可通过 onActivityResult 接收。
     * <p>
     * 设计原因：DriveCallback 模式让宿主能在不关闭 DriveActivity 的情况下接管播放
     * （例如宿主想保留浏览位置、或在播放返回后继续浏览），耦合度最低；
     * setResult 模式作为兜底，保证即使宿主不注入回调也能正常工作。
     */
    private void finishWithPlayFile(String name, String url, String headers) {
        com.github.tvbox.osc.drive.callback.DriveCallback cb = DriveModule.getDriveCallback();
        if (cb != null) {
            // 宿主已注入回调 → 直接回调，不关闭浏览界面（保留浏览位置，用户按返回键可回到文件列表）
            cb.onPlayFile(name, url, headers, null);
            return;
        }
        // 兜底：setResult 模式
        Intent result = new Intent();
        result.putExtra("playName", name);
        result.putExtra("playUrl", url);
        if (headers != null) {
            result.putExtra("playHeaders", headers);
        }
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    // ==================== 排序 ====================

    // [P0修复] 缓存 Collator 实例，避免每次比较都调用 getInstance()
    private final Collator sortCollator = Collator.getInstance(Locale.CHINESE);

    private Comparator<DriveFolderFile> sortComparator = (o1, o2) -> {
        switch (sortType) {
            case 1:
                return sortCollator.compare(o2.name.toUpperCase(Locale.CHINESE), o1.name.toUpperCase(Locale.CHINESE));
            case 2:
                // [P0修复] lastModifiedDate 为 Long 包装类型可能为 null，Long.compare() 拆箱会 NPE
                return compareNullableLong(o1.lastModifiedDate, o2.lastModifiedDate, false);
            case 3:
                return compareNullableLong(o1.lastModifiedDate, o2.lastModifiedDate, true);
            default:
                return sortCollator.compare(o1.name.toUpperCase(Locale.CHINESE), o2.name.toUpperCase(Locale.CHINESE));
        }
    };

    /**
     * 比较 two nullable Long values. null 视为最小值（排到最后）。
     */
    private static int compareNullableLong(Long a, Long b, boolean desc) {
        if (a == null && b == null) return 0;
        if (a == null) return desc ? -1 : 1;
        if (b == null) return desc ? 1 : -1;
        return desc ? Long.compare(b, a) : Long.compare(a, b);
    }

    private void openSortDialog() {
        List<String> options = Arrays.asList(getString(R.string.drive_sort_name_asc), getString(R.string.drive_sort_name_desc), getString(R.string.drive_sort_time_asc), getString(R.string.drive_sort_time_desc));
        int sort = sp.getInt(DriveConfig.STORAGE_DRIVE_SORT, 0);
        SelectDialog<String> dialog = new SelectDialog<>(DriveActivity.this);
        dialog.setTip(getString(R.string.drive_choose_sort));
        dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                sortType = pos;
                sp.edit().putInt(DriveConfig.STORAGE_DRIVE_SORT, pos).apply();
                dialog.dismiss();
                loadDriveData();
            }

            @Override
            public String getDisplay(String val) {
                return val;
            }
        }, null, options, sort);
        dialog.show();
    }

    // ==================== 文件选择器（本地目录） ====================

    /**
     * 打开目录浏览器对话框，逐级浏览选择本地目录。
     */
    private void openFolderPicker() {
        if (delMode) toggleDelMode();
        FolderPickerDialog dialog = new FolderPickerDialog(this, path -> {
            if (path == null || path.isEmpty()) {
                Toast.makeText(this, getString(R.string.drive_path_empty), Toast.LENGTH_SHORT).show();
                return;
            }
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                Toast.makeText(this, getString(R.string.drive_dir_not_exist, path), Toast.LENGTH_SHORT).show();
                return;
            }
            // [P1修复] 使用异步方式检查重复，避免主线程访问数据库
            DriveDataManager.isDriveAlreadyAddedAsync(path, alreadyAdded -> {
                if (alreadyAdded) {
                    Toast.makeText(DriveActivity.this, getString(R.string.drive_dir_already_added), Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean isRemovable = USB_PATH_PATTERN.matcher(path).find();
                DriveDataManager.insertLocalDriveRecord(path, isRemovable);
                EventBus.getDefault().post(new DriveEvent(DriveEvent.TYPE_DRIVE_REFRESH));
            });
        });
        dialog.show();
    }

    // ==================== [新增] U 盘自动检测 ====================

    /**
     * 注册 U 盘挂载/卸载广播。
     * U 盘插入后自动检测并提示用户添加到存储列表。
     */
    private void registerUsbReceiver() {
        if (usbReceiverRegistered) return;
        try {
            usbReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action == null) return;

                    if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
                        String path = intent.getData() != null ? intent.getData().getPath() : null;
                        if (path != null && USB_PATH_PATTERN.matcher(path).find()) {
                            // U 盘已挂载
                            // [P1修复] 使用异步方式检查重复，避免主线程访问数据库
                            DriveDataManager.isDriveAlreadyAddedAsync(path, alreadyAdded -> {
                                if (!alreadyAdded) {
                                    showUsbDetectedDialog(path);
                                }
                            });
                        }
                    } else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)
                            || Intent.ACTION_MEDIA_EJECT.equals(action)
                            || Intent.ACTION_MEDIA_REMOVED.equals(action)) {
                        // U 盘已拔出，刷新列表
                        mHandler.postDelayed(() -> {
                            if (viewModel != null && viewModel.getCurrentDrive() != null) {
                                String drivePath = viewModel.getCurrentDrive().name;
                                String removedPath = intent.getData() != null ? intent.getData().getPath() : "";
                                if (drivePath != null && drivePath.startsWith(removedPath)) {
                                    // 当前正在浏览被拔出的 U 盘，返回根目录
                                    Toast.makeText(DriveActivity.this, getString(R.string.drive_usb_removed), Toast.LENGTH_SHORT).show();
                                    returnToDriveList();
                                }
                            }
                        }, 300);
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            filter.addAction(Intent.ACTION_MEDIA_EJECT);
            filter.addAction(Intent.ACTION_MEDIA_REMOVED);
            filter.addDataScheme("file");

            // [P2修复] ACTION_MEDIA_MOUNTED 等是系统全局广播，
            // Android 14+ 使用 RECEIVER_NOT_EXPORTED 会导致无法接收，
            // 因此对所有版本均不传 RECEIVER_NOT_EXPORTED。
            // 这些广播携带 "file" scheme，仅系统在存储介质变化时发送，
            // 不会被第三方应用伪造，安全性可接受。
            registerReceiver(usbReceiver, filter);
            usbReceiverRegistered = true;
        } catch (Exception e) {
            // 部分厂商系统可能限制广播注册，不影响核心功能
            e.printStackTrace();
        }
    }

    private void unregisterUsbReceiver() {
        if (usbReceiverRegistered && usbReceiver != null) {
            try {
                unregisterReceiver(usbReceiver);
            } catch (Exception ignored) {
            }
            usbReceiverRegistered = false;
            usbReceiver = null;
        }
    }

    /**
     * U 盘检测到后弹窗提示用户是否添加。
     */
    private void showUsbDetectedDialog(String path) {
        String volumeName = new File(path).getName();
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.drive_usb_detected))
                .setMessage(getString(R.string.drive_usb_add_prompt, volumeName))
                .setPositiveButton(getString(R.string.drive_btn_add), (dialog, which) -> {
                    boolean isRemovable = USB_PATH_PATTERN.matcher(path).find();
                    DriveDataManager.insertLocalDriveRecord(path, isRemovable);
                    EventBus.getDefault().post(new DriveEvent(DriveEvent.TYPE_DRIVE_REFRESH));
                    Toast.makeText(this, getString(R.string.drive_usb_added, volumeName), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.drive_btn_not_now), null)
                .show();
    }

    /**
     * [新增] 返回到存储盘根列表。
     */
    private void returnToDriveList() {
        viewModel = null;
        backupViewModel = null;
        btnAddServer.setVisibility(View.VISIBLE);
        btnRemoveServer.setVisibility(View.VISIBLE);
        txtTitle.setText(getString(R.string.drive_act_drive));
        initData();
    }

    // ==================== Dialog ====================

    private void openWebdavDialog(StorageDrive drive) {
        WebdavDialog webdavDialog = new WebdavDialog(this, drive);
        EventBus.getDefault().register(webdavDialog);
        webdavDialog.setOnDismissListener(dialog -> EventBus.getDefault().unregister(dialog));
        webdavDialog.show();
    }

    private void openAlistDriveDialog(StorageDrive drive) {
        AlistDriveDialog alistDialog = new AlistDriveDialog(this, drive);
        EventBus.getDefault().register(alistDialog);
        alistDialog.setOnDismissListener(d -> EventBus.getDefault().unregister(d));
        alistDialog.show();
    }

    private void openFtpDialog(StorageDrive drive) {
        FtpDriveDialog ftpDialog = new FtpDriveDialog(this, drive);
        EventBus.getDefault().register(ftpDialog);
        ftpDialog.setOnDismissListener(d -> EventBus.getDefault().unregister(d));
        ftpDialog.show();
    }

    private void openSmbDialog(StorageDrive drive) {
        SmbDriveDialog smbDialog = new SmbDriveDialog(this, drive);
        EventBus.getDefault().register(smbDialog);
        smbDialog.setOnDismissListener(d -> EventBus.getDefault().unregister(d));
        smbDialog.show();
    }

    private void openFtpServerConfigDialog() {
        if (delMode) toggleDelMode();
        FtpServerConfigDialog serverDialog = new FtpServerConfigDialog(this);
        serverDialog.show();
    }

    // ==================== 压缩包解压对话框 ====================

    /**
     * 弹出解压目标选择对话框。
     * 三种选项：解压到同名目录 / 当前目录 / 选择目录。
     */
    private void openExtractDialog(DriveFolderFile archiveItem) {
        File localFile = resolveToLocalFile(archiveItem);
        if (localFile == null || !localFile.exists()) {
            Toast.makeText(this, getString(R.string.drive_cannot_access_archive), Toast.LENGTH_SHORT).show();
            return;
        }
        File parentDir = localFile.getParentFile();
        if (parentDir == null) {
            Toast.makeText(this, getString(R.string.drive_cannot_find_archive_dir), Toast.LENGTH_SHORT).show();
            return;
        }
        ArchiveExtractDialog dialog = new ArchiveExtractDialog(this, localFile, parentDir,
                targetPath -> {
                    // post 延迟一帧，避免与对话框关闭的布局变化冲突
                    mGridView.post(() -> {
                        if (viewModel != null) loadDriveData();
                    });
                });
        dialog.show();
    }

    // ==================== 压缩包浏览模式 ====================

    /**
     * 进入压缩包浏览模式。
     * 保存当前 ViewModel 状态，切换到 ArchiveBrowseViewModel。
     */
    private void enterArchiveMode(DriveFolderFile archiveItem) {
        File localFile = resolveToLocalFile(archiveItem);
        if (localFile == null || !localFile.exists()) {
            Toast.makeText(this, getString(R.string.drive_cannot_access_archive), Toast.LENGTH_SHORT).show();
            return;
        }
        // 保存当前状态
        savedViewModel = viewModel;
        savedTitleText = txtTitle.getText().toString();
        // 切换到归档模式
        archiveMode = true;
        archiveLocalFile = localFile;
        ArchiveBrowseViewModel archiveVM = new ArchiveBrowseViewModel();
        archiveVM.setArchiveFile(localFile);
        viewModel = archiveVM;
        DriveFolderFile archiveRoot = new DriveFolderFile(null, localFile.getName(), 0, false, null, null);
        archiveRoot.parentFolder = null;   // 标识根，返回时退出归档模式
        viewModel.setCurrentDriveNote(archiveRoot);
        loadDriveData();
    }

    /** 退出压缩包浏览模式，恢复之前的 ViewModel。 */
    private void exitArchiveMode() {
        if (viewModel != null) viewModel.cancel();
        viewModel = savedViewModel;
        savedViewModel = null;
        archiveMode = false;
        archiveLocalFile = null;
        if (viewModel == null) {
            initData();
        } else {
            txtTitle.setText(savedTitleText);
            loadDriveData();
        }
    }

    /** 将当前条目解析为本地 File（仅支持本地存储盘）。 */
    private File resolveToLocalFile(DriveFolderFile item) {
        DriveFolderFile curDrive = viewModel.getCurrentDrive();
        if (curDrive.getDriveType() == StorageDriveType.TYPE.LOCAL) {
            // [修复] 同 handleFileClick，使用字符串拼接避免 new File(parent, child) 的绝对路径陷阱
            return new File(curDrive.name + item.getAccessingPathStr() + item.name);
        }
        return null;
    }

    /**
     * 压缩包内文件点击处理：
     * - 视频文件 → 解压到缓存后播放
     * - 其他文件 → 解压到缓存后提示路径
     */
    private void handleArchiveFileClick(DriveFolderFile selectedItem) {
        if (!selectedItem.isFile) return;
        ArchiveBrowseViewModel archiveVM = (ArchiveBrowseViewModel) viewModel;
        String entryPath = archiveVM.getFullPathForItem(selectedItem.name);
        // 输出文件名取 entryPath 的最后一段
        String outName = entryPath;
        int ls = outName.lastIndexOf('/');
        if (ls >= 0) outName = outName.substring(ls + 1);

        File extractDir = getExtractCacheDir();
        File outputFile = new File(extractDir, outName);

        Toast.makeText(this, getString(R.string.drive_extracting), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ArchiveHelper.extractFile(archiveLocalFile, entryPath, outputFile, null);
                if (StorageDriveType.isVideoType(selectedItem.fileType)) {
                    String path = outputFile.getAbsolutePath();
                    mHandler.post(() -> finishWithPlayFile(selectedItem.name, path, null));
                } else {
                    mHandler.post(() -> Toast.makeText(DriveActivity.this,
                            getString(R.string.drive_extracted, outputFile.getAbsolutePath()), Toast.LENGTH_LONG).show());
                }
            } catch (final Exception e) {
                mHandler.post(() -> Toast.makeText(DriveActivity.this,
                        getString(R.string.drive_extract_fail, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /** 解压缓存目录。 */
    private File getExtractCacheDir() {
        File dir = getExternalCacheDir();
        if (dir == null) dir = getCacheDir();
        File d = new File(dir, "archive_extract");
        d.mkdirs();
        return d;
    }

    /** 选择目标目录后解压当前虚拟路径下的全部文件。 */
    private void openExtractAllPicker() {
        ArchiveBrowseViewModel archiveVM = (ArchiveBrowseViewModel) viewModel;
        String virtualPath = archiveVM.getCurrentVirtualPath();
        FolderPickerDialog dialog = new FolderPickerDialog(this, path -> {
            if (path == null || path.isEmpty()) return;
            File outputDir = new File(path);
            if (!outputDir.exists()) outputDir.mkdirs();
            Toast.makeText(this, getString(R.string.drive_start_extract), Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    ArchiveHelper.extractAll(archiveLocalFile, outputDir, virtualPath, null);
                    mHandler.post(() -> Toast.makeText(DriveActivity.this,
                            getString(R.string.drive_extract_done, path), Toast.LENGTH_SHORT).show());
                } catch (final Exception e) {
                    mHandler.post(() -> Toast.makeText(DriveActivity.this,
                            getString(R.string.drive_extract_fail, e.getMessage()), Toast.LENGTH_SHORT).show());
                }
            }).start();
        });
        dialog.show();
    }

    public void toggleDelMode() {
        delMode = !delMode;
        if (delMode) {
            // 主题色优先级：宿主 DriveCallback.getThemeColor() > Intent extra "themeColor" > 默认白色
            // 这样宿主可以选择两种集成方式之一：注入回调（推荐，低耦合）或通过 Intent extra 传色
            int themeColor = 0xFFFFFFFF; // 默认白色
            com.github.tvbox.osc.drive.callback.DriveCallback cb = DriveModule.getDriveCallback();
            if (cb != null) {
                int cbColor = cb.getThemeColor();
                if (cbColor != 0) themeColor = cbColor;
            } else if (getIntent() != null) {
                themeColor = getIntent().getIntExtra("themeColor", 0xFFFFFFFF);
            }
            this.btnRemoveServer.setColorFilter(themeColor);
        } else {
            this.btnRemoveServer.setColorFilter(ContextCompat.getColor(this, R.color.drive_color_FFFFFF));
        }
        adapter.toggleDelMode(delMode);
    }

    // ==================== 数据加载 ====================

    private void initData() {
        this.txtTitle.setText(getString(R.string.drive_act_drive));
        sortType = sp.getInt(DriveConfig.STORAGE_DRIVE_SORT, 0);
        btnSort.setVisibility(View.GONE);
        if (drives == null) {
            drives = new ArrayList<>();
            showLoadingView(true);
            // [P1修复] 使用异步方式从数据库加载，避免主线程访问数据库崩溃
            DriveDataManager.getAllDrivesAsync(storageDrives -> {
                for (StorageDrive storageDrive : storageDrives) {
                    DriveFolderFile drive = new DriveFolderFile(storageDrive);
                    if (delMode) drive.isDelMode = true;
                    drives.add(drive);
                }
                adapter.setNewData(drives);
                setSelectedItem(drives);
                btnAddServer.setVisibility(View.VISIBLE);
                btnRemoveServer.setVisibility(View.VISIBLE);
                showLoadingView(false);
            });
        } else {
            adapter.setNewData(drives);
            setSelectedItem(drives);
            btnAddServer.setVisibility(View.VISIBLE);
            btnRemoveServer.setVisibility(View.VISIBLE);
            showLoadingView(false);
        }
    }

    /** [修复#8] 用 post 队列替代固定 50ms 延迟，等布局完成再滚动 */
    private void setSelectedItem(List<DriveFolderFile> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).isSelected) {
                int isIndex = i;
                mGridView.post(() -> mGridView.setSelection(isIndex));
                return;
            }
        }
        mGridView.setSelection(0);
    }

    private void loadDriveData() {
        // [修复] 移除 hasStoragePermission() 前置检查，与原版 Box 保持一致。
        // 原因：小米等定制系统的 checkSelfPermission 返回值不可靠，
        // FolderPickerDialog 能浏览就说明权限没问题，直接让 listFiles() 结果说话。
        viewModel.setSortType(sortType);
        btnSort.setVisibility(View.VISIBLE);
        showLoadingView(true);
        String path = viewModel.loadData(new AbstractDriveViewModel.LoadDataCallback() {
            @Override
            public void callback(List<DriveFolderFile> list, boolean alreadyHasChildren) {
                mHandler.post(() -> {
                    showLoadingView(false);
                    if (alreadyHasChildren) {
                        adapter.setNewData(viewModel.getCurrentDriveNote().getChildren());
                        setSelectedItem(viewModel.getCurrentDriveNote().getChildren());
                    } else {
                        adapter.setNewData(viewModel.getCurrentDriveNote().getChildren());
                        mGridView.post(() -> mGridView.setSelection(0));
                    }
                });
            }

            @Override
            public void fail(String message) {
                mHandler.post(() -> {
                    showLoadingView(false);
                    viewModel = null;
                    btnSort.setVisibility(View.GONE);
                    btnAddServer.setVisibility(View.VISIBLE);
                    btnRemoveServer.setVisibility(View.VISIBLE);
                    txtTitle.setText(getString(R.string.drive_act_drive));
                    drives = null;
                    initData();
                    Toast.makeText(DriveActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
        if (StringUtils.isNotEmpty(path)) {
            this.txtTitle.setText(path);
        }
    }

    private void showLoadingView(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void cancel() {
        if (viewModel != null) viewModel.cancel();
    }

    private void returnPreviousFolder() {
        if (isInSearch && viewModel == null) {
            isInSearch = false;
            viewModel = backupViewModel;
            backupViewModel = null;
            if (viewModel == null) {
                initData();
            } else {
                loadDriveData();
            }
            return;
        }
        // 压缩包浏览模式：返回
        if (archiveMode) {
            if (viewModel.getCurrentDriveNote().parentFolder == null) {
                // 已在压缩包根目录 → 退出归档模式
                exitArchiveMode();
                return;
            }
            // 在压缩包子目录内 → 上一级
            ArchiveBrowseViewModel archiveVM = (ArchiveBrowseViewModel) viewModel;
            String cur = archiveVM.getCurrentVirtualPath();
            String trimmed = cur.endsWith("/") ? cur.substring(0, cur.length() - 1) : cur;
            int lastSlash = trimmed.lastIndexOf('/');
            String parentPath = lastSlash >= 0 ? trimmed.substring(0, lastSlash + 1) : "";

            viewModel.getCurrentDriveNote().setChildren(null);
            viewModel.setCurrentDriveNote(viewModel.getCurrentDriveNote().parentFolder);
            archiveVM.navigateTo(parentPath);
            loadDriveData();
            return;
        }
        viewModel.getCurrentDriveNote().setChildren(null);
        viewModel.setCurrentDriveNote(viewModel.getCurrentDriveNote().parentFolder);
        if (viewModel.getCurrentDriveNote() == null) {
            if (isInSearch) {
                this.txtTitle.setText(getString(R.string.drive_search_result));
                adapter.setNewData(searchResult);
                viewModel = null;
                return;
            }
            viewModel = null;
            initData();
            return;
        }
        loadDriveData();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(DriveEvent event) {
        if (event.type == DriveEvent.TYPE_DRIVE_REFRESH) {
            drives = null;
            initData();
        }
    }
}