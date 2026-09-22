package org.example.card.ui.fx;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/**
 * 程序化音效引擎：用代码合成波形，不依赖任何音频文件。
 *
 * 优点：零资源文件、零新增依赖（javax.sound.sampled 属 java.desktop）、无版权问题、体积极小。
 * 每个音效预生成若干 Clip 实例做成池，避免并发播放时互相打断。
 */
public final class SoundEngine {

    private static final int SAMPLE_RATE = 44100;
    /** 每种音效的并发实例数。 */
    private static final int POOL_SIZE = 4;
    /** 主音量 0..1。 */
    private static final float MASTER_VOLUME = 0.35f;

    /**
     * 音效池：预热线程写、界面线程读，所以用同步包装的 Map，
     * 避免合成过程中界面线程读到半成品（EnumMap 并发读写会出问题）。
     */
    private static final Map<Sfx, Deque<Clip>> POOL =
            Collections.synchronizedMap(new EnumMap<>(Sfx.class));
    private static boolean enabled = true;

    private SoundEngine() {
    }

    /** 初始化：在后台线程预热全部音效（不阻塞界面）。失败时静默降级。 */
    public static void init() {
        Thread warmer = new Thread(() -> {
            for (Sfx sfx : Sfx.values()) {
                try {
                    ensurePool(sfx);
                } catch (Exception ex) {
                    System.err.println("[音效] " + sfx + " 合成失败：" + ex);
                }
            }
        }, "sound-warmer");
        warmer.setDaemon(true);
        warmer.start();
    }

