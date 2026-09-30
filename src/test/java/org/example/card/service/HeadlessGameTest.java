package org.example.card.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v2.0 M0「引擎可嵌入化体检」：整局对局必须能在没有任何 JavaFX 初始化的情况下跑完，
 * 且引擎/服务侧字节码里不许出现 JavaFX 引用（联机服务端要的结果）。
 */
class HeadlessGameTest {

    @Test
    void runsTwentyRoundsWithoutUi() {
        HeadlessGame.Result result = HeadlessGame.run(7L, 20, org.example.card.ai.AiLevel.NORMAL,
                msg -> {
                }, true);

        System.out.println("HEADLESS_SUMMARY " + result.summary());
        assertTrue(result.rounds() > 0, "至少要跑一个回合");
        assertTrue(result.rounds() <= 20, "不得超过回合上限");
        assertTrue(result.engineTurns() > 0, "引擎回合数应有推进");
        assertTrue(result.eventCount() > 0, "事件总线应有事件");
        assertTrue(result.log().stream().anyMatch(m -> m.contains("回合")), "战报应含回合标记");
        if (result.gameOver()) {
            assertTrue(result.decided(), "终局必须分出胜负");
        }
    }

    @Test
    void sameSeedProducesSameOutcome() {
        String first = HeadlessGame.run(42L, 20, org.example.card.ai.AiLevel.HARD).summary();
        String second = HeadlessGame.run(42L, 20, org.example.card.ai.AiLevel.HARD).summary();
        assertEquals(first, second, "同种子必须完全可复现（联机仲裁依赖这条）");
    }

    @Test
    void manySeedsAllTerminateOrHitCap() {
        for (long seed = 1; seed <= 10; seed++) {
            HeadlessGame.Result result = HeadlessGame.run(seed, 20, org.example.card.ai.AiLevel.NORMAL);
            assertTrue(result.rounds() <= 20);
            assertTrue(result.playerLife() >= 0 && result.aiLife() >= 0);
        }
    }

    @Test
    void engineSideClassesDoNotReferenceJavafx() {
        List<Class<?>> engineSide = List.of(
                org.example.card.engine.GameEngine.class,
                org.example.card.engine.GameSession.class,
                org.example.card.engine.TurnController.class,
                org.example.card.engine.ActionValidator.class,
                org.example.card.engine.CombatResolver.class,
                org.example.card.model.PlayerState.class,
                org.example.card.model.Deck.class,
                org.example.card.data.CardDatabase.class,
                org.example.card.data.DeckBuilder.class,
                org.example.card.ai.SimpleAi.class,
                org.example.card.ai.HardAi.class,
                org.example.card.ai.AiLevel.class,
                org.example.card.event.GameEvent.class,
                org.example.card.event.GameEventBus.class,
                org.example.card.service.HeadlessGame.class,
                org.example.card.service.SaveService.class,
                org.example.card.service.ConfigService.class,
                org.example.card.service.StatsService.class,
                org.example.card.service.MatchReferee.class,
                // M1 协议层也必须能在无图形环境里跑（服务端要直接复用）
                org.example.card.net.Json.class,
                org.example.card.net.Json.Reader.class,
                org.example.card.net.Protocol.class,
                org.example.card.net.NetMessage.class,
                org.example.card.net.NetMessage.Action.class,
                org.example.card.net.NetMessage.Sync.class,
                org.example.card.net.MessageCodec.class,
                // M2 房间服务端：服务端要和引擎打进同一个 jar，同样不许碰 JavaFX
                org.example.card.server.GameRoom.class,
                org.example.card.server.RoomServer.class);
        for (Class<?> type : engineSide) {
            assertNoJavafx(type);
        }
    }

    /** 直接看 class 文件的常量池：出现 "javafx/" 就说明这条路径绑死了图形环境。 */
    private static void assertNoJavafx(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertNotNull(in, "找不到字节码：" + resource);
            String pool = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(pool.contains("javafx/"), type.getName() + " 的字节码引用了 JavaFX");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
