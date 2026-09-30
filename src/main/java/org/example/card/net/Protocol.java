package org.example.card.net;

/**
 * v2.0 联机协议常量（M1）。
 *
 * <p>客户端只是「手柄」：它把玩家意图编码成 {@link NetMessage.Action} 发给服务端，
 * 服务端用同一份引擎重算合法性并结算，再把 {@link NetMessage.Event} 广播回来。
 * 因此这里只定义「信封」，不含任何玩法规则——规则永远只有 {@code engine} 一份。
 */
public final class Protocol {

    /** 协议版本：握手时双方比对，不一致直接 {@link NetMessage.Failure} 拒绝。 */
    public static final int VERSION = 1;

    // ---- 信封种类（JSON 的 "kind" 字段） ----

    /** 客户端 → 服务端：请求入座。 */
    public static final String JOIN = "JOIN";
    /** 服务端 → 双端：开局，附带随机种子与先手方（双方用它各起一局，结果必须一致）。 */
    public static final String START = "START";
    /** 客户端 → 服务端：一个玩家意图。 */
    public static final String ACTION = "ACTION";
    /** 服务端 → 双端：一条**被接受**的指令回声（M3），客户端据此在本地重放同一局棋。 */
    public static final String SYNC = "SYNC";
    /** 服务端 → 客户端：一条结算事件（按 seq 严格递增）。 */
    public static final String EVENT = "EVENT";
    /** 双向：心跳。 */
    public static final String PING = "PING";
    /** 双向：心跳应答。 */
    public static final String PONG = "PONG";
    /** 服务端 → 客户端：拒绝（非法动作 / 越权 / 版本不符）。 */
    public static final String ERROR = "ERROR";

    // ---- 玩家意图动作（Action.action） ----

    /** 打出手牌（手牌下标）。 */
    public static final String MOVE_PLAY = "PLAY";
    /** 随从攻击（己方场上下标 + 对方场上下标；{@link #FACE} 表示打脸）。 */
    public static final String MOVE_ATTACK = "ATTACK";
    /** 结束回合。 */
    public static final String MOVE_END_TURN = "END_TURN";

    // ---- 拒绝码（Failure.code） ----

    public static final String ERR_VERSION = "VERSION_MISMATCH";
    public static final String ERR_BAD_MESSAGE = "BAD_MESSAGE";
    public static final String ERR_ROOM_FULL = "ROOM_FULL";
    public static final String ERR_NOT_YOUR_TURN = "NOT_YOUR_TURN";
    public static final String ERR_ILLEGAL_MOVE = "ILLEGAL_MOVE";
    /** 对局已结束，不再接受任何动作。 */
    public static final String ERR_GAME_OVER = "GAME_OVER";

    /** {@link NetMessage.Start#aiLevel()} 在 PvP 对局里的取值（没有 AI，只是让字段有确切含义）。 */
    public static final String MODE_PVP = "pvp";

    /** 攻击动作的「目标为空」哨兵值：打脸。 */
    public static final int FACE = -1;

    /** 该动作不使用的下标字段统一填这个值，便于日志一眼看出没用到。 */
    public static final int UNUSED = -1;

    private Protocol() {
    }
}
