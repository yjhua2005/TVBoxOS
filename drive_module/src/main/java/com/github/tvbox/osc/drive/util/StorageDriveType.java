package com.github.tvbox.osc.drive.util;

import android.content.Context;

import com.github.tvbox.osc.drive.R;

import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

/**
 * 存储盘类型枚举及视频格式判断工具。
 * [P2修复] getTypeNames() 改为接受 Context 参数，从 strings.xml 读取，支持国际化。
 */
public class StorageDriveType {

    public enum TYPE {
        LOCAL,
        WEBDAV,
        ALISTWEB,
        FTP,
        FTP_SERVER,
        SMB,
    }

    // [P2修复] 从字符串资源读取，支持多语言
    public static String[] getTypeNames(Context context) {
        return new String[]{
                context.getString(R.string.drive_type_local),
                context.getString(R.string.drive_type_webdav),
                context.getString(R.string.drive_type_alist),
                context.getString(R.string.drive_type_ftp),
                context.getString(R.string.drive_type_ftp_server),
                context.getString(R.string.drive_type_smb),
        };
    }

    public static boolean isVideoType(String type) {
        if (type == null || type.length() == 0)
            return false;
        // [P2修复] 使用 Set 替代线性搜索，提高查找效率
        return VIDEO_TYPES.contains(type.toUpperCase(Locale.ROOT).trim());
    }

    // [P2修复] 使用静态 Set 替代数组线性搜索
    private static final Set<String> VIDEO_TYPES;
    static {
        VIDEO_TYPES = new HashSet<>();
        String[] types = new String[]{
                "264", "3G2", "3GP", "3GP2", "3GPP", "3GPP2",
                "ASF", "ASX", "AVI", "DIVX", "F4V", "FLV",
                "H261", "H263", "H264", "H265", "HEVC",
                "M4V", "MKV", "MOV", "MP4", "MP4V", "MPEG", "MPEG4",
                "MPG", "MTS", "MTV", "OGV", "RM", "RMVB",
                "TS", "VOB", "WEBM", "WMV", "WVX", "XVID",
                "M3U8", "MPG2"
        };
        for (String t : types) VIDEO_TYPES.add(t);
    }
}