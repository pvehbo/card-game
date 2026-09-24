package org.example.card.data;

import org.example.card.model.Card;
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

    /** 卡模板 + 入堆份数（B-3：明星单卡 copies:1，普通卡默认 2）。 */
    public record CardEntry(Card template, int copies) {
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

    /**
     * 标准牌堆：每种卡按 copies 入堆，每份都是全新实例（受伤标记不共享）。
     * 老卡默认 2 份；B-3 明星单卡 1 份。
     */
    public static List<Card> standardDeck() {
        List<Card> deck = new ArrayList<>();
        for (CardEntry entry : loadEntries()) {
            for (int i = 0; i < entry.copies(); i++) {
                deck.add(entry.template().copy());
            }
        }
        return deck;
    }

    private static List<CardEntry> loadFile(String path, Map<String, String> idSource) {
        String fileName = path.startsWith("/") ? path.substring(1) : path;
        return entriesFrom(readResource(path), fileName, idSource);
    }

    /**
     * 测试入口：解析单份 JSON（sourceName 作为错误消息中的文件名）。
     * 校验：schemaVersion==1、id 唯一、数值非负、法术 kind 合法且 amount&gt;0、
     * keywords ⊆ {@link Keyword} 枚举、copies 缺省 2 且只能是 1～2。
     */
    public static List<Card> loadFrom(String json, String sourceName) {
        List<Card> result = new ArrayList<>();
        for (CardEntry entry : entriesFrom(json, sourceName, new LinkedHashMap<>())) {
            result.add(entry.template());
        }
        return result;
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

    /** 按字段区分卡型并解析；入堆份数缺省 2，只允许 1～2。 */
    private static CardEntry parseEntry(MiniJson.Obj obj) {
        String id = strField(obj, "id");
        String name = strField(obj, "name");
        String text = strField(obj, "text");
        long cost = longField(obj, "cost");
        List<Keyword> keywords = parseKeywords(needField(obj, "keywords").asArray());
        if (cost < 0) {
            throw new IllegalStateException(obj.atKey("cost", "cost 不能为负数：" + cost));
        }
        int copies = obj.contains("copies") ? intField(obj, "copies") : DEFAULT_COPIES;
        if (copies < 1 || copies > 2) {
            throw new IllegalStateException(obj.atKey("copies",
                    "copies 只能是 1～2：" + copies));
        }
        Card card;
        if (obj.contains("kind") || obj.contains("amount")) {
            card = parseSpell(obj, id, name, text, cost, keywords);
        } else if (obj.contains("attackBonus") || obj.contains("healthBonus")) {
            card = parsePet(obj, id, name, text, cost, keywords);
        } else {
            card = parseMinion(obj, id, name, text, cost, keywords);
        }
        return new CardEntry(card, copies);
    }

    private static MinionCard parseMinion(MiniJson.Obj obj, String id, String name, String text,
                                         long cost, List<Keyword> keywords) {
        int attack = intField(obj, "attack");
        int health = intField(obj, "health");
        if (attack < 0) {
            throw new IllegalStateException(obj.atKey("attack", "attack 不能为负数：" + attack));
        }
        if (health < 0) {
            throw new IllegalStateException(obj.atKey("health", "health 不能为负数：" + health));
        }
        return new MinionCard(id, name, text, attack, health, (int) cost, keywords);
    }

    private static SpellCard parseSpell(MiniJson.Obj obj, String id, String name, String text,
                                        long cost, List<Keyword> keywords) {
        String kindRaw = strField(obj, "kind");
        Keyword kind;
        try {
            kind = Keyword.valueOf(kindRaw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(obj.atKey("kind",
                    "非法法术 kind=" + kindRaw + "，合法值：DAMAGE/HEAL/DRAW"));
        }
        long amount = longField(obj, "amount");
        if (amount <= 0) {
            throw new IllegalStateException(obj.atKey("amount",
                    "法术 amount 必须大于 0：" + amount));
        }
        return new SpellCard(id, name, text, toSpellKind(kind), (int) amount, (int) cost);
    }

    private static PetCard parsePet(MiniJson.Obj obj, String id, String name, String text,
                                    long cost, List<Keyword> keywords) {
        int attackBonus = intField(obj, "attackBonus");
        int healthBonus = intField(obj, "healthBonus");
        // B-3：允许负光环（双刃剑宠物），范围 ±5，超出视为配表笔误
        if (attackBonus < -5 || attackBonus > 5) {
            throw new IllegalStateException(obj.atKey("attackBonus",
                    "attackBonus 超出范围 [-5, 5]：" + attackBonus));
        }
        if (healthBonus < -5 || healthBonus > 5) {
            throw new IllegalStateException(obj.atKey("healthBonus",
                    "healthBonus 超出范围 [-5, 5]：" + healthBonus));
        }
        return new PetCard(id, name, text, attackBonus, healthBonus, (int) cost);
    }

    private static SpellCard.Kind toSpellKind(Keyword keyword) {
        switch (keyword) {
            case DAMAGE:
                return SpellCard.Kind.DAMAGE;
            case HEAL:
                return SpellCard.Kind.HEAL;
            case DRAW:
                return SpellCard.Kind.DRAW;
            default:
                throw new AssertionError("未处理的关键字: " + keyword);
        }
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