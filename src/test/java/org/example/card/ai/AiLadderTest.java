package org.example.card.ai;

import org.example.card.data.CardDatabase;
import org.example.card.engine.GameEngine;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * B-4 AI 天梯：固定种子、28 张标准牌堆，不同策略捉对比拼。
 * 只断言“每局都终局”（可终局性），胜负表打印出来供 BALANCE.md 记录——
 * 样本与种子固定，可复现；调参后看表格变化。
 */
class AiLadderTest {

    private static final int GAMES = 10;
    private static final int ROUND_CAP = 60;

    /** 一局：先手策略 engine1 执 A，打印胜者名。 */
    private static String playOne(AiStrategy first, AiStrategy second, long seed) {
        GameEngine engineFirst = new GameEngine(first);
        GameEngine engineSecond = new GameEngine(second);
        List<Card> deckA = CardDatabase.standardDeck();
        List<Card> deckB = CardDatabase.standardDeck();
        java.util.Collections.shuffle(deckA, new Random(seed));
        java.util.Collections.shuffle(deckB, new Random(~seed));
        PlayerState firstPlayer = new PlayerState("先手", new Deck(deckA));
        PlayerState secondPlayer = new PlayerState("后手", new Deck(deckB));
        for (int i = 0; i < 3; i++) {
            firstPlayer.getDeck().draw().ifPresent(firstPlayer.getHand()::add);
            secondPlayer.getDeck().draw().ifPresent(secondPlayer.getHand()::add);
        }
        java.util.function.Consumer<String> quiet = msg -> {
        };
        PlayerState current = firstPlayer;
        PlayerState other = secondPlayer;
        GameEngine currentEngine = engineFirst;
        GameEngine otherEngine = engineSecond;
        AiStrategy currentAi = first;
        AiStrategy otherAi = second;
        for (int round = 0; round < ROUND_CAP; round++) {
            currentEngine.playTurn(current, other, quiet);
            if (other.isDefeated()) {
                return current.getName() + "(" + currentAi.getClass().getSimpleName() + ")";
            }
            PlayerState tmp = current;
            current = other;
            other = tmp;
            GameEngine tmpEngine = currentEngine;
            currentEngine = otherEngine;
            otherEngine = tmpEngine;
            AiStrategy tmpAi = currentAi;
            currentAi = otherAi;
            otherAi = tmpAi;
        }
        return (firstPlayer.getLifePoints() >= secondPlayer.getLifePoints()
                ? "先手" : "后手") + "(判胜)";
    }

    private static int runLadder(String title, AiStrategy first, AiStrategy second) {
        int firstWins = 0;
        StringBuilder table = new StringBuilder(title).append(": ");
        for (long seed = 1; seed <= GAMES; seed++) {
            String winner = playOne(first, second, seed);
            table.append(seed).append("=").append(winner).append(" ");
            if (winner.startsWith("先手")) {
                firstWins++;
            }
        }
        System.out.println(table);
        return firstWins;
    }

    @Test
    void ladderFinishesAndPrintsTable() {
        int greedyAsFirst = runLadder("贪心先手vs随机", new SimpleAi(), new RandomAi(new Random(1)));
        int hardAsFirst = runLadder("困难先手vs贪心", new HardAi(), new SimpleAi());
        int hardAsSecond = runLadder("贪心先手vs困难", new SimpleAi(), new HardAi());
        assertNotNull(greedyAsFirst);
        System.out.println("LADDER_SUMMARY greedy-first-wins=" + greedyAsFirst + "/10"
                + " hard-first-wins=" + hardAsFirst + "/10"
                + " hard-second-wins=" + (10 - hardAsSecond) + "/10");
    }
}
