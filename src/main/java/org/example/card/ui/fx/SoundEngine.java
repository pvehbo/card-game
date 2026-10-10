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

import org.example.card.service.GameLog;

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
    /**
     * 池锁：不要在局部变量（池引用）上同步——引用是局部的、对象是共享的，
     * 换引用后锁就散了；统一用这把静态锁。
     */
    private static final Object POOL_LOCK = new Object();
    private static boolean enabled = true;
    /** 池已关闭（退出期）：关闭后不再发布新合成的池，否则退出瞬间合成的那批 Clip 永远没人关。 */
    private static volatile boolean closed;
    /** 全部音效合成失败（通常是没有可用音频设备）：把「音效开着却没声」变成可诊断的状态。 */
    private static volatile boolean degraded;

    private SoundEngine() {
    }

    /** 初始化：在后台线程预热全部音效（不阻塞界面）。失败时静默降级，但会记日志 + 置降级标志。 */
    public static void init() {
        init(null);
    }

    /**
     * 带回调的初始化：预热全部结束后回调一次（**在预热线程上**）。
     * 界面可据此把「音效不可用」这类降级状态显示出来（自己记得切回 UI 线程）。
     */
    public static void init(Runnable onWarmupFinished) {
        closed = false;
        degraded = false;
        Thread warmer = new Thread(() -> {
            int failed = 0;
            for (Sfx sfx : Sfx.values()) {
                if (!ensurePool(sfx)) {
                    failed++;
                }
            }
            if (failed == Sfx.values().length) {
                degraded = true;
                GameLog.warn("音效合成全部失败（通常是没有可用音频设备），已静默降级；游戏与界面不受影响。");
            }
            if (onWarmupFinished != null) {
                onWarmupFinished.run();
            }
        }, "sound-warmer");
        warmer.setDaemon(true);
        warmer.start();
    }

    /** 是否已静默降级（无可用音频设备）。界面可据此把「音效开」的假象说清楚。 */
    public static boolean isDegraded() {
        return degraded;
    }

    /**
     * 由 {@link #init()} 的后台预热线程为每个音效合成一次；{@link #play(Sfx)} 不在调用线程合成
     * （池未就绪时直接跳过），故合成绝不会阻塞界面线程。
     *
     * <p>合成刻意放在锁**外**：{@code open} 会做可能长时间阻塞的音频 I/O，若把它放进锁内，
     * 无音频线路的环境里合成线程会一直占着 {@code POOL_LOCK}，导致退出时调用的
     * {@link #close()} 死等该锁、进程无法退出（已用 jstack 复现）。
     * 并发合成用「锁内 {@code putIfAbsent} + 关掉多余的那份」兜底：既不会互相覆盖，也不泄漏 Clip。
     */
    private static boolean ensurePool(Sfx sfx) {
        synchronized (POOL_LOCK) {
            if (POOL.containsKey(sfx)) {
                return true;
            }
            if (closed) {
                return false;
            }
        }
        Deque<Clip> clips = new ArrayDeque<>();
        try {
            byte[] pcm = synth(sfx);
            for (int i = 0; i < POOL_SIZE; i++) {
                clips.add(open(pcm));
            }
        } catch (Exception ex) {
            // 单个音效失败不影响其他音效
            return false;
        }
        synchronized (POOL_LOCK) {
            if (closed) {
                // 合成期间池已被关闭（窗口退出）：这份不能再发布，否则这批 Clip 永远没人关
                closeClips(clips);
                return false;
            }
            if (POOL.putIfAbsent(sfx, clips) != null) {
                // 别的线程抢先放好了：关掉我们这份，避免孤立 Clip
                closeClips(clips);
            }
        }
        return true;
    }

    /** 关闭一组 Clip（各自吞异常，单个失败不影响其它）。 */
    private static void closeClips(Deque<Clip> clips) {
        for (Clip clip : clips) {
            try {
                clip.stop();
                clip.close();
            } catch (Exception ignored) {
                // 单个 Clip 关闭失败不影响其它
            }
        }
    }

    /** 释放全部 Clip 并标记池已关闭（幂等）。窗口退出或测试收尾调用。 */
    public static void close() {
        synchronized (POOL_LOCK) {
            closed = true;
            for (Deque<Clip> clips : POOL.values()) {
                closeClips(clips);
            }
            POOL.clear();
        }
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 播放音效（真正的非阻塞）。池尚未预热好（后台合成中/合成失败）时直接跳过，
     * **绝不**在调用线程上做音频合成——调用方往往是 JavaFX 线程，无音频线路时
     * {@code Clip.open} 会阻塞，界面会整体卡死（已用 jstack 复现）。
     * 合成只由 {@link #init()} 的后台预热线程负责。
     */
    public static void play(Sfx sfx) {
        if (!enabled) {
            return;
        }
        Deque<Clip> clips = POOL.get(sfx);
        if (clips == null || clips.isEmpty()) {
            return;
        }
        Clip clip;
        synchronized (POOL_LOCK) {
            clip = clips.poll();
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
        synchronized (POOL_LOCK) {
            clips.offer(clip);
        }
    }

    // ============ 合成 ============

    private static Clip open(byte[] pcm) throws Exception {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        // try-with-resources：clip.open(stream) 会把数据读入内存，之后 stream 可安全关闭
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(pcm), format, pcm.length / 2)) {
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
            double s = Math.sin(2 * Math.PI * base * t)
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
            double t = i / seconds;
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
