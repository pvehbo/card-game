package org.example.card.engine;

import java.util.Random;

import org.example.card.data.CardDatabase;
import org.example.card.data.Ruleset;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.PlayerState;

/**
 * 对局会话（S3 从 CardGameApp 抽出，不改玩法）。
 *
 * 持有“一局游戏”的全部状态：双方玩家、代数、轮到谁。
 * 界面只负责渲染与输入，回合归属与开局发牌都在这里。零 JavaFX 引用。
 */
public final class GameSession {

    private final PlayerState player;
    private final PlayerState ai;
    /**
     * 对局代数：每开一局 +1。
     * AI 回合是「后台线程睡一会儿 → 回到界面线程执行」，如果玩家在 AI 思考期间点
     * 「开始游戏」，那个还没执行的回调必须凭代数丢掉，否则新对局会被硬塞一个多余的 AI 回合。
     */
    private int generation;
    /** 是否轮到玩家输入（否则是 AI 行动中或演示中）。 */
    private boolean yourTurn;

    private final Ruleset ruleset;

    private GameSession(PlayerState player, PlayerState ai, Ruleset ruleset) {
        this.player = player;
        this.ai = ai;
        this.ruleset = ruleset;
    }

    /** 新开一局 PvE：经典规则、标准牌堆、洗牌、双方各摸 Ruleset.openingHand() 张。 */
    public static GameSession newPveBattle(Random random) {
        return newPveBattle(random, CardDatabase.standardDeck());
    }

    /**
     * 新开一局 PvE（自定义玩家牌堆，须 30 张合法，调用方用 DeckBuilder 保证；
     * AI 永远标准牌堆）。牌按实例再拷一份，会话独占。规则集=经典。
     */
    public static GameSession newPveBattle(Random random, java.util.List<Card> playerCards) {
        return newPveBattle(random, playerCards, Ruleset.CLASSIC);
    }

    /** 规则集版：heroLife/maxManaCap 注入 PlayerState，openingHand 决定开局发牌数。 */
    public static GameSession newPveBattle(Random random, java.util.List<Card> playerCards,
                                           Ruleset ruleset) {
        PlayerState player = new PlayerState("你", new Deck(copyOf(playerCards)),
                ruleset.heroLife(), ruleset.maxManaCap());
        PlayerState ai = new PlayerState("AI", new Deck(copyOf(CardDatabase.standardDeck())),
                ruleset.heroLife(), ruleset.maxManaCap());
        player.getDeck().shuffle(random);
        ai.getDeck().shuffle(random);
        GameSession session = new GameSession(player, ai, ruleset);
        for (int i = 0; i < ruleset.openingHand(); i++) {
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
        return restore(player, ai, yourTurn, Ruleset.CLASSIC);
    }

    public static GameSession restore(PlayerState player, PlayerState ai, boolean yourTurn,
                                      Ruleset ruleset) {
        GameSession session = new GameSession(player, ai, ruleset);
        session.setYourTurn(yourTurn);
        return session;
    }

    public PlayerState getPlayer() {
        return player;
    }

    public PlayerState getAi() {
        return ai;
    }

    /** 本局的规则集（开局装配，只读）。 */
    public Ruleset ruleset() {
        return ruleset;
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
