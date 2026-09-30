package org.example.card.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import org.example.card.ai.AiLevel;
import org.example.card.engine.GameEngine;
import org.example.card.engine.GameSession;
import org.example.card.engine.TurnController;

/**
 * 无界面（headless）对局驱动器 —— v2.0 M0「引擎可嵌入化体检」。
 *
 * <p>本类只依赖 {@code engine}/{@code ai}/{@code event}/{@code model}，<b>不引用任何 JavaFX 类型</b>，
 * 因此可以在无图形环境（服务器、CI）里完整跑完一局。v2.0 的房间服务端要复用的正是这条路径：
 * 会话（{@link GameSession}）+ 结算（{@link GameEngine}）+ 回合编排（{@link TurnController}），
 * 界面只是众多订阅方之一。
 *
 * <p>与 {@code ui.AiTurnDirector} 的区别：后者逐动作演出（有延时、要动画），本类一口气跑完，
 * 只产出可断言的结果与事件计数。
 */
public final class HeadlessGame {

    /** 一局的结局与过程统计。 */
    public record Result(int rounds, int engineTurns, boolean playerFirst, boolean gameOver,
                         boolean playerWon, boolean aiWon, int playerLife, int aiLife,
                         int eventCount, List<String> log) {

        /** 是否分出胜负（未分胜负说明撞到了回合上限）。 */
        public boolean decided() {
            return playerWon || aiWon;
        }

        /** 一行摘要，测试与冒烟脚本用。 */
        public String summary() {
            return "rounds=" + rounds + " turns=" + engineTurns + " first=" + (playerFirst ? "player" : "ai")
                    + " over=" + gameOver + " winner=" + (playerWon ? "player" : aiWon ? "ai" : "none")
                    + " life=" + playerLife + "/" + aiLife + " events=" + eventCount;
        }
    }

    /** 默认回合上限：留足余量让多数对局自然终局。 */
    public static final int DEFAULT_MAX_ROUNDS = 20;

    private HeadlessGame() {
    }

    /** 跑一局标准对局（普通难度、不收集战报）。 */
    public static Result run(long seed) {
        return run(seed, DEFAULT_MAX_ROUNDS, AiLevel.NORMAL);
    }

    /** 跑一局标准对局（不收集战报）。 */
    public static Result run(long seed, int maxRounds, AiLevel level) {
        return run(seed, maxRounds, level, msg -> {
        }, false);
    }

    /**
     * 跑一局标准对局。
     *
     * @param seed      洗牌与先后手的随机种子（同种子完全可复现）
     * @param maxRounds 回合上限，防止极端局无限跑
     * @param level     双方（玩家侧也由 AI 代打）使用的难度
     * @param log       战报接收器，传 {@code msg -> {}} 表示静默
     * @param keepLog   为 true 时把战报完整留进 {@link Result#log()}
     */
    public static Result run(long seed, int maxRounds, AiLevel level,
                             Consumer<String> log, boolean keepLog) {
        List<String> collected = keepLog ? new ArrayList<>() : null;
        Consumer<String> sink = collected == null ? log : msg -> {
            collected.add(msg);
            log.accept(msg);
        };

        Random random = new Random(seed);
        GameSession session = GameSession.newPveBattle(random);
        GameEngine engine = new GameEngine(level.newAi());
        TurnController turns = new TurnController(engine);
        int[] events = {0};
        engine.eventBus().onAny(e -> events[0]++);

        boolean playerFirst = random.nextBoolean();
        turns.openGame(session, playerFirst, sink, false);

        int rounds = 0;
        while (!session.gameOver() && rounds < maxRounds) {
            if (session.isYourTurn()) {
                engine.playTurn(session.getPlayer(), session.getAi(), sink);
                if (session.gameOver()) {
                    break;
                }
                turns.closePlayerTurn(session, sink);
            } else {
                engine.playTurn(session.getAi(), session.getPlayer(), sink);
                if (session.gameOver()) {
                    break;
                }
                turns.openPlayerTurn(session, sink);
            }
            rounds++;
        }

        boolean playerWon = session.getAi().isDefeated() && !session.getPlayer().isDefeated();
        boolean aiWon = session.getPlayer().isDefeated() && !session.getAi().isDefeated();
        return new Result(rounds, engine.getTurn(), playerFirst, session.gameOver(),
                playerWon, aiWon, session.getPlayer().getLifePoints(), session.getAi().getLifePoints(),
                events[0], collected == null ? List.of() : List.copyOf(collected));
    }
}
