package com.github.tvbox.osc.drive.util;


import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class StringUtils {

    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    public static boolean isNotEmpty(CharSequence str) {
        return !isEmpty(str);
    }

    public static boolean isNull(Object obj) {
        return obj == null;
    }

    public static boolean isNotNull(Object obj) {
        return !isNull(obj);
    }

    public static boolean isEmpty(Object obj) {
        if (obj == null) return true;
        else if (obj instanceof CharSequence) return ((CharSequence) obj).length() == 0;
        else if (obj instanceof Collection) return ((Collection) obj).isEmpty();
        else if (obj instanceof Map) return ((Map) obj).isEmpty();
        else if (obj.getClass().isArray()) return Array.getLength(obj) == 0;
        return false;
    }

    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }

    public static String getBaseUrl(String url) {
        if (isEmpty(url)) {
            return url;
        }
        String baseUrls = url.replace("http://", "").replace("https://", "");
        String baseUrl2 = baseUrls.split("/")[0];
        String baseUrl;
        if (url.startsWith("https")) {
            baseUrl = "https://" + baseUrl2;
        } else {
            baseUrl = "http://" + baseUrl2;
        }
        return baseUrl;
    }
}