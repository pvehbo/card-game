package org.example.card.ai;

import org.example.card.engine.GameEngine;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-4 三档 AI：随机可复现、困难见斩杀打脸、有嘲讽回落解嘲讽。 */
class AiDifficultyTest {

    private static PlayerState playerOf(String name) {
        return new PlayerState(name, new Deck(new ArrayList<>()));
    }

    private static MinionCard minion(String name, int atk, int hp, Keyword... keywords) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp, 0, List.of(keywords));
    }

    private static GameView view(PlayerState self, PlayerState foe) {
        return GameView.snapshot(self, foe);
    }

    private static List<Target> targets(PlayerState foe) {
        return foe.getField().stream()
                .map(m -> new Target(m, GameEngine.currentHealth(foe, m)))
                .toList();
    }

    @Test
    void randomAiIsDeterministicWithSeed() {
        PlayerState self = playerOf("AI");
        PlayerState foe = playerOf("你");
        MinionCard a = minion("甲", 2, 2);
        MinionCard b = minion("乙", 5, 5);
        foe.getField().addAll(List.of(a, b));
        GameView view = view(self, foe);
        List<Target> targets = targets(foe);

        RandomAi first = new RandomAi(new Random(42));
        RandomAi second = new RandomAi(new Random(42));
        assertEquals(first.chooseAttackTarget(view, targets), second.chooseAttackTarget(view, targets));

        // 选的必须是合法目标或打脸
        Optional<MinionCard> pick = new RandomAi(new Random(7))
                .chooseAttackTarget(view, targets);
        assertTrue(pick.isEmpty() || pick.get() == a || pick.get() == b);
        assertTrue(new RandomAi().chooseAttackTarget(view, List.of()).isEmpty());
    }

    @Test
    void hardAiGoesFaceWhenLethal() {
        HardAi hard = new HardAi();
        PlayerState self = playerOf("AI");
        PlayerState foe = playerOf("你");
        foe.damage(PlayerState.START_LIFE - 6); // 剩 6 血
        MinionCard killer = minion("杀手", 7, 7);
        killer.setSummoningSickness(false);
        self.getField().add(killer);
        MinionCard chump = minion("杂兵", 1, 4);
        foe.getField().add(chump);

        // 7 ≥ 6 斩杀：放着杂兵不管，直接打脸
        assertTrue(hard.chooseAttackTarget(view(self, foe), targets(foe)).isEmpty());
    }

    @Test
    void hardAiClearsTauntWhenNoLethal() {
        HardAi hard = new HardAi();
        PlayerState self = playerOf("AI");
        PlayerState foe = playerOf("你"); // 满血，无斩杀
        MinionCard killer = minion("杀手", 3, 3);
        killer.setSummoningSickness(false);
        self.getField().add(killer);
        MinionCard wall = minion("城墙", 0, 5, Keyword.TAUNT);
        foe.getField().add(wall);

        // 有嘲讽：回落贪心，打血最少的嘲讽（这里只有城墙）
        assertEquals(wall, hard.chooseAttackTarget(view(self, foe), targets(foe)).orElseThrow());
    }

    @Test
    void hardAiFallsBackToGreedyWithoutLethal() {
        HardAi hard = new HardAi();
        SimpleAi greedy = new SimpleAi();
        PlayerState self = playerOf("AI");
        PlayerState foe = playerOf("你"); // 满血
        MinionCard killer = minion("杀手", 3, 3);
        killer.setSummoningSickness(false);
        self.getField().add(killer);
        MinionCard weak = minion("脆皮", 1, 2);
        MinionCard tank = minion("铁壁", 0, 9);
        foe.getField().addAll(List.of(weak, tank));

        // 无斩杀无嘲讽：与贪心一致，打最低血
        GameView view = view(self, foe);
        assertEquals(greedy.chooseAttackTarget(view, targets(foe)),
                hard.chooseAttackTarget(view, targets(foe)));
        assertEquals(weak, hard.chooseAttackTarget(view, targets(foe)).orElseThrow());
    }

    @Test
    void engineAcceptsStrategySwap() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf("AI");
        PlayerState foe = playerOf("你");
        foe.damage(PlayerState.START_LIFE - 6);
        MinionCard killer = minion("杀手", 7, 7);
        killer.setSummoningSickness(false);
        self.getField().add(killer);
        foe.getField().add(minion("杂兵", 1, 4));

        // 普通：打杂兵；切困难：打脸
        assertTrue(engine.chooseAiTarget(self, foe).isPresent());
        engine.setAiStrategy(new HardAi());
        assertTrue(engine.chooseAiTarget(self, foe).isEmpty());
    }
}
