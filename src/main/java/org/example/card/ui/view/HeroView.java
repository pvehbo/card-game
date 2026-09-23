package org.example.card.ui.view;

import org.example.card.ui.Assets;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.example.card.model.PlayerState;

/**
 * 英雄控件：圆形头像 + 生命数字 + 血条（缓动动画）。
 */
public class HeroView extends VBox {

    private static final double BAR_WIDTH = 132;
    private static final double BAR_HEIGHT = 12;

    private final PlayerState player;
    private final StackPane portrait;
    private final Label lifeLabel;
    private final Rectangle barFill;
    private final Label petLabel;
    private int shownLife;

    public HeroView(PlayerState player, String glyph, boolean own) {
        this.player = player;
        this.shownLife = player.getLifePoints();

        setSpacing(5);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(4));

        // 头像圆
        portrait = new StackPane();
        portrait.getStyleClass().add("hero-frame");
        portrait.setPrefSize(74, 74);
        portrait.setMinSize(74, 74);
        portrait.setMaxSize(74, 74);

        // 有头像图就用图（圆形裁剪），否则用文字符号
        var portraitImage = Assets.hero(own ? "player" : "ai");
        if (portraitImage.isPresent()) {
            ImageView iv = new ImageView(portraitImage.get());
            iv.setFitWidth(66);
            iv.setFitHeight(66);
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.setMouseTransparent(true);
            Circle clip = new Circle(33, 33, 33);
            iv.setClip(clip);
            portrait.getChildren().add(iv);
        } else {
            Label glyphLabel = new Label(glyph);
            glyphLabel.getStyleClass().add("hero-glyph");
            glyphLabel.setMouseTransparent(true);
            portrait.getChildren().add(glyphLabel);
        }

        Label nameLabel = new Label(player.getName());
        nameLabel.getStyleClass().add("title-text");
        nameLabel.setStyle("-fx-font-size: 13px;");

        // 血条
        StackPane bar = new StackPane();
        bar.setPrefSize(BAR_WIDTH, BAR_HEIGHT);
        bar.setMaxSize(BAR_WIDTH, BAR_HEIGHT);
        Rectangle track = new Rectangle(BAR_WIDTH, BAR_HEIGHT);
        track.setArcWidth(10);
        track.setArcHeight(10);
        track.getStyleClass().add("health-bar-track");
        barFill = new Rectangle(BAR_WIDTH - 2, BAR_HEIGHT - 2);
        barFill.setArcWidth(9);
        barFill.setArcHeight(9);
        barFill.getStyleClass().add("health-bar-fill");
        StackPane.setAlignment(barFill, Pos.CENTER_LEFT);
        bar.getChildren().addAll(track, barFill);

        lifeLabel = new Label(player.getLifePoints() + " / " + PlayerState.START_LIFE);
        lifeLabel.getStyleClass().add("subtle-text");
        lifeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e8d5a8;");

        petLabel = new Label();
        petLabel.getStyleClass().add("pet-chip");
        petLabel.setVisible(false);
        petLabel.setManaged(false);

        getChildren().addAll(nameLabel, portrait, bar, lifeLabel, petLabel);
        refresh();
    }

    public PlayerState getPlayer() {
        return player;
    }

    public StackPane getPortrait() {
        return portrait;
    }

    /** 刷新生命值（带缓动动画）+ 宠物光环标签。 */
    public void refresh() {
        int life = player.getLifePoints();
        double ratio = Math.max(0, Math.min(1.0, life / (double) PlayerState.START_LIFE));

        Timeline anim = new Timeline(new KeyFrame(Duration.millis(320),
                new KeyValue(barFill.widthProperty(), (BAR_WIDTH - 2) * ratio)));
        anim.play();

        barFill.getStyleClass().removeAll("health-bar-fill-hurt", "health-bar-fill-low");
        if (ratio <= 0.3) {
            barFill.getStyleClass().add("health-bar-fill-low");
        } else if (ratio <= 0.6) {
            barFill.getStyleClass().add("health-bar-fill-hurt");
        }

        lifeLabel.setText(life + " / " + PlayerState.START_LIFE);
        shownLife = life;

        if (player.getPets().isEmpty()) {
            petLabel.setVisible(false);
            petLabel.setManaged(false);
        } else {
            int atk = player.getPets().stream()
                    .mapToInt(org.example.card.model.PetCard::getAttackBonus).sum();
            int hp = player.getPets().stream()
                    .mapToInt(org.example.card.model.PetCard::getHealthBonus).sum();
            StringBuilder sb = new StringBuilder("靈 ");
            for (var pet : player.getPets()) {
                sb.append(pet.getName()).append(" ");
            }
            if (atk > 0) {
                sb.append("攻+").append(atk).append(" ");
            }
            if (hp > 0) {
                sb.append("血+").append(hp);
            }
            petLabel.setText(sb.toString().trim());
            petLabel.setVisible(true);
            petLabel.setManaged(true);
        }
    }

    public void markActive(boolean active) {
        getStyleClass().remove("hero-frame-active");
        portrait.getStyleClass().remove("hero-frame-active");
        if (active) {
            portrait.getStyleClass().add("hero-frame-active");
        }
    }

    public void markTarget(boolean target) {
        portrait.getStyleClass().remove("hero-frame-target");
        if (target) {
            portrait.getStyleClass().add("hero-frame-target");
        }
    }
}
