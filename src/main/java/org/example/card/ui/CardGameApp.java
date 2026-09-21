package org.example.card.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.card.ai.SimpleAi;
import org.example.card.engine.GameEngine;
import org.example.card.event.GameEvent;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 炉石式卡牌游戏（暗色奇幻风）玩家 vs AI。
 *
 * 操作：点手牌出牌；点己方随从选攻击者，再点对方随从/对方英雄攻击；点“结束回合”。
 */
public class CardGameApp extends Application {

    private static final Random RANDOM = new Random();

    private GameEngine engine = new GameEngine(new SimpleAi());
    private PlayerState player;
    private PlayerState ai;
    private boolean yourTurn = true;
    private MinionCard selectedAttacker;

    private final Label statusLabel = new Label("点击“开始游戏”");
    private final TextArea logArea = new TextArea();
    private final HBox aiField = new HBox(8);
    private final HBox playerField = new HBox(8);
    private final HBox handBox = new HBox(6);
    private final StackPane boardStack = new StackPane();
    private HeroView aiHero;
    private HeroView playerHero;
    private Button endTurnButton;
    private final Label pileLabel = new Label();

    @Override
    public void start(Stage stage) {
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("log-view");
        logArea.setPrefRowCount(7);

        Button startButton = new Button("开始游戏");
        startButton.getStyleClass().add("btn");
        startButton.setOnAction(e -> startNewGame());

        endTurnButton = new Button("结束回合");
        endTurnButton.getStyleClass().addAll("btn", "btn-primary");
        endTurnButton.setDisable(true);
        endTurnButton.setOnAction(e -> endYourTurn());

        statusLabel.getStyleClass().add("status-banner");
        pileLabel.getStyleClass().add("pile-info");

        // ---- 对手区 ----
        VBox aiZone = new VBox(6);
        aiZone.getStyleClass().add("panel");
        aiField.setAlignment(Pos.CENTER);
        aiField.setMinHeight(96);
        aiZone.getChildren().add(aiField);

        // ---- 中央战场 ----
        VBox boardZone = new VBox(8);
        boardZone.getStyleClass().add("battlefield");
        playerField.setAlignment(Pos.CENTER);
        playerField.setMinHeight(96);
        boardZone.getChildren().add(playerField);

        // ---- 手牌区 ----
        VBox handZone = new VBox(4);
        handBox.setAlignment(Pos.CENTER);
        handBox.setMinHeight(CardView.HEIGHT + 16);
        handZone.getChildren().add(handBox);

        HBox controls = new HBox(12, startButton, endTurnButton, statusLabel, pileLabel);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(6, 4, 0, 4));

        VBox playerZone = new VBox(6);
        playerZone.getStyleClass().add("panel");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setTop(new VBox(8, aiZone, boardZone));
        root.setCenter(new VBox(8, logArea, controls));
        root.setBottom(new VBox(8, handZone, playerZone));

        // 场景根用 StackPane，便于叠加横幅与伤害飘字
        StackPane sceneRoot = new StackPane();

        // 关键：运行时解析中文字体并用单一字体名设置，避免 CSS 多字体回退失效导致乱码
        String fontCss = Fonts.cssFontFamily();
        if (fontCss != null) {
            sceneRoot.setStyle(fontCss);
        }

        // 背景图（可选）：提供 images/backgrounds/board.jpg 就自动铺满
        Assets.background().ifPresent(bg -> {
            javafx.scene.image.ImageView bgView = new javafx.scene.image.ImageView(bg);
            bgView.setPreserveRatio(false);
            bgView.setSmooth(true);
            bgView.setMouseTransparent(true);
            bgView.fitWidthProperty().bind(sceneRoot.widthProperty());
            bgView.fitHeightProperty().bind(sceneRoot.heightProperty());
            sceneRoot.getChildren().add(bgView);
        });
        sceneRoot.getChildren().add(root);

