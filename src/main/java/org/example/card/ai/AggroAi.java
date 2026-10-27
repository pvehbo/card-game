package org.example.card.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.SpellCard;

/**
 * 快攻评测策略（路线图 V-B / T-B5 的“流派适配策略”）。
 *
 * <p>与 {@link SimpleAi} 的唯一区别是攻击取向：只要对方没有嘲讽，就优先打脸，
 * 不主动做“打最低血随从”的交换。它用于评测快攻基准构筑 A，避免用控制型
 * 贪心策略把快攻玩成换怪，从而低估 A 的强度。
 *
 * <p>它仍是评测代理，不是最终玩家 AI：不做场面价值评估，也不读隐藏信息。
 */
public final class AggroAi extends SimpleAi {

    @Override
    public Optional<MinionCard> chooseAttackTarget(GameView view, List<Target> legalTargets) {
        boolean taunt = view.foeMinions().stream().anyMatch(GameView.MinionInfo::taunt);
        if (!taunt) {
            // 空 Optional = 打脸；有嘲讽时合法目标表只剩嘲讽，交给父类选血最低的墙。
            return Optional.empty();
        }
        return super.chooseAttackTarget(view, legalTargets);
    }

    @Override
    public Optional<SpellCard> chooseSpell(GameView view, List<Card> hand) {
        List<SpellCard> spells = hand.stream()
                .filter(c -> c instanceof SpellCard)
                .map(c -> (SpellCard) c)
                .filter(s -> view.selfMana() >= s.getCost())
                .toList();
        if (spells.isEmpty()) {
            return Optional.empty();
        }
        Optional<SpellCard> lethal = spells.stream()
                .filter(s -> s.getKind() == SpellCard.Kind.DAMAGE && s.getAmount() >= view.foeLife())
                .findFirst();
        if (lethal.isPresent()) {
            return lethal;
        }
        Optional<SpellCard> damage = spells.stream()
                .filter(s -> s.getKind() == SpellCard.Kind.DAMAGE)
                .max(Comparator.comparingInt(SpellCard::getAmount));
        if (damage.isPresent()) {
            return damage;
        }
        return spells.stream().filter(s -> s.getKind() == SpellCard.Kind.DRAW).findFirst()
                .or(() -> spells.stream().filter(s -> s.getKind() == SpellCard.Kind.HEAL).findFirst());
    }
}
