package org.example.card.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据统计 + 成就（W1）。
 *
 * 刻意不用事件订阅而用显式 {@link #recordGame}：GAME_OVER 事件的 actor 是胜者，
 * 但“胜者是不是玩家”只有界面知道（单机恒为玩家 vs AI，可未来扩展）。
 * 补偿：方法纯数据进出、零引擎依赖，无 UI 可单测——与“只读事件不改引擎”同效。
 */
public final class StatsService {

    public static final int STATS_VERSION = 1;

    /** 成就定义（id 稳定，一旦发布不再改名，存档里存的就是它）。 */
    public enum Achievement {
        FIRST_WIN("first_win", "初露锋芒", "首次获胜"),
        STREAK_3("streak_3", "乘胜追击", "三连胜"),
        STREAK_10("streak_10", "十连绝世", "十连胜"),
        EMPTY_HAND("empty_hand", "空手而归", "空手牌获胜"),
        WIPE_4("wipe_4", "满门抄斩", "一回合清空对方 ≥4 随从"),
        LOW_HP_COMEBACK("low_hp", "绝地反击", "己方英雄 ≤5 血时反杀获胜"),
        GIANT_SLAYER("giant_slayer", "巨人杀手", "击败困难 AI"),
        MARATHON_15("marathon_15", "老谋深算", "单局打满 15 回合后获胜"),
        SECOND_WIND("second_wind", "后发制人", "后手获胜"),
        PERFECT_GUARD("perfect_guard", "金身不破", "满血获胜"),
        SWIFT_5("swift_5", "速战速决", "5 回合内获胜");

        private final String id;
        private final String name;
        private final String desc;

        Achievement(String id, String name, String desc) {
            this.id = id;
            this.name = name;
            this.desc = desc;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return name;
        }

        public String desc() {
            return desc;
        }
    }

    /** 单局战报（界面在终局时组装，全部是值，无活引用）。 */
    public record GameResult(boolean won, boolean hardMode, boolean playerFirst,
                             boolean handEmpty, int maxKillsInTurn,
                             int playerLife, int turns) {
    }

    private int games;
    private int wins;
    private final Map<String, int[]> byLevel = new LinkedHashMap<>();
    private int firstGames;
    private int firstWins;
    private int secondGames;
    private int secondWins;
    private int streak;
    private int bestStreak;
    private final Set<String> achievements = new LinkedHashSet<>();

    public StatsService() {
        for (String level : List.of("EASY", "NORMAL", "HARD")) {
            byLevel.put(level, new int[2]);
        }
    }

    /**
     * 记录一局，返回本次新解锁的成就（已解锁的不重复）。
     * aiLevel 取 AiLevel.name()，未知难度归 NORMAL。
     */
    public List<Achievement> recordGame(GameResult result, String aiLevel) {
        games++;
        String level = byLevel.containsKey(aiLevel) ? aiLevel : "NORMAL";
        byLevel.get(level)[0]++;
        if (result.playerFirst()) {
            firstGames++;
        } else {
            secondGames++;
        }
        List<Achievement> fresh = new ArrayList<>();
        if (result.won()) {
            wins++;
            byLevel.get(level)[1]++;
            if (result.playerFirst()) {
                firstWins++;
            } else {
                secondWins++;
            }
            streak++;
            bestStreak = Math.max(bestStreak, streak);
        } else {
            streak = 0;
        }
        check(fresh, Achievement.FIRST_WIN, wins == 1 && result.won());
        check(fresh, Achievement.STREAK_3, streak == 3);
        check(fresh, Achievement.STREAK_10, streak >= 10 && !achievements.contains("streak_10"));
        check(fresh, Achievement.EMPTY_HAND, result.won() && result.handEmpty());
        check(fresh, Achievement.WIPE_4, result.maxKillsInTurn() >= 4);
        check(fresh, Achievement.LOW_HP_COMEBACK, result.won() && result.playerLife() <= 5);
        check(fresh, Achievement.GIANT_SLAYER, result.won() && result.hardMode());
        check(fresh, Achievement.MARATHON_15, result.won() && result.turns() >= 15);
        check(fresh, Achievement.SECOND_WIND, result.won() && !result.playerFirst());
        check(fresh, Achievement.PERFECT_GUARD, result.won() && result.playerLife() >= 20);
        check(fresh, Achievement.SWIFT_5, result.won() && result.turns() <= 5);
        return fresh;
    }

    private void check(List<Achievement> fresh, Achievement achievement, boolean condition) {
        if (condition && achievements.add(achievement.id())) {
            fresh.add(achievement);
        }
    }

    // ============ 只读快照（战绩窗用） ============

    public int games() {
        return games;
    }

    public int wins() {
        return wins;
    }

    public int winsOf(String level) {
        int[] pair = byLevel.get(level);
        return pair == null ? 0 : pair[1];
    }

    public int gamesOf(String level) {
        int[] pair = byLevel.get(level);
        return pair == null ? 0 : pair[0];
    }

    public int firstWins() {
        return firstWins;
    }

    public int firstGames() {
        return firstGames;
    }

    public int secondWins() {
        return secondWins;
    }

    public int secondGames() {
        return secondGames;
    }

    public int streak() {
        return streak;
    }

    public int bestStreak() {
        return bestStreak;
    }

    public Set<String> unlockedAchievementIds() {
        return Set.copyOf(achievements);
    }

    /** 把另一份统计搬过来（读盘用；fromJson 恒返回可搬运对象）。 */
    public void absorb(StatsService other) {
        this.games = other.games;
        this.wins = other.wins;
        for (String level : byLevel.keySet()) {
            this.byLevel.get(level)[0] = other.byLevel.get(level)[0];
            this.byLevel.get(level)[1] = other.byLevel.get(level)[1];
        }
        this.firstGames = other.firstGames;
        this.firstWins = other.firstWins;
        this.secondGames = other.secondGames;
        this.secondWins = other.secondWins;
        this.streak = other.streak;
        this.bestStreak = other.bestStreak;
        this.achievements.clear();
        this.achievements.addAll(other.achievements);
    }

    // ============ 持久化（版本化，未知字段忽略，损坏归零） ============

    public String toJson() {
        StringBuilder out = new StringBuilder();
        out.append("{\"version\":").append(STATS_VERSION);
        out.append(",\"games\":").append(games);
        out.append(",\"wins\":").append(wins);
        for (Map.Entry<String, int[]> e : byLevel.entrySet()) {
            out.append(",\"games_").append(e.getKey()).append("\":").append(e.getValue()[0]);
            out.append(",\"wins_").append(e.getKey()).append("\":").append(e.getValue()[1]);
        }
        out.append(",\"firstGames\":").append(firstGames);
        out.append(",\"firstWins\":").append(firstWins);
        out.append(",\"secondGames\":").append(secondGames);
        out.append(",\"secondWins\":").append(secondWins);
        out.append(",\"streak\":").append(streak);
        out.append(",\"bestStreak\":").append(bestStreak);
        out.append(",\"achievements\":[");
        boolean first = true;
        for (String id : achievements) {
            if (!first) {
                out.append(",");
            }
            first = false;
            out.append("\"").append(id).append("\"");
        }
        out.append("]}");
        return out.toString();
    }

    public static StatsService fromJson(String json) {
        StatsService stats = new StatsService();
        MiniJsonShim shim;
        try {
            shim = new MiniJsonShim(json);
        } catch (RuntimeException ex) {
            GameLog.warn("统计损坏，从零计数: " + ex.getMessage());
            return stats;
        }
        stats.games = shim.number("games");
        stats.wins = shim.number("wins");
        for (String level : stats.byLevel.keySet()) {
            stats.byLevel.get(level)[0] = shim.number("games_" + level);
            stats.byLevel.get(level)[1] = shim.number("wins_" + level);
        }
        stats.firstGames = shim.number("firstGames");
        stats.firstWins = shim.number("firstWins");
        stats.secondGames = shim.number("secondGames");
        stats.secondWins = shim.number("secondWins");
        stats.streak = shim.number("streak");
        stats.bestStreak = shim.number("bestStreak");
        for (String id : shim.strings("achievements")) {
            stats.achievements.add(id);
        }
        return stats;
    }

    /** data.MiniJson 的轻量适配（S8 已加宽为 public，直接复用，未知字段天然忽略）。 */
    private static final class MiniJsonShim {
        private final org.example.card.data.MiniJson.Obj root;
        private final String source;

        MiniJsonShim(String json) {
            this.source = json;
            this.root = org.example.card.data.MiniJson.parse(json, "stats").asObject();
        }

        int number(String key) {
            if (!root.contains(key)) {
                return 0;
            }
            return (int) root.get(key).asNumber().value();
        }

        List<String> strings(String key) {
            List<String> out = new ArrayList<>();
            if (!root.contains(key)) {
                return out;
            }
            org.example.card.data.MiniJson.Arr arr = root.get(key).asArray();
            for (int i = 0; i < arr.size(); i++) {
                out.add(arr.get(i).asString().value());
            }
            return out;
        }
    }
}
