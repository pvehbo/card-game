package org.example.card.ui;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;

import org.example.card.engine.GameEngine;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;

/**
 * AI 演出导演（S7 从 CardGameApp 抽出）：把 AI 回合拆成一步一动的脚本。
 *
 * 之前一口气跑完整回合，所有音效挤在同一帧、玩家也看不清；
 * 现在 summon/spell/pet/逐个出手/收尾之间留 {@link #STEP_GAP} 间隔。
 * 攻击动画本身由 Stage 回调播（突刺演完再结算），本类只管排期。
 * 零游戏状态持有（状态全在会话里），纯 JavaFX 调度。
 */
public final class AiTurnDirector {

    /** 每步之间的停顿：让玩家看清 AI 做了什么。 */
    public static final Duration STEP_GAP = Duration.millis(420);

    /** 舞台回调（动效与收尾留在界面层）。 */
    public interface Stage {
        void log(String message);

        void refresh();

        boolean gameOver();

        /** 一次出手（含突刺动画与结算），由界面实现。 */
        void performAiStrike(MinionCard attacker);

        /** 回合收尾后调用（交棒给玩家）。 */
        void onTurnFinished();
    }

    private GameEngine engine;
    private final Deque<Runnable> steps = new ArrayDeque<>();

    public void attach(GameEngine engine) {
        this.engine = engine;
        this.steps.clear();
    }

    public void clear() {
        steps.clear();
    }

    /** 还有未演完的步骤（自动对局用它判断 AI 回合是否演完）。 */
    public boolean hasPendingSteps() {
        return !steps.isEmpty();
    }

    /** 开演一局 AI 回合：起手 + 出牌三件 + 逐个出手 + 收尾。 */
    public void beginTurn(PlayerState ai, PlayerState player, Stage stage) {
        if (ai == null || player == null || stage.gameOver()) {
            steps.clear();
            return;
        }
        steps.clear();
        engine.beginAiTurn(ai, stage::log);
        stage.refresh();

        steps.add(() -> {
            if (engine.aiSummon(ai, stage::log)) {
                stage.refresh();
            }
        });
        steps.add(() -> {
            if (engine.aiSpell(ai, player, stage::log)) {
                stage.refresh();
            }
        });
        steps.add(() -> {
            if (engine.aiPet(ai, stage::log)) {
                stage.refresh();
            }
        });
        for (MinionCard attacker : engine.aiReadyAttackers(ai)) {
            steps.add(() -> stage.performAiStrike(attacker));
        }
        steps.add(() -> {
            engine.endAiTurn(ai, player, stage::log);
            stage.onTurnFinished();
        });
        pump(stage);
    }

    /** 推进一步；队列空了或分出胜负就停下。 */
    private void pump(Stage stage) {
        if (steps.isEmpty() || stage.gameOver()) {
            steps.clear();
            return;
        }
        steps.poll().run();
        if (steps.isEmpty()) {
            return;
        }
        var gap = new PauseTransition(STEP_GAP);
        gap.setOnFinished(e -> pump(stage));
        gap.play();
    }
}
