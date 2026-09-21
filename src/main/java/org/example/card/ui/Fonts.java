package org.example.card.ui;

import javafx.scene.text.Font;

import java.util.List;

/**
 * 中文字体解析：JavaFX 的 CSS 多字体回退列表在部分平台上不可靠
 * （会回退到无中文字形的字体，导致界面出现乱码方块）。
 *
 * 这里在运行时按平台优先级挑一个真实存在的中文字体家族，
 * 再用单一字体名设置样式，保证中文正常显示。
 */
public final class Fonts {

    /** 按优先级排列的中文字体候选（覆盖 macOS / Windows / Linux）。 */
    private static final List<String> CANDIDATES = List.of(
            "PingFang SC",          // macOS 苹方（默认中文界面字体）
            "Hiragino Sans GB",     // macOS 冬青黑
            "Heiti SC",             // macOS 黑体
            "Microsoft YaHei UI",   // Windows 微软雅黑 UI
            "Microsoft YaHei",      // Windows 微软雅黑
            "Noto Sans CJK SC",     // Linux 思源黑体
            "Source Han Sans SC",   // Linux 思源黑体（另一命名）
            "WenQuanYi Micro Hei",  // Linux 文泉驿
            "Arial Unicode MS");    // 兜底：覆盖较全的通用字体

    private static String resolved;

    private Fonts() {
    }

    /** 返回本机可用的第一个中文字体家族名；都没有则返回 null（用系统默认）。 */
    public static synchronized String cjkFamily() {
        if (resolved != null) {
            return resolved.isEmpty() ? null : resolved;
        }
        List<String> installed = Font.getFamilies();
        for (String candidate : CANDIDATES) {
            if (installed.contains(candidate)) {
                resolved = candidate;
                return resolved;
            }
        }
        resolved = "";
        return null;
    }

    /** 生成可用的 -fx-font-family 值（找不到中文字体时返回 null）。 */
    public static String cssFontFamily() {
        String family = cjkFamily();
        return family == null ? null : "-fx-font-family: \"" + family + "\";";
    }
}
