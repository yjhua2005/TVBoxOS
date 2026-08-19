package com.github.tvbox.osc.bean;

import java.io.Serializable;

/**
 * 列表项基类（参照ysc BaseItem）
 * 提供 getUniKey() 用于 DiffUtil 对比和去重
 */
public class BaseItem implements Serializable {
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj != null && (obj instanceof BaseItem)) {
            String thisKey = getUniKey();
            String otherKey = ((BaseItem) obj).getUniKey();
            if (thisKey == null && otherKey == null) return true;
            if (thisKey == null || otherKey == null) return false;
            return thisKey.equals(otherKey);
        }
        return false;
    }

    public String getUniKey() {
        return "";
    }

    @Override
    public int hashCode() {
        String uniKey = getUniKey();
        if (uniKey != null) {
            return uniKey.hashCode();
        }
        return 0;
    }
}
