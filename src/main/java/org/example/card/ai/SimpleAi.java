package org.example.card.ai;

import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.SpellCard;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 简易 AI（单机对手，贪心策略），实现 {@link AiStrategy}：
 * - 随从：选攻击最高的（无费用，人人上得起）；
 * - 法术：能斩杀就打伤害，血危就回血，牌少就过牌，否则打伤害；
 * - 宠物：有就上；
 * - 攻击：优先打对方当前血量最低的随从，没有随从就打脸。
 *
 * 决策一律只读 {@link GameView} 快照/手牌，不碰对局实体。
 */
public class SimpleAi implements AiStrategy {

    @Override
    public Optional<MinionCard> chooseMinion(List<Card> hand) {
        return hand.stream()
                .filter(c -> c instanceof MinionCard)
                .map(c -> (MinionCard) c)
                .max(Comparator.comparingInt(MinionCard::getAttack));
    }

    @Override
    public Optional<SpellCard> chooseSpell(GameView view, List<Card> hand) {
        List<SpellCard> spells = hand.stream()
                .filter(c -> c instanceof SpellCard)
                .map(c -> (SpellCard) c)
                .toList();
        if (spells.isEmpty()) {
            return Optional.empty();
        }
        // 1. 斩杀
        Optional<SpellCard> lethal = spells.stream()
                .filter(s -> s.getKind() == SpellCard.Kind.DAMAGE && s.getAmount() >= view.foeLife())
                .findFirst();
        if (lethal.isPresent()) {
            return lethal;
        }
        // 2. 血危回血（10 点以下）
        if (view.selfLife() <= 10) {
            Optional<SpellCard> heal = spells.stream()
                    .filter(s -> s.getKind() == SpellCard.Kind.HEAL)
                    .findFirst();
            if (heal.isPresent()) {
                return heal;
            }
        }
        // 3. 手牌少时过牌
        if (view.selfHandSize() <= 3) {
            Optional<SpellCard> draw = spells.stream()
                    .filter(s -> s.getKind() == SpellCard.Kind.DRAW)
                    .findFirst();
            if (draw.isPresent()) {
                return draw;
            }
        }
        // 4. 默认打伤害
        return spells.stream()
                .filter(s -> s.getKind() == SpellCard.Kind.DAMAGE)
                .findFirst()
                .or(() -> Optional.of(spells.get(0)));
    }

    @Override
    public Optional<PetCard> choosePet(List<Card> hand) {
        return hand.stream()
                .filter(c -> c instanceof PetCard)
                .map(c -> (PetCard) c)
                .findFirst();
    }

    /**
     * 在合法目标里选“对方当前血量最低”的随从；表空（无可出手随从或对方空场）则打脸。
     * 调用方负责从 ActionValidator.legalActions 摘出合法目标（Target 带当前血量），
     * AI 只做偏好选择，不自己判断合法性。
     */
    @Override
    public Optional<MinionCard> chooseAttackTarget(GameView view, List<Target> legalTargets) {
        return legalTargets.stream()
                .min(Comparator.comparingInt(Target::currentHealth))
                .map(Target::card);
    }
}