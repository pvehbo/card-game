package org.example.card.ui.view;

import org.example.card.ui.Assets;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.SpellCard;

/**
 * 手牌卡牌控件（暗色奇幻风）。
 * 结构：费用圆 + 名称 + 卡图区 + 描述 + 攻/血徽章。
 */
public class CardView extends StackPane {

    public static final double WIDTH = 118;
    public static final double HEIGHT = 158;

    private final Card card;
    private Runnable onPlay;

    public CardView(Card card) {
        this.card = card;
        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        getStyleClass().add("card");

        VBox body = new VBox(4);
        body.setAlignment(Pos.TOP_CENTER);
        body.setPadding(new Insets(8, 8, 8, 8));
        body.setMouseTransparent(true);

        // 名称
        Label name = new Label(card.getName());
        name.getStyleClass().add("card-name");
        name.setWrapText(true);
        name.setTextAlignment(TextAlignment.CENTER);
        name.setAlignment(Pos.CENTER);
        name.setMaxWidth(WIDTH - 16);

        // 卡图区：有图片资源就用图片，没有则回退到渐变 + 类型符号
        StackPane art = new StackPane();
        art.getStyleClass().add("card-art");
        art.setPrefSize(94, 62);
        art.setMinHeight(62);
        art.getStyleClass().add(artStyleClass(card));
        // 圆角裁剪，让图片/渐变都收在圆角内
        Rectangle artClip = new Rectangle(94, 62);
        artClip.setArcWidth(12);
        artClip.setArcHeight(12);
        art.setClip(artClip);

        var artImage = Assets.cardArt(card);
        if (artImage.isPresent()) {
            ImageView iv = new ImageView(artImage.get());
            iv.setFitWidth(94);
            iv.setFitHeight(62);
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            art.getChildren().add(iv);
        } else {
            Label glyph = new Label(glyphOf(card));
            glyph.getStyleClass().add("art-glyph");
            art.getChildren().add(glyph);
        }

        // 卡框叠加层（可选）：提供 images/cards/frame.png 就会自动盖在卡图上
        Assets.cardFrame().ifPresent(frame -> {
            ImageView fv = new ImageView(frame);
            fv.setFitWidth(WIDTH);
            fv.setFitHeight(HEIGHT);
            fv.setPreserveRatio(false);
            fv.setMouseTransparent(true);
            getChildren().add(fv);
        });

        // 描述
        Label desc = new Label(descriptionOf(card));
        desc.getStyleClass().add("card-desc");
        desc.setWrapText(true);
        desc.setTextAlignment(TextAlignment.CENTER);
        desc.setAlignment(Pos.CENTER);
        desc.setMaxWidth(WIDTH - 16);

        body.getChildren().addAll(name, art, desc);
        getChildren().add(body);

        // 左下角攻/血徽章（随从）
        if (card instanceof MinionCard minion) {
            getChildren().addAll(
                    badge(String.valueOf(minion.getAttack()), "badge-attack", -1, -1),
                    badge(String.valueOf(minion.getMaxHealth()), "badge-health", 1, -1));
        }

        // 左上角费用圆（v1 无费用，用类型色圆点表示卡牌类别）
        Circle costCircle = new Circle(12);
        costCircle.getStyleClass().add("cost-circle");
        costCircle.setMouseTransparent(true);
        Label costText = new Label(typeShort(card));
        costText.getStyleClass().add("cost-text");
        costText.setMouseTransparent(true);
        StackPane cost = new StackPane(costCircle, costText);
        cost.setMouseTransparent(true);
        StackPane.setAlignment(cost, Pos.TOP_LEFT);
        StackPane.setMargin(cost, new Insets(-6, 0, 0, -6));
        getChildren().add(cost);

        setOnMouseClicked(e -> {
            if (onPlay != null) {
                onPlay.run();
            }
        });
    }

    public Card getCard() {
        return card;
    }

    public void setOnPlay(Runnable onPlay) {
        this.onPlay = onPlay;
    }

    public void setPlayable(boolean playable) {
        if (playable) {
            getStyleClass().remove("card-disabled");
        } else if (!getStyleClass().contains("card-disabled")) {
            getStyleClass().add("card-disabled");
        }
    }

    /** 位置徽章：x/y 为 -1 表示靠左/上，1 表示靠右/下。 */
    private StackPane badge(String value, String styleClass, int x, int y) {
        Circle circle = new Circle(11);
        circle.getStyleClass().add(styleClass);
        Label label = new Label(value);
        label.getStyleClass().add("badge-value");
        StackPane badge = new StackPane(circle, label);
        badge.setMouseTransparent(true);
        StackPane.setAlignment(badge, x < 0 ? Pos.BOTTOM_LEFT : Pos.BOTTOM_RIGHT);
        StackPane.setMargin(badge, new Insets(0, x < 0 ? 0 : -5, -5, x < 0 ? -5 : 0));
        return badge;
    }

    private static String artStyleClass(Card card) {
        if (card instanceof MinionCard) {
            return "art-minion";
        } else if (card instanceof SpellCard) {
            return "art-spell";
        } else if (card instanceof PetCard) {
            return "art-pet";
        }
        return "art-minion";
    }

    private static String glyphOf(Card card) {
        if (card instanceof MinionCard m) {
            // 用攻击力大小粗分“强度感”
            int atk = m.getAttack();
            if (atk >= 6) {
                return "龍";
            } else if (atk >= 4) {
                return "獸";
            } else if (atk >= 3) {
                return "刃";
            }
            return "兵";
        } else if (card instanceof SpellCard s) {
            return switch (s.getKind()) {
                case DAMAGE -> "火";
                case HEAL -> "癒";
                case DRAW -> "卷";
            };
        } else if (card instanceof PetCard) {
            return "靈";
        }
        return "?";
    }

    private static String typeShort(Card card) {
        if (card instanceof MinionCard) {
            return "兵";
        } else if (card instanceof SpellCard) {
            return "術";
        } else if (card instanceof PetCard) {
            return "靈";
        }
        return "?";
    }

    private static String descriptionOf(Card card) {
        if (card instanceof MinionCard) {
            return "随从";
        } else if (card instanceof SpellCard s) {
            return switch (s.getKind()) {
                case DAMAGE -> "造成 " + s.getAmount() + " 点伤害";
                case HEAL -> "回复 " + s.getAmount() + " 点生命";
                case DRAW -> "抽 " + s.getAmount() + " 张牌";
            };
        } else if (card instanceof PetCard p) {
            StringBuilder sb = new StringBuilder("光环 ");
            if (p.getAttackBonus() > 0) {
                sb.append("攻+").append(p.getAttackBonus()).append(" ");
            }
            if (p.getHealthBonus() > 0) {
                sb.append("血+").append(p.getHealthBonus());
            }
            return sb.toString().trim();
        }
        return "";
    }
}
