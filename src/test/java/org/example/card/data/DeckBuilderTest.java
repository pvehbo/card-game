package org.example.card.data;

import org.example.card.model.Card;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-7 构筑规则：30 张/同名上限/单卡上限/未知卡/曲线/实例隔离。 */
class DeckBuilderTest {

    private static Card cardOf(String id) {
        return CardDatabase.load().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void standardDeckIsLegalThirty() {
        DeckBuilder builder = new DeckBuilder();
        builder.useStandard();
        assertTrue(builder.validate().isEmpty(), builder.validate().toString());
        assertEquals(30, builder.build().size());
        int[] curve = builder.curve();
        int total = 0;
        for (int count : curve) {
            total += count;
        }
        assertEquals(30, total, "曲线总和应为 30");
    }

    @Test
    void shortDeckIsRejected() {
        DeckBuilder builder = new DeckBuilder();
        builder.add(cardOf("m1"));
        assertEquals("牌组数量不足：1/30", builder.validate().orElseThrow());
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void thirdCopyIsRejected() {
        DeckBuilder builder = new DeckBuilder();
        assertTrue(builder.add(cardOf("m1")));
        assertTrue(builder.add(cardOf("m1")));
        assertFalse(builder.add(cardOf("m1")), "同名第 3 张应被拒绝");
    }

    @Test
    void singletonSecondCopyIsRejected() {
        DeckBuilder builder = new DeckBuilder();
        assertTrue(builder.add(cardOf("m7")));
        assertFalse(builder.add(cardOf("m7")), "明星单卡第 2 张应被拒绝");
    }

    @Test
    void unknownCardIsRejected() {
        DeckBuilder builder = new DeckBuilder();
        Card fake = new org.example.card.model.MinionCard("mx", "假", "垫", 1, 1, 0);
        assertFalse(builder.add(fake));
    }

    @Test
    void buildReturnsFreshInstances() {
        DeckBuilder builder = new DeckBuilder();
        builder.useStandard();
        List<Card> first = builder.build();
        List<Card> second = builder.build();
        for (int i = 0; i < first.size(); i++) {
            assertTrue(first.get(i) != second.get(i), "两次 build 不共享实例");
            assertEquals(first.get(i).getId(), second.get(i).getId());
        }
    }

    @Test
    void removeAndClear() {
        DeckBuilder builder = new DeckBuilder();
        builder.add(cardOf("m1"));
        assertTrue(builder.remove(cardOf("m1")));
        assertFalse(builder.remove(cardOf("m1")));
        builder.add(cardOf("m1"));
        builder.clear();
        assertEquals("牌组数量不足：0/30", builder.validate().orElseThrow());
    }

    @Test
    void loadIdsRoundTrip() {
        DeckBuilder builder = new DeckBuilder();
        builder.useStandard();
        List<String> ids = builder.ids();
        assertEquals(30, ids.size());

        DeckBuilder restored = new DeckBuilder();
        restored.loadIds(ids);
        assertTrue(restored.validate().isEmpty());
        assertEquals(ids, restored.ids());
        assertThrows(IllegalStateException.class,
                () -> restored.loadIds(List.of("nope")));
    }
}
