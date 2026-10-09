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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // R-02：给随从带上「非默认」的召唤失调 / 已出手状态，读档必须原样恢复
        played.setSummoningSickness(false);
        played.registerAttack();
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

    /** R-02：读档必须保留随从的召唤失调与已出手次数（不能一律回到默认）。 */
    @Test
    void saveLoadPreservesSummoningSicknessAndAttacksUsed() {
        GameSession session = dirtiedSession();
        MinionCard before = session.getPlayer().getField().get(0);
        boolean sicknessBefore = before.isSummoningSickness();
        int attacksBefore = before.getAttacksUsed();

        GameSession restored = SaveService.load(SaveService.save(session));
        MinionCard after = restored.getPlayer().getField().get(0);

        assertEquals(sicknessBefore, after.isSummoningSickness(), "读档必须原样恢复召唤失调状态");
        assertEquals(attacksBefore, after.getAttacksUsed(), "读档必须原样恢复已出手次数");
    }

    /** R-02：老存档缺 sickness/attacksUsed 字段时用兼容默认（可攻击、未出手）。 */
    @Test
    void oldSaveWithoutNewFieldsDefaultsToAttackReady() {
        GameSession session = dirtiedSession();
        // 模拟老存档：把新增的两个战场字段整段删掉
        String old = SaveService.save(session)
                .replaceAll(",\"sickness\":\"(true|false)\"", "")
                .replaceAll(",\"attacksUsed\":\\d+", "");
        GameSession restored = SaveService.load(old);
        MinionCard minion = restored.getPlayer().getField().get(0);
        assertFalse(minion.isSummoningSickness(), "老存档缺字段 → 默认可攻击");
        assertEquals(0, minion.getAttacksUsed(), "老存档缺字段 → 默认未出手");
    }
}
