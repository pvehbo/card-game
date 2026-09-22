package org.example.card.engine;

import org.example.card.ai.SimpleAi;
import org.example.card.event.GameEvent;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 回合的「逐步驱动」API 单测。
 *
 * 界面为了把 AI 回合一个动作一个动作地演出来（而不是一口气跑完导致音效同帧炸响），
 * 走的是 beginAiTurn → aiSummon/aiSpell/aiPet → aiStrike → endAiTurn 这条路径。
 * 这里锁住两件事：逐步驱动与 playTurn 结果一致，以及逐步 API 的边界行为。
 */
class AiStepTurnTest {

    private static PlayerState playerOf(String name, List<Card> deck) {
        return new PlayerState(name, new Deck(new ArrayList<>(deck)));
    }

    private static MinionCard minion(String name, int atk, int hp) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp);
    }

    private static List<Card> deckOf() {
        return new ArrayList<>(List.of(
                minion("牌1", 2, 2),
                minion("牌2", 3, 3),
                new SpellCard("s1", "火球术", "打3", SpellCard.Kind.DAMAGE, 3)));
    }

    /** 一次逐步驱动的完整 AI 回合（与界面 planAiTurn 的脚本一致）。 */
    private static void playStepped(GameEngine engine, PlayerState ai, PlayerState foe, List<String> logs) {
        engine.beginAiTurn(ai, logs::add);
        engine.aiSummon(ai, logs::add);
        engine.aiSpell(ai, foe, logs::add);
        engine.aiPet(ai, logs::add);
        for (MinionCard attacker : engine.aiReadyAttackers(ai)) {
            if (foe.isDefeated()) {
                break;
            }
            engine.aiStrike(ai, foe, attacker, logs::add);
        }
        if (!foe.isDefeated()) {
            engine.endAiTurn(ai, foe, logs::add);
        }
    }

    @Test
    void steppedTurnMatchesOneShotTurn() {
        GameEngine oneShot = new GameEngine(new SimpleAi());
        PlayerState ai1 = playerOf("AI", deckOf());
        PlayerState foe1 = playerOf("你", deckOf());
        MinionCard m1 = minion("打手", 3, 4);
        m1.setSummoningSickness(false);
        ai1.getField().add(m1);
        foe1.getField().add(minion("守军", 2, 2));

        GameEngine stepped = new GameEngine(new SimpleAi());
        PlayerState ai2 = playerOf("AI", deckOf());
        PlayerState foe2 = playerOf("你", deckOf());
        MinionCard m2 = minion("打手", 3, 4);
        m2.setSummoningSickness(false);
        ai2.getField().add(m2);
        foe2.getField().add(minion("守军", 2, 2));

        oneShot.playTurn(ai1, foe1, new ArrayList<String>()::add);
        playStepped(stepped, ai2, foe2, new ArrayList<>());

        assertEquals(oneShot.getTurn(), stepped.getTurn());
        assertEquals(ai1.getLifePoints(), ai2.getLifePoints());
        assertEquals(foe1.getLifePoints(), foe2.getLifePoints());
        assertEquals(ai1.getField().size(), ai2.getField().size());
        assertEquals(foe1.getField().size(), foe2.getField().size());
        assertEquals(ai1.getHand().size(), ai2.getHand().size());
        assertEquals(ai1.getPets().size(), ai2.getPets().size());
    }

    /** 两条路径发布的事件类型序列也要一致（界面全靠事件驱动动效）。 */
    @Test
    void steppedTurnPublishesSameEventSequence() {
        GameEngine oneShot = new GameEngine(new SimpleAi());
        PlayerState ai1 = playerOf("AI", deckOf());
        PlayerState foe1 = playerOf("你", deckOf());
        MinionCard m1 = minion("打手", 3, 4);
        m1.setSummoningSickness(false);
        ai1.getField().add(m1);
        foe1.getField().add(minion("守军", 2, 2));

        GameEngine stepped = new GameEngine(new SimpleAi());
        PlayerState ai2 = playerOf("AI", deckOf());
        PlayerState foe2 = playerOf("你", deckOf());
        MinionCard m2 = minion("打手", 3, 4);
        m2.setSummoningSickness(false);
        ai2.getField().add(m2);
        foe2.getField().add(minion("守军", 2, 2));

        List<GameEvent.Type> oneShotTypes = new ArrayList<>();
        List<GameEvent.Type> steppedTypes = new ArrayList<>();
        oneShot.eventBus().onAny(e -> oneShotTypes.add(e.type()));
        stepped.eventBus().onAny(e -> steppedTypes.add(e.type()));

        oneShot.playTurn(ai1, foe1, new ArrayList<String>()::add);
        playStepped(stepped, ai2, foe2, new ArrayList<>());

        assertEquals(oneShotTypes, steppedTypes);
    }

    @Test
    void beginAiTurnResetsFlagsAndDrawsOneCard() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState ai = playerOf("AI", deckOf());
        PlayerState foe = playerOf("你", new ArrayList<>());
        ai.setMinionPlayed(true);
        ai.setSpellPlayed(true);
        ai.setPetPlayed(true);
        int handBefore = ai.getHand().size();

        List<GameEvent.Type> types = new ArrayList<>();
        engine.eventBus().onAny(e -> types.add(e.type()));

        engine.beginAiTurn(ai, new ArrayList<String>()::add);

        assertEquals(1, engine.getTurn());
        assertEquals(handBefore + 1, ai.getHand().size(), "起手抽 1 张");
        assertFalse(ai.isMinionPlayed(), "新回合重置出牌次数");
        assertFalse(ai.isSpellPlayed());
        assertFalse(ai.isPetPlayed());
        assertEquals(List.of(GameEvent.Type.TURN_START, GameEvent.Type.DRAW), types);
    }

    @Test
    void eachCardKindIsLimitedToOnePerTurn() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState ai = playerOf("AI", new ArrayList<>());
        PlayerState foe = playerOf("你", new ArrayList<>());
        ai.getHand().addAll(List.of(
                minion("A", 2, 2),
                minion("B", 5, 5),
                new SpellCard("s1", "火球术", "打3", SpellCard.Kind.DAMAGE, 3),
                new SpellCard("s2", "治疗之触", "回4", SpellCard.Kind.HEAL, 4),
                new PetCard("p1", "战鼓兽", "攻+1", 1, 0),
                new PetCard("p2", "石皮兽", "血+2", 0, 2)));
        List<String> logs = new ArrayList<>();

        engine.beginAiTurn(ai, logs::add);
        assertTrue(engine.aiSummon(ai, logs::add));
        assertFalse(engine.aiSummon(ai, logs::add), "同回合第二张随从应被拒绝");
        assertTrue(engine.aiSpell(ai, foe, logs::add));
        assertFalse(engine.aiSpell(ai, foe, logs::add), "同回合第二个法术应被拒绝");
        assertTrue(engine.aiPet(ai, logs::add));
        assertFalse(engine.aiPet(ai, logs::add), "同回合第二只宠物应被拒绝");

        assertEquals(1, ai.getField().size());
        assertEquals(1, ai.getPets().size());
        assertEquals(1, ai.getGraveyard().size(), "只有真正打出的那张法术进墓地");
    }

    @Test
    void readyAttackersExcludesSummoningSickMinions() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState ai = playerOf("AI", new ArrayList<>());
        MinionCard sick = minion("新兵", 2, 2);          // 默认召唤失调
        MinionCard ready = minion("老兵", 3, 3);
        ready.setSummoningSickness(false);
        ai.getField().add(sick);
        ai.getField().add(ready);

        assertEquals(List.of(ready), engine.aiReadyAttackers(ai));
        assertFalse(engine.aiStrike(ai, playerOf("你", new ArrayList<>()), sick,
                new ArrayList<String>()::add), "召唤失调的随从不能出手");
    }

    /** 界面要先知道"这只随从会打谁"才能把突刺动画朝向正确的目标。 */
    @Test
    void aiStrikeHonoursExplicitTarget() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState ai = playerOf("AI", new ArrayList<>());
        PlayerState foe = playerOf("你", new ArrayList<>());
        MinionCard attacker = minion("打手", 5, 5);
        attacker.setSummoningSickness(false);
        ai.getField().add(attacker);
        MinionCard weak = minion("脆皮", 1, 1);
        MinionCard tank = minion("铁壁", 0, 9);
        foe.getField().add(weak);
        foe.getField().add(tank);

        // 简单 AI 本能会挑血最低的脆皮，但界面显式指定铁壁
        assertEquals(weak, engine.chooseAiTarget(ai, foe).orElseThrow());
        assertTrue(engine.aiStrike(ai, foe, attacker, tank, new ArrayList<String>()::add));

        assertEquals(PlayerState.START_LIFE, foe.getLifePoints(), "只打随从，英雄不掉血");
        assertEquals(0, weak.getDamageTaken(), "没被指定的目标不该受伤");
        assertEquals(5, tank.getDamageTaken(), "显式指定的目标吃满 5 点伤害");
        assertTrue(foe.getField().contains(tank), "9 血坦克挨 5 点后仍在场");
    }
}
