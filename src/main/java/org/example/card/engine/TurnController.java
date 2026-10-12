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
    /** 玩家回合是否已被控制器打开（防重复开局；closePlayerTurn 恒放行，读档安全）。 */
    private boolean turnOpen;

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
        // 新局：控制器状态跟着复位（演示模式不算开局，后续动作仍可自由驱动）
        turnOpen = !deferFirstTurn;
        if (deferFirstTurn) {
            return;
        }
        if (playerFirst) {
            engine.startPlayerTurn(session.getPlayer(), session.getAi(), log);
            engine.grantSecondMoveBonus(session.getAi(), log);
        } else {
            engine.grantSecondMoveBonus(session.getPlayer(), log);
        }
    }

    /** 玩家结束回合：结算收尾，回合交给 AI。恒放行（读档恢复的玩家回合也要能交棒）。 */
    public void closePlayerTurn(GameSession session, Consumer<String> log) {
        engine.endPlayerTurn(session.getPlayer(), session.getAi(), log);
        session.setYourTurn(false);
        turnOpen = false;
    }

    /** AI 交棒：新玩家回合开局（抽牌），回合交还玩家。 */
    public void openPlayerTurn(GameSession session, Consumer<String> log) {
        // 收尾守卫：玩家回合未交棒时，AI 的重复交棒只提示、不重开回合（防双开局双抽牌）
        if (turnOpen) {
            log.accept("回合已在进行，忽略重复开局");
            return;
        }
        engine.startPlayerTurn(session.getPlayer(), session.getAi(), log);
        session.setYourTurn(true);
        turnOpen = true;
    }
}
