package org.example.card.event;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件总线契约（R-13）：订阅者异常必须**被隔离**（不影响其它订阅者与游戏流程），
 * 同时**被记账**（不能静默吞掉，否则「界面半刷新却照常跑」这类隐性故障无法定位）。
 */
class GameEventBusTest {

    @Test
    void subscriberFailureIsIsolatedAndRecorded() {
        GameEventBus bus = new GameEventBus();
        List<String> delivered = new ArrayList<>();
        bus.onAny(event -> {
            throw new IllegalStateException("订阅者炸了");
        });
        bus.on(GameEvent.Type.DRAW, event -> delivered.add(event.message()));

        bus.publish(GameEvent.of(GameEvent.Type.DRAW, "抽卡"));

        assertEquals(List.of("抽卡"), delivered, "一个订阅者抛异常不该影响其它订阅者");
        assertEquals(1, bus.subscriberErrors().size(), "异常必须被记账，不能静默吞掉");
        assertTrue(bus.subscriberErrors().get(0).contains("订阅者炸了"),
                "账本要留下可定位的信息");
    }

    @Test
    void healthyBusRecordsNothing() {
        GameEventBus bus = new GameEventBus();
        List<GameEvent.Type> seen = new ArrayList<>();
        bus.onAny(event -> seen.add(event.type()));
        bus.on(GameEvent.Type.DRAW, event -> {
        });

        bus.publish(GameEvent.of(GameEvent.Type.DRAW, "抽卡"));

        assertEquals(1, seen.size());
        assertTrue(bus.subscriberErrors().isEmpty(), "正常路径不该产生异常记录");
    }

    @Test
    void errorLedgerIsBoundedAndClearable() {
        GameEventBus bus = new GameEventBus();
        bus.onAny(event -> {
            throw new IllegalStateException("boom");
        });

        // 订阅者每个事件都炸时，账本必须有上限，否则长期运行会无限增长
        for (int i = 0; i < 40; i++) {
            bus.publish(GameEvent.of(GameEvent.Type.DRAW, "抽卡"));
        }

        assertEquals(32, bus.subscriberErrors().size(), "账本封顶在 32 条");
        bus.clearSubscriberErrors();
        assertTrue(bus.subscriberErrors().isEmpty());
    }
}
