package org.example.card.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.example.card.event.GameEvent;
import org.example.card.model.Card;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.example.card.net.MessageCodec;
import org.example.card.net.NetMessage;
import org.example.card.net.Protocol;
import org.junit.jupiter.api.Test;

/**
 * M2 房间验收：开局、权限、非法动作被拒并记录、事件广播与暗信息裁剪、终局封盘。
 *
 * <p>牌堆刻意用「1 费冲锋 5/5」：法力 1 即可上场、上场即能攻击，
 * 于是每一条规则都能在两步之内触发，脚本也必然在几步内分出胜负。
 */
class GameRoomTest {

    private static final long SEED = 42L;
    private static final String DECK_ID = null;

    /** 全是 1 费冲锋 5/5 的牌堆（30 张）。 */
    private static List<Card> chargeDeck() {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            cards.add(new MinionCard("t-charge", "冲锋兵", "测试用", 5, 5, 1, List.of(Keyword.CHARGE)));
        }
        return cards;
    }

    /** 一个座位收到的所有报文。 */
    private static final class Inbox implements GameRoom.Transport {

        private final List<String> wires = new ArrayList<>();

        @Override
        public void send(String wire) {
            wires.add(wire);
        }

        List<NetMessage> messages() {
            List<NetMessage> decoded = new ArrayList<>();
            for (String wire : wires) {
                decoded.add(MessageCodec.decode(wire));
            }
            return decoded;
        }

        List<NetMessage> since(int from) {
            List<NetMessage> all = messages();
            return new ArrayList<>(all.subList(Math.min(from, all.size()), all.size()));
        }

        NetMessage last() {
            return MessageCodec.decode(wires.get(wires.size() - 1));
        }

        List<NetMessage.Event> events() {
            List<NetMessage.Event> found = new ArrayList<>();
            for (NetMessage message : messages()) {
                if (message instanceof NetMessage.Event event) {
                    found.add(event);
                }
            }
            return found;
        }

        GameEvent.JsonData eventData(int index) {
            return GameEvent.fromJson(events().get(index).eventJson());
        }
    }

    /** 两个座位都入座的房间。 */
    private static final class Fixture {

        final GameRoom room;
        final Inbox seat0 = new Inbox();
        final Inbox seat1 = new Inbox();
        final int firstSeat;
        final int active;

        Fixture(long seed) {
            room = new GameRoom(seed, GameRoomTest::chargeDeck, GameRoomTest::chargeDeck);
            room.attach(0, seat0);
            room.attach(1, seat1);
            room.handle(0, MessageCodec.encode(new NetMessage.Join(Protocol.VERSION, "甲", DECK_ID)));
            room.handle(1, MessageCodec.encode(new NetMessage.Join(Protocol.VERSION, "乙", DECK_ID)));
            NetMessage.Start start = (NetMessage.Start) seat0.messages().get(0);
            firstSeat = start.firstSeat();
            active = firstSeat;
        }

        Inbox inbox(int seat) {
            return seat == 0 ? seat0 : seat1;
        }

        String name(int seat) {
            return seat == 0 ? "甲" : "乙";
        }

        PlayerState state(int seat) {
            return seat == 0 ? room.session().getPlayer() : room.session().getAi();
        }

        int seq(int seat) {
            return inbox(seat).events().size();
        }

        void send(int seat, NetMessage message) {
            room.handle(seat, MessageCodec.encode(message));
        }

        void sendAction(int seat, int seq, NetMessage.Action action) {
            send(seat, action);
        }
    }

    private static String wire(NetMessage message) {
        return MessageCodec.encode(message);
    }

    private static NetMessage.Failure failure(NetMessage message) {
        return assertInstanceOf(NetMessage.Failure.class, message, "期望一条 Failure，实际：" + message);
    }

    // ============ 入座与开局 ============

    @Test
    void bothSeatsMustJoinBeforeAnythingHappens() {
        GameRoom room = new GameRoom(SEED, GameRoomTest::chargeDeck, GameRoomTest::chargeDeck);
        Inbox seat0 = new Inbox();
        Inbox seat1 = new Inbox();
        room.attach(0, seat0);
        room.attach(1, seat1);

        room.handle(0, wire(new NetMessage.Join(Protocol.VERSION, "甲", DECK_ID)));

        assertEquals(GameRoom.State.WAITING, room.state(), "只有一个人时不开局");
        assertTrue(seat0.messages().isEmpty(), "一个人入座后不该收到任何消息");

        room.handle(1, wire(new NetMessage.Join(Protocol.VERSION, "乙", DECK_ID)));

        assertEquals(GameRoom.State.PLAYING, room.state());
        assertInstanceOf(NetMessage.Start.class, seat0.messages().get(0));
        assertInstanceOf(NetMessage.Start.class, seat1.messages().get(0));
    }

    @Test
    void startCarriesOneSeedAndMirroredSeats() {
        Fixture fixture = new Fixture(SEED);
        NetMessage.Start forSeat0 = (NetMessage.Start) fixture.seat0.messages().get(0);
        NetMessage.Start forSeat1 = (NetMessage.Start) fixture.seat1.messages().get(0);

        assertEquals(SEED, forSeat0.seed(), "seed 由服务端权威给出");
        assertEquals(SEED, forSeat1.seed());
        assertEquals(0, forSeat0.youSeat(), "youSeat 是服务端座位的权威事实");
        assertEquals(1, forSeat1.youSeat(), "每个座位收到的 youSeat 就是自己");
        assertEquals(forSeat0.firstSeat(), forSeat1.firstSeat(), "先手座位是全局事实，两端相同");
        assertEquals(Protocol.VERSION, forSeat0.protocolVersion());
        assertEquals(Protocol.MODE_PVP, forSeat0.aiLevel());

        assertEquals("甲", forSeat0.you());
        assertEquals("乙", forSeat0.opponent());
        assertEquals("乙", forSeat1.you());
        assertEquals("甲", forSeat1.opponent());
        assertEquals(forSeat0.firstSeat(), fixture.active, "先手座位与 activeSeat 必须一致");
        assertEquals(fixture.firstSeat, forSeat0.firstSeat(), "同一局里两个座位看到的先手座位是同一个");
    }

    @Test
    void protocolVersionMismatchIsRejectedAndNoGameStarts() {
        GameRoom room = new GameRoom(SEED, GameRoomTest::chargeDeck, GameRoomTest::chargeDeck);
        Inbox seat0 = new Inbox();
        Inbox seat1 = new Inbox();
        room.attach(0, seat0);
        room.attach(1, seat1);

        room.handle(0, wire(new NetMessage.Join(Protocol.VERSION + 1, "甲", DECK_ID)));

        assertEquals(Protocol.ERR_VERSION, failure(seat0.last()).code());
        assertEquals(GameRoom.State.WAITING, room.state());
        assertEquals(1, room.rejections().size(), "拒绝必须记录在案");
        assertTrue(room.rejections().get(0).contains("座位 0"), room.rejections().get(0));
        assertEquals(0, room.actionCount());
    }

    @Test
    void joiningAfterTheGameStartedIsRefused() {
        Fixture fixture = new Fixture(SEED);
        fixture.send(1, new NetMessage.Join(Protocol.VERSION, "丙", DECK_ID));

        NetMessage.Failure refusal = failure(fixture.seat1.last());
        assertEquals(Protocol.ERR_ROOM_FULL, refusal.code());
        assertEquals("甲", ((NetMessage.Start) fixture.seat0.messages().get(0)).you(),
                "重连不能把已经开好的局重开");
    }

    @Test
    void blankNameFallsBackToSeatNumber() {
        GameRoom room = new GameRoom(SEED, GameRoomTest::chargeDeck, GameRoomTest::chargeDeck);
        Inbox seat0 = new Inbox();
        Inbox seat1 = new Inbox();
        room.attach(0, seat0);
        room.attach(1, seat1);
        room.handle(0, wire(new NetMessage.Join(Protocol.VERSION, "   ", DECK_ID)));
        room.handle(1, wire(new NetMessage.Join(Protocol.VERSION, null, DECK_ID)));

        assertEquals("玩家1", ((NetMessage.Start) seat0.messages().get(0)).you());
        assertEquals("玩家2", ((NetMessage.Start) seat1.messages().get(0)).you());
    }

    // ============ 权限与非法动作 ============

    @Test
    void onlyTheActiveSeatMayAct() {
        Fixture fixture = new Fixture(SEED);
        int other = 1 - fixture.active;

        fixture.send(other, NetMessage.Action.play(1, fixture.name(other), 0));

        assertEquals(Protocol.ERR_NOT_YOUR_TURN, failure(fixture.inbox(other).last()).code());
        assertEquals(0, fixture.state(fixture.active).getField().size(), "不该有随从被偷偷放上场");
        assertEquals(0, fixture.room.actionCount(), "越权指令一步都不许走");
    }

    @Test
    void forgedActorCannotActForTheOpponent() {
        Fixture fixture = new Fixture(SEED);
        int other = 1 - fixture.active;

        // 座位 1 冒充座位 0 的名字发指令：权限按「连接」判定，不按 actor 字段。
        fixture.send(other, NetMessage.Action.play(7, fixture.name(fixture.active), 0));

        NetMessage.Failure refusal = failure(fixture.inbox(other).last());
        assertEquals(Protocol.ERR_NOT_YOUR_TURN, refusal.code());
        assertEquals(1, fixture.room.rejections().size());
        assertTrue(fixture.room.rejections().get(0).contains("座位 " + other),
                fixture.room.rejections().get(0));
        assertEquals(0, fixture.state(fixture.active).getField().size());
    }

    @Test
    void outOfRangeIndexesAreRejected() {
        Fixture fixture = new Fixture(SEED);
        int seat = fixture.active;

        fixture.send(seat, NetMessage.Action.play(1, fixture.name(seat), 99));
        assertEquals(Protocol.ERR_ILLEGAL_MOVE, failure(fixture.inbox(seat).last()).code());

        fixture.send(seat, new NetMessage.Action(2, fixture.name(seat), Protocol.MOVE_ATTACK,
                Protocol.UNUSED, 5, Protocol.FACE));
        assertEquals(Protocol.ERR_ILLEGAL_MOVE, failure(fixture.inbox(seat).last()).code());

        fixture.send(seat, new NetMessage.Action(3, fixture.name(seat), Protocol.MOVE_ATTACK,
                Protocol.UNUSED, -1, Protocol.FACE));
        assertEquals(Protocol.ERR_ILLEGAL_MOVE, failure(fixture.inbox(seat).last()).code());

        assertEquals(3, fixture.room.rejections().size());
        assertEquals(0, fixture.room.actionCount(), "被拒的动作不算步数");
    }

    @Test
    void playingTwiceInOneTurnIsRejectedByTheSameRuleAsOffline() {
        Fixture fixture = new Fixture(SEED);
        int seat = fixture.active;

        fixture.send(seat, NetMessage.Action.play(1, fixture.name(seat), 0));
        assertEquals(1, fixture.state(seat).getField().size());

        fixture.send(seat, NetMessage.Action.play(2, fixture.name(seat), 0));

        assertEquals(Protocol.ERR_ILLEGAL_MOVE, failure(fixture.inbox(seat).last()).code());
        assertEquals(1, fixture.state(seat).getField().size(), "一回合只能上一个随从");
    }

    @Test
    void unknownActionAndWrongDirectionMessagesAreRejected() {
        Fixture fixture = new Fixture(SEED);
        int seat = fixture.active;

        fixture.send(seat, new NetMessage.Action(1, fixture.name(seat), "TELEPORT",
                Protocol.UNUSED, Protocol.UNUSED, Protocol.UNUSED));
        assertEquals(Protocol.ERR_BAD_MESSAGE, failure(fixture.inbox(seat).last()).code());

        fixture.send(seat, new NetMessage.Start(Protocol.VERSION, 1L, 0, 0, "甲", "乙", Protocol.MODE_PVP));
        assertEquals(Protocol.ERR_BAD_MESSAGE, failure(fixture.inbox(seat).last()).code());
    }

    @Test
    void malformedWireIsRejectedWithoutKillingTheRoom() {
        Fixture fixture = new Fixture(SEED);
        int seat = fixture.active;

        fixture.room.handle(seat, "{\"kind\":\"ACTION\" oops}");

        assertEquals(Protocol.ERR_BAD_MESSAGE, failure(fixture.inbox(seat).last()).code());
        assertEquals(GameRoom.State.PLAYING, fixture.room.state());

        fixture.send(seat, NetMessage.Action.play(1, fixture.name(seat), 0));
        assertEquals(1, fixture.state(seat).getField().size(), "一次坏报文不该让房间瘫掉");
    }

    @Test
    void pingIsAnsweredOnlyToTheSender() {
        Fixture fixture = new Fixture(SEED);
        int before = fixture.seat1.messages().size();

        fixture.send(0, new NetMessage.Ping(1234));

        assertEquals(before, fixture.seat1.messages().size(), "心跳不该打扰对手");
        assertEquals(new NetMessage.Pong(1234), fixture.seat0.last());
    }

    // ============ 结算与事件广播 ============

    @Test
    void playAndAttackSettleOnTheServerAndReachBothSeats() {
        Fixture fixture = new Fixture(SEED);
        int seat = fixture.active;
        int foeSeat = 1 - seat;

        fixture.send(seat, NetMessage.Action.play(1, fixture.name(seat), 0));

        assertEquals(1, fixture.state(seat).getField().size());
        assertEquals(0, fixture.state(seat).getMana(), "1 费随从扣掉 1 点法力");
        assertTrue(fixture.seat0.events().stream().anyMatch(e -> e.eventJson().contains("SUMMON")));
        assertTrue(fixture.seat1.events().stream().anyMatch(e -> e.eventJson().contains("SUMMON")),
                "对手也要看到随从上场");

        int lifeBefore = fixture.state(foeSeat).getLifePoints();
        fixture.send(seat, new NetMessage.Action(2, fixture.name(seat), Protocol.MOVE_ATTACK,
                Protocol.UNUSED, 0, Protocol.FACE));

        assertEquals(lifeBefore - 5, fixture.state(foeSeat).getLifePoints(), "5 攻打脸");
        assertEquals(1, fixture.state(seat).getField().get(0).getAttacksUsed());
    }

    @Test
    void bothSeatsSeeTheSameEventSequence() {
        Fixture fixture = new Fixture(SEED);
        int seat = fixture.active;
        fixture.send(seat, NetMessage.Action.play(1, fixture.name(seat), 0));
        fixture.send(seat, NetMessage.Action.endTurn(2, fixture.name(seat)));

        List<Integer> seq0 = fixture.seat0.events().stream().map(NetMessage.Event::seq).toList();
        List<Integer> seq1 = fixture.seat1.events().stream().map(NetMessage.Event::seq).toList();

        assertFalse(seq0.isEmpty());
        assertEquals(seq0, seq1, "两个座位的 seq 必须逐条对齐");
        for (int i = 1; i < seq0.size(); i++) {
            assertTrue(seq0.get(i) > seq0.get(i - 1), "seq 必须严格递增");
        }
    }

    @Test
    void endTurnHandsOverToTheOtherSeat() {
        Fixture fixture = new Fixture(SEED);
        int seat = fixture.active;
        int other = 1 - seat;
        fixture.send(seat, NetMessage.Action.endTurn(1, fixture.name(seat)));

        assertEquals(other, fixture.room.activeSeat(), "回合必须交给对手");
        assertEquals(1, fixture.state(other).getMana(), "新回合法力回满（第 1 回合 = 1 点）");

        // 换手后引擎的回合数前进，广播里也带上了新回合号
        NetMessage.Event turnStart = null;
        for (NetMessage.Event event : fixture.seat0.events()) {
            if (GameEvent.fromJson(event.eventJson()).type() == GameEvent.Type.TURN_START) {
                turnStart = event;
            }
        }
        assertNotNull(turnStart, "换手必须广播 TURN_START");
        assertEquals(2, turnStart.turn(), "TURN_START 广播必须带新一轮的回合号");
    }

    @Test
    void drawIsRedactedForTheOpponentButNotForTheDrawer() {
        Fixture fixture = new Fixture(SEED);
        int seat = fixture.active;
        int other = 1 - seat;

        GameEvent.JsonData forDrawer = firstDraw(fixture.inbox(seat));
        GameEvent.JsonData forOpponent = firstDraw(fixture.inbox(other));

        assertNotEquals(null, forDrawer.cardId(), "本人必须看见抽到什么");
        assertNull(forOpponent.cardId(), "对手不该看见牌面");
        assertEquals(GameRoom.HIDDEN_DRAW, forOpponent.message());
        assertTrue(forDrawer.message().contains("抽卡"), forDrawer.message());
    }

    private static GameEvent.JsonData firstDraw(Inbox inbox) {
        for (NetMessage.Event event : inbox.events()) {
            GameEvent.JsonData data = GameEvent.fromJson(event.eventJson());
            if (data.type() == GameEvent.Type.DRAW) {
                return data;
            }
        }
        throw new AssertionError("没有收到任何抽牌事件");
    }

    // ============ 终局 ============

    @Test
    void gameRunsToGameOverThenRefusesEverything() {
        Fixture fixture = new Fixture(SEED);
        GameRoom room = fixture.room;
        int seq = 0;

        for (int guard = 0; guard < 200 && room.state() == GameRoom.State.PLAYING; guard++) {
            int seat = room.activeSeat();
            PlayerState self = fixture.state(seat);
            String name = fixture.name(seat);
            if (self.getField().size() < 7 && !self.getHand().isEmpty()) {
                fixture.send(seat, NetMessage.Action.play(++seq, name, 0));
            }
            if (room.state() != GameRoom.State.PLAYING) {
                break;
            }
            int attackers = self.getField().size();
            for (int i = 0; i < attackers && room.state() == GameRoom.State.PLAYING; i++) {
                fixture.send(seat, new NetMessage.Action(++seq, name, Protocol.MOVE_ATTACK,
                        Protocol.UNUSED, i, Protocol.FACE));
            }
            if (room.state() != GameRoom.State.PLAYING) {
                break;
            }
            fixture.send(seat, NetMessage.Action.endTurn(++seq, name));
        }

        assertEquals(GameRoom.State.FINISHED, room.state(), "脚本必须把对局打完");
        int loserLife = room.session().getPlayer().isDefeated()
                ? room.session().getPlayer().getLifePoints()
                : room.session().getAi().getLifePoints();
        assertEquals(0, loserLife);

        assertTrue(fixture.seat0.events().stream().anyMatch(e -> e.eventJson().contains("GAME_OVER")));
        assertTrue(fixture.seat1.events().stream().anyMatch(e -> e.eventJson().contains("GAME_OVER")),
                "终局事件要广播到两边");

        int rejectionsBefore = room.rejections().size();
        fixture.send(room.activeSeat(), NetMessage.Action.endTurn(999, "谁"));
        assertEquals(Protocol.ERR_GAME_OVER, failure(fixture.inbox(room.activeSeat()).last()).code());
        assertEquals(rejectionsBefore + 1, room.rejections().size());
    }

    @Test
    void gameOverIsAnnouncedExactlyOnce() {
        Fixture fixture = new Fixture(SEED);
        GameRoom room = fixture.room;
        int seq = 0;
        for (int guard = 0; guard < 200 && room.state() == GameRoom.State.PLAYING; guard++) {
            int seat = room.activeSeat();
            PlayerState self = fixture.state(seat);
            String name = fixture.name(seat);
            if (self.getField().size() < 7 && !self.getHand().isEmpty()) {
                fixture.send(seat, NetMessage.Action.play(++seq, name, 0));
            }
            int attackers = self.getField().size();
            for (int i = 0; i < attackers && room.state() == GameRoom.State.PLAYING; i++) {
                fixture.send(seat, new NetMessage.Action(
                        ++seq, name, Protocol.MOVE_ATTACK, Protocol.UNUSED, i, Protocol.FACE));
            }
            if (room.state() != GameRoom.State.PLAYING) {
                break;
            }
            fixture.send(seat, NetMessage.Action.endTurn(++seq, name));
        }

        long gameOverEvents = fixture.seat0.events().stream()
                .filter(e -> e.eventJson().contains("GAME_OVER"))
                .count();
        assertEquals(1, gameOverEvents, "终局只能播报一次（战斗结算报过就不再补报）");
    }
}
