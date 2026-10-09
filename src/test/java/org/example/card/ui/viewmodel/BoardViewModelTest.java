package org.example.card.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Random;

import org.example.card.ai.SimpleAi;
import org.example.card.data.CardDatabase;
import org.example.card.engine.GameEngine;
import org.example.card.engine.GameSession;
import org.example.card.model.Card;
import org.junit.jupiter.api.Test;

/**
 * 联机接线（M3c）：界面靠 localSeat 区分「你」和「对手」。
 *
 * <p>影子对局的座位顺序必须与服务端一致（发牌按座位顺序），所以坐在 1 号位的客户端，
 * 会话里的 {@code ai} 槽才是自己——这份用例把这条映射钉住，防止以后又按座位 0 硬编码。
 */
class BoardViewModelTest {

    private static GameSession versus() {
        return GameSession.newVersus(new Random(7),
                CardDatabase.standardDeck(), CardDatabase.standardDeck(), "甲", "乙");
    }

    @Test
    void defaultSeatKeepsSinglePlayerSemantics() {
        GameSession session = versus();
        BoardViewModel vm = new BoardViewModel();
        vm.attach(session, new GameEngine(new SimpleAi()));

        assertEquals(0, vm.localSeat());
        assertSame(session.getPlayer(), vm.me());
        assertSame(session.getAi(), vm.foe());
    }

    @Test
    void seatOneSeesTheSecondSeatAsMe() {
        GameSession session = versus();
        BoardViewModel vm = new BoardViewModel();
        vm.setLocalSeat(1);
        vm.attach(session, new GameEngine(new SimpleAi()));

        assertEquals(1, vm.localSeat());
        assertSame(session.getAi(), vm.me());
        assertSame(session.getPlayer(), vm.foe());
    }

    @Test
    void unknownSeatFallsBackToZero() {
        GameSession session = versus();
        BoardViewModel vm = new BoardViewModel();
        vm.setLocalSeat(7);
        vm.attach(session, new GameEngine(new SimpleAi()));

        assertEquals(0, vm.localSeat());
        assertSame(session.getPlayer(), vm.me());
    }

    @Test
    void playableFollowsTheLocalSeatHand() {
        GameSession session = versus();
        GameEngine engine = new GameEngine(new SimpleAi());
        BoardViewModel vm = new BoardViewModel();
        vm.setLocalSeat(1);
        vm.attach(session, engine);

        Card mine = vm.me().getHand().get(0);
        // 同一条判断必须落在「我的手牌」上，而不是会话里的座位 0
        assertEquals(engine.canPlay(vm.me(), mine), vm.isPlayable(mine));
        int handSize = vm.me().getHand().size();
        vm.pruneDrawn();    // 内部走 me()：坐在 1 号位时不该去查座位 0 的手牌
        assertEquals(handSize, vm.me().getHand().size());
    }

    /** R-03：同一引擎重复 attach（进出回放）不得让监听器线性增长。 */
    @Test
    void sameEngineAttachDoesNotResubscribe() {
        GameSession session = versus();
        GameEngine engine = new GameEngine(new SimpleAi());
        BoardViewModel vm = new BoardViewModel();
        vm.attach(session, engine);
        int before = engine.eventBus().subscriberCount();

        vm.attach(session, engine);

        assertEquals(before, engine.eventBus().subscriberCount(),
                "同一引擎二次 attach 不应增加订阅者");
    }

    /** R-03：换新引擎时对新总线订阅一次（DRAW + any 共 2 个）。 */
    @Test
    void newEngineAttachSubscribesTwice() {
        GameSession session = versus();
        GameEngine engine = new GameEngine(new SimpleAi());
        BoardViewModel vm = new BoardViewModel();
        vm.attach(session, engine);

        GameEngine fresh = new GameEngine(new SimpleAi());
        vm.attach(session, fresh);

        assertEquals(2, fresh.eventBus().subscriberCount(), "新引擎应订阅 DRAW + any 共 2 个");
    }
}
