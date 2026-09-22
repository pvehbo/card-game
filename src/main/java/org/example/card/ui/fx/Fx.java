package org.example.card.ui.fx;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PathTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.effect.Glow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.Random;

/**
 * 界面动效工具箱：抽牌、出牌飞入、卡牌翻面、攻击突刺、屏幕震动、受击闪光。
 * 全部基于 JavaFX 动画 API，不依赖额外资源。
 */
public final class Fx {

    private static final Random RND = new Random();
    /** 存动画引用用的键，避免同一节点上叠加多个同类动画。 */
    private static final String SHAKE_KEY = "fx.shake";
    private static final String BREATHE_KEY = "fx.breathe";

    private Fx() {
    }

    /** 出牌飞入：把节点从起点沿弧线飞到终点，并淡出（用于"卡牌飞向战场"）。 */
    public static void flyCard(Node overlayCard, double fromX, double fromY,
                               double toX, double toY, Runnable onFinished) {
        double midX = (fromX + toX) / 2;
        double midY = Math.min(fromY, toY) - 90;

        Path path = new Path();
        path.getElements().add(new MoveTo(fromX, fromY));
        path.getElements().add(new javafx.scene.shape.QuadCurveTo(midX, midY, toX, toY));

        PathTransition move = new PathTransition(Duration.millis(430), path, overlayCard);
        move.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition grow = new ScaleTransition(Duration.millis(430), overlayCard);
        grow.setFromX(0.85);
        grow.setFromY(0.85);
        grow.setToX(1.1);
        grow.setToY(1.1);

        RotateTransition spin = new RotateTransition(Duration.millis(430), overlayCard);
        spin.setFromAngle(-8);
        spin.setToAngle(4);

        FadeTransition fade = new FadeTransition(Duration.millis(200), overlayCard);
        fade.setDelay(Duration.millis(240));
        fade.setFromValue(1);
        fade.setToValue(0);

        ParallelTransition all = new ParallelTransition(move, grow, spin, fade);
        all.setOnFinished(e -> {
            if (onFinished != null) {
                onFinished.run();
            }
        });
        all.play();
    }

    /** 卡牌翻面（伪 3D）：绕 Y 轴翻转，视觉上"抽出一张牌"。 */
    public static void flipIn(Node node) {
        if (node == null) {
            return;
        }
        RotateTransition rt = new RotateTransition(Duration.millis(420), node);
        rt.setAxis(Rotate.Y_AXIS);
        rt.setFromAngle(-180);
        rt.setToAngle(0);
        rt.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition st = new ScaleTransition(Duration.millis(420), node);
        st.setFromX(0.2);
        st.setFromY(1.06);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(rt, st).play();
    }

