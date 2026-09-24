package org.example.card.data;

import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-3 数值体检：标准牌堆零告警；故意超标卡必被揪出。 */
class BalanceCheckTest {

    @Test
    void standardDeckPasses() {
        List<String> warnings = BalanceCheck.check(CardDatabase.standardDeck());
        assertEquals(List.of(), warnings, "标准牌堆应零告警：" + warnings);
    }

    @Test
    void brokenCardsAreFlagged() {
        // 9 费 1/1 白板：身材 2 vs 标准 20，差 18
        Card weak = new MinionCard("x-weak", "弱鸡", "垫", 1, 1, 9);
        // 1 费打 10：伤害 10 vs 上限 3
        Card nuke = new org.example.card.model.SpellCard(
                "x-nuke", "核弹", "垫", org.example.card.model.SpellCard.Kind.DAMAGE, 10, 1);
        List<String> warnings = BalanceCheck.check(List.of(weak, nuke));
        assertEquals(2, warnings.size(), warnings.toString());
    }

    @Test
    void zeroCostBasicsAreExempt() {
        // 0 费 6/6（祖传雷霆巨人）：免检，不告警
        Card basic = new MinionCard("m5", "雷霆巨人", "高攻终结", 6, 6, 0);
        assertTrue(BalanceCheck.check(List.of(basic)).isEmpty());
    }
}
