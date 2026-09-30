package org.example.card.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

import org.example.card.model.Card;
import org.example.card.engine.ActionValidator;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.example.card.net.NetMessage;
import org.example.card.net.Protocol;
import org.example.card.server.GameRoom;
import org.example.card.server.RoomServer;
import org.junit.jupiter.api.Test;

/**
 * M3 客户端验收：两个 {@link RoomClient} 通过真 socket 连上 {@link RoomServer}，脚本把一局打完，
 * 断言<b>三份状态逐字节一致</b>——客户端甲、客户端乙、以及服务端的房间棋局。
 *
 * <p>「三份一致」就是 M3 的全部意义：客户端不自己算账，只把服务端接受过的指令序列重放一遍。
 * 一旦某个边界上两端分歧，这里就会红。
 *
 * <p>联机对局固定使用标准牌堆（见 {@link GameRoom} 的握手校验）：START 只带 seed，
 * 客户端必须能自行重建同一副牌。比较状态时按<b>名字</b>归位，因为服务端座位 0
 * 与客户端本地座位 0（自己）可能是镜像的，直接比 getPlayer()/getAi() 会把
 * 「甲 = 乙」这种荒谬结果也算成一致。
 */
class RoomClientTest {

    private static final long SEED = 20260930L;
    private static final long WAIT_MS = 8000;

