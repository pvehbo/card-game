package org.example.card.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.example.card.ai.AiLevel;
import org.example.card.data.CardDatabase;
import org.example.card.engine.GameEngine;
import org.example.card.engine.GameSession;
import org.example.card.event.GameEvent;
import org.example.card.model.Card;
import org.example.card.model.PlayerState;
import org.example.card.net.MessageCodec;
import org.example.card.net.NetMessage;
import org.example.card.net.Protocol;
import org.example.card.service.MatchReferee;

/**
 * 联机房间（M2）：两个座位的权威对局，纯内存、零 JavaFX、零第三方依赖。
 *
 * <h2>权威在哪</h2>
 * 唯一的一份真实状态是服务端这里的 {@link GameSession}。客户端只是「手柄」：
 * 它发 {@link NetMessage.Action}（下标意图），服务端用<b>同一份引擎</b>重算合法性并结算，
 * 再把 {@link NetMessage.Event} 按 seq 递增广播回两端。因此客户端算什么都不影响结果，
 * 改包也只能改自己的显示，改不了账。
 *
 * <h2>权限只看连接，不看自称</h2>
 * {@link NetMessage.Action#actor()} 只写日志用；「这步是谁走的」一律按消息来自哪个座位判定，
 * 所以冒充对手的名字也拿不到对手的操作权（见 {@code GameRoomTest.forgedActorCannotActForOpponent}）。
 *
 * <h2>两端如何得到同一盘棋</h2>
 * {@link NetMessage.Start} 携带 seed（及先手方）。两端各自用同一个 seed 调
 * {@link GameSession#newVersus} 得到逐字节相同的开局手牌——开局手牌不发事件，是确定性构造的一部分；
 * 服务端的事件流从开局补牌那次引擎调用才开始。于是「重放事件」不会重复结算。
 *
 * <h2>谁说了算</h2>
 * 只有被 {@link MatchReferee} 判定合法的指令才会落地，落地后立刻以 {@link NetMessage.SYNC}
 * 回声给两个座位（见 {@link #announceSync}）。客户端拿到 SYNC 才在本地重放同一条指令——
 * 连自己的操作也要等回声，于是客户端状态只是「服务端接受过的指令序列」的函数，两端不可能分叉。
 * {@link NetMessage.Event} 则是同一批指令的结算明细，供线上记录、观战与将来的断线补状态使用。
 *
 * <h2>暗信息</h2>
 * 抽牌 / 烧牌是唯一会暴露「手里那张牌」的事件：发给对手的版本被换成「对手抽了一张牌」，
 * 牌面只留在服务端日志和本人那一侧（见 {@link #redact}）。其余事件都是明牌，原样转发。
 * 客户端凭 START 的 seed 自己就能推出对手摸到的是哪张——但那只存在于它本地，
 * 改包也改不出「服务端认为你知道」的事实。
 *
 * <h2>线程模型</h2>
 * 所有公开入口都 {@code synchronized}：一个房间在一瞬间只处理一条指令，
 * 事件按发生顺序编号，不会出现两条事件抢同一个 seq。
 */
public final class GameRoom {

    /** 座位数（1v1）。 */
    public static final int SEATS = 2;

    /** 对手抽牌时发给你的文案（牌面被刻意抹掉，见 {@link #redact}）。 */
    public static final String HIDDEN_DRAW = "对手抽了一张牌";

    /** 对手烧牌时发给你的文案（同上，只告诉你「发生了一次烧牌」）。 */
    public static final String HIDDEN_BURN = "对手手牌已满，烧掉一张牌";

    /** 房间生命周期。 */
    public enum State {
        /** 等对手入座。 */
        WAITING,
        /** 对局进行中。 */
        PLAYING,
        /** 分出胜负，不再接受动作。 */
        FINISHED
    }

    /** 一个座位的出口：房间只往这里写已经序列化好的报文，不关心它是 socket 还是测试里的 List。 */
    public interface Transport {
        void send(String wire);
    }

    private final long seed;
    private final Supplier<List<Card>>[] deckSources;

    private final Transport[] transports = new Transport[SEATS];
    private final String[] names = new String[SEATS];
    private final boolean[] joined = new boolean[SEATS];

    private final List<String> roomLog = new ArrayList<>();
    private final List<String> rejections = new ArrayList<>();

    private State state = State.WAITING;
    private GameSession session;
    private GameEngine engine;
    private MatchReferee referee;
    private int eventSeq;
    private int syncSeq;
    private int actionCount;
    private boolean gameOverPublished;

    private final Consumer<String> sink = roomLog::add;

