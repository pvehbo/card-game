package org.example.card.net;

import org.example.card.event.GameEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v2.0 M1「协议与编解码层」验收：
 * 八种信封必须无损往返；v1 事件正文必须照旧可解；未知字段可跳过（协议可向前扩展）。
 */
class ProtocolCodecTest {

    @Test
    void everyMessageKindRoundTrips() {
        NetMessage[] messages = {
                new NetMessage.Join(Protocol.VERSION, "老板", "starter-aggro"),
                new NetMessage.Join(Protocol.VERSION, "游客", null),
                new NetMessage.Start(Protocol.VERSION, 20240501L, 0, 0, "老板", "游客", "HARD"),
                new NetMessage.Start(Protocol.VERSION, -7L, 1, 0, "游客", "老板", "EASY"),
                NetMessage.Action.play(1, "老板", 3),
                new NetMessage.Action(2, "老板", Protocol.MOVE_ATTACK, Protocol.UNUSED, 0, Protocol.FACE),
                NetMessage.Action.endTurn(3, "老板"),
                new NetMessage.Sync(1, 2, "老板", Protocol.MOVE_PLAY, 3, Protocol.UNUSED, Protocol.UNUSED),
                new NetMessage.Sync(2, 2, "游客", Protocol.MOVE_ATTACK, Protocol.UNUSED, 0, Protocol.FACE),
                new NetMessage.Event(9, 4, GameEvent.of(GameEvent.Type.TURN_END, "回合结束").toJson(9, 4)),
                new NetMessage.Ping(1_700_000_000),
                new NetMessage.Pong(1_700_000_011),
                new NetMessage.Failure(Protocol.ERR_ILLEGAL_MOVE, "该随从已不在场上"),
        };
        for (NetMessage original : messages) {
            String wire = MessageCodec.encode(original);
            NetMessage decoded = MessageCodec.decode(wire);
            assertEquals(original, decoded, "往返后必须逐字段相等：" + wire);
            assertEquals(original.kind(), decoded.kind());
        }
    }

    @Test
    void actionConvenienceFactoriesFillUnusedSentinels() {
        NetMessage.Action play = NetMessage.Action.play(5, "老板", 2);
        assertEquals(Protocol.MOVE_PLAY, play.action());
        assertEquals(2, play.cardIndex());
        assertEquals(Protocol.UNUSED, play.fieldIndex());
        assertEquals(Protocol.UNUSED, play.targetIndex());

        NetMessage.Action end = NetMessage.Action.endTurn(6, "老板");
        assertEquals(Protocol.MOVE_END_TURN, end.action());
        assertEquals(Protocol.UNUSED, end.cardIndex());
    }

    /** SYNC 是客户端唯一的真相来源：转成 Action 后必须与原始指令逐字段一致，否则两端会走散。 */
    @Test
    void syncConvertsBackToTheSameAction() {
        NetMessage.Sync sync = new NetMessage.Sync(7, 3, "老板", Protocol.MOVE_ATTACK,
                Protocol.UNUSED, 2, 1);
        String wire = MessageCodec.encode(sync);
        NetMessage.Sync decoded = assertInstanceOf(NetMessage.Sync.class, MessageCodec.decode(wire));

        assertEquals(7, decoded.seq());
        assertEquals(3, decoded.turn());
        assertEquals("老板", decoded.actor());
        assertEquals(sync.toAction(), new NetMessage.Action(7, "老板", Protocol.MOVE_ATTACK,
                Protocol.UNUSED, 2, 1));
        assertEquals("老板", decoded.toAction().actor(), "重放时的 actor 直接取回声，不用自己猜");
    }

    /** 事件正文要内联成嵌套对象，而不是被转义成一坨字符串——联机日志靠肉眼读。 */
    @Test
    void eventPayloadIsInlinedNotEscaped() {
        String eventJson = GameEvent.damage(null, 5, "火球命中英雄").toJson(3, 2);
        String wire = MessageCodec.encode(new NetMessage.Event(3, 2, eventJson));

        assertTrue(wire.contains("\"event\":{"), "事件正文应内联为对象：" + wire);
        assertFalse(wire.contains("\\\"type\\\""), "事件正文不该被二次转义：" + wire);

        NetMessage.Event decoded = assertInstanceOf(NetMessage.Event.class, MessageCodec.decode(wire));
        assertEquals(3, decoded.seq());
        assertEquals(2, decoded.turn());
        GameEvent.JsonData parsed = GameEvent.fromJson(decoded.eventJson());
        assertEquals(GameEvent.Type.DAMAGE, parsed.type());
        assertEquals(5, parsed.amount());
        assertEquals("火球命中英雄", parsed.message());
        assertEquals(3, parsed.seq(), "seq 要一路透到事件正文里");
        assertEquals(2, parsed.turn());
    }

