package org.example.card.model;

/**
 * 随从卡（炉石式）：攻击 + 血量，受到的伤害会保留。
 * 光环加成（宠物）由 engine 动态计算，不写进基础数值，见 CombatResolver.effectiveAttack。
 */
public class MinionCard extends Card {

    private final int attack;
    private final int maxHealth;
    private final int cost;
    private final java.util.List<Keyword> keywords;
    private int damageTaken;
    /** 召唤失调：上场当回合不可攻击，己方回合结束时解除。 */
    private boolean summoningSickness = true;
    /** 本回合是否已经出过手：每个随从每回合只能攻击 1 次。 */
    private boolean attackedThisTurn;

    public MinionCard(String id, String name, String description, int attack, int maxHealth) {
        this(id, name, description, attack, maxHealth, 0);
    }

    /** 老卡默认 0 费、无关键词；新卡走 JSON cost/keywords（见 data.CardDatabase）。 */
    public MinionCard(String id, String name, String description,
                      int attack, int maxHealth, int cost) {
        this(id, name, description, attack, maxHealth, cost, java.util.List.of());
    }

    public MinionCard(String id, String name, String description,
                      int attack, int maxHealth, int cost, java.util.List<Keyword> keywords) {
        super(id, name, description);
        this.attack = attack;
        this.maxHealth = maxHealth;
        this.cost = Math.max(0, cost);
        this.keywords = java.util.List.copyOf(keywords);
    }

    /** 是否具有某关键词（冲锋/嘲讽/战吼/亡语）。 */
    public boolean hasKeyword(Keyword keyword) {
        return keywords.contains(keyword);
    }

    public int getAttack() {
        return attack;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public int getDamageTaken() {
        return damageTaken;
    }

    public void takeDamage(int amount) {
        damageTaken += Math.max(0, amount);
    }

    public boolean isSummoningSickness() {
        return summoningSickness;
    }

    public void setSummoningSickness(boolean summoningSickness) {
        this.summoningSickness = summoningSickness;
    }

    /** 本回合是否已经攻击过。 */
    public boolean isAttackedThisTurn() {
        return attackedThisTurn;
    }

    public void setAttackedThisTurn(boolean attackedThisTurn) {
        this.attackedThisTurn = attackedThisTurn;
    }

    @Override
    public String typeName() {
        return "随从";
    }

    @Override
    public int getCost() {
        return cost;
    }

    @Override
    public Card copy() {
        return new MinionCard(getId(), getName(), getDescription(),
                attack, maxHealth, cost, keywords);
    }

    @Override
    public String toString() {
        return "随从《" + getName() + "》(攻" + attack + "/血" + maxHealth + ")";
    }
}
