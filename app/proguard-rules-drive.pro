# ============================================================
#  Drive 子模块 ProGuard 混淆规则
#  将此文件复制到 app/proguard-rules-drive.pro
#  然后在 build.gradle 的 proguardFiles 末尾追加：
#
#    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'),
#                    'proguard-rules.pro',
#                    'proguard-rules-media.pro',
#                    'proguard-rules-drive.pro'
# ============================================================

# ---------- Drive 模块入口 ----------
-keep public class com.github.tvbox.osc.drive.DriveModule { *; }
-keep public class com.github.tvbox.osc.drive.callback.DriveCallback { *; }
-keep public class com.github.tvbox.osc.drive.ui.activity.DriveActivity { *; }

# ---------- Drive 模块内部 Widget ----------
-keep public class com.github.tvbox.osc.drive.widget.DriveTvRecyclerView { *; }

# ---------- Drive 模块 Room 实体与 DAO ----------
# [P1修复] 原规则使用 db 包名，但实际实体在 cache 包中，DAO 也在 cache 包中
-keep class com.github.tvbox.osc.drive.cache.** { *; }
-keep class * extends androidx.room.RoomDatabase

# ---------- Drive 模块数据管理器 ----------
# [P1修复] DriveDataManager 包含 Room 操作和回调接口，混淆后反射调用会失败
-keep class com.github.tvbox.osc.drive.data.DriveDataManager { *; }
-keep class com.github.tvbox.osc.drive.data.DriveDataManager$* { *; }
-keep class com.github.tvbox.osc.drive.data.DriveDatabase { *; }

# ---------- Drive 模块 Serializable 模型 ----------
-keep class com.github.tvbox.osc.drive.bean.** { *; }
-keepclassmembers class com.github.tvbox.osc.drive.bean.** implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---------- Gson（Drive 模块用） ----------
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.github.tvbox.osc.drive.bean.** { <fields>; }
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }

# ---------- sardine-android（WebDAV 客户端） ----------
-keep class com.thegrizzlylabs.sardineandroid.** { *; }
-dontwarn com.thegrizzlylabs.sardineandroid.**

# ---------- Commons Net（FTP 客户端） ----------
-keep class org.apache.commons.net.ftp.** { *; }
-dontwarn org.apache.commons.net.**

# ---------- Commons Compress（ZIP/7Z 解压） ----------
# [P1修复] 补全压缩包解压库的混淆规则
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.tukaani.xz.** { *; }
-dontwarn org.tukaani.xz.**

# ---------- SMBJ（SMB 2.0/3.0 客户端） ----------
-keep class com.hierynomus.smbj.** { *; }
-keep class com.hierynomus.mssmb2.** { *; }
-keep class com.hierynomus.msfscc.** { *; }
-keep class com.hierynomus.protocol.** { *; }
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**
-dontwarn net.java.dev.jna.**

# ---------- JCIFS-NG（SMB 1.0 / CIFS 客户端） ----------
-keep class jcifs.** { *; }
-dontwarn jcifs.**

# ---------- OkHttp（Drive 模块 Alist 请求） ----------
-dontwarn okhttp3.**
-dontwarn okio.**

# ---------- EventBus ----------
# [P1修复] 事件类也必须 keep，EventBus 通过反射实例化事件
-keep class com.github.tvbox.osc.drive.event.** { *; }
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# ---------- FTP 服务器 ----------
# [P1修复] SimpleFtpServer 内部通过反射/字符串构造的命令处理不需要 keep，
# 但保留类本身以防外部引用
-keep class com.github.tvbox.osc.drive.ftp.SimpleFtpServer { public *; }