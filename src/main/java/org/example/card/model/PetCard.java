package org.example.card.model;

/**
 * 宠物牌：不可攻击、不占战斗格、不可被攻击。
 * 打出后常驻己方宠物区，给己方全体随从提供光环（攻击/血量上限加成）。
 */
public class PetCard extends Card {

    private final int attackBonus;
    private final int healthBonus;

    public PetCard(String id, String name, String description, int attackBonus, int healthBonus) {
        super(id, name, description);
        this.attackBonus = attackBonus;
        this.healthBonus = healthBonus;
    }

    /** 己方随从攻击 +N。 */
    public int getAttackBonus() {
        return attackBonus;
    }

    /** 己方随从血量上限 +N。 */
    public int getHealthBonus() {
        return healthBonus;
    }

    @Override
    public String typeName() {
        return "宠物";
    }

    @Override
    public String toString() {
        return "宠物《" + getName() + "》(光环:攻+" + attackBonus + "/血+" + healthBonus + ")";
    }
}
