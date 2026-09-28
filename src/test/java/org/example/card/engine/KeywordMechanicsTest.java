package org.example.card.engine;

import org.example.card.ai.SimpleAi;
import org.example.card.effect.GameContext;
import org.example.card.effect.Trigger;
import org.example.card.effect.TriggerSystem;
import org.example.card.event.GameEvent;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-2 关键词：冲锋/嘲讽真机制；战吼/亡语走触发管线（探针端到端）。 */
class KeywordMechanicsTest {

    @AfterEach
    void clearTriggers() {
        TriggerSystem.reset();
    }

    private static PlayerState playerOf(List<Card> cards) {
        return new PlayerState("测试", new Deck(cards));
    }

    private static MinionCard minion(String name, int atk, int hp, Keyword... keywords) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp, 0, List.of(keywords));
    }

    @Test
    void chargeAttacksImmediately() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard charger = minion("骑兵", 3, 2, Keyword.CHARGE);
        self.getHand().add(charger);
        List<String> logs = new ArrayList<>();

        assertTrue(engine.playMinion(self, charger, logs::add));
        assertFalse(charger.isSummoningSickness(), "冲锋上场即解除召唤失调");
        assertTrue(logs.stream().anyMatch(l -> l.contains("冲锋")));
        assertTrue(engine.attack(self, foe, charger, null, logs::add));
        assertEquals(PlayerState.START_LIFE - 3, foe.getLifePoints());
    }

    @Test
    void tauntMustBeAttackedFirst() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("打手", 3, 3);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        MinionCard wall = minion("城墙", 0, 5, Keyword.TAUNT);
        MinionCard bystander = minion("路人", 1, 1);
        foe.getField().addAll(List.of(wall, bystander));
        List<String> logs = new ArrayList<>();

        // 打脸与打路人都被拦，打城墙放行
        assertFalse(engine.attack(self, foe, attacker, null, logs::add));
        assertFalse(engine.attack(self, foe, attacker, bystander, logs::add));
        assertTrue(logs.get(logs.size() - 1).contains("嘲讽"));
        assertTrue(engine.attack(self, foe, attacker, wall, logs::add));

        // 合法动作表里只有嘲讽目标（AI 自动遵守，无需改 AI）
        PlayerState self2 = playerOf(new ArrayList<>());
        MinionCard attacker2 = minion("打手", 3, 3);
        attacker2.setSummoningSickness(false);
        self2.getField().add(attacker2);
        List<ActionValidator.Move> moves = ActionValidator.legalActions(self2, foe);
        assertEquals(1, moves.size());
        assertEquals(wall, moves.get(0).target());
    }

    @Test
    void battlecryProbeFiresOnSummon() {
        TriggerSystem.register(new Trigger() {
            @Override
            public TriggerSystem.TriggerPoint point() {
                return TriggerSystem.TriggerPoint.ON_SUMMON;
            }

            @Override
            public boolean appliesTo(GameContext ctx) {
                return ctx.subject() != null
                        && ctx.subject().hasKeyword(Keyword.BATTLECRY);
            }

            @Override
            public List<GameEvent> apply(GameContext ctx) {
                ctx.self().heal(2);
                return List.of(GameEvent.of(GameEvent.Type.SPELL, "战吼：回复 2 点"));
            }
        });
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        self.damage(5);
        MinionCard herald = minion("传令官", 2, 2, Keyword.BATTLECRY);
        self.getHand().add(herald);
        List<String> logs = new ArrayList<>();

        assertTrue(engine.playMinion(self, herald, logs::add));
        assertEquals(PlayerState.START_LIFE - 3, self.getLifePoints(), "战吼回 2 血");
        assertTrue(logs.contains("战吼：回复 2 点"));
    }

    @Test
    void deathrattleProbeFiresOnDeath() {
        TriggerSystem.register(new Trigger() {
            @Override
            public TriggerSystem.TriggerPoint point() {
                return TriggerSystem.TriggerPoint.ON_DEATH;
            }

            @Override
            public boolean appliesTo(GameContext ctx) {
                return ctx.subject() != null
                        && ctx.subject().hasKeyword(Keyword.DEATHRATTLE);
            }

            @Override
            public List<GameEvent> apply(GameContext ctx) {
                return List.of(GameEvent.of(GameEvent.Type.SPELL, "亡语：触发"));
            }
        });
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("攻", 5, 5);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        MinionCard egg = minion("蛋", 0, 1, Keyword.DEATHRATTLE);
        foe.getField().add(egg);
        List<String> logs = new ArrayList<>();

        engine.attack(self, foe, attacker, egg, logs::add);

        assertTrue(foe.getField().isEmpty());
        assertTrue(logs.contains("亡语：触发"));
    }

    @Test
    void keywordsWithoutMechanicsChangeNothing() {
        // 战吼/亡语随从在无注册触发器时与白板一致（管线直通）
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard plain = minion("白板", 2, 2, Keyword.BATTLECRY, Keyword.DEATHRATTLE);
        self.getHand().add(plain);
        List<String> logs = new ArrayList<>();

        assertTrue(engine.playMinion(self, plain, logs::add));
        assertTrue(plain.isSummoningSickness(), "无冲锋仍有召唤失调");
    }

    /**
     * 防“卡住”回归：打脸被嘲讽拦下后，本回合出手权必须保留，
     * 且合法动作表仍能给出嘲讽目标（自动对局靠它推进，不会原地打转）。
     */
    @Test
    void rejectedFaceAttackKeepsActionAndTauntMoveRemains() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("打手", 3, 3);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        MinionCard wall = minion("城墙", 0, 5, Keyword.TAUNT);
        foe.getField().add(wall);
        List<String> logs = new ArrayList<>();

        assertFalse(engine.attack(self, foe, attacker, null, logs::add));
        assertFalse(attacker.isAttackedThisTurn(), "被拦下不消耗出手权");

        List<ActionValidator.Move> moves = ActionValidator.legalActions(self, foe);
        assertEquals(1, moves.size());
        assertEquals(wall, moves.get(0).target());

        // 顺着合法动作打出去，终局能推进
        assertTrue(engine.attack(self, foe, moves.get(0).attacker(), moves.get(0).target(),
                logs::add));
        assertTrue(attacker.isAttackedThisTurn());
    }

    @Test
    void divineShieldBlocksFirstHitOnly() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("打手", 3, 3);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        MinionCard guarded = minion("圣盾兵", 2, 2, Keyword.DIVINE_SHIELD);
        foe.getField().add(guarded);
        List<String> logs = new ArrayList<>();

        assertTrue(guarded.hasDivineShield());
        assertTrue(engine.attack(self, foe, attacker, guarded, logs::add));
        assertFalse(guarded.hasDivineShield(), "第一下破盾");
        assertTrue(foe.getField().contains(guarded), "挡下后存活");
        assertTrue(logs.stream().anyMatch(l -> l.contains("圣盾")));

        // 第二下：盾没了，正常受伤（2 血吃 3 点阵亡）
        MinionCard finisher = minion("补刀", 3, 3);
        finisher.setSummoningSickness(false);
        self.getField().add(finisher);
        assertTrue(engine.attack(self, foe, finisher, guarded, logs::add));
        assertFalse(foe.getField().contains(guarded));
    }

    @Test
    void windfuryAttacksTwice() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard fury = minion("旋风", 2, 4, Keyword.WINDFURY);
        fury.setSummoningSickness(false);
        self.getField().add(fury);
        List<String> logs = new ArrayList<>();

        assertTrue(engine.attack(self, foe, fury, null, logs::add));
        assertTrue(ActionValidator.isReadyToAttack(fury), "风怒出手 1 次后还能再出手");
        assertTrue(engine.attack(self, foe, fury, null, logs::add));
        assertFalse(ActionValidator.isReadyToAttack(fury), "第 2 次后耗尽");
        assertEquals(PlayerState.START_LIFE - 4, foe.getLifePoints());
    }

    @Test
    void poisonousDestroysRegardlessOfHealth() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard snake = minion("毒蛇", 1, 1, Keyword.POISONOUS);
        snake.setSummoningSickness(false);
        self.getField().add(snake);
        MinionCard giant = minion("巨人", 6, 9);
        foe.getField().add(giant);
        List<String> logs = new ArrayList<>();

        assertTrue(engine.attack(self, foe, snake, giant, logs::add));
        assertFalse(foe.getField().contains(giant), "1 攻剧毒咬死 9 血巨人");
        assertTrue(logs.stream().anyMatch(l -> l.contains("剧毒")));
    }

    @Test
    void shieldBlocksPoison() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard snake = minion("毒蛇", 2, 2, Keyword.POISONOUS);
        snake.setSummoningSickness(false);
        self.getField().add(snake);
        MinionCard guarded = minion("圣盾兵", 2, 5, Keyword.DIVINE_SHIELD);
        foe.getField().add(guarded);
        List<String> logs = new ArrayList<>();

        assertTrue(engine.attack(self, foe, snake, guarded, logs::add));
        assertTrue(foe.getField().contains(guarded), "圣盾挡下，剧毒不触发");
        assertFalse(guarded.hasDivineShield());
    }

    @Test
    void chargeWindfuryActsImmediatelyAndTwice() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard storm = minion("风暴", 2, 2, Keyword.CHARGE, Keyword.WINDFURY);
        self.getHand().add(storm);
        List<String> logs = new ArrayList<>();

        assertTrue(engine.playMinion(self, storm, logs::add));
        assertTrue(engine.attack(self, foe, storm, null, logs::add), "冲锋上场即打");
        assertTrue(engine.attack(self, foe, storm, null, logs::add), "风怒同回合再打");
        assertEquals(PlayerState.START_LIFE - 4, foe.getLifePoints());
    }

    @Test
    void tauntShieldWallMustBeBrokenFirst() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("打手", 5, 5);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        MinionCard wall = minion("圣盾墙", 0, 6, Keyword.TAUNT, Keyword.DIVINE_SHIELD);
        MinionCard backline = minion("后排", 3, 3);
        foe.getField().addAll(List.of(wall, backline));
        List<String> logs = new ArrayList<>();

        // 第一刀破盾（墙还在，嘲讽仍在）
        assertTrue(engine.attack(self, foe, attacker, wall, logs::add));
        assertTrue(foe.getField().contains(wall));
        assertFalse(engine.attack(self, foe, attacker, backline, logs::add),
                "墙没倒，后排碰不得");
    }
}
