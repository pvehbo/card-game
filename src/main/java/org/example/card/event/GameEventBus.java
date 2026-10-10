package org.example.card.event;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 极简事件总线：UI 按类型订阅游戏事件，引擎只负责发布。
 *
 * <p>单个订阅者抛异常**不影响**其他订阅者与游戏流程（刻意隔离：一个画错的动画
 * 不该把整局游戏带崩）；但异常**绝不静默**——会打完整堆栈并记进 {@link #subscriberErrors()}
 * 账本，否则「界面半刷新 / 横幅没出却照常跑」这类隐性故障几乎无法定位。
 *
 * <p>线程模型：总线按「发布方单线程」使用（界面在 JavaFX 线程、无界面引擎在调用方线程），
 * 订阅与发布不要在多个线程上并发进行。
 */
public class GameEventBus {

    /** 异常账本上限，避免长期运行无限增长。 */
    private static final int MAX_RECORDED_ERRORS = 32;
    /** 打堆栈的次数上限：订阅者若每个事件都炸，不能把日志刷爆。 */
    private static final int MAX_STACK_TRACES = 5;

    private final Map<GameEvent.Type, List<Consumer<GameEvent>>> listeners = new EnumMap<>(GameEvent.Type.class);
    private final List<Consumer<GameEvent>> anyListeners = new ArrayList<>();
    private final List<String> subscriberErrors = new ArrayList<>();

    /** 订阅某一类事件。 */
    public void on(GameEvent.Type type, Consumer<GameEvent> listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);
    }

    /** 订阅所有事件（用于写日志/调试）。 */
    public void onAny(Consumer<GameEvent> listener) {
        anyListeners.add(listener);
    }

    /** 测试用：当前订阅者总数（typed + any）。 */
    public int subscriberCount() {
        int n = anyListeners.size();
        for (var list : listeners.values()) {
            n += list.size();
        }
        return n;
    }

    /**
     * 订阅者异常账本（只读）。正常情况下恒为空；一旦非空就说明某个订阅者出过错——
     * 这是需要修的 bug，不是可以忽略的噪音。
     */
    public List<String> subscriberErrors() {
        return List.copyOf(subscriberErrors);
    }

    /** 清空异常账本（测试收尾用）。 */
    public void clearSubscriberErrors() {
        subscriberErrors.clear();
    }

    public void publish(GameEvent event) {
        for (Consumer<GameEvent> listener : anyListeners) {
            safeAccept(listener, event);
        }
        List<Consumer<GameEvent>> typed = listeners.get(event.type());
        if (typed != null) {
            for (Consumer<GameEvent> listener : typed) {
                safeAccept(listener, event);
            }
        }
    }

    private void safeAccept(Consumer<GameEvent> listener, GameEvent event) {
        try {
            listener.accept(event);
        } catch (RuntimeException ex) {
            String record = event.type() + ": " + ex;
            int seen = subscriberErrors.size();
            if (seen < MAX_STACK_TRACES) {
                System.err.println("[事件订阅者异常] " + record);
                ex.printStackTrace(System.err);
            } else if (seen == MAX_STACK_TRACES) {
                System.err.println("[事件订阅者异常] 后续异常只记账不再打堆栈（已打印 "
                        + MAX_STACK_TRACES + " 次）");
            }
            if (seen < MAX_RECORDED_ERRORS) {
                subscriberErrors.add(record);
            }
        }
    }
}
