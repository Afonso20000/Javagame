import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * NEON OVERDRIVE - a 2D arcade arena shooter.
 *
 * Pick a ship, survive waves of enemies, choose upgrades, beat three different
 * bosses, and spend the coins you earn on permanent upgrades between runs.
 * A new daily run, achievements and lifetime records give you reasons to come back.
 *
 * Compile:  javac NeonOverdrive.java
 * Run:      java NeonOverdrive
 *
 * Controls: WASD / arrows move, mouse aims, hold left click to shoot (F = auto-fire),
 *           SPACE dash, E or right click special power, Q bomb,
 *           P pause, M sound, N music, F11 fullscreen.
 *           In the hangar: G daily run, T records and achievements.
 */
public class NeonOverdrive {

    static final int W = 1000, H = 700;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("Neon Overdrive");
                final GamePanel panel = new GamePanel();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        panel.saveOnExit();
                    }
                });
                frame.add(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                panel.requestFocusInWindow();
                panel.start();
            }
        });
    }

    enum State { MENU, HANGAR, RECORDS, SKINS, PLAYING, UPGRADE, PAUSED, GAME_OVER }

    enum Special { OVERDRIVE, NOVA, TIME_WARP, FORTRESS }

    enum Ship {
        STRIKER("Striker", "All-rounder", "OVERDRIVE: triple fire rate", 0, 5, 265, 0.20, 1.0, 1, 0.12, 4.5, 1.1, 1.5,
                Special.OVERDRIVE, new Color(90, 240, 255), new double[][] {{20, 0}, {-13, 13}, {-6, 0}, {-13, -13}}),
        BLASTER("Blaster", "Short-range shotgun", "NOVA: ring of bullets + knockback", 0, 6, 240, 0.42, 1.0, 4, 0.17, 4.5, 0.55, 1.5,
                Special.NOVA, new Color(255, 170, 70), new double[][] {{16, -11}, {16, 11}, {-14, 15}, {-8, 0}, {-14, -15}}),
        PHANTOM("Phantom", "Very fast, but fragile", "TIME WARP: slows all enemies", 10, 3, 325, 0.16, 0.9, 1, 0.12, 3.5, 1.1, 0.8,
                Special.TIME_WARP, new Color(200, 130, 255), new double[][] {{24, 0}, {-12, 9}, {-4, 0}, {-12, -9}}),
        TITAN("Titan", "Slow tank, explosive cannon", "FORTRESS: invincible, burns nearby foes", 20, 8, 210, 0.55, 3.2, 1, 0.12, 8, 1.2, 2.0,
                Special.FORTRESS, new Color(120, 255, 150), new double[][] {{18, -8}, {18, 8}, {4, 18}, {-16, 14}, {-16, -14}, {4, -18}});

        final String title, role, specialText;
        final int cost, health, shots;
        final double speed, fireDelay, damage, spread, bulletRadius, bulletLife, dashDelay;
        final Special special;
        final Color color;
        final double[][] outline;

        Ship(String title, String role, String specialText, int cost, int health, double speed, double fireDelay,
             double damage, int shots, double spread, double bulletRadius, double bulletLife, double dashDelay,
             Special special, Color color, double[][] outline) {
            this.title = title;
            this.role = role;
            this.specialText = specialText;
            this.cost = cost;
            this.health = health;
            this.speed = speed;
            this.fireDelay = fireDelay;
            this.damage = damage;
            this.shots = shots;
            this.spread = spread;
            this.bulletRadius = bulletRadius;
            this.bulletLife = bulletLife;
            this.dashDelay = dashDelay;
            this.special = special;
            this.color = color;
            this.outline = outline;
        }

        /** Ships can also be earned by playing, as well as bought. */
        String unlockHint() {
            return this == PHANTOM ? "or reach wave 8" : this == TITAN ? "or beat The Serpent" : "";
        }
    }

    /** A twist on the rules. The daily run picks one for everybody, based on the date. */
    enum Modifier {
        //              title            description                                              count  hp   speed bullet elite  health dmg  bombs
        NONE(           "",              "",                                                      1.0, 1.0,  1.0,  1.0,  0.0,   0,  1.0,  0),
        SWARM(          "Swarm",         "50% more enemies, but they have 30% less health",       1.5, 0.7,  1.0,  1.0,  0.0,   0,  1.0,  0),
        GLASS_ARENA(    "Glass Arena",   "2 less max health, but you deal 60% more damage",       1.0, 1.0,  1.0,  1.0,  0.0,  -2,  1.6,  0),
        ELITE_HUNT(     "Elite Hunt",    "Elites from wave 1, and many more of them",             1.0, 1.0,  1.0,  1.0,  0.2,   0,  1.0,  0),
        BULLET_STORM(   "Bullet Storm",  "Enemy bullets are 35% faster, but you get 3 more bombs", 1.0, 1.0, 1.0,  1.35, 0.0,   0,  1.0,  3),
        OVERCLOCK(      "Overclock",     "Enemies are 25% faster, but you deal 25% more damage",  1.0, 1.0,  1.25, 1.0,  0.0,   0,  1.25, 0),
        BOMB_BONANZA(   "Bomb Bonanza",  "5 more bombs, but enemies have 25% more health",        1.0, 1.25, 1.0,  1.0,  0.0,   0,  1.0,  5);

        final String title, description;
        final double enemyCount, enemyHealth, enemySpeed, bulletSpeed, eliteBonus, damage;
        final int healthChange, bonusBombs;

        Modifier(String title, String description, double enemyCount, double enemyHealth, double enemySpeed,
                 double bulletSpeed, double eliteBonus, int healthChange, double damage, int bonusBombs) {
            this.title = title;
            this.description = description;
            this.enemyCount = enemyCount;
            this.enemyHealth = enemyHealth;
            this.enemySpeed = enemySpeed;
            this.bulletSpeed = bulletSpeed;
            this.eliteBonus = eliteBonus;
            this.healthChange = healthChange;
            this.damage = damage;
            this.bonusBombs = bonusBombs;
        }
    }

    /** One-time goals. Each one pays out coins the moment you reach it. */
    enum Achievement {
        BOSS_SLAYER("Boss Slayer", "Defeat a boss", 25),
        TRIPLE_THREAT("Triple Threat", "Defeat all three bosses", 60),
        FLAWLESS("Flawless", "Defeat a boss without taking a hit in that wave", 60),
        NO_MERCY("No Mercy", "Defeat a boss on Hard", 50),
        DEEP_RUN("Deep Run", "Reach wave 10", 30),
        SURVIVOR("Survivor", "Reach wave 20", 80),
        RAMPAGE("Rampage", "Get a 50 kill combo", 30),
        GODLIKE("Godlike", "Get a 100 kill combo", 60),
        EXTERMINATOR("Exterminator", "Destroy 1000 enemies in total", 50),
        DAILY_PILOT("Daily Pilot", "Finish a daily run", 20),
        REGULAR("Regular", "Play the daily run 3 days in a row", 40),
        FULLY_LOADED("Fully Loaded", "Max out every permanent upgrade", 50),
        FLEET("Fleet Commander", "Own all four ships", 50);

        final String title, description;
        final int reward, diamonds;

        Achievement(String title, String description, int reward) {
            this.title = title;
            this.description = description;
            this.reward = reward;
            // Every achievement also pays diamonds: 1 for every 10 coins of its reward.
            this.diamonds = Math.max(1, reward / 10);
        }
    }

    /** Color skins, bought with diamonds. A skin recolors whichever ship you fly. */
    enum Skin {
        DEFAULT("Factory", "The ship's own color", 0, null),
        CRIMSON("Crimson", "Blood-red neon", 4, new Color(255, 70, 90)),
        GOLD("Gold Rush", "Shiny and expensive-looking", 6, new Color(255, 205, 60)),
        TOXIC("Toxic", "Glows like radioactive waste", 6, new Color(170, 255, 60)),
        ICE("Glacier", "Cold, pale blue", 8, new Color(190, 235, 255)),
        SAKURA("Sakura", "Soft pink petals", 10, new Color(255, 150, 210)),
        VOID("Void", "Deep violet from the rift", 12, new Color(150, 90, 255)),
        RAINBOW("Prism", "Cycles through every color", 25, null);

        final String title, description;
        final int cost;
        final Color color;

        Skin(String title, String description, int cost, Color color) {
            this.title = title;
            this.description = description;
            this.cost = cost;
            this.color = color;
        }
    }

    enum Difficulty {
        EASY("Easy", 0.7, 0.8, 0.88, 2, 0.7, 0.75, new Color(120, 240, 150)),
        NORMAL("Normal", 1.0, 1.0, 1.0, 0, 1.0, 1.0, new Color(255, 225, 120)),
        HARD("Hard", 1.35, 1.25, 1.14, 0, 1.6, 1.5, new Color(255, 100, 110));

        final String title;
        final double enemyHealth, enemyCount, enemySpeed, scoreMultiplier, coinMultiplier;
        final int bonusHealth;
        final Color color;

        Difficulty(String title, double enemyHealth, double enemyCount, double enemySpeed, int bonusHealth,
                   double scoreMultiplier, double coinMultiplier, Color color) {
            this.title = title;
            this.enemyHealth = enemyHealth;
            this.enemyCount = enemyCount;
            this.enemySpeed = enemySpeed;
            this.bonusHealth = bonusHealth;
            this.scoreMultiplier = scoreMultiplier;
            this.coinMultiplier = coinMultiplier;
            this.color = color;
        }
    }

    /** Permanent upgrades bought with coins in the hangar. */
    enum Perk {
        HULL("Reinforced Hull", "+1 starting health", 3, 40),
        ARSENAL("Arsenal", "+1 starting bomb", 2, 50),
        GREED("Coin Magnet", "+25% coins earned", 3, 60),
        REROLL("Lucky Dice", "+1 upgrade reroll per run", 3, 45);

        final String title, description;
        final int max, baseCost;

        Perk(String title, String description, int max, int baseCost) {
            this.title = title;
            this.description = description;
            this.max = max;
            this.baseCost = baseCost;
        }

        int costAt(int level) {
            return baseCost * (level + 1);
        }
    }

    enum EnemyType {
        //        radius   hp   speed score color
        CHASER(   14,    3.0, 105,   10, new Color(255, 80, 100)),
        SPEEDER(  11,    1.5, 270,   15, new Color(255, 215, 70)),
        TANK(     27,   18.0,  60,   50, new Color(180, 100, 255)),
        SPLITTER( 20,    8.0,  85,   25, new Color(90, 235, 130)),
        MINI(     10,    1.5, 150,    5, new Color(150, 255, 170)),
        SHOOTER(  15,    5.0,  90,   30, new Color(70, 215, 255)),
        BOMBER(   18,    4.0,  80,   30, new Color(255, 135, 50)),
        GHOST(    15,    4.5,  95,   35, new Color(210, 190, 255)),
        OVERLORD( 56,  200.0,  70, 1000, new Color(255, 70, 200)),
        SERPENT(  36,  300.0, 165, 1500, new Color(120, 255, 90)),
        SEGMENT(  24,    1.0,   0,   20, new Color(70, 200, 90)),
        WARDEN(   50,  400.0,  45, 2000, new Color(255, 90, 70));

        final int radius, score;
        final double hp, speed;
        final Color color;

        EnemyType(int radius, double hp, double speed, int score, Color color) {
            this.radius = radius;
            this.hp = hp;
            this.speed = speed;
            this.score = score;
            this.color = color;
        }

        boolean isBoss() {
            return this == OVERLORD || this == SERPENT || this == WARDEN;
        }
    }

    enum Rarity {
        COMMON("COMMON", 70, new Color(170, 185, 220)),
        RARE("RARE", 25, new Color(90, 170, 255)),
        LEGENDARY("LEGENDARY", 6, new Color(255, 190, 60));

        final String label;
        final int weight;
        final Color color;

        Rarity(String label, int weight, Color color) {
            this.label = label;
            this.weight = weight;
            this.color = color;
        }
    }

    enum Upgrade {
        RAPID("Rapid Fire", "Shoot 20% faster", 6, Rarity.COMMON),
        POWER("Power Shot", "+35% bullet damage", 6, Rarity.COMMON),
        SWIFT("Swift", "Move 12% faster", 4, Rarity.COMMON),
        VITALITY("Vitality", "+1 max health and heal fully", 5, Rarity.COMMON),
        BOMBS("Bomb Stash", "+1 bomb right now", 5, Rarity.COMMON),
        HEAVY("Heavy Rounds", "Bigger bullets with +15% damage", 3, Rarity.COMMON),
        CRIT("Critical Hits", "+15% chance of triple damage", 4, Rarity.COMMON),
        MULTI("Multi Shot", "+1 bullet in every shot", 4, Rarity.RARE),
        PIERCE("Piercing", "Bullets pass through +1 enemy", 3, Rarity.RARE),
        BOUNCE("Ricochet", "Bullets bounce off walls +1 time", 3, Rarity.RARE),
        EXPLOSIVE("Explosive Rounds", "Bullets explode when they hit", 3, Rarity.RARE),
        ORBITALS("Orbital Guard", "+1 orb circles you, hurts enemies and blocks bullets", 4, Rarity.RARE),
        HOMING("Homing", "Bullets curve toward enemies", 3, Rarity.RARE),
        DASH("Dash Strike", "Dash recharges faster and damages enemies", 3, Rarity.RARE),
        FROST("Frost Rounds", "Hit enemies are slowed", 2, Rarity.RARE),
        DRONE("Combat Drone", "+1 drone that shoots by itself", 3, Rarity.RARE),
        CHAIN("Chain Lightning", "Hits zap nearby enemies with lightning", 3, Rarity.LEGENDARY),
        VAMPIRE("Vampire", "Heal 1 health every 40 kills", 1, Rarity.LEGENDARY),
        SECOND_LIFE("Second Life", "Revive once with 3 health and a huge blast", 1, Rarity.LEGENDARY),
        GLASS("Glass Cannon", "+150% damage, but -2 max health", 1, Rarity.LEGENDARY);

        final String title, description;
        final int max;
        final Rarity rarity;

        Upgrade(String title, String description, int max, Rarity rarity) {
            this.title = title;
            this.description = description;
            this.max = max;
            this.rarity = rarity;
        }
    }

    static final class Enemy {
        final EnemyType type;
        double x, y, vx, vy, r, hp, maxHp, flash, spawnTimer, spawnTotal, attackTimer, angle, contactCooldown, wobble;
        boolean elite, dead;
        double fuse, slowTimer;
        // Ghost.
        double blinkWarn, warpX, warpY;
        // Bosses.
        int bossStep;
        double chargeWarn, chargeTime, spiralTime, aimX, aimY;
        double beamWarn, beamTime, beamAngle, beamSpin;
        int beamCount;
        Enemy owner, previous;
        // Knockback from bullets, and damage waiting to be shown as a number.
        double pushX, pushY, pendingDamage, damageTextTimer;

        Enemy(EnemyType type, double x, double y, double hp) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.hp = hp;
            this.maxHp = hp;
            this.r = type.radius;
        }
    }

    static final class Bullet {
        double x, y, vx, vy, r, damage, life;
        // Where the bullet was last checked for hits, so it cannot skip over anything.
        double sweepX, sweepY;
        int pierce, bounces;
        boolean enemy, dead, drone;
        Color color;
        List<Enemy> alreadyHit;
    }

    static final class Drone {
        double x, y, cooldown, facing;
    }

    static final class Particle {
        double x, y, vx, vy, life, maxLife, size, angle, spin;
        boolean shard;
        Color color;
    }

    static final class Ring {
        double x, y, maxRadius, life, maxLife;
        boolean flash;
        Color color;
    }

    /** A fading copy of the ship left behind while dashing. */
    static final class Afterimage {
        double x, y, angle, life;
    }

    static final class Bolt {
        double x1, y1, x2, y2, life;
    }

    static final class FloatText {
        double x, y, life;
        String text;
        Color color;
        int size;
    }

    enum PickupType { HEART, BOMB, COIN, DIAMOND }

    static final class Pickup {
        PickupType type;
        double x, y, vx, vy, life;
    }

    static final class Theme {
        final String name;
        final BufferedImage image;

        Theme(String name, BufferedImage image) {
            this.name = name;
            this.image = image;
        }
    }

    /** All sound is generated in code, so the game needs no audio files. */
    static final class Sound {
        enum Effect { SHOOT, HIT, BOOM, BIG_BOOM, HURT, PICKUP, COIN, DASH, UPGRADE, WAVE, BOSS, ZAP, SPECIAL, DENY }

        enum Track { MENU, GAME, BOSS, NONE }

        static final float RATE = 22050f;
        private final Map<Effect, Clip[]> clips = new EnumMap<Effect, Clip[]>(Effect.class);
        private final Map<Track, Clip> tracks = new EnumMap<Track, Clip>(Track.class);
        private final Random variation = new Random();
        private final Map<Effect, Long> lastPlayed = new EnumMap<Effect, Long>(Effect.class);
        private final ThreadPoolExecutor worker;
        private final ExecutorService musicWorker;
        private volatile Track wanted = Track.NONE;
        private Track playing = Track.NONE;
        private volatile boolean tracksReady;
        volatile boolean effectsOn = true;
        volatile boolean musicOn = true;

        Sound() {
            ThreadFactory daemon = new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "sound");
                    thread.setDaemon(true);
                    return thread;
                }
            };
            worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(6),
                    daemon, new ThreadPoolExecutor.DiscardPolicy());
            musicWorker = Executors.newSingleThreadExecutor(daemon);
        }

        /** Prepares everything in the background. A computer with no speakers just stays silent. */
        void load() {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        makeEffect(Effect.SHOOT, 900, 350, 0.07, 0.13, 0.05);
                        makeEffect(Effect.HIT, 500, 250, 0.05, 0.15, 0.3);
                        makeEffect(Effect.BOOM, 220, 40, 0.22, 0.38, 0.8);
                        makeEffect(Effect.BIG_BOOM, 130, 25, 0.8, 0.7, 0.85);
                        makeEffect(Effect.HURT, 300, 70, 0.3, 0.55, 0.45);
                        makeEffect(Effect.PICKUP, 600, 1300, 0.14, 0.35, 0);
                        makeEffect(Effect.COIN, 1300, 1900, 0.09, 0.22, 0);
                        makeEffect(Effect.DASH, 250, 900, 0.14, 0.3, 0.5);
                        makeEffect(Effect.UPGRADE, 450, 1500, 0.3, 0.4, 0);
                        makeEffect(Effect.WAVE, 300, 800, 0.4, 0.35, 0);
                        makeEffect(Effect.BOSS, 90, 55, 1.0, 0.6, 0.2);
                        makeEffect(Effect.ZAP, 1800, 500, 0.08, 0.16, 0.6);
                        makeEffect(Effect.SPECIAL, 200, 1800, 0.5, 0.5, 0.15);
                        makeEffect(Effect.DENY, 160, 120, 0.15, 0.35, 0);
                    } catch (Exception | LinkageError e) {
                        System.err.println("Sound effects are off: " + e.getMessage());
                    }
                }
            });
            musicWorker.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        makeTrack(Track.MENU);
                        makeTrack(Track.GAME);
                        makeTrack(Track.BOSS);
                        tracksReady = true;
                        applyMusic();
                    } catch (Exception | LinkageError e) {
                        System.err.println("Music is off: " + e.getMessage());
                    }
                }
            });
        }

        private void makeEffect(Effect effect, double startHz, double endHz, double seconds, double volume, double noise)
                throws Exception {
            // Sounds you hear constantly get a few pitches, so they do not grate over a long run.
            boolean varied = effect == Effect.SHOOT || effect == Effect.HIT || effect == Effect.BOOM
                    || effect == Effect.COIN || effect == Effect.ZAP;
            double[] pitches = varied ? new double[] {0.93, 1, 1.08} : new double[] {1};
            Clip[] set = new Clip[pitches.length];
            for (int i = 0; i < pitches.length; i++) {
                byte[] data = renderEffect(effect.ordinal() * 31L + i, startHz * pitches[i], endHz * pitches[i],
                        seconds, volume, noise);
                set[i] = AudioSystem.getClip();
                set[i].open(new AudioFormat(RATE, 16, 1, true, false), data, 0, data.length);
            }
            clips.put(effect, set);
        }

        /** Two slightly detuned, softened square waves plus filtered noise, with a click-free fade. */
        static byte[] renderEffect(long seed, double startHz, double endHz, double seconds, double volume, double noise) {
            int length = (int) (RATE * seconds);
            byte[] data = new byte[length * 2];
            Random random = new Random(seed);
            double phase = 0, phase2 = 0, rumble = 0;
            double shape = Math.tanh(3);
            for (int i = 0; i < length; i++) {
                double t = i / (double) length;
                double hz = startHz + (endHz - startHz) * t;
                phase += hz / RATE;
                phase2 += hz * 1.007 / RATE;
                double tone = (Math.tanh(3 * Math.sin(phase * Math.PI * 2))
                        + Math.tanh(3 * Math.sin(phase2 * Math.PI * 2))) / (2 * shape);
                rumble += ((random.nextDouble() * 2 - 1) - rumble) * 0.35;
                double sample = tone * (1 - noise) + rumble * noise * 1.6;
                double attack = Math.min(1, i / (RATE * 0.002));
                double fade = Math.pow(1 - t, 1.8);
                double value = Math.max(-1, Math.min(1, sample * volume * 1.2 * attack * fade));
                int level = (int) (value * 32767);
                data[i * 2] = (byte) level;
                data[i * 2 + 1] = (byte) (level >> 8);
            }
            return data;
        }

        private void makeTrack(Track track) throws Exception {
            byte[] data = synthesize(track);
            Clip clip = AudioSystem.getClip();
            clip.open(new AudioFormat(RATE, 16, 1, true, false), data, 0, data.length);
            tracks.put(track, clip);
        }

        /** Writes a looping chiptune: bass, arpeggio and drums over a chord progression. */
        static byte[] synthesize(Track track) {
            int bpm = track == Track.BOSS ? 158 : track == Track.GAME ? 134 : 96;
            int[] roots = track == Track.BOSS ? new int[] {0, 0, 1, 0, 3, 1, 0, -2}
                    : track == Track.GAME ? new int[] {0, 0, -4, -4, -2, -2, 3, 2}
                    : new int[] {0, -4, 3, -2, 0, -4, -2, -2};
            int stepSamples = (int) (RATE * 60.0 / bpm / 4);
            int steps = roots.length * 16;
            double[] mix = new double[stepSamples * steps];
            Random noise = new Random(3);
            int[] chord = {0, 3, 7, 12, 15, 12, 7, 3};
            for (int step = 0; step < steps; step++) {
                int root = roots[step / 16];
                int start = step * stepSamples;
                int beat = step % 16;
                if (track == Track.MENU) {
                    addNote(mix, start, stepSamples * 2, 220 * semitones(root + chord[step % 8]), 0.16, 0.25);
                    if (beat % 8 == 0) {
                        addNote(mix, start, stepSamples * 8, 55 * semitones(root), 0.22, 0.5);
                    }
                    continue;
                }
                double pulse = track == Track.BOSS ? 0.3 : 0.5;
                if (beat % 2 == 0) {
                    addNote(mix, start, stepSamples * 2, 55 * semitones(root + (beat % 8 == 6 ? 12 : 0)), 0.3, pulse);
                }
                addNote(mix, start, stepSamples, 440 * semitones(root + chord[step % 8]), 0.1, 0.25);
                if (beat % 4 == 0) {
                    addDrum(mix, start, stepSamples * 2, 120, 45, 0.5, 0, noise);
                }
                if (beat % 8 == 4) {
                    addDrum(mix, start, stepSamples * 2, 220, 140, 0.24, 0.8, noise);
                }
                if (beat % 2 == 1) {
                    addDrum(mix, start, stepSamples / 2, 0, 0, 0.09, 1, noise);
                }
            }
            double peak = 0.0001;
            for (double value : mix) {
                peak = Math.max(peak, Math.abs(value));
            }
            double gain = 0.3 / peak; // Keeps the music well below the sound effects.
            byte[] data = new byte[mix.length * 2];
            for (int i = 0; i < mix.length; i++) {
                int sample = (int) (mix[i] * gain * 32767);
                data[i * 2] = (byte) sample;
                data[i * 2 + 1] = (byte) (sample >> 8);
            }
            return data;
        }

        private static double semitones(int count) {
            return Math.pow(2, count / 12.0);
        }

        private static void addNote(double[] mix, int start, int length, double hz, double volume, double duty) {
            double phase = 0;
            for (int i = 0; i < length; i++) {
                phase += hz / RATE;
                double envelope = 1 - i / (double) length;
                mix[(start + i) % mix.length] += ((phase % 1) < duty ? 1 : -1) * volume * envelope;
            }
        }

        private static void addDrum(double[] mix, int start, int length, double fromHz, double toHz, double volume,
                                    double noiseAmount, Random noise) {
            double phase = 0;
            for (int i = 0; i < length; i++) {
                double t = i / (double) length;
                phase += (fromHz + (toHz - fromHz) * t) / RATE;
                double tone = Math.sin(phase * Math.PI * 2) * (1 - noiseAmount);
                double hiss = (noise.nextDouble() * 2 - 1) * noiseAmount;
                mix[(start + i) % mix.length] += (tone + hiss) * volume * (1 - t) * (1 - t);
            }
        }

        int loadedEffects() {
            return clips.size();
        }

        int loadedTracks() {
            return tracks.size();
        }

        void play(final Effect effect) {
            if (!effectsOn) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = lastPlayed.get(effect);
            if (last != null && now - last < 45) {
                return;
            }
            lastPlayed.put(effect, now);
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    Clip[] set = clips.get(effect);
                    if (set != null) {
                        Clip clip = set[variation.nextInt(set.length)];
                        clip.stop();
                        clip.setFramePosition(0);
                        clip.start();
                    }
                }
            });
        }

        void setMusic(Track track) {
            if (track != wanted) {
                wanted = track;
                musicWorker.execute(new Runnable() {
                    @Override
                    public void run() {
                        applyMusic();
                    }
                });
            }
        }

        void toggleMusic() {
            musicOn = !musicOn;
            musicWorker.execute(new Runnable() {
                @Override
                public void run() {
                    applyMusic();
                }
            });
        }

        private void applyMusic() {
            if (!tracksReady) {
                return;
            }
            Track target = musicOn ? wanted : Track.NONE;
            if (target == playing) {
                return;
            }
            Clip current = tracks.get(playing);
            if (current != null) {
                current.stop();
            }
            Clip next = tracks.get(target);
            if (next != null) {
                next.setFramePosition(0);
                next.loop(Clip.LOOP_CONTINUOUSLY);
            }
            playing = target;
        }
    }

    static final class GamePanel extends JPanel {
        static final double PLAYER_RADIUS = 11, DASH_SPEED = 900, DASH_TIME = 0.15;
        static final int START_BOMBS = 1, MAX_PARTICLES = 800, MAX_ENEMIES = 110;
        static final double SPECIAL_TIME_OVERDRIVE = 5, SPECIAL_TIME_WARP = 5, SPECIAL_TIME_FORTRESS = 4;
        static final double AFTERIMAGE_LIFE = 0.24;
        private static final BasicStroke SHARD_STROKE = new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        final Sound sound = new Sound();
        final Timer timer;
        Random rng = new Random();
        private final Random fx = new Random();
        private final Theme[] themes = {
            new Theme("Deep Space", makeBackground(new Color(9, 10, 24), new Color(28, 32, 70), new Color(70, 90, 190), new Color(40, 50, 130))),
            new Theme("Toxic Zone", makeBackground(new Color(7, 18, 14), new Color(20, 56, 40), new Color(70, 190, 120), new Color(20, 110, 70))),
            new Theme("Magma Core", makeBackground(new Color(22, 9, 10), new Color(66, 26, 26), new Color(220, 90, 60), new Color(130, 40, 20))),
            new Theme("Violet Rift", makeBackground(new Color(16, 8, 26), new Color(50, 26, 76), new Color(180, 90, 240), new Color(100, 30, 140))),
        };
        private final BufferedImage vignette = makeVignette(new Color(0, 0, 0, 150));
        private final BufferedImage dangerVignette = makeVignette(new Color(255, 20, 50, 190));
        private final double[][] stars = new double[110][3];
        private final Font[] plainFonts = new Font[128], boldFonts = new Font[128];

        // Saved between runs.
        File saveFile = new File("neonoverdrive_save.properties");
        int coins, highScore;
        // Coins buy permanent upgrades. Diamonds buy ships and skins.
        int diamonds;
        Skin skin = Skin.DEFAULT;
        final boolean[] ownedSkins = new boolean[Skin.values().length];
        final boolean[] unlocked = new boolean[Ship.values().length];
        final int[] perks = new int[Perk.values().length];
        Ship ship = Ship.STRIKER;
        Difficulty difficulty = Difficulty.NORMAL;

        // Lifetime records, also saved.
        int totalRuns, totalKills, totalBosses, bestWave, bestComboEver;
        double playSeconds;
        final int[] shipBestWave = new int[Ship.values().length];
        final int[] bossKills = new int[3];
        final boolean[] achieved = new boolean[Achievement.values().length];

        // Daily run. Your own ship and difficulty are put back when it ends.
        boolean dailyRun;
        Modifier modifier = Modifier.NONE;
        Ship savedShip = Ship.STRIKER;
        Difficulty savedDifficulty = Difficulty.NORMAL;
        String dailyLastDate = "", dailyNote = "";
        int dailyBest, dailyStreak;
        LocalDate dateOverride;

        // This run's report card.
        boolean runActive, hitThisWave;
        double runTime, runDamage;
        int runBosses, runAchievements;

        // Achievement and unlock pop-ups, shown one at a time.
        final List<String> toasts = new ArrayList<String>();
        String toastText = "";
        double toastTimer;

        State state = State.MENU;
        double clock;
        long lastTick;
        // Window scaling: the game always draws at W x H and is stretched to fit.
        double viewScale = 1, viewX, viewY;
        boolean fullscreen;

        // Input.
        boolean up, down, left, right, firing, autoFire;
        double aimX = W / 2.0, aimY = H / 3.0;
        int mouseX, mouseY;

        // Player.
        double px, py, facing, invulnerable, dashTimer, dashCooldown, dashDirX, dashDirY, fireCooldown, hurtFlash;
        int health, maxHealth, bombs, secondLives;
        double specialCharge, overdriveTimer, warpTimer, fortressTimer;

        // Weapon, changed by upgrades.
        double fireDelay, bulletDamage, bulletRadius, moveSpeed, dashDelay, critChance, dashDamage, homing, explosiveRadius;
        double frostTime;
        int shots, pierce, bounces, orbitals, chainTargets, vampire, killsSinceHeal, rerolls;
        double orbitAngle;
        final int[] stacks = new int[Upgrade.values().length];
        final List<Drone> drones = new ArrayList<Drone>();

        // Progress.
        int wave, score, kills, combo, bestCombo, runCoins, runDiamonds;
        double coinFraction;
        double comboTimer, spawnTimer, spawnDelay, clearTimer, bannerTimer, cardDelay, overDelay, hitStop;
        double announceTimer, noticeTimer;
        boolean clearing, newRecord;
        String banner = "", announcement = "", notice = "";
        Color announceColor = Color.WHITE;
        final List<EnemyType> spawnQueue = new ArrayList<EnemyType>();
        final List<Upgrade> choices = new ArrayList<Upgrade>();
        int hoveredCard = -1;

        // World.
        final List<Enemy> enemies = new ArrayList<Enemy>();
        final List<Enemy> newEnemies = new ArrayList<Enemy>();
        final List<Bullet> bullets = new ArrayList<Bullet>();
        final List<Bullet> newBullets = new ArrayList<Bullet>();
        final List<Particle> particles = new ArrayList<Particle>();
        final List<Ring> rings = new ArrayList<Ring>();
        final List<Bolt> bolts = new ArrayList<Bolt>();
        final List<FloatText> texts = new ArrayList<FloatText>();
        final List<Pickup> pickups = new ArrayList<Pickup>();
        final List<Afterimage> afterimages = new ArrayList<Afterimage>();
        double shake, slowMotion, recoil, displayScore, multiplierPulse;

        GamePanel() {
            setPreferredSize(new Dimension(W, H));
            setMinimumSize(new Dimension(W / 2, H / 2));
            setBackground(Color.BLACK);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            Random starRandom = new Random(11);
            for (double[] star : stars) {
                star[0] = starRandom.nextDouble() * W;
                star[1] = starRandom.nextDouble() * H;
                star[2] = 0.3 + starRandom.nextDouble();
            }
            unlocked[Ship.STRIKER.ordinal()] = true;
            unlocked[Ship.BLASTER.ordinal()] = true;
            ownedSkins[Skin.DEFAULT.ordinal()] = true;
            timer = new Timer(16, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    long now = System.nanoTime();
                    double dt = Math.min((now - lastTick) / 1e9, 0.05);
                    lastTick = now;
                    update(dt);
                    repaint();
                    Toolkit.getDefaultToolkit().sync();
                }
            });
            addInput();
            loadSave();
            refreshSavedRun();
        }

        void start() {
            sound.load();
            lastTick = System.nanoTime();
            timer.start();
        }

        // ============================== SAVING ==============================

        void loadSave() {
            if (saveFile == null || !saveFile.exists()) {
                return;
            }
            Properties data = new Properties();
            try (InputStream in = new FileInputStream(saveFile)) {
                data.load(in);
            } catch (Exception e) {
                System.err.println("Could not read the save file, starting fresh: " + e.getMessage());
                return;
            }
            // Each value is read on its own, so one damaged line cannot lose the rest of the save.
            coins = readInt(data, "coins");
            diamonds = readInt(data, "diamonds");
            highScore = readInt(data, "highScore");
            for (Skin s : Skin.values()) {
                if (Boolean.parseBoolean(data.getProperty("skin." + s.name(), "false"))) {
                    ownedSkins[s.ordinal()] = true;
                }
            }
            for (Skin s : Skin.values()) {
                if (s.name().equals(data.getProperty("selectedSkin")) && ownedSkins[s.ordinal()]) {
                    skin = s;
                }
            }
            for (Ship s : Ship.values()) {
                if (Boolean.parseBoolean(data.getProperty("ship." + s.name(), "false"))) {
                    unlocked[s.ordinal()] = true;
                }
                shipBestWave[s.ordinal()] = readInt(data, "stat.bestWave." + s.name());
            }
            for (Perk p : Perk.values()) {
                perks[p.ordinal()] = Math.min(p.max, readInt(data, "perk." + p.name()));
            }
            try {
                Ship selected = Ship.valueOf(data.getProperty("selectedShip", "STRIKER"));
                if (unlocked[selected.ordinal()]) {
                    ship = selected;
                }
                difficulty = Difficulty.valueOf(data.getProperty("difficulty", "NORMAL"));
            } catch (IllegalArgumentException e) {
                System.err.println("Ignoring a damaged ship or difficulty in the save: " + e.getMessage());
            }
            totalRuns = readInt(data, "stat.runs");
            totalKills = readInt(data, "stat.kills");
            totalBosses = readInt(data, "stat.bosses");
            bestWave = readInt(data, "stat.bestWave");
            bestComboEver = readInt(data, "stat.bestCombo");
            playSeconds = readInt(data, "stat.seconds");
            for (int i = 0; i < bossKills.length; i++) {
                bossKills[i] = readInt(data, "stat.boss." + i);
            }
            for (Achievement a : Achievement.values()) {
                achieved[a.ordinal()] = Boolean.parseBoolean(data.getProperty("achievement." + a.name(), "false"));
            }
            dailyLastDate = data.getProperty("daily.lastDate", "");
            dailyBest = readInt(data, "daily.best");
            dailyStreak = readInt(data, "daily.streak");
            sound.effectsOn = Boolean.parseBoolean(data.getProperty("option.sound", "true"));
            sound.musicOn = Boolean.parseBoolean(data.getProperty("option.music", "true"));
            autoFire = Boolean.parseBoolean(data.getProperty("option.autoFire", "false"));
        }

        private static int readInt(Properties data, String key) {
            try {
                return Math.max(0, Integer.parseInt(data.getProperty(key, "0").trim()));
            } catch (NumberFormatException e) {
                System.err.println("Ignoring a damaged value in the save: " + key);
                return 0;
            }
        }

        void save() {
            if (saveFile == null) {
                return;
            }
            Properties data = new Properties();
            data.setProperty("coins", String.valueOf(coins));
            data.setProperty("diamonds", String.valueOf(diamonds));
            data.setProperty("highScore", String.valueOf(highScore));
            for (Skin s : Skin.values()) {
                data.setProperty("skin." + s.name(), String.valueOf(ownedSkins[s.ordinal()]));
            }
            data.setProperty("selectedSkin", skin.name());
            for (Ship s : Ship.values()) {
                data.setProperty("ship." + s.name(), String.valueOf(unlocked[s.ordinal()]));
            }
            for (Perk p : Perk.values()) {
                data.setProperty("perk." + p.name(), String.valueOf(perks[p.ordinal()]));
            }
            // A daily run borrows a ship and a difficulty, so remember the player's own choices.
            data.setProperty("selectedShip", (dailyRun ? savedShip : ship).name());
            data.setProperty("difficulty", (dailyRun ? savedDifficulty : difficulty).name());
            data.setProperty("stat.runs", String.valueOf(totalRuns));
            data.setProperty("stat.kills", String.valueOf(totalKills));
            data.setProperty("stat.bosses", String.valueOf(totalBosses));
            data.setProperty("stat.bestWave", String.valueOf(bestWave));
            data.setProperty("stat.bestCombo", String.valueOf(bestComboEver));
            data.setProperty("stat.seconds", String.valueOf((int) playSeconds));
            for (Ship s : Ship.values()) {
                data.setProperty("stat.bestWave." + s.name(), String.valueOf(shipBestWave[s.ordinal()]));
            }
            for (int i = 0; i < bossKills.length; i++) {
                data.setProperty("stat.boss." + i, String.valueOf(bossKills[i]));
            }
            for (Achievement a : Achievement.values()) {
                data.setProperty("achievement." + a.name(), String.valueOf(achieved[a.ordinal()]));
            }
            data.setProperty("daily.lastDate", dailyLastDate);
            data.setProperty("daily.best", String.valueOf(dailyBest));
            data.setProperty("daily.streak", String.valueOf(dailyStreak));
            data.setProperty("option.sound", String.valueOf(sound.effectsOn));
            data.setProperty("option.music", String.valueOf(sound.musicOn));
            data.setProperty("option.autoFire", String.valueOf(autoFire));
            try (OutputStream out = new FileOutputStream(saveFile)) {
                data.store(out, "Neon Overdrive save");
            } catch (Exception e) {
                System.err.println("Could not save: " + e.getMessage());
            }
        }

        /**
         * Closing the window saves everything. A run in progress is not ended: it was already
         * saved at the start of its wave (or on the upgrade screen), so it can be continued later.
         */
        void saveOnExit() {
            save();
        }

        // ============================ SAVED RUN =============================
        // The run in progress lives in its own file next to the main save. It is written at the
        // start of every wave and when the upgrade cards appear, and deleted when the run ends.

        /** Wave and ship of the saved run, for the hangar button. Null when there is none. */
        String savedRunInfo;

        File runFile() {
            if (saveFile == null) {
                return null;
            }
            String name = saveFile.getName().replaceFirst("\\.properties$", "") + "_run.properties";
            return new File(saveFile.getAbsoluteFile().getParentFile(), name);
        }

        boolean hasSavedRun() {
            return savedRunInfo != null && runFile() != null;
        }

        private Properties readRunFile() {
            File file = runFile();
            if (file == null || !file.exists()) {
                return null;
            }
            Properties data = new Properties();
            try (InputStream in = new FileInputStream(file)) {
                data.load(in);
                return data;
            } catch (Exception e) {
                System.err.println("Could not read the saved run: " + e.getMessage());
                return null;
            }
        }

        void refreshSavedRun() {
            Properties data = readRunFile();
            savedRunInfo = null;
            if (data != null) {
                String shipName = data.getProperty("ship", "");
                for (Ship s : Ship.values()) {
                    if (s.name().equals(shipName)) {
                        savedRunInfo = "Wave " + readInt(data, "wave") + " - " + s.title
                                + (Boolean.parseBoolean(data.getProperty("dailyRun")) ? " (daily)" : "");
                    }
                }
            }
        }

        private void deleteRunFile() {
            File file = runFile();
            if (file != null && file.exists() && !file.delete()) {
                System.err.println("Could not delete the saved run: " + file);
            }
            savedRunInfo = null;
        }

        /** Writes the run in progress. Only called at a wave start or on the upgrade screen. */
        void saveRun() {
            File file = runFile();
            if (file == null || !runActive) {
                return;
            }
            Properties data = new Properties();
            data.setProperty("state", state == State.UPGRADE ? "UPGRADE" : "PLAYING");
            data.setProperty("ship", ship.name());
            data.setProperty("difficulty", difficulty.name());
            data.setProperty("modifier", modifier.name());
            data.setProperty("dailyRun", String.valueOf(dailyRun));
            data.setProperty("savedShip", savedShip.name());
            data.setProperty("savedDifficulty", savedDifficulty.name());
            int[] ints = {wave, score, kills, bestCombo, runCoins, runBosses, runAchievements, health, maxHealth,
                bombs, secondLives, rerolls, shots, pierce, bounces, orbitals, chainTargets, vampire, killsSinceHeal,
                drones.size()};
            for (int i = 0; i < RUN_INTS.length; i++) {
                data.setProperty(RUN_INTS[i], String.valueOf(ints[i]));
            }
            double[] doubles = {coinFraction, runTime, runDamage, specialCharge, fireDelay, bulletDamage, bulletRadius,
                moveSpeed, dashDelay, critChance, dashDamage, homing, explosiveRadius, frostTime};
            for (int i = 0; i < RUN_DOUBLES.length; i++) {
                data.setProperty(RUN_DOUBLES[i], String.valueOf(doubles[i]));
            }
            for (Upgrade u : Upgrade.values()) {
                data.setProperty("stack." + u.name(), String.valueOf(stacks[u.ordinal()]));
            }
            StringBuilder cards = new StringBuilder();
            for (Upgrade u : choices) {
                cards.append(cards.length() > 0 ? "," : "").append(u.name());
            }
            data.setProperty("choices", state == State.UPGRADE ? cards.toString() : "");
            data.setProperty("runDiamonds", String.valueOf(runDiamonds));
            try (OutputStream out = new FileOutputStream(file)) {
                data.store(out, "Neon Overdrive run in progress");
            } catch (Exception e) {
                System.err.println("Could not save the run: " + e.getMessage());
                return;
            }
            savedRunInfo = "Wave " + wave + " - " + ship.title + (dailyRun ? " (daily)" : "");
        }

        private static final String[] RUN_INTS = {"wave", "score", "kills", "bestCombo", "runCoins", "runBosses",
            "runAchievements", "health", "maxHealth", "bombs", "secondLives", "rerolls", "shots", "pierce", "bounces",
            "orbitals", "chainTargets", "vampire", "killsSinceHeal", "drones"};
        private static final String[] RUN_DOUBLES = {"coinFraction", "runTime", "runDamage", "specialCharge",
            "fireDelay", "bulletDamage", "bulletRadius", "moveSpeed", "dashDelay", "critChance", "dashDamage", "homing",
            "explosiveRadius", "frostTime"};

        private static double readDouble(Properties data, String key) {
            String text = data.getProperty(key);
            if (text == null) {
                throw new IllegalArgumentException("missing " + key);
            }
            double value = Double.parseDouble(text.trim());
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
                throw new IllegalArgumentException("bad " + key);
            }
            return value;
        }

        /** Picks the saved run back up. Returns false (and drops the file) if it is damaged. */
        boolean continueRun() {
            Properties data = readRunFile();
            if (data == null) {
                savedRunInfo = null;
                return false;
            }
            try {
                Ship runShip = Ship.valueOf(data.getProperty("ship"));
                Difficulty runDifficulty = Difficulty.valueOf(data.getProperty("difficulty"));
                Modifier runModifier = Modifier.valueOf(data.getProperty("modifier", "NONE"));
                boolean runDaily = Boolean.parseBoolean(data.getProperty("dailyRun"));
                Ship ownShip = Ship.valueOf(data.getProperty("savedShip", ship.name()));
                Difficulty ownDifficulty = Difficulty.valueOf(data.getProperty("savedDifficulty", difficulty.name()));
                int[] ints = new int[RUN_INTS.length];
                for (int i = 0; i < ints.length; i++) {
                    ints[i] = Integer.parseInt(data.getProperty(RUN_INTS[i], "x").trim());
                    if (ints[i] < 0) {
                        throw new IllegalArgumentException("bad " + RUN_INTS[i]);
                    }
                }
                double[] doubles = new double[RUN_DOUBLES.length];
                for (int i = 0; i < doubles.length; i++) {
                    doubles[i] = readDouble(data, RUN_DOUBLES[i]);
                }
                int[] savedStacks = new int[stacks.length];
                for (Upgrade u : Upgrade.values()) {
                    savedStacks[u.ordinal()] = Math.min(u.max, readInt(data, "stack." + u.name()));
                }
                List<Upgrade> savedChoices = new ArrayList<Upgrade>();
                for (String name : data.getProperty("choices", "").split(",")) {
                    if (!name.trim().isEmpty()) {
                        savedChoices.add(Upgrade.valueOf(name.trim()));
                    }
                }
                boolean upgradeScreen = "UPGRADE".equals(data.getProperty("state")) && !savedChoices.isEmpty();
                if (ints[0] < 1 || ints[8] < 1 || ints[7] < 1) {
                    throw new IllegalArgumentException("bad wave or health");
                }

                // Everything checked out, so set the run up exactly like a fresh one, then restore it.
                dailyRun = runDaily;
                if (runDaily) {
                    savedShip = ownShip;
                    savedDifficulty = ownDifficulty;
                }
                ship = runShip;
                difficulty = runDifficulty;
                modifier = runDaily ? runModifier : Modifier.NONE;
                resetRun();
                rng = runDaily ? new Random(dailySeed() + ints[0]) : new Random();
                wave = ints[0];
                score = ints[1];
                kills = ints[2];
                bestCombo = ints[3];
                runCoins = ints[4];
                runDiamonds = readInt(data, "runDiamonds");
                runBosses = ints[5];
                runAchievements = ints[6];
                maxHealth = ints[8];
                health = Math.min(ints[7], maxHealth);
                bombs = ints[9];
                secondLives = ints[10];
                rerolls = ints[11];
                shots = Math.max(1, ints[12]);
                pierce = ints[13];
                bounces = ints[14];
                orbitals = ints[15];
                chainTargets = ints[16];
                vampire = ints[17];
                killsSinceHeal = ints[18];
                for (int i = 0; i < ints[19]; i++) {
                    Drone drone = new Drone();
                    drone.x = px;
                    drone.y = py;
                    drones.add(drone);
                }
                coinFraction = doubles[0];
                runTime = doubles[1];
                runDamage = doubles[2];
                specialCharge = Math.min(1, doubles[3]);
                fireDelay = doubles[4];
                bulletDamage = doubles[5];
                bulletRadius = doubles[6];
                moveSpeed = doubles[7];
                dashDelay = doubles[8];
                critChance = doubles[9];
                dashDamage = doubles[10];
                homing = doubles[11];
                explosiveRadius = doubles[12];
                frostTime = doubles[13];
                System.arraycopy(savedStacks, 0, stacks, 0, stacks.length);
                displayScore = score;
                if (upgradeScreen) {
                    choices.clear();
                    choices.addAll(savedChoices);
                    cardDelay = 0.35;
                    hoveredCard = -1;
                    firing = false;
                    state = State.UPGRADE;
                } else {
                    // Replay the wave the run was saved on, from its start.
                    wave--;
                    nextWave();
                }
                showNotice("Run continued");
                return true;
            } catch (RuntimeException e) {
                System.err.println("The saved run is damaged and was dropped: " + e.getMessage());
                runActive = false;
                deleteRunFile();
                goToHangar();
                showNotice("The saved run was damaged and could not be loaded");
                return false;
            }
        }

        /** Starting a new run drops the saved one, but its coins and score still count. */
        void discardSavedRun() {
            Properties data = readRunFile();
            if (data != null) {
                coins += readInt(data, "runCoins");
                diamonds += readInt(data, "runDiamonds");
                totalRuns++;
                try {
                    playSeconds += readDouble(data, "runTime");
                } catch (RuntimeException e) {
                    System.err.println("Ignoring a damaged play time in the saved run");
                }
                int oldScore = readInt(data, "score");
                if (!Boolean.parseBoolean(data.getProperty("dailyRun")) && oldScore > highScore) {
                    highScore = oldScore;
                }
            }
            deleteRunFile();
        }

        // ============================== INPUT ===============================

        private void addInput() {
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    handleKey(e.getKeyCode(), true);
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    handleKey(e.getKeyCode(), false);
                }
            });
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mouseX = worldX(e.getX());
                    mouseY = worldY(e.getY());
                    aimX = mouseX;
                    aimY = mouseY;
                    hoveredCard = cardAt(mouseX, mouseY);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    mouseMoved(e);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    mouseMoved(e);
                    if (SwingUtilities.isRightMouseButton(e)) {
                        useSpecial();
                    } else {
                        boolean wasPlaying = state == State.PLAYING;
                        handleClick(mouseX, mouseY);
                        firing = wasPlaying && state == State.PLAYING;
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!SwingUtilities.isRightMouseButton(e)) {
                        firing = false;
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    releaseInput();
                    if (state == State.PLAYING) {
                        state = State.PAUSED;
                    }
                }
            });
        }

        int worldX(int screenX) {
            return (int) ((screenX - viewX) / viewScale);
        }

        int worldY(int screenY) {
            return (int) ((screenY - viewY) / viewScale);
        }

        private void releaseInput() {
            up = false;
            down = false;
            left = false;
            right = false;
            firing = false;
        }

        void handleKey(int key, boolean pressed) {
            switch (key) {
                case KeyEvent.VK_UP:
                case KeyEvent.VK_W:
                    up = pressed;
                    break;
                case KeyEvent.VK_DOWN:
                case KeyEvent.VK_S:
                    down = pressed;
                    break;
                case KeyEvent.VK_LEFT:
                case KeyEvent.VK_A:
                    left = pressed;
                    if (pressed) {
                        cycleShip(-1);
                    }
                    break;
                case KeyEvent.VK_RIGHT:
                case KeyEvent.VK_D:
                    right = pressed;
                    if (pressed) {
                        cycleShip(1);
                    }
                    break;
                default:
                    if (pressed) {
                        handleKeyPress(key);
                    }
                    break;
            }
        }

        private void cycleShip(int direction) {
            if (state != State.HANGAR && state != State.SKINS) {
                return;
            }
            int count = Ship.values().length;
            int index = ship.ordinal();
            for (int i = 0; i < count; i++) {
                index = (index + direction + count) % count;
                if (unlocked[index]) {
                    ship = Ship.values()[index];
                    return;
                }
            }
        }

        private void handleKeyPress(int key) {
            if (confirmReset) {
                // While the "are you sure?" box is open, only Y and N/ESC do anything.
                if (key == KeyEvent.VK_Y) {
                    resetProgress();
                } else if (key == KeyEvent.VK_N || key == KeyEvent.VK_ESCAPE) {
                    confirmReset = false;
                }
                return;
            }
            if (key == KeyEvent.VK_DELETE && state == State.MENU) {
                confirmReset = true;
                return;
            }
            if (key == KeyEvent.VK_M) {
                sound.effectsOn = !sound.effectsOn;
                save();
            } else if (key == KeyEvent.VK_N) {
                sound.toggleMusic();
                save();
            } else if (key == KeyEvent.VK_F11) {
                toggleFullscreen();
            } else if (key == KeyEvent.VK_F) {
                autoFire = !autoFire;
                save();
            } else if (key == KeyEvent.VK_P || key == KeyEvent.VK_ESCAPE) {
                if (state == State.PLAYING) {
                    state = State.PAUSED;
                } else if (state == State.PAUSED) {
                    state = State.PLAYING;
                } else if (state == State.HANGAR) {
                    state = State.MENU;
                } else if (state == State.RECORDS || state == State.SKINS) {
                    state = State.HANGAR;
                }
            } else if (key == KeyEvent.VK_K && state == State.HANGAR) {
                state = State.SKINS;
            } else if (key == KeyEvent.VK_K && state == State.SKINS) {
                state = State.HANGAR;
            } else if (key == KeyEvent.VK_X && state == State.PAUSED) {
                endRun();
                goToHangar();
            } else if (key == KeyEvent.VK_C && state == State.HANGAR && hasSavedRun()) {
                continueRun();
            } else if (key == KeyEvent.VK_G && state == State.HANGAR) {
                startDaily();
            } else if (key == KeyEvent.VK_T && state == State.HANGAR) {
                state = State.RECORDS;
            } else if (key == KeyEvent.VK_T && state == State.RECORDS) {
                state = State.HANGAR;
            } else if (key == KeyEvent.VK_SPACE) {
                if (state == State.PLAYING) {
                    tryDash();
                } else if (state == State.PAUSED) {
                    state = State.PLAYING;
                }
            } else if (key == KeyEvent.VK_Q) {
                useBomb();
            } else if (key == KeyEvent.VK_E) {
                useSpecial();
            } else if (key == KeyEvent.VK_R) {
                reroll();
            } else if (key == KeyEvent.VK_ENTER) {
                if (state == State.MENU) {
                    state = State.HANGAR;
                } else if (state == State.HANGAR) {
                    startGame();
                } else if (state == State.RECORDS) {
                    state = State.HANGAR;
                } else if (state == State.GAME_OVER && overDelay <= 0) {
                    goToHangar();
                }
            } else if (key >= KeyEvent.VK_1 && key <= KeyEvent.VK_3) {
                if (state == State.UPGRADE) {
                    chooseCard(key - KeyEvent.VK_1);
                } else if (state == State.HANGAR) {
                    difficulty = Difficulty.values()[key - KeyEvent.VK_1];
                }
            }
        }

        void handleClick(int x, int y) {
            switch (state) {
                case MENU:
                    if (confirmReset) {
                        if (resetYesButton().contains(x, y)) {
                            resetProgress();
                        } else if (resetNoButton().contains(x, y)) {
                            confirmReset = false;
                        }
                    } else if (resetButton().contains(x, y)) {
                        confirmReset = true;
                    } else {
                        state = State.HANGAR;
                    }
                    break;
                case HANGAR:
                    hangarClick(x, y);
                    break;
                case RECORDS:
                    state = State.HANGAR;
                    break;
                case SKINS:
                    skinsClick(x, y);
                    break;
                case GAME_OVER:
                    if (overDelay <= 0) {
                        goToHangar();
                    }
                    break;
                case UPGRADE:
                    if (rerollButton().contains(x, y)) {
                        reroll();
                    } else {
                        chooseCard(cardAt(x, y));
                    }
                    break;
                case PAUSED:
                    state = State.PLAYING;
                    break;
                default:
                    break;
            }
        }

        private void toggleFullscreen() {
            java.awt.Window window = SwingUtilities.getWindowAncestor(this);
            if (!(window instanceof JFrame)) {
                return;
            }
            JFrame frame = (JFrame) window;
            fullscreen = !fullscreen;
            frame.dispose();
            frame.setUndecorated(fullscreen);
            if (fullscreen) {
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            } else {
                frame.setExtendedState(JFrame.NORMAL);
                frame.pack();
                frame.setLocationRelativeTo(null);
            }
            frame.setVisible(true);
            requestFocusInWindow();
        }

        // ============================== HANGAR ==============================

        Rectangle shipCard(int index) {
            return new Rectangle(40 + index * 235, 92, 215, 205);
        }

        Rectangle difficultyButton(int index) {
            return new Rectangle(190 + index * 210, 326, 200, 44);
        }

        Rectangle perkCard(int index) {
            return new Rectangle(40 + index * 235, 402, 215, 118);
        }

        /** With a saved run the launch button shares its space with a continue button. */
        Rectangle launchButton() {
            return hasSavedRun() ? new Rectangle(505, 556, 165, 70) : new Rectangle(330, 556, 340, 70);
        }

        Rectangle continueButton() {
            return new Rectangle(330, 556, 165, 70);
        }

        Rectangle recordsButton() {
            return new Rectangle(40, 556, 270, 70);
        }

        Rectangle dailyButton() {
            return new Rectangle(690, 556, 270, 70);
        }

        private void hangarClick(int x, int y) {
            for (Ship s : Ship.values()) {
                if (shipCard(s.ordinal()).contains(x, y)) {
                    selectOrBuy(s);
                    return;
                }
            }
            for (Difficulty d : Difficulty.values()) {
                if (difficultyButton(d.ordinal()).contains(x, y)) {
                    difficulty = d;
                    save();
                    return;
                }
            }
            for (Perk p : Perk.values()) {
                if (perkCard(p.ordinal()).contains(x, y)) {
                    buyPerk(p);
                    return;
                }
            }
            if (skinsButton().contains(x, y)) {
                state = State.SKINS;
            } else if (hasSavedRun() && continueButton().contains(x, y)) {
                continueRun();
            } else if (launchButton().contains(x, y)) {
                startGame();
            } else if (dailyButton().contains(x, y)) {
                startDaily();
            } else if (recordsButton().contains(x, y)) {
                state = State.RECORDS;
            }
        }

        void selectOrBuy(Ship s) {
            if (unlocked[s.ordinal()]) {
                ship = s;
            } else if (diamonds >= s.cost) {
                diamonds -= s.cost;
                unlocked[s.ordinal()] = true;
                ship = s;
                sound.play(Sound.Effect.UPGRADE);
                showNotice(s.title + " unlocked!");
            } else {
                sound.play(Sound.Effect.DENY);
                showNotice("You need " + (s.cost - diamonds) + " more diamonds for " + s.title);
            }
            checkAchievements();
            save();
        }

        void buyPerk(Perk p) {
            int level = perks[p.ordinal()];
            if (level >= p.max) {
                sound.play(Sound.Effect.DENY);
                showNotice(p.title + " is already maxed out");
                return;
            }
            int cost = p.costAt(level);
            if (coins < cost) {
                sound.play(Sound.Effect.DENY);
                showNotice("You need " + (cost - coins) + " more coins for " + p.title);
                return;
            }
            coins -= cost;
            perks[p.ordinal()]++;
            sound.play(Sound.Effect.UPGRADE);
            showNotice(p.title + " upgraded!");
            checkAchievements();
            save();
        }

        // ============================ DAILY RUN =============================

        LocalDate today() {
            return dateOverride != null ? dateOverride : LocalDate.now();
        }

        /** The date alone decides the ship, the modifier and the random numbers of the run. */
        long dailySeed() {
            return today().toEpochDay() * 2654435761L + 90210;
        }

        Ship dailyShip() {
            return Ship.values()[new Random(dailySeed()).nextInt(Ship.values().length)];
        }

        Modifier dailyModifier() {
            Random random = new Random(dailySeed() + 1);
            return Modifier.values()[1 + random.nextInt(Modifier.values().length - 1)];
        }

        int dailyBestToday() {
            return today().toString().equals(dailyLastDate) ? dailyBest : 0;
        }

        /** A streak only counts while you have played today or yesterday. */
        int currentStreak() {
            boolean alive = today().toString().equals(dailyLastDate)
                    || today().minusDays(1).toString().equals(dailyLastDate);
            return alive ? dailyStreak : 0;
        }

        void startDaily() {
            savedShip = ship;
            savedDifficulty = difficulty;
            dailyRun = true;
            ship = dailyShip();
            difficulty = Difficulty.NORMAL;
            modifier = dailyModifier();
            rng = new Random(dailySeed());
            startGame();
        }

        /** Leaves a finished run, and hands back the ship and difficulty a daily run borrowed. */
        void goToHangar() {
            state = State.HANGAR;
            if (dailyRun) {
                dailyRun = false;
                ship = savedShip;
                difficulty = savedDifficulty;
                modifier = Modifier.NONE;
                rng = new Random();
            }
        }

        private void finishDaily() {
            String date = today().toString();
            if (!date.equals(dailyLastDate)) {
                dailyStreak = today().minusDays(1).toString().equals(dailyLastDate) ? dailyStreak + 1 : 1;
                dailyLastDate = date;
                dailyBest = 0;
                int bonus = 20 + 5 * Math.min(dailyStreak, 6);
                int gems = dailyStreak >= 3 ? 2 : 1;
                coins += bonus;
                diamonds += gems;
                dailyNote = "First daily run today: +" + bonus + " coins, +" + gems + " diamond" + (gems > 1 ? "s" : "")
                        + "  (" + dailyStreak + " day streak)";
            }
            if (score > dailyBest) {
                dailyBest = score;
                newRecord = true;
            }
        }

        // =========================== ACHIEVEMENTS ===========================

        void checkAchievements() {
            unlock(Achievement.BOSS_SLAYER, totalBosses > 0);
            unlock(Achievement.TRIPLE_THREAT, bossKills[0] > 0 && bossKills[1] > 0 && bossKills[2] > 0);
            unlock(Achievement.DEEP_RUN, bestWave >= 10);
            unlock(Achievement.SURVIVOR, bestWave >= 20);
            unlock(Achievement.RAMPAGE, bestComboEver >= 50);
            unlock(Achievement.GODLIKE, bestComboEver >= 100);
            unlock(Achievement.EXTERMINATOR, totalKills >= 1000);
            unlock(Achievement.DAILY_PILOT, !dailyLastDate.isEmpty());
            unlock(Achievement.REGULAR, dailyStreak >= 3);
            boolean allPerks = true;
            for (Perk p : Perk.values()) {
                allPerks &= perks[p.ordinal()] >= p.max;
            }
            unlock(Achievement.FULLY_LOADED, allPerks);
            earnShip(Ship.PHANTOM, bestWave >= 8);
            earnShip(Ship.TITAN, bossKills[1] > 0);
            boolean allShips = true;
            for (boolean owned : unlocked) {
                allShips &= owned;
            }
            unlock(Achievement.FLEET, allShips);
        }

        private void unlock(Achievement a, boolean reached) {
            if (reached && !achieved[a.ordinal()]) {
                achieved[a.ordinal()] = true;
                coins += a.reward;
                diamonds += a.diamonds;
                runAchievements++;
                toasts.add(a.title + "  +" + a.reward + " coins  +" + a.diamonds + " diamonds");
                save();
            }
        }

        private void earnShip(Ship s, boolean reached) {
            if (reached && !unlocked[s.ordinal()]) {
                unlocked[s.ordinal()] = true;
                toasts.add("New ship unlocked: " + s.title);
                save();
            }
        }

        private void updateToasts(double dt) {
            toastTimer = Math.max(0, toastTimer - dt);
            if (toastTimer <= 0 && !toasts.isEmpty()) {
                toastText = toasts.remove(0);
                toastTimer = 2.8;
                sound.play(Sound.Effect.UPGRADE);
            }
        }

        private void showNotice(String text) {
            notice = text;
            noticeTimer = 2.5;
        }

        // ============================== SETUP ===============================

        void startGame() {
            discardSavedRun();
            resetRun();
            nextWave();
        }

        /** Puts the player, weapon and world back to the start of a run, without starting a wave. */
        void resetRun() {
            save();
            if (!dailyRun) {
                modifier = Modifier.NONE;
            }
            runActive = true;
            runTime = 0;
            runDamage = 0;
            runBosses = 0;
            runAchievements = 0;
            dailyNote = "";
            displayScore = 0;
            multiplierPulse = 0;
            recoil = 0;
            afterimages.clear();
            px = W / 2.0;
            py = H / 2.0;
            maxHealth = Math.max(1, ship.health + perks[Perk.HULL.ordinal()] + difficulty.bonusHealth + modifier.healthChange);
            health = maxHealth;
            bombs = START_BOMBS + perks[Perk.ARSENAL.ordinal()] + modifier.bonusBombs;
            rerolls = 1 + perks[Perk.REROLL.ordinal()];
            secondLives = 0;
            invulnerable = 0;
            dashTimer = 0;
            dashCooldown = 0;
            fireCooldown = 0;
            hurtFlash = 0;
            specialCharge = 0.5;
            overdriveTimer = 0;
            warpTimer = 0;
            fortressTimer = 0;
            fireDelay = ship.fireDelay;
            bulletDamage = ship.damage * modifier.damage;
            bulletRadius = ship.bulletRadius;
            moveSpeed = ship.speed;
            dashDelay = ship.dashDelay;
            shots = ship.shots;
            critChance = 0;
            dashDamage = 0;
            homing = 0;
            explosiveRadius = ship == Ship.TITAN ? 55 : 0;
            frostTime = 0;
            pierce = 0;
            bounces = 0;
            orbitals = 0;
            chainTargets = 0;
            vampire = 0;
            killsSinceHeal = 0;
            Arrays.fill(stacks, 0);
            drones.clear();
            wave = 0;
            score = 0;
            kills = 0;
            combo = 0;
            bestCombo = 0;
            comboTimer = 0;
            runCoins = 0;
            runDiamonds = 0;
            coinFraction = 0;
            newRecord = false;
            enemies.clear();
            newEnemies.clear();
            bullets.clear();
            newBullets.clear();
            particles.clear();
            rings.clear();
            bolts.clear();
            texts.clear();
            pickups.clear();
            shake = 0;
            slowMotion = 0;
            hitStop = 0;
            announceTimer = 0;
            state = State.PLAYING;
        }

        void nextWave() {
            wave++;
            clearing = false;
            hitThisWave = false;
            bestWave = Math.max(bestWave, wave);
            shipBestWave[ship.ordinal()] = Math.max(shipBestWave[ship.ordinal()], wave);
            checkAchievements();
            spawnQueue.clear();
            if (wave % 5 == 0) {
                EnemyType[] bosses = {EnemyType.OVERLORD, EnemyType.SERPENT, EnemyType.WARDEN};
                EnemyType boss = bosses[(wave / 5 - 1) % bosses.length];
                spawnQueue.add(boss);
                for (int i = 0; i < wave / 5 * 3; i++) {
                    spawnQueue.add(EnemyType.CHASER);
                }
                banner = boss == EnemyType.OVERLORD ? "THE OVERLORD" : boss == EnemyType.SERPENT ? "THE SERPENT" : "THE WARDEN";
                sound.play(Sound.Effect.BOSS);
            } else {
                int count = (int) (Math.min(60, 6 + wave * 3) * difficulty.enemyCount * modifier.enemyCount);
                for (int i = 0; i < count; i++) {
                    spawnQueue.add(rollEnemy());
                }
                banner = "WAVE " + wave;
                if ((wave - 1) % 5 == 0 && wave > 1) {
                    banner = currentTheme().name.toUpperCase();
                }
                sound.play(Sound.Effect.WAVE);
            }
            spawnDelay = Math.max(0.16, 0.8 - wave * 0.05);
            spawnTimer = 0.9;
            bannerTimer = 1.7;
            saveRun();
        }

        Theme currentTheme() {
            return themes[(Math.max(1, wave) - 1) / 5 % themes.length];
        }

        private EnemyType rollEnemy() {
            int roll = rng.nextInt(100);
            if (wave >= 6 && roll < 8) return EnemyType.TANK;
            if (wave >= 7 && roll < 17) return EnemyType.GHOST;
            if (wave >= 4 && roll < 30) return EnemyType.SHOOTER;
            if (wave >= 3 && roll < 42) return EnemyType.BOMBER;
            if (wave >= 3 && roll < 56) return EnemyType.SPLITTER;
            if (wave >= 2 && roll < 74) return EnemyType.SPEEDER;
            return EnemyType.CHASER;
        }

        private double healthScale() {
            // Past wave 12 enemies get 15% tougher every wave, so no build can last forever.
            int late = Math.max(0, wave - 12);
            return difficulty.enemyHealth * modifier.enemyHealth * (1 + 0.1 * (wave - 1)) * Math.pow(1.15, late);
        }

        private void spawnEnemy(EnemyType type) {
            double x = 0, y = 0;
            for (int tries = 0; tries < 25; tries++) {
                x = 50 + rng.nextDouble() * (W - 100);
                y = 50 + rng.nextDouble() * (H - 100);
                if (Math.hypot(x - px, y - py) > 230) {
                    break;
                }
            }
            double hp = type.hp * healthScale();
            if (type.isBoss()) {
                x = W / 2.0;
                y = py > H / 2.0 ? 130 : H - 130;
                int late = Math.max(0, wave - 12);
                hp = type.hp * difficulty.enemyHealth * modifier.enemyHealth * (1 + 0.35 * (wave / 5 - 1)) * Math.pow(1.12, late);
                sound.setMusic(Sound.Track.BOSS);
            }
            Enemy enemy = new Enemy(type, x, y, hp);
            enemy.spawnTotal = type.isBoss() ? 1.2 : 0.7;
            enemy.spawnTimer = enemy.spawnTotal;
            enemy.attackTimer = 1 + rng.nextDouble() * 1.5;
            enemy.wobble = rng.nextDouble() * 10;
            enemy.angle = rng.nextDouble() * Math.PI * 2;
            double eliteChance = (wave >= 6 ? Math.min(0.2, 0.05 + wave * 0.004) : 0) + modifier.eliteBonus;
            if (!type.isBoss() && rng.nextDouble() < eliteChance) {
                enemy.elite = true;
                enemy.r = type.radius * 1.35;
                enemy.hp *= 3;
                enemy.maxHp = enemy.hp;
            }
            newEnemies.add(enemy);
            if (type == EnemyType.SERPENT) {
                Enemy previous = enemy;
                for (int i = 0; i < 11; i++) {
                    Enemy segment = new Enemy(EnemyType.SEGMENT, x, y, 1);
                    segment.owner = enemy;
                    segment.previous = previous;
                    segment.spawnTotal = enemy.spawnTotal;
                    segment.spawnTimer = enemy.spawnTotal;
                    segment.r = EnemyType.SEGMENT.radius - i * 0.9;
                    newEnemies.add(segment);
                    previous = segment;
                }
            }
        }

        // ============================== UPDATE ==============================

        void update(double dt) {
            clock += dt;
            shake = Math.max(0, shake - 40 * dt);
            hurtFlash = Math.max(0, hurtFlash - dt);
            bannerTimer = Math.max(0, bannerTimer - dt);
            cardDelay = Math.max(0, cardDelay - dt);
            overDelay = Math.max(0, overDelay - dt);
            announceTimer = Math.max(0, announceTimer - dt);
            noticeTimer = Math.max(0, noticeTimer - dt);
            multiplierPulse = Math.max(0, multiplierPulse - dt);
            displayScore += (score - displayScore) * Math.min(1, dt * 12);
            updateToasts(dt);
            syncMusic();

            if (state == State.PAUSED) {
                return;
            }
            if (hitStop > 0) {
                hitStop -= dt;
                return;
            }
            double worldDt = dt;
            if (slowMotion > 0) {
                slowMotion -= dt;
                worldDt = dt * 0.3;
            }
            updateEffects(worldDt);
            if (state == State.PLAYING) {
                step(worldDt);
            }
        }

        private void syncMusic() {
            if (state == State.MENU || state == State.HANGAR || state == State.RECORDS || state == State.SKINS) {
                sound.setMusic(Sound.Track.MENU);
            } else if (state == State.GAME_OVER) {
                sound.setMusic(Sound.Track.NONE);
            } else if (state == State.PLAYING || state == State.UPGRADE) {
                sound.setMusic(bossAlive() ? Sound.Track.BOSS : Sound.Track.GAME);
            }
        }

        boolean bossAlive() {
            for (Enemy e : enemies) {
                if (e.type.isBoss() && !e.dead) {
                    return true;
                }
            }
            return false;
        }

        private void step(double dt) {
            runTime += dt;
            updatePlayer(dt);
            updateDrones(dt);
            updateOrbitals(dt);
            updateBullets(dt);
            updateEnemies(dt);
            updatePickups(dt);
            updateWave(dt);
            enemies.addAll(newEnemies);
            newEnemies.clear();
            bullets.addAll(newBullets);
            newBullets.clear();
            removeDead();
            comboTimer -= dt;
            if (comboTimer <= 0) {
                combo = 0;
            }
        }

        private void removeDead() {
            for (Iterator<Enemy> it = enemies.iterator(); it.hasNext(); ) {
                if (it.next().dead) {
                    it.remove();
                }
            }
            for (Iterator<Bullet> it = bullets.iterator(); it.hasNext(); ) {
                if (it.next().dead) {
                    it.remove();
                }
            }
        }

        private void updatePlayer(double dt) {
            invulnerable = Math.max(0, invulnerable - dt);
            dashCooldown = Math.max(0, dashCooldown - dt);
            overdriveTimer = Math.max(0, overdriveTimer - dt);
            warpTimer = Math.max(0, warpTimer - dt);
            fortressTimer = Math.max(0, fortressTimer - dt);
            fireCooldown -= dt;
            recoil = Math.max(0, recoil - 40 * dt);
            facing = Math.atan2(aimY - py, aimX - px);

            double vx, vy;
            if (dashTimer > 0) {
                dashTimer -= dt;
                vx = dashDirX * DASH_SPEED;
                vy = dashDirY * DASH_SPEED;
                addParticle(px, py, 0, 0, 0.25, 9, shipColor());
                Afterimage image = new Afterimage();
                image.x = px;
                image.y = py;
                image.angle = facing;
                image.life = AFTERIMAGE_LIFE;
                afterimages.add(image);
            } else {
                double dx = (right ? 1 : 0) - (left ? 1 : 0);
                double dy = (down ? 1 : 0) - (up ? 1 : 0);
                double length = Math.hypot(dx, dy);
                vx = length == 0 ? 0 : dx / length * moveSpeed;
                vy = length == 0 ? 0 : dy / length * moveSpeed;
                if (length > 0 && fx.nextDouble() < 0.5) {
                    addParticle(px - dx / length * 12, py - dy / length * 12,
                            -vx * 0.2 + (fx.nextDouble() - 0.5) * 40, -vy * 0.2 + (fx.nextDouble() - 0.5) * 40,
                            0.25, 3.5, shipColor());
                }
            }
            px = clamp(px + vx * dt, 22, W - 22);
            py = clamp(py + vy * dt, 22, H - 22);

            if ((firing || autoFire) && fireCooldown <= 0) {
                shoot();
            }
            if (fortressTimer > 0) {
                for (Enemy e : enemies) {
                    if (!e.dead && e.spawnTimer <= 0 && Math.hypot(e.x - px, e.y - py) < 90 + e.r && e.contactCooldown <= 0) {
                        e.contactCooldown = 0.25;
                        damage(e, 2 + bulletDamage, false);
                    }
                }
            }
        }

        private void shoot() {
            fireCooldown = overdriveTimer > 0 ? fireDelay / 3 : fireDelay;
            recoil = ship == Ship.TITAN || ship == Ship.BLASTER ? 5 : 2.5;
            for (int i = 0; i < shots; i++) {
                double angle = facing + (i - (shots - 1) / 2.0) * ship.spread;
                addPlayerBullet(px + Math.cos(angle) * 18, py + Math.sin(angle) * 18, angle, bulletDamage, false);
            }
            addParticle(px + Math.cos(facing) * 20, py + Math.sin(facing) * 20,
                    Math.cos(facing) * 60, Math.sin(facing) * 60, 0.08, 8, Color.WHITE);
            sound.play(Sound.Effect.SHOOT);
        }

        private void addPlayerBullet(double x, double y, double angle, double amount, boolean fromDrone) {
            Bullet b = new Bullet();
            b.x = x;
            b.y = y;
            // The first hit check starts at the gun, so enemies touching the ship can still be shot.
            b.sweepX = fromDrone ? x : px;
            b.sweepY = fromDrone ? y : py;
            b.vx = Math.cos(angle) * 680;
            b.vy = Math.sin(angle) * 680;
            b.r = fromDrone ? 3.5 : bulletRadius;
            b.damage = amount;
            // Drones aim up to 460 away, so their bullets must always fly at least that far.
            b.life = fromDrone ? Math.max(ship.bulletLife, 0.8) : ship.bulletLife;
            b.pierce = pierce;
            b.bounces = bounces;
            b.drone = fromDrone;
            b.color = fromDrone ? new Color(180, 255, 200) : brighten(shipColor());
            newBullets.add(b);
        }

        private static Color brighten(Color c) {
            return new Color((c.getRed() + 255) / 2, (c.getGreen() + 255) / 2, (c.getBlue() + 255) / 2);
        }

        void tryDash() {
            if (state != State.PLAYING || dashCooldown > 0 || dashTimer > 0) {
                return;
            }
            double dx = (right ? 1 : 0) - (left ? 1 : 0);
            double dy = (down ? 1 : 0) - (up ? 1 : 0);
            if (dx == 0 && dy == 0) {
                dx = Math.cos(facing);
                dy = Math.sin(facing);
            }
            double length = Math.hypot(dx, dy);
            dashDirX = dx / length;
            dashDirY = dy / length;
            dashTimer = DASH_TIME;
            dashCooldown = dashDelay;
            addRing(px, py, 46, 0.2, shipColor());
            burst(px, py, 10, shipColor(), 220);
            sound.play(Sound.Effect.DASH);
        }

        void useBomb() {
            if (state != State.PLAYING || bombs <= 0) {
                return;
            }
            bombs--;
            blast(px, py, 40);
            sound.play(Sound.Effect.BIG_BOOM);
        }

        /** Screen-clearing explosion used by bombs and Second Life. */
        private void blast(double x, double y, double amount) {
            addRing(x, y, 900, 0.6, new Color(255, 240, 180));
            addFlash(x, y, 520, 0.3, new Color(255, 245, 210));
            burst(x, y, 80, new Color(255, 220, 120), 500);
            addShards(x, y, 24, new Color(255, 220, 120), 560);
            shake = Math.max(shake, 26);
            slowMotion = Math.max(slowMotion, 0.25);
            clearEnemyBullets();
            for (Enemy e : enemies) {
                if (!e.dead && e.spawnTimer <= 0 && e.type != EnemyType.SEGMENT) {
                    damage(e, amount, false);
                }
            }
        }

        private void clearEnemyBullets() {
            for (Bullet b : bullets) {
                if (b.enemy) {
                    b.dead = true;
                }
            }
            for (Bullet b : newBullets) {
                if (b.enemy) {
                    b.dead = true;
                }
            }
        }

        void useSpecial() {
            if (state != State.PLAYING) {
                return;
            }
            if (specialCharge < 1) {
                sound.play(Sound.Effect.DENY);
                return;
            }
            specialCharge = 0;
            sound.play(Sound.Effect.SPECIAL);
            shake = Math.max(shake, 12);
            addRing(px, py, 260, 0.45, shipColor());
            switch (ship.special) {
                case OVERDRIVE:
                    overdriveTimer = SPECIAL_TIME_OVERDRIVE;
                    announce("OVERDRIVE!", shipColor());
                    break;
                case NOVA:
                    for (int i = 0; i < 40; i++) {
                        addPlayerBullet(px, py, i * Math.PI * 2 / 40, Math.max(2, bulletDamage * 2), false);
                    }
                    for (Enemy e : enemies) {
                        if (e.type != EnemyType.SEGMENT && !e.type.isBoss()) {
                            double d = Math.max(1, Math.hypot(e.x - px, e.y - py));
                            if (d < 300) {
                                e.x = clamp(e.x + (e.x - px) / d * 90, e.r, W - e.r);
                                e.y = clamp(e.y + (e.y - py) / d * 90, e.r, H - e.r);
                            }
                        }
                    }
                    announce("NOVA!", shipColor());
                    break;
                case TIME_WARP:
                    warpTimer = SPECIAL_TIME_WARP;
                    announce("TIME WARP!", shipColor());
                    break;
                default:
                    fortressTimer = SPECIAL_TIME_FORTRESS;
                    announce("FORTRESS!", shipColor());
                    break;
            }
        }

        private void updateDrones(double dt) {
            for (int i = 0; i < drones.size(); i++) {
                Drone drone = drones.get(i);
                double angle = -clock * 1.4 + i * Math.PI * 2 / drones.size();
                double tx = px + Math.cos(angle) * 46, ty = py + Math.sin(angle) * 46;
                drone.x += (tx - drone.x) * Math.min(1, dt * 7);
                drone.y += (ty - drone.y) * Math.min(1, dt * 7);
                drone.cooldown -= dt;
                Enemy target = nearestEnemy(drone.x, drone.y, 460, null);
                if (target != null) {
                    drone.facing = Math.atan2(target.y - drone.y, target.x - drone.x);
                    if (drone.cooldown <= 0) {
                        drone.cooldown = 0.5;
                        addPlayerBullet(drone.x, drone.y, drone.facing, Math.max(1, bulletDamage * 0.7), true);
                    }
                }
            }
        }

        private Enemy nearestEnemy(double x, double y, double range, List<Enemy> exclude) {
            Enemy nearest = null;
            double best = range;
            for (Enemy e : enemies) {
                if (!e.dead && e.spawnTimer <= 0 && (exclude == null || !exclude.contains(e))) {
                    double d = Math.hypot(e.x - x, e.y - y);
                    if (d < best) {
                        best = d;
                        nearest = e;
                    }
                }
            }
            return nearest;
        }

        private void updateOrbitals(double dt) {
            orbitAngle += dt * 3.2;
            for (int i = 0; i < orbitals; i++) {
                double angle = orbitAngle + i * Math.PI * 2 / orbitals;
                double ox = px + Math.cos(angle) * 72, oy = py + Math.sin(angle) * 72;
                for (Enemy e : enemies) {
                    if (!e.dead && e.spawnTimer <= 0 && e.contactCooldown <= 0 && Math.hypot(e.x - ox, e.y - oy) < e.r + 10) {
                        e.contactCooldown = 0.3;
                        damage(e, 1.5 + bulletDamage, false);
                    }
                }
                for (Bullet b : bullets) {
                    if (b.enemy && !b.dead && Math.hypot(b.x - ox, b.y - oy) < b.r + 11) {
                        b.dead = true;
                        burst(b.x, b.y, 5, b.color, 120);
                    }
                }
            }
        }

        private double enemyTimeScale() {
            return warpTimer > 0 ? 0.3 : 1;
        }

        private void updateBullets(double dt) {
            double enemyDt = dt * enemyTimeScale();
            for (Bullet b : bullets) {
                if (b.dead) {
                    continue;
                }
                double step = b.enemy ? enemyDt : dt;
                b.life -= step;
                if (b.life <= 0) {
                    b.dead = true;
                    continue;
                }
                if (!b.enemy && homing > 0) {
                    steerBullet(b, dt);
                }
                b.x += b.vx * step;
                b.y += b.vy * step;
                boolean hitWall = false;
                if (b.x < b.r || b.x > W - b.r) {
                    b.vx = -b.vx;
                    b.x = clamp(b.x, b.r, W - b.r);
                    hitWall = true;
                }
                if (b.y < b.r || b.y > H - b.r) {
                    b.vy = -b.vy;
                    b.y = clamp(b.y, b.r, H - b.r);
                    hitWall = true;
                }
                if (hitWall) {
                    if (!b.enemy && b.bounces > 0) {
                        b.bounces--;
                        b.life = Math.max(b.life, 0.7);
                        b.alreadyHit = null;
                    } else {
                        b.dead = true;
                    }
                    b.sweepX = b.x;
                    b.sweepY = b.y;
                    burst(b.x, b.y, 4, b.color, 140);
                    continue;
                }
                if (b.enemy) {
                    if (Math.hypot(b.x - px, b.y - py) < b.r + PLAYER_RADIUS && canBeHurt()) {
                        b.dead = true;
                        hurtPlayer();
                    }
                } else {
                    hitEnemies(b);
                    b.sweepX = b.x;
                    b.sweepY = b.y;
                }
            }
        }

        /** How close (x, y) comes to the path this bullet travelled during this frame. */
        private static double distanceToPath(Bullet b, double x, double y) {
            double dx = b.x - b.sweepX, dy = b.y - b.sweepY;
            double lengthSquared = dx * dx + dy * dy;
            double t = lengthSquared == 0 ? 0 : clamp(((x - b.sweepX) * dx + (y - b.sweepY) * dy) / lengthSquared, 0, 1);
            return Math.hypot(b.sweepX + dx * t - x, b.sweepY + dy * t - y);
        }

        private void steerBullet(Bullet b, double dt) {
            Enemy nearest = nearestEnemy(b.x, b.y, 320, b.alreadyHit);
            if (nearest != null) {
                double speed = Math.hypot(b.vx, b.vy);
                double current = Math.atan2(b.vy, b.vx);
                double wanted = Math.atan2(nearest.y - b.y, nearest.x - b.x);
                double diff = Math.atan2(Math.sin(wanted - current), Math.cos(wanted - current));
                double turn = clamp(diff, -homing * dt, homing * dt);
                b.vx = Math.cos(current + turn) * speed;
                b.vy = Math.sin(current + turn) * speed;
            }
        }

        private void hitEnemies(Bullet b) {
            for (Enemy e : enemies) {
                if (e.dead || e.spawnTimer > 0 || (b.alreadyHit != null && b.alreadyHit.contains(e))) {
                    continue;
                }
                if (distanceToPath(b, e.x, e.y) < e.r + b.r) {
                    boolean crit = rng.nextDouble() < critChance;
                    double amount = b.damage * (crit ? 3 : 1);
                    damage(e, amount, crit);
                    knockBack(e, b);
                    if (frostTime > 0) {
                        e.slowTimer = frostTime;
                    }
                    burst(b.x, b.y, 3, e.type.color, 130);
                    if (explosiveRadius > 0 && !b.drone) {
                        explode(b.x, b.y, explosiveRadius, b.damage * 0.6);
                    }
                    if (chainTargets > 0 && !b.drone) {
                        chainLightning(e, b.damage * 0.5);
                    }
                    if (b.pierce > 0) {
                        b.pierce--;
                        if (b.alreadyHit == null) {
                            b.alreadyHit = new ArrayList<Enemy>();
                        }
                        b.alreadyHit.add(e);
                    } else {
                        b.dead = true;
                        return;
                    }
                }
            }
        }

        /** A small shove in the bullet's direction. Big enemies and elites barely move. */
        private void knockBack(Enemy e, Bullet b) {
            if (e.type.isBoss() || e.owner != null) {
                return;
            }
            double speed = Math.max(1, Math.hypot(b.vx, b.vy));
            double force = 110 * 14 / e.r * (e.elite ? 0.5 : 1);
            e.pushX += b.vx / speed * force;
            e.pushY += b.vy / speed * force;
            double total = Math.hypot(e.pushX, e.pushY);
            if (total > 260) {
                e.pushX *= 260 / total;
                e.pushY *= 260 / total;
            }
        }

        private void chainLightning(Enemy first, double amount) {
            List<Enemy> hit = new ArrayList<Enemy>();
            hit.add(first);
            Enemy from = first;
            for (int i = 0; i < chainTargets; i++) {
                Enemy next = nearestEnemy(from.x, from.y, 190, hit);
                if (next == null) {
                    break;
                }
                Bolt bolt = new Bolt();
                bolt.x1 = from.x;
                bolt.y1 = from.y;
                bolt.x2 = next.x;
                bolt.y2 = next.y;
                bolt.life = 0.14;
                bolts.add(bolt);
                damage(next, amount, false);
                sound.play(Sound.Effect.ZAP);
                hit.add(next);
                from = next;
            }
        }

        private void explode(double x, double y, double radius, double amount) {
            addRing(x, y, radius, 0.22, new Color(255, 170, 80));
            addFlash(x, y, radius * 0.75, 0.12, new Color(255, 215, 150));
            burst(x, y, 7, new Color(255, 170, 80), 170);
            for (Enemy e : enemies) {
                if (!e.dead && e.spawnTimer <= 0 && Math.hypot(e.x - x, e.y - y) < radius + e.r) {
                    damage(e, amount, false);
                }
            }
        }

        void damage(Enemy e, double amount, boolean crit) {
            if (e.dead) {
                return;
            }
            e.flash = 0.08;
            if (e.owner != null) {
                // Serpent body parts pass half the damage to the head.
                damage(e.owner, amount * 0.5, crit);
                return;
            }
            runDamage += Math.min(amount, Math.max(0, e.hp));
            if (!crit) {
                e.pendingDamage += amount;
            }
            e.hp -= amount;
            if (e.type.isBoss()) {
                // Worth about two specials per boss, however strong your weapon is.
                chargeSpecial(Math.min(amount, Math.max(0, e.hp + amount)) / e.maxHp * 2);
            }
            sound.play(Sound.Effect.HIT);
            if (crit) {
                addText(e.x, e.y - e.r, "CRIT " + (int) Math.ceil(amount), new Color(255, 240, 150), 18);
            }
            if (e.hp <= 0) {
                kill(e);
            }
        }

        /** No charging while a special is running, so specials can never stay on forever. */
        private void chargeSpecial(double amount) {
            if (overdriveTimer <= 0 && warpTimer <= 0 && fortressTimer <= 0) {
                specialCharge = Math.min(1, specialCharge + amount);
            }
        }

        private void kill(Enemy e) {
            e.dead = true;
            kills++;
            totalKills++;
            int oldMultiplier = multiplier();
            combo++;
            if (multiplier() != oldMultiplier) {
                multiplierPulse = 0.25;
            }
            bestCombo = Math.max(bestCombo, combo);
            bestComboEver = Math.max(bestComboEver, combo);
            comboTimer = 2.5;
            chargeSpecial(e.elite ? 0.15 : 0.04);
            announceCombo();

            int points = (int) (e.type.score * (e.elite ? 3 : 1) * multiplier() * difficulty.scoreMultiplier);
            score += points;
            addText(e.x, e.y, "+" + points, e.type.color, multiplier() > 2 ? 20 : 15);

            boolean boss = e.type.isBoss();
            burst(e.x, e.y, boss ? 220 : 14 + (int) e.r, e.type.color, boss ? 640 : 300);
            addRing(e.x, e.y, boss ? 560 : e.r * 3.5, boss ? 0.9 : 0.28, e.type.color);
            addFlash(e.x, e.y, boss ? 300 : e.r * 2.2, boss ? 0.5 : 0.13, brighten(e.type.color));
            addShards(e.x, e.y, boss ? 44 : 4 + (int) (e.r / 5), e.type.color, boss ? 560 : 250);
            shake = Math.max(shake, boss ? 38 : 3 + e.r * 0.28);
            if (e.elite) {
                hitStop = Math.max(hitStop, 0.035);
            }
            sound.play(boss ? Sound.Effect.BIG_BOOM : Sound.Effect.BOOM);

            if (vampire > 0 && ++killsSinceHeal >= 40) {
                killsSinceHeal = 0;
                if (health < maxHealth) {
                    health++;
                    addText(px, py - 22, "+1 HEALTH", new Color(255, 120, 140), 18);
                }
            }
            if (e.type == EnemyType.SPLITTER) {
                for (int i = 0; i < 3; i++) {
                    double angle = i * Math.PI * 2 / 3 + rng.nextDouble();
                    Enemy mini = new Enemy(EnemyType.MINI, e.x + Math.cos(angle) * 16, e.y + Math.sin(angle) * 16,
                            EnemyType.MINI.hp * healthScale());
                    mini.vx = Math.cos(angle) * 240;
                    mini.vy = Math.sin(angle) * 240;
                    mini.wobble = rng.nextDouble() * 10;
                    newEnemies.add(mini);
                }
            }
            if (e.type == EnemyType.BOMBER) {
                bomberExplosion(e);
            }
            if (boss) {
                bossDefeated(e);
            } else {
                dropLoot(e);
            }
            checkAchievements();
        }

        /** Bombers hurt everything nearby, so shoot them when they are next to their friends. */
        private void bomberExplosion(Enemy e) {
            double radius = 100;
            addRing(e.x, e.y, radius, 0.3, new Color(255, 150, 60));
            addFlash(e.x, e.y, radius * 0.8, 0.16, new Color(255, 210, 140));
            burst(e.x, e.y, 30, new Color(255, 150, 60), 360);
            shake = Math.max(shake, 12);
            if (Math.hypot(px - e.x, py - e.y) < radius - 12 && canBeHurt()) {
                hurtPlayer();
            }
            for (Enemy other : enemies) {
                if (!other.dead && other.spawnTimer <= 0 && other.owner == null
                        && Math.hypot(other.x - e.x, other.y - e.y) < radius + other.r) {
                    damage(other, 8, false);
                }
            }
        }

        private void bossDefeated(Enemy e) {
            slowMotion = 1.0;
            hitStop = 0.08;
            announce("BOSS DEFEATED!", e.type.color);
            runBosses++;
            totalBosses++;
            bossKills[e.type == EnemyType.OVERLORD ? 0 : e.type == EnemyType.SERPENT ? 1 : 2]++;
            unlock(Achievement.FLAWLESS, !hitThisWave);
            unlock(Achievement.NO_MERCY, difficulty == Difficulty.HARD);
            addPickup(PickupType.HEART, e.x - 30, e.y);
            addPickup(PickupType.BOMB, e.x + 30, e.y);
            for (int i = 0; i < 20; i++) {
                addCoin(e.x, e.y);
            }
            // Every boss drops 2 diamonds. They get pulled in when the wave clears, so none are lost.
            addPickup(PickupType.DIAMOND, e.x - 14, e.y + 30);
            addPickup(PickupType.DIAMOND, e.x + 14, e.y + 30);
            clearEnemyBullets();
            for (Enemy other : enemies) {
                if (other.owner == e && !other.dead) {
                    other.dead = true;
                    burst(other.x, other.y, 24, other.type.color, 360);
                    addRing(other.x, other.y, 70, 0.3, other.type.color);
                }
            }
            for (Enemy other : newEnemies) {
                if (other.owner == e) {
                    other.dead = true;
                }
            }
        }

        private void dropLoot(Enemy e) {
            if (e.elite) {
                for (int i = 0; i < 5; i++) {
                    addCoin(e.x, e.y);
                }
            } else if (rng.nextDouble() < 0.14) {
                addCoin(e.x, e.y);
            }
            double roll = rng.nextDouble();
            if (roll < 0.035) {
                addPickup(PickupType.HEART, e.x, e.y);
            } else if (roll < 0.05) {
                addPickup(PickupType.BOMB, e.x, e.y);
            }
            // Diamonds are rare: 1 in 20 elites and 1 in 250 normal enemies drop one.
            if (e.type != EnemyType.SEGMENT && rng.nextDouble() < (e.elite ? 0.05 : 0.004)) {
                addPickup(PickupType.DIAMOND, e.x, e.y);
            }
        }

        private void announceCombo() {
            if (combo == 15) {
                announce("KILLING SPREE!", new Color(120, 240, 255));
            } else if (combo == 35) {
                announce("RAMPAGE!", new Color(255, 200, 80));
            } else if (combo == 60) {
                announce("UNSTOPPABLE!", new Color(255, 120, 90));
            } else if (combo == 100) {
                announce("GODLIKE!", new Color(255, 90, 220));
            }
        }

        private void announce(String text, Color color) {
            announcement = text;
            announceColor = color;
            announceTimer = 1.4;
        }

        int multiplier() {
            return Math.min(8, 1 + combo / 8);
        }

        private void updateEnemies(double dt) {
            double lateBoost = 1 + Math.min(0.7, Math.max(0, wave - 12) * 0.05);
            for (Enemy e : enemies) {
                if (e.dead) {
                    continue;
                }
                e.flash = Math.max(0, e.flash - dt);
                e.contactCooldown = Math.max(0, e.contactCooldown - dt);
                e.slowTimer = Math.max(0, e.slowTimer - dt);
                showPendingDamage(e, dt);
                if (e.spawnTimer > 0) {
                    e.spawnTimer -= dt;
                    continue;
                }
                double moveDt = dt * enemyTimeScale() * lateBoost * difficulty.enemySpeed * modifier.enemySpeed
                        * (e.slowTimer > 0 ? 0.55 : 1);
                e.angle += moveDt * 2.5;
                moveEnemy(e, moveDt);
                if (e.dead) {
                    continue;
                }
                e.x += e.pushX * dt;
                e.y += e.pushY * dt;
                e.pushX *= Math.max(0, 1 - 8 * dt);
                e.pushY *= Math.max(0, 1 - 8 * dt);
                separate(e, moveDt);
                e.x = clamp(e.x, e.r, W - e.r);
                e.y = clamp(e.y, e.r, H - e.r);

                if (Math.hypot(e.x - px, e.y - py) < e.r + PLAYER_RADIUS && e.blinkWarn <= 0) {
                    if (dashTimer > 0 && dashDamage > 0 && e.contactCooldown <= 0) {
                        e.contactCooldown = 0.3;
                        damage(e, dashDamage, false);
                    } else if (canBeHurt()) {
                        hurtPlayer();
                    }
                }
            }
        }

        /** Adds up rapid hits into one number, so fast weapons do not fill the screen with text. */
        private void showPendingDamage(Enemy e, double dt) {
            e.damageTextTimer -= dt;
            if (e.pendingDamage > 0 && e.damageTextTimer <= 0) {
                e.damageTextTimer = 0.14;
                if (texts.size() <= 28) {
                    String number = String.valueOf(Math.max(1, Math.round(e.pendingDamage)));
                    addText(e.x + (fx.nextDouble() - 0.5) * e.r, e.y - e.r * 0.7, number,
                            new Color(235, 240, 255), e.type.isBoss() ? 16 : 13);
                    texts.get(texts.size() - 1).life = 0.45;
                }
                e.pendingDamage = 0;
            }
        }

        private void moveEnemy(Enemy e, double dt) {
            switch (e.type) {
                case SPEEDER:
                    moveSpeeder(e, dt);
                    break;
                case SHOOTER:
                    moveShooter(e, dt);
                    break;
                case BOMBER:
                    moveBomber(e, dt);
                    break;
                case GHOST:
                    moveGhost(e, dt);
                    break;
                case OVERLORD:
                    moveOverlord(e, dt);
                    break;
                case SERPENT:
                    moveSerpent(e, dt);
                    break;
                case SEGMENT:
                    moveSegment(e);
                    break;
                case WARDEN:
                    moveWarden(e, dt);
                    break;
                default:
                    moveChaser(e, dt);
                    break;
            }
        }

        private void moveChaser(Enemy e, double dt) {
            double dx = px - e.x, dy = py - e.y, d = Math.max(1, Math.hypot(dx, dy));
            // A little sideways weave makes groups spread out and look alive.
            double weave = Math.sin(clock * 3 + e.wobble) * 0.45;
            double wantX = (dx / d - dy / d * weave) * e.type.speed;
            double wantY = (dy / d + dx / d * weave) * e.type.speed;
            e.vx += (wantX - e.vx) * Math.min(1, dt * 5);
            e.vy += (wantY - e.vy) * Math.min(1, dt * 5);
            e.x += e.vx * dt;
            e.y += e.vy * dt;
        }

        /** Builds up speed and overshoots, so a sidestep or a dash dodges it. */
        private void moveSpeeder(Enemy e, double dt) {
            double dx = px - e.x, dy = py - e.y, d = Math.max(1, Math.hypot(dx, dy));
            e.vx += dx / d * 620 * dt;
            e.vy += dy / d * 620 * dt;
            double speed = Math.hypot(e.vx, e.vy);
            if (speed > e.type.speed) {
                e.vx *= e.type.speed / speed;
                e.vy *= e.type.speed / speed;
            }
            e.x += e.vx * dt;
            e.y += e.vy * dt;
            e.angle = Math.atan2(e.vy, e.vx);
            if (e.x <= e.r || e.x >= W - e.r) {
                e.vx = -e.vx * 0.6;
            }
            if (e.y <= e.r || e.y >= H - e.r) {
                e.vy = -e.vy * 0.6;
            }
        }

        private void moveShooter(Enemy e, double dt) {
            double dx = px - e.x, dy = py - e.y, d = Math.max(1, Math.hypot(dx, dy));
            double direction = d > 310 ? 1 : d < 230 ? -1 : 0;
            e.x += dx / d * direction * e.type.speed * dt - dy / d * 35 * dt;
            e.y += dy / d * direction * e.type.speed * dt + dx / d * 35 * dt;
            e.angle = Math.atan2(dy, dx);
            e.attackTimer -= dt;
            if (e.attackTimer <= 0) {
                e.attackTimer = Math.max(1.3, 2.4 - wave * 0.04);
                fireEnemyBullet(e.x, e.y, e.angle, 245, e.type.color);
            }
        }

        private void moveBomber(Enemy e, double dt) {
            if (e.fuse > 0) {
                e.fuse -= dt;
                e.flash = (int) (clock * 16) % 2 == 0 ? 0.05 : 0;
                if (e.fuse <= 0) {
                    kill(e);
                }
                return;
            }
            moveChaser(e, dt);
            if (Math.hypot(px - e.x, py - e.y) < 85) {
                e.fuse = 0.65;
            }
        }

        /** Blinks to a new spot near the player. A ring shows where it will appear. */
        private void moveGhost(Enemy e, double dt) {
            if (e.blinkWarn > 0) {
                e.blinkWarn -= dt;
                if (e.blinkWarn <= 0) {
                    burst(e.x, e.y, 12, e.type.color, 160);
                    e.x = e.warpX;
                    e.y = e.warpY;
                    burst(e.x, e.y, 12, e.type.color, 160);
                    e.attackTimer = 2.2 + rng.nextDouble();
                }
                return;
            }
            moveChaser(e, dt);
            e.attackTimer -= dt;
            if (e.attackTimer <= 0) {
                double angle = rng.nextDouble() * Math.PI * 2;
                double distance = 150 + rng.nextDouble() * 70;
                e.warpX = clamp(px + Math.cos(angle) * distance, 30, W - 30);
                e.warpY = clamp(py + Math.sin(angle) * distance, 30, H - 30);
                e.blinkWarn = 0.6;
            }
        }

        private void moveOverlord(Enemy e, double dt) {
            double rage = e.hp < e.maxHp / 2 ? 1.5 : 1;
            if (e.chargeWarn > 0) {
                e.chargeWarn -= dt;
                if (e.chargeWarn <= 0) {
                    e.chargeTime = 0.55;
                    double d = Math.max(1, Math.hypot(e.aimX - e.x, e.aimY - e.y));
                    e.vx = (e.aimX - e.x) / d * 720;
                    e.vy = (e.aimY - e.y) / d * 720;
                    shake = Math.max(shake, 8);
                    sound.play(Sound.Effect.DASH);
                }
                return;
            }
            if (e.chargeTime > 0) {
                e.chargeTime -= dt;
                e.x += e.vx * dt;
                e.y += e.vy * dt;
                addParticle(e.x, e.y, 0, 0, 0.3, 24, e.type.color);
                return;
            }
            if (e.spiralTime > 0) {
                e.spiralTime -= dt;
                e.attackTimer -= dt;
                if (e.attackTimer <= 0) {
                    e.attackTimer = 0.1;
                    e.aimX += 0.5;
                    for (int i = 0; i < 3; i++) {
                        fireEnemyBullet(e.x, e.y, e.aimX + i * Math.PI * 2 / 3, 190, e.type.color);
                    }
                }
                if (e.spiralTime <= 0) {
                    e.attackTimer = 1.4 / rage;
                }
                return;
            }
            double dx = px - e.x, dy = py - e.y, d = Math.max(1, Math.hypot(dx, dy));
            e.x += dx / d * e.type.speed * rage * dt;
            e.y += dy / d * e.type.speed * rage * dt;
            e.attackTimer -= dt;
            if (e.attackTimer > 0) {
                return;
            }
            e.attackTimer = 2.3 / rage;
            switch (e.bossStep++ % 4) {
                case 0:
                    radialBurst(e, 16 + wave / 5 * 2, 200);
                    break;
                case 1:
                    e.chargeWarn = 0.75;
                    e.aimX = px;
                    e.aimY = py;
                    break;
                case 2:
                    summon(e, EnemyType.CHASER, 4);
                    break;
                default:
                    e.spiralTime = 2.2;
                    e.attackTimer = 0;
                    e.aimX = rng.nextDouble() * Math.PI * 2;
                    break;
            }
        }

        private void radialBurst(Enemy e, int count, double speed) {
            double offset = rng.nextDouble() * Math.PI;
            for (int i = 0; i < count; i++) {
                fireEnemyBullet(e.x, e.y, offset + i * Math.PI * 2 / count, speed, e.type.color);
            }
            addRing(e.x, e.y, 130, 0.3, e.type.color);
            sound.play(Sound.Effect.BOOM);
        }

        private void summon(Enemy e, EnemyType type, int count) {
            for (int i = 0; i < count && enemies.size() + newEnemies.size() < MAX_ENEMIES; i++) {
                double angle = i * Math.PI * 2 / count + Math.PI / 4;
                Enemy minion = new Enemy(type, clamp(e.x + Math.cos(angle) * 90, 30, W - 30),
                        clamp(e.y + Math.sin(angle) * 90, 30, H - 30), type.hp * healthScale());
                minion.spawnTotal = 0.5;
                minion.spawnTimer = 0.5;
                minion.attackTimer = 1 + rng.nextDouble();
                minion.wobble = rng.nextDouble() * 10;
                newEnemies.add(minion);
            }
        }

        /** A long worm that hunts the player. Hit the head for full damage, or the body for half. */
        private void moveSerpent(Enemy e, double dt) {
            double rage = e.hp < e.maxHp / 2 ? 1.3 : 1;
            double wanted = Math.atan2(py - e.y, px - e.x) + Math.sin(clock * 2.6) * 0.7;
            double diff = Math.atan2(Math.sin(wanted - e.aimX), Math.cos(wanted - e.aimX));
            e.aimX += clamp(diff, -2.4 * dt, 2.4 * dt);
            e.x += Math.cos(e.aimX) * e.type.speed * rage * dt;
            e.y += Math.sin(e.aimX) * e.type.speed * rage * dt;
            e.angle = e.aimX;
            e.attackTimer -= dt;
            if (e.attackTimer <= 0) {
                e.attackTimer = 3.0 / rage;
                int index = 0;
                for (Enemy part : enemies) {
                    if (part.owner == e && !part.dead && index++ % 2 == 0) {
                        double angle = Math.atan2(py - part.y, px - part.x);
                        fireEnemyBullet(part.x, part.y, angle, 170, e.type.color);
                    }
                }
                sound.play(Sound.Effect.BOOM);
            }
        }

        private void moveSegment(Enemy e) {
            Enemy leader = e.previous;
            double dx = leader.x - e.x, dy = leader.y - e.y, d = Math.hypot(dx, dy);
            double spacing = (leader.r + e.r) * 0.8;
            if (d > spacing) {
                e.x += dx / d * (d - spacing);
                e.y += dy / d * (d - spacing);
            }
            e.angle = Math.atan2(dy, dx);
        }

        /** Sits in the arena and sweeps deadly laser beams. Beams flicker as a warning first. */
        private void moveWarden(Enemy e, double dt) {
            double rage = e.hp < e.maxHp / 2 ? 1.35 : 1;
            if (e.beamWarn > 0) {
                e.beamWarn -= dt;
                e.beamAngle += e.beamSpin * 0.25 * dt;
                if (e.beamWarn <= 0) {
                    e.beamTime = 4;
                    shake = Math.max(shake, 8);
                    sound.play(Sound.Effect.SPECIAL);
                }
                return;
            }
            if (e.beamTime > 0) {
                e.beamTime -= dt;
                e.beamAngle += e.beamSpin * dt;
                if (touchingBeam(e) && canBeHurt()) {
                    hurtPlayer();
                }
                if (e.beamTime <= 0) {
                    e.attackTimer = 1.6 / rage;
                }
                return;
            }
            double dx = px - e.x, dy = py - e.y, d = Math.max(1, Math.hypot(dx, dy));
            e.x += dx / d * e.type.speed * rage * dt;
            e.y += dy / d * e.type.speed * rage * dt;
            e.attackTimer -= dt;
            if (e.attackTimer > 0) {
                return;
            }
            e.attackTimer = 2 / rage;
            switch (e.bossStep++ % 3) {
                case 0:
                    e.beamCount = rage > 1 ? 3 : 2;
                    e.beamAngle = rng.nextDouble() * Math.PI * 2;
                    e.beamSpin = (rng.nextBoolean() ? 1 : -1) * 0.75 * rage;
                    e.beamWarn = 1.1;
                    break;
                case 1:
                    radialBurst(e, 20, 180);
                    break;
                default:
                    summon(e, EnemyType.BOMBER, 3);
                    break;
            }
        }

        /** Each beam shoots out from the Warden in one direction, so there are safe gaps. */
        boolean touchingBeam(Enemy e) {
            double dx = px - e.x, dy = py - e.y;
            for (int i = 0; i < e.beamCount; i++) {
                double angle = e.beamAngle + i * Math.PI * 2 / e.beamCount;
                double along = dx * Math.cos(angle) + dy * Math.sin(angle);
                double across = Math.abs(-dx * Math.sin(angle) + dy * Math.cos(angle));
                if (along > 0 && across < 15 + PLAYER_RADIUS * 0.6) {
                    return true;
                }
            }
            return false;
        }

        private void separate(Enemy e, double dt) {
            if (e.type.isBoss() || e.type == EnemyType.SEGMENT) {
                return;
            }
            for (Enemy other : enemies) {
                if (other == e || other.dead || other.spawnTimer > 0) {
                    continue;
                }
                double dx = e.x - other.x, dy = e.y - other.y;
                double minimum = e.r + other.r;
                if (Math.abs(dx) < minimum && Math.abs(dy) < minimum) {
                    double d = Math.hypot(dx, dy);
                    if (d < minimum && d > 0.01) {
                        double push = (minimum - d) * 3 * dt;
                        e.x += dx / d * push;
                        e.y += dy / d * push;
                    }
                }
            }
        }

        private void fireEnemyBullet(double x, double y, double angle, double speed, Color color) {
            Bullet b = new Bullet();
            b.x = x;
            b.y = y;
            b.vx = Math.cos(angle) * speed * modifier.bulletSpeed;
            b.vy = Math.sin(angle) * speed * modifier.bulletSpeed;
            b.r = 6.5;
            b.life = 6;
            b.enemy = true;
            b.color = color;
            newBullets.add(b);
        }

        boolean canBeHurt() {
            return invulnerable <= 0 && dashTimer <= 0 && fortressTimer <= 0 && state == State.PLAYING;
        }

        private void hurtPlayer() {
            health--;
            hitThisWave = true;
            hitStop = Math.max(hitStop, 0.05);
            invulnerable = 1.3;
            hurtFlash = 0.35;
            shake = Math.max(shake, 20);
            slowMotion = Math.max(slowMotion, 0.2);
            combo = 0;
            sound.play(Sound.Effect.HURT);
            burst(px, py, 26, new Color(255, 90, 110), 320);
            if (health <= 0) {
                if (secondLives > 0) {
                    secondLives--;
                    health = Math.min(3, maxHealth);
                    invulnerable = 3;
                    announce("SECOND LIFE!", new Color(255, 190, 60));
                    blast(px, py, 60);
                    sound.play(Sound.Effect.BIG_BOOM);
                } else {
                    gameOver();
                }
            }
        }

        private void gameOver() {
            burst(px, py, 200, shipColor(), 560);
            addRing(px, py, 420, 0.8, shipColor());
            shake = 36;
            sound.play(Sound.Effect.BIG_BOOM);
            endRun();
            releaseInput();
            overDelay = 0.9;
            state = State.GAME_OVER;
        }

        /** Keeps the coins and the record from this run. */
        void endRun() {
            if (!runActive) {
                return;
            }
            runActive = false;
            deleteRunFile();
            coins += runCoins;
            diamonds += runDiamonds;
            totalRuns++;
            playSeconds += runTime;
            if (dailyRun) {
                // Daily scores use different rules, so they get their own record.
                finishDaily();
            } else if (score > highScore) {
                highScore = score;
                newRecord = true;
            }
            checkAchievements();
            save();
        }

        private void updatePickups(double dt) {
            for (Iterator<Pickup> it = pickups.iterator(); it.hasNext(); ) {
                Pickup p = it.next();
                p.life -= dt;
                p.x = clamp(p.x + p.vx * dt, 20, W - 20);
                p.y = clamp(p.y + p.vy * dt, 20, H - 20);
                p.vx *= 1 - 3 * dt;
                p.vy *= 1 - 3 * dt;
                double d = Math.max(1, Math.hypot(px - p.x, py - p.y));
                double range = p.type == PickupType.COIN || p.type == PickupType.DIAMOND ? 170 : 130;
                if (d < range || clearing) {
                    double pull = clearing ? 900 : 360;
                    p.x += (px - p.x) / d * pull * dt;
                    p.y += (py - p.y) / d * pull * dt;
                }
                if (d < 26) {
                    collect(p);
                    it.remove();
                } else if (p.life <= 0) {
                    it.remove();
                }
            }
        }

        private void collect(Pickup p) {
            switch (p.type) {
                case HEART:
                    if (health < maxHealth) {
                        health++;
                        addText(px, py - 22, "+1 HEALTH", new Color(255, 120, 140), 18);
                    } else {
                        score += 100;
                        addText(px, py - 22, "+100", new Color(255, 120, 140), 18);
                    }
                    sound.play(Sound.Effect.PICKUP);
                    break;
                case BOMB:
                    bombs++;
                    addText(px, py - 22, "+1 BOMB", new Color(255, 200, 120), 18);
                    sound.play(Sound.Effect.PICKUP);
                    break;
                case DIAMOND:
                    runDiamonds++;
                    addText(px, py - 22, "+1 DIAMOND", DIAMOND_COLOR, 20);
                    addRing(px, py, 50, 0.3, DIAMOND_COLOR);
                    sound.play(Sound.Effect.UPGRADE);
                    break;
                default:
                    earnCoins(1);
                    sound.play(Sound.Effect.COIN);
                    break;
            }
        }

        void earnCoins(int amount) {
            coinFraction += amount * difficulty.coinMultiplier * (1 + 0.25 * perks[Perk.GREED.ordinal()]);
            int whole = (int) coinFraction;
            coinFraction -= whole;
            runCoins += whole;
        }

        private void addPickup(PickupType type, double x, double y) {
            Pickup p = new Pickup();
            p.type = type;
            p.x = clamp(x, 30, W - 30);
            p.y = clamp(y, 30, H - 30);
            p.life = type == PickupType.DIAMOND ? 16 : 10;
            pickups.add(p);
        }

        private void addCoin(double x, double y) {
            Pickup p = new Pickup();
            p.type = PickupType.COIN;
            p.x = x;
            p.y = y;
            double angle = rng.nextDouble() * Math.PI * 2, speed = 80 + rng.nextDouble() * 220;
            p.vx = Math.cos(angle) * speed;
            p.vy = Math.sin(angle) * speed;
            p.life = 9;
            pickups.add(p);
        }

        private void updateWave(double dt) {
            if (state != State.PLAYING) {
                return;
            }
            if (clearing) {
                clearTimer -= dt;
                if (clearTimer <= 0 && pickups.isEmpty()) {
                    openUpgrades();
                }
                return;
            }
            if (!spawnQueue.isEmpty()) {
                spawnTimer -= dt;
                if (spawnTimer <= 0) {
                    spawnTimer = spawnDelay;
                    spawnEnemy(spawnQueue.remove(0));
                }
                return;
            }
            boolean anyAlive = false;
            for (Enemy e : newEnemies) {
                anyAlive |= !e.dead;
            }
            for (Enemy e : enemies) {
                anyAlive |= !e.dead;
            }
            if (!anyAlive) {
                clearing = true;
                clearTimer = 1.1;
                slowMotion = Math.max(slowMotion, 0.3);
                banner = "WAVE CLEARED!";
                bannerTimer = 1.1;
                score += (int) (wave * 50 * difficulty.scoreMultiplier);
                earnCoins(wave / 2 + 1);
                sound.play(Sound.Effect.UPGRADE);
                clearEnemyBullets();
            }
        }

        private void openUpgrades() {
            rollChoices();
            if (choices.isEmpty()) {
                nextWave();
                return;
            }
            cardDelay = 0.35;
            hoveredCard = -1;
            firing = false;
            state = State.UPGRADE;
            saveRun();
        }

        /** Picks up to 3 different upgrades. Rarer ones become more likely in later waves. */
        void rollChoices() {
            List<Upgrade> pool = new ArrayList<Upgrade>();
            for (Upgrade upgrade : Upgrade.values()) {
                if (stacks[upgrade.ordinal()] < upgrade.max) {
                    pool.add(upgrade);
                }
            }
            choices.clear();
            while (choices.size() < 3 && !pool.isEmpty()) {
                int total = 0;
                for (Upgrade upgrade : pool) {
                    total += weightOf(upgrade);
                }
                int roll = rng.nextInt(total);
                for (Iterator<Upgrade> it = pool.iterator(); it.hasNext(); ) {
                    Upgrade upgrade = it.next();
                    roll -= weightOf(upgrade);
                    if (roll < 0) {
                        choices.add(upgrade);
                        it.remove();
                        break;
                    }
                }
            }
        }

        private int weightOf(Upgrade upgrade) {
            int bonus = upgrade.rarity == Rarity.COMMON ? 0 : Math.min(wave, 25);
            return upgrade.rarity.weight + bonus * (upgrade.rarity == Rarity.LEGENDARY ? 1 : 2);
        }

        void reroll() {
            if (state != State.UPGRADE || cardDelay > 0) {
                return;
            }
            if (rerolls <= 0) {
                sound.play(Sound.Effect.DENY);
                return;
            }
            rerolls--;
            rollChoices();
            sound.play(Sound.Effect.PICKUP);
        }

        void chooseCard(int index) {
            if (state != State.UPGRADE || cardDelay > 0 || index < 0 || index >= choices.size()) {
                return;
            }
            if (!canBuyCard(index)) {
                sound.play(Sound.Effect.DENY);
                showNotice("You need " + (cardPrice(index) - runCoins) + " more coins for " + choices.get(index).title);
                return;
            }
            runCoins -= cardPrice(index);
            applyUpgrade(choices.get(index));
            sound.play(Sound.Effect.UPGRADE);
            state = State.PLAYING;
            nextWave();
        }

        void applyUpgrade(Upgrade upgrade) {
            stacks[upgrade.ordinal()]++;
            switch (upgrade) {
                case RAPID:
                    fireDelay *= 0.8;
                    break;
                case POWER:
                    bulletDamage *= 1.35;
                    break;
                case SWIFT:
                    moveSpeed *= 1.12;
                    break;
                case VITALITY:
                    maxHealth++;
                    health = maxHealth;
                    break;
                case BOMBS:
                    bombs++;
                    break;
                case HEAVY:
                    bulletRadius += 2;
                    bulletDamage *= 1.15;
                    break;
                case CRIT:
                    critChance += 0.15;
                    break;
                case MULTI:
                    shots++;
                    break;
                case PIERCE:
                    pierce++;
                    break;
                case BOUNCE:
                    bounces++;
                    break;
                case EXPLOSIVE:
                    explosiveRadius = Math.max(explosiveRadius, 35) + 20;
                    break;
                case ORBITALS:
                    orbitals++;
                    break;
                case HOMING:
                    homing += 3.5;
                    break;
                case DASH:
                    dashDelay *= 0.78;
                    dashDamage += 6;
                    break;
                case FROST:
                    frostTime += 1.5;
                    break;
                case DRONE:
                    Drone drone = new Drone();
                    drone.x = px;
                    drone.y = py;
                    drones.add(drone);
                    break;
                case CHAIN:
                    chainTargets += 2;
                    break;
                case VAMPIRE:
                    vampire++;
                    break;
                case SECOND_LIFE:
                    secondLives++;
                    break;
                case GLASS:
                    bulletDamage *= 2.5;
                    maxHealth = Math.max(1, maxHealth - 2);
                    health = Math.min(health, maxHealth);
                    break;
                default:
                    break;
            }
        }

        // ============================== EFFECTS =============================

        private void updateEffects(double dt) {
            for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
                Particle p = it.next();
                p.life -= dt;
                if (p.life <= 0) {
                    it.remove();
                    continue;
                }
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.vx *= 1 - 2.8 * dt;
                p.vy *= 1 - 2.8 * dt;
                p.angle += p.spin * dt;
            }
            for (Iterator<Afterimage> it = afterimages.iterator(); it.hasNext(); ) {
                Afterimage image = it.next();
                image.life -= dt;
                if (image.life <= 0) {
                    it.remove();
                }
            }
            for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
                Ring r = it.next();
                r.life -= dt;
                if (r.life <= 0) {
                    it.remove();
                }
            }
            for (Iterator<Bolt> it = bolts.iterator(); it.hasNext(); ) {
                Bolt b = it.next();
                b.life -= dt;
                if (b.life <= 0) {
                    it.remove();
                }
            }
            for (Iterator<FloatText> it = texts.iterator(); it.hasNext(); ) {
                FloatText t = it.next();
                t.life -= dt;
                t.y -= 42 * dt;
                if (t.life <= 0) {
                    it.remove();
                }
            }
        }

        private void addParticle(double x, double y, double vx, double vy, double life, double size, Color color) {
            if (particles.size() >= MAX_PARTICLES) {
                return;
            }
            Particle p = new Particle();
            p.x = x;
            p.y = y;
            p.vx = vx;
            p.vy = vy;
            p.life = life;
            p.maxLife = life;
            p.size = size;
            p.color = color;
            particles.add(p);
        }

        private void burst(double x, double y, int count, Color color, double speed) {
            for (int i = 0; i < count; i++) {
                double angle = fx.nextDouble() * Math.PI * 2;
                double s = speed * (0.25 + fx.nextDouble() * 0.75);
                addParticle(x, y, Math.cos(angle) * s, Math.sin(angle) * s,
                        0.25 + fx.nextDouble() * 0.45, 2 + fx.nextDouble() * 4, color);
            }
        }

        /** Spinning pieces of the thing that just blew up. */
        private void addShards(double x, double y, int count, Color color, double speed) {
            for (int i = 0; i < count; i++) {
                double angle = fx.nextDouble() * Math.PI * 2;
                double s = speed * (0.35 + fx.nextDouble() * 0.65);
                addParticle(x, y, Math.cos(angle) * s, Math.sin(angle) * s,
                        0.35 + fx.nextDouble() * 0.4, 5 + fx.nextDouble() * 6, color);
                if (particles.size() < MAX_PARTICLES) {
                    Particle p = particles.get(particles.size() - 1);
                    p.shard = true;
                    p.angle = fx.nextDouble() * Math.PI * 2;
                    p.spin = (fx.nextDouble() - 0.5) * 24;
                }
            }
        }

        /** A quick bright bloom at the centre of an explosion. */
        private void addFlash(double x, double y, double radius, double life, Color color) {
            addRing(x, y, radius, life, color);
            rings.get(rings.size() - 1).flash = true;
        }

        private void addRing(double x, double y, double maxRadius, double life, Color color) {
            Ring r = new Ring();
            r.x = x;
            r.y = y;
            r.maxRadius = maxRadius;
            r.life = life;
            r.maxLife = life;
            r.color = color;
            rings.add(r);
        }

        private void addText(double x, double y, String text, Color color, int size) {
            if (texts.size() > 40) {
                return;
            }
            FloatText t = new FloatText();
            t.x = x;
            t.y = y;
            t.text = text;
            t.color = color;
            t.size = size;
            t.life = 0.75;
            texts.add(t);
        }

        // ============================= DRAWING ==============================

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            viewScale = Math.min(getWidth() / (double) W, getHeight() / (double) H);
            viewX = (getWidth() - W * viewScale) / 2;
            viewY = (getHeight() - H * viewScale) / 2;
            g.translate(viewX, viewY);
            g.scale(viewScale, viewScale);
            g.clipRect(0, 0, W, H);
            render(g);
            g.dispose();
        }

        void render(Graphics2D g) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean inGame = state != State.MENU && state != State.HANGAR && state != State.RECORDS && state != State.SKINS;
            g.drawImage((inGame ? currentTheme() : themes[0]).image, 0, 0, null);
            drawStars(g);

            if (state == State.MENU) {
                drawMenu(g);
            } else if (state == State.HANGAR) {
                drawHangar(g);
            } else if (state == State.RECORDS) {
                drawRecords(g);
            } else if (state == State.SKINS) {
                drawSkins(g);
            } else {
                drawWorld(g);
            }
            drawToast(g);
        }

        private void drawToast(Graphics2D g) {
            if (toastTimer <= 0) {
                return;
            }
            alpha(g, (float) Math.min(1, toastTimer * 3));
            font(g, 17, true);
            int width = g.getFontMetrics().stringWidth(toastText) + 56;
            Rectangle box = new Rectangle((W - width) / 2, 70, width, 38);
            g.setColor(new Color(24, 26, 56));
            g.fillRoundRect(box.x, box.y, box.width, box.height, 14, 14);
            g.setColor(new Color(255, 200, 80));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(box.x, box.y, box.width, box.height, 14, 14);
            g.setStroke(new BasicStroke(1));
            centeredIn(g, toastText, box, box.y + 25);
            alpha(g, 1);
        }

        private void drawWorld(Graphics2D g) {
            double shakeX = (fx.nextDouble() - 0.5) * shake, shakeY = (fx.nextDouble() - 0.5) * shake;
            g.translate(shakeX, shakeY);
            drawRings(g);
            drawPickups(g);
            drawBeams(g);
            for (int i = enemies.size() - 1; i >= 0; i--) {
                drawEnemy(g, enemies.get(i));
            }
            drawBolts(g);
            drawBullets(g);
            if (state != State.GAME_OVER) {
                drawPlayer(g);
            }
            drawParticles(g);
            drawTexts(g);
            g.translate(-shakeX, -shakeY);

            g.drawImage(vignette, 0, 0, null);
            if (state == State.PLAYING && health == 1 && maxHealth > 1) {
                // The edges of the screen pulse red on your last heart.
                alpha(g, 0.35f + 0.3f * (float) Math.abs(Math.sin(clock * 4)));
                g.drawImage(dangerVignette, 0, 0, null);
                alpha(g, 1);
            }
            if (warpTimer > 0) {
                alpha(g, 0.14f);
                g.setColor(new Color(170, 110, 255));
                g.fillRect(0, 0, W, H);
                alpha(g, 1);
            }
            if (hurtFlash > 0) {
                alpha(g, (float) (hurtFlash / 0.35 * 0.35));
                g.setColor(new Color(255, 30, 60));
                g.fillRect(0, 0, W, H);
                alpha(g, 1);
            }
            drawHud(g);
            if (state == State.PLAYING) {
                if (announceTimer > 0) {
                    alpha(g, (float) Math.min(1, announceTimer * 2));
                    g.setColor(announceColor);
                    centered(g, announcement, 280, 40 + (int) (Math.max(0, announceTimer - 1.1) * 40), true);
                    alpha(g, 1);
                }
                if (bannerTimer > 0) {
                    alpha(g, (float) Math.min(1, bannerTimer * 2));
                    g.setColor(banner.startsWith("THE ") ? new Color(255, 90, 170) : Color.WHITE);
                    centered(g, banner, 190, 58, true);
                    if (banner.startsWith("THE ") && (int) (clock * 6) % 2 == 0) {
                        g.setColor(new Color(255, 70, 80));
                        centered(g, "WARNING", 132, 28, true);
                    }
                    alpha(g, 1);
                }
            } else if (state == State.UPGRADE) {
                drawUpgradeScreen(g);
            } else if (state == State.PAUSED) {
                dim(g, 0.68f);
                g.setColor(Color.WHITE);
                centered(g, "PAUSED", 310, 56, true);
                g.setColor(new Color(190, 198, 235));
                centered(g, "Press P or click to keep playing", 360, 20, false);
                centered(g, "Press X to end this run and keep your coins", 392, 20, false);
                centered(g, "Closing the game saves the run. Continue it from the start of this wave.", 424, 17, false);
            } else if (state == State.GAME_OVER) {
                drawGameOver(g);
            }
        }

        private BufferedImage makeBackground(Color base, Color grid, Color border, Color nebula) {
            BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(base);
            g.fillRect(0, 0, W, H);
            Random random = new Random(base.getRGB());
            Color clear = new Color(nebula.getRed(), nebula.getGreen(), nebula.getBlue(), 0);
            Color soft = new Color(nebula.getRed(), nebula.getGreen(), nebula.getBlue(), 75);
            for (int i = 0; i < 5; i++) {
                float cx = random.nextFloat() * W, cy = random.nextFloat() * H, radius = 180 + random.nextFloat() * 240;
                g.setPaint(new RadialGradientPaint(cx, cy, radius, new float[] {0f, 1f}, new Color[] {soft, clear}));
                g.fillRect(0, 0, W, H);
            }
            g.setColor(grid);
            for (int x = 0; x <= W; x += 50) {
                g.drawLine(x, 0, x, H);
            }
            for (int y = 0; y <= H; y += 50) {
                g.drawLine(0, y, W, y);
            }
            g.setColor(border);
            g.setStroke(new BasicStroke(4));
            g.drawRect(2, 2, W - 5, H - 5);
            g.dispose();
            return image;
        }

        private BufferedImage makeVignette(Color edge) {
            BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            Color clear = new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 0);
            g.setPaint(new RadialGradientPaint(W / 2f, H / 2f, W * 0.72f, new float[] {0.55f, 1f},
                    new Color[] {clear, edge}));
            g.fillRect(0, 0, W, H);
            g.dispose();
            return image;
        }

        private void drawStars(Graphics2D g) {
            // Stars shift a little with the player, which makes the arena feel deep.
            double offsetX = state == State.MENU ? clock * 12 : -(px - W / 2.0) * 0.04;
            double offsetY = state == State.MENU ? 0 : -(py - H / 2.0) * 0.04;
            for (double[] star : stars) {
                double depth = star[2];
                int x = (int) (((star[0] + offsetX * depth) % W + W) % W);
                int y = (int) (((star[1] + offsetY * depth) % H + H) % H);
                int shade = 80 + (int) (depth * 110);
                g.setColor(new Color(shade, shade, Math.min(255, shade + 30)));
                int size = depth > 1 ? 2 : 1;
                g.fillRect(x, y, size, size);
            }
        }

        private void alpha(Graphics2D g, float value) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, Math.min(1, value))));
        }

        private void dim(Graphics2D g, float amount) {
            alpha(g, amount);
            g.setColor(new Color(5, 6, 14));
            g.fillRect(0, 0, W, H);
            alpha(g, 1);
        }

        private void font(Graphics2D g, int size, boolean bold) {
            Font[] cache = bold ? boldFonts : plainFonts;
            int index = Math.min(size, cache.length - 1);
            if (cache[index] == null) {
                cache[index] = new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, index);
            }
            g.setFont(cache[index]);
        }

        private void centered(Graphics2D g, String text, int y, int size, boolean bold) {
            font(g, size, bold);
            g.drawString(text, (W - g.getFontMetrics().stringWidth(text)) / 2, y);
        }

        private void centeredIn(Graphics2D g, String text, Rectangle box, int y) {
            g.drawString(text, box.x + (box.width - g.getFontMetrics().stringWidth(text)) / 2, y);
        }

        private Polygon regular(double x, double y, double r, int sides, double angle) {
            Polygon polygon = new Polygon();
            for (int i = 0; i < sides; i++) {
                double a = angle + i * Math.PI * 2 / sides;
                polygon.addPoint((int) Math.round(x + Math.cos(a) * r), (int) Math.round(y + Math.sin(a) * r));
            }
            return polygon;
        }

        private void glowCircle(Graphics2D g, double x, double y, double r, Color color) {
            alpha(g, 0.25f);
            g.setColor(color);
            g.fillOval((int) (x - r * 2), (int) (y - r * 2), (int) (r * 4), (int) (r * 4));
            alpha(g, 1);
            g.fillOval((int) (x - r), (int) (y - r), (int) (r * 2), (int) (r * 2));
        }

        private void glowPolygon(Graphics2D g, Polygon shape, Color glow, int width) {
            alpha(g, 0.28f);
            g.setColor(glow);
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(shape);
            g.setStroke(new BasicStroke(1));
            alpha(g, 1);
        }

        private void drawRings(Graphics2D g) {
            for (Ring r : rings) {
                double t = 1 - r.life / r.maxLife;
                double radius = r.maxRadius * Math.sqrt(t);
                if (r.flash) {
                    alpha(g, (float) (0.6 * (1 - t) * (1 - t)));
                    g.setColor(r.color);
                    g.fillOval((int) (r.x - radius), (int) (r.y - radius), (int) (radius * 2), (int) (radius * 2));
                    continue;
                }
                alpha(g, (float) (0.8 * (1 - t)));
                g.setColor(r.color);
                g.setStroke(new BasicStroke((float) (2 + 5 * (1 - t))));
                g.drawOval((int) (r.x - radius), (int) (r.y - radius), (int) (radius * 2), (int) (radius * 2));
            }
            alpha(g, 1);
            g.setStroke(new BasicStroke(1));
        }

        private void drawPickups(Graphics2D g) {
            for (Pickup p : pickups) {
                if (p.life < 2.5 && (int) (clock * 8) % 2 == 0) {
                    continue;
                }
                double bob = Math.sin(clock * 5 + p.x) * 3;
                int x = (int) p.x, y = (int) (p.y + bob);
                switch (p.type) {
                    case HEART:
                        drawHeart(g, x - 11, y - 10, 22, new Color(255, 80, 110));
                        break;
                    case BOMB:
                        drawBombIcon(g, x, y);
                        break;
                    case DIAMOND:
                        drawDiamond(g, x, y, 10);
                        break;
                    default:
                        drawCoin(g, x, y, 7);
                        break;
                }
            }
        }

        static final Color DIAMOND_COLOR = new Color(110, 230, 255);

        /** The color the player's ship is drawn in: the equipped skin, or the ship's own color. */
        Color shipColor() {
            return colorFor(ship, skin);
        }

        Color colorFor(Ship s, Skin k) {
            if (k == Skin.RAINBOW) {
                return Color.getHSBColor((float) (clock * 0.25 % 1.0), 0.75f, 1f);
            }
            return k.color != null ? k.color : s.color;
        }

        private void drawDiamond(Graphics2D g, int x, int y, int r) {
            // A cut gem that glints now and then.
            glowCircle(g, x, y, r * 0.9, DIAMOND_COLOR);
            int[] xs = {x, x + r, x, x - r};
            int[] ys = {y - r - r / 3, y - r / 4, y + r + r / 3, y - r / 4};
            g.setColor(DIAMOND_COLOR);
            g.fillPolygon(xs, ys, 4);
            g.setColor(new Color(210, 250, 255));
            g.fillPolygon(new int[] {x, x + r, x - r}, new int[] {y - r - r / 3, y - r / 4, y - r / 4}, 3);
            g.setColor(new Color(40, 130, 190));
            g.drawPolygon(xs, ys, 4);
            if ((int) (clock * 1.5 + x * 0.01) % 3 == 0) {
                g.setColor(Color.WHITE);
                g.fillOval(x - r / 2 - 1, y - r / 2 - 1, 3, 3);
            }
        }

        private void drawCoin(Graphics2D g, int x, int y, int r) {
            // The coin narrows and widens so it looks like it is spinning.
            int width = Math.max(2, (int) (r * Math.abs(Math.cos(clock * 5 + x))));
            alpha(g, 0.25f);
            g.setColor(new Color(255, 215, 80));
            g.fillOval(x - r * 2, y - r * 2, r * 4, r * 4);
            alpha(g, 1);
            g.fillOval(x - width, y - r, width * 2, r * 2);
            g.setColor(new Color(255, 245, 180));
            g.fillOval(x - width / 2, y - r / 2, Math.max(1, width), r);
        }

        private void drawHeart(Graphics2D g, int x, int y, int size, Color color) {
            g.setColor(color);
            int half = size / 2 + 1;
            g.fillOval(x, y, half, half);
            g.fillOval(x + size - half, y, half, half);
            g.fillPolygon(new int[] {x, x + size, x + size / 2}, new int[] {y + size * 3 / 8, y + size * 3 / 8, y + size}, 3);
        }

        private void drawBombIcon(Graphics2D g, int x, int y) {
            glowCircle(g, x, y + 2, 9, new Color(255, 170, 80));
            g.setColor(new Color(45, 45, 65));
            g.fillOval(x - 8, y - 6, 16, 16);
            g.setColor(new Color(255, 240, 180));
            g.fillOval(x + 4, y - 10, 5, 5);
        }

        private void drawBeams(Graphics2D g) {
            for (Enemy e : enemies) {
                if (e.type != EnemyType.WARDEN || e.dead || (e.beamWarn <= 0 && e.beamTime <= 0)) {
                    continue;
                }
                for (int i = 0; i < e.beamCount; i++) {
                    double angle = e.beamAngle + i * Math.PI * 2 / e.beamCount;
                    int ex = (int) (e.x + Math.cos(angle) * 1300), ey = (int) (e.y + Math.sin(angle) * 1300);
                    if (e.beamWarn > 0) {
                        alpha(g, 0.3f + 0.5f * (float) Math.abs(Math.sin(clock * 22)));
                        g.setColor(new Color(255, 80, 70));
                        g.setStroke(new BasicStroke(3));
                        g.drawLine((int) e.x, (int) e.y, ex, ey);
                    } else {
                        alpha(g, 0.35f);
                        g.setColor(new Color(255, 70, 50));
                        g.setStroke(new BasicStroke(34, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.drawLine((int) e.x, (int) e.y, ex, ey);
                        alpha(g, 1);
                        g.setColor(new Color(255, 225, 200));
                        g.setStroke(new BasicStroke(12));
                        g.drawLine((int) e.x, (int) e.y, ex, ey);
                    }
                }
                g.setStroke(new BasicStroke(1));
                alpha(g, 1);
            }
        }

        private void drawEnemy(Graphics2D g, Enemy e) {
            if (e.dead) {
                return;
            }
            // Swells for an instant when hit, so every shot feels like it landed.
            double r = e.r * (1 + e.flash * 1.6);
            Color color = e.flash > 0 ? Color.WHITE : e.slowTimer > 0 ? mix(e.type.color, new Color(120, 200, 255)) : e.type.color;
            if (e.spawnTimer > 0) {
                // A warning ring gives the player time to move out of the way.
                double t = 1 - e.spawnTimer / Math.max(0.01, e.spawnTotal);
                alpha(g, 0.3f + 0.6f * (float) t);
                g.setColor(e.type.color);
                g.setStroke(new BasicStroke(2));
                double ring = r * (2.6 - 1.6 * t);
                g.drawOval((int) (e.x - ring), (int) (e.y - ring), (int) (ring * 2), (int) (ring * 2));
                g.setStroke(new BasicStroke(1));
                alpha(g, 1);
                return;
            }
            if (e.type == EnemyType.GHOST && e.blinkWarn > 0) {
                alpha(g, 0.8f);
                g.setColor(e.type.color);
                g.setStroke(new BasicStroke(2));
                double ring = 12 + e.blinkWarn * 34;
                g.drawOval((int) (e.warpX - ring), (int) (e.warpY - ring), (int) (ring * 2), (int) (ring * 2));
                g.setStroke(new BasicStroke(1));
                alpha(g, 1);
            }
            if (e.type == EnemyType.BOMBER && e.fuse > 0) {
                alpha(g, 0.2f + 0.2f * (float) Math.abs(Math.sin(clock * 16)));
                g.setColor(new Color(255, 120, 50));
                g.fillOval((int) e.x - 100, (int) e.y - 100, 200, 200);
                alpha(g, 1);
            }
            Polygon shape = enemyShape(e, r);
            if (e.elite) {
                glowPolygon(g, shape, new Color(255, 215, 80), 18);
            }
            glowPolygon(g, shape, e.type.color, 12);
            boolean faded = e.type == EnemyType.GHOST;
            if (faded) {
                alpha(g, 0.55f + 0.25f * (float) Math.sin(clock * 5 + e.wobble));
            }
            g.setColor(color);
            g.fillPolygon(shape);
            alpha(g, 1);
            if (e.elite) {
                g.setColor(new Color(255, 225, 110));
                g.setStroke(new BasicStroke(3));
                g.drawPolygon(shape);
                g.setStroke(new BasicStroke(1));
            }
            drawEnemyDetails(g, e, r);
            if (e.type != EnemyType.SEGMENT) {
                drawFace(g, e, (int) r);
            }
            if ((e.type == EnemyType.TANK || e.elite) && e.hp < e.maxHp) {
                g.setColor(new Color(30, 30, 50));
                g.fillRect((int) e.x - 24, (int) (e.y - r - 12), 48, 5);
                g.setColor(e.elite ? new Color(255, 215, 80) : e.type.color);
                g.fillRect((int) e.x - 24, (int) (e.y - r - 12), (int) (48 * Math.max(0, e.hp) / e.maxHp), 5);
            }
        }

        private Polygon enemyShape(Enemy e, double r) {
            switch (e.type) {
                case CHASER:
                    return regular(e.x, e.y, r, 4, e.angle);
                case SPEEDER:
                    return regular(e.x, e.y, r + 3, 3, e.angle);
                case TANK:
                    return regular(e.x, e.y, r, 6, e.angle * 0.3);
                case SPLITTER:
                    return regular(e.x, e.y, r, 5, e.angle * 0.6);
                case MINI:
                    return regular(e.x, e.y, r, 3, e.angle * 2);
                case SHOOTER:
                    return regular(e.x, e.y, r, 12, 0);
                case BOMBER:
                    return regular(e.x, e.y, r, 10, e.angle);
                case GHOST:
                    return regular(e.x, e.y, r, 7, Math.sin(clock + e.wobble) * 0.5);
                case SEGMENT:
                    return regular(e.x, e.y, r, 8, e.angle);
                case SERPENT:
                    return regular(e.x, e.y, r, 6, e.angle);
                case WARDEN:
                    return regular(e.x, e.y, r, 4, e.angle * 0.2 + Math.PI / 4);
                default:
                    return regular(e.x, e.y, r, 8, e.angle * 0.3);
            }
        }

        private void drawEnemyDetails(Graphics2D g, Enemy e, double r) {
            switch (e.type) {
                case SHOOTER:
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(5));
                    g.drawLine((int) e.x, (int) e.y, (int) (e.x + Math.cos(e.angle) * (r + 8)), (int) (e.y + Math.sin(e.angle) * (r + 8)));
                    g.setStroke(new BasicStroke(1));
                    break;
                case BOMBER:
                    g.setColor(new Color(60, 30, 20));
                    g.setStroke(new BasicStroke(3));
                    g.drawLine((int) e.x, (int) (e.y - r), (int) (e.x + 7), (int) (e.y - r - 8));
                    g.setStroke(new BasicStroke(1));
                    g.setColor(new Color(255, 240, 150));
                    g.fillOval((int) e.x + 4, (int) (e.y - r) - 12, 7, 7);
                    break;
                case SEGMENT:
                    g.setColor(new Color(30, 110, 50));
                    g.fillPolygon(regular(e.x, e.y, r * 0.55, 6, e.angle));
                    break;
                case OVERLORD:
                    g.setColor(new Color(110, 20, 90));
                    g.fillPolygon(regular(e.x, e.y, r * 0.68, 8, -e.angle * 0.5));
                    if (e.chargeWarn > 0) {
                        // Shows exactly where the boss is about to charge.
                        alpha(g, 0.35f + 0.4f * (float) Math.abs(Math.sin(clock * 18)));
                        g.setColor(new Color(255, 70, 110));
                        g.setStroke(new BasicStroke((float) r * 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        double d = Math.max(1, Math.hypot(e.aimX - e.x, e.aimY - e.y));
                        g.drawLine((int) e.x, (int) e.y, (int) (e.x + (e.aimX - e.x) / d * 396), (int) (e.y + (e.aimY - e.y) / d * 396));
                        g.setStroke(new BasicStroke(1));
                        alpha(g, 1);
                    }
                    break;
                case WARDEN:
                    g.setColor(new Color(120, 25, 25));
                    g.fillPolygon(regular(e.x, e.y, r * 0.7, 4, -e.angle * 0.3));
                    break;
                default:
                    break;
            }
        }

        private static Color mix(Color a, Color b) {
            return new Color((a.getRed() + b.getRed()) / 2, (a.getGreen() + b.getGreen()) / 2, (a.getBlue() + b.getBlue()) / 2);
        }

        /** Angry little eyes that follow the player. */
        private void drawFace(Graphics2D g, Enemy e, int r) {
            double look = Math.atan2(py - e.y, px - e.x);
            int eye = Math.max(3, r / 4), gap = Math.max(4, r * 2 / 5);
            for (int side = -1; side <= 1; side += 2) {
                int ex = (int) (e.x + side * gap), ey = (int) (e.y - r / 8.0);
                g.setColor(Color.WHITE);
                g.fillOval(ex - eye, ey - eye, eye * 2, eye * 2);
                g.setColor(new Color(15, 15, 30));
                int pupil = Math.max(2, eye * 3 / 5);
                int qx = (int) (ex + Math.cos(look) * (eye - pupil)), qy = (int) (ey + Math.sin(look) * (eye - pupil));
                g.fillOval(qx - pupil, qy - pupil, pupil * 2, pupil * 2);
                g.setStroke(new BasicStroke(Math.max(2, r / 8f)));
                g.drawLine(ex + eye * side, ey - eye - 2, ex - eye * side / 2, ey - eye / 3);
                g.setStroke(new BasicStroke(1));
            }
        }

        private void drawBolts(Graphics2D g) {
            g.setColor(new Color(170, 220, 255));
            g.setStroke(new BasicStroke(3));
            for (Bolt b : bolts) {
                int segments = 6;
                double lastX = b.x1, lastY = b.y1;
                for (int i = 1; i <= segments; i++) {
                    double t = i / (double) segments;
                    double jitter = i == segments ? 0 : 14;
                    double x = b.x1 + (b.x2 - b.x1) * t + (fx.nextDouble() - 0.5) * jitter;
                    double y = b.y1 + (b.y2 - b.y1) * t + (fx.nextDouble() - 0.5) * jitter;
                    g.drawLine((int) lastX, (int) lastY, (int) x, (int) y);
                    lastX = x;
                    lastY = y;
                }
            }
            g.setStroke(new BasicStroke(1));
        }

        private void drawBullets(Graphics2D g) {
            for (Bullet b : bullets) {
                if (b.dead) {
                    continue;
                }
                g.setColor(b.color);
                if (b.enemy) {
                    glowCircle(g, b.x, b.y, b.r, b.color);
                    g.setColor(Color.WHITE);
                    g.fillOval((int) b.x - 3, (int) b.y - 3, 6, 6);
                } else {
                    alpha(g, 0.3f);
                    g.setStroke(new BasicStroke((float) b.r * 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine((int) b.x, (int) b.y, (int) (b.x - b.vx * 0.03), (int) (b.y - b.vy * 0.03));
                    g.setStroke(new BasicStroke(1));
                    alpha(g, 1);
                    g.fillOval((int) (b.x - b.r), (int) (b.y - b.r), (int) (b.r * 2), (int) (b.r * 2));
                }
            }
        }

        private Polygon shipShape(Ship type, double x, double y, double angle, double size) {
            Polygon shape = new Polygon();
            double c = Math.cos(angle), s = Math.sin(angle);
            for (double[] point : type.outline) {
                double a = point[0] * size, b = point[1] * size;
                shape.addPoint((int) Math.round(x + a * c - b * s), (int) Math.round(y + a * s + b * c));
            }
            return shape;
        }

        private void drawPlayer(Graphics2D g) {
            for (int i = 0; i < orbitals; i++) {
                double angle = orbitAngle + i * Math.PI * 2 / orbitals;
                g.setColor(new Color(120, 220, 255));
                glowCircle(g, px + Math.cos(angle) * 72, py + Math.sin(angle) * 72, 8, new Color(120, 220, 255));
            }
            for (Drone drone : drones) {
                Polygon shape = regular(drone.x, drone.y, 9, 3, drone.facing);
                glowPolygon(g, shape, new Color(150, 255, 190), 8);
                g.setColor(new Color(180, 255, 200));
                g.fillPolygon(shape);
            }
            if (fortressTimer > 0) {
                alpha(g, 0.22f + 0.1f * (float) Math.sin(clock * 12));
                g.setColor(shipColor());
                g.fillOval((int) px - 90, (int) py - 90, 180, 180);
                alpha(g, 1);
            }
            if (overdriveTimer > 0) {
                alpha(g, 0.35f);
                g.setColor(shipColor());
                int size = 30 + (int) (Math.sin(clock * 20) * 4);
                g.fillOval((int) px - size, (int) py - size, size * 2, size * 2);
                alpha(g, 1);
            }
            for (Afterimage image : afterimages) {
                alpha(g, (float) (image.life / AFTERIMAGE_LIFE * 0.45));
                g.setColor(shipColor());
                g.fillPolygon(shipShape(ship, image.x, image.y, image.angle, 1));
            }
            alpha(g, 1);
            if (invulnerable > 0 && dashTimer <= 0 && (int) (clock * 14) % 2 == 0) {
                return;
            }
            // The ship kicks backward a little with every shot.
            Polygon shape = shipShape(ship, px - Math.cos(facing) * recoil, py - Math.sin(facing) * recoil, facing, 1);
            glowPolygon(g, shape, shipColor(), 11);
            g.setColor(dashTimer > 0 ? Color.WHITE : shipColor());
            g.fillPolygon(shape);
            g.setColor(Color.WHITE);
            g.fillOval((int) px - 4, (int) py - 4, 8, 8);
        }

        private void drawParticles(Graphics2D g) {
            g.setStroke(SHARD_STROKE);
            for (Particle p : particles) {
                alpha(g, (float) (p.life / p.maxLife));
                g.setColor(p.color);
                if (p.shard) {
                    int dx = (int) (Math.cos(p.angle) * p.size), dy = (int) (Math.sin(p.angle) * p.size);
                    g.drawLine((int) p.x - dx, (int) p.y - dy, (int) p.x + dx, (int) p.y + dy);
                    continue;
                }
                int size = (int) Math.ceil(p.size);
                g.fillRect((int) p.x - size / 2, (int) p.y - size / 2, size, size);
            }
            g.setStroke(new BasicStroke(1));
            alpha(g, 1);
        }

        private void drawTexts(Graphics2D g) {
            for (FloatText t : texts) {
                alpha(g, (float) Math.min(1, t.life * 2));
                font(g, t.size, true);
                g.setColor(t.color);
                g.drawString(t.text, (int) t.x - g.getFontMetrics().stringWidth(t.text) / 2, (int) t.y);
            }
            alpha(g, 1);
        }

        private void rightAligned(Graphics2D g, String text, int y) {
            g.drawString(text, W - 18 - g.getFontMetrics().stringWidth(text), y);
        }

        private void drawHud(Graphics2D g) {
            for (int i = 0; i < maxHealth; i++) {
                drawHeart(g, 16 + i * 28, 14, 24, i < health ? new Color(255, 75, 105) : new Color(55, 58, 85));
            }
            if (secondLives > 0) {
                font(g, 13, true);
                g.setColor(new Color(255, 190, 60));
                g.drawString("SECOND LIFE READY", 18, 56);
            }
            if (dailyRun) {
                font(g, 13, true);
                g.setColor(new Color(120, 220, 255));
                g.drawString("DAILY: " + modifier.title, 18, 74);
            }

            font(g, 26, true);
            g.setColor(Color.WHITE);
            rightAligned(g, String.valueOf(Math.round(displayScore)), 36);
            font(g, 14, false);
            g.setColor(new Color(170, 180, 220));
            rightAligned(g, "WAVE " + wave + "   BEST " + (dailyRun ? dailyBestToday() : highScore), 56);

            drawCoin(g, W - 70, 74, 7);
            font(g, 16, true);
            g.setColor(new Color(255, 220, 110));
            g.drawString("+" + runCoins, W - 58, 80);
            if (runDiamonds > 0) {
                drawDiamond(g, W - 128, 74, 7);
                g.setColor(DIAMOND_COLOR);
                g.drawString("+" + runDiamonds, W - 116, 80);
            }

            if (multiplier() > 1) {
                font(g, 30 + (int) (multiplierPulse * 50), true);
                g.setColor(new Color(255, 225, 90));
                rightAligned(g, "x" + multiplier(), 118);
                g.fillRect(W - 78, 124, (int) (60 * Math.max(0, comboTimer) / 2.5), 4);
            }

            // Dash, special power and bombs along the bottom.
            font(g, 13, true);
            g.setColor(new Color(170, 180, 220));
            g.drawString("DASH [SPACE]", 18, H - 36);
            drawBar(g, 18, H - 28, 120, 1 - dashCooldown / dashDelay, shipColor());

            boolean ready = specialCharge >= 1;
            g.setColor(ready ? new Color(255, 225, 120) : new Color(170, 180, 220));
            String specialName = ship.special == Special.TIME_WARP ? "TIME WARP" : ship.special.name();
            g.drawString(specialName + " [E]" + (ready && (int) (clock * 4) % 2 == 0 ? "  READY!" : ""), 158, H - 36);
            drawBar(g, 158, H - 28, 170, specialCharge, ready ? new Color(255, 225, 120) : new Color(180, 130, 255));

            g.setColor(new Color(170, 180, 220));
            g.drawString("BOMB [Q]  x" + bombs, 350, H - 36);
            for (int i = 0; i < bombs && i < 8; i++) {
                drawBombIcon(g, 360 + i * 24, H - 18);
            }
            if (autoFire) {
                g.setColor(new Color(150, 240, 150));
                font(g, 13, true);
                rightAligned(g, "AUTO-FIRE ON", H - 18);
            }

            for (Enemy e : enemies) {
                if (e.type.isBoss() && !e.dead && e.spawnTimer <= 0) {
                    g.setColor(new Color(50, 25, 55));
                    g.fillRoundRect(230, 46, 540, 18, 8, 8);
                    g.setColor(e.hp < e.maxHp / 2 ? new Color(255, 70, 90) : e.type.color);
                    g.fillRoundRect(230, 46, (int) (540 * Math.max(0, e.hp) / e.maxHp), 18, 8, 8);
                    g.setColor(Color.WHITE);
                    centered(g, e.type.name(), 60, 13, true);
                    break;
                }
            }
        }

        private void drawBar(Graphics2D g, int x, int y, int width, double fill, Color color) {
            g.setColor(new Color(40, 44, 80));
            g.fillRoundRect(x, y, width, 10, 6, 6);
            g.setColor(fill >= 1 ? color : color.darker());
            g.fillRoundRect(x, y, (int) (width * clamp(fill, 0, 1)), 10, 6, 6);
        }

        private int cardX(int index) {
            int width = 280, gap = 30, count = Math.max(1, choices.size());
            int total = count * width + (count - 1) * gap;
            return (W - total) / 2 + index * (width + gap);
        }

        int cardAt(int x, int y) {
            if (state != State.UPGRADE || y < 190 || y > 510) {
                return -1;
            }
            for (int i = 0; i < choices.size(); i++) {
                if (x >= cardX(i) && x <= cardX(i) + 280) {
                    return i;
                }
            }
            return -1;
        }

        Rectangle rerollButton() {
            return new Rectangle(375, 545, 250, 50);
        }

        /**
         * What an upgrade card costs, in coins picked up this run. Rarer cards cost more,
         * every level you already have makes the next one 30% dearer, and prices rise 10%
         * per wave after the first, because later waves drop more coins.
         */
        int cardCost(Upgrade upgrade) {
            int base = upgrade.rarity == Rarity.LEGENDARY ? 20 : upgrade.rarity == Rarity.RARE ? 10 : 5;
            double waveScale = 1 + 0.1 * Math.max(0, wave - 1);
            return (int) Math.round(base * (1 + 0.3 * stacks[upgrade.ordinal()]) * waveScale);
        }

        /** The price of card number index right now: the cheapest card is free when you cannot afford any. */
        int cardPrice(int index) {
            if (index < 0 || index >= choices.size()) {
                return 0;
            }
            int cheapest = -1;
            for (int i = 0; i < choices.size(); i++) {
                int cost = cardCost(choices.get(i));
                if (cost <= runCoins) {
                    return cardCost(choices.get(index));
                }
                if (cheapest < 0 || cost < cardCost(choices.get(cheapest))) {
                    cheapest = i;
                }
            }
            return index == cheapest ? 0 : cardCost(choices.get(index));
        }

        boolean canBuyCard(int index) {
            return index >= 0 && index < choices.size() && cardPrice(index) <= runCoins;
        }

        private void drawUpgradeScreen(Graphics2D g) {
            dim(g, 0.75f);
            g.setColor(new Color(255, 225, 120));
            centered(g, "CHOOSE AN UPGRADE", 110, 44, true);
            g.setColor(new Color(170, 180, 220));
            centered(g, "Click a card or press 1, 2 or 3. Upgrades cost the coins you picked up this run.", 145, 17, false);
            font(g, 20, true);
            String wallet = runCoins + " coins to spend";
            int walletX = (W - g.getFontMetrics().stringWidth(wallet)) / 2 + 12;
            drawCoin(g, walletX - 22, 176, 8);
            g.setColor(new Color(255, 220, 110));
            g.drawString(wallet, walletX, 183);
            for (int i = 0; i < choices.size(); i++) {
                drawCard(g, choices.get(i), i);
            }
            Rectangle button = rerollButton();
            boolean can = rerolls > 0;
            boolean hover = can && button.contains(mouseX, mouseY);
            g.setColor(hover ? new Color(60, 66, 120) : new Color(30, 33, 66));
            g.fillRoundRect(button.x, button.y, button.width, button.height, 16, 16);
            g.setColor(can ? new Color(170, 200, 255) : new Color(80, 85, 115));
            g.drawRoundRect(button.x, button.y, button.width, button.height, 16, 16);
            font(g, 18, true);
            centeredIn(g, "REROLL [R]  (" + rerolls + " left)", button, button.y + 32);
            if (noticeTimer > 0) {
                g.setColor(new Color(255, 130, 140));
                centered(g, notice, 630, 20, true);
            }
        }

        private void drawCard(Graphics2D g, Upgrade upgrade, int index) {
            int x = cardX(index), y = index == hoveredCard ? 200 : 210, width = 280, height = 300;
            Color color = upgrade.rarity.color;
            if (upgrade.rarity == Rarity.LEGENDARY) {
                // Legendary cards pulse with a golden glow.
                alpha(g, 0.25f + 0.15f * (float) Math.sin(clock * 5));
                g.setColor(color);
                g.fillRoundRect(x - 10, y - 10, width + 20, height + 20, 30, 30);
                alpha(g, 1);
            }
            g.setPaint(new GradientPaint(x, y, new Color(36, 40, 80), x, y + height, new Color(16, 18, 40)));
            g.fillRoundRect(x, y, width, height, 22, 22);
            g.setColor(color);
            g.setStroke(new BasicStroke(index == hoveredCard ? 5 : 3));
            g.drawRoundRect(x, y, width, height, 22, 22);
            g.setStroke(new BasicStroke(1));
            font(g, 13, true);
            Rectangle box = new Rectangle(x, y, width, height);
            centeredIn(g, upgrade.rarity.label, box, y + 28);
            g.fillPolygon(regular(x + width / 2.0, y + 84, 34, 6, clock + index));
            g.setColor(new Color(15, 15, 30));
            font(g, 28, true);
            centeredIn(g, String.valueOf(index + 1), box, y + 94);

            g.setColor(Color.WHITE);
            font(g, 23, true);
            centeredIn(g, upgrade.title, box, y + 158);
            g.setColor(new Color(190, 198, 235));
            font(g, 17, false);
            int line = 0;
            for (String text : wrap(g, upgrade.description, width - 40)) {
                centeredIn(g, text, box, y + 190 + line * 24);
                line++;
            }
            g.setColor(color);
            font(g, 14, true);
            centeredIn(g, "Level " + stacks[upgrade.ordinal()] + " / " + upgrade.max, box, y + height - 18);

            int price = cardPrice(index);
            boolean affordable = canBuyCard(index);
            if (!affordable) {
                // Cards you cannot pay for are dimmed; the price stays bright on top.
                alpha(g, 0.5f);
                g.setColor(new Color(8, 8, 20));
                g.fillRoundRect(x, y, width, height, 22, 22);
                alpha(g, 1);
            }
            if (price == 0) {
                font(g, 20, true);
                g.setColor(new Color(150, 255, 170));
                centeredIn(g, "FREE", box, y + height - 46);
            } else {
                font(g, 20, true);
                String text = affordable ? String.valueOf(price) : price + " (need " + (price - runCoins) + ")";
                int textX = x + (width - g.getFontMetrics().stringWidth(text)) / 2 + 12;
                drawCoin(g, textX - 20, y + height - 53, 8);
                g.setColor(affordable ? new Color(255, 220, 110) : new Color(255, 110, 120));
                g.drawString(text, textX, y + height - 46);
            }
        }

        private List<String> wrap(Graphics2D g, String text, int width) {
            List<String> lines = new ArrayList<String>();
            String current = "";
            for (String word : text.split(" ")) {
                String next = current.isEmpty() ? word : current + " " + word;
                if (g.getFontMetrics().stringWidth(next) > width && !current.isEmpty()) {
                    lines.add(current);
                    current = word;
                } else {
                    current = next;
                }
            }
            lines.add(current);
            return lines;
        }

        private void drawMenu(Graphics2D g) {
            double pulse = Math.sin(clock * 2.5);
            font(g, 84, true);
            String title = "NEON OVERDRIVE";
            int titleX = (W - g.getFontMetrics().stringWidth(title)) / 2;
            alpha(g, 0.4f);
            g.setColor(new Color(255, 70, 200));
            g.drawString(title, titleX + 5 + (int) (pulse * 5), 165);
            alpha(g, 1);
            g.setPaint(new GradientPaint(0, 95, new Color(160, 250, 255), 0, 165, new Color(50, 170, 255)));
            g.drawString(title, titleX, 160);

            EnemyType[] parade = {EnemyType.CHASER, EnemyType.SPEEDER, EnemyType.BOMBER, EnemyType.SPLITTER,
                EnemyType.GHOST, EnemyType.SHOOTER, EnemyType.TANK};
            px = W / 2.0;
            py = 700;
            for (int i = 0; i < parade.length; i++) {
                Enemy sample = new Enemy(parade[i], 200 + i * 100, 228 + Math.sin(clock * 3 + i) * 8, 1);
                sample.angle = clock * 2;
                drawEnemy(g, sample);
            }

            g.setColor(Color.WHITE);
            centered(g, "4 ships. 3 bosses. 20 upgrades. A new challenge every day.", 300, 24, false);
            g.setColor(new Color(190, 198, 235));
            String[] controls = {
                "Move: WASD or arrow keys     Aim: mouse",
                "Shoot: hold left click  (F = auto-fire)",
                "Dash: SPACE     Special power: E or right click     Bomb: Q",
                "Pause: P     Sound: M     Music: N     Fullscreen: F11",
            };
            for (int i = 0; i < controls.length; i++) {
                centered(g, controls[i], 355 + i * 31, 19, false);
            }
            if (highScore > 0) {
                g.setColor(new Color(255, 225, 120));
                centered(g, "Best score: " + highScore, 510, 22, true);
            }
            if ((int) (clock * 2) % 2 == 0 && !confirmReset) {
                g.setColor(new Color(150, 255, 170));
                centered(g, "Click or press ENTER", 585, 30, true);
            }
            if (noticeTimer > 0) {
                g.setColor(new Color(255, 225, 120));
                centered(g, notice, 640, 18, true);
            }

            Rectangle reset = resetButton();
            boolean hover = !confirmReset && reset.contains(mouseX, mouseY);
            g.setColor(hover ? new Color(70, 26, 40) : new Color(34, 18, 30));
            g.fillRoundRect(reset.x, reset.y, reset.width, reset.height, 14, 14);
            g.setColor(hover ? new Color(255, 110, 120) : new Color(150, 70, 85));
            g.setStroke(new BasicStroke(hover ? 2.5f : 1.5f));
            g.drawRoundRect(reset.x, reset.y, reset.width, reset.height, 14, 14);
            g.setStroke(new BasicStroke(1));
            font(g, 14, true);
            centeredIn(g, "RESET PROGRESS  [DEL]", reset, reset.y + 26);

            if (confirmReset) {
                drawResetConfirm(g);
            }
        }

        // ============================ RESET ================================

        /** True while the "are you sure?" box for wiping the save is open. */
        boolean confirmReset;

        Rectangle resetButton() {
            return new Rectangle(W - 230, H - 58, 210, 40);
        }

        Rectangle resetYesButton() {
            return new Rectangle(W / 2 - 190, 420, 180, 56);
        }

        Rectangle resetNoButton() {
            return new Rectangle(W / 2 + 10, 420, 180, 56);
        }

        private void drawResetConfirm(Graphics2D g) {
            dim(g, 0.8f);
            Rectangle box = new Rectangle(W / 2 - 260, 200, 520, 310);
            g.setColor(new Color(24, 20, 44));
            g.fillRoundRect(box.x, box.y, box.width, box.height, 22, 22);
            g.setColor(new Color(255, 110, 120));
            g.setStroke(new BasicStroke(3));
            g.drawRoundRect(box.x, box.y, box.width, box.height, 22, 22);
            g.setStroke(new BasicStroke(1));
            font(g, 30, true);
            centeredIn(g, "RESET ALL PROGRESS?", box, box.y + 54);
            g.setColor(new Color(200, 205, 235));
            font(g, 16, false);
            String[] lines = {
                "Coins, diamonds, ships, skins, permanent upgrades,",
                "achievements, records and the saved run are all wiped.",
                "Sound, music and auto-fire settings are kept.",
            };
            for (int i = 0; i < lines.length; i++) {
                centeredIn(g, lines[i], box, box.y + 100 + i * 26);
            }
            g.setColor(new Color(255, 200, 120));
            font(g, 15, true);
            centeredIn(g, "This cannot be undone in the game.", box, box.y + 190);

            Rectangle yes = resetYesButton(), no = resetNoButton();
            boolean hoverYes = yes.contains(mouseX, mouseY), hoverNo = no.contains(mouseX, mouseY);
            g.setColor(hoverYes ? new Color(220, 60, 80) : new Color(160, 40, 60));
            g.fillRoundRect(yes.x, yes.y, yes.width, yes.height, 16, 16);
            g.setColor(hoverNo ? new Color(70, 80, 140) : new Color(44, 50, 96));
            g.fillRoundRect(no.x, no.y, no.width, no.height, 16, 16);
            g.setColor(Color.WHITE);
            font(g, 19, true);
            centeredIn(g, "YES, RESET  [Y]", yes, yes.y + 35);
            centeredIn(g, "CANCEL  [N]", no, no.y + 35);
        }

        /** Wipes the save back to a brand-new game. Keeps sound, music and auto-fire settings. */
        void resetProgress() {
            confirmReset = false;
            backupSave();
            if (runActive) {
                runActive = false;
            }
            deleteRunFile();
            coins = 0;
            diamonds = 0;
            highScore = 0;
            Arrays.fill(unlocked, false);
            unlocked[Ship.STRIKER.ordinal()] = true;
            unlocked[Ship.BLASTER.ordinal()] = true;
            Arrays.fill(ownedSkins, false);
            ownedSkins[Skin.DEFAULT.ordinal()] = true;
            skin = Skin.DEFAULT;
            Arrays.fill(perks, 0);
            ship = Ship.STRIKER;
            difficulty = Difficulty.NORMAL;
            savedShip = Ship.STRIKER;
            savedDifficulty = Difficulty.NORMAL;
            totalRuns = 0;
            totalKills = 0;
            totalBosses = 0;
            bestWave = 0;
            bestComboEver = 0;
            playSeconds = 0;
            Arrays.fill(shipBestWave, 0);
            Arrays.fill(bossKills, 0);
            Arrays.fill(achieved, false);
            dailyLastDate = "";
            dailyBest = 0;
            dailyStreak = 0;
            toasts.clear();
            toastTimer = 0;
            save();
            state = State.MENU;
            sound.play(Sound.Effect.BIG_BOOM);
            showNotice("Progress reset. A fresh start!");
        }

        /** Keeps a copy of the old save next to it, just in case the reset was a mistake. */
        private void backupSave() {
            if (saveFile == null || !saveFile.exists()) {
                return;
            }
            File backup = new File(saveFile.getAbsoluteFile().getParentFile(),
                    saveFile.getName().replaceFirst("\\.properties$", "") + "_before_reset.properties");
            try {
                java.nio.file.Files.copy(saveFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                System.err.println("Could not back up the save before resetting: " + e.getMessage());
            }
        }

        private void drawHangar(Graphics2D g) {
            g.setColor(Color.WHITE);
            font(g, 36, true);
            g.drawString("HANGAR", 40, 62);
            drawWallet(g, 60);
            drawSideButton(g, skinsButton(), "SKINS  [K]", skin.title + " equipped", DIAMOND_COLOR);

            for (Ship s : Ship.values()) {
                drawShipCard(g, s);
            }
            font(g, 15, true);
            g.setColor(new Color(170, 180, 220));
            centered(g, "DIFFICULTY", 318, 14, true);
            for (Difficulty d : Difficulty.values()) {
                drawDifficultyButton(g, d);
            }
            centered(g, "PERMANENT UPGRADES", 394, 14, true);
            for (Perk p : Perk.values()) {
                drawPerkCard(g, p);
            }

            Rectangle launch = launchButton();
            boolean hover = launch.contains(mouseX, mouseY);
            g.setPaint(new GradientPaint(0, launch.y, hover ? new Color(120, 255, 170) : new Color(80, 225, 140),
                    0, launch.y + launch.height, new Color(30, 150, 90)));
            g.fillRoundRect(launch.x, launch.y, launch.width, launch.height, 22, 22);
            g.setColor(new Color(10, 30, 20));
            if (hasSavedRun()) {
                font(g, 20, true);
                centeredIn(g, "NEW RUN", launch, launch.y + 32);
                font(g, 13, true);
                centeredIn(g, "[ENTER]", launch, launch.y + 54);

                Rectangle resume = continueButton();
                boolean hoverResume = resume.contains(mouseX, mouseY);
                g.setPaint(new GradientPaint(0, resume.y, hoverResume ? new Color(255, 225, 120) : new Color(255, 200, 80),
                        0, resume.y + resume.height, new Color(190, 120, 30)));
                g.fillRoundRect(resume.x, resume.y, resume.width, resume.height, 22, 22);
                g.setColor(new Color(40, 22, 5));
                font(g, 20, true);
                centeredIn(g, "CONTINUE [C]", resume, resume.y + 32);
                font(g, 13, true);
                centeredIn(g, savedRunInfo, resume, resume.y + 54);
            } else {
                font(g, 30, true);
                centeredIn(g, "LAUNCH  [ENTER]", launch, launch.y + 45);
            }

            int earned = 0;
            for (boolean done : achieved) {
                earned += done ? 1 : 0;
            }
            drawSideButton(g, recordsButton(), "RECORDS  [T]", earned + " / " + achieved.length + " achievements",
                    new Color(170, 200, 255));
            int best = dailyBestToday();
            drawSideButton(g, dailyButton(), "DAILY RUN  [G]",
                    dailyShip().title + " - " + dailyModifier().title + (best > 0 ? " - Best " + best : ""),
                    new Color(120, 220, 255));

            if (noticeTimer > 0) {
                g.setColor(new Color(255, 225, 120));
                centered(g, notice, 662, 20, true);
            } else if (dailyButton().contains(mouseX, mouseY)) {
                g.setColor(new Color(120, 220, 255));
                centered(g, "Today: " + dailyModifier().description, 662, 16, true);
            } else if (hasSavedRun() && launch.contains(mouseX, mouseY)) {
                g.setColor(new Color(255, 210, 110));
                centered(g, "A new run ends your saved run. You keep its coins.", 662, 16, true);
            } else if (hasSavedRun()) {
                g.setColor(new Color(255, 210, 110));
                centered(g, "You have a saved run. Press C to continue from the start of its wave.", 662, 16, false);
            } else {
                g.setColor(new Color(130, 140, 180));
                centered(g, "Click a ship to choose or buy it. Earn coins by playing.", 662, 16, false);
            }
        }

        private void drawSideButton(Graphics2D g, Rectangle box, String title, String detail, Color color) {
            boolean hover = box.contains(mouseX, mouseY);
            g.setColor(hover ? new Color(36, 44, 90) : new Color(20, 22, 48));
            g.fillRoundRect(box.x, box.y, box.width, box.height, 18, 18);
            g.setColor(color);
            g.setStroke(new BasicStroke(hover ? 3 : 1.5f));
            g.drawRoundRect(box.x, box.y, box.width, box.height, 18, 18);
            g.setStroke(new BasicStroke(1));
            font(g, 19, true);
            centeredIn(g, title, box, box.y + 30);
            g.setColor(new Color(190, 198, 235));
            font(g, 13, false);
            centeredIn(g, detail, box, box.y + 53);
        }

        private static String formatTime(double seconds) {
            int total = (int) seconds;
            if (total >= 3600) {
                return total / 3600 + "h " + total / 60 % 60 + "m";
            }
            return total / 60 + ":" + (total % 60 < 10 ? "0" : "") + total % 60;
        }

        private void drawRecords(Graphics2D g) {
            g.setColor(Color.WHITE);
            font(g, 36, true);
            g.drawString("RECORDS", 40, 62);

            String[][] rows = {
                {"Runs played", String.valueOf(totalRuns)},
                {"Time played", formatTime(playSeconds)},
                {"Enemies destroyed", String.valueOf(totalKills)},
                {"Bosses defeated", String.valueOf(totalBosses)},
                {"Best score", String.valueOf(highScore)},
                {"Best wave", String.valueOf(bestWave)},
                {"Best combo", String.valueOf(bestComboEver)},
                {"Daily streak", currentStreak() + (currentStreak() == 1 ? " day" : " days")},
                {"Daily best today", String.valueOf(dailyBestToday())},
            };
            int y = 110;
            for (String[] row : rows) {
                drawRecordRow(g, row[0], row[1], y, new Color(190, 198, 235));
                y += 34;
            }
            y += 6;
            font(g, 14, true);
            g.setColor(new Color(170, 180, 220));
            g.drawString("BEST WAVE BY SHIP", 40, y);
            y += 28;
            for (Ship s : Ship.values()) {
                drawRecordRow(g, s.title, String.valueOf(shipBestWave[s.ordinal()]), y, s.color);
                y += 30;
            }

            int rowY = 84;
            for (Achievement a : Achievement.values()) {
                boolean done = achieved[a.ordinal()];
                g.setColor(done ? new Color(30, 44, 62) : new Color(20, 22, 48));
                g.fillRoundRect(360, rowY, 600, 40, 12, 12);
                g.setColor(done ? new Color(120, 240, 160) : new Color(60, 64, 100));
                g.fillOval(372, rowY + 11, 18, 18);
                g.setColor(done ? Color.WHITE : new Color(150, 155, 185));
                font(g, 14, true);
                g.drawString(a.title, 402, rowY + 17);
                g.setColor(new Color(170, 180, 220));
                font(g, 12, false);
                g.drawString(a.description, 402, rowY + 33);
                g.setColor(done ? new Color(120, 240, 160) : new Color(255, 220, 110));
                font(g, 13, true);
                String reward = done ? "EARNED" : "+" + a.reward + " coins  +" + a.diamonds + " diamonds";
                g.drawString(reward, 948 - g.getFontMetrics().stringWidth(reward), rowY + 25);
                rowY += 44;
            }
            g.setColor(new Color(130, 140, 180));
            centered(g, "Click or press ESC to go back", 680, 15, false);
        }

        private void drawRecordRow(Graphics2D g, String label, String value, int y, Color labelColor) {
            font(g, 16, false);
            g.setColor(labelColor);
            g.drawString(label, 40, y);
            font(g, 16, true);
            g.setColor(Color.WHITE);
            g.drawString(value, 330 - g.getFontMetrics().stringWidth(value), y);
        }

        /** Coins and diamonds in the top right corner, right-aligned. */
        private void drawWallet(Graphics2D g, int y) {
            font(g, 24, true);
            String gems = String.valueOf(diamonds), cash = String.valueOf(coins);
            int gemsX = W - 40 - g.getFontMetrics().stringWidth(gems);
            g.setColor(DIAMOND_COLOR);
            g.drawString(gems, gemsX, y);
            drawDiamond(g, gemsX - 20, y - 9, 10);
            int cashX = gemsX - 60 - g.getFontMetrics().stringWidth(cash);
            g.setColor(new Color(255, 220, 110));
            g.drawString(cash, cashX, y);
            drawCoin(g, cashX - 24, y - 9, 9);
        }

        Rectangle skinsButton() {
            return new Rectangle(250, 16, 190, 66);
        }

        Rectangle skinCard(int index) {
            return new Rectangle(40 + index % 4 * 235, 104 + index / 4 * 246, 215, 230);
        }

        Rectangle skinsBackButton() {
            return new Rectangle(40, 604, 220, 70);
        }

        void buyOrEquipSkin(Skin k) {
            if (ownedSkins[k.ordinal()]) {
                skin = k;
                showNotice(k.title + " equipped");
            } else if (diamonds >= k.cost) {
                diamonds -= k.cost;
                ownedSkins[k.ordinal()] = true;
                skin = k;
                sound.play(Sound.Effect.UPGRADE);
                showNotice(k.title + " unlocked and equipped!");
            } else {
                sound.play(Sound.Effect.DENY);
                showNotice("You need " + (k.cost - diamonds) + " more diamonds for " + k.title);
            }
            save();
        }

        private void skinsClick(int x, int y) {
            for (Skin k : Skin.values()) {
                if (skinCard(k.ordinal()).contains(x, y)) {
                    buyOrEquipSkin(k);
                    return;
                }
            }
            if (skinsBackButton().contains(x, y)) {
                state = State.HANGAR;
            }
        }

        private void drawSkins(Graphics2D g) {
            g.setColor(Color.WHITE);
            font(g, 36, true);
            g.drawString("SKINS", 40, 62);
            drawWallet(g, 60);
            font(g, 14, false);
            g.setColor(new Color(170, 180, 220));
            g.drawString("Skins recolor any ship. Showing: " + ship.title + "  (arrow keys to switch)", 170, 58);

            for (Skin k : Skin.values()) {
                Rectangle box = skinCard(k.ordinal());
                boolean owned = ownedSkins[k.ordinal()], equipped = k == skin;
                boolean hover = box.contains(mouseX, mouseY);
                Color color = colorFor(ship, k);
                g.setColor(equipped ? new Color(36, 44, 90) : hover ? new Color(30, 34, 70) : new Color(20, 22, 48));
                g.fillRoundRect(box.x, box.y, box.width, box.height, 18, 18);
                g.setColor(equipped ? color : new Color(70, 76, 120));
                g.setStroke(new BasicStroke(equipped ? 4 : 1.5f));
                g.drawRoundRect(box.x, box.y, box.width, box.height, 18, 18);
                g.setStroke(new BasicStroke(1));

                double spin = equipped || hover ? clock * 1.2 : -Math.PI / 2;
                Polygon shape = shipShape(ship, box.getCenterX(), box.y + 70, spin, 1.8);
                glowPolygon(g, shape, color, owned ? 14 : 6);
                g.setColor(owned ? color : color.darker().darker());
                g.fillPolygon(shape);

                g.setColor(Color.WHITE);
                font(g, 20, true);
                centeredIn(g, k.title, box, box.y + 148);
                g.setColor(new Color(170, 180, 220));
                font(g, 12, false);
                centeredIn(g, k.description, box, box.y + 170);
                if (equipped) {
                    font(g, 15, true);
                    g.setColor(color);
                    centeredIn(g, "EQUIPPED", box, box.y + 208);
                } else if (owned) {
                    font(g, 14, true);
                    g.setColor(new Color(150, 255, 170));
                    centeredIn(g, "OWNED - click to equip", box, box.y + 208);
                } else {
                    font(g, 16, true);
                    String price = String.valueOf(k.cost);
                    int width = g.getFontMetrics().stringWidth(price) + 22;
                    int left = (int) box.getCenterX() - width / 2;
                    drawDiamond(g, left + 7, box.y + 202, 8);
                    g.setColor(diamonds >= k.cost ? DIAMOND_COLOR : new Color(255, 120, 130));
                    g.drawString(price, left + 22, box.y + 208);
                }
            }

            drawSideButton(g, skinsBackButton(), "BACK  [ESC]", "Return to the hangar", new Color(170, 200, 255));
            if (noticeTimer > 0) {
                g.setColor(new Color(255, 225, 120));
                centered(g, notice, 645, 20, true);
            } else {
                g.setColor(new Color(130, 140, 180));
                centered(g, "Earn diamonds from bosses, achievements, daily runs and rare drops.", 645, 16, false);
            }
        }

        private void drawShipCard(Graphics2D g, Ship s) {
            Rectangle box = shipCard(s.ordinal());
            boolean open = unlocked[s.ordinal()], selected = s == ship;
            boolean hover = box.contains(mouseX, mouseY);
            g.setColor(selected ? new Color(36, 44, 90) : hover ? new Color(30, 34, 70) : new Color(20, 22, 48));
            g.fillRoundRect(box.x, box.y, box.width, box.height, 18, 18);
            g.setColor(selected ? s.color : new Color(70, 76, 120));
            g.setStroke(new BasicStroke(selected ? 4 : 1.5f));
            g.drawRoundRect(box.x, box.y, box.width, box.height, 18, 18);
            g.setStroke(new BasicStroke(1));

            double spin = selected ? clock * 1.2 : -Math.PI / 2;
            Polygon shape = shipShape(s, box.getCenterX(), box.y + 44, spin, 1.3);
            // The selected ship shows off the equipped skin.
            Color body = selected ? colorFor(s, skin) : s.color;
            if (open) {
                glowPolygon(g, shape, body, 12);
            }
            g.setColor(open ? body : new Color(70, 74, 100));
            g.fillPolygon(shape);

            g.setColor(open ? Color.WHITE : new Color(150, 155, 180));
            font(g, 20, true);
            centeredIn(g, s.title, box, box.y + 100);
            g.setColor(new Color(170, 180, 220));
            font(g, 12, false);
            centeredIn(g, s.role, box, box.y + 118);
            int statY = box.y + 130;
            drawStat(g, box.x + 14, statY, "HP", s.health / 8.0, s.color);
            drawStat(g, box.x + 14, statY + 13, "SPD", s.speed / 330.0, s.color);
            drawStat(g, box.x + 14, statY + 26, "DMG", s.damage * s.shots / s.fireDelay / 11.0, s.color);
            g.setColor(new Color(200, 205, 235));
            font(g, 11, false);
            centeredIn(g, s.specialText, box, box.y + 181);
            if (!open) {
                font(g, 12, true);
                g.setColor(DIAMOND_COLOR);
                centeredIn(g, s.cost + " diamonds " + s.unlockHint(), box, box.y + 198);
            } else if (selected) {
                font(g, 13, true);
                g.setColor(s.color);
                centeredIn(g, "SELECTED", box, box.y + 198);
            }
        }

        private void drawStat(Graphics2D g, int x, int y, String label, double value, Color color) {
            font(g, 10, true);
            g.setColor(new Color(150, 160, 200));
            g.drawString(label, x, y + 8);
            g.setColor(new Color(40, 44, 80));
            g.fillRect(x + 32, y + 1, 150, 7);
            g.setColor(color);
            g.fillRect(x + 32, y + 1, (int) (150 * clamp(value, 0.05, 1)), 7);
        }

        private void drawDifficultyButton(Graphics2D g, Difficulty d) {
            Rectangle box = difficultyButton(d.ordinal());
            boolean selected = d == difficulty;
            g.setColor(selected ? new Color(36, 44, 90) : new Color(20, 22, 48));
            g.fillRoundRect(box.x, box.y, box.width, box.height, 14, 14);
            g.setColor(selected ? d.color : new Color(70, 76, 120));
            g.setStroke(new BasicStroke(selected ? 3 : 1));
            g.drawRoundRect(box.x, box.y, box.width, box.height, 14, 14);
            g.setStroke(new BasicStroke(1));
            g.setColor(selected ? d.color : new Color(170, 180, 220));
            font(g, 17, true);
            centeredIn(g, (d.ordinal() + 1) + ". " + d.title + "  x" + d.scoreMultiplier, box, box.y + 28);
        }

        private void drawPerkCard(Graphics2D g, Perk p) {
            Rectangle box = perkCard(p.ordinal());
            int level = perks[p.ordinal()];
            boolean maxed = level >= p.max;
            boolean hover = box.contains(mouseX, mouseY);
            g.setColor(hover && !maxed ? new Color(30, 34, 70) : new Color(20, 22, 48));
            g.fillRoundRect(box.x, box.y, box.width, box.height, 14, 14);
            g.setColor(new Color(70, 76, 120));
            g.drawRoundRect(box.x, box.y, box.width, box.height, 14, 14);
            g.setColor(Color.WHITE);
            font(g, 16, true);
            centeredIn(g, p.title, box, box.y + 26);
            g.setColor(new Color(170, 180, 220));
            font(g, 12, false);
            centeredIn(g, p.description, box, box.y + 46);
            for (int i = 0; i < p.max; i++) {
                int dotX = (int) box.getCenterX() - p.max * 10 + i * 20 + 3;
                g.setColor(i < level ? new Color(120, 240, 160) : new Color(50, 54, 90));
                g.fillOval(dotX, box.y + 58, 14, 14);
            }
            font(g, 14, true);
            if (maxed) {
                g.setColor(new Color(120, 240, 160));
                centeredIn(g, "MAXED", box, box.y + 100);
            } else {
                int cost = p.costAt(level);
                g.setColor(coins >= cost ? new Color(255, 220, 110) : new Color(150, 110, 110));
                centeredIn(g, "BUY: " + cost + " coins", box, box.y + 100);
            }
        }

        private void drawGameOver(Graphics2D g) {
            dim(g, 0.7f);
            g.setColor(new Color(255, 90, 120));
            centered(g, dailyRun ? "DAILY RUN OVER" : "GAME OVER", 170, dailyRun ? 60 : 74, true);
            g.setColor(Color.WHITE);
            centered(g, "Score: " + score, 236, 38, true);
            if (newRecord) {
                g.setColor(new Color(255, 225, 90));
                centered(g, dailyRun ? "NEW BEST TODAY!" : "NEW RECORD!", 274, 26, true);
            } else {
                g.setColor(new Color(170, 180, 220));
                centered(g, dailyRun ? "Best today: " + dailyBestToday() : "Best: " + highScore, 274, 22, false);
            }
            g.setColor(new Color(190, 198, 235));
            String setup = dailyRun ? "Daily: " + modifier.title : difficulty.title;
            centered(g, ship.title + "  |  " + setup + "  |  Reached wave " + wave, 322, 21, false);
            centered(g, "Kills: " + kills + "     Best combo: " + bestCombo + "     Bosses: " + runBosses, 354, 21, false);
            centered(g, "Time: " + formatTime(runTime) + "     Damage dealt: " + Math.round(runDamage), 386, 21, false);
            g.setColor(new Color(255, 220, 110));
            centered(g, "+" + runCoins + " coins  (total " + coins + ")", 424, 24, true);
            g.setColor(DIAMOND_COLOR);
            centered(g, "+" + runDiamonds + " diamonds  (total " + diamonds + ")", 448, 18, true);
            if (!dailyNote.isEmpty()) {
                g.setColor(new Color(120, 220, 255));
                centered(g, dailyNote, 466, 18, true);
            }
            if (runAchievements > 0) {
                g.setColor(new Color(255, 200, 80));
                centered(g, "Achievements earned: " + runAchievements, 494, 18, true);
            }
            if (overDelay <= 0 && (int) (clock * 2) % 2 == 0) {
                g.setColor(new Color(150, 255, 170));
                centered(g, "Click or press ENTER for the hangar", 548, 28, true);
            }
        }

        static double clamp(double value, double low, double high) {
            return Math.max(low, Math.min(high, value));
        }
    }
}
