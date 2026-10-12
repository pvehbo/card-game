package org.example.card.data;

import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 批② S2：卡 JSON 双层份数（deckCopies/copyLimit）与战吼/亡语效果 spec 契约。 */
class CardDatabaseEntriesTest {

    /** 未闭合的随从体：调用方追加 extra 键后再补右括号。 */
    private static String minion(String id) {
        return "{\"id\": \"" + id + "\", \"name\": \"镜" + id + "\", \"text\": \"t\","
                + " \"attack\": 2, \"health\": 2, \"cost\": 1, \"keywords\": []";
    }

    private static List<CardDatabase.CardEntry> entries(String... cardJsons) {
        String json = "{\"schemaVersion\": 1, \"cards\": [" + String.join(",", cardJsons) + "]}";
        return CardDatabase.loadEntriesFrom(json, "entries-单测.json");
    }

    @Test
    void doubleLayerCopiesLoadWithDefaultsAndExplicitValues() {
        List<CardDatabase.CardEntry> list = entries(
                minion("e1") + "}",
                minion("e2") + ", \"copies\": 1}",
                minion("e3") + ", \"deckCopies\": 1, \"copyLimit\": 2}");

        assertEquals(3, list.size());
        assertEquals(2, list.get(0).deckCopies(), "新键缺省入堆 2");
        assertEquals(2, list.get(0).copyLimit());
        assertEquals(1, list.get(1).deckCopies(), "老键 copies 兼容");
        assertEquals(1, list.get(1).copyLimit(), "老键 copies=1 收紧构筑上限");
        assertEquals(1, list.get(2).deckCopies(), "deckCopies 独立可配");
        assertEquals(2, list.get(2).copyLimit(), "copyLimit 与 deckCopies 解耦");
    }

    @Test
    void copyRangeIsEnforced() {
        IllegalStateException e1 = assertThrows(IllegalStateException.class,
                () -> entries(minion("e1") + ", \"deckCopies\": 3}"));
        assertTrue(e1.getMessage().contains("deckCopies"), e1.getMessage());
        IllegalStateException e2 = assertThrows(IllegalStateException.class,
                () -> entries(minion("e1") + ", \"copyLimit\": 0}"));
        assertTrue(e2.getMessage().contains("copyLimit"), e2.getMessage());
        IllegalStateException e3 = assertThrows(IllegalStateException.class,
                () -> entries(minion("e1") + ", \"copyLimit\": 3}"));
        assertTrue(e3.getMessage().contains("copyLimit"), e3.getMessage());
    }

    @Test
    void battlecrySpecRoundTrips() {
        List<CardDatabase.CardEntry> list = entries(
                "{\"id\": \"bc\", \"name\": \"火袭\", \"text\": \"t\", \"attack\": 2, \"health\": 2, \"cost\": 1,"
                        + " \"keywords\": [\"BATTLECRY\"],"
                        + " \"battlecry\": {\"kind\": \"DAMAGE\", \"amount\": 3}}");
        MinionCard card = (MinionCard) list.get(0).template();
        assertEquals(Keyword.DAMAGE, card.battlecrySpec().kind(), "spec kind = DAMAGE");
        assertEquals(3, card.battlecrySpec().amount(), "spec amount = 3");
    }

    @Test
    void specKindAndAmountAreChecked() {
        String battlecryField = "\"keywords\": [\"BATTLECRY\"], \"battlecry\": {\"kind\": \"%s\", \"amount\": %d}";
        String badKind = assertThrows(IllegalStateException.class,
                () -> entries("{\"id\": \"b1\", \"name\": \"x\", \"text\": \"t\", \"attack\": 1, \"health\": 1, \"cost\": 1, "
                        + String.format(battlecryField, "SUMMON", 1) + "}"))
                .getMessage();
        assertTrue(badKind.contains("DAMAGE/HEAL/DRAW"), badKind);
        String badAmount = assertThrows(IllegalStateException.class,
                () -> entries("{\"id\": \"b2\", \"name\": \"x\", \"text\": \"t\", \"attack\": 1, \"health\": 1, \"cost\": 1, "
                        + String.format(battlecryField, "DAMAGE", 0) + "}"))
                .getMessage();
        assertTrue(badAmount.contains("amount"), badAmount);
    }

    @Test
    void specNeedsItsKeyword() {
        String missingBattlecry = assertThrows(IllegalStateException.class, () -> entries(
                "{\"id\": \"b3\", \"name\": \"无吼\", \"text\": \"t\", \"attack\": 1, \"health\": 1, \"cost\": 1,"
                        + " \"keywords\": [], \"battlecry\": {\"kind\": \"DAMAGE\", \"amount\": 1}}"))
                .getMessage();
        assertTrue(missingBattlecry.contains("BATTLECRY"), missingBattlecry);

        String missingDeathrattle = assertThrows(IllegalStateException.class, () -> entries(
                "{\"id\": \"d3\", \"name\": \"无亡\", \"text\": \"t\", \"attack\": 1, \"health\": 1, \"cost\": 1,"
                        + " \"keywords\": [], \"deathrattle\": {\"kind\": \"HEAL\", \"amount\": 1}}"))
                .getMessage();
        assertTrue(missingDeathrattle.contains("DEATHRATTLE"), missingDeathrattle);
    }

    @Test
    void deathrattleSpecRoundTrips() {
        List<CardDatabase.CardEntry> list = entries(
                "{\"id\": \"d1\", \"name\": \"回响\", \"text\": \"t\", \"attack\": 1, \"health\": 1, \"cost\": 2,"
                        + " \"keywords\": [\"DEATHRATTLE\"],"
                        + " \"deathrattle\": {\"kind\": \"HEAL\", \"amount\": 5},"
                        + " \"deckCopies\": 2, \"copyLimit\": 1}");
        MinionCard card = (MinionCard) list.get(0).template();
        assertEquals(Keyword.HEAL, card.deathrattleSpec().kind());
        assertEquals(5, card.deathrattleSpec().amount());
        assertEquals(2, list.get(0).deckCopies());
        assertEquals(1, list.get(0).copyLimit());
    }
}