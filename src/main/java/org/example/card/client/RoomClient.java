package org.example.card.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.example.card.ai.AiLevel;
import org.example.card.data.CardDatabase;
import org.example.card.engine.GameEngine;
import org.example.card.engine.GameSession;
import org.example.card.model.PlayerState;
import org.example.card.net.MessageCodec;
import org.example.card.net.NetMessage;
import org.example.card.net.Protocol;
import org.example.card.service.MatchReferee;

/**
 * 联机客户端（M3）：一个 socket、一个后台读线程、一份<b>本地影子对局</b>。
 *
 * <h2>客户端不算账</h2>
 * 所有判断都在服务端。这里只做两件事：把玩家的下标意图发上去；把服务端接受过的指令
 * （{@link NetMessage.Sync}）按原顺序喂给与服务器同一个 {@link MatchReferee}，在本地重放同一盘棋。
 * 因为「同 seed + 同牌堆 + 同指令序列」是逐字节确定的，两端的状态永远一致——除非代码有 bug，
 * 那种情况会被记进 {@link #divergences()} 而不是悄悄画错一张牌。
 *
 * <h2>自己的操作也要等回声</h2>
 * {@link #playCard} 只是把意图发上去；牌真的打出去，是在服务端回 SYNC 之后。
 * 局域网里这一点延迟感觉不到，换来的是「客户端状态 = 服务端接受过的指令序列」这条单一真相路径。
 *
 * <h2>线程模型</h2>
 * 读线程（daemon）负责收发与重放，{@link Listener} 的回调都发生在读线程上；
 * 所有状态读取方法都带锁，UI 侧请把回调里的刷新丢回自己的 UI 线程
 * （JavaFX 用 {@code Platform.runLater}）。
 *
 * <p>零第三方依赖。M3 的联机对局只用标准牌堆：START 只带 seed 不带对手牌表，
 * 两端必须各自能构造出同一副牌（自定义牌组仍是 PvE 专属，见 docs）。
 */
public final class RoomClient implements AutoCloseable {

    /** 客户端事件回调；全部在后台读线程上触发。 */
    public interface Listener {

        /** 收到 START：本地影子对局已经建好，可以开始画棋盘了。 */
        default void onStart() {
        }

        /** 状态变了（收到 START 或一条 SYNC），该重绘了。 */
        default void onChanged() {
        }

        /** 服务端拒绝了一条指令（越权 / 非法 / 对局结束）。 */
        default void onRefused(NetMessage.Failure failure) {
        }

        /** 连接断了或被关闭：reason 面向玩家可读。 */
        default void onClosed(String reason) {
        }
    }

    private final String host;
    private final int port;
    private final String playerName;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread pump;
    private volatile boolean running;
    private volatile String closeReason;

    private final Object writeLock = new Object();
    private int sendSeq;

    private final List<String> engineLog = new ArrayList<>();
    private final List<NetMessage.Failure> refusals = new ArrayList<>();
    private final List<NetMessage.Event> serverEvents = new ArrayList<>();
    private final List<String> divergences = new ArrayList<>();

    private Listener listener = new Listener() {
    };

    /**
     * 入站报文的执行线程：默认就地执行（读线程），测试与无界面用法保持同步语义；
     * 界面用法传 {@code Platform::runLater}，让「重放 → 引擎事件 → 动画/音效」
     * 整条链路都落在 JavaFX 线程上，避免跨线程碰控件。
     */
    private volatile java.util.function.Consumer<Runnable> dispatcher = Runnable::run;

    private GameSession session;
    private GameEngine engine;
    private MatchReferee referee;
    private String myName;
    private String opponentName;
    private String[] seatNames = new String[2];
    private int youSeat;
    private int firstSeat;
    private long seed;

    public RoomClient(String host, int port, String playerName) {
        this.host = host;
        this.port = port;
        this.playerName = (playerName == null || playerName.isBlank()) ? "玩家" : playerName.trim();
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? new Listener() {
        } : listener;
    }

