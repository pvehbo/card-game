package org.example.card.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
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
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
import org.example.card.ui.fx.Fx;
import org.example.card.ui.fx.ParticleLayer;
import org.example.card.ui.fx.Sfx;
import org.example.card.ui.fx.SoundEngine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
    private ParticleLayer particleLayer;
    private Pane fxLayer;
    private Button soundButton;
    /** 胜负只播报一次（引擎事件与界面自检都会触发）。 */
    private boolean announcedOver;
    /** 本回合新抽到的牌：等控件建好后补一段"抽牌入场"动画。 */
    private final Set<Card> pendingDrawAnim = new HashSet<>();
    /** AI 回合的演出步骤队列（每步之间留间隔，避免音效/动效挤在一起）。 */
    private final Deque<Runnable> aiSteps = new ArrayDeque<>();
    /** 每步之间的停顿：让玩家看清 AI 做了什么。 */
    private static final Duration AI_STEP_GAP = Duration.millis(420);
    /** 自动对局（冒烟测试）每步间隔。 */
    private static final Duration AUTOPLAY_GAP = Duration.millis(650);
    /** 自动对局的步数硬上限，防止逻辑异常导致死循环。 */
    private static final int AUTOPLAY_STEP_CAP = 400;
    private int autoplayTurnsLeft;
    private int autoplaySteps;
    /**
     * 对局代数：每开一局 +1。
     * AI 回合是「后台线程睡一会儿 → 回到界面线程执行」，如果玩家在 AI 思考期间点「开始游戏」，
     * 那个还没执行的回调必须丢掉，否则新对局会被硬塞一个多余的 AI 回合。
     */
    private int gameGeneration;

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

        soundButton = new Button(SoundEngine.isEnabled() ? "音效：开" : "音效：关");
        soundButton.getStyleClass().add("btn");
        soundButton.setOnAction(e -> {
            SoundEngine.setEnabled(!SoundEngine.isEnabled());
            soundButton.setText(SoundEngine.isEnabled() ? "音效：开" : "音效：关");
            if (SoundEngine.isEnabled()) {
                SoundEngine.play(Sfx.TURN);
            }
        });

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

        HBox controls = new HBox(12, startButton, endTurnButton, soundButton, statusLabel, pileLabel);
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

        // 粒子层：覆盖在最上方，鼠标穿透
        particleLayer = new ParticleLayer();
        particleLayer.widthProperty().bind(sceneRoot.widthProperty());
        particleLayer.heightProperty().bind(sceneRoot.heightProperty());
        sceneRoot.getChildren().add(particleLayer);

        // 特效覆盖层：承载飞行动画（出牌飞入等）
        fxLayer = new Pane();
        fxLayer.setMouseTransparent(true);
        fxLayer.setPickOnBounds(false);
        sceneRoot.getChildren().add(fxLayer);

        Scene scene = new Scene(sceneRoot, 1000, 760);
        var css = getClass().getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("Card Game · 暗色奇幻");
        stage.setScene(scene);
        stage.show();

        // 启动粒子系统 + 预合成音效（失败自动降级，不影响游戏）
        particleLayer.start();
        SoundEngine.init();

        // 诊断/宣传用：-Dui.screenshot=<路径> 时自动构造演示局面、截图并退出
        // 若同时给了 -Dui.autoplay=N，则改由自动对局在打满 N 回合后再截图
        String shot = System.getProperty("ui.screenshot");
        if (shot != null && System.getProperty("ui.autoplay") == null) {
            startNewGame();
            setupDemoBoard();
            refresh();
            javafx.animation.PauseTransition wait =
                    new javafx.animation.PauseTransition(Duration.millis(1600));
            wait.setOnFinished(e -> {
                saveScreenshot(scene, shot);
                Platform.exit();
            });
            wait.play();
        }

        // 诊断冒烟测试：-Dui.autoplay=<回合数> 让程序自己跟 AI 打完若干回合
        startAutoplayIfRequested(scene);
    }

    /** 把当前场景存成 PNG（诊断用）。 */
    private static void saveScreenshot(Scene scene, String path) {
        try {
            WritableImage img = scene.snapshot(null);
            javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null),
                    "png", new java.io.File(path));
            System.out.println("SCREENSHOT_SAVED=" + path);
        } catch (Exception ex) {
            System.out.println("SCREENSHOT_FAILED=" + ex);
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
        gameGeneration++;
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
        announcedOver = false;
        pendingDrawAnim.clear();
        aiSteps.clear();
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

    /** 订阅引擎事件：伤害飘字、粒子、音效、胜负横幅。 */
    private void subscribeEvents() {
        var bus = engine.eventBus();

        // 抽牌：音效；玩家抽到的牌等控件建好后补入场动画
        bus.on(GameEvent.Type.DRAW, e -> {
            SoundEngine.play(Sfx.DRAW);
            if (e.actor() == player && e.card() != null) {
                pendingDrawAnim.add(e.card());
            }
        });

        // 手牌满，烧牌
        bus.on(GameEvent.Type.BURN, e -> SoundEngine.play(Sfx.DEATH));

        // 上场：音效 + 落地上场粒子 / 翻面动画
        // 随从控件要等 refresh() 之后才存在，所以表现部分推迟一帧再做
        bus.on(GameEvent.Type.SUMMON, e -> {
            SoundEngine.play(Sfx.SUMMON);
            if (e.card() instanceof MinionCard m) {
                Platform.runLater(() -> summonFeedback(m));
            }
        });

        // 法术：音效（伤害类还会额外收到 DAMAGE 事件；治疗类飘绿字）
        bus.on(GameEvent.Type.SPELL, e -> {
            SoundEngine.play(Sfx.SPELL);
            if (e.card() instanceof SpellCard s && s.getKind() == SpellCard.Kind.HEAL && e.actor() == player) {
                floatDamage(heroPortrait(player), "+" + e.amount(), true);
            }
        });

        // 宠物：音效 + 金色粒子（锚在召唤方英雄身上）
        bus.on(GameEvent.Type.PET, e -> {
            SoundEngine.play(Sfx.PET);
            Node portrait = heroPortrait(e.actor());
            if (portrait != null && particleLayer != null) {
                var b = portrait.localToScene(portrait.getBoundsInLocal());
                particleLayer.burst(b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2,
                        Color.web("#f0c96b"), 18);
            }
        });

        // 伤害：飘字 + 受击闪光 + 火花 + 音效 + 屏幕震动
        bus.on(GameEvent.Type.DAMAGE, e -> {
            // 随从优先：互撞的伤害事件带着具体随从，飘字必须锚在随从身上而不是英雄头像上
            Node anchor = e.defender() != null ? findMinionNode(e.defender()) : heroPortrait(e.target());
            floatDamage(anchor, "-" + e.amount(), false);
            Fx.hitFlash(anchor);
            SoundEngine.play(Sfx.DAMAGE);

            if (anchor != null && particleLayer != null) {
                var b = anchor.localToScene(anchor.getBoundsInLocal());
                particleLayer.burst(b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2,
                        Color.web("#ff8855"), 22);
            }
            // 英雄挨打才震屏（随从互撞的小抖动交给攻击动画）
            if (e.defender() == null) {
                Fx.shake(sceneRootNode(), e.amount() >= 4 ? 6.0 : 3.0);
            }
        });

        // 阵亡：消散粒子 + 音效
        bus.on(GameEvent.Type.DEATH, e -> {
            SoundEngine.play(Sfx.DEATH);
            Node node = findMinionNode(e.card() instanceof MinionCard m ? m : null);
            if (node != null && particleLayer != null) {
                var b = node.localToScene(node.getBoundsInLocal());
                particleLayer.dissipate(b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2,
                        Color.web("#9a7bff"), 26);
            }
        });

        // 回合开始：横幅 + 音效
        bus.on(GameEvent.Type.TURN_START, e -> {
            SoundEngine.play(Sfx.TURN);
            if (e.actor() == player) {
                showBanner("你 的 回 合", "#f0c96b");
            } else if (e.actor() == ai) {
                showBanner("A I 回 合", "#c98b6b");
            }
        });

        // 胜负：e.actor() 是胜者
        bus.on(GameEvent.Type.GAME_OVER, e -> announceGameOver(e.actor() == player));
    }

    /** 新随从上场的表现：翻面入场 + 一圈绿色火花（控件已建好后再调用）。 */
    private void summonFeedback(MinionCard minion) {
        Node node = findMinionNode(minion);
        if (node == null || node.getScene() == null) {
            return;
        }
        Fx.flipIn(node);
        var b = node.localToScene(node.getBoundsInLocal());
        if (particleLayer != null && b.getWidth() > 0 && b.getHeight() > 0) {
            particleLayer.burst(b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2,
                    Color.web("#8ce6a8"), 14);
        }
    }

    /** 取得某一方英雄的头像控件（null 安全）。 */
    private Node heroPortrait(PlayerState side) {
        if (side == null) {
            return null;
        }
        if (side == player) {
            return playerHero == null ? null : playerHero.getPortrait();
        }
        if (side == ai) {
            return aiHero == null ? null : aiHero.getPortrait();
        }
        return null;
    }

    /** 场景根容器（震屏用），无场景时返回 null。 */
    private Region sceneRootNode() {
        Scene scene = handBox.getScene();
        return scene != null && scene.getRoot() instanceof Region region ? region : null;
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

    /** 出牌（带"卡牌从手牌飞向战场"的动效）。 */
    private void playCard(Card card) {
        if (!yourTurn || gameOver()) {
            return;
        }
        // 找到被点击的那张手牌控件，作为飞行动画的起点
        Node sourceCard = null;
        for (Node n : handBox.getChildren()) {
            if (n instanceof CardView cv && cv.getCard() == card) {
                sourceCard = n;
                break;
            }
        }

        boolean acted;
        if (card instanceof MinionCard minion) {
            acted = engine.playMinion(player, minion, this::log);
        } else if (card instanceof SpellCard spell) {
            acted = engine.playSpell(player, ai, spell, this::log);
        } else if (card instanceof PetCard pet) {
            acted = engine.playPet(player, pet, this::log);
        } else {
            acted = false;
        }

        if (!acted) {
            refresh();
            return;
        }

        checkGameOver();
        refresh();

        // 出牌动效：用起点卡片的快照飞向对应的落点
        if (sourceCard != null && fxLayer != null && fxLayer.getScene() != null) {
            animateCardFly(card, sourceCard);
        }
    }

    /** 用 snapshot 快照做"卡牌飞入战场"的视觉。 */
    private void animateCardFly(Card card, Node sourceCard) {
        Node destination = null;
        if (card instanceof MinionCard m) {
            destination = findMinionNode(m);
        } else if (card instanceof PetCard) {
            destination = playerHero == null ? null : playerHero.getPortrait();
        } else {
            // 法术：飞向敌方英雄（技能效果作用于对方）
            destination = aiHero == null ? null : aiHero.getPortrait();
        }
        if (destination == null) {
            return;
        }

        var src = sourceCard.localToScene(sourceCard.getBoundsInLocal());
        var dst = destination.localToScene(destination.getBoundsInLocal());
        WritableImage snap = sourceCard.snapshot(null, null);
        ImageView ghost = new ImageView(snap);
        ghost.setFitWidth(src.getWidth());
        ghost.setFitHeight(src.getHeight());
        ghost.setOpacity(0.95);
        ghost.setMouseTransparent(true);

        var root = fxLayer.getScene().getRoot();
        var rootBounds = root.localToScene(root.getBoundsInLocal());
        double startX = src.getMinX() - rootBounds.getMinX();
        double startY = src.getMinY() - rootBounds.getMinY();
        double endX = dst.getMinX() - rootBounds.getMinX() + (dst.getWidth() - src.getWidth()) / 2;
        double endY = dst.getMinY() - rootBounds.getMinY() + (dst.getHeight() - src.getHeight()) / 2;

        ghost.setLayoutX(0);
        ghost.setLayoutY(0);
        ghost.setTranslateX(startX);
        ghost.setTranslateY(startY);
        Fx.mountOn(fxLayer, ghost);
        Fx.flyCard(ghost, startX, startY, endX, endY,
                () -> fxLayer.getChildren().remove(ghost));

        // 法术额外拖一条粒子尾迹，让"施法"更有存在感
        if (card instanceof SpellCard) {
            Color tint = ((SpellCard) card).getKind() == SpellCard.Kind.DAMAGE
                    ? Color.web("#7fc4ff") : Color.web("#8cf0a8");
            Timeline trail = new Timeline();
            for (int i = 1; i <= 9; i++) {
                trail.getKeyFrames().add(new KeyFrame(Duration.millis(i * 46), e -> emitTrail(ghost, tint)));
            }
            trail.play();
        }
    }

    /** 在飞行的卡牌当前位置撒一撮尾迹粒子。 */
    private void emitTrail(Node ghost, Color tint) {
        if (particleLayer == null || ghost.getScene() == null) {
            return;
        }
        var b = ghost.localToScene(ghost.getBoundsInLocal());
        particleLayer.trail(b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2, tint, 5);
    }

    /** 选中攻击者，或取消选择。 */
    private void selectAttacker(MinionCard minion) {
        if (!yourTurn || gameOver()) {
            return;
        }
        if (selectedAttacker == minion) {
            selectedAttacker = null;
            log("取消选择：" + minion.getName());
        } else if (minion.isAttackedThisTurn()) {
            log(minion.getName() + " 本回合已经攻击过了");
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
        playAttack(attacker, findMinionNode(target), () -> {
            engine.attack(player, ai, attacker, target, this::log);
            checkGameOver();
            refresh();
        });
    }

    private void attackHero() {
        if (selectedAttacker == null) {
            return;
        }
        MinionCard attacker = selectedAttacker;
        selectedAttacker = null;
        Node heroNode = aiHero == null ? null : aiHero.getPortrait();
        playAttack(attacker, heroNode, () -> {
            engine.attack(player, ai, attacker, null, this::log);
            checkGameOver();
            refresh();
        });
    }

    /** 播放攻击动效（突刺 → 撞击反馈 → 结算）。 */
    private void playAttack(MinionCard attacker, Node targetNode, Runnable resolve) {
        Node attackerNode = findMinionNode(attacker);
        SoundEngine.play(Sfx.ATTACK);
        if (attackerNode == null) {
            resolve.run();
            return;
        }
        // atImpact：前冲到位那一刻的火花与抖屏；onComplete：整套动画结束再结算伤害
        Fx.lunge(attackerNode, targetNode, () -> impactFeedback(targetNode), resolve);
    }

    /** 撞击反馈：目标位置迸出金色火花 + 轻微抖屏。 */
    private void impactFeedback(Node targetNode) {
        if (targetNode != null && particleLayer != null && targetNode.getScene() != null) {
            var b = targetNode.localToScene(targetNode.getBoundsInLocal());
            if (b.getWidth() > 0 && b.getHeight() > 0) {
                particleLayer.burst(b.getMinX() + b.getWidth() / 2,
                        b.getMinY() + b.getHeight() / 2, Color.web("#ffd166"), 16);
            }
        }
        Fx.shake(sceneRootNode(), 4.0);
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

    /** AI 回合前的短暂停顿，然后回到界面线程逐步演出整个回合。 */
    private void runAiTurn() {
        final int generation = gameGeneration;
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            Platform.runLater(() -> {
                // 期间玩家可能已经点过「开始游戏」，这一局早就作废了
                if (generation == gameGeneration) {
                    planAiTurn();
                }
            });
        }).start();
    }

    /**
     * 把 AI 回合拆成"一个动作一步"的脚本，逐步播放。
     *
     * 之前是一口气把整个回合跑完，结果所有音效在同一帧炸响、粒子挤成一团、玩家也看不清
     * AI 做了什么。现在每步之间留 {@link #AI_STEP_GAP} 的间隔，攻击还等突刺动画演完再结算。
     */
    private void planAiTurn() {
        if (ai == null || player == null || gameOver()) {
            aiSteps.clear();
            return;
        }
        aiSteps.clear();
        engine.beginAiTurn(ai, this::log);
        refresh();

        aiSteps.add(() -> {
            if (engine.aiSummon(ai, this::log)) {
                refresh();
            }
        });
        aiSteps.add(() -> {
            if (engine.aiSpell(ai, player, this::log)) {
                refresh();
            }
        });
        aiSteps.add(() -> {
            if (engine.aiPet(ai, this::log)) {
                refresh();
            }
        });
        for (MinionCard attacker : engine.aiReadyAttackers(ai)) {
            aiSteps.add(() -> performAiStrike(attacker));
        }
        aiSteps.add(() -> {
            engine.endAiTurn(ai, player, this::log);
            finishAiTurn();
        });
        runNextAiStep();
    }

    /** AI 的一次出手：先把突刺演给玩家看，动画结束再真正结算伤害。 */
    private void performAiStrike(MinionCard attacker) {
        if (gameOver() || !ai.getField().contains(attacker) || attacker.isSummoningSickness()) {
            return;
        }
        MinionCard target = engine.chooseAiTarget(ai, player).orElse(null);
        Runnable strike = () -> {
            if (gameOver()) {
                return;
            }
            engine.aiStrike(ai, player, attacker, target, this::log);
            refresh();
            checkGameOver();
        };
        Node attackerNode = findMinionNode(attacker);
        Node targetNode = target != null ? findMinionNode(target) : heroPortrait(player);
        SoundEngine.play(Sfx.ATTACK);
        if (attackerNode == null) {
            strike.run();
            return;
        }
        Fx.lunge(attackerNode, targetNode, () -> impactFeedback(targetNode), strike);
    }

    /** 推进一步 AI 演出；队列空了或分出胜负就停下。 */
    private void runNextAiStep() {
        if (aiSteps.isEmpty() || gameOver()) {
            aiSteps.clear();
            return;
        }
        aiSteps.poll().run();
        if (aiSteps.isEmpty()) {
            return;
        }
        var gap = new javafx.animation.PauseTransition(AI_STEP_GAP);
        gap.setOnFinished(e -> runNextAiStep());
        gap.play();
    }

    /** AI 回合收尾：把回合交还给玩家（若已分出胜负则不再开局）。 */
    private void finishAiTurn() {
        checkGameOver();
        if (!gameOver()) {
            yourTurn = true;
            engine.startPlayerTurn(player, ai, this::log);
            statusLabel.setText("你的回合 · 请出牌");
        }
        selectedAttacker = null;
        refresh();
    }

    private boolean gameOver() {
        return player != null && (player.isDefeated() || ai.isDefeated());
    }

    /** 胜负播报（引擎事件与界面自检都会走到这里，所以必须幂等）。 */
    private void announceGameOver(boolean won) {
        if (player == null || announcedOver) {
            return;
        }
        announcedOver = true;
        endTurnButton.setDisable(true);
        if (won) {
            log("胜利！敌方英雄被击溃。");
            statusLabel.setText("你赢了！");
            showBanner("胜 利", "#f0c96b");
            SoundEngine.play(Sfx.WIN);
            if (particleLayer != null) {
                particleLayer.ambientDust(40);
            }
        } else {
            log("败北……你的英雄倒下了。");
            statusLabel.setText("你输了");
            showBanner("败 北", "#ff6b6b");
            SoundEngine.play(Sfx.LOSE);
        }
    }

    /** 界面自检：只要有一方倒下就播报（法术斩杀等路径不会发 GAME_OVER 事件）。 */
    private void checkGameOver() {
        if (player == null || announcedOver) {
            return;
        }
        if (player.isDefeated()) {
            announceGameOver(false);
        } else if (ai.isDefeated()) {
            announceGameOver(true);
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
        Fx.breathe(aiHero.getPortrait(), !yourTurn && !gameOver());
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
        Fx.breathe(playerHero.getPortrait(), yourTurn && !gameOver());

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
            // 刚抽到的牌补一段"从牌堆滑入"的入场动画
            if (pendingDrawAnim.remove(c)) {
                Fx.drawIn(view);
            }
        }
        // 没来得及演动画就被打出去的牌，清掉标记避免泄漏
        pendingDrawAnim.removeIf(c -> !player.getHand().contains(c));
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

    // ============ 自动对局（诊断冒烟测试） ============

    /**
     * -Dui.autoplay=&lt;回合数&gt;：程序自己跟 AI 打完若干回合后退出。
     * 这是整局流程的冒烟测试（尤其是 AI 的逐步演出与突刺结算），
     * 搭配 -Dui.screenshot=&lt;路径&gt; 可以在结束时留下一张截图。
     */
    private void startAutoplayIfRequested(Scene scene) {
        String raw = System.getProperty("ui.autoplay");
        if (raw == null) {
            return;
        }
        try {
            autoplayTurnsLeft = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            System.out.println("AUTOPLAY_BAD_ARG=" + raw);
            return;
        }
        if (autoplayTurnsLeft <= 0) {
            System.out.println("AUTOPLAY_BAD_ARG=" + raw);
            return;
        }
        System.out.println("AUTOPLAY_START turns=" + autoplayTurnsLeft);
        startNewGame();
        scheduleAutoplayStep(AUTOPLAY_GAP);
    }

    private void scheduleAutoplayStep(Duration delay) {
        var pause = new javafx.animation.PauseTransition(delay);
        pause.setOnFinished(e -> autoplayStep());
        pause.play();
    }

    /** 玩家的一个动作：出牌 → 攻击 → 结束回合；AI 回合期间只是等待。 */
    private void autoplayStep() {
        if (player == null || ai == null || gameOver()) {
            finishAutoplay("game-over");
            return;
        }
        if (++autoplaySteps > AUTOPLAY_STEP_CAP) {
            finishAutoplay("step-cap");
            return;
        }
        // AI 回合还在演（或还没开始演）：等它演完再轮到"玩家"
        if (!yourTurn || !aiSteps.isEmpty()) {
            scheduleAutoplayStep(AUTOPLAY_GAP);
            return;
        }
        for (Card c : new ArrayList<>(player.getHand())) {
            if (isPlayable(c)) {
                playCard(c);
                scheduleAutoplayStep(AUTOPLAY_GAP);
                return;
            }
        }
        for (MinionCard m : new ArrayList<>(player.getField())) {
            if (!m.isSummoningSickness() && !m.isAttackedThisTurn()) {
                selectedAttacker = m;
                attackHero();
                scheduleAutoplayStep(AUTOPLAY_GAP);
                return;
            }
        }
        autoplayTurnsLeft--;
        if (autoplayTurnsLeft <= 0) {
            finishAutoplay("turns-done");
            return;
        }
        endYourTurn();
        scheduleAutoplayStep(AUTOPLAY_GAP);
    }

    private void finishAutoplay(String reason) {
        System.out.println("AUTOPLAY_END reason=" + reason
                + " turn=" + (engine == null ? 0 : engine.getTurn())
                + " playerLife=" + (player == null ? "-" : player.getLifePoints())
                + " aiLife=" + (ai == null ? "-" : ai.getLifePoints()));
        String shot = System.getProperty("ui.screenshot");
        Scene scene = handBox.getScene();
        if (shot != null && scene != null) {
            // 等最后一帧动画落定再截图
            var pause = new javafx.animation.PauseTransition(Duration.millis(700));
            pause.setOnFinished(e -> {
                saveScreenshot(scene, shot);
                Platform.exit();
            });
            pause.play();
            return;
        }
        Platform.exit();
    }

    private void log(String msg) {
        logArea.appendText(msg + System.lineSeparator());
        // 自动对局时把战报同步打到控制台，方便冒烟测试直接看流程
        if (System.getProperty("ui.autoplay") != null) {
            System.out.println("[game] " + msg);
        }
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
