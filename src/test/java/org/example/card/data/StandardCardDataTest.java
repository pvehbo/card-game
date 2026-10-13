package org.example.card.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.card.model.Card;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.SpellCard;
import org.junit.jupiter.api.Test;

/**
 * 批3：标准覆盖值快照（提案 §6 候选表）+ 默认 30 张牌堆 + 三基准构筑。
 * 经典字段保持历史数值（确认②），标准值全部显式落在 standard 块。
 */
class StandardCardDataTest {

    private static Map<String, CardDatabase.CardEntry> byId(Ruleset ruleset) {
        Map<String, CardDatabase.CardEntry> map = new LinkedHashMap<>();
        for (CardDatabase.CardEntry entry : CardDatabase.entries(ruleset)) {
            map.put(entry.template().getId(), entry);
        }
        return map;
    }

    private static Map<String, Integer> counts(List<Card> deck) {
        Map<String, Integer> counts = new HashMap<>();
        for (Card card : deck) {
            counts.merge(card.getId(), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void classicValuesStayHistorical() {
        Map<String, CardDatabase.CardEntry> classic = byId(Ruleset.CLASSIC);
        MinionCard m1 = (MinionCard) classic.get("m1").template();
        assertEquals(0, m1.getCost());
        assertEquals(2, m1.getAttack());
        assertEquals(1, m1.getMaxHealth());
        MinionCard m5 = (MinionCard) classic.get("m5").template();
        assertEquals(0, m5.getCost());
        assertEquals(6, m5.getAttack());
        assertEquals(6, m5.getMaxHealth());
        SpellCard s1 = (SpellCard) classic.get("s1").template();
        assertEquals(0, s1.getCost());
        assertEquals(3, s1.getAmount());
        PetCard p1 = (PetCard) classic.get("p1").template();
        assertEquals(0, p1.getCost());
    }

    @Test
    void standardFollowsCandidateTable() {
        Map<String, CardDatabase.CardEntry> std = byId(Ruleset.STANDARD);
        MinionCard m1 = (MinionCard) std.get("m1").template();
        assertEquals(1, m1.getCost());
        assertEquals(2, m1.getAttack());
        assertEquals(3, m1.getMaxHealth());

        MinionCard m2 = (MinionCard) std.get("m2").template();
        assertEquals(2, m2.getCost());
        assertTrue(m2.hasKeyword(Keyword.TAUNT), "标准铁壁卫士必须带嘲讽");
        assertTrue(m2.getDescription().contains("嘲讽"));

        MinionCard m5 = (MinionCard) std.get("m5").template();
        assertEquals(5, m5.getCost());
        assertEquals(6, m5.getAttack());
        assertEquals(6, m5.getMaxHealth());

        MinionCard m7 = (MinionCard) std.get("m7").template();
        assertEquals(6, m7.getCost());
        assertEquals(6, m7.getAttack());
        assertEquals(5, m7.getMaxHealth());
        assertTrue(m7.hasKeyword(Keyword.CHARGE));

        MinionCard m9 = (MinionCard) std.get("m9").template();
        assertEquals(2, m9.getAttack());
        assertEquals(7, m9.getMaxHealth());

        MinionCard m10 = (MinionCard) std.get("m10").template();
        assertEquals(3, m10.getAttack());
        assertEquals(4, m10.getMaxHealth());
        assertTrue(m10.hasKeyword(Keyword.BATTLECRY));
        assertTrue(m10.hasKeyword(Keyword.DEATHRATTLE));

        MinionCard m14 = (MinionCard) std.get("m14").template();
        assertEquals(3, m14.getCost());
        assertEquals(2, m14.getAttack());
        assertEquals(4, m14.getMaxHealth());

        assertEquals(6, ((MinionCard) std.get("m17").template()).getMaxHealth());

        SpellCard s4 = (SpellCard) std.get("s4").template();
        assertEquals(4, s4.getCost());
        assertEquals(8, s4.getAmount());
        assertTrue(s4.getDescription().contains("8"));

        assertEquals(3, ((SpellCard) std.get("s5").template()).getCost());

        assertEquals(3, ((PetCard) std.get("p1").template()).getCost());
        assertEquals(5, ((PetCard) std.get("p4").template()).getCost());
        assertEquals(6, ((PetCard) std.get("p5").template()).getCost());
    }

    @Test
    void standardDeckIsThirtyPerMiddleTemplate() {
        List<Card> deck = CardDatabase.deck(Ruleset.STANDARD);
        assertEquals(30, deck.size());
        Map<String, Integer> expected = new HashMap<>();
        for (String id : List.of("m1", "m2", "m3", "m4", "m5", "m6", "m10", "m15", "m16")) {
            expected.put(id, 2);
        }
        expected.put("m7", 1);
        expected.put("m8", 1);
        expected.put("s1", 1);
        expected.put("s2", 1);
        expected.put("s3", 1);
        expected.put("s5", 2);
        expected.put("s6", 2);
        expected.put("p1", 1);
        expected.put("p4", 1);
        expected.put("p5", 1);
        assertEquals(expected, counts(deck));
        assertEquals(40, CardDatabase.standardDeck().size(), "经典标准牌堆仍是 40 张");
    }

    @Test
    void standardCopyLimitsMatchProposal() {
        Map<String, CardDatabase.CardEntry> std = byId(Ruleset.STANDARD);
        assertEquals(1, std.get("m7").copyLimit());
        assertEquals(1, std.get("m17").copyLimit());
        for (String id : List.of("p1", "p2", "p3", "p4", "p5")) {
            assertEquals(1, std.get(id).copyLimit(), id);
        }
        // 不进默认牌堆但仍可构筑：deckCopies=0 且 copyLimit=2
        assertEquals(0, std.get("m9").deckCopies());
        assertEquals(2, std.get("m9").copyLimit());
        assertEquals(0, std.get("s4").deckCopies());
        assertEquals(2, std.get("s4").copyLimit());
    }

    @Test
    void foundationDecksFollowProposal() {
        Map<String, int[]> curves = Map.of(
                "A", new int[]{0, 8, 6, 12, 2, 2, 0, 0, 0, 0, 0},
                "M", new int[]{0, 5, 9, 6, 4, 4, 2, 0, 0, 0, 0},
                "D", new int[]{0, 4, 6, 5, 8, 3, 4, 0, 0, 0, 0});
        Map<String, Double> avgCosts = Map.of("A", 2.47, "M", 2.97, "D", 3.40);
        Map<String, CardDatabase.CardEntry> std = byId(Ruleset.STANDARD);
        for (String key : List.of("A", "M", "D")) {
            List<Card> deck = CardDatabase.foundationDeck(key);
            assertEquals(30, deck.size(), key);
            int[] curve = new int[11];
            double totalCost = 0;
            for (Card card : deck) {
                curve[Math.min(10, card.getCost())]++;
                totalCost += card.getCost();
            }
            assertArrayEquals(curves.get(key), curve, key + " 费用曲线");
            assertEquals(avgCosts.get(key), totalCost / deck.size(), 0.011, key + " 平均费用");
            for (Map.Entry<String, Integer> e : counts(deck).entrySet()) {
                assertTrue(e.getValue() <= std.get(e.getKey()).copyLimit(),
                        key + " " + e.getKey() + " 超构筑上限");
            }
        }
        assertThrows(IllegalStateException.class, () -> CardDatabase.foundationDeck("C"));
    }
}
