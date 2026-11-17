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
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
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
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * NEON SWARM - a 2D arcade arena shooter.
 *
 * Survive wave after wave, pick an upgrade after each one, and beat a boss
 * every fifth wave. How far can you get?
 *
 * Compile:  javac NeonSwarm.java
 * Run:      java NeonSwarm
 *
 * Controls: WASD / arrows move, mouse aims, hold left click to shoot,
 *           SPACE dashes, Q drops a bomb, F toggles auto-fire,
 *           P pauses, M mutes.
 */
public class NeonSwarm {

    static final int W = 1000, H = 700;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("Neon Swarm");
                GamePanel panel = new GamePanel();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setResizable(false);
                frame.add(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                panel.requestFocusInWindow();
                panel.start();
            }
        });
    }

    enum State { MENU, PLAYING, UPGRADE, PAUSED, GAME_OVER }

    enum EnemyType {
        //       radius  hp   speed  score  color
        CHASER(  14,    3.0,  105,   10, new Color(255, 80, 100)),
        SPEEDER( 11,    1.5,  270,   15, new Color(255, 215, 70)),
        TANK(    27,   18.0,   60,   50, new Color(180, 100, 255)),
        SPLITTER(20,    8.0,   85,   25, new Color(90, 235, 130)),
        MINI(    10,    1.5,  150,    5, new Color(150, 255, 170)),
        SHOOTER( 15,    5.0,   90,   30, new Color(70, 215, 255)),
        BOSS(    56,  200.0,   70, 1000, new Color(255, 70, 200));

        final int radius;
        final double hp, speed;
        final int score;
        final Color color;

        EnemyType(int radius, double hp, double speed, int score, Color color) {
            this.radius = radius;
            this.hp = hp;
            this.speed = speed;
            this.score = score;
            this.color = color;
        }
    }

    enum Upgrade {
        RAPID("Rapid Fire", "Shoot 20% faster", 6, new Color(255, 215, 70)),
        POWER("Power Shot", "+50% bullet damage", 6, new Color(255, 90, 90)),
        MULTI("Multi Shot", "+1 bullet in every shot", 4, new Color(90, 200, 255)),
        PIERCE("Piercing", "Bullets pass through +1 enemy", 3, new Color(200, 160, 255)),
        BOUNCE("Ricochet", "Bullets bounce off walls +1 time", 3, new Color(120, 255, 200)),
        EXPLOSIVE("Explosive Rounds", "Bullets explode when they hit", 3, new Color(255, 140, 60)),
        ORBITALS("Orbital Guard", "+1 orb circles you: hurts enemies, blocks bullets", 4, new Color(120, 220, 255)),
        HOMING("Homing", "Bullets curve toward enemies", 3, new Color(255, 120, 220)),
        SWIFT("Swift", "Move 12% faster", 4, new Color(170, 255, 120)),
        VITALITY("Vitality", "+1 max health and heal fully", 5, new Color(255, 100, 130)),
        DASH("Dash Strike", "Dash recharges faster and damages enemies", 3, new Color(150, 240, 255)),
        CRIT("Critical Hits", "15% more chance of triple damage", 4, new Color(255, 240, 150)),
        BOMBS("Bomb Stash", "+1 bomb right now", 5, new Color(255, 170, 90));

        final String title, description;
        final int max;
        final Color color;

        Upgrade(String title, String description, int max, Color color) {
            this.title = title;
            this.description = description;
            this.max = max;
            this.color = color;
        }
    }

    static final class Enemy {
        final EnemyType type;
        double x, y, vx, vy, hp, maxHp, flash, spawnTimer, attackTimer, angle, contactCooldown;
        double wobble;
        // Boss only.
        int bossStep;
        double chargeWarn, chargeTime, spiralTime, aimX, aimY;
        boolean dead;

        Enemy(EnemyType type, double x, double y, double hp) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.hp = hp;
            this.maxHp = hp;
        }
    }

    static final class Bullet {
        double x, y, vx, vy, r, damage, life;
        int pierce, bounces;
        boolean enemy, dead;
        Color color;
        List<Enemy> alreadyHit;
    }

    static final class Particle {
        double x, y, vx, vy, life, maxLife, size;
        Color color;
    }

    static final class Ring {
        double x, y, radius, maxRadius, life, maxLife;
        Color color;
    }

    static final class FloatText {
        double x, y, life;
        String text;
        Color color;
        int size;
    }

    enum PickupType { HEART, BOMB }

    static final class Pickup {
        PickupType type;
        double x, y, life;
    }

    /** Small retro sound effects, generated in code so the game needs no sound files. */
    static final class Sound {
        enum Effect { SHOOT, HIT, BOOM, BIG_BOOM, HURT, PICKUP, DASH, UPGRADE, WAVE, BOSS }

        private static final float RATE = 22050f;
        private final Map<Effect, Clip> clips = new EnumMap<Effect, Clip>(Effect.class);
        private final Map<Effect, Long> lastPlayed = new EnumMap<Effect, Long>(Effect.class);
        private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(6), new ThreadPoolExecutor.DiscardPolicy());
        volatile boolean muted;

        Sound() {
            worker.setThreadFactory(r -> {
                Thread thread = new Thread(r, "sound");
                thread.setDaemon(true);
                return thread;
            });
        }

        /** Prepares the effects in the background. A computer with no speakers just stays silent. */
        void load() {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        make(Effect.SHOOT, 900, 350, 0.07, 0.16, 0.05);
                        make(Effect.HIT, 500, 250, 0.05, 0.18, 0.3);
                        make(Effect.BOOM, 220, 40, 0.22, 0.4, 0.8);
                        make(Effect.BIG_BOOM, 130, 25, 0.8, 0.7, 0.85);
                        make(Effect.HURT, 300, 70, 0.3, 0.55, 0.45);
                        make(Effect.PICKUP, 600, 1300, 0.14, 0.35, 0);
                        make(Effect.DASH, 250, 900, 0.14, 0.3, 0.5);
                        make(Effect.UPGRADE, 450, 1500, 0.3, 0.4, 0);
                        make(Effect.WAVE, 300, 800, 0.4, 0.35, 0);
                        make(Effect.BOSS, 90, 55, 1.0, 0.6, 0.2);
                    } catch (Exception | LinkageError e) {
                        System.err.println("Sound is off: " + e.getMessage());
                    }
                }
            });
        }

        private void make(Effect effect, double startHz, double endHz, double seconds, double volume, double noise)
                throws Exception {
            int length = (int) (RATE * seconds);
            byte[] data = new byte[length];
            Random random = new Random(effect.ordinal());
            double phase = 0;
            for (int i = 0; i < length; i++) {
                double t = i / (double) length;
                phase += (startHz + (endHz - startHz) * t) / RATE;
                double square = (phase % 1) < 0.5 ? 1 : -1;
                double sample = square * (1 - noise) + (random.nextDouble() * 2 - 1) * noise;
                data[i] = (byte) (sample * volume * (1 - t) * 127);
            }
            Clip clip = AudioSystem.getClip();
            clip.open(new AudioFormat(RATE, 8, 1, true, false), data, 0, length);
            clips.put(effect, clip);
        }

        int loadedCount() {
            return clips.size();
        }

        void play(final Effect effect) {
            if (muted) {
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
                    Clip clip = clips.get(effect);
                    if (clip != null) {
                        clip.stop();
                        clip.setFramePosition(0);
                        clip.start();
                    }
                }
            });
        }
    }

    static final class GamePanel extends JPanel {
        static final double PLAYER_RADIUS = 11, BASE_SPEED = 265, DASH_SPEED = 900, DASH_TIME = 0.15;
        static final int START_HEALTH = 5, START_BOMBS = 1, MAX_PARTICLES = 700;

        static final Color BG = new Color(9, 10, 22), PLAYER_COLOR = new Color(90, 240, 255);

        final Timer timer;
        final Sound sound = new Sound();
        Random rng = new Random();
        private final Random fx = new Random();
        private final BufferedImage background = makeBackground();

        File scoreFile = new File("neonswarm_highscore.txt");

        State state = State.MENU;
        double clock;
        long lastTick;

        // Input.
        boolean up, down, left, right, firing, autoFire;
        double aimX = W / 2.0, aimY = H / 3.0;

        // Player.
        double px, py, facing, invulnerable, dashTimer, dashCooldown, dashDirX, dashDirY, fireCooldown, hurtFlash;
        int health, maxHealth, bombs;

        // Weapon, changed by upgrades.
        double fireDelay, bulletDamage, moveSpeed, dashDelay, critChance, dashDamage, homing, explosiveRadius;
        int bulletCount, pierce, bounces, orbitals;
        double orbitAngle;
        final int[] stacks = new int[Upgrade.values().length];

        // Progress.
        int wave, score, highScore, kills, combo, bestCombo;
        double comboTimer, spawnTimer, spawnDelay, clearTimer, bannerTimer, cardDelay, overDelay;
        boolean clearing, newRecord;
        String banner = "";
        final List<EnemyType> spawnQueue = new ArrayList<EnemyType>();
        final List<Upgrade> choices = new ArrayList<Upgrade>();
        int hoveredCard = -1;

        // World.
        final List<Enemy> enemies = new ArrayList<Enemy>();
        final List<Enemy> newEnemies = new ArrayList<Enemy>();
        final List<Bullet> bullets = new ArrayList<Bullet>();
        final List<Particle> particles = new ArrayList<Particle>();
        final List<Ring> rings = new ArrayList<Ring>();
        final List<FloatText> texts = new ArrayList<FloatText>();
        final List<Pickup> pickups = new ArrayList<Pickup>();
        double shake, slowMotion;

        GamePanel() {
            setPreferredSize(new Dimension(W, H));
            setBackground(BG);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
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
            highScore = readHighScore();
        }

        void start() {
            sound.load();
            lastTick = System.nanoTime();
            timer.start();
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
                    aimX = e.getX();
                    aimY = e.getY();
                    hoveredCard = cardAt(e.getX(), e.getY());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    mouseMoved(e);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    handleClick(e.getX(), e.getY());
                    firing = true;
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    firing = false;
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
                    break;
                case KeyEvent.VK_RIGHT:
                case KeyEvent.VK_D:
                    right = pressed;
                    break;
                default:
                    if (pressed) {
                        handleKeyPress(key);
                    }
                    break;
            }
        }

        private void handleKeyPress(int key) {
            if (key == KeyEvent.VK_M) {
                sound.muted = !sound.muted;
            } else if (key == KeyEvent.VK_F) {
                autoFire = !autoFire;
            } else if (key == KeyEvent.VK_P || key == KeyEvent.VK_ESCAPE) {
                if (state == State.PLAYING) {
                    state = State.PAUSED;
                } else if (state == State.PAUSED) {
                    state = State.PLAYING;
                }
            } else if (key == KeyEvent.VK_SPACE) {
                if (state == State.PLAYING) {
                    tryDash();
                } else if (state == State.PAUSED) {
                    state = State.PLAYING;
                }
            } else if (key == KeyEvent.VK_Q || key == KeyEvent.VK_E) {
                useBomb();
            } else if (key == KeyEvent.VK_ENTER) {
                if (state == State.MENU || (state == State.GAME_OVER && overDelay <= 0)) {
                    startGame();
                }
            } else if (key >= KeyEvent.VK_1 && key <= KeyEvent.VK_3) {
                chooseCard(key - KeyEvent.VK_1);
            }
        }

        void handleClick(int x, int y) {
            if (state == State.MENU) {
                startGame();
            } else if (state == State.GAME_OVER && overDelay <= 0) {
                startGame();
            } else if (state == State.UPGRADE) {
                chooseCard(cardAt(x, y));
            } else if (state == State.PAUSED) {
                state = State.PLAYING;
            }
        }

        // ============================== SETUP ===============================

        void startGame() {
            px = W / 2.0;
            py = H / 2.0;
            health = START_HEALTH;
            maxHealth = START_HEALTH;
            bombs = START_BOMBS;
            invulnerable = 0;
            dashTimer = 0;
            dashCooldown = 0;
            fireCooldown = 0;
            hurtFlash = 0;
            fireDelay = 0.2;
            bulletDamage = 1;
            moveSpeed = BASE_SPEED;
            dashDelay = 1.5;
            critChance = 0;
            dashDamage = 0;
            homing = 0;
            explosiveRadius = 0;
            bulletCount = 1;
            pierce = 0;
            bounces = 0;
            orbitals = 0;
            Arrays.fill(stacks, 0);
            wave = 0;
            score = 0;
            kills = 0;
            combo = 0;
            bestCombo = 0;
            comboTimer = 0;
            newRecord = false;
            enemies.clear();
            newEnemies.clear();
            bullets.clear();
            particles.clear();
            rings.clear();
            texts.clear();
            pickups.clear();
            shake = 0;
            slowMotion = 0;
            state = State.PLAYING;
            nextWave();
        }

        void nextWave() {
            wave++;
            clearing = false;
            spawnQueue.clear();
            boolean bossWave = wave % 5 == 0;
            if (bossWave) {
                spawnQueue.add(EnemyType.BOSS);
                for (int i = 0; i < wave / 5 * 3; i++) {
                    spawnQueue.add(EnemyType.CHASER);
                }
                banner = "BOSS INCOMING!";
                sound.play(Sound.Effect.BOSS);
            } else {
                int count = Math.min(60, 6 + wave * 3);
                for (int i = 0; i < count; i++) {
                    spawnQueue.add(rollEnemy());
                }
                banner = "WAVE " + wave;
                sound.play(Sound.Effect.WAVE);
            }
            spawnDelay = Math.max(0.16, 0.8 - wave * 0.05);
            spawnTimer = 0.9;
            bannerTimer = 1.6;
        }

        private EnemyType rollEnemy() {
            int roll = rng.nextInt(100);
            if (wave >= 6 && roll < 10) return EnemyType.TANK;
            if (wave >= 4 && roll < 28) return EnemyType.SHOOTER;
            if (wave >= 3 && roll < 46) return EnemyType.SPLITTER;
            if (wave >= 2 && roll < 68) return EnemyType.SPEEDER;
            return EnemyType.CHASER;
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
            // Past wave 12 enemies toughen up fast, so no build can last forever.
            int late = Math.max(0, wave - 12);
            double scale = 1 + 0.1 * (wave - 1) + 0.05 * late * late;
            double hp = type.hp * scale;
            if (type == EnemyType.BOSS) {
                x = W / 2.0;
                y = 130;
                if (Math.hypot(x - px, y - py) < 200) {
                    y = H - 130;
                }
                hp = (type.hp + 170 * (wave / 5 - 1)) * (1 + 0.03 * late * late);
            }
            Enemy enemy = new Enemy(type, x, y, hp);
            enemy.spawnTimer = type == EnemyType.BOSS ? 1.2 : 0.7;
            enemy.attackTimer = 1 + rng.nextDouble() * 1.5;
            enemy.wobble = rng.nextDouble() * 10;
            newEnemies.add(enemy);
        }

        // ============================== UPDATE ==============================

        void update(double dt) {
            clock += dt;
            shake = Math.max(0, shake - 40 * dt);
            hurtFlash = Math.max(0, hurtFlash - dt);
            bannerTimer = Math.max(0, bannerTimer - dt);
            cardDelay = Math.max(0, cardDelay - dt);
            overDelay = Math.max(0, overDelay - dt);

            if (state == State.PAUSED) {
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

        private void step(double dt) {
            updatePlayer(dt);
            updateOrbitals(dt);
            updateBullets(dt);
            updateEnemies(dt);
            updatePickups(dt);
            updateWave(dt);
            enemies.addAll(newEnemies);
            newEnemies.clear();
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
            fireCooldown -= dt;
            facing = Math.atan2(aimY - py, aimX - px);

            double vx, vy;
            if (dashTimer > 0) {
                dashTimer -= dt;
                vx = dashDirX * DASH_SPEED;
                vy = dashDirY * DASH_SPEED;
                addParticle(px, py, 0, 0, 0.25, 9, new Color(90, 240, 255));
            } else {
                double dx = (right ? 1 : 0) - (left ? 1 : 0);
                double dy = (down ? 1 : 0) - (up ? 1 : 0);
                double length = Math.hypot(dx, dy);
                vx = length == 0 ? 0 : dx / length * moveSpeed;
                vy = length == 0 ? 0 : dy / length * moveSpeed;
            }
            px = clamp(px + vx * dt, 22, W - 22);
            py = clamp(py + vy * dt, 22, H - 22);

            if ((firing || autoFire) && fireCooldown <= 0) {
                shoot();
            }
        }

        private void shoot() {
            fireCooldown = fireDelay;
            double spread = 0.12;
            for (int i = 0; i < bulletCount; i++) {
                double angle = facing + (i - (bulletCount - 1) / 2.0) * spread;
                Bullet b = new Bullet();
                b.x = px + Math.cos(angle) * 18;
                b.y = py + Math.sin(angle) * 18;
                b.vx = Math.cos(angle) * 680;
                b.vy = Math.sin(angle) * 680;
                b.r = 4.5;
                b.damage = bulletDamage;
                b.life = 1.1;
                b.pierce = pierce;
                b.bounces = bounces;
                b.color = new Color(170, 250, 255);
                bullets.add(b);
            }
            addParticle(px + Math.cos(facing) * 20, py + Math.sin(facing) * 20,
                    Math.cos(facing) * 60, Math.sin(facing) * 60, 0.08, 8, new Color(220, 255, 255));
            sound.play(Sound.Effect.SHOOT);
        }

        void tryDash() {
            if (dashCooldown > 0 || dashTimer > 0) {
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
            sound.play(Sound.Effect.DASH);
        }

        void useBomb() {
            if (state != State.PLAYING || bombs <= 0) {
                return;
            }
            bombs--;
            addRing(px, py, 900, 0.6, new Color(255, 240, 180));
            burst(px, py, 80, new Color(255, 220, 120), 500);
            shake = Math.max(shake, 26);
            slowMotion = Math.max(slowMotion, 0.25);
            sound.play(Sound.Effect.BIG_BOOM);
            for (Bullet b : bullets) {
                if (b.enemy) {
                    b.dead = true;
                }
            }
            for (Enemy e : enemies) {
                if (!e.dead && e.spawnTimer <= 0) {
                    damage(e, 40, false);
                }
            }
        }

        private void updateOrbitals(double dt) {
            orbitAngle += dt * 3.2;
            for (int i = 0; i < orbitals; i++) {
                double angle = orbitAngle + i * Math.PI * 2 / orbitals;
                double ox = px + Math.cos(angle) * 72, oy = py + Math.sin(angle) * 72;
                for (Enemy e : enemies) {
                    if (!e.dead && e.spawnTimer <= 0 && e.contactCooldown <= 0
                            && Math.hypot(e.x - ox, e.y - oy) < e.type.radius + 10) {
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

        private void updateBullets(double dt) {
            for (Bullet b : bullets) {
                if (b.dead) {
                    continue;
                }
                b.life -= dt;
                if (b.life <= 0) {
                    b.dead = true;
                    continue;
                }
                if (!b.enemy && homing > 0) {
                    steerBullet(b, dt);
                }
                b.x += b.vx * dt;
                b.y += b.vy * dt;
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
                }
            }
        }

        private void steerBullet(Bullet b, double dt) {
            Enemy nearest = null;
            double best = 320;
            for (Enemy e : enemies) {
                if (!e.dead && e.spawnTimer <= 0) {
                    double d = Math.hypot(e.x - b.x, e.y - b.y);
                    if (d < best && (b.alreadyHit == null || !b.alreadyHit.contains(e))) {
                        best = d;
                        nearest = e;
                    }
                }
            }
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
                if (Math.hypot(e.x - b.x, e.y - b.y) < e.type.radius + b.r) {
                    boolean crit = rng.nextDouble() < critChance;
                    damage(e, b.damage * (crit ? 3 : 1), crit);
                    burst(b.x, b.y, 3, e.type.color, 130);
                    if (explosiveRadius > 0) {
                        explode(b.x, b.y, explosiveRadius, b.damage * 0.6);
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

        private void explode(double x, double y, double radius, double amount) {
            addRing(x, y, radius, 0.22, new Color(255, 170, 80));
            burst(x, y, 7, new Color(255, 170, 80), 170);
            for (Enemy e : enemies) {
                if (!e.dead && e.spawnTimer <= 0 && Math.hypot(e.x - x, e.y - y) < radius + e.type.radius) {
                    damage(e, amount, false);
                }
            }
        }

        void damage(Enemy e, double amount, boolean crit) {
            if (e.dead) {
                return;
            }
            e.hp -= amount;
            e.flash = 0.08;
            sound.play(Sound.Effect.HIT);
            if (crit) {
                addText(e.x, e.y - e.type.radius, "CRIT!", new Color(255, 240, 150), 18);
            }
            if (e.hp <= 0) {
                kill(e);
            }
        }

        private void kill(Enemy e) {
            e.dead = true;
            kills++;
            combo++;
            bestCombo = Math.max(bestCombo, combo);
            comboTimer = 2.5;
            int points = e.type.score * multiplier();
            score += points;
            addText(e.x, e.y, "+" + points, e.type.color, multiplier() > 2 ? 20 : 15);

            boolean big = e.type == EnemyType.BOSS;
            burst(e.x, e.y, big ? 200 : 14 + e.type.radius, e.type.color, big ? 620 : 300);
            addRing(e.x, e.y, big ? 520 : e.type.radius * 3.5, big ? 0.9 : 0.28, e.type.color);
            shake = Math.max(shake, big ? 36 : 3 + e.type.radius * 0.28);
            sound.play(big ? Sound.Effect.BIG_BOOM : Sound.Effect.BOOM);

            if (e.type == EnemyType.SPLITTER) {
                for (int i = 0; i < 3; i++) {
                    double angle = i * Math.PI * 2 / 3 + rng.nextDouble();
                    Enemy mini = new Enemy(EnemyType.MINI, e.x + Math.cos(angle) * 16, e.y + Math.sin(angle) * 16,
                            EnemyType.MINI.hp);
                    mini.vx = Math.cos(angle) * 240;
                    mini.vy = Math.sin(angle) * 240;
                    mini.wobble = rng.nextDouble() * 10;
                    newEnemies.add(mini);
                }
            }
            if (big) {
                slowMotion = 1.0;
                addPickup(PickupType.HEART, e.x - 30, e.y);
                addPickup(PickupType.BOMB, e.x + 30, e.y);
                for (Bullet b : bullets) {
                    if (b.enemy) {
                        b.dead = true;
                    }
                }
            } else {
                double roll = rng.nextDouble();
                if (roll < 0.035) {
                    addPickup(PickupType.HEART, e.x, e.y);
                } else if (roll < 0.05) {
                    addPickup(PickupType.BOMB, e.x, e.y);
                }
            }
        }

        int multiplier() {
            return Math.min(8, 1 + combo / 8);
        }

        private void updateEnemies(double dt) {
            for (Enemy e : enemies) {
                if (e.dead) {
                    continue;
                }
                e.flash = Math.max(0, e.flash - dt);
                e.contactCooldown = Math.max(0, e.contactCooldown - dt);
                if (e.spawnTimer > 0) {
                    e.spawnTimer -= dt;
                    continue;
                }
                e.angle += dt * 2.5;
                // Enemies also speed up in late waves (up to 70% faster).
                double moveDt = dt * (1 + Math.min(0.7, Math.max(0, wave - 12) * 0.05));
                switch (e.type) {
                    case SPEEDER:
                        moveSpeeder(e, moveDt);
                        break;
                    case SHOOTER:
                        moveShooter(e, moveDt);
                        break;
                    case BOSS:
                        moveBoss(e, moveDt);
                        break;
                    default:
                        moveChaser(e, moveDt);
                        break;
                }
                separate(e, dt);
                e.x = clamp(e.x, e.type.radius, W - e.type.radius);
                e.y = clamp(e.y, e.type.radius, H - e.type.radius);

                double distance = Math.hypot(e.x - px, e.y - py);
                if (distance < e.type.radius + PLAYER_RADIUS) {
                    if (dashTimer > 0 && dashDamage > 0 && e.contactCooldown <= 0) {
                        e.contactCooldown = 0.3;
                        damage(e, dashDamage, false);
                    } else if (canBeHurt()) {
                        hurtPlayer();
                    }
                }
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
            if (e.x <= e.type.radius || e.x >= W - e.type.radius) {
                e.vx = -e.vx * 0.6;
            }
            if (e.y <= e.type.radius || e.y >= H - e.type.radius) {
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

        private void moveBoss(Enemy e, double dt) {
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
                    int count = 16 + wave / 5 * 2;
                    double offset = rng.nextDouble() * Math.PI;
                    for (int i = 0; i < count; i++) {
                        fireEnemyBullet(e.x, e.y, offset + i * Math.PI * 2 / count, 200, e.type.color);
                    }
                    addRing(e.x, e.y, 130, 0.3, e.type.color);
                    sound.play(Sound.Effect.BOOM);
                    break;
                case 1:
                    e.chargeWarn = 0.75;
                    e.aimX = px;
                    e.aimY = py;
                    break;
                case 2:
                    for (int i = 0; i < 4; i++) {
                        double angle = i * Math.PI / 2 + Math.PI / 4;
                        Enemy minion = new Enemy(EnemyType.CHASER, e.x + Math.cos(angle) * 90,
                                e.y + Math.sin(angle) * 90, EnemyType.CHASER.hp);
                        minion.spawnTimer = 0.5;
                        minion.wobble = rng.nextDouble() * 10;
                        newEnemies.add(minion);
                    }
                    break;
                default:
                    e.spiralTime = 2.2;
                    e.attackTimer = 0;
                    e.aimX = rng.nextDouble() * Math.PI * 2;
                    break;
            }
        }

        private void separate(Enemy e, double dt) {
            for (Enemy other : enemies) {
                if (other == e || other.dead || other.spawnTimer > 0) {
                    continue;
                }
                double dx = e.x - other.x, dy = e.y - other.y;
                double minimum = e.type.radius + other.type.radius;
                if (Math.abs(dx) < minimum && Math.abs(dy) < minimum) {
                    double d = Math.hypot(dx, dy);
                    if (d < minimum && d > 0.01 && e.type != EnemyType.BOSS) {
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
            b.vx = Math.cos(angle) * speed;
            b.vy = Math.sin(angle) * speed;
            b.r = 6.5;
            b.life = 6;
            b.enemy = true;
            b.color = color;
            bullets.add(b);
        }

        boolean canBeHurt() {
            return invulnerable <= 0 && dashTimer <= 0 && state == State.PLAYING;
        }

        private void hurtPlayer() {
            health--;
            invulnerable = 1.3;
            hurtFlash = 0.35;
            shake = Math.max(shake, 20);
            slowMotion = Math.max(slowMotion, 0.2);
            combo = 0;
            sound.play(Sound.Effect.HURT);
            burst(px, py, 26, new Color(255, 90, 110), 320);
            if (health <= 0) {
                gameOver();
            }
        }

        private void gameOver() {
            burst(px, py, 180, PLAYER_COLOR, 560);
            addRing(px, py, 420, 0.8, PLAYER_COLOR);
            shake = 36;
            sound.play(Sound.Effect.BIG_BOOM);
            if (score > highScore) {
                highScore = score;
                newRecord = true;
                saveHighScore();
            }
            releaseInput();
            overDelay = 0.9;
            state = State.GAME_OVER;
        }

        private void updatePickups(double dt) {
            for (Iterator<Pickup> it = pickups.iterator(); it.hasNext(); ) {
                Pickup p = it.next();
                p.life -= dt;
                double d = Math.max(1, Math.hypot(px - p.x, py - p.y));
                if (d < 130 || clearing) {
                    double pull = clearing ? 800 : 340;
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
            if (p.type == PickupType.HEART) {
                if (health < maxHealth) {
                    health++;
                    addText(px, py - 22, "+1 HEALTH", new Color(255, 120, 140), 18);
                } else {
                    score += 100;
                    addText(px, py - 22, "+100", new Color(255, 120, 140), 18);
                }
            } else {
                bombs++;
                addText(px, py - 22, "+1 BOMB", new Color(255, 200, 120), 18);
            }
            sound.play(Sound.Effect.PICKUP);
        }

        private void addPickup(PickupType type, double x, double y) {
            Pickup p = new Pickup();
            p.type = type;
            p.x = clamp(x, 30, W - 30);
            p.y = clamp(y, 30, H - 30);
            p.life = 10;
            pickups.add(p);
        }

        private void updateWave(double dt) {
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
            boolean anyAlive = !newEnemies.isEmpty();
            for (Enemy e : enemies) {
                anyAlive |= !e.dead;
            }
            if (!anyAlive) {
                clearing = true;
                clearTimer = 1.1;
                banner = "WAVE CLEARED!";
                bannerTimer = 1.1;
                score += wave * 50;
                sound.play(Sound.Effect.UPGRADE);
                for (Bullet b : bullets) {
                    if (b.enemy) {
                        b.dead = true;
                    }
                }
            }
        }

        private void openUpgrades() {
            choices.clear();
            for (Upgrade upgrade : Upgrade.values()) {
                if (stacks[upgrade.ordinal()] < upgrade.max) {
                    choices.add(upgrade);
                }
            }
            if (choices.isEmpty()) {
                nextWave();
                return;
            }
            Collections.shuffle(choices, rng);
            while (choices.size() > 3) {
                choices.remove(choices.size() - 1);
            }
            cardDelay = 0.35;
            hoveredCard = -1;
            firing = false;
            state = State.UPGRADE;
        }

        void chooseCard(int index) {
            if (state != State.UPGRADE || cardDelay > 0 || index < 0 || index >= choices.size()) {
                return;
            }
            applyUpgrade(choices.get(index));
            state = State.PLAYING;
            nextWave();
        }

        void applyUpgrade(Upgrade upgrade) {
            stacks[upgrade.ordinal()]++;
            sound.play(Sound.Effect.PICKUP);
            switch (upgrade) {
                case RAPID:
                    fireDelay *= 0.8;
                    break;
                case POWER:
                    bulletDamage += 0.5;
                    break;
                case MULTI:
                    bulletCount++;
                    break;
                case PIERCE:
                    pierce++;
                    break;
                case BOUNCE:
                    bounces++;
                    break;
                case EXPLOSIVE:
                    explosiveRadius = 35 + 20 * stacks[upgrade.ordinal()];
                    break;
                case ORBITALS:
                    orbitals++;
                    break;
                case HOMING:
                    homing += 3.5;
                    break;
                case SWIFT:
                    moveSpeed *= 1.12;
                    break;
                case VITALITY:
                    maxHealth++;
                    health = maxHealth;
                    break;
                case DASH:
                    dashDelay *= 0.78;
                    dashDamage += 6;
                    break;
                case CRIT:
                    critChance += 0.15;
                    break;
                case BOMBS:
                    bombs++;
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
            }
            for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
                Ring r = it.next();
                r.life -= dt;
                if (r.life <= 0) {
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
            render((Graphics2D) graphics);
        }

        void render(Graphics2D g) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.drawImage(background, 0, 0, null);
            if (state == State.MENU) {
                drawMenu(g);
                return;
            }

            double shakeX = (fx.nextDouble() - 0.5) * shake, shakeY = (fx.nextDouble() - 0.5) * shake;
            g.translate(shakeX, shakeY);
            drawRings(g);
            drawPickups(g);
            for (Enemy e : enemies) {
                drawEnemy(g, e);
            }
            drawBullets(g);
            if (state != State.GAME_OVER) {
                drawPlayer(g);
            }
            drawParticles(g);
            drawTexts(g);
            g.translate(-shakeX, -shakeY);

            if (hurtFlash > 0) {
                alpha(g, (float) (hurtFlash / 0.35 * 0.35));
                g.setColor(new Color(255, 30, 60));
                g.fillRect(0, 0, W, H);
                alpha(g, 1);
            }
            drawHud(g);
            if (bannerTimer > 0 && state == State.PLAYING) {
                drawBanner(g);
            }
            if (state == State.UPGRADE) {
                drawUpgradeScreen(g);
            } else if (state == State.PAUSED) {
                dim(g, 0.65f);
                g.setColor(Color.WHITE);
                centered(g, "PAUSED", 330, 56, true);
                centered(g, "Press P or click to keep playing", 380, 20, false);
            } else if (state == State.GAME_OVER) {
                drawGameOver(g);
            }
        }

        private BufferedImage makeBackground() {
            BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(BG);
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(24, 28, 58));
            for (int x = 0; x <= W; x += 50) {
                g.drawLine(x, 0, x, H);
            }
            for (int y = 0; y <= H; y += 50) {
                g.drawLine(0, y, W, y);
            }
            g.setColor(new Color(70, 90, 190));
            g.setStroke(new BasicStroke(4));
            g.drawRect(2, 2, W - 5, H - 5);
            g.dispose();
            return image;
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

        private void centered(Graphics2D g, String text, int y, int size, boolean bold) {
            g.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
            g.drawString(text, (W - g.getFontMetrics().stringWidth(text)) / 2, y);
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

        private void drawRings(Graphics2D g) {
            for (Ring r : rings) {
                double t = 1 - r.life / r.maxLife;
                double radius = r.maxRadius * Math.sqrt(t);
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
                double bob = Math.sin(clock * 5) * 3;
                if (p.type == PickupType.HEART) {
                    drawHeart(g, (int) p.x - 11, (int) (p.y + bob) - 10, 22, new Color(255, 80, 110));
                } else {
                    drawBombIcon(g, (int) p.x, (int) (p.y + bob));
                }
            }
        }

        private void drawHeart(Graphics2D g, int x, int y, int size, Color color) {
            g.setColor(color);
            int half = size / 2 + 1;
            g.fillOval(x, y, half, half);
            g.fillOval(x + size - half, y, half, half);
            g.fillPolygon(new int[] {x, x + size, x + size / 2},
                    new int[] {y + size * 3 / 8, y + size * 3 / 8, y + size}, 3);
        }

        private void drawBombIcon(Graphics2D g, int x, int y) {
            glowCircle(g, x, y + 2, 9, new Color(255, 170, 80));
            g.setColor(new Color(45, 45, 65));
            g.fillOval(x - 8, y - 6, 16, 16);
            g.setColor(new Color(255, 240, 180));
            g.fillOval(x + 4, y - 10, 5, 5);
        }

        private void drawEnemy(Graphics2D g, Enemy e) {
            if (e.dead) {
                return;
            }
            int r = e.type.radius;
            Color color = e.flash > 0 ? Color.WHITE : e.type.color;
            if (e.spawnTimer > 0) {
                // A warning ring gives the player time to move out of the way.
                double total = e.type == EnemyType.BOSS ? 1.2 : 0.7;
                double t = 1 - e.spawnTimer / total;
                alpha(g, 0.3f + 0.6f * (float) t);
                g.setColor(e.type.color);
                g.setStroke(new BasicStroke(2));
                double ring = r * (2.6 - 1.6 * t);
                g.drawOval((int) (e.x - ring), (int) (e.y - ring), (int) (ring * 2), (int) (ring * 2));
                g.setStroke(new BasicStroke(1));
                alpha(g, 1);
                return;
            }
            Polygon shape;
            switch (e.type) {
                case CHASER:
                    shape = regular(e.x, e.y, r, 4, e.angle);
                    break;
                case SPEEDER:
                    shape = regular(e.x, e.y, r + 3, 3, e.angle);
                    break;
                case TANK:
                    shape = regular(e.x, e.y, r, 6, e.angle * 0.3);
                    break;
                case SPLITTER:
                    shape = regular(e.x, e.y, r, 5, e.angle * 0.6);
                    break;
                case MINI:
                    shape = regular(e.x, e.y, r, 3, e.angle * 2);
                    break;
                case SHOOTER:
                    shape = regular(e.x, e.y, r, 12, 0);
                    break;
                default:
                    shape = regular(e.x, e.y, r, 8, e.angle * 0.3);
                    break;
            }
            alpha(g, 0.28f);
            g.setColor(e.type.color);
            g.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(shape);
            g.setStroke(new BasicStroke(1));
            alpha(g, 1);
            g.setColor(color);
            g.fillPolygon(shape);

            if (e.type == EnemyType.SHOOTER) {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(5));
                g.drawLine((int) e.x, (int) e.y, (int) (e.x + Math.cos(e.angle) * 23), (int) (e.y + Math.sin(e.angle) * 23));
                g.setStroke(new BasicStroke(1));
            }
            if (e.type == EnemyType.BOSS) {
                drawBossExtras(g, e);
            }
            drawFace(g, e, r);
            if (e.type == EnemyType.TANK && e.hp < e.maxHp) {
                g.setColor(new Color(30, 30, 50));
                g.fillRect((int) e.x - 24, (int) e.y - r - 12, 48, 5);
                g.setColor(e.type.color);
                g.fillRect((int) e.x - 24, (int) e.y - r - 12, (int) (48 * e.hp / e.maxHp), 5);
            }
        }

        private void drawBossExtras(Graphics2D g, Enemy e) {
            g.setColor(new Color(110, 20, 90));
            g.fillPolygon(regular(e.x, e.y, 38, 8, -e.angle * 0.5));
            if (e.chargeWarn > 0) {
                // Shows exactly where the boss is about to charge.
                alpha(g, 0.35f + 0.4f * (float) Math.abs(Math.sin(clock * 18)));
                g.setColor(new Color(255, 70, 110));
                g.setStroke(new BasicStroke(e.type.radius * 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double d = Math.max(1, Math.hypot(e.aimX - e.x, e.aimY - e.y));
                g.drawLine((int) e.x, (int) e.y, (int) (e.x + (e.aimX - e.x) / d * 396), (int) (e.y + (e.aimY - e.y) / d * 396));
                g.setStroke(new BasicStroke(1));
                alpha(g, 1);
            }
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

        private void drawBullets(Graphics2D g) {
            for (Bullet b : bullets) {
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

        private void drawPlayer(Graphics2D g) {
            for (int i = 0; i < orbitals; i++) {
                double angle = orbitAngle + i * Math.PI * 2 / orbitals;
                g.setColor(new Color(120, 220, 255));
                glowCircle(g, px + Math.cos(angle) * 72, py + Math.sin(angle) * 72, 8, new Color(120, 220, 255));
            }
            if (invulnerable > 0 && dashTimer <= 0 && (int) (clock * 14) % 2 == 0) {
                return;
            }
            Polygon ship = new Polygon();
            double[][] points = {{20, 0}, {-13, 13}, {-6, 0}, {-13, -13}};
            for (double[] point : points) {
                double c = Math.cos(facing), s = Math.sin(facing);
                ship.addPoint((int) Math.round(px + point[0] * c - point[1] * s),
                        (int) Math.round(py + point[0] * s + point[1] * c));
            }
            alpha(g, 0.3f);
            g.setColor(PLAYER_COLOR);
            g.setStroke(new BasicStroke(11, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(ship);
            g.setStroke(new BasicStroke(1));
            alpha(g, 1);
            g.setColor(dashTimer > 0 ? Color.WHITE : PLAYER_COLOR);
            g.fillPolygon(ship);
            g.setColor(Color.WHITE);
            g.fillOval((int) px - 4, (int) py - 4, 8, 8);
        }

        private void drawParticles(Graphics2D g) {
            Composite original = g.getComposite();
            for (Particle p : particles) {
                alpha(g, (float) (p.life / p.maxLife));
                g.setColor(p.color);
                int size = (int) Math.ceil(p.size);
                g.fillRect((int) p.x - size / 2, (int) p.y - size / 2, size, size);
            }
            g.setComposite(original);
        }

        private void drawTexts(Graphics2D g) {
            for (FloatText t : texts) {
                alpha(g, (float) Math.min(1, t.life * 2));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, t.size));
                g.setColor(t.color);
                g.drawString(t.text, (int) t.x - g.getFontMetrics().stringWidth(t.text) / 2, (int) t.y);
            }
            alpha(g, 1);
        }

        private void drawHud(Graphics2D g) {
            for (int i = 0; i < maxHealth; i++) {
                drawHeart(g, 16 + i * 30, 14, 24, i < health ? new Color(255, 75, 105) : new Color(55, 58, 85));
            }

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            g.setColor(Color.WHITE);
            String scoreText = String.valueOf(score);
            g.drawString(scoreText, W - 18 - g.getFontMetrics().stringWidth(scoreText), 36);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.setColor(new Color(170, 180, 220));
            String waveText = "WAVE " + wave + "   BEST " + highScore;
            g.drawString(waveText, W - 18 - g.getFontMetrics().stringWidth(waveText), 56);

            if (multiplier() > 1) {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
                g.setColor(new Color(255, 225, 90));
                String text = "x" + multiplier();
                g.drawString(text, W - 18 - g.getFontMetrics().stringWidth(text), 92);
                g.fillRect(W - 78, 98, (int) (60 * Math.max(0, comboTimer) / 2.5), 4);
            }

            // Dash charge bar and bombs along the bottom left.
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.setColor(new Color(170, 180, 220));
            g.drawString("DASH [SPACE]", 18, H - 36);
            g.setColor(new Color(40, 44, 80));
            g.fillRoundRect(18, H - 28, 120, 10, 6, 6);
            double charge = 1 - dashCooldown / dashDelay;
            g.setColor(charge >= 1 ? PLAYER_COLOR : new Color(80, 110, 170));
            g.fillRoundRect(18, H - 28, (int) (120 * charge), 10, 6, 6);
            g.setColor(new Color(170, 180, 220));
            g.drawString("BOMB [Q]", 160, H - 36);
            for (int i = 0; i < bombs && i < 8; i++) {
                drawBombIcon(g, 170 + i * 24, H - 18);
            }
            if (autoFire) {
                g.setColor(new Color(150, 240, 150));
                g.drawString("AUTO-FIRE ON", W - 118, H - 18);
            }

            for (Enemy e : enemies) {
                if (e.type == EnemyType.BOSS && !e.dead && e.spawnTimer <= 0) {
                    g.setColor(new Color(50, 25, 55));
                    g.fillRoundRect(230, 46, 540, 18, 8, 8);
                    g.setColor(e.hp < e.maxHp / 2 ? new Color(255, 70, 90) : e.type.color);
                    g.fillRoundRect(230, 46, (int) (540 * Math.max(0, e.hp) / e.maxHp), 18, 8, 8);
                    g.setColor(Color.WHITE);
                    centered(g, "BOSS", 60, 13, true);
                    break;
                }
            }
        }

        private void drawBanner(Graphics2D g) {
            alpha(g, (float) Math.min(1, bannerTimer * 2));
            g.setColor(banner.startsWith("BOSS") ? new Color(255, 90, 170) : Color.WHITE);
            centered(g, banner, 190, 58, true);
            alpha(g, 1);
        }

        private int cardX(int index) {
            int width = 280, gap = 30, count = Math.max(1, choices.size());
            int total = count * width + (count - 1) * gap;
            return (W - total) / 2 + index * (width + gap);
        }

        int cardAt(int x, int y) {
            if (state != State.UPGRADE || y < 210 || y > 510) {
                return -1;
            }
            for (int i = 0; i < choices.size(); i++) {
                if (x >= cardX(i) && x <= cardX(i) + 280) {
                    return i;
                }
            }
            return -1;
        }

        private void drawUpgradeScreen(Graphics2D g) {
            dim(g, 0.72f);
            g.setColor(new Color(255, 225, 120));
            centered(g, "CHOOSE AN UPGRADE", 130, 44, true);
            g.setColor(new Color(170, 180, 220));
            centered(g, "Click a card or press 1, 2 or 3", 170, 18, false);
            for (int i = 0; i < choices.size(); i++) {
                drawCard(g, choices.get(i), i);
            }
        }

        private void drawCard(Graphics2D g, Upgrade upgrade, int index) {
            int x = cardX(index), y = index == hoveredCard ? 200 : 210, width = 280, height = 300;
            g.setColor(new Color(24, 27, 54));
            g.fillRoundRect(x, y, width, height, 22, 22);
            g.setColor(upgrade.color);
            g.setStroke(new BasicStroke(index == hoveredCard ? 5 : 2));
            g.drawRoundRect(x, y, width, height, 22, 22);
            g.setStroke(new BasicStroke(1));
            g.fillPolygon(regular(x + width / 2.0, y + 72, 36, 6, clock + index));
            g.setColor(new Color(15, 15, 30));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            String number = String.valueOf(index + 1);
            g.drawString(number, x + (width - g.getFontMetrics().stringWidth(number)) / 2, y + 82);

            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23));
            g.drawString(upgrade.title, x + (width - g.getFontMetrics().stringWidth(upgrade.title)) / 2, y + 150);
            g.setColor(new Color(190, 198, 235));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
            int line = 0;
            for (String text : wrap(g, upgrade.description, width - 40)) {
                g.drawString(text, x + (width - g.getFontMetrics().stringWidth(text)) / 2, y + 186 + line * 24);
                line++;
            }
            g.setColor(upgrade.color);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            String level = "Level " + stacks[upgrade.ordinal()] + " / " + upgrade.max;
            g.drawString(level, x + (width - g.getFontMetrics().stringWidth(level)) / 2, y + height - 20);
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
            alpha(g, 0.35f);
            g.setColor(new Color(255, 70, 200));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 92));
            String title = "NEON SWARM";
            int titleX = (W - g.getFontMetrics().stringWidth(title)) / 2;
            g.drawString(title, titleX + 4 + (int) (pulse * 4), 164);
            alpha(g, 1);
            g.setColor(new Color(90, 240, 255));
            g.drawString(title, titleX, 160);

            EnemyType[] parade = {EnemyType.CHASER, EnemyType.SPEEDER, EnemyType.SPLITTER, EnemyType.SHOOTER, EnemyType.TANK};
            for (int i = 0; i < parade.length; i++) {
                Enemy sample = new Enemy(parade[i], 300 + i * 100, 225 + Math.sin(clock * 3 + i) * 8, 1);
                sample.angle = clock * 2;
                drawEnemy(g, sample);
            }

            g.setColor(Color.WHITE);
            centered(g, "Survive the waves. Choose upgrades. Beat a boss every 5 waves.", 300, 22, false);
            g.setColor(new Color(190, 198, 235));
            String[] controls = {
                "Move: WASD or arrow keys",
                "Aim: mouse     Shoot: hold left click  (F = auto-fire)",
                "Dash through danger: SPACE     Bomb: Q",
                "Pause: P     Mute: M",
            };
            for (int i = 0; i < controls.length; i++) {
                centered(g, controls[i], 360 + i * 32, 19, false);
            }
            if (highScore > 0) {
                g.setColor(new Color(255, 225, 120));
                centered(g, "Best score: " + highScore, 515, 22, true);
            }
            if ((int) (clock * 2) % 2 == 0) {
                g.setColor(new Color(150, 255, 170));
                centered(g, "Click or press ENTER to play", 585, 30, true);
            }
        }

        private void drawGameOver(Graphics2D g) {
            dim(g, 0.68f);
            g.setColor(new Color(255, 90, 120));
            centered(g, "GAME OVER", 210, 74, true);
            g.setColor(Color.WHITE);
            centered(g, "Score: " + score, 285, 38, true);
            if (newRecord) {
                g.setColor(new Color(255, 225, 90));
                centered(g, "NEW RECORD!", 325, 26, true);
            } else {
                g.setColor(new Color(170, 180, 220));
                centered(g, "Best: " + highScore, 325, 22, false);
            }
            g.setColor(new Color(190, 198, 235));
            centered(g, "Reached wave " + wave + "     Kills: " + kills + "     Best combo: " + bestCombo, 385, 22, false);
            if (overDelay <= 0 && (int) (clock * 2) % 2 == 0) {
                g.setColor(new Color(150, 255, 170));
                centered(g, "Click or press ENTER to try again", 470, 28, true);
            }
        }

        // ============================== HELPERS =============================

        static double clamp(double value, double low, double high) {
            return Math.max(low, Math.min(high, value));
        }

        private int readHighScore() {
            try {
                if (scoreFile != null && scoreFile.exists()) {
                    String text = new String(Files.readAllBytes(scoreFile.toPath()), StandardCharsets.UTF_8);
                    return Integer.parseInt(text.trim());
                }
            } catch (Exception e) {
                return 0;
            }
            return 0;
        }

        private void saveHighScore() {
            try {
                if (scoreFile != null) {
                    Files.write(scoreFile.toPath(), String.valueOf(highScore).getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                System.err.println("Could not save the high score: " + e.getMessage());
            }
        }
    }
}
