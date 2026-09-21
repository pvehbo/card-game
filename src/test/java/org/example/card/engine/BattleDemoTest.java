package org.example.card.engine;

import org.example.card.ai.SimpleAi;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 整局演示回归：AI vs AI 打满 12 回合，断言能正常结束（有人获胜或按血量判胜），
 * 并把完整战报打印出来方便人肉检查规则。
 */
class BattleDemoTest {

    @Test
    void fullAiBattleFinishes() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState alice = new PlayerState("爱丽丝(AI)", new Deck(demoDeck()));
        PlayerState bob = new PlayerState("鲍勃(AI)", new Deck(demoDeck()));
        alice.getDeck().shuffle();
        bob.getDeck().shuffle();
        for (int i = 0; i < 3; i++) {
            alice.getDeck().draw().ifPresent(alice.getHand()::add);
            bob.getDeck().draw().ifPresent(bob.getHand()::add);
        }

        List<String> logs = new ArrayList<>();
        logs.add("开战！双方 20 生命");
        PlayerState first = alice;
        PlayerState second = bob;
        String winner = null;
        int rounds = 0;
        for (int round = 0; round < 12; round++) {
            rounds++;
            engine.playTurn(first, second, logs::add);
            if (second.isDefeated()) {
                winner = first.getName();
                logs.add("胜负已分：胜者 " + winner);
                break;
            }
            PlayerState tmp = first;
            first = second;
            second = tmp;
        }
        if (winner == null) {
            winner = alice.getLifePoints() >= bob.getLifePoints() ? alice.getName() : bob.getName();
            logs.add("12 回合打满，按生命判定胜者：" + winner);
        }

        System.out.println(String.join(System.lineSeparator(), logs));
        // 稳定断言：12 回合（或提前分出胜负）正常结束，且日志里有抽卡/上场记录表明引擎真在运转
        assertTrue(rounds >= 1, "至少进行了一回合");
        assertTrue(logs.stream().anyMatch(l -> l.contains("上场随从")),
                "对战里应有随从上场记录");
        assertTrue(logs.stream().anyMatch(l -> l.contains("直击") || l.contains("互撞") || l.contains("阵亡")),
                "对战里应有战斗结算记录");
    }

    private static List<Card> demoDeck() {
        List<Card> cards = new ArrayList<>();
        cards.add(new MinionCard("m1", "幼龙", "低攻快攻", 2, 1));
        cards.add(new MinionCard("m2", "铁壁卫士", "高血挡刀", 1, 5));
        cards.add(new MinionCard("m3", "烈焰剑士", "中坚输出", 3, 3));
        cards.add(new MinionCard("m4", "暗影刺客", "先手压制", 4, 2));
        cards.add(new MinionCard("m5", "雷霆巨人", "高攻终结", 6, 6));
        cards.add(new MinionCard("m6", "风语射手", "稳定输出", 3, 2));
        cards.add(new SpellCard("s1", "火球术", "打脸 3", SpellCard.Kind.DAMAGE, 3));
        cards.add(new SpellCard("s2", "治疗之触", "回 4", SpellCard.Kind.HEAL, 4));
        cards.add(new PetCard("p1", "战鼓兽", "全员+1 攻", 1, 0));
        cards.add(new PetCard("p2", "石皮兽", "全员+2 血", 0, 2));
        List<Card> full = new ArrayList<>(cards);
        full.addAll(cards);
        return full;
    }
}
