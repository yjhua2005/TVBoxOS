package com.github.tvbox.osc.bean;

/**
 * 直播源数据模型
 */
public class LiveSourceBean {
    private boolean isOfficial;
    private boolean isSelected;
    private String sourceName = "";
    private String sourceUrl = "";
    private boolean canDelete = true;
    private String extraKey = "";
    private String name = "";

    public boolean getCanDelete() {
        return this.canDelete;
    }

    public void setCanDelete(boolean canDelete) {
        this.canDelete = canDelete;
    }

    public String getExtraKey() {
        return this.extraKey;
    }

    public void setExtraKey(String extraKey) {
        this.extraKey = extraKey;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceName() {
        return this.sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceUrl() {
        return this.sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getUniKey() {
        return String.valueOf(this.sourceUrl.hashCode());
    }

    public boolean isOfficial() {
        return this.isOfficial;
    }

    public void setOfficial(boolean official) {
        this.isOfficial = official;
    }

    public boolean isSelected() {
        return this.isSelected;
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }
}
