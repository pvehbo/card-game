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

    public SpellCard(String id, String name, String description, Kind kind, int amount) {
        super(id, name, description);
        this.kind = kind;
        this.amount = amount;
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
    public String toString() {
        return "法术《" + getName() + "》(" + kind + amount + ")";
    }
}
