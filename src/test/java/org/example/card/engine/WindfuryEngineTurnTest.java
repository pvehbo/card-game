package org.example.card.engine;

import org.example.card.ai.AiStrategy;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则一致性复现（批① T2）：风怒随从在引擎自动回合必须能出手两次。
 * 修复前的缺陷：playTurn 对每个随从只调一次 aiStrike，风怒第二击永远丢失。
 */
class WindfuryEngineTurnTest {

    private static final Consumer<String> QUIET = msg -> { };

    /** 风怒随从 + 空策略：整回合只打击英雄两腿，不许漏第二击。 */
    @Test
    void windfuryStrikesTwiceInPlayTurn() {
        PlayerState self = solo("我", 2, Keyword.WINDFURY);  // 一个风怒随从，回合内已可行动
        PlayerState foe = village("敌", 20);
        GameEngine engine = new GameEngine(noAi());

        engine.beginAiTurn(self, QUIET);
        engine.endAiTurn(self, foe, QUIET);     // 解召唤失调 + 清攻击计数，进入可行动态
        for (MinionCard m : self.getField()) {  // 摆拍后手动进入可攻击状态
            m.setSummoningSickness(false);
        }

        engine.playTurn(self, foe, QUIET);

        assertEquals(16, foe.getLifePoints(), "风怒随从 2 攻 × 2 击 = 4 点伤害，40 设为 20 生命");
    }

    @Test
    void plainMinionStrikesOnce() {
        PlayerState self = solo("我", 1); // 无关键词：只出手一次
        PlayerState foe = village("敌", 40);
        GameEngine engine = new GameEngine(noAi());

        engine.beginAiTurn(self, QUIET);
        engine.endAiTurn(self, foe, QUIET);
        for (MinionCard m : self.getField()) {
            m.setSummoningSickness(false);
        }

        engine.playTurn(self, foe, QUIET);

        assertEquals(19, foe.getLifePoints(), "无风怒随从 1 攻 × 1 击 = 1 点伤害");
    }

    // ---------- 摆拍助手 ----------

    private static PlayerState solo(String name, int attack, org.example.card.model.Keyword... keywords) {
        PlayerState p = new PlayerState(name, new Deck(List.of()));
        MinionCard minion = new MinionCard("w1", "测试兵", "", attack, 10, 1, List.of(keywords));
        minion.setSummoningSickness(false);
        p.getField().add(minion);
        return p;
    }

    private static PlayerState village(String name, int lifeUnused) {
        PlayerState p = new PlayerState(name, new Deck(List.of()));
        // PlayerState 生命固定钳制在 [0, 20]，Drain 不再允许漂移
        return p;
    }

    private static AiStrategy noAi() {
        return new AiStrategy() {
            @Override public java.util.Optional<MinionCard> chooseMinion(java.util.List<Card> hand) {
                return java.util.Optional.empty();
            }
            @Override public java.util.Optional<org.example.card.model.SpellCard> chooseSpell(
                    org.example.card.ai.GameView view, java.util.List<Card> hand) {
                return java.util.Optional.empty();
            }
            @Override public java.util.Optional<org.example.card.model.PetCard> choosePet(java.util.List<Card> hand) {
                return java.util.Optional.empty();
            }
            @Override public java.util.Optional<MinionCard> chooseAttackTarget(
                    org.example.card.ai.GameView view, java.util.List<org.example.card.ai.Target> legalTargets) {
                return java.util.Optional.empty();
            }
        };
    }
}