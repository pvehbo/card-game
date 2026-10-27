package org.example.card.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.example.card.model.MinionCard;
import org.junit.jupiter.api.Test;

/** 节奏策略：能便宜解就解，解不动就打脸；嘲讽优先拆。 */
class TempoAiTest {

    private GameView view(boolean taunt) {
        return new GameView(20, 5, 5, 20,
                List.of(new GameView.MinionInfo("小怪", 1, 2, taunt),
                        new GameView.MinionInfo("大怪", 6, 6, false)),
                List.of(new GameView.MinionInfo("我", 4, 4, false)));
    }

    @Test
    void takesProfitableTrade() {
        MinionCard small = new MinionCard("s", "小怪", "", 1, 2);
        MinionCard big = new MinionCard("b", "大怪", "", 6, 6);
        var chosen = new TempoAi().chooseAttackTarget(view(false),
                List.of(new Target(small, 2), new Target(big, 6)));
        assertEquals(small, chosen.orElseThrow(), "能击杀且不被反杀的小怪应优先解掉");
    }

    @Test
    void goesFaceWhenNoProfitableTrade() {
        MinionCard big = new MinionCard("b", "大怪", "", 6, 6);
        var chosen = new TempoAi().chooseAttackTarget(view(false), List.of(new Target(big, 6)));
        assertTrue(chosen.isEmpty(), "解不动的大怪不硬换，打脸");
    }

    @Test
    void clearsTauntFirst() {
        MinionCard wall = new MinionCard("w", "墙", "", 2, 5);
        var chosen = new TempoAi().chooseAttackTarget(view(true), List.of(new Target(wall, 5)));
        assertEquals(wall, chosen.orElseThrow());
    }

    @Test
    void usesActualAttackerInsteadOfBoardMaximum() {
        MinionCard target = new MinionCard("t", "目标", "", 1, 2);
        MinionCard weak = new MinionCard("w", "弱兵", "", 1, 1);
        var chosen = new TempoAi().chooseAttackTarget(view(false), weak,
                List.of(new Target(target, 2)));
        assertTrue(chosen.isEmpty(), "1 攻弱兵杀不死 2 血目标，应打脸而不是送死");
    }
}
