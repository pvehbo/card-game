package org.example.card.engine;

import org.example.card.ai.SimpleAi;
import org.example.card.data.CardDatabase;
import org.example.card.data.Ruleset;
import org.example.card.model.Card;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 批② S2 四十分钟大单：Ruleset → 引擎数值接线（生命/法力/发牌/后手补偿）端到端。 */
class RulesetSessionTest {

    /** 标准牌堆前 30 组成合法牌堆（第一章固定明文）。 */
    private static List<Card> thirty() {
        return new ArrayList<>(CardDatabase.standardDeck().subList(0, 30));
    }

    @Test
    void customRulesetShapesLifeManaAndOpeningHand() {
        Ruleset ruleset = new Ruleset("旧约", 12, 8, 1, 2);
        GameSession session = GameSession.newPveBattle(new Random(7), thirty(), ruleset);

        assertEquals(12, session.getPlayer().getLifePoints(), "heroLife 注入玩家");
        assertEquals(12, session.getAi().getLifePoints(), "heroLife 注入 AI");
        assertEquals(1, session.getPlayer().getHand().size(), "openingHand 决定开局发牌数");
        assertEquals(1, session.getAi().getHand().size());
        assertTrue(session.ruleset() == ruleset, "会话持有开局规则集");

        PlayerState you = session.getPlayer();
        for (int i = 0; i < 12; i++) {
            you.resetTurnFlags();
        }
        assertEquals(8, you.getMaxMana(), "法力上限封顶停在 maxManaCap");
    }

    @Test
    void secondMoveBonusReadsRuleset() {
        Ruleset ruleset = new Ruleset("三张", 20, 10, 3, 3);
        GameSession session = GameSession.newPveBattle(new Random(11), thirty(), ruleset);
        TurnController controller = new TurnController(new GameEngine(new SimpleAi()));
        List<String> logs = new ArrayList<>();
        int deckBefore = session.getPlayer().getDeck().size();

        controller.openGame(session, false, logs::add, false);

        assertEquals(6, session.getPlayer().getHand().size(), "开局 3 + 后手补偿 3");
        assertEquals(deckBefore - 3, session.getPlayer().getDeck().size());
        assertTrue(logs.contains("后手补偿：后手多抽 3 张"), logs.toString());
    }

    @Test
    void zeroBonusIsSilent() {
        Ruleset ruleset = new Ruleset("无补偿", 20, 10, 3, 0);
        GameSession session = GameSession.newPveBattle(new Random(13), thirty(), ruleset);
        TurnController controller = new TurnController(new GameEngine(new SimpleAi()));
        List<String> logs = new ArrayList<>();

        controller.openGame(session, false, logs::add, false);

        assertTrue(logs.isEmpty(), "0 张后手补偿既不抽牌也不记日志：" + logs);
        assertEquals(3, session.getPlayer().getHand().size());
    }

    @Test
    void defaultAndRestoreFallBackToClassic() {
        GameSession session = GameSession.newPveBattle(new Random(9));
        assertSame(Ruleset.CLASSIC, session.ruleset(), "老入口默认走经典规则集");
        assertEquals(3, session.getPlayer().getHand().size());

        GameSession restored = GameSession.restore(session.getPlayer(), session.getAi(), true,
                new Ruleset("存档档", 15, 9, 3, 1));
        assertEquals(15, restored.ruleset().heroLife(), "存档可保有自己的规则集");

        GameSession fallback = GameSession.restore(session.getPlayer(), session.getAi(), false);
        assertSame(Ruleset.CLASSIC, fallback.ruleset(), "老 restore 签名回退经典");
        assertFalse(fallback.isYourTurn());
    }
}