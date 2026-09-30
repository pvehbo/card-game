package org.example.card.event;

import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.example.card.net.Json;

/**
 * 游戏事件：引擎在关键节点发出，UI 订阅后做动效与刷新。
 * 用 record 保证不可变，字段按事件类型取舍使用。
 *
 * <p>M1 起 JSON 升到 {@link #EVENT_SCHEMA} = 2：只新增两个可选协议字段（seq/turn），
 * v1 正文照旧可解析，联机广播与本地回放共用同一套编解码。
 */
public record GameEvent(
        Type type,
        PlayerState actor,
        PlayerState target,
        Card card,
        MinionCard attacker,
        MinionCard defender,
        int amount,
        String message
) {

    /**
     * 事件序列化版本号：fromJson 拒绝更大的版本（提示升级游戏）。
     * v1 → v2 只加了 seq/turn 两个可选字段，是向前兼容的加法。
     */
    public static final int EVENT_SCHEMA = 2;

    public enum Type {
        /** 抽牌 */
        DRAW,
        /** 手牌满，烧牌 */
        BURN,
        /** 随从上场 */
        SUMMON,
        /** 法术结算 */
        SPELL,
        /** 宠物召唤 */
        PET,
        /** 随从攻击（含直击） */
        ATTACK,
        /** 受到伤害（amount = 伤害值，target 为受击方） */
        DAMAGE,
        /** 随从阵亡 */
        DEATH,
        /** 回合开始 */
        TURN_START,
        /** 回合结束 */
        TURN_END,
        /** 分出胜负 */
        GAME_OVER
    }

    public static GameEvent of(Type type, String message) {
        return new GameEvent(type, null, null, null, null, null, 0, message);
    }

    public static GameEvent draw(PlayerState who, Card card, String message) {
        return new GameEvent(Type.DRAW, who, null, card, null, null, 0, message);
    }

    /**
     * 烧牌（手牌满时抽到的那张直接进墓地）。
     * 带 owner/card 是 M2 联机需要：对手那张牌是暗信息，广播时要按座位抹掉牌面。
     */
    public static GameEvent burn(PlayerState owner, Card card, String message) {
        return new GameEvent(Type.BURN, owner, null, card, null, null, 0, message);
    }

    public static GameEvent summon(PlayerState who, MinionCard minion, String message) {
        return new GameEvent(Type.SUMMON, who, null, minion, null, null, 0, message);
    }

    public static GameEvent spell(PlayerState who, Card card, int amount, String message) {
        return new GameEvent(Type.SPELL, who, null, card, null, null, amount, message);
    }

    public static GameEvent pet(PlayerState who, Card pet, String message) {
        return new GameEvent(Type.PET, who, null, pet, null, null, 0, message);
    }

    public static GameEvent attack(PlayerState attackerSide, PlayerState defenderSide,
                                   MinionCard attacker, MinionCard defender, int amount, String message) {
        return new GameEvent(Type.ATTACK, attackerSide, defenderSide, null, attacker, defender, amount, message);
    }

    /** 英雄受伤：target = 受伤方，defender = null。 */
    public static GameEvent damage(PlayerState victim, int amount, String message) {
        return new GameEvent(Type.DAMAGE, null, victim, null, null, null, amount, message);
    }

    /**
     * 随从受伤：target = 随从所属玩家（用于判断是哪一侧），defender = 受伤的随从。
     * UI 据此把飘字/粒子锚定在随从身上，而不是英雄头像上。
     */
    public static GameEvent damage(PlayerState owner, MinionCard victim, int amount, String message) {
        return new GameEvent(Type.DAMAGE, null, owner, null, null, victim, amount, message);
    }

    public static GameEvent death(PlayerState owner, MinionCard minion, String message) {
        return new GameEvent(Type.DEATH, owner, null, minion, null, null, 0, message);
    }

    public static GameEvent turn(Type type, PlayerState who, String message) {
        return new GameEvent(type, who, null, null, null, null, 0, message);
    }

    public static GameEvent gameOver(PlayerState winner, String message) {
        return new GameEvent(Type.GAME_OVER, winner, null, null, null, null, 0, message);
    }

    // ============ 版本化 JSON（S9 存档/回放 → M1 联机广播共用） ============

    /**
     * 解析后的事件数据（实体引用还原为名字/id，数值与消息完整保留）。
     *
     * <p>{@code seq}/{@code turn} 是 M1 的协议字段：v1 正文里没有，解析时补 0，
     * 所以旧存档、旧客户端事件流不需要任何迁移。
     */
    public record JsonData(Type type, String actor, String target, String cardId,
                           String attacker, String defender, int amount,
                           String message, int schemaVersion, int seq, int turn) {
    }

    /** 本地存档/回放：不带协议顺序号（seq/turn 记 0），其余与 {@link #toJson(int, int)} 完全一致。 */
    public String toJson() {
        return toJson(0, 0);
    }

    /**
     * v2 协议序列化（字段固定顺序，缺失写 null）。
     * actor/target 取玩家名，card 取卡 id，attacker/defender 取随从名。
     *
     * @param seq  服务端广播序号（从 1 递增）；客户端重连时靠它判断漏没漏事件
     * @param turn 事件发生时的引擎回合号
     */
    public String toJson(int seq, int turn) {
        return "{\"type\":\"" + type.name() + "\""
                + ",\"actor\":" + Json.quote(actor == null ? null : actor.getName())
                + ",\"target\":" + Json.quote(target == null ? null : target.getName())
                + ",\"cardId\":" + Json.quote(card == null ? null : card.getId())
                + ",\"attacker\":" + Json.quote(attacker == null ? null : attacker.getName())
                + ",\"defender\":" + Json.quote(defender == null ? null : defender.getName())
                + ",\"amount\":" + amount
                + ",\"message\":" + Json.quote(message)
                + ",\"seq\":" + seq
                + ",\"turn\":" + turn
                + ",\"schemaVersion\":" + EVENT_SCHEMA + "}";
    }

    /**
     * 反解析；缺必填字段或版本大于 {@link #EVENT_SCHEMA} 时抛 IllegalArgumentException。
     *
     * <p>未知字段仍然报错——存档是本机文件，写错了要当场发现；但 seq/turn 是可选字段，
     * v1 正文（没有它们）解析出来的 seq/turn 是 0。
     */
    public static JsonData fromJson(String json) {
        Json.Reader reader = new Json.Reader(json, "事件 JSON");
        reader.expect('{');
        String type = null;
        String actor = null;
        String target = null;
        String cardId = null;
        String attacker = null;
        String defender = null;
        Integer amount = null;
        String message = null;
        Integer schemaVersion = null;
        int seq = 0;
        int turn = 0;
        boolean first = true;
        while (reader.nextEntry(first)) {
            first = false;
            String key = reader.string();
            reader.expect(':');
            switch (key) {
                case "type" -> type = reader.string();
                case "actor" -> actor = reader.nullableString();
                case "target" -> target = reader.nullableString();
                case "cardId" -> cardId = reader.nullableString();
                case "attacker" -> attacker = reader.nullableString();
                case "defender" -> defender = reader.nullableString();
                case "amount" -> amount = reader.integer();
                case "message" -> message = reader.nullableString();
                case "seq" -> seq = reader.integer();
                case "turn" -> turn = reader.integer();
                case "schemaVersion" -> schemaVersion = reader.integer();
                default -> throw new IllegalArgumentException("未知字段: " + key);
            }
        }
        reader.expect('}');
        reader.end();
        if (type == null || amount == null || message == null || schemaVersion == null) {
            throw new IllegalArgumentException("事件 JSON 缺字段: " + json);
        }
        if (schemaVersion > EVENT_SCHEMA) {
            throw new IllegalArgumentException(
                    "事件版本过新（schemaVersion=" + schemaVersion + "），请升级游戏");
        }
        Type parsedType;
        try {
            parsedType = Type.valueOf(type);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("未知事件类型: " + type, ex);
        }
        return new JsonData(parsedType, actor, target, cardId,
                attacker, defender, amount, message, schemaVersion, seq, turn);
    }
}
