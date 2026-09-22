package org.example.card.ui.fx;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Canvas 粒子层：60fps 自绘的火花/尘埃/魔法粒子。
 * 覆盖在界面上方，鼠标穿透，不影响交互。
 */
public class ParticleLayer extends Canvas {

    private static final Random RND = new Random();
    private static final int MAX_PARTICLES = 400;

    private final List<Particle> particles = new ArrayList<>();
    private AnimationTimer timer;
    private long lastNanos;

    private static final class Particle {
        double x;
        double y;
        double vx;
        double vy;
        double life;
        double maxLife;
        double size;
        Color color;
        double gravity;
        double drag;
    }

    public ParticleLayer() {
        setMouseTransparent(true);
        setOpacity(0.95);
        // 尺寸跟随父容器
        widthProperty().addListener((o, a, b) -> redraw());
        heightProperty().addListener((o, a, b) -> redraw());
    }

    public void start() {
        if (timer != null) {
            return;
        }
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dt = lastNanos == 0 ? 0.016 : Math.min(0.05, (now - lastNanos) / 1e9);
                lastNanos = now;
                update(dt);
                redraw();
            }
        };
        timer.start();
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    // ============ 特效发射器 ============

    /** 撞击火花：向四周迸射。 */
    public void burst(double x, double y, Color base, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            double angle = RND.nextDouble() * Math.PI * 2;
            double speed = 90 + RND.nextDouble() * 260;
            p.x = x;
            p.y = y;
            p.vx = Math.cos(angle) * speed;
            p.vy = Math.sin(angle) * speed - 40;
            p.maxLife = 0.35 + RND.nextDouble() * 0.45;
            p.life = p.maxLife;
            p.size = 1.6 + RND.nextDouble() * 3.2;
            p.color = vary(base);
            p.gravity = 420;
            p.drag = 1.6;
            add(p);
        }
    }

    /** 上升烟雾/消散：用于随从阵亡。 */
    public void dissipate(double x, double y, Color base, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.x = x + (RND.nextDouble() - 0.5) * 34;
            p.y = y + (RND.nextDouble() - 0.5) * 20;
            p.vx = (RND.nextDouble() - 0.5) * 40;
            p.vy = -50 - RND.nextDouble() * 70;
            p.maxLife = 0.7 + RND.nextDouble() * 0.7;
            p.life = p.maxLife;
            p.size = 3 + RND.nextDouble() * 6;
            p.color = vary(base);
            p.gravity = -30;
            p.drag = 0.9;
            add(p);
        }
    }

    /** 拖尾：单颗粒沿路径飘散（用于法术飞行）。 */
    public void trail(double x, double y, Color base, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.x = x + (RND.nextDouble() - 0.5) * 12;
            p.y = y + (RND.nextDouble() - 0.5) * 12;
            p.vx = (RND.nextDouble() - 0.5) * 70;
            p.vy = (RND.nextDouble() - 0.5) * 70;
            p.maxLife = 0.25 + RND.nextDouble() * 0.3;
            p.life = p.maxLife;
            p.size = 2 + RND.nextDouble() * 3;
            p.color = vary(base);
            p.gravity = 20;
            p.drag = 2.4;
            add(p);
        }
    }

    /** 从画面底部缓缓升起的尘埃（营造氛围）。 */
    public void ambientDust(int count) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.x = RND.nextDouble() * getWidth();
            p.y = getHeight() + RND.nextDouble() * 40;
            p.vx = (RND.nextDouble() - 0.5) * 16;
            p.vy = -14 - RND.nextDouble() * 26;
            p.maxLife = 5 + RND.nextDouble() * 5;
            p.life = p.maxLife;
            p.size = 1 + RND.nextDouble() * 2.2;
            p.color = Color.web("#c9b6ff", 0.35);
            p.gravity = -2;
            p.drag = 0.05;
            add(p);
        }
    }

    private void add(Particle p) {
        if (particles.size() >= MAX_PARTICLES) {
            particles.remove(0);
        }
        particles.add(p);
    }

    private Color vary(Color base) {
        double f = 0.75 + RND.nextDouble() * 0.5;
        return Color.color(
                Math.min(1, base.getRed() * f),
                Math.min(1, base.getGreen() * f),
                Math.min(1, base.getBlue() * f),
                1);
    }

    // ============ 更新与绘制 ============

    private void update(double dt) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0) {
                it.remove();
                continue;
            }
            double damping = Math.max(0, 1 - p.drag * dt);
            p.vx *= damping;
            p.vy = p.vy * damping + p.gravity * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
        }
    }

    private void redraw() {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        g.clearRect(0, 0, w, h);
        if (particles.isEmpty()) {
            return;
        }
        g.setGlobalBlendMode(BlendMode.SRC_OVER);
        for (Particle p : particles) {
            double t = Math.max(0, p.life / p.maxLife);
            double alpha = Math.min(1, t * 1.4);
            double radius = p.size * (0.4 + t * 0.8);
            g.setGlobalAlpha(alpha);
            g.setFill(p.color);
            // 外层柔光
            g.setGlobalAlpha(alpha * 0.28);
            g.fillOval(p.x - radius * 2.1, p.y - radius * 2.1, radius * 4.2, radius * 4.2);
            // 核心亮点
            g.setGlobalAlpha(alpha);
            g.fillOval(p.x - radius, p.y - radius, radius * 2, radius * 2);
        }
        g.setGlobalAlpha(1);
    }
}
