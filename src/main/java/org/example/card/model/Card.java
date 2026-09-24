package org.example.card.model;

/**
 * 卡牌基类（炉石式 v1：无费用，出牌靠每回合次数规则约束，见 engine.GameEngine）。
 */
public abstract class Card {

    private final String id;
    private final String name;
    private final String description;

    protected Card(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /** 卡牌类型名（随从 / 法术 / 宠物），UI 展示用。 */
    public abstract String typeName();

    /** 费用（v1 老卡全 0；B-1 起结算与置灰都认它）。 */
    public abstract int getCost();

    /** 全新实例拷贝（同模板，不同状态；组牌堆/读档用，避免实例共享受伤标记）。 */
    public abstract Card copy();

    @Override
    public String toString() {
        return typeName() + "《" + name + "》";
    }
}
