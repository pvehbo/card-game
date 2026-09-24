package org.example.card.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.card.model.Card;

/**
 * 牌组构筑器（B-7，无 UI 纯规则，可单测）。
 *
 * 规则：30 张；同种卡最多 2 张（明星单卡按自身 copies 上限，即 1 张）；
 * id 必须出自卡库。build() 吐全新实例（受伤标记不共享）。
 */
public final class DeckBuilder {

    /** 牌组张数。 */
    public static final int DECK_SIZE = 30;
    /** 同种卡上限（不超过卡牌自身的 copies 上限）。 */
    public static final int MAX_COPIES = 2;

    private final List<Card> picks = new ArrayList<>();
    private final Map<String, CardDatabase.CardEntry> library;

    public DeckBuilder() {
        this.library = new LinkedHashMap<>();
        for (CardDatabase.CardEntry entry : CardDatabase.loadEntries()) {
            library.putIfAbsent(entry.template().getId(), entry);
        }
    }

    /** 卡库（按载入顺序，去重）。 */
    public List<Card> library() {
        List<Card> cards = new ArrayList<>();
        for (CardDatabase.CardEntry entry : library.values()) {
            cards.add(entry.template());
        }
        return List.copyOf(cards);
    }

    /** 当前已选（模板引用，只读）。 */
    public List<Card> picks() {
        return List.copyOf(picks);
    }

    /** 同种卡在牌组中的上限（明星单卡 1 张）。 */
    public int copyLimit(Card template) {
        CardDatabase.CardEntry entry = library.get(template.getId());
        if (entry == null) {
            return 0;
        }
        return Math.min(MAX_COPIES, entry.copies());
    }

    /** 加入一张；超量/未知返回 false（界面据此禁用按钮或提示）。 */
    public boolean add(Card template) {
        CardDatabase.CardEntry known = library.get(template.getId());
        if (known == null) {
            return false;
        }
        long count = picks.stream().filter(c -> c.getId().equals(template.getId())).count();
        if (count >= copyLimit(template)) {
            return false;
        }
        if (picks.size() >= DECK_SIZE) {
            return false;
        }
        picks.add(known.template());
        return true;
    }

    public boolean remove(Card template) {
        for (int i = 0; i < picks.size(); i++) {
            if (picks.get(i).getId().equals(template.getId())) {
                picks.remove(i);
                return true;
            }
        }
        return false;
    }

    public void clear() {
        picks.clear();
    }

    /** 一键默认：标准牌堆（30 张，合法可直接开局）。 */
    public void useStandard() {
        clear();
        for (Card card : CardDatabase.standardDeck()) {
            picks.add(library.get(card.getId()).template());
        }
    }

    /** 费用曲线：下标为费用（0～10），值为该费用张数。 */
    public int[] curve() {
        int[] curve = new int[11];
        for (Card card : picks) {
            curve[Math.min(10, card.getCost())]++;
        }
        return curve;
    }

    /**
     * 校验：empty 表示合法可开局，否则返回中文原因。
     * 顺序：未知卡 → 同名超量 → 数量。
     */
    public Optional<String> validate() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Card card : picks) {
            CardDatabase.CardEntry known = library.get(card.getId());
            if (known == null) {
                return Optional.of("未知卡牌：" + card.getId());
            }
            counts.merge(card.getId(), 1, Integer::sum);
            if (counts.get(card.getId()) > copyLimit(known.template())) {
                return Optional.of("同名超量：《" + known.template().getName() + "》最多 "
                        + copyLimit(known.template()) + " 张");
            }
        }
        if (picks.size() != DECK_SIZE) {
            return Optional.of("牌组数量不足：" + picks.size() + "/" + DECK_SIZE);
        }
        return Optional.empty();
    }

    /** 组牌：校验通过后吐 30 张全新实例；非法抛 IllegalStateException。 */
    public List<Card> build() {
        Optional<String> reason = validate();
        if (reason.isPresent()) {
            throw new IllegalStateException("非法牌组：" + reason.get());
        }
        List<Card> deck = new ArrayList<>();
        for (Card pick : picks) {
            deck.add(library.get(pick.getId()).template().copy());
        }
        return deck;
    }

    /** 按 id 表重建（读配置用）；未知 id 抛 IllegalStateException。 */
    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (Card pick : picks) {
            ids.add(pick.getId());
        }
        return ids;
    }

    /** 从 id 表装填（读配置用）；未知 id 抛 IllegalStateException。 */
    public void loadIds(List<String> ids) {
        clear();
        for (String id : ids) {
            CardDatabase.CardEntry known = library.get(id);
            if (known == null) {
                throw new IllegalStateException("未知卡牌 id：" + id);
            }
            picks.add(known.template());
        }
    }
}
