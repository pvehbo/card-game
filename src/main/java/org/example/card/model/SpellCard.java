package org.example.card.model;

/** 法术卡：打出即结算（伤害打敌方英雄脸 / 回复己方英雄 / 抽牌），然后进墓地。 */
public class SpellCard extends Card {

    public enum Kind {
        /** 对敌方英雄造成 amount 点伤害。 */
        DAMAGE,
        /** 回复己方英雄 amount 点生命（不超过 20 上限）。 */
        HEAL,
        /** 抽 amount 张牌。 */
        DRAW
    }

    private final Kind kind;
    private final int amount;
    private final int cost;

    public SpellCard(String id, String name, String description, Kind kind, int amount) {
        this(id, name, description, kind, amount, 0);
    }

    /** 老卡默认 0 费；新卡走 JSON cost（见 data.CardDatabase）。 */
    public SpellCard(String id, String name, String description, Kind kind, int amount, int cost) {
        super(id, name, description);
        this.kind = kind;
        this.amount = amount;
        this.cost = Math.max(0, cost);
    }

    public Kind getKind() {
        return kind;
    }

    public int getAmount() {
        return amount;
    }

    @Override
    public String typeName() {
        return "法术";
    }

    @Override
    public int getCost() {
        return cost;
    }

    @Override
    public Card copy() {
        return new SpellCard(getId(), getName(), getDescription(), kind, amount, cost);
    }

    @Override
    public String toString() {
        return "法术《" + getName() + "》(" + kind + amount + ")";
    }
}