    /** 兼容：对端把事件正文当转义字符串发过来（等价但更省事的写法）也要能解。 */
    @Test
    void eventPayloadAsEscapedStringStillDecodes() {
        String inner = GameEvent.of(GameEvent.Type.BURN, "烧掉：\"火球\"").toJson(1, 1);
        String wire = "{\"kind\":\"EVENT\",\"seq\":1,\"turn\":1,\"event\":" + Json.quote(inner) + "}";
        NetMessage.Event decoded = assertInstanceOf(NetMessage.Event.class, MessageCodec.decode(wire));
        assertEquals(inner, decoded.eventJson());
        assertEquals("烧掉：\"火球\"", GameEvent.fromJson(decoded.eventJson()).message());
    }

    @Test
    void fieldOrderDoesNotMatter() {
        String wire = "{\"action\":\"END_TURN\",\"actor\":\"老板\",\"targetIndex\":-1,"
                + "\"kind\":\"ACTION\",\"seq\":7,\"cardIndex\":-1,\"fieldIndex\":-1}";
        NetMessage.Action decoded = assertInstanceOf(NetMessage.Action.class, MessageCodec.decode(wire));
        assertEquals(7, decoded.seq());
        assertEquals(Protocol.MOVE_END_TURN, decoded.action());
        assertEquals("老板", decoded.actor());
    }

    /** 协议向前扩展的前提：本端不认识的新字段必须跳过而不是报错。 */
    @Test
    void unknownFieldsAreSkippedForForwardCompatibility() {
        String wire = "{\"kind\":\"JOIN\",\"protocol\":1,\"name\":\"老板\",\"deckId\":null,"
                + "\"future\":{\"nested\":[1,2,3],\"deep\":{\"x\":\"}\"}},\"flag\":true}";
        NetMessage.Join decoded = assertInstanceOf(NetMessage.Join.class, MessageCodec.decode(wire));
        assertEquals("老板", decoded.name());
        assertEquals(Protocol.VERSION, decoded.protocolVersion());
    }

    @Test
    void malformedMessagesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode("not json"));
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode("{}"));
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode("{\"kind\":\"NOPE\"}"));
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode("{\"kind\":\"ACTION\"}"));
        // 名字是可缺席的（房间回退成「玩家N」），但类型错了仍然要拦下
        assertNull(assertInstanceOf(NetMessage.Join.class,
                MessageCodec.decode("{\"kind\":\"JOIN\"}")).name());
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode("{\"kind\":\"JOIN\",\"name\":42}"));
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode("{\"kind\":\"PING\""));
    }

    @Test
    void peekKindReadsKindWithoutFullParse() {
        assertEquals(Protocol.JOIN, MessageCodec.peekKind("{\"kind\":\"JOIN\",\"name\":\"a\"}"));
        assertEquals(Protocol.EVENT,
                MessageCodec.peekKind("{\"seq\":1,\"event\":{\"type\":\"BURN\"},\"kind\":\"EVENT\"}"));
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.peekKind("{\"name\":\"a\"}"));
    }

    @Test
    void specialCharactersSurviveTheWire() {
        String nasty = "换行\n制表\t引号\"反斜杠\\结束";
        NetMessage.Failure failure = new NetMessage.Failure(Protocol.ERR_BAD_MESSAGE, nasty);
        NetMessage.Failure decoded = assertInstanceOf(NetMessage.Failure.class,
                MessageCodec.decode(MessageCodec.encode(failure)));
        assertEquals(nasty, decoded.message());
    }

    /** 线上格式一旦发出去就要稳定，用一条黄金样例钉住字段名与顺序。 */
    @Test
    void startMessageWireFormatIsStable() {
        String wire = MessageCodec.encode(
                new NetMessage.Start(1, 42L, 0, 1, "老板", "游客", "NORMAL"));
        assertEquals("{\"kind\":\"START\",\"protocol\":1,\"seed\":42,\"youSeat\":0,\"firstSeat\":1,"
                + "\"you\":\"老板\",\"opponent\":\"游客\",\"aiLevel\":\"NORMAL\"}", wire);
    }

    /** 超过 int 的种子（联机常用 time-based seed）不能丢精度。 */
    @Test
    void largeSeedSurvivesTheWire() {
        long seed = 4_102_444_800_000L;
        NetMessage.Start decoded = assertInstanceOf(NetMessage.Start.class,
                MessageCodec.decode(MessageCodec.encode(
                        new NetMessage.Start(1, seed, 1, 0, "a", "b", "HARD"))));
        assertEquals(seed, decoded.seed());
    }
}
