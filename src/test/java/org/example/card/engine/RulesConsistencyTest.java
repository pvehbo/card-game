package org.example.card.engine;

import org.example.card.model.Deck;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则一致性锁定（批① T6）：把 v0.1 平衡方案依赖的结算语义写成测试，
 * 防止后续调参时悄悄走样：
 * 1. 互撞双归零 → 同一次结算里双方都下场（交换随从是公平的对拼）；
 * 2. 0 伤害不破圣盾（圣盾只挡「有伤害」的打击）；
 * 3. 双英雄同时归零 → 会话终局，胜负口径下无胜者（平局语义）。
 */
class RulesConsistencyTest {

    private static final Consumer<String> QUIET = msg -> {
    };

    @Test
    void mutualTradeRemovesBothMinions() {
        PlayerState self = withField("我", minion("a1", "攻3血3", 3, 3));
        PlayerState foe = withField("敌", minion("b1", "攻3血3", 3, 3));

        engine().attack(self, foe, self.getField().get(0), foe.getField().get(0), QUIET);

        assertTrue(self.getField().isEmpty(), "互撞双归零：攻击者下场");
        assertTrue(foe.getField().isEmpty(), "互撞双归零：防守方下场");
        assertTrue(self.getGraveyard().stream().anyMatch(c -> "a1".equals(c.getId())),
                "攻击者进其主人墓地");
        assertTrue(foe.getGraveyard().stream().anyMatch(c -> "b1".equals(c.getId())),
                "防守方进其主人墓地");
    }

    @Test
    void zeroDamageDoesNotBreakShield() {
        PlayerState self = withField("我", minion("a1", "0攻", 0, 5));
        PlayerState foe = withField("敌",
                minion("b1", "带盾", 2, 3, Keyword.DIVINE_SHIELD));

        engine().attack(self, foe, self.getField().get(0), foe.getField().get(0), QUIET);

        assertTrue(foe.getField().get(0).hasDivineShield(), "0 伤害不破圣盾");
        assertEquals(0, foe.getField().get(0).getDamageTaken(), "目标无伤害");
        assertEquals(2, self.getField().get(0).getDamageTaken(), "0 攻随从吃 2 伤反击");
        assertFalse(self.getField().isEmpty(), "5 血随从 2 伤后仍存活（无圣盾可挡）");
    }

    @Test
    void bothHeroesZeroMeansGameOverWithoutWinner() {
        GameSession session = GameSession.newPveBattle(new java.util.Random(7));
        session.getPlayer().damage(PlayerState.START_LIFE);
        session.getAi().damage(PlayerState.START_LIFE);

        assertTrue(session.gameOver(), "双英雄归零即终局");
        assertTrue(session.getPlayer().isDefeated() && session.getAi().isDefeated(),
                "双方同时败亡——HeadlessGame 口径下 playerWon/aiWon 均为 false（平局）");
        // 与 HeadlessGame.Result.decided() 的判据同构：两 defeated 全真 → decided=false
        assertFalse(session.getPlayer().isDefeated() ^ session.getAi().isDefeated(),
                "判定互斥位不成立：非单方胜利");
    }

    // ---------- 摆拍助手 ----------

    private static GameEngine engine() {
        return new GameEngine(new org.example.card.ai.SimpleAi());
    }

    private static MinionCard minion(String id, String name, int attack, int health, Keyword... keywords) {
        MinionCard m = new MinionCard(id, name, "", attack, health, 1, List.of(keywords));
        m.setSummoningSickness(false);
        return m;
    }

    private static PlayerState withField(String name, MinionCard... minions) {
        PlayerState p = new PlayerState(name, new Deck(new ArrayList<>()));
        p.getField().addAll(List.of(minions));
        return p;
    }
}
