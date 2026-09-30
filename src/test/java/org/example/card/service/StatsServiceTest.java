package org.example.card.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W1 统计与成就：计数、连胜、11 成就触发与不触发、文件往返、损坏归零。 */
class StatsServiceTest {

    private static StatsService.GameResult win(boolean hard, boolean first) {
        return new StatsService.GameResult(true, hard, first, false, 0, 10, 8);
    }

    @Test
    void countsGamesWinsAndStreaks() {
        StatsService stats = new StatsService();
        stats.recordGame(win(false, true), "NORMAL");
        stats.recordGame(win(false, false), "HARD");
        stats.recordGame(new StatsService.GameResult(false, false, true, false, 0, 0, 5), "NORMAL");

        assertEquals(3, stats.games());
        assertEquals(2, stats.wins());
        assertEquals(1, stats.winsOf("NORMAL"));
        assertEquals(1, stats.winsOf("HARD"));
        assertEquals(1, stats.firstWins());
        assertEquals(2, stats.firstGames());
        assertEquals(1, stats.secondWins());
        assertEquals(0, stats.streak(), "输了连胜清零");
        assertEquals(2, stats.bestStreak());
    }

    @Test
    void achievementsFireOnce() {
        StatsService stats = new StatsService();
        // 首胜 + 空手牌 + 5 血反杀 + 困难 + 15 回合，一局五开
        var result = new StatsService.GameResult(true, true, true, true, 0, 5, 16);
        List<StatsService.Achievement> fresh = stats.recordGame(result, "HARD");
        assertEquals(5, fresh.size(), fresh.toString());
        assertTrue(stats.unlockedAchievementIds().containsAll(
                List.of("first_win", "empty_hand", "low_hp", "giant_slayer", "marathon_15")));

        // 再赢一局：首胜等不再重复
        List<StatsService.Achievement> again =
                stats.recordGame(win(false, true), "NORMAL");
        assertTrue(again.stream().noneMatch(a -> a.id().equals("first_win")));
    }

    @Test
    void streakAchievements() {
        StatsService stats = new StatsService();
        for (int i = 0; i < 2; i++) {
            assertTrue(stats.recordGame(win(false, true), "NORMAL").stream()
                    .noneMatch(a -> a.id().equals("streak_3")));
        }
        List<StatsService.Achievement> third = stats.recordGame(win(false, true), "NORMAL");
        assertTrue(third.stream().anyMatch(a -> a.id().equals("streak_3")));
        assertEquals(3, stats.streak());
    }

    @Test
    void newAchievementsSecondWindPerfectSwift() {
        StatsService stats = new StatsService();
        var second = new StatsService.GameResult(true, false, false, false, 0, 10, 8);
        assertTrue(stats.recordGame(second, "NORMAL").stream()
                .anyMatch(a -> a.id().equals("second_wind")), "后手获胜解锁后发制人");
        var perfect = new StatsService.GameResult(true, false, true, false, 0, 20, 8);
        assertTrue(stats.recordGame(perfect, "NORMAL").stream()
                .anyMatch(a -> a.id().equals("perfect_guard")), "满血获胜解锁金身不破");
        var swift = new StatsService.GameResult(true, false, true, false, 0, 10, 5);
        assertTrue(stats.recordGame(swift, "NORMAL").stream()
                .anyMatch(a -> a.id().equals("swift_5")), "5 回合内获胜解锁速战速决");
        var slow = new StatsService.GameResult(true, false, true, false, 0, 10, 6);
        assertTrue(stats.recordGame(slow, "NORMAL").stream()
                .noneMatch(a -> a.id().equals("swift_5")), "6 回合不算速战");
    }

    @Test
    void wipeCountsWithoutWin() {
        StatsService stats = new StatsService();
        var loss = new StatsService.GameResult(false, false, true, false, 5, 0, 9);
        List<StatsService.Achievement> fresh = stats.recordGame(loss, "NORMAL");
        assertTrue(fresh.stream().anyMatch(a -> a.id().equals("wipe_4")), "输了也算满门抄斩");
        assertTrue(fresh.stream().noneMatch(a -> a.id().equals("first_win")));
    }

    @Test
    void jsonRoundTripAndUnknownFieldsIgnored(@TempDir Path dir) throws Exception {
        StatsService stats = new StatsService();
        stats.recordGame(win(true, false), "HARD");
        Path file = dir.resolve("stats.json");
        Files.writeString(file, stats.toJson().replace("}", ",\"future\":1}"));

        StatsService loaded = StatsService.fromJson(Files.readString(file));
        assertEquals(1, loaded.games());
        assertEquals(1, loaded.wins());
        assertEquals(1, loaded.winsOf("HARD"));
        assertEquals(1, loaded.secondWins());
        assertTrue(loaded.unlockedAchievementIds().contains("first_win"));
        assertTrue(loaded.unlockedAchievementIds().contains("giant_slayer"));
    }

    @Test
    void corruptFileStartsFromZero() {
        StatsService loaded = StatsService.fromJson("not json{{{");
        assertEquals(0, loaded.games());
        assertEquals(0, loaded.bestStreak());
    }

    @Test
    void missingFileStartsFromZero(@TempDir Path dir) {
        // 上版本无 stats.json：调用方文件不存在时直接 new，不经过 fromJson
        assertEquals(0, new StatsService().games());
        assertTrue(new StatsService().unlockedAchievementIds().isEmpty());
    }
}