    /**
     * @param seed       随机种子：开局洗牌与先手判定都只看它，两端凭它复现同一盘棋
     * @param seat0Deck  座位 0 的牌堆来源（每次开局时取一次，Room 会再拷一份）
     * @param seat1Deck  座位 1 的牌堆来源
     */
    @SuppressWarnings("unchecked")
    public GameRoom(long seed, Supplier<List<Card>> seat0Deck, Supplier<List<Card>> seat1Deck) {
        this.seed = seed;
        this.deckSources = new Supplier[] {seat0Deck, seat1Deck};
    }

    /** 双方都用标准牌堆的房间。 */
    public static GameRoom standard(long seed) {
        return new GameRoom(seed, CardDatabase::standardDeck, CardDatabase::standardDeck);
    }

    // ============ 入座与开局 ============

    /** 绑定一个座位的出口（还没有 JOIN，只是「这条连接坐在这个位子上」）。 */
    public synchronized void attach(int seat, Transport transport) {
        checkSeat(seat);
        transports[seat] = transport;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized GameSession session() {
        return session;
    }

    /** 当前该谁走；对局还没开始时为 -1。 */
    public synchronized int activeSeat() {
        return referee == null ? -1 : referee.activeSeat();
    }

    /** 服务端视角的对局日志（引擎打印的每一行）。 */
    public synchronized List<String> log() {
        return List.copyOf(roomLog);
    }

    /** 被拒的非法动作记录：「座位 1 被拒 [NOT_YOUR_TURN] 还没轮到你」。 */
    public synchronized List<String> rejections() {
        return List.copyOf(rejections);
    }

    public synchronized int actionCount() {
        return actionCount;
    }

    /**
     * 处理一条来自某座位的报文。这是房间唯一的输入口。
     * 任何解析失败 / 越权 / 非法动作都只回一条 {@link NetMessage.Failure} 并记录，不影响对局状态。
     */
    public synchronized void handle(int seat, String wire) {
        checkSeat(seat);
        NetMessage message;
        try {
            message = MessageCodec.decode(wire);
        } catch (RuntimeException ex) {
            reject(seat, Protocol.ERR_BAD_MESSAGE, "报文解析失败：" + ex.getMessage());
            return;
        }
        if (message instanceof NetMessage.Join join) {
            join(seat, join);
            return;
        }
        if (message instanceof NetMessage.Ping ping) {
            send(seat, new NetMessage.Pong(ping.timestamp()));
            return;
        }
        if (message instanceof NetMessage.Action action) {
            applyAction(seat, action);
            return;
        }
        reject(seat, Protocol.ERR_BAD_MESSAGE, "该座位不该发这种报文：" + message.kind());
    }

    private void join(int seat, NetMessage.Join join) {
        if (join.protocolVersion() != Protocol.VERSION) {
            reject(seat, Protocol.ERR_VERSION,
                    "协议版本不符：服务端 " + Protocol.VERSION + "，客户端 " + join.protocolVersion());
            return;
        }
        if (join.deckId() != null && !join.deckId().isBlank()) {
            // M3 的范围：联机只打标准牌堆。START 只带 seed，客户端必须能自行重建同一副牌，
            // 所以自定义牌组在握手阶段就被挡住——否则两个客户端会用各自的牌表重放，必然分叉。
            reject(seat, Protocol.ERR_BAD_MESSAGE, "联机对局固定使用标准牌堆（自定义牌组仍是单机专属）");
            return;
        }
        if (state != State.WAITING) {
            reject(seat, Protocol.ERR_ROOM_FULL, "对局已经开始，无法入座");
            return;
        }
        names[seat] = (join.name() == null || join.name().isBlank()) ? "玩家" + (seat + 1) : join.name().trim();
        joined[seat] = true;
        if (joined[0] && joined[1]) {
            start();
        }
    }

    /** 双方就位：建局、开局补牌、广播 START。 */
    private void start() {
        state = State.PLAYING;
        Random random = new Random(seed);
        List<Card> seat0Cards = deckSources[0].get();
        List<Card> seat1Cards = deckSources[1].get();
        session = GameSession.newVersus(random, seat0Cards, seat1Cards, names[0], names[1]);
        engine = new GameEngine(AiLevel.NORMAL.newAi());
        engine.eventBus().onAny(this::onEvent);
        boolean playerFirst = random.nextBoolean();
        int firstSeat = playerFirst ? 0 : 1;
        // 先把 START 发出去，再开局补牌：客户端必须先知道 seed 与先手方，才能解释随后的开局事件。
        for (int seat = 0; seat < SEATS; seat++) {
            // you/opponent 是收件人视角的名字，youSeat/firstSeat 是服务端座位的权威事实：
            // 客户端必须知道自己是几号座位，才能把本地棋局摆成与服务端同序的一局（发牌按座位顺序）。
            send(seat, new NetMessage.Start(Protocol.VERSION, seed, seat, firstSeat,
                    names[seat], names[1 - seat], Protocol.MODE_PVP));
        }
        roomLog.add("房间开局：seed=" + seed + "，" + names[0] + " vs " + names[1]
                + "，先手=" + names[firstSeat]);
        // 先手起手（抽 1 张 + 法力 1）+ 后手补偿，和 PvE 同一套规则，只是两个座位都走 startPlayerTurn，
        // 没有 beginAiTurn 那种 AI 专用入口——这正说明引擎的每个方法都是座位无关的。
        referee = new MatchReferee(session, engine, sink, firstSeat);
    }

    // ============ 动作结算 ============

    private void applyAction(int seat, NetMessage.Action action) {
        if (state == State.WAITING) {
            reject(seat, Protocol.ERR_BAD_MESSAGE, "对局还没开始");
            return;
        }
        if (state == State.FINISHED) {
            reject(seat, Protocol.ERR_GAME_OVER, "对局已经结束");
            return;
        }
        Optional<MatchReferee.Refusal> refusal = referee.apply(seat, action);
        if (refusal.isPresent()) {
            MatchReferee.Refusal why = refusal.get();
            reject(seat, why.code(), why.message());
            return;
        }
        actionCount++;
        announceSync(seat, action);
        if (state == State.PLAYING && session.gameOver()) {
            announceGameOver();
        }
    }

    /**
     * 把一条被接受的指令回声给两个座位（M3）。
     *
     * <p>客户端不自己判断任何规则：它只按收到的 SYNC 顺序，在本地把同一条指令喂给同一个
     * {@link MatchReferee}。于是「客户端状态」永远只是服务端接受过的指令序列的函数，
     * 两端不可能分叉；自己的操作同样要等这条回声才落地。
     *
     * <p>actor 用服务端记下的名字而不是客户端自称的那个——回声是权威事实，不是转述。
     */
    private void announceSync(int seat, NetMessage.Action action) {
        syncSeq++;
        int turn = engine.getTurn();
        for (int i = 0; i < SEATS; i++) {
            send(i, new NetMessage.Sync(syncSeq, turn, names[seat], action.action(),
                    action.cardIndex(), action.fieldIndex(), action.targetIndex()));
        }
    }

    private void announceGameOver() {
        state = State.FINISHED;
        if (gameOverPublished) {
            return;
        }
        gameOverPublished = true;
        PlayerState winner = session.getPlayer().isDefeated() ? session.getAi() : session.getPlayer();
        engine.eventBus().publish(GameEvent.gameOver(winner, "对局结束：" + winner.getName() + " 获胜"));
    }

    // ============ 事件出口 ============

    /**
     * 引擎每发一条事件就编号，再<b>按座位裁剪</b>后广播。
     *
     * <p>裁剪只针对「手里那张牌」：抽牌 / 烧牌会暴露牌面，对手收到的版本把卡牌抹掉、
     * 文案改成「对手抽了一张牌」。其余事件（上场、法术、攻击、死亡、回合、终局）本来就是明牌，
     * 原样转发。两个座位拿到同一个 seq，顺序不会有分歧。
     *
     * <p>服务端自己的日志保留完整牌面——审计要看得见，网络上看不见。
     */
    private void onEvent(GameEvent event) {
        if (event.type() == GameEvent.Type.GAME_OVER) {
            gameOverPublished = true;
        }
        eventSeq++;
        int turn = engine == null ? 0 : engine.getTurn();
        roomLog.add("[" + eventSeq + "] " + event.message());
        for (int seat = 0; seat < SEATS; seat++) {
            if (transports[seat] == null) {
                continue;
            }
            GameEvent visible = redact(event, seat);
            send(seat, new NetMessage.Event(eventSeq, turn, visible.toJson(eventSeq, turn)));
        }
    }

    /** 该座位能不能看见这张牌的牌面；看不见就换成一条匿名事件。 */
    private GameEvent redact(GameEvent event, int seat) {
        boolean hiddenKind = event.type() == GameEvent.Type.DRAW || event.type() == GameEvent.Type.BURN;
        if (!hiddenKind || event.card() == null
                || event.actor() != MatchReferee.foeState(session, seat)) {
            return event;
        }
        String text = event.type() == GameEvent.Type.DRAW ? HIDDEN_DRAW : HIDDEN_BURN;
        return new GameEvent(event.type(), event.actor(), null, null, null, null, 0, text);
    }

    private void send(int seat, NetMessage message) {
        if (transports[seat] != null) {
            transports[seat].send(MessageCodec.encode(message));
        }
    }

    /** 非法动作：回执给当事人 + 记在服务端账上（验收要求「被拒并记录」）。 */
    private void reject(int seat, String code, String message) {
        rejections.add("座位 " + seat + " 被拒 [" + code + "] " + message);
        send(seat, new NetMessage.Failure(code, message));
    }

    private static void checkSeat(int seat) {
        if (seat < 0 || seat >= SEATS) {
            throw new IllegalArgumentException("座位号越界：" + seat + "，合法范围 0.." + (SEATS - 1));
        }
    }
}
