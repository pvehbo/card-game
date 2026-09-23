package org.example.card.data;

import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.SpellCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** S5 卡牌 JSON 化 + 加载器（CardDatabase）测试。 */
class CardDatabaseTest {

    @Test
    void loadsTwentyCardsWithExpectedTypeDistribution() {
        List<Card> deck = CardDatabase.standardDeck();
        assertEquals(20, deck.size(), "standardDeck 应返回 20 张（10 种 × 2）");
        assertEquals(12, deck.stream().filter(c -> c instanceof MinionCard).count(), "翻倍后随从应 12 张");
        assertEquals(4, deck.stream().filter(c -> c instanceof SpellCard).count(), "翻倍后法术应 4 张");
        assertEquals(4, deck.stream().filter(c -> c instanceof PetCard).count(), "翻倍后宠物应 4 张");
        assertEquals(10, CardDatabase.load().size(), "基础卡表应 10 种");
    }

    @Test
    void standardDeckMatchesBattleDemoDeckIdMultiset() {
        // BattleDemoTest.demoDeck 为 private 无法直接引用，这里逐字复刻它的 id 多重集合并对比。
        List<String> expected = new ArrayList<>();
        for (int copy = 0; copy < 2; copy++) {
            expected.addAll(List.of("m1", "m2", "m3", "m4", "m5", "m6", "s1", "s2", "p1", "p2"));
        }
        List<String> actual = new ArrayList<>();
        for (Card card : CardDatabase.standardDeck()) {
            actual.add(card.getId());
        }
        Collections.sort(expected);
        Collections.sort(actual);
        assertEquals(expected, actual, "standardDeck 与 BattleDemoTest.demoDeck 的 id 多重集合应一致");
    }

    @Test
    void missingFieldFailsFastWithFileLine() {
        String json = "{\"schemaVersion\": 1, \"cards\": ["
                + "{\"name\": \"无 id 卡\", \"text\": \"x\", \"attack\": 1, \"health\": 1, \"cost\": 0, \"keywords\": []}"
                + "]}";
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> CardDatabase.loadFrom(json, "missing.json"));
        assertTrue(e.getMessage().startsWith("missing.json:"), "错误消息应带文件名:行号，实际: " + e.getMessage());
        assertTrue(e.getMessage().contains("id"), "应指向缺失的 id 字段，实际: " + e.getMessage());
    }

    @Test
    void duplicateIdFailsFast() {
        String json = "{\"schemaVersion\": 1, \"cards\": ["
                + "{\"id\": \"m1\", \"name\": \"一\", \"text\": \"t\", \"attack\": 2, \"health\": 1, \"cost\": 0, \"keywords\": []},"
                + "{\"id\": \"m1\", \"name\": \"二\", \"text\": \"t\", \"attack\": 1, \"health\": 5, \"cost\": 0, \"keywords\": []}"
                + "]}";
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> CardDatabase.loadFrom(json, "dupid.json"));
        assertTrue(e.getMessage().startsWith("dupid.json:"), "错误消息应带文件名:行号，实际: " + e.getMessage());
        assertTrue(e.getMessage().contains("id 重复"), "应报告 id 重复，实际: " + e.getMessage());
    }

    @Test
    void unknownKeywordFailsFast() {
        String json = "{\"schemaVersion\": 1, \"cards\": ["
                + "{\"id\": \"m1\", \"name\": \"一\", \"text\": \"t\", \"attack\": 2, \"health\": 1, \"cost\": 0, \"keywords\": [\"HURT\"]}"
                + "]}";
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> CardDatabase.loadFrom(json, "badkeyword.json"));
        assertTrue(e.getMessage().startsWith("badkeyword.json:"), "错误消息应带文件名:行号，实际: " + e.getMessage());
        assertTrue(e.getMessage().contains("未知关键字"), "应报告未知关键字，实际: " + e.getMessage());
    }

    @Test
    void invalidSpellKindFailsFast() {
        String json = "{\"schemaVersion\": 1, \"cards\": ["
                + "{\"id\": \"s9\", \"name\": \"火\", \"text\": \"t\", \"kind\": \"FLAME\", \"amount\": 3, \"cost\": 0, \"keywords\": []}"
                + "]}";
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> CardDatabase.loadFrom(json, "badkind.json"));
        assertTrue(e.getMessage().startsWith("badkind.json:"), "错误消息应带文件名:行号，实际: " + e.getMessage());
        assertTrue(e.getMessage().contains("非法法术 kind"), "应报告非法 kind，实际: " + e.getMessage());
    }
}