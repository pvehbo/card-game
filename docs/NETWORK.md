# 联机对战（v2.0 · M0–M3 预览）

> 目标：**最小可玩**——两个客户端连同一个服务端，用同一份引擎重算，打完一整局双方状态完全一致。
> 当前状态：M0（引擎可嵌入化）→ M1（协议/编解码）→ M2（房间服务端）→ M3（客户端接线）全部落地，
> `mvn -o verify` 172 用例全绿；M4（断线重连/超时判负）与 M5（账号/ELO）**尚未开工**。

## 1. 一分钟跑起来

```bash
# ① 编译（服务端与客户端是同一份 jar）
mvn -o -DskipTests package

# ② 起服务端（端口可省，默认 7788；第二个参数是 seed，可省）
java -cp target/classes org.example.card.server.RoomServer 7788 42
#   → 房间已开：端口 7788，seed=42，等两位玩家入座……

# ③ 两端各起一个客户端，点工具栏「联机」
mvn javafx:run            # 机器 A
mvn javafx:run            # 机器 B
#   地址栏默认 127.0.0.1:7788；换机器就把 host 改成服务端 IP
#   昵称可用 -Dnet.name=小明 预设，地址可用 -Dnet.room=192.168.1.5:7788 预设
```

两位玩家都入座后服务端自动开局：**固定标准牌堆**（自定义牌组仍是单机专属）、双方拿到同一 seed、
随机先手。对局中所有操作都发给服务端，服务端用同一套 `GameEngine` 重算后把**被接受的指令**和
**事件流**广播回来，客户端只负责重放。

- 服务端启动参数：`args[0]` = 端口（默认 `RoomServer.DEFAULT_PORT = 7788`），`args[1]` = seed（默认 `System.nanoTime()`）。
- 客户端昵称不能相同：动作是按名字归属的，同名会把指令归错座位（默认昵称是 `玩家####` 随机四位）。
- 对局结束后服务端打印「对局结束，共 N 步，被拒 M 次」并退出。

## 2. 为什么是指令同步

```
客户端（只是手柄）                服务端（唯一真相）
   │  ACTION {PLAY|ATTACK|END_TURN}  ──▶  同一套 GameEngine + MatchReferee
   │  ◀── SYNC（被接受的指令回声）        非法动作 → ERROR，只记不结算
   │  ◀── EVENT（GameEvent v2 JSON）
```

- 客户端**不自己判规则**：本地 `MatchReferee` 只按收到的 SYNC 顺序重放同一条指令，
  所以「客户端状态 = 服务端接受过的指令序列的函数」，两端不可能分叉。
- 反作弊天然成立：服务端用同 jar 同代码重算，客户端算出来的任何东西都不被信任。
- 这是 A6「AI 与玩家同权」架构的 payoff：引擎本来就只吃指令、吐事件，不需要为联机改造。

## 3. 协议（`net/Protocol.java`，`VERSION = 1`）

传输：**裸 TCP + 一行一条 JSON**（UTF-8，`\n` 分隔，`Socket.setTcpNoDelay(true)`）。
编解码：`net/MessageCodec`，信封是 `sealed interface NetMessage`（少写一个 case 就编译不过）。

| kind | 方向 | 字段 | 说明 |
|---|---|---|---|
| `JOIN` | C→S | `protocolVersion, name, deckId` | 入座；`deckId` 非空即被拒（联机只打标准牌堆） |
| `START` | S→双端 | `protocolVersion, seed, youSeat, firstSeat, you, opponent, aiLevel` | 开局；**seed 是权威事实**，两端各自从零重放 |
| `ACTION` | C→S | `seq, actor, action, cardIndex, fieldIndex, targetIndex` | 玩家意图；`action ∈ {PLAY, ATTACK, END_TURN}`，`targetIndex = -1` 表示打脸 |
| `SYNC` | S→双端 | `seq, turn, actor, action, cardIndex, fieldIndex, targetIndex` | **被接受**的指令回声，客户端据此重放 |
| `EVENT` | S→双端 | `seq, turn, eventJson` | 一条结算事件（`GameEvent.toJson` 的 v2 原文，嵌套对象） |
| `PING` / `PONG` | 双向 | `timestamp` | 心跳，仅用于算 RTT |
| `ERROR` | S→C | `code, message` | 拒绝回执，`message` 面向玩家可读 |

