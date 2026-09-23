package org.example.card.engine;

import org.example.card.ai.SimpleAi;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GameSession + TurnController 单测：开局发牌、先后手、代数作废、回合交接。 */
class SessionTest {

    private static final Consumer<String> QUIET = msg -> {
    };

    @Test
    void newBattleDealsThreeCardsEach() {
        GameSession session = GameSession.newPveBattle(new Random(42));

        assertEquals(GameSession.Mode.PVE, session.getMode());
        assertEquals(3, session.getPlayer().getHand().size());
        assertEquals(3, session.getAi().getHand().size());
        assertEquals(17, session.getPlayer().getDeck().size(), "20 张标准牌堆摸 3 剩 17");
        assertEquals(17, session.getAi().getDeck().size());
        assertEquals(PlayerState.START_LIFE, session.getPlayer().getLifePoints());
        assertEquals(0, session.getGeneration());
        assertFalse(session.gameOver());
    }

    @Test
    void sameSeedDealsSameHands() {
        GameSession a = GameSession.newPveBattle(new Random(7));
        GameSession b = GameSession.newPveBattle(new Random(7));

        assertEquals(handIds(a), handIds(b), "同一种子开局手牌一致，可复现对局");
    }

    private static List<String> handIds(GameSession session) {
        return session.getPlayer().getHand().stream()
                .map(c -> c.getId()).toList();
    }

    @Test
    void generationInvalidatesStaleCallbacks() {
        GameSession session = GameSession.newPveBattle(new Random());
        assertEquals(0, session.getGeneration());
        session.nextGeneration();
        session.nextGeneration();
        assertEquals(2, session.getGeneration());
    }

    @Test
    void playerFirstOpensWithDraw() {
        GameSession session = GameSession.newPveBattle(new Random(1));
        TurnController controller = new TurnController(new GameEngine(new SimpleAi()));

        controller.openGame(session, true, QUIET, false);

        assertTrue(session.isYourTurn());
        assertEquals(4, session.getPlayer().getHand().size(), "玩家先手开局抽 1 张");
        assertEquals(3, session.getAi().getHand().size());
    }

    @Test
    void aiFirstOpensWithoutDraw() {
        GameSession session = GameSession.newPveBattle(new Random(1));
        TurnController controller = new TurnController(new GameEngine(new SimpleAi()));

        controller.openGame(session, false, QUIET, false);

        assertFalse(session.isYourTurn());
        assertEquals(3, session.getPlayer().getHand().size(), "AI 先手时玩家还没抽");
    }

    @Test
    void deferredFirstTurnKeepsDemoBoard() {
        GameSession session = GameSession.newPveBattle(new Random(1));
        TurnController controller = new TurnController(new GameEngine(new SimpleAi()));

        // 截图/演示模式：只定归属，不抽牌不计回合（原 startNewGame 行为）
        controller.openGame(session, true, QUIET, true);

        assertTrue(session.isYourTurn());
        assertEquals(3, session.getPlayer().getHand().size());
    }

    @Test
    void turnsPassBetweenSides() {
        GameSession session = GameSession.newPveBattle(new Random(1));
        GameEngine engine = new GameEngine(new SimpleAi());
        TurnController controller = new TurnController(engine);
        controller.openGame(session, true, QUIET, false);

        controller.closePlayerTurn(session, QUIET);
        assertFalse(session.isYourTurn(), "结束回合后交给 AI");

        controller.openPlayerTurn(session, QUIET);
        assertTrue(session.isYourTurn(), "AI 交棒后回到玩家");
        assertEquals(2, engine.getTurn(), "两个玩家回合各计 1");
    }

    @Test
    void gameOverMirrorsDefeat() {
        GameSession session = GameSession.newPveBattle(new Random());
        assertFalse(session.gameOver());
        session.getAi().damage(PlayerState.START_LIFE);
        assertTrue(session.gameOver());
    }

    @Test
    void fullGameThroughSessionEnds() {
        // 整局经会话对象打完：双方状态机与原来直调一致
        GameSession session = GameSession.newPveBattle(new Random(3));
        GameEngine engine = new GameEngine(new SimpleAi());
        TurnController controller = new TurnController(engine);
        controller.openGame(session, true, QUIET, false);

        List<String> logs = new ArrayList<>();
        for (int i = 0; i < 30 && !session.gameOver(); i++) {
            if (session.isYourTurn()) {
                // 玩家：有牌就打，能打就打脸
                var hand = new ArrayList<>(session.getPlayer().getHand());
                for (var c : hand) {
                    if (c instanceof org.example.card.model.MinionCard m) {
                        engine.playMinion(session.getPlayer(), m, logs::add);
                    } else if (c instanceof org.example.card.model.SpellCard s) {
                        engine.playSpell(session.getPlayer(), session.getAi(), s, logs::add);
                    } else if (c instanceof org.example.card.model.PetCard p) {
                        engine.playPet(session.getPlayer(), p, logs::add);
                    }
                }
                for (var m : new ArrayList<>(session.getPlayer().getField())) {
                    engine.attack(session.getPlayer(), session.getAi(), m, null, logs::add);
                }
                controller.closePlayerTurn(session, logs::add);
            } else {
                engine.playTurn(session.getAi(), session.getPlayer(), logs::add);
                if (!session.gameOver()) {
                    controller.openPlayerTurn(session, logs::add);
                }
            }
        }
        assertTrue(session.gameOver(), "30 回合内应分出胜负");
    }
}