    /** 设置入站处理所在的线程（界面传 {@code Platform::runLater}；传 null 恢复就地执行）。 */
    public void setDispatcher(java.util.function.Consumer<Runnable> dispatcher) {
        this.dispatcher = dispatcher == null ? Runnable::run : dispatcher;
    }

    /** 连上并请求入座。返回时只是「已发出 JOIN」，开局要等 {@link Listener#onStart()}。 */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        running = true;
        pump = new Thread(this::pumpLoop, "room-client-" + playerName);
        pump.setDaemon(true);
        pump.start();
        send(new NetMessage.Join(Protocol.VERSION, playerName, null));
    }

    // ============ 玩家意图（只是「请求」，不算数） ============

    public void playCard(int handIndex) {
        sendAction(NetMessage.Action.play(nextSeq(), playerName, handIndex));
    }

    public void attack(int fieldIndex, int targetIndex) {
        sendAction(new NetMessage.Action(nextSeq(), playerName, Protocol.MOVE_ATTACK,
                Protocol.UNUSED, fieldIndex, targetIndex));
    }

    public void endTurn() {
        sendAction(NetMessage.Action.endTurn(nextSeq(), playerName));
    }

    public void ping() {
        send(new NetMessage.Ping((int) System.currentTimeMillis()));
    }

    // ============ 本地状态（重放出来的影子对局） ============

    /** 本地影子对局；还没收到 START 时为 null。 */
    public synchronized GameSession session() {
        return session;
    }

    /** 本地引擎：UI 可以订阅它的事件总线做动画（回调线程由 {@link #setDispatcher} 决定，界面传 Platform::runLater 即为 UI 线程）。 */
    public synchronized GameEngine engine() {
        return engine;
    }

    public synchronized MatchReferee referee() {
        return referee;
    }

    /** 我的玩家状态（按服务端给我的座位号取，不是本地座位 0）。 */
    public synchronized PlayerState me() {
        return session == null ? null : MatchReferee.seatState(session, youSeat);
    }

    /** 对手的玩家状态。 */
    public synchronized PlayerState opponent() {
        return session == null ? null : MatchReferee.foeState(session, youSeat);
    }

    public synchronized String myName() {
        return myName;
    }

    public synchronized String opponentName() {
        return opponentName;
    }

    /** 我在服务端的座位号（0 或 1）。 */
    public synchronized int youSeat() {
        return youSeat;
    }

    /** 先手是服务端的几号座位。 */
    public synchronized int firstSeat() {
        return firstSeat;
    }

    /** 我先手吗（= 我先手方是几号座位这件事，和我自己的座位号一致）。 */
    public synchronized boolean playerFirst() {
        return firstSeat == youSeat;
    }

    public synchronized long seed() {
        return seed;
    }

    public synchronized boolean started() {
        return session != null;
    }

    public synchronized boolean finished() {
        return referee != null && referee.finished();
    }

    /** 轮到我了吗。 */
    public synchronized boolean myTurn() {
        return referee != null && !referee.finished() && referee.activeSeat() == youSeat;
    }

    public synchronized int turn() {
        return engine == null ? 0 : engine.getTurn();
    }

    /** 本地引擎打出的日志（与本地单机模式同一套观感）。 */
    public synchronized List<String> log() {
        return List.copyOf(engineLog);
    }

    /** 服务端拒绝过的指令，按发生顺序。 */
    public synchronized List<NetMessage.Failure> refusals() {
        return List.copyOf(refusals);
    }

    /** 服务端广播的事件流：本地重放不依赖它，留作线上记录与将来断线补状态。 */
    public synchronized List<NetMessage.Event> serverEvents() {
        return List.copyOf(serverEvents);
    }

    /**
     * 本地重放与服务端不一致的记录。正常情况下永远是空的；
     * 一旦非空，说明两端代码在某个边界上分歧了——这是需要立刻修的 bug，不是可以忽略的噪音。
     */
    public synchronized List<String> divergences() {
        return List.copyOf(divergences);
    }

    public String closeReason() {
        return closeReason;
    }

    public boolean isClosed() {
        return !running;
    }

    @Override
    public void close() {
        closed("本地主动断开");
    }

    // ============ 内部 ============

    private int nextSeq() {
        synchronized (writeLock) {
            return ++sendSeq;
        }
    }

    /** 把玩家意图发上去——它只是「请求」，服务端接受并回 SYNC 之后才真正落地。 */
    private void sendAction(NetMessage.Action action) {
        send(action);
    }

    private void send(NetMessage message) {
        String wire = MessageCodec.encode(message);
        synchronized (writeLock) {
            if (writer == null) {
                return;
            }
            try {
                writer.write(wire);
                writer.write('\n');
                writer.flush();
            } catch (IOException ex) {
                if (running) {
                    closed("发送失败：" + ex.getMessage());
                }
            }
        }
    }

    private void pumpLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                String message = line;
                dispatcher.accept(() -> receive(message));
            }
            if (running) {
                closed("服务端关闭了连接");
            }
        } catch (IOException ex) {
            if (running) {
                closed("连接中断：" + ex.getMessage());
            }
        }
    }

    private void receive(String line) {
        NetMessage message;
        try {
            message = MessageCodec.decode(line);
        } catch (RuntimeException ex) {
            synchronized (this) {
                divergences.add("无法解析服务端报文：" + ex.getMessage());
            }
            return;
        }
        NetMessage.Failure refused = null;
        boolean changed = false;
        synchronized (this) {
            if (message instanceof NetMessage.Start start) {
                beginGame(start);
                changed = true;
            } else if (message instanceof NetMessage.Sync sync) {
                replay(sync);
                changed = true;
            } else if (message instanceof NetMessage.Event event) {
                serverEvents.add(event);
            } else if (message instanceof NetMessage.Failure failure) {
                refusals.add(failure);
                refused = failure;
            }
        }
        if (changed) {
            listener.onChanged();
        }
        if (refused != null) {
            listener.onRefused(refused);
        }
    }

    /**
     * START 到达：用同一个 seed 把<b>服务端那一局原样摆一遍</b>——座位顺序也必须一致，
     * 因为发牌是按座位顺序发的：摆反了，两个人拿到的起手牌会互换，重放出的状态从第一回合起就对不上。
     */
    private void beginGame(NetMessage.Start start) {
        myName = start.you();
        opponentName = start.opponent();
        seed = start.seed();
        youSeat = start.youSeat() == 1 ? 1 : 0;
        firstSeat = start.firstSeat() == 1 ? 1 : 0;
        seatNames = new String[2];
        seatNames[youSeat] = myName;
        seatNames[1 - youSeat] = opponentName;
        // M3 联机只用标准牌堆：两端各拿一副标准牌 + 同一个 seed，洗出来的顺序才一致。
        session = GameSession.newVersus(new Random(seed),
                CardDatabase.standardDeck(), CardDatabase.standardDeck(), seatNames[0], seatNames[1]);
        engine = new GameEngine(AiLevel.NORMAL.newAi());
        engine.eventBus().onAny(event -> engineLog.add(event.message()));
        referee = new MatchReferee(session, engine, engineLog::add, firstSeat);
        // 裁判器按「座位 0 视角」维护 yourTurn；界面这里只关心「轮到我了吗」，所以按我的座位改写一次
        session.setYourTurn(myTurn());
    }

    /** 把一条权威指令重放到影子对局上（座位号与服务器同序，直接按 actor 名字归位）。 */
    private void replay(NetMessage.Sync sync) {
        if (referee == null) {
            divergences.add("还没开局就收到 SYNC：" + sync.action());
            return;
        }
        int seat = sync.actor() != null && sync.actor().equals(seatNames[0]) ? 0 : 1;
        Optional<MatchReferee.Refusal> refusal = referee.apply(seat, sync.toAction());
        if (refusal.isPresent()) {
            divergences.add("重放被拒（本地与服务端已经走散）：" + sync.action() + " → " + refusal.get().message());
        }
        session.setYourTurn(referee.activeSeat() == youSeat);
    }

    private void closed(String reason) {
        if (!running) {
            return;
        }
        running = false;
        closeReason = reason;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // 已经断了，关不掉也不用管
        }
        listener.onClosed(reason);
    }
}
