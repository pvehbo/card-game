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
    /** 本回合已出手次数（风怒可出手 2 次，其余 1 次）。 */
    private int attacksUsed;
    /** 圣盾：抵消下一次受到的伤害（有圣盾关键词上场时立起）。 */
    private boolean shieldUp;

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
        this.shieldUp = this.keywords.contains(Keyword.DIVINE_SHIELD);
    }

    /** 是否具有某关键词（冲锋/嘲讽/战吼/亡语/圣盾/风怒/剧毒）。 */
    public boolean hasKeyword(Keyword keyword) {
        return keywords.contains(keyword);
    }

    /** 圣盾是否还立着。 */
    public boolean hasDivineShield() {
        return shieldUp;
    }

    /**
     * 尝试用圣盾抵挡一次伤害：立着就消耗并返回 true（本次不受伤害），
     * 否则返回 false。
     */
    public boolean consumeDivineShield() {
        if (shieldUp) {
            shieldUp = false;
            return true;
        }
        return false;
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

    /** 本回合是否已经出过手（风怒出手 1 次后仍可再出手，显示层请用 ActionValidator）。 */
    public boolean isAttackedThisTurn() {
        return attacksUsed > 0;
    }

    /**
     * 标记出手状态（兼容旧调用：true 按“耗尽”计，false 清零）。
     * 引擎走 {@link #registerAttack} / {@link #resetAttacks}。
     */
    public void setAttackedThisTurn(boolean attackedThisTurn) {
        if (attackedThisTurn) {
            attacksUsed = Math.max(attacksUsed, hasKeyword(Keyword.WINDFURY) ? 2 : 1);
        } else {
            attacksUsed = 0;
        }
    }

    /** 本回合已出手次数。 */
    public int getAttacksUsed() {
        return attacksUsed;
    }

    /** 出手一次（结算成功后调用）。 */
    public void registerAttack() {
        attacksUsed++;
    }

    /** 新回合开始：出手次数清零。 */
    public void resetAttacks() {
        attacksUsed = 0;
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
