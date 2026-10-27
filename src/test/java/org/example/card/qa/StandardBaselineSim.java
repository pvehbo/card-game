package org.example.card.qa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.example.card.ai.AggroAi;
import org.example.card.ai.AiStrategy;
import org.example.card.ai.SimpleAi;
import org.example.card.ai.TempoAi;
import org.example.card.data.CardDatabase;
import org.example.card.data.Ruleset;
import org.example.card.engine.GameEngine;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.PlayerState;

/**
 * 标准模式基准模拟器（路线图 V-B / T-B5–T-B6）。
 *
 * <p>用途：三基准构筑 A/M/D 的镜像与交叉对阵，按“同种子换边”配对，
 * 输出决定局胜率、平局率、超限率、局长中位数/P90 与剩余生命差。
 *
 * <p>口径：一轮 = 双方各完成一个玩家回合；一方在轮内斩杀时本轮仍计入局长。
 * 双归零记平局；打满整轮上限仍未终局记超限，不按血量补造胜者。
 *
 * <p>运行：
 * {@code java -cp target/classes:target/test-classes org.example.card.qa.StandardBaselineSim --pairs 250}
 */
public final class StandardBaselineSim {

    private static final Ruleset RULES = Ruleset.STANDARD;
    private static final int DEFAULT_PAIRS = 250;
    private static final int DEFAULT_CAP = 30;
    private static final String[] DECKS = {"A", "M", "D"};

    private StandardBaselineSim() {
    }

    /** 单局结果：winner ∈ {A,B}，draw/over 时 winner 无意义。 */
    public record GameResult(String winner, boolean draw, boolean over, int rounds,
                             int aLife, int bLife) {
    }

    /** 一组对阵的汇总。 */
    public record Matchup(String a, String b, int games, int aWins, int bWins,
                          int draws, int overs, double aDecidedWinRate,
                          double medianRounds, int p90Rounds, double avgLifeDiff) {
    }

    public static void main(String[] args) throws IOException {
        int pairs = intArg(args, "--pairs", DEFAULT_PAIRS);
        int cap = intArg(args, "--cap", DEFAULT_CAP);
        Path out = Path.of(strArg(args, "--out", "docs/BALANCE-BASELINE-v0.2.md"));
        String strategy = strArg(args, "--strategy", "archetype");

        String report;
        if ("all".equals(strategy)) {
            Map<String, List<Matchup>> byStrategy = new LinkedHashMap<>();
            for (String s : List.of("simple", "archetype", "face")) {
                byStrategy.put(s, runAll(pairs, cap, s));
            }
            report = renderAll(byStrategy, pairs, cap);
        } else {
            report = render(runAll(pairs, cap, strategy), pairs, cap, strategy);
        }
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.println(report);
        System.out.println("WROTE " + out.toAbsolutePath());
    }

    /** 跑全部 6 组对阵（3 镜像 + 3 交叉），每组 pairs 对换边种子。 */
    public static List<Matchup> runAll(int pairs, int cap, String strategy) {
        List<Matchup> results = new ArrayList<>();
        for (int i = 0; i < DECKS.length; i++) {
            for (int j = i; j < DECKS.length; j++) {
                results.add(runMatchup(DECKS[i], DECKS[j], pairs, cap, strategy));
            }
        }
        return results;
    }

