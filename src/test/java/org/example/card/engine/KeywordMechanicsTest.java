package org.example.card.engine;

import org.example.card.ai.SimpleAi;
import org.example.card.effect.GameContext;
import org.example.card.effect.KeywordTriggers;
import org.example.card.effect.Trigger;
import org.example.card.effect.TriggerSystem;
import org.example.card.event.GameEvent;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
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
    void noKeywordMinionUnaffected() {
        // 没带战吼/亡语的随从不受内置触发器影响（注册表里挂着也只是零开销直通）
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(List.of(minion("垫", 1, 1)));
        MinionCard plain = minion("白板", 2, 2);
        self.getHand().add(plain);
        List<GameEvent.Type> types = new ArrayList<>();
        engine.eventBus().onAny(e -> types.add(e.type()));

        assertTrue(engine.playMinion(self, plain, new ArrayList<String>()::add));

        assertEquals(1, self.getDeck().size(), "白板不上场抽牌");
        assertFalse(types.contains(GameEvent.Type.DRAW));
        assertTrue(plain.isSummoningSickness(), "无冲锋仍有召唤失调");
    }

    // ============ R-12：战吼 / 亡语 从占位关键词变成真机制 ============

    @Test
    void battlecryDrawsACard() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(List.of(minion("垫", 1, 1)));
        MinionCard herald = minion("传令官", 2, 2, Keyword.BATTLECRY);
        self.getHand().add(herald);
        List<GameEvent.Type> types = new ArrayList<>();
        engine.eventBus().onAny(e -> types.add(e.type()));

        assertTrue(engine.playMinion(self, herald, new ArrayList<String>()::add));

        assertEquals(1, self.getHand().size(), "战吼抽 1 张进手牌");
        assertEquals(0, self.getDeck().size());
        assertTrue(types.contains(GameEvent.Type.DRAW), "战吼走统一抽牌管线，发 DRAW 事件");
    }

    @Test
    void battlecryOnEmptyDeckIsSilent() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        MinionCard herald = minion("传令官", 2, 2, Keyword.BATTLECRY);
        self.getHand().add(herald);
        List<GameEvent.Type> types = new ArrayList<>();
        engine.eventBus().onAny(e -> types.add(e.type()));

        assertTrue(engine.playMinion(self, herald, new ArrayList<String>()::add));

        assertTrue(self.getHand().isEmpty(), "空牌堆抽不到牌");
        assertFalse(types.contains(GameEvent.Type.DRAW), "空牌堆不抽牌也不报错");
    }

    /**
     * 亡语归属（R-12 关键回归）：敌方随从阵亡时，亡语必须打**随从主人的对手**，
     * 而不是打到主人自己身上——即上下文里的 (self, foe) 必须按「阵亡随从的归属」构造。
     */
    @Test
    void deathrattleOfFoeMinionHitsOwnerOpponent() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("攻", 5, 5);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        MinionCard plagued = minion("瘟疫", 1, 1, Keyword.DEATHRATTLE);
        foe.getField().add(plagued);

        engine.attack(self, foe, attacker, plagued, new ArrayList<String>()::add);

        assertEquals(PlayerState.START_LIFE, foe.getLifePoints(), "亡语不该打到随从的主人自己");
        assertEquals(PlayerState.START_LIFE - KeywordTriggers.DEATHRATTLE_DAMAGE,
                self.getLifePoints(), "亡语打的是主人的对手");
    }

    /** 己方亡语随从阵亡时，打的是敌方英雄。 */
    @Test
    void deathrattleOfOwnMinionHitsFoeHero() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard plagued = minion("我的瘟疫", 1, 1, Keyword.DEATHRATTLE);
        plagued.setSummoningSickness(false);
        self.getField().add(plagued);
        MinionCard wall = minion("大块头", 9, 9);
        foe.getField().add(wall);

        engine.attack(self, foe, plagued, wall, new ArrayList<String>()::add);

        assertTrue(self.getField().isEmpty(), "1/1 撞 9/9 必死");
        assertEquals(PlayerState.START_LIFE - KeywordTriggers.DEATHRATTLE_DAMAGE,
                foe.getLifePoints(), "亡语打敌方英雄");
        assertEquals(PlayerState.START_LIFE, self.getLifePoints(), "不该打到自己");
    }

    /** 亡语造成的伤害可以终结对局（GAME_OVER 由引擎在阵亡触发之后判定）。 */
    @Test
    void deathrattleCanFinishTheGame() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        foe.damage(PlayerState.START_LIFE - KeywordTriggers.DEATHRATTLE_DAMAGE);
        MinionCard plagued = minion("我的瘟疫", 1, 1, Keyword.DEATHRATTLE);
        plagued.setSummoningSickness(false);
        self.getField().add(plagued);
        MinionCard wall = minion("大块头", 9, 9);
        foe.getField().add(wall);
        List<GameEvent.Type> types = new ArrayList<>();
        engine.eventBus().onAny(e -> types.add(e.type()));

        engine.attack(self, foe, plagued, wall, new ArrayList<String>()::add);

        assertTrue(foe.isDefeated());
        assertTrue(types.contains(GameEvent.Type.GAME_OVER), "亡语斩杀要出 GAME_OVER");
    }

    /** reset() 只清额外注册的探针，内置战吼/亡语必须仍在册。 */
    @Test
    void resetKeepsBuiltInTriggers() {
        TriggerSystem.reset();
        assertTrue(TriggerSystem.registered() >= 2, "reset 不该清掉内置关键词触发器");
    }

    /**
     * R-17：互撞分支与打脸同口径——负攻按 0 结算，且 **0 伤害不该击破圣盾**。
     * （修前 {@code consumeDivineShield()} 无条件调用，0/负伤也会把盾打掉。）
     */
    @Test
    void zeroDamageCollisionDoesNotBreakDivineShield() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard weak = minion("虚弱", 1, 5);
        weak.setSummoningSickness(false);
        self.getField().add(weak);
        MinionCard shielded = minion("银甲", 2, 3, Keyword.DIVINE_SHIELD);
        foe.getField().add(shielded);
        // 己方负攻光环：effectiveAttack(weak) = 1 - 3 = -2 → 按 0 结算
        self.getPets().add(new PetCard("t-debuff", "衰弱图腾", "全员-3 攻", -3, 0, 0));

        engine.attack(self, foe, weak, shielded, new ArrayList<String>()::add);

        assertTrue(shielded.hasDivineShield(), "0 伤害不该击破圣盾");
        assertEquals(0, shielded.getDamageTaken(), "0 伤害不该留下伤");
        assertEquals(2, weak.getDamageTaken(), "反击照常结算");
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