        Scene scene = new Scene(sceneRoot, 1000, 760);
        var css = getClass().getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("Card Game · 暗色奇幻");
        stage.setScene(scene);
        stage.show();

        // 诊断/宣传用：-Dui.screenshot=<路径> 时自动构造演示局面、截图并退出
        String shot = System.getProperty("ui.screenshot");
        if (shot != null) {
            startNewGame();
            setupDemoBoard();
            refresh();
            javafx.animation.PauseTransition wait =
                    new javafx.animation.PauseTransition(Duration.millis(1600));
            wait.setOnFinished(e -> {
                try {
                    javafx.scene.image.WritableImage img = scene.snapshot(null);
                    javax.imageio.ImageIO.write(
                            javafx.embed.swing.SwingFXUtils.fromFXImage(img, null),
                            "png", new java.io.File(shot));
                    System.out.println("SCREENSHOT_SAVED=" + shot);
                } catch (Exception ex) {
                    System.out.println("SCREENSHOT_FAILED=" + ex);
                }
                Platform.exit();
            });
            wait.play();
        }
    }

    /** 构造一个“看起来在对战”的演示局面（仅用于截图/宣传图）。 */
    private void setupDemoBoard() {
        player.getField().clear();
        ai.getField().clear();
        player.getHand().clear();
        player.getPets().clear();
        ai.getPets().clear();

        // 血量差异：你 16，AI 12
        player.damage(4);
        ai.damage(8);

        // 你的随从（第三只带伤，展示血量变化；已解除召唤失调）
        MinionCard dragon = new MinionCard("m1", "幼龙", "低攻快攻", 2, 1);
        MinionCard blade = new MinionCard("m3", "烈焰剑士", "中坚输出", 3, 3);
        MinionCard archer = new MinionCard("m6", "风语射手", "稳定输出", 3, 2);
        archer.takeDamage(1);
        for (MinionCard m : List.of(dragon, blade, archer)) {
            m.setSummoningSickness(false);
            player.getField().add(m);
        }

        // 你的宠物光环：全员 +1 攻
        player.getPets().add(new PetCard("p1", "战鼓兽", "全员+1 攻", 1, 0));

        // AI 的随从
        MinionCard assassin = new MinionCard("m4", "暗影刺客", "先手压制", 4, 2);
        MinionCard giant = new MinionCard("m5", "雷霆巨人", "高攻终结", 6, 6);
        giant.takeDamage(3);
        for (MinionCard m : List.of(assassin, giant)) {
            m.setSummoningSickness(false);
            ai.getField().add(m);
        }
        ai.getPets().add(new PetCard("p2", "石皮兽", "全员+2 血", 0, 2));

        // 你的手牌
        player.getHand().addAll(List.of(
                new MinionCard("m2", "铁壁卫士", "高血挡刀", 1, 5),
                new SpellCard("s1", "火球术", "打脸 3", SpellCard.Kind.DAMAGE, 3),
                new SpellCard("s2", "治疗之触", "回 4", SpellCard.Kind.HEAL, 4),
                new PetCard("p1", "战鼓兽", "全员+1 攻", 1, 0)));

        statusLabel.setText("你的回合 · 请出牌");
        log("你上场了随从：随从《幼龙》");
        log("法术：火球术 对敌方英雄 -3");
        log("选中 烈焰剑士，点击对方随从或英雄头像进行攻击");
    }

    /** 新游戏：初始化双方牌堆、手牌，随机先后手。 */
    private void startNewGame() {
        engine = new GameEngine(new SimpleAi());
        player = new PlayerState("你", new Deck(demoDeck()));
        ai = new PlayerState("AI", new Deck(demoDeck()));
        player.getDeck().shuffle();
        ai.getDeck().shuffle();
        for (int i = 0; i < 3; i++) {
            player.getDeck().draw().ifPresent(player.getHand()::add);
            ai.getDeck().draw().ifPresent(ai.getHand()::add);
        }
        // 截图模式下固定玩家先手，且不触发 AI 自动回合（否则会覆盖演示局面）
        boolean screenshotMode = System.getProperty("ui.screenshot") != null;
        yourTurn = screenshotMode || RANDOM.nextBoolean();
        selectedAttacker = null;
        // 新对局必须重建英雄控件，否则它们还引用旧的对局状态
        aiHero = null;
        playerHero = null;
        subscribeEvents();
        logArea.clear();
        log("开局：你与 AI 各 3 张手牌，20 生命，无费用；每回合各限 1 随从 + 1 法术 + 1 宠物。");
        endTurnButton.setDisable(false);
        if (yourTurn) {
            if (!screenshotMode) {
                engine.startPlayerTurn(player, ai, this::log);
            }
            statusLabel.setText("你的回合 · 请出牌");
        } else {
            statusLabel.setText("AI 先手…");
            runAiTurn();
        }
        refresh();
    }

    /** 订阅引擎事件：伤害飘字、胜负横幅、模式化提示。 */
    private void subscribeEvents() {
        var bus = engine.eventBus();
        // 伤害：在受击方（英雄或随从）上方飘数字
        bus.on(GameEvent.Type.DAMAGE, e -> {
            Node anchor = null;
            if (e.target() == ai) {
                anchor = aiHero == null ? null : aiHero.getPortrait();
            } else if (e.target() == player) {
                anchor = playerHero == null ? null : playerHero.getPortrait();
            } else if (e.defender() != null) {
                anchor = findMinionNode(e.defender());
            }
            floatDamage(anchor, "-" + e.amount(), false);
        });
        // 回合开始：横幅提示
        bus.on(GameEvent.Type.TURN_START, e -> {
            if (e.actor() == player) {
                showBanner("你 的 回 合", "#f0c96b");
            } else if (e.actor() == ai) {
                showBanner("A I 回 合", "#c98b6b");
            }
        });
    }

    /** 在双方战场里找到某个随从对应的控件。 */
    private Node findMinionNode(MinionCard minion) {
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

    /** 出牌。 */
    private void playCard(Card card) {        if (!yourTurn || gameOver()) {
            return;
        }
        boolean acted = false;
        if (card instanceof MinionCard minion) {
            acted = engine.playMinion(player, minion, this::log);
        } else if (card instanceof SpellCard spell) {
            acted = engine.playSpell(player, ai, spell, this::log);
        } else if (card instanceof PetCard pet) {
            acted = engine.playPet(player, pet, this::log);
        }
        if (acted) {
            checkGameOver();
        }
        refresh();
    }

    /** 选中攻击者，或取消选择。 */
    private void selectAttacker(MinionCard minion) {
        if (!yourTurn || gameOver()) {
            return;
        }
        if (selectedAttacker == minion) {
            selectedAttacker = null;
            log("取消选择：" + minion.getName());
        } else if (minion.isSummoningSickness()) {
            log(minion.getName() + " 召唤失调，本回合还不能攻击");
        } else {
            selectedAttacker = minion;
            log("选中 " + minion.getName() + "，点击对方随从或英雄头像进行攻击");
        }
        refresh();
    }

    private void attack(MinionCard target) {
        if (selectedAttacker == null) {
            return;
        }
        MinionCard attacker = selectedAttacker;
        selectedAttacker = null;
        engine.attack(player, ai, attacker, target, this::log);
        checkGameOver();
        refresh();
    }

    private void attackHero() {
        if (selectedAttacker == null) {
            return;
        }
        MinionCard attacker = selectedAttacker;
        selectedAttacker = null;
        engine.attack(player, ai, attacker, null, this::log);
        checkGameOver();
        refresh();
    }

    private void endYourTurn() {
        if (!yourTurn || gameOver()) {
            return;
        }
        engine.endPlayerTurn(player, ai, this::log);
        yourTurn = false;
        statusLabel.setText("AI 回合…");
        refresh();
        runAiTurn();
    }

    /** AI 在后台线程跑一整个回合，避免界面卡死。 */
    private void runAiTurn() {
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            Platform.runLater(() -> {
                engine.playTurn(ai, player, this::log);
                checkGameOver();
                if (!gameOver()) {
                    yourTurn = true;
                    engine.startPlayerTurn(player, ai, this::log);
                    statusLabel.setText("你的回合 · 请出牌");
                }
                selectedAttacker = null;
                refresh();
            });
        }).start();
    }

    private boolean gameOver() {
        return player != null && (player.isDefeated() || ai.isDefeated());
    }

    private void checkGameOver() {
        if (player == null) {
            return;
        }
        if (player.isDefeated()) {
            log("败北……你的英雄倒下了。");
            statusLabel.setText("你输了");
            endTurnButton.setDisable(true);
            showBanner("败 北", "#ff6b6b");
        } else if (ai.isDefeated()) {
            log("胜利！敌方英雄被击溃。");
            statusLabel.setText("你赢了！");
            endTurnButton.setDisable(true);
            showBanner("胜 利", "#f0c96b");
        }
    }

    // ============ 界面刷新 ============

    private void refresh() {
        if (player == null) {
            return;
        }
        refreshAiZone();
        refreshPlayerZone();
        refreshHand();
        pileLabel.setText("牌堆 " + player.getDeck().size() + " · 墓地 "
                + player.getGraveyard().size() + " · 第 " + Math.max(1, engine.getTurn()) + " 回合");
    }

    private void refreshAiZone() {
        aiField.getChildren().clear();
        if (aiHero == null) {
            aiHero = new HeroView(ai, "魔", false);
            aiHero.getPortrait().setOnMouseClicked(e -> attackHero());
        }
        aiHero.refresh();
        aiHero.markTarget(selectedAttacker != null && yourTurn);
        aiField.getChildren().add(aiHero);

        for (MinionCard m : ai.getField()) {
            MinionView view = new MinionView(m, ai, false);
            if (selectedAttacker != null && yourTurn) {
                view.markTarget();
                view.setOnMouseClicked(e -> attack(m));
            }
            aiField.getChildren().add(view);
        }
        // 空槽占位
        for (int i = ai.getField().size(); i < Math.min(7, Math.max(3, ai.getField().size() + 1)); i++) {
            aiField.getChildren().add(emptySlot());
        }
    }

    private void refreshPlayerZone() {
        playerField.getChildren().clear();
        if (playerHero == null) {
            playerHero = new HeroView(player, "勇", true);
        }
        playerHero.refresh();
        playerHero.markActive(yourTurn && !gameOver());

        for (MinionCard m : player.getField()) {
            MinionView view = new MinionView(m, player, true);
            if (selectedAttacker == m) {
                view.markAttacker();
            }
            view.setOnMouseClicked(e -> selectAttacker(m));
            playerField.getChildren().add(view);
        }
        for (int i = player.getField().size(); i < Math.min(7, Math.max(3, player.getField().size() + 1)); i++) {
            playerField.getChildren().add(emptySlot());
        }
        // 己方英雄放在战场右侧
        playerField.getChildren().add(playerHero);
    }

    private void refreshHand() {
        handBox.getChildren().clear();
        for (Card c : player.getHand()) {
            CardView view = new CardView(c);
            view.setOnPlay(() -> playCard(c));
            boolean playable = yourTurn && !gameOver() && isPlayable(c);
            view.setPlayable(playable);
            handBox.getChildren().add(view);
        }
    }

    private boolean isPlayable(Card card) {
        if (card instanceof MinionCard) {
            return engine.canPlayMinion(player);
        } else if (card instanceof SpellCard) {
            return engine.canPlaySpell(player);
        } else if (card instanceof PetCard) {
            return engine.canPlayPet(player);
        }
        return false;
    }

    private Region emptySlot() {
        Region slot = new Region();
        slot.getStyleClass().add("slot-empty");
        slot.setPrefSize(MinionView.SIZE, MinionView.SIZE);
        slot.setMinSize(MinionView.SIZE, MinionView.SIZE);
        return slot;
    }

    // ============ 动效 ============

    /** 中央横幅：回合切换 / 胜负。 */
    private void showBanner(String text, String color) {
        // 截图模式下不弹横幅，避免遮挡画面
        if (System.getProperty("ui.screenshot") != null) {
            return;
        }
        Label banner = new Label(text);
        banner.setStyle("-fx-text-fill: " + color
                + "; -fx-font-size: 46px; -fx-font-weight: bold;"
                + " -fx-effect: dropshadow(gaussian, " + color + ", 26, 0.6, 0, 0);");
        banner.setMouseTransparent(true);
        if (handBox.getScene() == null) {
            return;
        }
        StackPane rootStack = (StackPane) handBox.getScene().getRoot();
        Pane overlay = new Pane(banner);
        overlay.setMouseTransparent(true);
        overlay.setPickOnBounds(false);
        rootStack.getChildren().add(overlay);
        banner.layoutXProperty().bind(rootStack.widthProperty().subtract(200).divide(2));
        banner.layoutYProperty().bind(rootStack.heightProperty().subtract(150).divide(2));

        FadeTransition in = new FadeTransition(Duration.millis(260), banner);
        in.setFromValue(0);
        in.setToValue(1);
        ScaleTransition pop = new ScaleTransition(Duration.millis(320), banner);
        pop.setFromX(0.6);
        pop.setFromY(0.6);
        pop.setToX(1.0);
        pop.setToY(1.0);
        pop.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition out = new FadeTransition(Duration.millis(700), banner);
        out.setFromValue(1);
        out.setToValue(0);
        out.setDelay(Duration.millis(1100));

        SequentialTransition seq = new SequentialTransition(new ParallelTransition(in, pop), out);
        seq.setOnFinished(e -> rootStack.getChildren().remove(overlay));
        seq.play();
    }

    /** 伤害飘字（在目标控件上方浮起淡出）。 */
    private void floatDamage(Node anchor, String text, boolean heal) {
        if (anchor == null || anchor.getScene() == null) {
            return;
        }
        Label label = new Label(text);
        label.getStyleClass().add(heal ? "heal-float" : "damage-float");
        label.setMouseTransparent(true);

        Pane overlay = new Pane(label);
        overlay.setMouseTransparent(true);
        overlay.setPickOnBounds(false);

        StackPane rootStack = (StackPane) anchor.getScene().getRoot();
        rootStack.getChildren().add(overlay);

        var bounds = anchor.localToScene(anchor.getBoundsInLocal());
        label.setLayoutX(bounds.getMinX() + bounds.getWidth() / 2 - 12);
        label.setLayoutY(bounds.getMinY());

        TranslateTransition rise = new TranslateTransition(Duration.millis(900), label);
        rise.setByY(-46);
        rise.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition fade = new FadeTransition(Duration.millis(900), label);
        fade.setFromValue(1);
        fade.setToValue(0);
        ScaleTransition pop = new ScaleTransition(Duration.millis(300), label);
        pop.setFromX(0.7);
        pop.setFromY(0.7);
        pop.setToX(1.25);
        pop.setToY(1.25);

        ParallelTransition anim = new ParallelTransition(rise, fade, pop);
        anim.setOnFinished(e -> rootStack.getChildren().remove(overlay));
        anim.play();
    }

    private void log(String msg) {
        logArea.appendText(msg + System.lineSeparator());
    }

    // ============ 牌库 ============

    static List<Card> demoDeck() {
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

    public static void main(String[] args) {
        launch(args);
    }
}
