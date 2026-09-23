package org.example.card.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.card.data.CardDatabase;
import org.example.card.data.MiniJson;
import org.example.card.engine.GameSession;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;

/**
 * 版本化存档（S8）：对局快照 ↔ JSON 字符串。
 *
 * 规则：saveVersion &gt; {@link #SAVE_VERSION} 拒绝并提示升级游戏；
 * 未知字段忽略（给未来留余地）；未知卡 id 拒绝（牌表对不上读了也白读）。
 * 回合计数不进存档（读档后从当前引擎继续，见 GameEngine.getTurn）。
 */
public final class SaveService {

    public static final int SAVE_VERSION = 1;
    private static final String SOURCE = "save";

    private SaveService() {
    }

    /** 存档：双方生命/手牌/战场（含受伤）/宠物/牌堆顺序/墓地/轮到谁。 */
    public static String save(GameSession session) {
        StringBuilder out = new StringBuilder();
        out.append("{\"saveVersion\":").append(SAVE_VERSION);
        // MiniJson 无布尔类型，布尔存成字符串
        out.append(",\"yourTurn\":").append(quote(Boolean.toString(session.isYourTurn())));
        out.append(",\"player\":");
        writeSide(out, session.getPlayer());
        out.append(",\"ai\":");
        writeSide(out, session.getAi());
        out.append("}");
        return out.toString();
    }

    /**
     * 读档：重建双方状态并装进新会话（卡牌按 id 从 {@link CardDatabase} 取模板复制）。
     * 调用方负责把返回的会话接给界面与引擎。
     */
    public static GameSession load(String json) {
        MiniJson.Value root = MiniJson.parse(json, SOURCE);
        MiniJson.Obj obj = root.asObject();
        int version = (int) number(obj, "saveVersion");
        if (version > SAVE_VERSION) {
            throw new IllegalStateException(
                    "存档版本过新（saveVersion=" + version + "），请升级游戏");
        }
        boolean yourTurn = bool(obj, "yourTurn");
        Map<String, Card> templates = templates();
        PlayerState player = readSide(obj.get("player").asObject(), "你", templates);
        PlayerState ai = readSide(obj.get("ai").asObject(), "AI", templates);
        return GameSession.restore(player, ai, yourTurn);
    }

    // ============ 写入 ============

    private static void writeSide(StringBuilder out, PlayerState state) {
        out.append("{\"life\":").append(state.getLifePoints());
        out.append(",\"hand\":");
        writeIds(out, state.getHand());
        out.append(",\"field\":[");
        for (int i = 0; i < state.getField().size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            MinionCard m = state.getField().get(i);
            out.append("{\"id\":").append(quote(m.getId()));
            out.append(",\"damageTaken\":").append(m.getDamageTaken()).append("}");
        }
        out.append("],\"pets\":");
        writeIds(out, new ArrayList<>(state.getPets()));
        out.append(",\"deck\":");
        writeDeckIds(out, state);
        out.append(",\"graveyard\":");
        writeIds(out, state.getGraveyard());
        out.append("}");
    }

    private static void writeIds(StringBuilder out, List<? extends Card> cards) {
        out.append("[");
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            out.append(quote(cards.get(i).getId()));
        }
        out.append("]");
    }

    private static void writeDeckIds(StringBuilder out, PlayerState state) {
        // 牌顶在前（下一次抽到的在前），读档时倒序压回即原样
        writeIds(out, state.getDeck().viewFromTop());
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ============ 读取 ============

    private static Map<String, Card> templates() {
        Map<String, Card> map = new LinkedHashMap<>();
        for (Card card : CardDatabase.load()) {
            map.putIfAbsent(card.getId(), card);
        }
        return map;
    }

    private static PlayerState readSide(MiniJson.Obj obj, String name, Map<String, Card> templates) {
        // 牌堆 id 顺序即 writeDeckIds 存的“下一次抽到的在前”，重建时倒序压回
        List<String> deckIds = strings(obj.get("deck").asArray());
        List<Card> deckCards = new ArrayList<>();
        for (int i = deckIds.size() - 1; i >= 0; i--) {
            deckCards.add(freshCopy(deckIds.get(i), templates));
        }
        PlayerState state = new PlayerState(name, new Deck(deckCards));
        int life = (int) number(obj, "life");
        state.damage(PlayerState.START_LIFE - life);
        for (String id : strings(obj.get("hand").asArray())) {
            state.getHand().add(freshCopy(id, templates));
        }
        for (MiniJson.Value item : array(obj, "field")) {
            MiniJson.Obj field = item.asObject();
            String id = field.get("id").asString().value();
            int damageTaken = (int) field.get("damageTaken").asNumber().value();
            Card copy = freshCopy(id, templates);
            if (!(copy instanceof MinionCard minion)) {
                throw new IllegalStateException(SOURCE + ":战场上的不是随从: " + id);
            }
            minion.takeDamage(damageTaken);
            state.getField().add(minion);
        }
        for (String id : strings(obj.get("pets").asArray())) {
            Card copy = freshCopy(id, templates);
            if (!(copy instanceof PetCard pet)) {
                throw new IllegalStateException(SOURCE + ":宠物区里的不是宠物: " + id);
            }
            state.getPets().add(pet);
        }
        for (String id : strings(obj.get("graveyard").asArray())) {
            state.getGraveyard().add(freshCopy(id, templates));
        }
        return state;
    }

    private static Card freshCopy(String id, Map<String, Card> templates) {
        Card template = templates.get(id);
        if (template == null) {
            throw new IllegalStateException(SOURCE + ":未知卡牌 id: " + id);
        }
        if (template instanceof MinionCard m) {
            return new MinionCard(m.getId(), m.getName(), m.getDescription(),
                    m.getAttack(), m.getMaxHealth());
        }
        if (template instanceof SpellCard s) {
            return new SpellCard(s.getId(), s.getName(), s.getDescription(),
                    s.getKind(), s.getAmount());
        }
        if (template instanceof PetCard p) {
            return new PetCard(p.getId(), p.getName(), p.getDescription(),
                    p.getAttackBonus(), p.getHealthBonus());
        }
        throw new IllegalStateException(SOURCE + ":未知卡牌类型: " + id);
    }

    private static long number(MiniJson.Obj obj, String key) {
        MiniJson.Value value = obj.get(key);
        if (value == null) {
            throw new IllegalStateException(SOURCE + ":存档缺字段: " + key);
        }
        return value.asNumber().value();
    }

    private static boolean bool(MiniJson.Obj obj, String key) {
        MiniJson.Value value = obj.get(key);
        if (value == null) {
            throw new IllegalStateException(SOURCE + ":存档缺字段: " + key);
        }
        String raw = value.asString().value();
        if (!raw.equals("true") && !raw.equals("false")) {
            throw new IllegalStateException(SOURCE + ":布尔字段非法: " + key);
        }
        return Boolean.parseBoolean(raw);
    }

    private static List<String> strings(MiniJson.Arr arr) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            out.add(arr.get(i).asString().value());
        }
        return out;
    }

    private static List<MiniJson.Value> array(MiniJson.Obj obj, String key) {
        MiniJson.Value value = obj.get(key);
        if (value == null) {
            throw new IllegalStateException(SOURCE + ":存档缺字段: " + key);
        }
        MiniJson.Arr arr = value.asArray();
        List<MiniJson.Value> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            out.add(arr.get(i));
        }
        return out;
    }
}
