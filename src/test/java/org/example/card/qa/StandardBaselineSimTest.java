package org.example.card.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 模拟器结构测试（T-B5）：只验证口径、配对数量与确定性，不把胜率当断言。
 */
class StandardBaselineSimTest {

    @Test
    void matchupCountsAndSeatPairsAreComplete() {
        StandardBaselineSim.Matchup m = StandardBaselineSim.runMatchup("A", "A", 3, 12, "simple");
        assertEquals(6, m.games(), "3 对换边种子 = 6 局");
        assertEquals(6, m.aWins() + m.bWins() + m.draws() + m.overs(),
                "每局必须归入胜/负/平/超限之一");
        assertTrue(m.medianRounds() >= 1 && m.medianRounds() <= 12, "局长应在 1..上限 内");
    }

    @Test
    void sameSeedsProduceSameMatchup() {
        StandardBaselineSim.Matchup first = StandardBaselineSim.runMatchup("A", "M", 3, 12, "simple");
        StandardBaselineSim.Matchup again = StandardBaselineSim.runMatchup("A", "M", 3, 12, "simple");
        assertEquals(first, again, "同种子同策略应完全可复现");
    }

    @Test
    void allSixMatchupsAreProduced() {
        var all = StandardBaselineSim.runAll(1, 10, "simple");
        assertEquals(6, all.size(), "3 镜像 + 3 交叉");
    }
}