    /** 自定义牌堆：只用来验证握手会把它挡住。 */
    private static List<Card> chargeDeck() {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            cards.add(new MinionCard("t-charge-" + i, "冲锋兵" + i, "测试用", 5, 5, 1, List.of(Keyword.CHARGE)));
        }
        return cards;
    }

    @Test
    void twoClientsReplayTheSameGameAndEndUpIdenticalToTheServer() throws Exception {
        try (RoomServer server = new RoomServer(0, GameRoom.standard(SEED))) {
            server.start();
            RoomClient one = new RoomClient("127.0.0.1", server.port(), "甲");
            RoomClient two = new RoomClient("127.0.0.1", server.port(), "乙");
            try {
                one.connect();
                two.connect();
                awaitTrue(() -> one.started() && two.started(), "两边都要收到 START");

                assertEquals(SEED, one.seed());
                assertEquals(SEED, two.seed());
                assertTrue(one.youSeat() != two.youSeat(), "两人必定坐在不同座位：" + one.youSeat() + "/" + two.youSeat());
                assertEquals(one.firstSeat(), two.firstSeat(), "先手座位是全局事实，两端看到的必须相同");
                assertEquals(one.playerFirst(), !two.playerFirst(), "一个先手一个后手");
                assertEquals(one.myName(), two.opponentName());
                assertEquals(one.opponentName(), two.myName());

                driveOneGame(one, two);

                assertTrue(one.finished(), "甲看到对局结束：" + diagnose(one, two));
                assertTrue(two.finished(), "乙看到对局结束：" + diagnose(one, two));
                assertEquals(List.of(), one.divergences(), "客户端甲不该出现重放分歧");
                assertEquals(List.of(), two.divergences(), "客户端乙不该出现重放分歧");

                GameRoom room = server.room();
                assertNotNull(room.session(), "房间里有棋局");
                assertTrue(room.session().gameOver(), "服务端也判定结束");
                String serverState = byName(room.session().getPlayer().getName(), room.session().getPlayer(),
                        room.session().getAi().getName(), room.session().getAi());
                assertEquals(serverState, byName(one.myName(), one.me(), one.opponentName(), one.opponent()),
                        "客户端甲 = 服务端");
                assertEquals(serverState, byName(two.myName(), two.me(), two.opponentName(), two.opponent()),
                        "客户端乙 = 服务端");
                assertTrue(one.log().size() > 3, "客户端本地也要有可读日志：" + one.log());
                assertFalse(one.serverEvents().isEmpty(), "服务端事件流留档，供 M4 断线补状态");
                assertEquals(List.of(), one.refusals(), "脚本不该发出非法指令（被打脸/打空场除外）");
            } finally {
                one.close();
                two.close();
            }
        }
    }

    @Test
    void actingOutOfTurnIsRefusedAndNothingChanges() throws Exception {
        try (RoomServer server = new RoomServer(0, GameRoom.standard(SEED))) {
            server.start();
            RoomClient one = new RoomClient("127.0.0.1", server.port(), "甲");
            RoomClient two = new RoomClient("127.0.0.1", server.port(), "乙");
            try {
                one.connect();
                two.connect();
                awaitTrue(() -> one.started() && two.started(), "两边都要收到 START");

                RoomClient active = one.myTurn() ? one : two;
                RoomClient idle = one.myTurn() ? two : one;
                assertFalse(idle.myTurn(), "总有一边不是自己回合");
                String before = digest(idle);

                idle.endTurn();

                awaitTrue(() -> !idle.refusals().isEmpty(), "越权指令要被拒");
                NetMessage.Failure failure = idle.refusals().get(0);
                assertEquals(Protocol.ERR_NOT_YOUR_TURN, failure.code());
                assertEquals(before, digest(idle), "被拒的指令不该改变任何状态");
                assertTrue(active.myTurn(), "还是轮到该行动的那一方");
                assertEquals(List.of(), idle.divergences(), "被拒不等于走散");
            } finally {
                one.close();
                two.close();
            }
        }
    }

    /** 把一局打完：谁轮到了就让谁行动，等本地状态跟上再继续。 */
    private static void driveOneGame(RoomClient one, RoomClient two) {
        String lastSeen = "";
        int stalled = 0;
        for (int guard = 0; guard < 600; guard++) {
            if (one.finished() || two.finished()) {
                return;
            }
            RoomClient actor = one.myTurn() ? one : (two.myTurn() ? two : null);
            if (actor == null) {
                sleep(5);
                assertTrue(++stalled < 200, "没人轮到，卡住了：" + diagnose(one, two));
                continue;
            }
            String before = position(one) + position(two);
            takeOneTurn(actor);
            if (before.equals(position(one) + position(two))) {
                assertTrue(++stalled < 40, "连续多回合状态没变，脚本卡住了：" + diagnose(one, two));
            } else {
                stalled = 0;
            }
            lastSeen = before;
        }
        assertTrue(one.finished() || two.finished(),
                "对局没能在限定步数内结束（最后见过 " + lastSeen + "）：" + diagnose(one, two));
    }

    /**
     * 一个回合：能打的牌打完、每个随从各找一个合法目标打一次、然后交回合。
     *
     * <p>脚本只用<b>本地那份规则</b>（{@link ActionValidator}，与服务端结算共用同一份代码）
     * 挑动作：任何一次被服务端拒绝都说明「本地算出的合法动作」和「服务端认可的合法动作」对不上，
     * 那正是 M3 要抓的分歧，所以外层断言 refusals 必须为空。
     */
    private static void takeOneTurn(RoomClient actor) {
        for (int i = 0; i < 12; i++) {
            int index = firstPlayable(actor.me());
            if (index < 0) {
                break;
            }
            String before = position(actor);
            int refusals = actor.refusals().size();
            actor.playCard(index);
            if (!settle(actor, before, refusals)) {
                break;
            }
        }
        for (int i = 0; i < 8; i++) {
            List<ActionValidator.Move> moves = ActionValidator.legalActions(actor.me(), actor.opponent());
            if (moves.isEmpty()) {
                break;
            }
            ActionValidator.Move move = moves.get(0);
            int fieldIndex = actor.me().getField().indexOf(move.attacker());
            int targetIndex = move.isFaceHit()
                    ? Protocol.FACE
                    : actor.opponent().getField().indexOf(move.target());
            String before = position(actor);
            int refusals = actor.refusals().size();
            actor.attack(fieldIndex, targetIndex);
            settle(actor, before, refusals);
        }
        String before = position(actor);
        int refusals = actor.refusals().size();
        actor.endTurn();
        settle(actor, before, refusals);
    }

    private static int firstPlayable(PlayerState me) {
        for (int i = 0; i < me.getHand().size(); i++) {
            if (ActionValidator.canPlay(me, me.getHand().get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 等本地影子棋局跟上服务端（状态变了算跟上，出现<b>新的</b>拒绝也算跟上）。
     *
     * <p>超时不算失败，交给外层收敛。这里必须只看新拒绝：早先的拒绝若让 settle 永远返回 false，
     * 脚本会在回声到达之前继续发指令，把「自己动作太快」误判成规则分歧。
     */
    private static boolean settle(RoomClient client, String before, int refusalsBefore) {
        long deadline = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < deadline) {
            if (!position(client).equals(before)) {
                return true;
            }
            if (client.refusals().size() > refusalsBefore) {
                return false;
            }
            sleep(5);
        }
        return false;
    }

    /** 状态 + 回合号：交回合只改回合号与法力，光看状态有可能看不出变化。 */
    private static String position(RoomClient client) {
        return client.turn() + "|" + digest(client);
    }

    /** 按名字归位再比较：服务端座位与客户端本地座位可能是镜像的。 */
    private static String byName(String nameA, PlayerState stateA, String nameB, PlayerState stateB) {
        Map<String, PlayerState> byName = new TreeMap<>();
        byName.put(nameA, stateA);
        byName.put(nameB, stateB);
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, PlayerState> entry : byName.entrySet()) {
            stateLines(entry.getKey(), entry.getValue(), text);
        }
        return text.toString();
    }

    private static String digest(RoomClient client) {
        if (!client.started()) {
            return "未开局";
        }
        return byName(client.myName(), client.me(), client.opponentName(), client.opponent());
    }

    private static String diagnose(RoomClient one, RoomClient two) {
        return "\n甲[" + digest(one) + " turn=" + one.turn() + " myTurn=" + one.myTurn()
                + " refusals=" + one.refusals().size() + " div=" + one.divergences() + "]"
                + "\n乙[" + digest(two) + " turn=" + two.turn() + " myTurn=" + two.myTurn()
                + " refusals=" + two.refusals().size() + " div=" + two.divergences() + "]";
    }

    private static void stateLines(String name, PlayerState state, StringBuilder out) {
        out.append(name).append(" life=").append(state.getLifePoints())
                .append(" mana=").append(state.getMana())
                .append(" hand=").append(state.getHand().size())
                .append(" field=");
        List<String> field = new ArrayList<>();
        for (MinionCard minion : state.getField()) {
            field.add(minion.getName() + "(" + (minion.getMaxHealth() - minion.getDamageTaken()) + ")");
        }
        field.sort(Comparator.naturalOrder());
        out.append(field).append('\n');
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitTrue(BooleanSupplier condition, String what) {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(10);
        }
        assertTrue(condition.getAsBoolean(), what);
    }
}
