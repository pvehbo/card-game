package org.example.card.ai;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.SpellCard;

/**
 * 简单 AI（B-4）：在合法选项里均匀随机，专治“怎么打都输”的新手村体验。
 * 费用与合法性由调用方（引擎预过滤 + 快照）保证，这里只管随机。
 */
public final class RandomAi implements AiStrategy {

    private final Random random;

    public RandomAi() {
        this(new Random());
    }

    /** 可定种子的构造（测试复现用）。 */
    public RandomAi(Random random) {
        this.random = random;
    }

    @Override
    public Optional<MinionCard> chooseMinion(List<Card> hand) {
        List<MinionCard> minions = hand.stream()
                .filter(c -> c instanceof MinionCard)
                .map(c -> (MinionCard) c)
                .toList();
        if (minions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(minions.get(random.nextInt(minions.size())));
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
        return Optional.of(spells.get(random.nextInt(spells.size())));
    }

    @Override
    public Optional<PetCard> choosePet(List<Card> hand) {
        List<PetCard> pets = hand.stream()
                .filter(c -> c instanceof PetCard)
                .map(c -> (PetCard) c)
                .toList();
        if (pets.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pets.get(random.nextInt(pets.size())));
    }

    @Override
    public Optional<MinionCard> chooseAttackTarget(GameView view, List<Target> legalTargets) {
        // 打脸只有在「对方没有嘲讽」时才是合法选项：有嘲讽时引擎给的 legalTargets 只含嘲讽，
        // 这时若返回 empty（打脸）会被引擎判非法，这一次出手就被白白浪费掉。
        boolean faceLegal = view.foeMinions().stream()
                .noneMatch(GameView.MinionInfo::taunt);
        int options = legalTargets.size() + (faceLegal ? 1 : 0);
        if (options == 0) {
            return Optional.empty();
        }
        int pick = random.nextInt(options);
        return pick < legalTargets.size()
                ? Optional.of(legalTargets.get(pick).card())
                : Optional.empty();
    }
}
