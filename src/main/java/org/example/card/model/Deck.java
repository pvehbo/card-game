package org.example.card.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** 牌堆：洗牌 + 抽卡。抽空返回 Optional.empty()（判负逻辑后续在 engine 里加）。 */
public class Deck {

    private final List<Card> cards;

    public Deck(List<Card> cards) {
        this.cards = new ArrayList<>(cards);
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    /** 带随机源的洗牌（对局用，可测试时固定种子复现）。 */
    public void shuffle(Random random) {
        Collections.shuffle(cards, random);
    }

    public Optional<Card> draw() {
        if (cards.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(cards.remove(cards.size() - 1));
    }

    public int size() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }
}
