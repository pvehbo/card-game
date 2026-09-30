package org.example.card.ui;

import java.net.URL;

/**
 * UI 主题（V3 双主题）：暗色奇幻 / 亮色原野。
 *
 * <p>纯映射，无 JavaFX Toolkit 依赖，可在无头单测里直接测：枚举 ↔ 配置 id ↔
 * 样式表路径。界面层用 {@link #cssUrl()} 拿到样式表 URL 后挂到 Scene 上。
 */
public enum Theme {

    /** 暗色奇幻（默认）：深色石质背景 + 金属镶边 + 金色高光。 */
    DARK("dark", "/theme-dark.css", false),
    /** 亮色原野：羊皮纸底 + 青铜镶边 + 日光高光。 */
    LIGHT("light", "/theme-light.css", true);

    private final String id;
    private final String cssPath;
    private final boolean light;

    Theme(String id, String cssPath, boolean light) {
        this.id = id;
        this.cssPath = cssPath;
        this.light = light;
    }

    /** 配置落盘用的稳定 id（{@code ui.theme}）。 */
    public String id() {
        return id;
    }

    /** 样式表 classpath 路径。 */
    public String cssPath() {
        return cssPath;
    }

    /** 是否亮主题（决定战场背景图选哪张）。 */
    public boolean isLight() {
        return light;
    }

    /** 样式表 URL（external form），资源缺失时返回 null，调用方回退不断 css。 */
    public String cssUrl() {
        URL url = Theme.class.getResource(cssPath);
        return url == null ? null : url.toExternalForm();
    }

    /** 切换到另一个主题。 */
    public Theme toggle() {
        return this == DARK ? LIGHT : DARK;
    }

    /** 配置读盘：未知/空 id 一律回退默认暗色，绝不炸启动。 */
    public static Theme fromId(String id) {
        for (Theme theme : values()) {
            if (theme.id.equals(id)) {
                return theme;
            }
        }
        return DARK;
    }
}
