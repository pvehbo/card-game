package org.example.card.ai;

import org.example.card.data.CardDatabase;
import org.example.card.engine.GameEngine;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-4 AI 天梯：固定种子、40 张标准牌堆（copies 缺省 2），不同策略捉对比拼。
 *
 * <p>口径（批① T5 修订）：一轮 = 双方各行动一次；胜方以「对手生命先归零」计；
 * 打满整轮上限仍未分胜负时按剩余生命判胜（记为「判胜」，不入确定性断言）。
 * 只断言结构与确定性（可复现、名额合法）；胜负表打印出来供 BALANCE.md 记录——
 * 样本与种子固定，可复现；调参后看表格变化。
 */
class AiLadderTest {

    private static final int GAMES = 10;
    /** 整轮上限：满 30 整轮（60 个半回合行动）仍未分胜负按判胜收场。 */
    private static final int ROUND_CAP = 30;

    /** 单局结果：winner ∈ {先手, 后手}；judged=true 表示超限按血判胜、非双方自然终局。 */
    private record Outcome(String winner, boolean judged) {
    }

    /** 一局：先手策略执先手方。 */
    private static Outcome playOne(AiStrategy first, AiStrategy second, long seed) {
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
        GameEngine firstSide = engineFirst;
        GameEngine secondSide = engineSecond;
        for (int round = 0; round < ROUND_CAP; round++) {
            firstSide.playTurn(firstPlayer, secondPlayer, quiet);
            if (secondPlayer.isDefeated()) {
                return new Outcome("先手", false);
            }
            secondSide.playTurn(secondPlayer, firstPlayer, quiet);
            if (firstPlayer.isDefeated()) {
                return new Outcome("后手", false);
            }
        }
        boolean firstWon = firstPlayer.getLifePoints() >= secondPlayer.getLifePoints();
        return new Outcome(firstWon ? "先手" : "后手", true);
    }

    private static String runLadder(String title, AiStrategy first, AiStrategy second) {
        int firstWins = 0;
        int judged = 0;
        StringBuilder table = new StringBuilder(title).append(": ");
        for (long seed = 1; seed <= GAMES; seed++) {
            Outcome outcome = playOne(first, second, seed);
            table.append(seed).append("=").append(outcome.winner())
                    .append(outcome.judged() ? "(判)" : "").append(" ");
            if (outcome.winner().equals("先手")) {
                firstWins++;
            }
            if (outcome.judged()) {
                judged++;
            }
        }
        System.out.println(table);
        System.out.println("  first-wins=" + firstWins + "/" + GAMES + " judged=" + judged + "/" + GAMES);
        return table.toString();
    }

    @Test
    void ladderFinishesAndPrintsTable() {
        String tableA = runLadder("贪心先手vs随机", new SimpleAi(), new RandomAi(new Random(1)));
        String tableB = runLadder("困难先手vs贪心", new HardAi(), new SimpleAi());
        String tableC = runLadder("贪心先手vs困难", new SimpleAi(), new HardAi());
        assertTrue(countRows(tableA) == GAMES && countRows(tableB) == GAMES && countRows(tableC) == GAMES,
                "三组天梯都应打满 10 局（可终局性）");
    }

    /** 统计 "seed=… " 记录条数，验证每局都产出了结果。 */
    private static int countRows(String table) {
        int count = 0;
        for (int i = table.indexOf("="); i >= 0; i = table.indexOf("=", i + 1)) {
            count++;
        }
        return count;
    }

    @Test
    void ladderIsDeterministicBySeed() {
        String tableA = runLadder("贪心先手vs随机[A]", new SimpleAi(), new RandomAi(new Random(1)));
        String tableAgain = runLadder("贪心先手vs随机[A]", new SimpleAi(), new RandomAi(new Random(1)));
        assertEquals(tableA, tableAgain, "同种子重跑应得到同一张胜负表（确定性）");
    }
}
