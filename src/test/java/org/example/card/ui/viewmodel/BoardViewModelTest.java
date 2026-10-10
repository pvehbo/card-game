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
 * 战场 ViewModel 契约：{@code me()/foe()} 指向会话的两个槽位，
 * 可玩判断与抽牌动画标记都落在「我的手牌」上。
 */
class BoardViewModelTest {

    private static GameSession pve() {
        return GameSession.newPveBattle(new Random(7), CardDatabase.standardDeck());
    }

    @Test
    void meAndFoeMapToSessionSlots() {
        GameSession session = pve();
        BoardViewModel vm = new BoardViewModel();
        vm.attach(session, new GameEngine(new SimpleAi()));

        assertSame(session.getPlayer(), vm.me(), "me() 是会话里的玩家槽");
        assertSame(session.getAi(), vm.foe(), "foe() 是会话里的对手槽");
    }

    @Test
    void playableFollowsMyHand() {
        GameSession session = pve();
        GameEngine engine = new GameEngine(new SimpleAi());
        BoardViewModel vm = new BoardViewModel();
        vm.attach(session, engine);

        Card mine = vm.me().getHand().get(0);
        // 同一条判断必须落在「我的手牌」上
        assertEquals(engine.canPlay(vm.me(), mine), vm.isPlayable(mine));
        int handSize = vm.me().getHand().size();
        vm.pruneDrawn();    // 内部走 me()，不该去查别人的手牌
        assertEquals(handSize, vm.me().getHand().size());
    }

    /** R-03：同一引擎重复 attach（进出回放）不得让监听器线性增长。 */
    @Test
    void sameEngineAttachDoesNotResubscribe() {
        GameSession session = pve();
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
        GameSession session = pve();
        GameEngine engine = new GameEngine(new SimpleAi());
        BoardViewModel vm = new BoardViewModel();
        vm.attach(session, engine);

        GameEngine fresh = new GameEngine(new SimpleAi());
        vm.attach(session, fresh);

        assertEquals(2, fresh.eventBus().subscriberCount(), "新引擎应订阅 DRAW + any 共 2 个");
    }
}
