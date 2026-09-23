package org.example.card.ai;

import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.SpellCard;

import java.util.List;
import java.util.Optional;

/**
 * AI 决策接口（S6 接口化）：
 * 所有决策只依赖只读快照（{@link GameView}）或手牌列表，不接触 PlayerState / 引擎实体。
 * 便于替换成更复杂的 AI、外部 AI 服务，也便于单测；引擎既可持具体 SimpleAi，
 * 也可改持本接口。
 */
public interface AiStrategy {

    /** 从手牌里选一张随从上场（无合适随从返回 empty）。 */
    Optional<MinionCard> chooseMinion(List<Card> hand);

    /** 从手牌里选一张法术打出（无合适法术返回 empty）。 */
    Optional<SpellCard> chooseSpell(GameView view, List<Card> hand);

    /** 从手牌里选一只宠物召唤（没有就返回 empty）。 */
    Optional<PetCard> choosePet(List<Card> hand);

    /**
     * 在合法目标里选一个攻击目标（返回选中随从的 MinionCard）；legalTargets 为空代表打脸（返回 empty）。
     * 目标血量已由调用方算好放进 {@link Target}，AI 不必接触 PlayerState。
     */
    Optional<MinionCard> chooseAttackTarget(GameView view, List<Target> legalTargets);
}