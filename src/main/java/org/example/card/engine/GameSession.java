package org.example.card.engine;

import java.util.Random;

import org.example.card.data.CardDatabase;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.PlayerState;

/**
 * 对局会话（S3 从 CardGameApp 抽出，不改玩法）。
 *
 * 持有“一局游戏”的全部状态：双方玩家、模式、代数、轮到谁。
 * 界面只负责渲染与输入，回合归属与开局发牌都在这里。零 JavaFX 引用。
 */
public final class GameSession {

    /** 对局模式：PVE 打 AI；PVP 两个人类座位（v2.0 联机复用同一份引擎）。 */
    public enum Mode {
        PVE,
        PVP
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
        return newPveBattle(random, CardDatabase.standardDeck());
    }

    /**
     * 新开一局 PvE（自定义玩家牌堆，须 30 张合法，调用方用 DeckBuilder 保证；
     * AI 永远标准牌堆）。牌按实例再拷一份，会话独占。
     */
    public static GameSession newPveBattle(Random random, java.util.List<Card> playerCards) {
        PlayerState player = new PlayerState("你", new Deck(copyOf(playerCards)));
        PlayerState ai = new PlayerState("AI", new Deck(copyOf(CardDatabase.standardDeck())));
        player.getDeck().shuffle(random);
        ai.getDeck().shuffle(random);
        GameSession session = new GameSession(player, ai, Mode.PVE);
        for (int i = 0; i < OPENING_HAND; i++) {
            player.getDeck().draw().ifPresent(player.getHand()::add);
            ai.getDeck().draw().ifPresent(ai.getHand()::add);
        }
        return session;
    }

    /**
     * 新开一局 PvP（v2.0 联机）：两个座位都是人类，双方各用自己的牌堆，
     * 洗牌后各摸 {@value #OPENING_HAND} 张，之后没有任何 AI 驱动——每一步都由外部指令推动。
     *
     * <p>刻意复用 PvE 的 (player, ai) 两个 {@link PlayerState} 槽位：引擎里所有牌局操作
     * （startPlayerTurn / playMinion / attack …）本来就是座位无关的 {@code (self, foe)}，
     * 所以「对手位」只是叫 ai 而已，没有任何 AI 语义渗进玩法。
     *
     * <p>开局手牌不发事件：它是「同种子 → 同状态」的确定性构造的一部分，
     * 两端各自用同一个 seed 调本方法即可得到逐字节相同的开局，
     * 服务端的事件流从第一次引擎调用（开局补牌）才开始，因此不会重复结算。
     */
    public static GameSession newVersus(Random random, java.util.List<Card> playerCards,
                                        java.util.List<Card> opponentCards,
                                        String playerName, String opponentName) {
        PlayerState player = new PlayerState(playerName, new Deck(copyOf(playerCards)));
        PlayerState ai = new PlayerState(opponentName, new Deck(copyOf(opponentCards)));
        player.getDeck().shuffle(random);
        ai.getDeck().shuffle(random);
        GameSession session = new GameSession(player, ai, Mode.PVP);
        for (int i = 0; i < OPENING_HAND; i++) {
            player.getDeck().draw().ifPresent(player.getHand()::add);
            ai.getDeck().draw().ifPresent(ai.getHand()::add);
        }
        return session;
    }

    private static java.util.List<Card> copyOf(java.util.List<Card> cards) {
        java.util.List<Card> copies = new java.util.ArrayList<>();
        for (Card card : cards) {
            copies.add(card.copy());
        }
        return copies;
    }

    /** 读档重建（SaveService 用）：状态由调用方备好，这里只装配归属。 */
    public static GameSession restore(PlayerState player, PlayerState ai, boolean yourTurn) {
        GameSession session = new GameSession(player, ai, Mode.PVE);
        session.setYourTurn(yourTurn);
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