    /** 首次使用某个音效时才合成（懒加载，避免启动卡顿）。 */
    private static void ensurePool(Sfx sfx) {
        if (POOL.containsKey(sfx)) {
            return;
        }
        try {
            byte[] pcm = synth(sfx);
            Deque<Clip> clips = new ArrayDeque<>();
            for (int i = 0; i < POOL_SIZE; i++) {
                clips.add(open(pcm));
            }
            POOL.put(sfx, clips);
        } catch (Exception ex) {
            // 单个音效失败不影响其他音效
        }
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** 播放音效（非阻塞）。若该音效尚未合成，立即合成后播放。 */
    public static void play(Sfx sfx) {
        if (!enabled) {
            return;
        }
        Deque<Clip> clips = POOL.get(sfx);
        if (clips == null) {
            ensurePool(sfx);
            clips = POOL.get(sfx);
        }
        if (clips == null || clips.isEmpty()) {
            return;
        }
        final Deque<Clip> pool = clips;
        Clip clip;
        synchronized (pool) {
            clip = pool.poll();
        }
        if (clip == null) {
            return;
        }
        try {
            clip.setFramePosition(0);
            clip.start();
        } catch (Exception ignored) {
            // 播放失败不影响游戏
        }
        synchronized (pool) {
            pool.offer(clip);
        }
    }

    // ============ 合成 ============

    private static Clip open(byte[] pcm) throws Exception {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(pcm), format, pcm.length / 2);
        Clip clip = AudioSystem.getClip();
        clip.open(stream);
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            gain.setValue(20f * (float) Math.log10(Math.max(0.0001f, MASTER_VOLUME)));
        } catch (Exception ignored) {
            // 某些平台没有音量控制，忽略
        }
        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
                clip.setFramePosition(0);
            }
        });
        return clip;
    }

    /** 按音效类型合成 16-bit 单声道 PCM。 */
    private static byte[] synth(Sfx sfx) {
        return switch (sfx) {
            case DRAW -> sweep(0.10, 900, 1500, 0.35, Wave.SINE);
            case SUMMON -> sweep(0.22, 320, 880, 0.5, Wave.SINE);
            case SPELL -> magic(0.34);
            case PET -> bell(0.40, 1046.5);
            case ATTACK -> impact(0.20, 160);
            case DAMAGE -> sweep(0.16, 700, 240, 0.55, Wave.SQUARE_LITE);
            case DEATH -> sweep(0.42, 320, 90, 0.5, Wave.SINE);
            case TURN -> bell(0.30, 784.0);
            case WIN -> arpeggio(new double[]{523.25, 659.25, 783.99, 1046.5}, 0.13);
            case LOSE -> arpeggio(new double[]{523.25, 415.30, 329.63, 261.63}, 0.17);
        };
    }

    private enum Wave { SINE, SQUARE_LITE }

    /** 频率扫描音：从 f0 线性滑到 f1。 */
    private static byte[] sweep(double seconds, double f0, double f1,
                                double decay, Wave wave) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] buf = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            double freq = f0 + (f1 - f0) * t;
            phase += 2 * Math.PI * freq / SAMPLE_RATE;
            double env = Math.exp(-decay * t * 6.0) * (1 - t * 0.15);
            double s = switch (wave) {
                case SINE -> Math.sin(phase);
                case SQUARE_LITE -> Math.signum(Math.sin(phase)) * 0.45 + Math.sin(phase) * 0.55;
            };
            buf[i] = (short) (s * env * 0.75 * Short.MAX_VALUE);
        }
        return toBytes(buf);
    }

    /** 撞击声：噪声 + 低频冲击，快速衰减。 */
    private static byte[] impact(double seconds, double lowFreq) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] buf = new short[n];
        java.util.Random rnd = new java.util.Random(1234);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            phase += 2 * Math.PI * lowFreq / SAMPLE_RATE;
            double noise = (rnd.nextDouble() * 2 - 1);
            double env = Math.exp(-9.0 * t);
            double s = (Math.sin(phase) * 0.55 + noise * 0.45) * env;
            buf[i] = (short) (s * 0.85 * Short.MAX_VALUE);
        }
        return toBytes(buf);
    }

    /** 魔法音：两个正弦叠加 + 轻微颤音。 */
    private static byte[] magic(double seconds) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] buf = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            double base = 660 + 260 * Math.sin(2 * Math.PI * 6 * t);
            double env = Math.sin(Math.PI * Math.min(1, t * 1.4)) * Math.exp(-1.6 * t);
            double s = Math.sin(2 * Math.PI * base * t / 1.0 * 1.0 + 0)
                    + 0.5 * Math.sin(2 * Math.PI * base * 2 * t);
            buf[i] = (short) (s / 1.5 * env * 0.7 * Short.MAX_VALUE);
        }
        return toBytes(buf);
    }

    /** 铃声：基频 + 泛音，长衰减。 */
    private static byte[] bell(double seconds, double freq) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] buf = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) seconds;
            double env = Math.exp(-4.2 * t);
            double s = Math.sin(2 * Math.PI * freq * i / SAMPLE_RATE)
                    + 0.42 * Math.sin(2 * Math.PI * freq * 2.01 * i / SAMPLE_RATE)
                    + 0.18 * Math.sin(2 * Math.PI * freq * 3.02 * i / SAMPLE_RATE);
            buf[i] = (short) (s / 1.6 * env * 0.7 * Short.MAX_VALUE);
        }
        return toBytes(buf);
    }

    /** 琶音：依次播放若干音高。 */
    private static byte[] arpeggio(double[] freqs, double noteSeconds) {
        int noteN = (int) (noteSeconds * SAMPLE_RATE);
        short[] buf = new short[noteN * freqs.length];
        for (int k = 0; k < freqs.length; k++) {
            for (int i = 0; i < noteN; i++) {
                double t = i / (double) noteN;
                double env = Math.sin(Math.PI * t) * 0.9;
                double s = Math.sin(2 * Math.PI * freqs[k] * i / SAMPLE_RATE)
                        + 0.3 * Math.sin(2 * Math.PI * freqs[k] * 2 * i / SAMPLE_RATE);
                buf[k * noteN + i] = (short) (s / 1.3 * env * 0.7 * Short.MAX_VALUE);
            }
        }
        return toBytes(buf);
    }

    private static byte[] toBytes(short[] buf) {
        byte[] out = new byte[buf.length * 2];
        for (int i = 0; i < buf.length; i++) {
            out[i * 2] = (byte) (buf[i] & 0xFF);
            out[i * 2 + 1] = (byte) ((buf[i] >> 8) & 0xFF);
        }
        return out;
    }
}
