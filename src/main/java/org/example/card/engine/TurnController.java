package org.example.card.engine;

import java.util.function.Consumer;

/**
 * 回合流转（S3 从 CardGameApp 抽出，不改玩法）。
 *
 * 只管“谁先谁后、回合开合”这一层编排，单步结算仍在 GameEngine。
 * 界面开局 / 结束回合 / AI 交棒都调这里，不再自己拼流程。零 JavaFX 引用。
 */
public final class TurnController {

    private final GameEngine engine;

    public TurnController(GameEngine engine) {
        this.engine = engine;
    }

    /**
     * 开局：定先手并推进第一个回合。
     *
     * @param deferFirstTurn 演示/截图模式传 true：只定归属，不抽牌不计回合，
     *                       避免覆盖界面摆好的演示局面（原 startNewGame 行为）。
     */
    public void openGame(GameSession session, boolean playerFirst,
                         Consumer<String> log, boolean deferFirstTurn) {
        session.setYourTurn(playerFirst);
        if (playerFirst && !deferFirstTurn) {
            engine.startPlayerTurn(session.getPlayer(), session.getAi(), log);
        }
    }

    /** 玩家结束回合：结算收尾，回合交给 AI。 */
    public void closePlayerTurn(GameSession session, Consumer<String> log) {
        engine.endPlayerTurn(session.getPlayer(), session.getAi(), log);
        session.setYourTurn(false);
    }

    /** AI 交棒：新玩家回合开局（抽牌），回合交还玩家。 */
    public void openPlayerTurn(GameSession session, Consumer<String> log) {
        engine.startPlayerTurn(session.getPlayer(), session.getAi(), log);
        session.setYourTurn(true);
    }
}
