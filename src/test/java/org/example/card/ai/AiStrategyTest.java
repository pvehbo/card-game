package org.example.card.ai;

import org.example.card.engine.GameEngine;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S6 接口化单测：SimpleAi 实现 {@link AiStrategy}，新「快照版」重载与旧重载结果一致；
 * GameView 不许泄露可变模型实体、快照不受后续对局变化影响。
 */
class AiStrategyTest {

    private final SimpleAi ai = new SimpleAi();

    private static PlayerState playerOf(String name, List<Card> deck) {
        return new PlayerState(name, new Deck(new ArrayList<>(deck)));
    }

    private static MinionCard minion(String name, int atk, int hp) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp);
    }

    private static List<Card> handOf(Card... cards) {
        return new ArrayList<>(List.of(cards));
    }

    /** 随从：接口方法，选攻击最高的。 */
    @Test
    void chooseMinionPicksHighestAttack() {
        assertTrue(ai instanceof AiStrategy, "SimpleAi 应实现 AiStrategy 接口");
        MinionCard strong = minion("乙", 7, 3);
        List<Card> hand = handOf(
                new SpellCard("s1", "火球术", "打3", SpellCard.Kind.DAMAGE, 3),
                minion("甲", 2, 5),
                strong,
                new PetCard("p1", "战鼓兽", "攻+1", 1, 0));
        assertEquals(strong, ai.chooseMinion(hand).orElseThrow());
    }

    /** 法术：新快照版 chooseSpell 三条分支（斩杀 / 回血 / 过牌）各选对牌。 */
    @Test
    void spellBranchesPickRightCard() {
        // 斩杀：对方 5 血，火球打 6
        SpellCard lethal = new SpellCard("s2", "火球术", "打6", SpellCard.Kind.DAMAGE, 6);
        SpellCard heal = new SpellCard("s3", "回春术", "回4", SpellCard.Kind.HEAL, 4);
        assertEquals(lethal, pickSpell(20, 5, lethal, heal));
        // 血危回血：自己 <=10 血，敌方满血，应选治疗而非打伤害
        SpellCard poke = new SpellCard("s4", "火球术", "打3", SpellCard.Kind.DAMAGE, 3);
        SpellCard cure = new SpellCard("s5", "回春术", "回4", SpellCard.Kind.HEAL, 4);
        assertEquals(cure, pickSpell(5, 20, poke, cure));
        // 手牌少时过牌：自己满血、非斩杀、牌 <=3 张，应选过牌
        SpellCard chip = new SpellCard("s6", "火球术", "打2", SpellCard.Kind.DAMAGE, 2);
        SpellCard draw = new SpellCard("s7", "奥术智慧", "抽2", SpellCard.Kind.DRAW, 2);
        assertEquals(draw, pickSpell(20, 20, chip, draw));
    }

    /** 同一份手牌/对局走快照版 chooseSpell，返回指定分支的牌。 */
    private SpellCard pickSpell(int selfLife, int foeLife, SpellCard damage, SpellCard utility) {
        PlayerState self = playerOf("AI", new ArrayList<>());
        PlayerState foe = playerOf("你", new ArrayList<>());
        self.damage(PlayerState.START_LIFE - selfLife);
        foe.damage(PlayerState.START_LIFE - foeLife);
        self.getHand().addAll(List.of(damage, utility));

        return ai.chooseSpell(GameView.snapshot(self, foe), self.getHand()).orElseThrow();
    }

    /** 随从攻击目标：快照版选当前血量最低（含宠物光环血量），空目标列表（打脸）返回 empty。 */
    @Test
    void attackTargetPicksLowestHealthAndEmptyWhenNoTargets() {
        PlayerState self = playerOf("AI", new ArrayList<>());
        PlayerState foe = playerOf("你", new ArrayList<>());
        MinionCard weak = minion("脆皮", 1, 2);
        weak.takeDamage(1);                       // 当前血 1
        MinionCard tank = minion("铁壁", 0, 9);
        foe.getField().add(weak);
        foe.getField().add(tank);
        foe.getPets().add(new PetCard("p1", "石肤兽", "血+2", 0, 2));  // 铁壁当前血 11

        List<Target> targets = List.of(weak, tank).stream()
                .map(m -> new Target(m, GameEngine.currentHealth(foe, m)))
                .toList();

        assertEquals(weak, ai.chooseAttackTarget(GameView.snapshot(self, foe), targets).orElseThrow(),
                "选当前血量最低的随从");

        // 打脸：没有任何合法随从目标 -> empty
        Optional<MinionCard> empty =
                ai.chooseAttackTarget(GameView.snapshot(self, foe), List.of());
        assertTrue(empty.isEmpty(), "空目标（打脸）应返回 empty");
    }

    /** 防作弊 1：GameView 所有公开方法返回类型不得泄露 Deck / PlayerState / Card。 */
    @Test
    void gameViewPublicMethodsNeverExposeMutableModelTypes() {
        for (Method m : GameView.class.getMethods()) {
            Class<?> ret = m.getReturnType();
            assertFalse(Deck.class.isAssignableFrom(ret), m.getName() + " 返回类型泄露 Deck");
            assertFalse(PlayerState.class.isAssignableFrom(ret), m.getName() + " 返回类型泄露 PlayerState");
            assertFalse(Card.class.isAssignableFrom(ret), m.getName() + " 返回类型泄露 Card");
        }
    }

    /** 防作弊 2：快照后再改对手战场/血线，快照内容必须原样不变；getter 拿不到可变集合。 */
    @Test
    void snapshotIsImmutableAgainstLaterFoeChanges() {
        PlayerState self = playerOf("AI", new ArrayList<>());
        PlayerState foe = playerOf("你", new ArrayList<>());
        MinionCard a = minion("甲", 3, 5);
        foe.getField().add(a);

        GameView view = GameView.snapshot(self, foe);
        assertEquals(1, view.foeMinions().size());
        assertEquals("甲", view.foeMinions().get(0).name());
        assertEquals(5, view.foeMinions().get(0).currentHealth());

        // 拍完快照再改战场：换随从、新随从受伤、旧随从也受伤
        foe.getField().clear();
        MinionCard b = minion("乙", 8, 8);
        b.takeDamage(3);
        foe.getField().add(b);
        a.takeDamage(4);

        assertEquals(1, view.foeMinions().size(), "快照不受后续战场变化影响");
        assertEquals("甲", view.foeMinions().get(0).name());
        assertEquals(5, view.foeMinions().get(0).currentHealth());

        assertThrows(UnsupportedOperationException.class,
                () -> view.foeMinions().add(new GameView.MinionInfo("丙", 1)),
                "getter 不应暴露可变集合");
    }

    /** 快照血量必须委托 GameEngine.currentHealth（含宠物光环），不能自己重算光环。 */
    @Test
    void snapshotHealthUsesGameEngineCurrentHealthWithAura() {
        PlayerState self = playerOf("AI", new ArrayList<>());
        PlayerState foe = playerOf("你", new ArrayList<>());
        MinionCard m = minion("被光环覆盖", 2, 5);
        m.takeDamage(2);                                     // 基础上限 5 - 2 = 3
        foe.getField().add(m);
        foe.getPets().add(new PetCard("p1", "石肤兽", "血+2", 0, 2)); // 光环上限 +2 -> 5

        GameView view = GameView.snapshot(self, foe);
        assertEquals("被光环覆盖", view.foeMinions().get(0).name());
        assertEquals(5, view.foeMinions().get(0).currentHealth(), "5(上限)+2(光环)-2(受伤)");
    }
}