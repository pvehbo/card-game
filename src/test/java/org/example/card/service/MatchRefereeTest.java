package org.example.card.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.example.card.ai.AiLevel;
import org.example.card.engine.GameEngine;
import org.example.card.engine.GameSession;
import org.example.card.model.Card;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.example.card.net.NetMessage;
import org.example.card.net.Protocol;
import org.junit.jupiter.api.Test;

/**
 * M3「客户端接线」的地基验收：裁判器是服务端与客户端共用的<b>同一段代码</b>，
 * 所以「同 seed + 同牌堆 + 同指令序列 ⇒ 逐字节相同的对局」必须成立，
 * 否则联机两端迟早会在某个边界上走散。
 */
class MatchRefereeTest {

    /** 全是 1 费冲锋 5/5：法力 1 即可上场、上场即能攻击，几步之内必然分出胜负。 */
    private static List<Card> chargeDeck() {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            cards.add(new MinionCard("t-charge", "冲锋兵", "测试用", 5, 5, 1, List.of(Keyword.CHARGE)));
        }
        return cards;
    }

    /** 一个玩家能看见的全部状态：桌面上的数字、手牌张数、场上的随从名。 */
    private static String digest(MatchReferee referee) {
        return "turn=" + referee.engine().getTurn() + " active=" + referee.activeSeat()
                + " finished=" + referee.finished()
                + " | 0: " + seatDigest(referee.seatState(0))
                + " | 1: " + seatDigest(referee.seatState(1));
    }

    private static String seatDigest(PlayerState state) {
        List<String> field = new ArrayList<>();
        for (MinionCard minion : state.getField()) {
            field.add(minion.getName() + "(" + (minion.getMaxHealth() - minion.getDamageTaken()) + ")");
        }
        return "life=" + state.getLifePoints() + " mana=" + state.getMana()
                + " hand=" + state.getHand().size() + " field=" + field;
    }

    /** 一端：一局棋 + 一个裁判器 + 它收到的日志与事件。 */
    private static final class End {
        final GameSession session;
        final MatchReferee referee;
        final List<String> log = new ArrayList<>();
        final List<String> events = new ArrayList<>();

        End(long seed, int firstSeat) {
            this.session = GameSession.newVersus(new Random(seed), chargeDeck(), chargeDeck(), "甲", "乙");
            GameEngine engine = new GameEngine(AiLevel.NORMAL.newAi());
            engine.eventBus().onAny(event -> events.add(event.toJson(events.size() + 1, engine.getTurn())));
            this.referee = new MatchReferee(session, engine, log::add, firstSeat);
            this.engine = engine;
        }

        final GameEngine engine;
    }

    @Test
    void mirroredEndsStayIdenticalThroughAWholeGame() {
        End server = new End(2024L, 0);
        End client = new End(2024L, 0);
        assertFalse(server.referee.finished());

        int steps = 0;
        for (int guard = 0; guard < 400 && !server.referee.finished(); guard++) {
            int seat = server.referee.activeSeat();
            String actor = seat == 0 ? "甲" : "乙";
            PlayerState self = server.referee.seatState(seat);

            List<NetMessage.Action> plan = new ArrayList<>();
            if (!self.getHand().isEmpty() && self.getField().size() < 7) {
                plan.add(NetMessage.Action.play(guard * 10 + 1, actor, 0));
            }
            for (int i = 0; i < self.getField().size(); i++) {
                plan.add(new NetMessage.Action(guard * 10 + 2 + i, actor, Protocol.MOVE_ATTACK,
                        Protocol.UNUSED, i, Protocol.FACE));
            }
            plan.add(NetMessage.Action.endTurn(guard * 10 + 9, actor));

            for (NetMessage.Action action : plan) {
                if (server.referee.finished()) {
                    break;
                }
                Optional<MatchReferee.Refusal> serverSays = server.referee.apply(seat, action);
                Optional<MatchReferee.Refusal> clientSays = client.referee.apply(seat, action);
                assertEquals(serverSays, clientSays, "两端对同一条指令的判定必须一致：" + action.action());
                steps++;
                assertEquals(digest(server.referee), digest(client.referee),
                        "重放同一序列后两端状态必须一致（第 " + steps + " 步，动作 " + action.action() + "）");
                assertEquals(server.events, client.events, "两端的事件流也必须逐条相同");
                assertEquals(server.log, client.log, "日志只进本地，但同一段代码应该打出同样的字");
            }
        }

        assertTrue(server.referee.finished(), "脚本必须把对局打完");
        assertTrue(steps > 10, "至少要跑出十步以上才算验过，实际 " + steps);
        assertEquals(digest(server.referee), digest(client.referee));
        assertEquals(server.events, client.events);
    }

    @Test
    void refusalCodesAreTheProtocolOnes() {
        End end = new End(7L, 0);
        MatchReferee referee = end.referee;
        int idle = 1 - referee.activeSeat();
        PlayerState active = referee.seatState(referee.activeSeat());

        assertEquals(Protocol.ERR_NOT_YOUR_TURN,
                referee.apply(idle, NetMessage.Action.endTurn(1, "乙")).orElseThrow().code());
        assertEquals(Protocol.ERR_ILLEGAL_MOVE,
                referee.apply(referee.activeSeat(), NetMessage.Action.play(2, "甲", 99)).orElseThrow().code());
        assertEquals(Protocol.ERR_BAD_MESSAGE,
                referee.apply(referee.activeSeat(), new NetMessage.Action(3, "甲", "TELEPORT",
                        Protocol.UNUSED, Protocol.UNUSED, Protocol.UNUSED)).orElseThrow().code());

        // 合法的一步必须真的落地：手牌少一张、场上多一个随从、法力见底。
        int handBefore = active.getHand().size();
        assertTrue(referee.apply(referee.activeSeat(), NetMessage.Action.play(4, "甲", 0)).isEmpty());
        assertEquals(handBefore - 1, active.getHand().size());
        assertEquals(1, active.getField().size());
        assertEquals(0, active.getMana());

        // 打完的局只剩一种回答。
        MatchReferee over = new End(2024L, 0).referee;
        while (!over.finished()) {
            int seat = over.activeSeat();
            PlayerState self = over.seatState(seat);
            if (!self.getHand().isEmpty() && self.getField().size() < 7) {
                over.apply(seat, NetMessage.Action.play(1, "甲", 0));
            }
            for (int i = 0; i < self.getField().size() && !over.finished(); i++) {
                over.apply(seat, new NetMessage.Action(2 + i, "甲", Protocol.MOVE_ATTACK,
                        Protocol.UNUSED, i, Protocol.FACE));
            }
            if (!over.finished()) {
                over.apply(seat, NetMessage.Action.endTurn(99, "甲"));
            }
        }
        assertEquals(Protocol.ERR_GAME_OVER, over.apply(0, NetMessage.Action.endTurn(100, "甲")).orElseThrow().code());
    }
}
