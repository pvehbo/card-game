package org.example.card.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 批② S2 二十分钟小单：规则集档位、取值域，CLASSIC 与 STANDARD 的差异承诺。 */
class RulesetTest {

    @Test
    void classicFixesLiveParameters() {
        assertEquals("经典", Ruleset.CLASSIC.name());
        assertEquals(20, Ruleset.CLASSIC.heroLife(), "PlayerState.START_LIFE 同源");
        assertEquals(10, Ruleset.CLASSIC.maxManaCap(), "PlayerState 旧封顶 10 同源");
        assertEquals(3, Ruleset.CLASSIC.openingHand(), "旧 OPENING_HAND 同源");
        assertEquals(1, Ruleset.CLASSIC.secondMoveBonusDraws(), "后手补偿（B-后手补偿）张数同源");
    }

    @Test
    void standardIsSameValuesUntilBatch3() {
        // 标准规则集是同名安装位：批③把差异落在卡池数据层后才翻值，这里先与经典同值
        assertEquals("标准", Ruleset.STANDARD.name());
        assertEquals(Ruleset.CLASSIC.heroLife(), Ruleset.STANDARD.heroLife());
        assertEquals(Ruleset.CLASSIC.maxManaCap(), Ruleset.STANDARD.maxManaCap());
        assertEquals(Ruleset.CLASSIC.openingHand(), Ruleset.STANDARD.openingHand());
        assertEquals(Ruleset.CLASSIC.secondMoveBonusDraws(), Ruleset.STANDARD.secondMoveBonusDraws());
    }

    @Test
    void validatesRanges() {
        assertThrows(IllegalArgumentException.class, () -> r(0, 10, 3, 1), "heroLife ≥ 1");
        assertThrows(IllegalArgumentException.class, () -> r(20, 0, 3, 1), "maxManaPitch ≥ 1");
        assertThrows(IllegalArgumentException.class, () -> r(20, 21, 3, 1), "maxManaPitch ≤ 20");
        assertThrows(IllegalArgumentException.class, () -> r(20, 10, -1, 1), "openingHand ≥ 0");
        assertThrows(IllegalArgumentException.class, () -> r(20, 10, 11, 1), "openingHand ≤ 10");
        assertThrows(IllegalArgumentException.class, () -> r(20, 10, 3, -1), "bonus ≥ 0");
        assertThrows(IllegalArgumentException.class, () -> r(20, 10, 3, 4), "bonus ≤ 3");
        assertThrows(IllegalArgumentException.class, () -> new Ruleset("  ", 20, 10, 3, 1), "名称必填");
    }

    @Test
    void boundaryValuesAreLegal() {
        Ruleset lo = new Ruleset("下限", 1, 1, 0, 0);
        Ruleset hi = new Ruleset("上限", 3, 20, 10, 3);
        assertEquals(1, lo.heroLife());
        assertEquals(20, hi.maxManaCap());
        assertEquals(0, lo.openingHand(), "开局不发牌也是合法档位");
        assertEquals(3, hi.secondMoveBonusDraws(), "后手补偿最多 3 张");
    }

    private static Ruleset r(int heroLife, int maxManaCap, int openingHand, int bonus) {
        return new Ruleset("rfc", heroLife, maxManaCap, openingHand, bonus);
    }
}