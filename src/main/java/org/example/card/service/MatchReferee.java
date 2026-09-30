package org.example.card.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.example.card.engine.ActionValidator;
import org.example.card.engine.GameEngine;
import org.example.card.engine.GameSession;
import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;
import org.example.card.net.NetMessage;
import org.example.card.net.Protocol;

/**
 * 裁判器（M3）：把「一条玩家意图落到一局棋上」的逻辑单独拎出来，服务端（{@code server.GameRoom}）
 * 与客户端（{@code net.RoomClient}）共用<b>同一段代码</b>。
 *
 * <h2>为什么必须是同一段代码</h2>
 * 联机不允许两份规则：只要服务端和客户端各写一遍「这张牌能不能出」，它们早晚会在某个边界上分歧。
 * 客户端因此不做自己的判断，它只是把服务端接受过的指令按原顺序喂给同一个裁判器。
 *
 * <h2>它不管的事</h2>
 * 座位权限（谁连在哪）、报文格式、广播与裁剪都在调用方：这里只认「座位号 + 意图」，
 * 「轮到你没有」由 {@code activeSeat} 判定，冒充名字这类事由调用方按连接判定。
 *
 * <h2>开局就在构造函数里</h2>
 * 构造即开局（先手起手 + 后手补偿）。开局手牌是确定性构造的一部分，不含任何需要同步的随机量，
 * 所以两端各构造一次就得到同一盘棋。
 *
 * <p>本类零 JavaFX、零第三方依赖：服务端要能直接复用同一份 jar（见 HeadlessGameTest 的体检用例）。
 */
public final class MatchReferee {

    /** 被拒的指令：{@code code} 取自 {@link Protocol} 的 ERR_* 常量，{@code message} 面向玩家。 */
    public record Refusal(String code, String message) {
    }

    private final GameSession session;
    private final GameEngine engine;
    private final Consumer<String> sink;
    private int activeSeat;

    /**
     * 建局并开局。
     *
     * @param session   已经发好手牌的对局（两端必须用同一个 seed 构造）
     * @param engine    驱动这局棋的引擎
     * @param sink      引擎日志出口；两端各自收集，服务端的那份是审计日志
     * @param firstSeat 先手座位（0 或 1），由 START 广播的权威事实决定
     */
    public MatchReferee(GameSession session, GameEngine engine, Consumer<String> sink, int firstSeat) {
        this.session = session;
        this.engine = engine;
        this.sink = sink;
        this.activeSeat = firstSeat;
        openSeatTurn(firstSeat);
        engine.grantSecondMoveBonus(seatState(1 - firstSeat), sink);
        session.setYourTurn(firstSeat == 0);
    }

    public int activeSeat() {
        return activeSeat;
    }

    public GameSession session() {
        return session;
    }

    public GameEngine engine() {
        return engine;
    }

    /** 对局是否已经分出胜负。 */
    public boolean finished() {
        return session.gameOver();
    }

    /** 座位上的玩家状态（座位 0 是 {@link GameSession#getPlayer()}，1 是 {@link GameSession#getAi()}）。 */
    public PlayerState seatState(int seat) {
        return seatState(session, seat);
    }

    /** 对手的状态。 */
    public PlayerState foeState(int seat) {
        return foeState(session, seat);
    }

    /**
     * 静态版本：给「还没把裁判器建出来」的时刻用。
     *
     * <p>{@link GameRoom} 在开局补牌那几条事件里就要按座位裁剪，而那时裁判器正在构造函数里，
     * 字段还没赋值——所以座位映射必须是能脱离实例调用的纯函数。
     */
    public static PlayerState seatState(GameSession session, int seat) {
        return seat == 0 ? session.getPlayer() : session.getAi();
    }

    /** 静态版本的对手状态。 */
    public static PlayerState foeState(GameSession session, int seat) {
        return seat == 0 ? session.getAi() : session.getPlayer();
    }

