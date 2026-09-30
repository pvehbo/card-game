package org.example.card.ui.viewmodel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.example.card.engine.GameEngine;
import org.example.card.engine.GameSession;
import org.example.card.event.GameEvent;
import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;

/**
 * 战场 ViewModel（S7）：界面唯一的真相源。
 *
 * 持有本局会话 + 已选攻击者 + 抽牌动画标记，对外只暴露只读查询与选择操作；
 * 订阅引擎事件（任意事件 → 变更通知；抽牌 → 动画标记），视图层只绑它，
 * 不再直读 PlayerState 可变列表做决策。零 JavaFX 引用，可脱离界面单测。
 */
public final class BoardViewModel {

    private GameSession session;
    private GameEngine engine;
    /**
     * 我在会话里的座位：单机恒为 0；联机时是服务端给我的座位号，
     * 因为客户端的影子棋局必须与服务端同序（发牌按座位顺序），我可能坐在 1 号位。
     */
    private int localSeat;
    private MinionCard selectedAttacker;
    /** 本回合新抽到的牌：等控件建好后补一段“抽牌入场”动画。 */
    private final Set<Card> pendingDrawAnim = new HashSet<>();
    private final List<Runnable> changeListeners = new ArrayList<>();

    /** 接线一局（开局时调用）：换会话与引擎，重新订阅新总线。 */
    public void attach(GameSession session, GameEngine engine) {
        this.session = session;
        this.engine = engine;
        this.selectedAttacker = null;
        this.pendingDrawAnim.clear();
        engine.eventBus().on(GameEvent.Type.DRAW, e -> {
            if (me() != null && e.actor() == me()
                    && e.card() != null) {
                pendingDrawAnim.add(e.card());
            }
        });
        engine.eventBus().onAny(e -> touch());
    }

    public GameSession session() {
        return session;
    }

    /** 设置我在会话里的座位（联机开局时调用；单机不用管，默认 0）。 */
    public void setLocalSeat(int seat) {
        this.localSeat = seat == 1 ? 1 : 0;
    }

    public int localSeat() {
        return localSeat;
    }

    /** 我这边的玩家状态（界面里的「你」）。 */
    public PlayerState me() {
        if (session == null) {
            return null;
        }
        return localSeat == 1 ? session.getAi() : session.getPlayer();
    }

    /** 对面那一边的玩家状态。 */
    public PlayerState foe() {
        if (session == null) {
            return null;
        }
        return localSeat == 1 ? session.getPlayer() : session.getAi();
    }

    /** 界面刷新通知：订阅一次，之后每次 touch() 自动刷新。 */
    public void onChange(Runnable listener) {
        changeListeners.add(listener);
    }

    /** 状态变了（引擎事件会自动调，界面直接动作后手动调）。 */
    public void touch() {
        for (Runnable listener : new ArrayList<>(changeListeners)) {
            listener.run();
        }
    }

    // ============ 回合与终局 ============

    /** 是否轮到玩家输入（会话为空时视为否，避免开局前误触）。 */
    public boolean yourTurn() {
        return session != null && session.isYourTurn();
    }

    public boolean gameOver() {
        return session != null && session.gameOver();
    }

    public int turn() {
        return engine == null ? 0 : engine.getTurn();
    }

    // ============ 选择 ============

    public MinionCard selectedAttacker() {
        return selectedAttacker;
    }

    public boolean hasSelection() {
        return selectedAttacker != null;
    }

    public void selectAttacker(MinionCard minion) {
        this.selectedAttacker = minion;
    }

    public void clearSelection() {
        this.selectedAttacker = null;
    }

    // ============ 可玩判断（与 AI 共用同一套 ActionValidator，经引擎方法） ============

    public boolean isPlayable(Card card) {
        if (session == null || engine == null) {
            return false;
        }
        // 次数限 + 费用合并判断：费用不够自动置灰
        return engine.canPlay(me(), card);
    }

    // ============ 抽牌动画标记 ============

    /** 取走标记（有则顺带做入场动画），没有返回 false。 */
    public boolean consumeDrawn(Card card) {
        return pendingDrawAnim.remove(card);
    }

    /** 清掉已不在手牌的过期标记，避免泄漏。 */
    public void pruneDrawn() {
        if (session == null) {
            pendingDrawAnim.clear();
            return;
        }
        pendingDrawAnim.removeIf(c -> !me().getHand().contains(c));
    }
}
