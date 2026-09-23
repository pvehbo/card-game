package org.example.card.service;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 文案国际化（S8）：resources/i18n/messages(+_zh).properties，默认中文。
 * 缺 key 时回退 key 本身（不炸界面）。全量界面文案替换是 v1.2 的活，
 * 本版先接音效开关验证链路。
 */
public final class I18n {

    private I18n() {
    }

    public static String get(String key) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", Locale.getDefault());
            if (bundle.containsKey(key)) {
                return bundle.getString(key);
            }
        } catch (MissingResourceException | SecurityException ex) {
            GameLog.warn("读取文案失败 key=" + key + ": " + ex.getMessage());
        }
        return key;
    }
}