    /**
     * 结算一条指令。
     *
     * <p>调用方负责「对局还没开始」这类房间级检查；这里只判「轮到你没有」与规则本身。
     *
     * @return 被拒时给出原因（调用方应把它回执给当事人），成功返回 {@link Optional#empty()}
     */
    public Optional<Refusal> apply(int seat, NetMessage.Action action) {
        if (session.gameOver()) {
            return Optional.of(new Refusal(Protocol.ERR_GAME_OVER, "对局已经结束"));
        }
        if (seat != activeSeat) {
            return Optional.of(new Refusal(Protocol.ERR_NOT_YOUR_TURN, "还没轮到你"));
        }
        String move = action.action();
        if (Protocol.MOVE_END_TURN.equals(move)) {
            return endTurn(seat);
        }
        if (Protocol.MOVE_PLAY.equals(move)) {
            return playCard(seat, action);
        }
        if (Protocol.MOVE_ATTACK.equals(move)) {
            return attack(seat, action);
        }
        return Optional.of(new Refusal(Protocol.ERR_BAD_MESSAGE, "未知动作：" + move));
    }

    /** 让某座位起手：回合数 +1、重置出牌次数、法力回满、抽 1 张，并发布 TURN_START。 */
    private void openSeatTurn(int seat) {
        engine.startPlayerTurn(seatState(seat), foeState(seat), sink);
        session.setYourTurn(seat == 0);
    }

    /** 结算一张手牌。 */
    private Optional<Refusal> playCard(int seat, NetMessage.Action action) {
        PlayerState self = seatState(seat);
        PlayerState foe = foeState(seat);
        List<Card> hand = self.getHand();
        int index = action.cardIndex();
        if (index < 0 || index >= hand.size()) {
            return Optional.of(illegal("手牌下标越界：" + index));
        }
        Card card = hand.get(index);
        Optional<String> reason = ActionValidator.playRejectReason(self, card);
        if (reason.isPresent()) {
            return Optional.of(illegal(reason.get()));
        }
        boolean settled;
        if (card instanceof MinionCard minion) {
            settled = engine.playMinion(self, minion, sink);
        } else if (card instanceof SpellCard spell) {
            settled = engine.playSpell(self, foe, spell, sink);
        } else if (card instanceof PetCard pet) {
            settled = engine.playPet(self, pet, sink);
        } else {
            return Optional.of(illegal("未知卡牌类型：" + card.getName()));
        }
        if (!settled) {
            return Optional.of(illegal("结算被引擎拒绝：" + card.getName()));
        }
        return Optional.empty();
    }

    /** 结算一次攻击（{@link Protocol#FACE} = 打脸）。 */
    private Optional<Refusal> attack(int seat, NetMessage.Action action) {
        PlayerState self = seatState(seat);
        PlayerState foe = foeState(seat);
        List<MinionCard> attackers = self.getField();
        int attackerIndex = action.fieldIndex();
        if (attackerIndex < 0 || attackerIndex >= attackers.size()) {
            return Optional.of(illegal("己方随从下标越界：" + attackerIndex));
        }
        MinionCard attacker = attackers.get(attackerIndex);
        MinionCard target = null;
        int targetIndex = action.targetIndex();
        if (targetIndex != Protocol.FACE) {
            List<MinionCard> defenders = foe.getField();
            if (targetIndex < 0 || targetIndex >= defenders.size()) {
                return Optional.of(illegal("对方随从下标越界：" + targetIndex));
            }
            target = defenders.get(targetIndex);
        }
        Optional<String> reason = ActionValidator.rejectReason(self, foe, attacker, target);
        if (reason.isPresent()) {
            return Optional.of(illegal(reason.get()));
        }
        if (!engine.attack(self, foe, attacker, target, sink)) {
            return Optional.of(illegal("结算被引擎拒绝：" + attacker.getName() + " 的攻击"));
        }
        return Optional.empty();
    }

    /**
     * 换手：先收尾当前座位，再让对手起手——两个座位走同一段代码，没有 AI 分支，
     * 这也是引擎「座位无关」的直接体现（GameEngine 的每个方法都收 (self, foe)）。
     */
    private Optional<Refusal> endTurn(int seat) {
        engine.endPlayerTurn(seatState(seat), foeState(seat), sink);
        if (session.gameOver()) {
            return Optional.empty();
        }
        activeSeat = 1 - seat;
        openSeatTurn(activeSeat);
        return Optional.empty();
    }

    private static Refusal illegal(String message) {
        return new Refusal(Protocol.ERR_ILLEGAL_MOVE, message);
    }
}
