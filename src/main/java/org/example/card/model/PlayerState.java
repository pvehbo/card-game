package org.example.card.model;

import java.util.ArrayList;
import java.util.List;

/** 玩家状态：20 点固定生命，无能量/法力，出牌靠回合次数规则。 */
public class PlayerState {

    /** 双方英雄固定 20 点生命。 */
    public static final int START_LIFE = 20;
    /** 手牌上限 10 张，超出抽到的牌烧掉（进墓地）。 */
    public static final int MAX_HAND = 10;
    /** 战斗格上限 7 个随从。 */
    public static final int MAX_FIELD = 7;

    private final String name;
    private int lifePoints = START_LIFE;
    private final Deck deck;
    private final List<Card> hand = new ArrayList<>();
    private final List<MinionCard> field = new ArrayList<>();
    private final List<PetCard> pets = new ArrayList<>();
    private final List<Card> graveyard = new ArrayList<>();
    /** 本回合是否已打过 1 张随从 / 1 张法术 / 1 只宠物（每回合各限 1 次）。 */
    private boolean minionPlayed;
    private boolean spellPlayed;
    private boolean petPlayed;

    public PlayerState(String name, Deck deck) {
        this.name = name;
        this.deck = deck;
    }

    /** 新回合开始前调用，重置本回合的出牌次数标志。 */
    public void resetTurnFlags() {
        minionPlayed = false;
        spellPlayed = false;
        petPlayed = false;
    }

    public boolean isMinionPlayed() {
        return minionPlayed;
    }

    public void setMinionPlayed(boolean minionPlayed) {
        this.minionPlayed = minionPlayed;
    }

    public boolean isSpellPlayed() {
        return spellPlayed;
    }

    public void setSpellPlayed(boolean spellPlayed) {
        this.spellPlayed = spellPlayed;
    }

    public boolean isPetPlayed() {
        return petPlayed;
    }

    public void setPetPlayed(boolean petPlayed) {
        this.petPlayed = petPlayed;
    }

    public String getName() {
        return name;
    }

    public int getLifePoints() {
        return lifePoints;
    }

    public void damage(int amount) {
        lifePoints = Math.max(0, lifePoints - amount);
    }

    /** 回复生命，不超过 20 上限。 */
    public void heal(int amount) {
        lifePoints = Math.min(START_LIFE, lifePoints + Math.max(0, amount));
    }

    public boolean isDefeated() {
        return lifePoints <= 0;
    }

    public Deck getDeck() {
        return deck;
    }

    public List<Card> getHand() {
        return hand;
    }

    public List<MinionCard> getField() {
        return field;
    }

    /** 宠物区：常驻光环，不可被攻击。 */
    public List<PetCard> getPets() {
        return pets;
    }

    public List<Card> getGraveyard() {
        return graveyard;
    }

    @Override
    public String toString() {
        return name + "(生命" + lifePoints + " 手牌" + hand.size()
                + " 场上" + field.size() + " 宠物" + pets.size() + " 牌堆" + deck.size() + ")";
    }
}
