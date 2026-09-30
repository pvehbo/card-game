package org.example.card.engine;

import org.example.card.ai.SimpleAi;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-后手补偿：后手多抽 1 张，满手烧牌、空堆不炸。 */
class SecondMoveBonusTest {

    @Test
    void bonusDrawsOneCard() {
        GameSession session = GameSession.newPveBattle(new Random(9));
        GameEngine engine = new GameEngine(new SimpleAi());
        List<String> logs = new ArrayList<>();
        int before = session.getAi().getHand().size();
        int deckBefore = session.getAi().getDeck().size();

        engine.grantSecondMoveBonus(session.getAi(), logs::add);

        assertEquals(before + 1, session.getAi().getHand().size());
        assertEquals(deckBefore - 1, session.getAi().getDeck().size());
        assertTrue(logs.stream().anyMatch(m -> m.contains("后手补偿")));
    }

    @Test
    void bonusBurnsWhenHandFull() {
        GameSession session = GameSession.newPveBattle(new Random(9));
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState ai = session.getAi();
        while (ai.getHand().size() < PlayerState.MAX_HAND && !ai.getDeck().isEmpty()) {
            ai.getDeck().draw().ifPresent(ai.getHand()::add);
        }
        int gravesBefore = ai.getGraveyard().size();
        engine.grantSecondMoveBonus(ai, m -> {
        });
        assertEquals(PlayerState.MAX_HAND, ai.getHand().size(), "满手不超上限");
        assertEquals(gravesBefore + 1, ai.getGraveyard().size(), "多抽的烧进墓地");
    }

    @Test
    void bonusWithEmptyDeckIsNoop() {
        GameSession session = GameSession.newPveBattle(new Random(9));
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState ai = session.getAi();
        while (!ai.getDeck().isEmpty()) {
            ai.getDeck().draw();
        }
        int handBefore = ai.getHand().size();
        engine.grantSecondMoveBonus(ai, m -> {
        });
        assertEquals(handBefore, ai.getHand().size());
    }
}
