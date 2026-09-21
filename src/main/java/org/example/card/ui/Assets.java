package org.example.card.ui;

import javafx.scene.image.Image;
import org.example.card.model.Card;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 图片资源加载器（找不到就返回 empty，由调用方回退到程序化图形）。
 *
 * 约定目录（放在 src/main/resources/images/ 下）：
 *   images/cards/<卡牌id>.png        例：m1.png / s1.png / p1.png
 *   images/cards/<卡牌名称>.png      例：幼龙.png（id 找不到时按名称再试）
 *   images/cards/frame.png          卡框叠加层（可选，会盖在卡图之上）
 *   images/heroes/player.png        玩家英雄头像（可选）
 *   images/heroes/ai.png            AI 英雄头像（可选）
 *   images/backgrounds/board.jpg    战场背景（可选，铺满窗口）
 *
 * 支持 PNG / JPG（JavaFX 原生）；建议卡图 256×256 或 320×240、带透明通道。
 */
public final class Assets {

    private static final String BASE = "/images/";
    private static final Map<String, Optional<Image>> CACHE = new HashMap<>();

    private Assets() {
    }

    /** 卡图：先按 id 再按名称，先 png 再 jpg。 */
    public static Optional<Image> cardArt(Card card) {
        return firstOf(
                BASE + "cards/" + card.getId() + ".png",
                BASE + "cards/" + card.getId() + ".jpg",
                BASE + "cards/" + card.getName() + ".png",
                BASE + "cards/" + card.getName() + ".jpg");
    }

    /** 卡框叠加层（可选）。 */
    public static Optional<Image> cardFrame() {
        return firstOf(BASE + "cards/frame.png", BASE + "cards/frame.jpg");
    }

    /** 英雄头像。key 传 "player" 或 "ai"。 */
    public static Optional<Image> hero(String key) {
        return firstOf(BASE + "heroes/" + key + ".png", BASE + "heroes/" + key + ".jpg");
    }

    /** 战场背景。 */
    public static Optional<Image> background() {
        return firstOf(BASE + "backgrounds/board.png", BASE + "backgrounds/board.jpg");
    }

    private static Optional<Image> firstOf(String... paths) {
        for (String path : paths) {
            Optional<Image> image = load(path);
            if (image.isPresent()) {
                return image;
            }
        }
        return Optional.empty();
    }

    /** 按 classpath 路径加载并缓存（不存在则缓存 empty，避免重复 IO）。 */
    public static Optional<Image> load(String path) {
        if (CACHE.containsKey(path)) {
            return CACHE.get(path);
        }
        Optional<Image> result = Optional.empty();
        try (InputStream in = Assets.class.getResourceAsStream(path)) {
            if (in != null) {
                result = Optional.of(new Image(in));
            }
        } catch (Exception ignored) {
            // 图片损坏或格式不支持：当作没有，走回退
        }
        CACHE.put(path, result);
        return result;
    }

    /** 清空缓存（换图/热重载时可用）。 */
    public static void clearCache() {
        CACHE.clear();
    }
}
