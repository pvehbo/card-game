package org.example.card.ui.view;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.example.card.ui.fx.Fx;
import org.example.card.ui.viewmodel.BoardViewModel;

/**
 * 战场视图（S7 从 CardGameApp 抽出）：四区刷新 + 控件查找 + 动效锚点查询。
 *
 * 只读 ViewModel 做决策（选中谁、能不能点），点击行为经 Handlers 回调界面；
 * 翻面/呼吸等纯表现调用 Fx。动效需要的节点查找（findMinionNode/heroPortrait）
 * 也收拢在这里，界面播动画时来问它。
 */
public final class BoardView {

    /** 点击行为回调（动效与结算留在界面层）。 */
    public interface Handlers {
        void onPlayCard(Card card);

        void onSelectAttacker(MinionCard minion);

        void onAttackMinion(MinionCard target);

        void onAttackHero();
    }

    private final BoardViewModel vm;
    private final Handlers handlers;
    private final HBox aiField;
    private final HBox playerField;
    private final HBox handBox;
    private final Label pileLabel;
    private HeroView aiHero;
    private HeroView playerHero;

    public BoardView(BoardViewModel vm, Handlers handlers,
                     HBox aiField, HBox playerField, HBox handBox,
                     Label pileLabel) {
        this.vm = vm;
        this.handlers = handlers;
        this.aiField = aiField;
        this.playerField = playerField;
        this.handBox = handBox;
        this.pileLabel = pileLabel;
    }

    /** 新对局：英雄控件重建（否则还引用旧对局状态）。 */
    public void reset() {
        aiHero = null;
        playerHero = null;
    }

    public void refresh() {
        if (vm.session() == null) {
            return;
        }
        refreshAiZone();
        refreshPlayerZone();
        refreshHand();
        PlayerState player = vm.session().getPlayer();
        pileLabel.setText("牌堆 " + player.getDeck().size() + " · 墓地 "
                + player.getGraveyard().size() + " · 第 " + Math.max(1, vm.turn()) + " 回合");
    }

    private void refreshAiZone() {
        PlayerState ai = vm.session().getAi();
        aiField.getChildren().clear();
        if (aiHero == null) {
            aiHero = new HeroView(ai, "魔", false);
            aiHero.getPortrait().setOnMouseClicked(e -> handlers.onAttackHero());
        }
        aiHero.refresh();
        aiHero.markTarget(vm.hasSelection() && vm.yourTurn());
        Fx.breathe(aiHero.getPortrait(), !vm.yourTurn() && !vm.gameOver());
        aiField.getChildren().add(aiHero);

        for (MinionCard m : ai.getField()) {
            MinionView view = new MinionView(m, ai, false);
            if (vm.hasSelection() && vm.yourTurn()) {
                view.markTarget();
                view.setOnMouseClicked(e -> handlers.onAttackMinion(m));
            }
            aiField.getChildren().add(view);
        }
        // 空槽占位
        for (int i = ai.getField().size(); i < Math.min(7, Math.max(3, ai.getField().size() + 1)); i++) {
            aiField.getChildren().add(emptySlot());
        }
    }

    private void refreshPlayerZone() {
        PlayerState player = vm.session().getPlayer();
        playerField.getChildren().clear();
        if (playerHero == null) {
            playerHero = new HeroView(player, "勇", true);
        }
        playerHero.refresh();
        playerHero.markActive(vm.yourTurn() && !vm.gameOver());
        Fx.breathe(playerHero.getPortrait(), vm.yourTurn() && !vm.gameOver());

        for (MinionCard m : player.getField()) {
            MinionView view = new MinionView(m, player, true);
            if (vm.selectedAttacker() == m) {
                view.markAttacker();
            }
            view.setOnMouseClicked(e -> handlers.onSelectAttacker(m));
            playerField.getChildren().add(view);
        }
        for (int i = player.getField().size(); i < Math.min(7, Math.max(3, player.getField().size() + 1)); i++) {
            playerField.getChildren().add(emptySlot());
        }
        // 己方英雄放在战场右侧
        playerField.getChildren().add(playerHero);
    }

    private void refreshHand() {
        PlayerState player = vm.session().getPlayer();
        handBox.getChildren().clear();
        for (Card c : player.getHand()) {
            CardView view = new CardView(c);
            view.setOnPlay(() -> handlers.onPlayCard(c));
            view.setPlayable(vm.yourTurn() && !vm.gameOver() && vm.isPlayable(c));
            handBox.getChildren().add(view);
            // 刚抽到的牌补一段“从牌堆滑入”的入场动画
            if (vm.consumeDrawn(c)) {
                Fx.drawIn(view);
            }
        }
        // 没来得及演动画就被打出去的牌，清掉标记避免泄漏
        vm.pruneDrawn();
    }

    private Region emptySlot() {
        Region slot = new Region();
        slot.getStyleClass().add("slot-empty");
        slot.setPrefSize(MinionView.SIZE, MinionView.SIZE);
        slot.setMinSize(MinionView.SIZE, MinionView.SIZE);
        return slot;
    }

    // ============ 动效锚点查询 ============

    /** 在双方战场里找到某个随从对应的控件。 */
    public Node findMinionNode(MinionCard minion) {
        for (Node n : aiField.getChildren()) {
            if (n instanceof MinionView mv && mv.getMinion() == minion) {
                return n;
            }
        }
        for (Node n : playerField.getChildren()) {
            if (n instanceof MinionView mv && mv.getMinion() == minion) {
                return n;
            }
        }
        return null;
    }

    /** 取得某一方英雄的头像控件（null 安全）。 */
    public Node heroPortrait(PlayerState side) {
        if (side == null || vm.session() == null) {
            return null;
        }
        if (side == vm.session().getPlayer()) {
            return playerHero == null ? null : playerHero.getPortrait();
        }
        if (side == vm.session().getAi()) {
            return aiHero == null ? null : aiHero.getPortrait();
        }
        return null;
    }

    public Node aiHeroPortrait() {
        return aiHero == null ? null : aiHero.getPortrait();
    }

    public Node playerHeroPortrait() {
        return playerHero == null ? null : playerHero.getPortrait();
    }

    /** 手牌区某张卡对应的控件（卡牌飞行动画起点用）。 */
    public Node findHandCardNode(Card card) {
        for (Node n : handBox.getChildren()) {
            if (n instanceof CardView cv && cv.getCard() == card) {
                return n;
            }
        }
        return null;
    }

    /** 场景根容器（震屏用），无场景时返回 null。 */
    public Region sceneRootNode() {
        Scene scene = handBox.getScene();
        return scene != null && scene.getRoot() instanceof Region region ? region : null;
    }
}