    public static Matchup runMatchup(String a, String b, int pairs, int cap, String strategy) {
        List<Card> deckA = CardDatabase.foundationDeck(a);
        List<Card> deckB = CardDatabase.foundationDeck(b);
        int aWins = 0;
        int bWins = 0;
        int draws = 0;
        int overs = 0;
        List<Integer> rounds = new ArrayList<>();
        long lifeDiff = 0;
        int decided = 0;

        for (int pair = 1; pair <= pairs; pair++) {
            long seedA = pair * 2L + 1;
            long seedB = pair * 2L + 2;
            for (boolean aFirst : new boolean[]{true, false}) {
                GameResult r = playGame(deckA, deckB, aFirst, seedA, seedB, cap, strategy, a, b);
                if (r.draw()) {
                    draws++;
                } else if (r.over()) {
                    overs++;
                } else {
                    decided++;
                    rounds.add(r.rounds());
                    lifeDiff += r.aLife() - r.bLife();
                    if (r.winner().equals("A")) {
                        aWins++;
                    } else {
                        bWins++;
                    }
                }
            }
        }

        List<Integer> sorted = new ArrayList<>(rounds);
        Collections.sort(sorted);
        double median = sorted.isEmpty() ? 0 : median(sorted);
        int p90 = sorted.isEmpty() ? 0 : sorted.get(Math.min(sorted.size() - 1,
                (int) Math.ceil(sorted.size() * 0.9) - 1));
        double winRate = decided == 0 ? 0 : aWins / (double) decided;
        double avgDiff = decided == 0 ? 0 : lifeDiff / (double) decided;
        return new Matchup(a, b, pairs * 2, aWins, bWins, draws, overs,
                round4(winRate), median, p90, round4(avgDiff));
    }

    /** 一局：A/B 两套牌，aFirst 指定座位；换边时使用同一对牌序种子。 */
    public static GameResult playGame(List<Card> deckA, List<Card> deckB, boolean aFirst,
                                      long seedA, long seedB, int cap, String strategy,
                                      String keyA, String keyB) {
        PlayerState a = new PlayerState("A", new Deck(copyOf(deckA)),
                RULES.heroLife(), RULES.maxManaCap());
        PlayerState b = new PlayerState("B", new Deck(copyOf(deckB)),
                RULES.heroLife(), RULES.maxManaCap());
        a.getDeck().shuffle(new Random(seedA));
        b.getDeck().shuffle(new Random(seedB));
        for (int i = 0; i < RULES.openingHand(); i++) {
            a.getDeck().draw().ifPresent(a.getHand()::add);
            b.getDeck().draw().ifPresent(b.getHand()::add);
        }

        GameEngine engineA = new GameEngine(newStrategy(strategy, keyA), RULES);
        GameEngine engineB = new GameEngine(newStrategy(strategy, keyB), RULES);
        java.util.function.Consumer<String> quiet = msg -> {
        };

        // 后手补偿：先手先开回合，故后手在第一次 playTurn 前多抽。
        PlayerState second = aFirst ? b : a;
        GameEngine secondEngine = aFirst ? engineB : engineA;
        secondEngine.grantSecondMoveBonus(second, RULES.secondMoveBonusDraws(), quiet);

        int rounds = 1;
        while (rounds <= cap) {
            GameResult early = playHalf(aFirst ? a : b, aFirst ? b : a,
                    aFirst ? engineA : engineB, rounds, a, b);
            if (early != null) {
                return early;
            }
            GameResult late = playHalf(aFirst ? b : a, aFirst ? a : b,
                    aFirst ? engineB : engineA, rounds, a, b);
            if (late != null) {
                return late;
            }
            rounds++;
        }
        return new GameResult("", false, true, cap, a.getLifePoints(), b.getLifePoints());
    }

    /** 一方行动一次；终局立即返回，否则 null。 */
    private static GameResult playHalf(PlayerState self, PlayerState foe, GameEngine engine,
                                       int rounds, PlayerState a, PlayerState b) {
        engine.playTurn(self, foe, msg -> {
        });
        return terminal(a, b, rounds);
    }

    private static GameResult terminal(PlayerState a, PlayerState b, int rounds) {
        boolean aDead = a.isDefeated();
        boolean bDead = b.isDefeated();
        if (aDead && bDead) {
            return new GameResult("", true, false, rounds, a.getLifePoints(), b.getLifePoints());
        }
        if (aDead) {
            return new GameResult("B", false, false, rounds, a.getLifePoints(), b.getLifePoints());
        }
        if (bDead) {
            return new GameResult("A", false, false, rounds, a.getLifePoints(), b.getLifePoints());
        }
        return null;
    }

