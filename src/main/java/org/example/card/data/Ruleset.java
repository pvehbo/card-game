package org.example.card.data;

/**
 * 规则集（平衡方案 v0.1 批②，S2 数据契约）：一局对局的可调常量收在一处，
 * 经典/标准只是取值档位——经典=现网参数；标准是同名安装位，供批③随
 * 卡池数据（零费特权取消、递增疲劳等）一起翻值，见 docs/BALANCE-PROPOSAL-v0.1.md §0.1。
 *
 * <p>数值流转：heroLife/maxManaCap → PlayerState 构造，openingHand →
 * 开局发牌，secondMoveBonusDraws → 后手补偿（TurnController 开局时读）。
 */
public record Ruleset(String name, int heroLife, int maxManaCap,
                      int openingHand, int secondMoveBonusDraws) {

    /** 经典规则集：现网参数原样固化（PlayerState.START_LIFE/MAX_HAND 同源）。 */
    public static final Ruleset CLASSIC = new Ruleset("经典", 20, 10, 3, 1);
    /** 标准规则集：常量与经典同值；差异落在卡池数据层，批③翻值。 */
    public static final Ruleset STANDARD = new Ruleset("标准", 20, 10, 3, 1);

    public Ruleset {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("规则集名称不能为空");
        }
        if (heroLife < 1) {
            throw new IllegalArgumentException("heroLife 必须 ≥ 1：" + heroLife);
        }
        if (maxManaCap < 1 || maxManaCap > 20) {
            throw new IllegalArgumentException("maxManaCap 只允许 1～20：" + maxManaCap);
        }
        if (openingHand < 0 || openingHand > 10) {
            throw new IllegalArgumentException("openingHand 只允许 0～10：" + openingHand);
        }
        if (secondMoveBonusDraws < 0 || secondMoveBonusDraws > 3) {
            throw new IllegalArgumentException("secondMoveBonusDraws 只允许 0～3：" + secondMoveBonusDraws);
        }
    }
}
