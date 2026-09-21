package org.example.card.ai;

import org.example.card.engine.GameEngine;
import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 简易 AI（单机对手，贪心策略）：
 * - 随从：选攻击最高的（无费用，人人上得起）；
 * - 法术：能斩杀就打伤害，血危就回血，牌少就过牌，否则打伤害；
 * - 宠物：有就上；
 * - 攻击：优先打对方当前血量最低的随从，没有随从就打脸。
 */
public class SimpleAi {

    public Optional<MinionCard> chooseMinion(List<Card> hand) {
        return hand.stream()
                .filter(c -> c instanceof MinionCard)
                .map(c -> (MinionCard) c)
                .max(Comparator.comparingInt(MinionCard::getAttack));
    }

    public Optional<SpellCard> chooseSpell(List<Card> hand, PlayerState self, PlayerState foe) {
        List<SpellCard> spells = hand.stream()
                .filter(c -> c instanceof SpellCard)
                .map(c -> (SpellCard) c)
                .toList();
        if (spells.isEmpty()) {
            return Optional.empty();
        }
        // 1. 斩杀
        Optional<SpellCard> lethal = spells.stream()
                .filter(s -> s.getKind() == SpellCard.Kind.DAMAGE && s.getAmount() >= foe.getLifePoints())
                .findFirst();
        if (lethal.isPresent()) {
            return lethal;
        }
        // 2. 血危回血（10 点以下）
        if (self.getLifePoints() <= 10) {
            Optional<SpellCard> heal = spells.stream()
                    .filter(s -> s.getKind() == SpellCard.Kind.HEAL)
                    .findFirst();
            if (heal.isPresent()) {
                return heal;
            }
        }
        // 3. 手牌少时过牌
        if (self.getHand().size() <= 3) {
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

    public Optional<PetCard> choosePet(List<Card> hand) {
        return hand.stream()
                .filter(c -> c instanceof PetCard)
                .map(c -> (PetCard) c)
                .findFirst();
    }

    /** 在“对方当前血量最低”的随从里选目标；对方空场则打脸。 */
    public Optional<MinionCard> chooseAttackTarget(PlayerState self, PlayerState foe) {
        return foe.getField().stream()
                .min(Comparator.comparingInt(m -> GameEngine.currentHealth(foe, m)));
    }
}
