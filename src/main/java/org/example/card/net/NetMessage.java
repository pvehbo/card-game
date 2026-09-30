package org.example.card.net;

/**
 * 联机信封（M1）：{@code JOIN / START / ACTION / SYNC / EVENT / PING / PONG / ERROR} 八种，
 * 一一对应 {@link Protocol} 常量。
 *
 * <p>用 sealed 接口而不是弱类型 Map，是为了让「服务端少写一个 case」变成编译错误。
 * 下标类字段一律用 int 而非卡名：名字可以重复，下标不会，服务端校验也只需比大小。
 */
public sealed interface NetMessage {

    /** JSON 的 "kind" 判别字段。 */
    String kind();

    /** 客户端 → 服务端：请求入座。deckId 为 null 表示使用默认牌组。 */
    record Join(int protocolVersion, String name, String deckId) implements NetMessage {
        @Override
        public String kind() {
            return Protocol.JOIN;
        }
    }

    /**
     * 服务端 → 双端：开局。
     * seed 是权威事实——两端各自用它从零重放，事件流才对得上。
     *
     * <p>{@code you} / {@code opponent} 是<b>收件人视角</b>的名字（方便直接显示）；
     * {@code youSeat} 与 {@code firstSeat} 则是<b>服务端座位</b>的权威事实：我是几号座位、几号座位先手。
     * 客户端必须按 {@code youSeat} 把本地棋局摆成和服务端同序的一局——发牌是按座位顺序发的，
     * 摆反了两个人拿到的起手牌会互换，重放出的状态从第一回合起就与服务器不同。
     */
    record Start(int protocolVersion, long seed, int youSeat, int firstSeat,
                 String you, String opponent, String aiLevel) implements NetMessage {
        @Override
        public String kind() {
            return Protocol.START;
        }
    }

    /**
     * 客户端 → 服务端：一个玩家意图。
     *
     * <p>只有 {@code action} 对应的下标字段有意义，其余为 {@link Protocol#UNUSED}：
     * PLAY 用 cardIndex（手牌下标）；ATTACK 用 fieldIndex（己方场上下标）+ targetIndex
     * （对方场上下标，{@link Protocol#FACE} 为打脸）；END_TURN 全不用。
     * seq 由客户端自增，服务端只用来回执与排序，不信任它做规则判断。
     */
    record Action(int seq, String actor, String action,
                  int cardIndex, int fieldIndex, int targetIndex) implements NetMessage {
        @Override
        public String kind() {
            return Protocol.ACTION;
        }

        /** 便捷构造：出牌。 */
        public static Action play(int seq, String actor, int handIndex) {
            return new Action(seq, actor, Protocol.MOVE_PLAY, handIndex, Protocol.UNUSED, Protocol.UNUSED);
        }

        /** 便捷构造：结束回合。 */
        public static Action endTurn(int seq, String actor) {
            return new Action(seq, actor, Protocol.MOVE_END_TURN, Protocol.UNUSED, Protocol.UNUSED, Protocol.UNUSED);
        }
    }

    /**
     * 服务端 → 双端：一条**被接受**的指令回声（M3）。
     *
     * <p>客户端拿到它，用同一份引擎在本地重放；自己的操作同样要等这条回声才落地，
     * 于是「客户端状态」永远只有一条真相路径：服务端接受过的指令序列。
     * 字段与 {@link Action} 同形（去掉了客户端自增的 seq 语义），{@link #toAction()} 直接喂给裁判器。
     *
     * <p>被拒的指令不会出现在 SYNC 里——客户端只有先收到 {@link Failure} 才知道自己想错了。
     */
    record Sync(int seq, int turn, String actor, String action,
                int cardIndex, int fieldIndex, int targetIndex) implements NetMessage {
        @Override
        public String kind() {
            return Protocol.SYNC;
        }

        /** 转成可重放的玩家意图：seq 用服务端的权威序号。 */
        public Action toAction() {
            return new Action(seq, actor, action, cardIndex, fieldIndex, targetIndex);
        }
    }

    /**
     * 服务端 → 客户端：一条结算事件。
     *
     * <p>{@code eventJson} 是 {@code GameEvent} 的 v2 JSON 原文（嵌套对象，不是转义字符串），
     * 客户端直接丢给 {@code GameEvent.fromJson} 即可；net 层刻意不依赖 event 层。
     */
    record Event(int seq, int turn, String eventJson) implements NetMessage {
        @Override
        public String kind() {
            return Protocol.EVENT;
        }
    }

    /** 双向心跳；timestamp 为发送方毫秒时钟，仅用于算 RTT。 */
    record Ping(int timestamp) implements NetMessage {
        @Override
        public String kind() {
            return Protocol.PING;
        }
    }

    record Pong(int timestamp) implements NetMessage {
        @Override
        public String kind() {
            return Protocol.PONG;
        }
    }

    /** 服务端拒绝：code 取自 {@link Protocol} 的 ERR_*，message 面向玩家可读。 */
    record Failure(String code, String message) implements NetMessage {
        @Override
        public String kind() {
            return Protocol.ERROR;
        }
    }
}
