package org.example.card.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import org.example.card.engine.GameEngine;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;

/**
 * 场上随从控件（圆形徽章 + 攻/血数字 + 名字）。
 * 状态：可攻击 / 召唤失调 / 被选为攻击者 / 被选为攻击目标。
 */
public class MinionView extends StackPane {

    public static final double SIZE = 86;

    private final MinionCard minion;
    private final PlayerState owner;

    public MinionView(MinionCard minion, PlayerState owner, boolean own) {
        this.minion = minion;
        this.owner = owner;

        setPrefSize(SIZE, SIZE);
        setMinSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);
        getStyleClass().add("minion");

        Circle inner = new Circle(SIZE / 2 - 7);
        inner.setFill(javafx.scene.paint.Color.web(own ? "#1e3222" : "#3a2430"));
        inner.setStroke(javafx.scene.paint.Color.web(own ? "#6fae7f" : "#b07a8a"));
        inner.setStrokeWidth(1);
        inner.setMouseTransparent(true);

        Label glyph = new Label(glyphOf(minion));
        glyph.getStyleClass().add("hero-glyph");
        glyph.setStyle("-fx-font-size: 26px;");
        glyph.setMouseTransparent(true);

        Label name = new Label(shortName(minion.getName()));
        name.getStyleClass().add("minion-name");
        name.setMouseTransparent(true);
        StackPane.setAlignment(name, Pos.BOTTOM_CENTER);
        StackPane.setMargin(name, new Insets(0, 0, -1, 0));

        // 名字带深色底衬，避免压在圆形边框上糊成一团
        StackPane namePlate = new StackPane(name);
        namePlate.setMouseTransparent(true);
        namePlate.setMaxWidth(Region.USE_PREF_SIZE);
        namePlate.setMaxHeight(Region.USE_PREF_SIZE);
        namePlate.setStyle("-fx-background-color: rgba(10, 8, 16, 0.82);"
                + " -fx-background-radius: 8; -fx-padding: 0 6 0 6;");
        StackPane.setAlignment(namePlate, Pos.BOTTOM_CENTER);
        StackPane.setMargin(namePlate, new Insets(0, 0, -6, 0));

        getChildren().addAll(inner, namePlate);

        // 有立绘用立绘（复用卡图资源），否则用类型符号
        var art = Assets.cardArt(minion);
        if (art.isPresent()) {
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(art.get());
            iv.setFitWidth(SIZE - 14);
            iv.setFitHeight(SIZE - 14);
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.setMouseTransparent(true);
            Circle clip = new Circle((SIZE - 14) / 2, (SIZE - 14) / 2, SIZE / 2 - 7);
            iv.setClip(clip);
            getChildren().add(iv);
        } else {
            getChildren().add(glyph);
        }

        // 攻击力（左下）
        getChildren().add(badge(String.valueOf(GameEngine.effectiveAttack(owner, minion)),
                "badge-attack", Pos.BOTTOM_LEFT, 1, 8));
        // 当前血量（右下）
        int hp = GameEngine.currentHealth(owner, minion);
        getChildren().add(badge(String.valueOf(hp), "badge-health", Pos.BOTTOM_RIGHT, 1, 8));

        if (minion.isSummoningSickness()) {
            getStyleClass().add("minion-sick");
            Label sick = new Label("Zzz");
            sick.getStyleClass().add("subtle-text");
            sick.setMouseTransparent(true);
            StackPane.setAlignment(sick, Pos.TOP_CENTER);
            StackPane.setMargin(sick, new Insets(2, 0, 0, 0));
            getChildren().add(sick);
        } else if (minion.isAttackedThisTurn()) {
            // 本回合已经出过手：压暗，提示"这只已经动过了"
            getStyleClass().add("minion-spent");
            Label spent = new Label("已动");
            spent.getStyleClass().add("minion-spent-text");
            spent.setMouseTransparent(true);
            StackPane.setAlignment(spent, Pos.TOP_CENTER);
            StackPane.setMargin(spent, new Insets(2, 0, 0, 0));
            getChildren().add(spent);
        }
    }

    public MinionCard getMinion() {
        return minion;
    }

    public void markAttacker() {
        if (!getStyleClass().contains("minion-attacker")) {
            getStyleClass().add("minion-attacker");
        }
    }

    public void markTarget() {
        if (!getStyleClass().contains("minion-target")) {
            getStyleClass().add("minion-target");
        }
    }

    private StackPane badge(String value, String styleClass, Pos pos, double insetX, double insetY) {
        Circle circle = new Circle(12);
        circle.getStyleClass().add(styleClass);
        Label label = new Label(value);
        label.getStyleClass().add("badge-value");
        StackPane badge = new StackPane(circle, label);
        badge.setMouseTransparent(true);
        StackPane.setAlignment(badge, pos);
        boolean left = pos == Pos.BOTTOM_LEFT;
        StackPane.setMargin(badge, left
                ? new Insets(0, 0, insetY, insetX)
                : new Insets(0, insetX, insetY, 0));
        return badge;
    }

    private static String glyphOf(MinionCard m) {
        return switch (Math.min(4, m.getAttack() / 2)) {
            case 0 -> "兵";
            case 1 -> "刃";
            case 2 -> "獸";
            case 3 -> "龍";
            default -> "神";
        };
    }

    private static String shortName(String name) {
        return name.length() <= 5 ? name : name.substring(0, 4) + "…";
    }
}