    private static AiStrategy newStrategy(String name, String deckKey) {
        return switch (name) {
            case "archetype" -> "A".equals(deckKey) ? new TempoAi() : new SimpleAi();
            case "face" -> "A".equals(deckKey) ? new AggroAi() : new SimpleAi();
            case "simple", "normal" -> new SimpleAi();
            default -> throw new IllegalArgumentException(
                    "未知策略：" + name + "（支持 archetype / face / simple）");
        };
    }

    private static List<Card> copyOf(List<Card> cards) {
        List<Card> copies = new ArrayList<>();
        for (Card card : cards) {
            copies.add(card.copy());
        }
        return copies;
    }

    private static double median(List<Integer> sorted) {
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2)
                : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static double round4(double value) {
        return Math.round(value * 10000) / 10000.0;
    }

    private static int intArg(String[] args, String key, int fallback) {
        String raw = strArg(args, key, null);
        return raw == null ? fallback : Integer.parseInt(raw);
    }

    private static String strArg(String[] args, String key, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    /** 三种策略的对照报告：先给每组一张表，再给策略敏感性区间。 */
    public static String renderAll(Map<String, List<Matchup>> byStrategy, int pairs, int cap) {
        StringBuilder md = new StringBuilder();
        md.append("# 标准模式基线报告（自动生成）\n\n");
        md.append("> 生成器：`StandardBaselineSim`；每组 ").append(pairs)
                .append(" 对换边种子（").append(pairs * 2).append(" 局）；整轮上限 ")
                .append(cap).append("。\n\n");
        md.append("## 为什么要跑三种策略\n\n");
        md.append("同一套牌在“无脑交换 / 节奏 / 无脑打脸”下的胜率可能反向。"
                + "只报一种策略会把 AI 偏好误当成卡牌强度，因此这里同时给出区间。\n\n");
        md.append("## 口径\n\n");
        md.append("- 一轮 = 双方各完成一个玩家回合；轮内斩杀计入本轮。\n");
        md.append("- 双归零 = 平局；打满上限未终局 = 超限，不按血量补造胜者。\n");
        md.append("- 决定局胜率 = A 胜 /（A 胜 + B 胜），平局与超限不入分母。\n\n");
        for (Map.Entry<String, List<Matchup>> e : byStrategy.entrySet()) {
            md.append("## 策略：").append(e.getKey()).append("\n\n");
            md.append(strategyNote(e.getKey())).append("\n\n");
            appendTable(md, e.getValue());
        }
        md.append("## 策略敏感性（同一对阵的 A 决定局胜率区间）\n\n");
        List<Matchup> base = byStrategy.values().iterator().next();
        for (Matchup m : base) {
            double min = 1;
            double max = 0;
            for (List<Matchup> list : byStrategy.values()) {
                for (Matchup x : list) {
                    if (x.a().equals(m.a()) && x.b().equals(m.b())) {
                        min = Math.min(min, x.aDecidedWinRate());
                        max = Math.max(max, x.aDecidedWinRate());
                    }
                }
            }
            md.append("- ").append(m.a()).append(" vs ").append(m.b()).append("：")
                    .append(String.format("%.1f%%–%.1f%%", min * 100, max * 100));
            if (max - min >= 0.20) {
                md.append("（≥20 个百分点，策略敏感，暂不能据此调卡）");
            }
            md.append("\n");
        }
        md.append("\n## 判读提醒\n\n");
        md.append("- 这是基线，不是平衡通过证明；未达门禁时按路线图 T-B7 小批调参。\n");
        md.append("- 策略敏感性 ≥20 个百分点的对阵，先改评测策略/AI，不要先改卡。\n");
        md.append("- 样本为配对换边，区间估计应以“种子对”为单位。\n");
        return md.toString();
    }

    private static String strategyNote(String strategy) {
        return switch (strategy) {
            case "simple" -> "双方都用 SimpleAi：有随从就优先交换，几乎不主动打脸（控制偏好）。";
            case "archetype" -> "A 用 TempoAi（能便宜交换才换，否则打脸），M/D 用 SimpleAi。";
            case "face" -> "A 用 AggroAi（无嘲讽一律打脸），M/D 用 SimpleAi。";
            default -> "";
        };
    }

    private static void appendTable(StringBuilder md, List<Matchup> results) {
        md.append("| 对阵 | 局数 | A 胜 | B 胜 | 平局 | 超限 | A 决定局胜率 | 局长中位 | 局长 P90 | 平均生命差(A-B) |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Matchup m : results) {
            md.append("| ").append(m.a()).append(" vs ").append(m.b())
                    .append(" | ").append(m.games())
                    .append(" | ").append(m.aWins())
                    .append(" | ").append(m.bWins())
                    .append(" | ").append(m.draws())
                    .append(" | ").append(m.overs())
                    .append(" | ").append(String.format("%.2f%%", m.aDecidedWinRate() * 100))
                    .append(" | ").append(String.format("%.1f", m.medianRounds()))
                    .append(" | ").append(m.p90Rounds())
                    .append(" | ").append(String.format("%.2f", m.avgLifeDiff()))
                    .append(" |\n");
        }
        md.append("\n");
    }

    /** 生成 Markdown 报告（仓库文档，允许表格）。 */
    public static String render(List<Matchup> results, int pairs, int cap, String strategy) {
        StringBuilder md = new StringBuilder();
        md.append("# 标准模式基线报告（自动生成）\n\n");
        md.append("> 生成器：`StandardBaselineSim`；策略：").append(strategy)
                .append("；每组 ").append(pairs).append(" 对换边种子（")
                .append(pairs * 2).append(" 局）；整轮上限 ").append(cap).append("。\n\n");
        md.append("## 口径\n\n");
        md.append("- 一轮 = 双方各完成一个玩家回合；轮内斩杀计入本轮。\n");
        md.append("- 双归零 = 平局；打满上限未终局 = 超限，不按血量补造胜者。\n");
        md.append("- 决定局胜率 = A 胜 /（A 胜 + B 胜），平局与超限不入分母。\n\n");
        md.append("## 结果\n\n");
        md.append("| 对阵 | 局数 | A 胜 | B 胜 | 平局 | 超限 | A 决定局胜率 | 局长中位 | 局长 P90 | 平均生命差(A-B) |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Matchup m : results) {
            md.append("| ").append(m.a()).append(" vs ").append(m.b())
                    .append(" | ").append(m.games())
                    .append(" | ").append(m.aWins())
                    .append(" | ").append(m.bWins())
                    .append(" | ").append(m.draws())
                    .append(" | ").append(m.overs())
                    .append(" | ").append(String.format("%.2f%%", m.aDecidedWinRate() * 100))
                    .append(" | ").append(String.format("%.1f", m.medianRounds()))
                    .append(" | ").append(m.p90Rounds())
                    .append(" | ").append(String.format("%.2f", m.avgLifeDiff()))
                    .append(" |\n");
        }
        md.append("\n## 判读提醒\n\n");
        md.append("- 本报告只反映所选评测策略，不代表人类体验或困难 AI 强度。\n");
        md.append("- `archetype`：A 用 TempoAi（能便宜交换才换，否则打脸），M/D 用 SimpleAi。\n");
        md.append("- 镜像对阵的 A 胜率即先手偏差；交叉对阵的 A 胜率包含牌组强度差。\n");
        md.append("- 样本为配对换边，区间估计应以“种子对”为单位，不能把每局当独立样本。\n");
        md.append("- 这是基线，不是平衡通过证明；未达门禁时按路线图 T-B7 小批调参。\n");
        return md.toString();
    }

    /** 便于测试/外部读取的键值摘要。 */
    public static Map<String, Object> summary(List<Matchup> results) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Matchup m : results) {
            map.put(m.a() + "-" + m.b(), m);
        }
        return map;
    }
}
