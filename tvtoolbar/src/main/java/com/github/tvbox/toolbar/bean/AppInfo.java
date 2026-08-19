package com.github.tvbox.toolbar.bean;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppInfo {

    public String name;
    public Drawable icon;
    public String pack;

    public AppInfo(String name, String pack, Drawable icon) {
        this.name = name;
        this.pack = pack;
        this.icon = icon;
    }

    public static AppInfo get(Context context, ApplicationInfo info) {
        Drawable icon = info.loadIcon(context.getPackageManager());
        String name = info.loadLabel(context.getPackageManager()).toString();
        String pack = info.packageName;
        return new AppInfo(name, pack, icon);
    }

    public String getName() {
        return name;
    }

    public String getPack() {
        return pack;
    }

    public Drawable getIcon() {
        return icon;
    }

    public static class Sorter implements Comparator<AppInfo> {

        public static void sort(List<AppInfo> items) {
            Collections.sort(items, new Sorter());
        }

        @Override
        public int compare(AppInfo info1, AppInfo info2) {
            return info1.getName().compareToIgnoreCase(info2.getName());
        }
    }
}