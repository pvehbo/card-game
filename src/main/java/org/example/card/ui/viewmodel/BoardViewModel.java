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
import org.example.card.model.PetCard;
import org.example.card.model.SpellCard;

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
            if (session.getPlayer() != null && e.actor() == session.getPlayer()
                    && e.card() != null) {
                pendingDrawAnim.add(e.card());
            }
        });
        engine.eventBus().onAny(e -> touch());
    }

    public GameSession session() {
        return session;
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
        if (card instanceof MinionCard) {
            return engine.canPlayMinion(session.getPlayer());
        } else if (card instanceof SpellCard) {
            return engine.canPlaySpell(session.getPlayer());
        } else if (card instanceof PetCard) {
            return engine.canPlayPet(session.getPlayer());
        }
        return false;
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
        pendingDrawAnim.removeIf(c -> !session.getPlayer().getHand().contains(c));
    }
}
