package org.example.card.engine;

import java.util.Random;

import org.example.card.data.CardDatabase;
import org.example.card.model.Deck;
import org.example.card.model.PlayerState;

/**
 * 对局会话（S3 从 CardGameApp 抽出，不改玩法）。
 *
 * 持有“一局游戏”的全部状态：双方玩家、模式、代数、轮到谁。
 * 界面只负责渲染与输入，回合归属与开局发牌都在这里。零 JavaFX 引用。
 */
public final class GameSession {

    /** 对局模式（先只有 PvE，本地双人后续加）。 */
    public enum Mode {
        PVE
    }

    /** 开局手牌数。 */
    private static final int OPENING_HAND = 3;

    private final PlayerState player;
    private final PlayerState ai;
    private final Mode mode;
    /**
     * 对局代数：每开一局 +1。
     * AI 回合是「后台线程睡一会儿 → 回到界面线程执行」，如果玩家在 AI 思考期间点
     * 「开始游戏」，那个还没执行的回调必须凭代数丢掉，否则新对局会被硬塞一个多余的 AI 回合。
     */
    private int generation;
    /** 是否轮到玩家输入（否则是 AI 行动中或演示中）。 */
    private boolean yourTurn;

    private GameSession(PlayerState player, PlayerState ai, Mode mode) {
        this.player = player;
        this.ai = ai;
        this.mode = mode;
    }

    /** 新开一局 PvE：标准牌堆、洗牌、双方各摸 {@value #OPENING_HAND} 张。 */
    public static GameSession newPveBattle(Random random) {
        PlayerState player = new PlayerState("你", new Deck(CardDatabase.standardDeck()));
        PlayerState ai = new PlayerState("AI", new Deck(CardDatabase.standardDeck()));
        player.getDeck().shuffle(random);
        ai.getDeck().shuffle(random);
        GameSession session = new GameSession(player, ai, Mode.PVE);
        for (int i = 0; i < OPENING_HAND; i++) {
            player.getDeck().draw().ifPresent(player.getHand()::add);
            ai.getDeck().draw().ifPresent(ai.getHand()::add);
        }
        return session;
    }

    public PlayerState getPlayer() {
        return player;
    }

    public PlayerState getAi() {
        return ai;
    }

    public Mode getMode() {
        return mode;
    }

    /** 新开一局前调用，旧的 AI 延迟回调凭此作废。 */
    public void nextGeneration() {
        generation++;
    }

    public int getGeneration() {
        return generation;
    }

    public boolean isYourTurn() {
        return yourTurn;
    }

    public void setYourTurn(boolean yourTurn) {
        this.yourTurn = yourTurn;
    }

    /** 任一方英雄倒下即终局。 */
    public boolean gameOver() {
        return player.isDefeated() || ai.isDefeated();
    }
}
