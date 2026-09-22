package org.example.card.model;

/**
 * 随从卡（炉石式）：攻击 + 血量，受到的伤害会保留。
 * 光环加成（宠物）由 engine 动态计算，不写进基础数值，见 GameEngine.effectiveAttack。
 */
public class MinionCard extends Card {

    private final int attack;
    private final int maxHealth;
    private int damageTaken;
    /** 召唤失调：上场当回合不可攻击，己方回合结束时解除。 */
    private boolean summoningSickness = true;
    /** 本回合是否已经出过手：每个随从每回合只能攻击 1 次。 */
    private boolean attackedThisTurn;

    public MinionCard(String id, String name, String description, int attack, int maxHealth) {
        super(id, name, description);
        this.attack = attack;
        this.maxHealth = maxHealth;
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
    public String toString() {
        return "随从《" + getName() + "》(攻" + attack + "/血" + maxHealth + ")";
    }
}
