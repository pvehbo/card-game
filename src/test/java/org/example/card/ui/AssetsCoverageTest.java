package org.example.card.ui;

import org.example.card.data.CardDatabase;
import org.example.card.model.Card;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0 资源规范冻结锁：卡库中每一种卡都必须有 {@code /images/cards/<id>.png}。
 *
 * <p>只查 classpath 资源存在性（不初始化 JavaFX，避免 headless CI 起 toolkit）。
 * 删图做回退冒烟时可临时跳过本测试（-Dtest=!AssetsCoverageTest）。
 */
class AssetsCoverageTest {

    @Test
    void everyCardHasPngArtwork() {
        List<Card> cards = CardDatabase.load();
        assertEquals(30, cards.size(), "基础卡表应 30 种");
        List<String> missing = new ArrayList<>();
        for (Card card : cards) {
            String path = "/images/cards/" + card.getId() + ".png";
            if (getClass().getResourceAsStream(path) == null) {
                missing.add(card.getId());
            }
        }
        assertTrue(missing.isEmpty(), "缺卡图: " + missing);
    }

    @Test
    void heroesAndBackgroundsPresent() {
        assertNotNull(getClass().getResourceAsStream("/images/heroes/player.png"), "player.png 缺失");
        assertNotNull(getClass().getResourceAsStream("/images/heroes/ai.png"), "ai.png 缺失");
        assertNotNull(getClass().getResourceAsStream("/images/backgrounds/board.jpg"), "board.jpg 缺失");
        assertNotNull(getClass().getResourceAsStream("/images/backgrounds/board-light.jpg"), "board-light.jpg 缺失");
    }
}
