package org.example.card.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.card.net.MessageCodec;
import org.example.card.net.NetMessage;
import org.example.card.net.Protocol;

/**
 * 房间的 TCP 外壳（M2）：一行一条 JSON，谁先连上谁先坐。
 *
 * <p>刻意只有薄薄一层：这里不碰任何玩法，只做「把 {@link GameRoom} 的出口接到 socket 上」。
 * 之所以手写 socket 而不上 Spring Boot：{@code server} 包要和 {@code engine} 一起打进同一个 jar
 * （服务端复用同一份规则），引框架会把客户端也拖上依赖；将来真要部署，
 * 这一层换成 WebSocket/STOMP 的适配器即可，房间逻辑一行不用改。
 *
 * <p>帧格式：UTF-8 + {@code \n} 结尾。JSON 里的换行已被 {@code Json.escape} 转成 {@code \n}，
 * 所以一条报文永远只占一行，读端用 readLine 就够，不需要长度前缀。
 */
public final class RoomServer implements AutoCloseable {

    /** 默认端口（M3 客户端默认连它）。 */
    public static final int DEFAULT_PORT = 7788;

    private final GameRoom room;
    private final ServerSocket serverSocket;
    private final AtomicInteger connections = new AtomicInteger();
    private final List<Socket> sockets = new ArrayList<>();
    private final List<Thread> workers = new ArrayList<>();
    private volatile boolean running = true;
    private Thread acceptThread;

    public RoomServer(int port, long seed) throws IOException {
        this(port, GameRoom.standard(seed));
    }

    public RoomServer(int port, GameRoom room) throws IOException {
        this.room = room;
        this.serverSocket = new ServerSocket(port);
    }

    /** 实际监听端口（构造时传 0 即为系统分配，测试用它避免端口冲突）。 */
    public int port() {
        return serverSocket.getLocalPort();
    }

    public GameRoom room() {
        return room;
    }

    /** 启动接受循环（后台线程），立刻返回。 */
    public void start() {
        acceptThread = new Thread(this::acceptLoop, "room-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException ex) {
                if (running) {
                    System.err.println("[房间] 接受连接失败：" + ex);
                }
                return;
            }
            int seat = connections.getAndIncrement();
            if (seat >= GameRoom.SEATS) {
                refuseExtra(socket);
                continue;
            }
            try {
                socket.setTcpNoDelay(true);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                synchronized (sockets) {
                    sockets.add(socket);
                }
                room.attach(seat, wire -> writeTo(out, wire));
                Thread worker = new Thread(() -> readLoop(seat, socket, in), "room-seat-" + seat);
                worker.setDaemon(true);
                synchronized (workers) {
                    workers.add(worker);
                }
                worker.start();
            } catch (IOException ex) {
                System.err.println("[房间] 座位 " + seat + " 初始化失败：" + ex);
                closeQuietly(socket);
            }
        }
    }

    private void readLoop(int seat, Socket socket, BufferedReader in) {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                room.handle(seat, line);
            }
        } catch (IOException ex) {
            if (running) {
                System.err.println("[房间] 座位 " + seat + " 读失败：" + ex);
            }
        } finally {
            closeQuietly(socket);
        }
    }

    private void writeTo(BufferedWriter out, String wire) {
        try {
            synchronized (out) {
                out.write(wire);
                out.write('\n');
                out.flush();
            }
        } catch (IOException ex) {
            // 对端正常退出（关掉窗口 / 测试收尾）会让写失败，这不算异常情况。
            if (running) {
                System.err.println("[房间] 写失败：" + ex);
            }
        }
    }

    private void refuseExtra(Socket socket) {
        try {
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            out.write(MessageCodec.encode(new NetMessage.Failure(Protocol.ERR_ROOM_FULL, "房间已满（1v1）")));
            out.write('\n');
            out.flush();
        } catch (IOException ignored) {
            // 对方已经断了就算了
        } finally {
            closeQuietly(socket);
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 关不上不影响别人
        }
    }

    @Override
    public void close() {
        running = false;
        closeQuietly(serverSocket);
        synchronized (sockets) {
            sockets.forEach(RoomServer::closeQuietly);
        }
    }

    private static void closeQuietly(ServerSocket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 同上
        }
    }

    /**
     * 手动开一局（M3 之前的联机冒烟）：
     * {@code mvn -o exec:java} 不便，直接用 {@code java -cp target/classes:... org.example.card.server.RoomServer 7788 42}。
     */
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : System.nanoTime();
        RoomServer server = new RoomServer(port, seed);
        server.start();
        System.out.println("房间已开：端口 " + server.port() + "，seed=" + seed + "，等两位玩家入座……");
        while (server.room().state() != GameRoom.State.FINISHED) {
            Thread.sleep(500);
        }
        System.out.println("对局结束，共 " + server.room().actionCount() + " 步，被拒 "
                + server.room().rejections().size() + " 次");
        server.close();
    }
}
