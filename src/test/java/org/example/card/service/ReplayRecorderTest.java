package org.example.card.service;

import org.example.card.ai.SimpleAi;
import org.example.card.engine.GameEngine;
import org.example.card.engine.GameSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-6 回放记录器：回合快照序列可逐个还原，文件往返一致。 */
class ReplayRecorderTest {

    @Test
    void snapshotsRestoreInOrder() {
        GameSession session = GameSession.newPveBattle(new Random(5));
        GameEngine engine = new GameEngine(new SimpleAi());
        ReplayRecorder recorder = new ReplayRecorder();
        List<String> logs = new ArrayList<>();
        engine.eventBus().onAny(recorder::recordEvent);

        recorder.captureTurn(session);
        engine.startPlayerTurn(session.getPlayer(), session.getAi(), logs::add);
        recorder.captureTurn(session);
        engine.endPlayerTurn(session.getPlayer(), session.getAi(), logs::add);

        assertEquals(2, recorder.turns());
        assertTrue(recorder.events() > 0, "应记下事件流");

        GameSession first = recorder.snapshot(0);
        GameSession second = recorder.snapshot(1);
        assertEquals(3, first.getPlayer().getHand().size(), "首快照是开局 3 张");
        assertEquals(4, second.getPlayer().getHand().size(), "次快照是抽牌后 4 张");
        assertEquals(session.getPlayer().getDeck().size(),
                second.getPlayer().getDeck().size());
    }

    @Test
    void fileRoundTrip() throws Exception {
        GameSession session = GameSession.newPveBattle(new Random(6));
        ReplayRecorder recorder = new ReplayRecorder();
        recorder.captureTurn(session);
        Path tmp = Files.createTempFile("replay", ".txt");
        try {
            recorder.saveToFile(tmp);
            ReplayRecorder loaded = ReplayRecorder.loadFromFile(tmp);
            assertEquals(1, loaded.turns());
            assertEquals(session.getPlayer().getHand().size(),
                    loaded.snapshot(0).getPlayer().getHand().size());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void missingFileLoadsEmpty() {
        ReplayRecorder loaded = ReplayRecorder.loadFromFile(
                Path.of(System.getProperty("java.io.tmpdir"), "no-such-replay-xyz.txt"));
        assertEquals(0, loaded.turns());
        assertEquals(0, loaded.events());
    }
}
