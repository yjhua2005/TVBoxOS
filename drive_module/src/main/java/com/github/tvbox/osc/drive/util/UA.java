package com.github.tvbox.osc.drive.util;

import com.github.tvbox.osc.drive.DriveModule;

import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * 随机 User-Agent 生成器。
 * 需要将 ua.db 放入 assets 目录。
 */
public class UA {

    private static InputStream openAssets(String path) {
        try {
            return DriveModule.getAppContext().getAssets().open(path);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String random() {
        try {
            InputStream fis = openAssets("ua.db");
            if (fis == null) return DEFAULT_UA;
            DataInputStream dis = new DataInputStream(fis);
            int len = dis.readInt();
            int random = new Random().nextInt(len);
            dis.skipBytes(random * 4);
            int offset = dis.readInt();
            dis.skipBytes((len - 1 - random) * 4 + offset);
            String s = dis.readUTF();
            dis.close();
            return s;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return DEFAULT_UA;
    }

    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36";
}