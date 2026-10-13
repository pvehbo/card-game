package org.example.card.data;

import org.example.card.model.Card;
import org.example.card.model.EffectSpec;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.SpellCard;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 卡牌数据加载器（S5）：从 classpath 的 resources/cards/*.json 读入三张卡表，
 * 严格校验后提供与 CardGameApp.demoDeck 同内容同数量的标准牌堆（10 种 × 2 = 20 张）。
 * 校验失败 fail-fast 抛 {@link IllegalStateException}，消息含"文件:行:原因"。
 * 本包（org.example.card.data）不依赖 JavaFX，可脱离 UI 独立测试。
 */
public final class CardDatabase {

    private static final long SCHEMA_VERSION = 1;
    /** copies 缺省值（老卡行为：每种 2 份）。 */
    private static final int DEFAULT_COPIES = 2;

    private CardDatabase() {
    }

    /** 卡模板 + 双层份数（批2数据契约）：deckCopies=标准牌堆入堆份数；copyLimit=构筑同种卡上限。 */
    public record CardEntry(Card template, int deckCopies, int copyLimit) {
    }

    /**
     * 从 classpath 读取 minions.json / spells.json / pets.json，
     * 返回全部基础卡模板（id 全局唯一，含跨文件校验）。
     */
    public static List<Card> load() {
        List<Card> all = new ArrayList<>();
        for (CardEntry entry : loadEntries()) {
            all.add(entry.template());
        }
        return all;
    }

    /** 同 load，但保留每种卡的入堆份数（组牌堆用）。 */
    public static List<CardEntry> loadEntries() {
        Map<String, String> idSource = new LinkedHashMap<>();
        List<CardEntry> all = new ArrayList<>();
        all.addAll(loadFile("/cards/minions.json", idSource));
        all.addAll(loadFile("/cards/spells.json", idSource));
        all.addAll(loadFile("/cards/pets.json", idSource));
        return all;
    }

    /** 标准池（批3）：读取各卡表的 standard 覆盖块，按候选表值解析标准条目。 */
    public static List<CardEntry> loadStandardEntries() {
        Map<String, String> idSource = new LinkedHashMap<>();
        List<CardEntry> all = new ArrayList<>();
        all.addAll(loadStandardFile("/cards/minions.json", idSource));
        all.addAll(loadStandardFile("/cards/spells.json", idSource));
        all.addAll(loadStandardFile("/cards/pets.json", idSource));
        return all;
    }