    /** 抽牌：从牌堆方向滑入 + 淡入。 */
    public static void drawIn(Node node) {
        node.setOpacity(0);
        node.setTranslateX(-46);
        node.setTranslateY(-26);
        node.setScaleX(0.9);
        node.setScaleY(0.9);

        TranslateTransition move = new TranslateTransition(Duration.millis(320), node);
        move.setToX(0);
        move.setToY(0);
        move.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition fade = new FadeTransition(Duration.millis(280), node);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(320), node);
        scale.setToX(1);
        scale.setToY(1);
        new ParallelTransition(move, fade, scale).play();
    }

    /**
     * 攻击突刺：节点朝目标方向冲一下再回位。
     *
     * @param atImpact   前冲到位的那一刻触发（撞击火花/抖屏）
     * @param onComplete 整套动画结束（冲过去 + 退回来）后触发，用于结算伤害
     */
    public static void lunge(Node attacker, Node target, Runnable atImpact, Runnable onComplete) {
        if (attacker == null) {
            if (atImpact != null) {
                atImpact.run();
            }
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        double dx = 0;
        double dy = 0;
        if (target != null && attacker.getScene() != null && target.getScene() != null) {
            Bounds a = attacker.localToScene(attacker.getBoundsInLocal());
            Bounds t = target.localToScene(target.getBoundsInLocal());
            dx = (t.getMinX() + t.getWidth() / 2) - (a.getMinX() + a.getWidth() / 2);
            dy = (t.getMinY() + t.getHeight() / 2) - (a.getMinY() + a.getHeight() / 2);
            // 只冲过去约 45%，保持队形感
            dx *= 0.45;
            dy *= 0.45;
        }

        TranslateTransition forward = new TranslateTransition(Duration.millis(110), attacker);
        forward.setByX(dx);
        forward.setByY(dy);
        forward.setInterpolator(Interpolator.EASE_IN);

        TranslateTransition back = new TranslateTransition(Duration.millis(220), attacker);
        back.setToX(0);
        back.setToY(0);
        back.setInterpolator(Interpolator.EASE_OUT);

        // 撞击反馈卡在前冲结束的瞬间，而不是整套动画结束时
        forward.setOnFinished(e -> {
            if (atImpact != null) {
                atImpact.run();
            }
        });

        SequentialTransition seq = new SequentialTransition(forward, back);
        seq.setOnFinished(e -> {
            if (onComplete != null) {
                onComplete.run();
            }
        });
        seq.play();
    }

    /** 受击闪光：短暂叠加发光 + 轻微缩放下沉。 */
    public static void hitFlash(Node node) {
        if (node == null) {
            return;
        }
        Glow glow = new Glow(0.9);
        node.setEffect(glow);
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(glow.levelProperty(), 0.95)),
                new KeyFrame(Duration.millis(130),
                        new KeyValue(glow.levelProperty(), 0.15)),
                new KeyFrame(Duration.millis(300),
                        new KeyValue(glow.levelProperty(), 0.0)));
        tl.setOnFinished(e -> node.setEffect(null));
        tl.play();

        ScaleTransition punch = new ScaleTransition(Duration.millis(120), node);
        punch.setFromX(1.0);
        punch.setFromY(1.0);
        punch.setToX(0.9);
        punch.setToY(0.9);
        punch.setAutoReverse(true);
        punch.setCycleCount(2);
        punch.play();
    }

    /** 屏幕震动：对指定根容器做小幅随机抖动（重复调用会打断上一次，避免动画叠加）。 */
    public static void shake(Region root, double intensity) {
        if (root == null) {
            return;
        }
        Object running = root.getProperties().remove(SHAKE_KEY);
        if (running instanceof Timeline previous) {
            previous.stop();
        }
        Timeline tl = new Timeline();
        int steps = 7;
        for (int i = 0; i <= steps; i++) {
            double decay = 1.0 - (i / (double) steps);
            double ox = (RND.nextDouble() * 2 - 1) * intensity * decay;
            double oy = (RND.nextDouble() * 2 - 1) * intensity * decay;
            tl.getKeyFrames().add(new KeyFrame(
                    Duration.millis(i * 28),
                    new KeyValue(root.translateXProperty(), ox, Interpolator.LINEAR),
                    new KeyValue(root.translateYProperty(), oy, Interpolator.LINEAR)));
        }
        tl.getKeyFrames().add(new KeyFrame(Duration.millis(steps * 28 + 40),
                new KeyValue(root.translateXProperty(), 0, Interpolator.EASE_OUT),
                new KeyValue(root.translateYProperty(), 0, Interpolator.EASE_OUT)));
        tl.setOnFinished(e -> {
            root.setTranslateX(0);
            root.setTranslateY(0);
            root.getProperties().remove(SHAKE_KEY);
        });
        root.getProperties().put(SHAKE_KEY, tl);
        tl.play();
    }

    /**
     * 呼吸光晕：让某个节点持续缓慢放大缩小（用于"当前回合"提示）。
     * 传入 false 会停掉动画并复位，同一节点重复调用不会叠加动画。
     */
    public static void breathe(Node node, boolean on) {
        if (node == null) {
            return;
        }
        Object running = node.getProperties().get(BREATHE_KEY);
        if (!on) {
            if (running instanceof ScaleTransition st) {
                st.stop();
                node.getProperties().remove(BREATHE_KEY);
                node.setScaleX(1);
                node.setScaleY(1);
            }
            return;
        }
        if (running instanceof ScaleTransition st && st.getStatus() == Animation.Status.RUNNING) {
            return;
        }
        ScaleTransition breath = new ScaleTransition(Duration.millis(1400), node);
        breath.setFromX(1.0);
        breath.setFromY(1.0);
        breath.setToX(1.055);
        breath.setToY(1.055);
        breath.setAutoReverse(true);
        breath.setCycleCount(Animation.INDEFINITE);
        breath.setInterpolator(Interpolator.EASE_BOTH);
        node.getProperties().put(BREATHE_KEY, breath);
        breath.play();
    }

    /** 把节点临时挂到一个覆盖层上（用于飞行动画），动画结束后自动移除。 */
    public static void mountOn(Pane overlay, Node node) {
        overlay.getChildren().add(node);
        overlay.setMouseTransparent(true);
        overlay.setPickOnBounds(false);
    }
}
