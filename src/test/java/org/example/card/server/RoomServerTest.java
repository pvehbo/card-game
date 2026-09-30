package org.example.card.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.example.card.net.MessageCodec;
import org.example.card.net.NetMessage;
import org.example.card.net.Protocol;
import org.junit.jupiter.api.Test;

/**
 * M2 联网验收：真 socket 往返（一行一条 JSON 的帧）。
 *
 * <p>坐在同一台机器上，但走的确实是 TCP：连接顺序决定座位，帧就是 {@link MessageCodec} 的正文。
 * 读操作都带 5 秒超时——不来的消息要让测试明确失败，而不是把构建挂死。
 */
class RoomServerTest {

    private static final int READ_TIMEOUT_MS = 5000;

    @Test
    void twoTcpClientsGetOneSeedAndCanHandOverTheTurn() throws Exception {
        try (RoomServer server = new RoomServer(0, 99L)) {
            server.start();
            try (Socket first = connect(server.port()); Socket second = connect(server.port())) {
                Client seat0 = new Client(first);
                Client seat1 = new Client(second);
                seat0.send(new NetMessage.Join(Protocol.VERSION, "泰", null));
                seat1.send(new NetMessage.Join(Protocol.VERSION, "坦", null));

                NetMessage.Start start0 = assertInstanceOf(NetMessage.Start.class, seat0.next());
                NetMessage.Start start1 = assertInstanceOf(NetMessage.Start.class, seat1.next());

                assertEquals(99L, start0.seed(), "两端必须拿到同一个 seed");
                assertEquals(start0.seed(), start1.seed());
                assertEquals(0, start0.youSeat(), "座位 0 收到的 youSeat 就是 0");
                assertEquals(1, start1.youSeat(), "座位 1 收到的 youSeat 就是 1");
                assertEquals(start0.firstSeat(), start1.firstSeat(), "先手座位是全局事实，两端相同");
                assertEquals("泰", start0.you());
                assertNotEquals(start0.you(), start0.opponent());

                // 先手方换手：先手座位是 0 还是 1 由 seed 决定，测试不假设，直接问 START。
                Client mover = start0.firstSeat() == 0 ? seat0 : seat1;
                Client watcher = start0.firstSeat() == 0 ? seat1 : seat0;
                mover.send(NetMessage.Action.endTurn(1, start0.you()));

                // 开局本身就是一次 TURN_START（回合 1），所以要一直读到回合 2 的那条
                NetMessage.Event turnStart = watcher.nextTurnStart(2);
                assertTrue(turnStart.eventJson().contains("TURN_START"));
            }
        }
    }

    @Test
    void actingOutOfTurnIsRefusedOverTheWire() throws Exception {
        try (RoomServer server = new RoomServer(0, 7L)) {
            server.start();
            try (Socket first = connect(server.port()); Socket second = connect(server.port())) {
                Client seat0 = new Client(first);
                Client seat1 = new Client(second);
                seat0.send(new NetMessage.Join(Protocol.VERSION, "甲", null));
                seat1.send(new NetMessage.Join(Protocol.VERSION, "乙", null));

                NetMessage.Start start0 = assertInstanceOf(NetMessage.Start.class, seat0.next());
                assertInstanceOf(NetMessage.Start.class, seat1.next());

                Client idler = start0.firstSeat() == 0 ? seat1 : seat0;
                Client active = start0.firstSeat() == 0 ? seat0 : seat1;

                // 越权指令：座位判定先于卡牌合法性，所以就算下标是 0 也必须先被「没轮到你」拦下。
                idler.send(NetMessage.Action.play(2, "冒充先手", 0));
                NetMessage.Failure refusal = idler.nextFailure();
                assertEquals(Protocol.ERR_NOT_YOUR_TURN, refusal.code());
                assertTrue(server.room().rejections().stream().anyMatch(r -> r.contains("NOT_YOUR_TURN")),
                        server.room().rejections().toString());

                // 轮到的人真的能通过网络推进对局
                active.send(NetMessage.Action.endTurn(3, start0.you()));
                assertNotNull(active.nextTurnStart(2));
            }
        }
    }

    @Test
    void aThirdConnectionIsRefusedWithoutJoining() throws Exception {
        try (RoomServer server = new RoomServer(0, 1L)) {
            server.start();
            try (Socket first = connect(server.port());
                 Socket second = connect(server.port());
                 Socket third = connect(server.port())) {
                Client extra = new Client(third);
                NetMessage.Failure refusal = assertInstanceOf(NetMessage.Failure.class, extra.next());
                assertEquals(Protocol.ERR_ROOM_FULL, refusal.code());
                assertEquals(GameRoom.State.WAITING, server.room().state(),
                        "多出来的连接不该把房间推进对局状态");
            }
        }
    }

    private static Socket connect(int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), READ_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        socket.setTcpNoDelay(true);
        return socket;
    }

    /** 一个 TCP 客户端：发一行 JSON、收一行 JSON。 */
    private static final class Client {

        private final BufferedReader in;
        private final BufferedWriter out;

        Client(Socket socket) throws IOException {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void send(NetMessage message) throws IOException {
            out.write(MessageCodec.encode(message));
            out.write('\n');
            out.flush();
        }

        NetMessage next() throws IOException {
            String line = in.readLine();
            assertNotNull(line, "连接被服务端关闭（期待一条消息）");
            return MessageCodec.decode(line);
        }

        /** 一直读到「指定回合号的 TURN_START」为止，最多读 60 条（开局那次是回合 1）。 */
        NetMessage.Event nextTurnStart(int turn) throws IOException {
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                NetMessage message = next();
                if (message instanceof NetMessage.Event event
                        && event.eventJson().contains("TURN_START")
                        && event.turn() == turn) {
                    return event;
                }
                seen.add(message.kind() + " " + message);
            }
            throw new AssertionError("没有等到回合 " + turn + " 的 TURN_START，只看到：" + seen);
        }

        /** 一直读到一条 Failure 为止（之前的广播事件都属于「先手开局」）。 */
        NetMessage.Failure nextFailure() throws IOException {
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                NetMessage message = next();
                if (message instanceof NetMessage.Failure failure) {
                    return failure;
                }
                seen.add(message.kind());
            }
            throw new AssertionError("没有等到 Failure，只看到：" + seen);
        }
    }
}