    /** 读单份卡表的 standard 覆盖块（只收集带 standard 键的卡，id 跨文件查重）。 */
    private static List<CardEntry> loadStandardFile(String path, Map<String, String> idSource) {
        String fileName = path.startsWith("/") ? path.substring(1) : path;
        MiniJson.Obj root = MiniJson.parse(readResource(path), fileName).asObject();
        long schemaVersion = longField(root, "schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalStateException(root.atKey("schemaVersion",
                    "不支持的 schemaVersion=" + schemaVersion + "，期望 " + SCHEMA_VERSION));
        }
        MiniJson.Arr cards = needField(root, "cards").asArray();
        List<CardEntry> result = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            MiniJson.Obj cardObj = cards.get(i).asObject();
            if (!cardObj.contains("standard")) {
                continue;
            }
            CardEntry entry = parseEntry(cardObj, cardObj.get("standard").asObject());
            String id = entry.template().getId();
            String firstSource = idSource.putIfAbsent(id, fileName);
            if (firstSource != null) {
                throw new IllegalStateException(cardObj.atKey("id",
                        "id 重复：" + id + "（首次出现于 " + firstSource + "）"));
            }
            result.add(entry);
        }
        return result;
    }

    /**
     * 标准牌堆（经典口径）：每种卡按 deckCopies 入堆，每份都是全新实例（受伤标记不共享）。
     * 老卡默认 2 份；B-3 明星单卡 1 份。
     */
    public static List<Card> standardDeck() {
        return deck(Ruleset.CLASSIC);
    }

    /** 按规则集组默认牌堆（批3）：经典 = 40 张标准牌堆；标准 = 30 张（标准 deckCopies 合计 30，零费特权已取消）。 */
    public static List<Card> deck(Ruleset ruleset) {
        List<Card> deck = new ArrayList<>();
        for (CardEntry entry : entries(ruleset)) {
            for (int i = 0; i < entry.deckCopies(); i++) {
                deck.add(entry.template().copy());
            }
        }
        return deck;
    }

    /** 按规则集取卡池（批3）：经典 = 历史数值（loadEntries）；标准 = 候选表覆盖值（loadStandardEntries）。 */
    public static List<CardEntry> entries(Ruleset ruleset) {
        return ruleset == Ruleset.STANDARD ? loadStandardEntries() : loadEntries();
    }

    /**
     * 三基准构筑（提案 §6.4，批3）：key 支持 A/快攻、M/中速、D/防御。
     * 全部用标准模板组 30 张；id 未知、同种超上限、张数不对都 fail-fast。
     */
    public static List<Card> foundationDeck(String key) {
        String name = normalizeFoundation(key);
        Map<String, Integer> composition = FOUNDATIONS.get(name);
        if (composition == null) {
            throw new IllegalStateException("未知基准构筑：" + key + "（可用 A/快攻、M/中速、D/防御）");
        }
        Map<String, Integer> pending = new LinkedHashMap<>(composition);
        List<Card> deck = new ArrayList<>();
        for (CardEntry entry : loadStandardEntries()) {
            Integer count = pending.remove(entry.template().getId());
            if (count == null) {
                continue;
            }
            if (count > entry.copyLimit()) {
                throw new IllegalStateException("基准构筑违反同种上限：" + entry.template().getId() + " ×" + count);
            }
            for (int i = 0; i < count; i++) {
                deck.add(entry.template().copy());
            }
        }
        if (!pending.isEmpty()) {
            throw new IllegalStateException("基准构筑引用未知卡：" + pending.keySet());
        }
        if (deck.size() != DeckBuilder.DECK_SIZE) {
            throw new IllegalStateException("基准构筑张数非法：" + name + " = " + deck.size());
        }
        return deck;
    }

    private static String normalizeFoundation(String key) {
        return switch (key) {
            case "A", "快攻" -> "A";
            case "M", "中速" -> "M";
            case "D", "防御" -> "D";
            default -> key;
        };
    }

    /** 三基准构筑面板（提案 §6.4）：id → 张数。 */
    private static final Map<String, Map<String, Integer>> FOUNDATIONS = buildFoundations();

    private static Map<String, Map<String, Integer>> buildFoundations() {
        Map<String, Map<String, Integer>> all = new LinkedHashMap<>();
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put("m1", 2); a.put("m6", 2); a.put("m13", 2); a.put("m3", 2); a.put("m4", 2);
        a.put("m11", 2); a.put("m14", 2); a.put("m15", 2); a.put("m10", 2); a.put("m8", 2);
        a.put("s1", 2); a.put("s6", 2); a.put("s3", 2); a.put("s5", 2);
        a.put("p1", 1); a.put("p3", 1);
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("m1", 2); m.put("m6", 2); m.put("m2", 2); m.put("m3", 2); m.put("m4", 2);
        m.put("m10", 2); m.put("m15", 2); m.put("m16", 2); m.put("m5", 2); m.put("m8", 1); m.put("m7", 1);
        m.put("s1", 1); m.put("s6", 2); m.put("s3", 1); m.put("s2", 1); m.put("s5", 2);
        m.put("p1", 1); m.put("p4", 1); m.put("p5", 1);
        Map<String, Integer> d = new LinkedHashMap<>();
        d.put("m2", 2); d.put("m3", 2); d.put("m9", 2); d.put("m10", 2); d.put("m12", 2);
        d.put("m15", 2); d.put("m16", 2); d.put("m5", 2); d.put("m7", 1); d.put("m17", 1);
        d.put("s1", 2); d.put("s2", 2); d.put("s4", 2); d.put("s5", 2); d.put("s8", 2);
        d.put("p2", 1); d.put("p4", 1);
        all.put("A", a); all.put("M", m); all.put("D", d);
        return all;
    }

    private static List<CardEntry> loadFile(String path, Map<String, String> idSource) {
        String fileName = path.startsWith("/") ? path.substring(1) : path;
        return entriesFrom(readResource(path), fileName, idSource);
    }

    /**
     * 测试入口：解析单份 JSON（sourceName 作为错误消息中的文件名）。
     * 校验：schemaVersion==1、id 唯一、数值非负、法术 kind 合法且 amount&gt;0、
     * keywords ⊆ {@link Keyword} 枚举、deckCopies/copyLimit 缺省 2 且只能是 1～2、
     * 战吼/亡语 spec 的 kind ∈ DAMAGE/HEAL/DRAW 且需配套关键词。
     */
    public static List<Card> loadFrom(String json, String sourceName) {
        List<Card> result = new ArrayList<>();
        for (CardEntry entry : entriesFrom(json, sourceName, new LinkedHashMap<>())) {
            result.add(entry.template());
        }
        return result;
    }

    /** 测试入口：与 loadFrom 同校验，但保留双层份数（deckCopies/copyLimit 与效果 spec）。 */
    public static List<CardEntry> loadEntriesFrom(String json, String sourceName) {
        return entriesFrom(json, sourceName, new LinkedHashMap<>());
    }

    private static List<CardEntry> entriesFrom(String json, String sourceName,
                                              Map<String, String> idSource) {
        MiniJson.Obj root = MiniJson.parse(json, sourceName).asObject();
        long schemaVersion = longField(root, "schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalStateException(root.atKey("schemaVersion",
                    "不支持的 schemaVersion=" + schemaVersion + "，期望 " + SCHEMA_VERSION));
        }
        MiniJson.Arr cards = needField(root, "cards").asArray();
        List<CardEntry> result = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            MiniJson.Obj cardObj = cards.get(i).asObject();
            CardEntry entry = parseEntry(cardObj);
            String id = entry.template().getId();
            String firstSource = idSource.putIfAbsent(id, sourceName);
            if (firstSource != null) {
                throw new IllegalStateException(cardObj.atKey("id",
                        "id 重复：" + id + "（首次出现于 " + firstSource + "）"));
            }
            result.add(entry);
        }
        return result;
    }

    /** 按字段区分卡型并解析（经典数值；标准覆盖见重载）。 */
    private static CardEntry parseEntry(MiniJson.Obj obj) {
        return parseEntry(obj, null);
    }

    /**
     * 解析一张卡；std 为标准覆盖块（批3候选表），可空。
     * 字段一律 std 优先：text/cost/keywords/数值/效果 spec/deckCopies/copyLimit。
     * deckCopies：经典 1～2；标准 0～2（0 = 不进默认牌堆，但构筑仍可加）。
     * copyLimit：标准缺省 2（m7/m17/宠物在数据里显式给 1）。
     */
    private static CardEntry parseEntry(MiniJson.Obj obj, MiniJson.Obj std) {
        String id = strField(obj, "id");
        String name = strField(obj, "name");
        String text = std != null && std.contains("text") ? strField(std, "text") : strField(obj, "text");
        long cost = std != null && std.contains("cost") ? longField(std, "cost") : longField(obj, "cost");
        if (cost < 0) {
            throw new IllegalStateException(place(std, obj, "cost").atKey("cost", "cost 不能为负数：" + cost));
        }
        List<Keyword> keywords = std != null && std.contains("keywords")
                ? parseKeywords(std.get("keywords").asArray())
                : parseKeywords(needField(obj, "keywords").asArray());
        int deckCopies = std != null && std.contains("deckCopies") ? intField(std, "deckCopies")
                : obj.contains("deckCopies") ? intField(obj, "deckCopies")
                : obj.contains("copies") ? intField(obj, "copies") : DEFAULT_COPIES;
        if (deckCopies < (std == null ? 1 : 0) || deckCopies > 2) {
            throw new IllegalStateException(place(std, obj, "deckCopies").atKey("deckCopies",
                    (std == null ? "deckCopies 只能是 1～2：" : "标准 deckCopies 只能是 0～2：") + deckCopies));
        }
        int copyLimit = std != null && std.contains("copyLimit") ? intField(std, "copyLimit")
                : std != null ? DeckBuilder.MAX_COPIES
                : obj.contains("copyLimit") ? intField(obj, "copyLimit")
                : Math.min(DeckBuilder.MAX_COPIES, deckCopies);
        if (copyLimit < 1 || copyLimit > DeckBuilder.MAX_COPIES) {
            throw new IllegalStateException(place(std, obj, "copyLimit").atKey("copyLimit",
                    "copyLimit 只能是 1～" + DeckBuilder.MAX_COPIES + "：" + copyLimit));
        }
        Card card;
        if (hasAny(std, obj, List.of("kind", "amount"))) {
            card = parseSpell(obj, std, id, name, text, cost, keywords);
        } else if (hasAny(std, obj, List.of("attackBonus", "healthBonus"))) {
            card = parsePet(obj, std, id, name, text, cost, keywords);
        } else {
            card = parseMinion(obj, std, id, name, text, cost, keywords);
        }
        return new CardEntry(card, deckCopies, copyLimit);
    }

    /** 覆盖块优先的错误定位：std 里有该键取 std，否则回落基本块。 */
    private static MiniJson.Obj place(MiniJson.Obj std, MiniJson.Obj obj, String key) {
        return std != null && std.contains(key) ? std : obj;
    }

    /** 任一键出现（std 优先判定，兼容无覆盖的经典块）。 */
    private static boolean hasAny(MiniJson.Obj std, MiniJson.Obj obj, List<String> keys) {
        for (String key : keys) {
            if ((std != null && std.contains(key)) || obj.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static MinionCard parseMinion(MiniJson.Obj obj, MiniJson.Obj std, String id, String name,
                                          String text, long cost, List<Keyword> keywords) {
        int attack = std != null && std.contains("attack") ? intField(std, "attack") : intField(obj, "attack");
        int health = std != null && std.contains("health") ? intField(std, "health") : intField(obj, "health");
        if (attack < 0) {
            throw new IllegalStateException(place(std, obj, "attack").atKey("attack", "attack 不能为负数：" + attack));
        }
        if (health < 0) {
            throw new IllegalStateException(place(std, obj, "health").atKey("health", "health 不能为负数：" + health));
        }
        EffectSpec battlecry = parseSpec(std != null && std.contains("battlecry") ? std : obj,
                "battlecry", keywords, Keyword.BATTLECRY);
        EffectSpec deathrattle = parseSpec(std != null && std.contains("deathrattle") ? std : obj,
                "deathrattle", keywords, Keyword.DEATHRATTLE);
        return new MinionCard(id, name, text, attack, health, (int) cost, keywords,
                battlecry, deathrattle);
    }

    /**
     * 解析战吼/亡语效果 spec（批2数据契约）：{kind: DAMAGE/HEAL/DRAW, amount ≥ 1}，
     * 且卡必须带配套关键词（BATTLECRY/DEATHRATTLE）；字段缺省返回 null（走关键词默认效果）。
     */
    private static EffectSpec parseSpec(MiniJson.Obj obj, String field,
                                        List<Keyword> keywords, Keyword required) {
        if (!obj.contains(field)) {
            return null;
        }
        MiniJson.Obj spec = obj.get(field).asObject();
        String kindRaw = strField(spec, "kind");
        Keyword kind;
        try {
            kind = Keyword.valueOf(kindRaw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(spec.atKey("kind",
                    field + " 的非法 kind=" + kindRaw + "，合法值：DAMAGE/HEAL/DRAW"));
        }
        if (kind != Keyword.DAMAGE && kind != Keyword.HEAL && kind != Keyword.DRAW) {
            throw new IllegalStateException(spec.atKey("kind",
                    field + " 的非法 kind=" + kindRaw + "，合法值：DAMAGE/HEAL/DRAW"));
        }
        int amount = intField(spec, "amount");
        if (amount < 1) {
            throw new IllegalStateException(spec.atKey("amount",
                    field + " 的 amount 必须大于 0：" + amount));
        }
        if (!keywords.contains(required)) {
            throw new IllegalStateException(obj.atKey(field,
                    field + " 效果需要 keywords 含 " + required));
        }
        return new EffectSpec(kind, amount);
    }

    private static SpellCard parseSpell(MiniJson.Obj obj, MiniJson.Obj std, String id, String name,
                                        String text, long cost, List<Keyword> keywords) {
        MiniJson.Obj kindSrc = std != null && std.contains("kind") ? std : obj;
        String kindRaw = strField(kindSrc, "kind");
        Keyword kind;
        try {
            kind = Keyword.valueOf(kindRaw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(kindSrc.atKey("kind",
                    "非法法术 kind=" + kindRaw + "，合法值：DAMAGE/HEAL/DRAW"));
        }
        MiniJson.Obj amountSrc = std != null && std.contains("amount") ? std : obj;
        long amount = longField(amountSrc, "amount");
        if (amount <= 0) {
            throw new IllegalStateException(amountSrc.atKey("amount",
                    "法术 amount 必须大于 0：" + amount));
        }
        return new SpellCard(id, name, text, toSpellKind(kind), (int) amount, (int) cost);
    }

    private static PetCard parsePet(MiniJson.Obj obj, MiniJson.Obj std, String id, String name,
                                    String text, long cost, List<Keyword> keywords) {
        int attackBonus = std != null && std.contains("attackBonus") ? intField(std, "attackBonus")
                : intField(obj, "attackBonus");
        int healthBonus = std != null && std.contains("healthBonus") ? intField(std, "healthBonus")
                : intField(obj, "healthBonus");
        // B-3：允许负光环（双刃剑宠物），范围 ±5，超出视为配表笔误
        if (attackBonus < -5 || attackBonus > 5) {
            throw new IllegalStateException(place(std, obj, "attackBonus").atKey("attackBonus",
                    "attackBonus 超出范围 [-5, 5]：" + attackBonus));
        }
        if (healthBonus < -5 || healthBonus > 5) {
            throw new IllegalStateException(place(std, obj, "healthBonus").atKey("healthBonus",
                    "healthBonus 超出范围 [-5, 5]：" + healthBonus));
        }
        return new PetCard(id, name, text, attackBonus, healthBonus, (int) cost);
    }

    private static SpellCard.Kind toSpellKind(Keyword keyword) {
        return switch (keyword) {
            case DAMAGE -> SpellCard.Kind.DAMAGE;
            case HEAL -> SpellCard.Kind.HEAL;
            case DRAW -> SpellCard.Kind.DRAW;
            default -> throw new AssertionError("未处理的关键字: " + keyword);
        };
    }

    private static List<Keyword> parseKeywords(MiniJson.Arr keywords) {
        List<Keyword> result = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i++) {
            MiniJson.Str keywordValue = keywords.get(i).asString();
            String raw = keywordValue.value();
            try {
                result.add(Keyword.valueOf(raw));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(keywordValue.at(
                        "未知关键字 keywords[" + i + "]=" + raw + "，合法值见 model.Keyword"));
            }
        }
        return result;
    }

    // ============ 字段读取辅助（全部带"文件:行:原因"错误） ============

    private static MiniJson.Value needField(MiniJson.Obj obj, String key) {
        if (!obj.contains(key)) {
            throw new IllegalStateException(obj.atKey(key, "缺少字段 '" + key + "'"));
        }
        return obj.get(key);
    }

    private static String strField(MiniJson.Obj obj, String key) {
        return needField(obj, key).asString().value();
    }

    private static long longField(MiniJson.Obj obj, String key) {
        return needField(obj, key).asNumber().value();
    }

    private static int intField(MiniJson.Obj obj, String key) {
        long value = longField(obj, key);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException(obj.atKey(key, "数值超出 int 范围：" + value));
        }
        return (int) value;
    }

    // ============ classpath 读取 ============

    private static String readResource(String path) {
        try (InputStream in = CardDatabase.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("找不到 classpath 资源 " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 classpath 资源失败 " + path + ": " + e.getMessage(), e);
        }
    }
}