package org.example.card.service;

import org.example.card.ai.SimpleAi;
import org.example.card.engine.GameEngine;
import org.example.card.engine.GameSession;
import org.example.card.model.MinionCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** S8 存档兼容：存取一致、未知字段忽略、高版本拒绝、未知卡拒绝。 */
class SaveCompatTest {

    private static GameSession dirtiedSession() {
        GameSession session = GameSession.newPveBattle(new Random(11));
        GameEngine engine = new GameEngine(new SimpleAi());
        List<String> logs = new ArrayList<>();
        engine.startPlayerTurn(session.getPlayer(), session.getAi(), logs::add);
        // 上 1 张随从并让它带伤，随便打出去 1 张法术/宠物（有就打）
        for (var card : new ArrayList<>(session.getPlayer().getHand())) {
            if (card instanceof MinionCard m) {
                engine.playMinion(session.getPlayer(), m, logs::add);
                break;
            }
        }
        MinionCard played = session.getPlayer().getField().get(0);
        played.takeDamage(1);
        for (var card : new ArrayList<>(session.getPlayer().getHand())) {
            if (card instanceof org.example.card.model.SpellCard s) {
                engine.playSpell(session.getPlayer(), session.getAi(), s, logs::add);
                break;
            }
        }
        return session;
    }

    private static void assertSameGame(GameSession expected, GameSession actual) {
        assertEquals(expected.isYourTurn(), actual.isYourTurn());
        assertSide(expected.getPlayer(), actual.getPlayer());
        assertSide(expected.getAi(), actual.getAi());
    }

    private static void assertSide(org.example.card.model.PlayerState expected,
                                   org.example.card.model.PlayerState actual) {
        assertEquals(expected.getLifePoints(), actual.getLifePoints());
        assertEquals(ids(expected.getHand()), ids(actual.getHand()));
        assertEquals(expected.getField().size(), actual.getField().size());
        for (int i = 0; i < expected.getField().size(); i++) {
            assertEquals(expected.getField().get(i).getId(), actual.getField().get(i).getId());
            assertEquals(expected.getField().get(i).getDamageTaken(),
                    actual.getField().get(i).getDamageTaken());
        }
        assertEquals(ids(expected.getPets()), ids(actual.getPets()));
        assertEquals(ids(expected.getDeck().viewFromTop()), ids(actual.getDeck().viewFromTop()));
        assertEquals(ids(expected.getGraveyard()), ids(actual.getGraveyard()));
    }

    private static List<String> ids(List<? extends org.example.card.model.Card> cards) {
        return cards.stream().map(c -> c.getId()).toList();
    }

    @Test
    void saveLoadRoundTrip() {
        GameSession session = dirtiedSession();
        GameSession restored = SaveService.load(SaveService.save(session));
        assertSameGame(session, restored);
        assertTrue(session.getPlayer().getHand().size() > 0, "脏局手牌非空才有意义");
    }

    @Test
    void unknownFieldsAreIgnored() {
        GameSession session = dirtiedSession();
        String json = SaveService.save(session).replace("\"yourTurn\"",
                "\"future\":1,\"yourTurn\"");
        GameSession restored = SaveService.load(json);
        assertSameGame(session, restored);
    }

    @Test
    void futureVersionIsRejected() {
        GameSession session = dirtiedSession();
        String json = SaveService.save(session).replace("\"saveVersion\":1", "\"saveVersion\":999");
        assertThrows(IllegalStateException.class, () -> SaveService.load(json));
    }

    @Test
    void unknownCardIsRejected() {
        GameSession session = dirtiedSession();
        String json = SaveService.save(session).replaceFirst("\"m[0-9]\"", "\"mx-nope\"");
        assertThrows(IllegalStateException.class, () -> SaveService.load(json));
    }
}