错误码：`VERSION_MISMATCH` / `BAD_MESSAGE` / `ROOM_FULL` / `NOT_YOUR_TURN` / `ILLEGAL_MOVE` / `GAME_OVER`。

两个容易踩的点：

1. **座位视角**：`youSeat` 决定本地把哪一侧当「我」。发牌按座位顺序，摆反了两人的起手牌会互换，
   从第一回合起状态就与服务端不同。界面靠 `BoardViewModel.setLocalSeat(seat)` 切换 `me()/foe()`。
2. **抽牌是暗的**：服务端按座位裁剪事件——对手的 `DRAW`/`BURN` 事件被抹掉牌面、文案换成
   「对手抽了一张牌」；服务端自己的日志保留完整牌面（审计看得见，网络上看不见）。

## 4. 代码地图

| 位置 | 职责 |
|---|---|
| `src/main/java/org/example/card/net/Json.java` | 手写极小 JSON 读写器（不引第三方依赖） |
| `.../net/NetMessage.java` | sealed 信封：`Join / Start / Action / Sync / Event / Ping / Pong / Failure` |
| `.../net/MessageCodec.java` | `encode / decode / peekKind` |
| `.../net/Protocol.java` | 版本号、kind、动作、错误码、`FACE = -1` |
| `.../server/GameRoom.java` | 房间唯一输入口 `handle(seat, wire)`：解析 → 校验 → 结算 → 广播；`rejections()` 记在账上 |
| `.../server/RoomServer.java` | TCP 接受循环、按座位绑 `Transport`、`main` 手动开房 |
| `.../client/RoomClient.java` | 客户端：连服务端、发指令、按 SYNC 重放、收事件、`divergences()` 记录分叉 |
| `.../service/MatchReferee.java` | 座位无关的裁判器：`apply(seat, action)` + `activeSeat()`，服务端与客户端共用 |
| `.../ui/CardGameApp.java` | 工具栏「联机」按钮、地址对话框、`adoptNetworkGame / networkChanged / sendNetworkAttack` |

界面接线要点（M3c）：

- 入站报文整体派发到 JavaFX 线程（`RoomClient.setDispatcher(Platform::runLater)`），
  于是「重放 → 引擎事件 → 动画/音效」全在 UI 线程，订阅手柄可以直接碰控件。
- 联机局**不挂本地 AI 导演**（不调 `director.attach`），也不写单机存档/回放/战绩。
- 联机时「构筑」只提示不生效（固定标准牌堆），「读档」「回放」被挡下。

## 5. 测试覆盖

| 用例 | 覆盖 |
|---|---|
| `net/ProtocolCodecTest`（12） | 每种报文的 round-trip、黄金样例字节格式、大 seed、v1 事件兼容 |
| `server/GameRoomTest`（18） | 入座/开局广播、镜像座位、指令校验与拒绝记录、暗牌裁剪、终局广播 |
| `server/RoomServerTest`（3） | 真 socket：两端入座、第三位被拒 `ROOM_FULL`、端口复用 |
| `client/RoomClientTest`（2） | **两个客户端 + 真 socket 打完一整局，重放状态与服务端三次比对一致** |
| `ui/viewmodel/BoardViewModelTest`（4） | 座位映射：`setLocalSeat(1)` 后 `me()` 指向会话的 `ai` 槽 |

## 6. 已知限制 / 待办（M4–M5）

- **没有断线重连**：连接断了即出局，重连要等 M4（重放事件流快照恢复）。
- **没有超时判负 / 逃跑判胜**：一方关窗口后对局停在原处。
- **不是 Spring Boot + WebSocket**：计划里推荐过 Spring Boot 3 + STOMP，M1–M3 先用纯 Java SE + 裸 TCP 打通
  「协议 / 房间 / 双端一致」三件事，服务端只依赖 JDK；换成 WebSocket 时协议与 `GameRoom` 逻辑可原样保留，
  只替换 `Transport` 这一层适配。
- **无账号 / ELO**（M5 可砍，游客随机昵称已够用）。
- 一局一房：服务端同时只服务一个房间（`RoomServer` 一次 `accept` 两位）。
