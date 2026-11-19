import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
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
 * Pick a ship, survive waves of enemies, choose upgrades, beat ten different
 * bosses, and spend the coins you earn on permanent upgrades between runs.
 * A new daily run, achievements and lifetime records give you reasons to come back.
 *
 * English lives in this file. Other languages are in lang/<code>.properties, so the
 * game text can be corrected without touching the code.
 *
 * Compile:  javac NeonOverdrive.java
 * Run:      java NeonOverdrive
 *
 * Default controls: WASD / arrows move, mouse aims, hold left click to shoot (F = auto-fire),
 *           SPACE dash, E or right click special power, Q bomb,
 *           P pause, M sound, N music, F11 fullscreen.
 *           Every key can be changed in Settings.
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

    enum State { MENU, HANGAR, RECORDS, SKINS, WORKSHOP, SETTINGS, PLAYING, UPGRADE, PAUSED, GAME_OVER }

    /** The languages of the game. Each one except English has a file in the lang folder. */
    enum Language {
        ENGLISH("en", "English", "English"),
        PORTUGUESE("pt", "Portugu\u00eas", "Portuguese"),
        SPANISH("es", "Espa\u00f1ol", "Spanish"),
        FRENCH("fr", "Fran\u00e7ais", "French"),
        ITALIAN("it", "Italiano", "Italian"),
        GERMAN("de", "Deutsch", "German"),
        RUSSIAN("ru", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439", "Russian"),
        CHINESE("zh", "\u7b80\u4f53\u4e2d\u6587", "Chinese"),
        JAPANESE("ja", "\u65e5\u672c\u8a9e", "Japanese");

        final String code, nativeName, englishName;

        Language(String code, String nativeName, String englishName) {
            this.code = code;
            this.nativeName = nativeName;
            this.englishName = englishName;
        }
    }

    /** Everything a key can do. Each action has two key slots; 0 means the slot is empty. */
    enum Action {
        MOVE_UP("Move up", KeyEvent.VK_W, KeyEvent.VK_UP),
        MOVE_DOWN("Move down", KeyEvent.VK_S, KeyEvent.VK_DOWN),
        MOVE_LEFT("Move left", KeyEvent.VK_A, KeyEvent.VK_LEFT),
        MOVE_RIGHT("Move right", KeyEvent.VK_D, KeyEvent.VK_RIGHT),
        FIRE("Shoot (hold)", 0, 0),
        DASH("Dash", KeyEvent.VK_SPACE, 0),
        SPECIAL("Special power", KeyEvent.VK_E, 0),
        BOMB("Bomb", KeyEvent.VK_Q, 0),
        AUTO_FIRE("Auto-fire on/off", KeyEvent.VK_F, 0),
        REROLL("Reroll upgrades", KeyEvent.VK_R, 0),
        PAUSE("Pause", KeyEvent.VK_P, 0),
        SOUND("Sound on/off", KeyEvent.VK_M, 0),
        MUSIC("Music on/off", KeyEvent.VK_N, 0),
        FULLSCREEN("Fullscreen", KeyEvent.VK_F11, 0);

        final String title;
        final int firstKey, secondKey;

        Action(String title, int firstKey, int secondKey) {
            this.title = title;
            this.firstKey = firstKey;
            this.secondKey = secondKey;
        }
    }

    /** What the right and middle mouse buttons do. */
    enum MouseFunction {
        NONE("Nothing"), SPECIAL("Special power"), DASH("Dash"), BOMB("Bomb");

        final String title;

        MouseFunction(String title) {
            this.title = title;
        }
    }

    enum MenuItem {
        PLAY("Play"), CONTINUE("Continue"), DAILY("Daily run"), UPGRADES("Upgrades"),
        SKINS("Skins"), RECORDS("Records"), SETTINGS("Settings"), QUIT("Quit");

        final String title;

        MenuItem(String title) {
            this.title = title;
        }
    }

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
        TRIPLE_THREAT("Triple Threat", "Defeat the first three bosses", 60),
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
        FLEET("Fleet Commander", "Own all four ships", 50),
        MONSTER_HUNTER("Monster Hunter", "Defeat all ten bosses", 150),
        VETERAN("Veteran", "Reach wave 30", 120),
        ANNIHILATOR("Annihilator", "Destroy 10000 enemies in total", 120),
        LEGEND("Legend", "Reach wave 50", 250);

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
        REROLL("Lucky Dice", "+1 upgrade reroll per run", 3, 45),
        DAMAGE("Weapon Tuning", "+8% bullet damage", 5, 50),
        FIRE_RATE("Autoloader", "Shoot 6% faster", 5, 50),
        THRUSTERS("Thrusters", "Move 5% faster", 4, 40),
        DASH_CORE("Dash Capacitor", "Dash recharges 8% faster", 4, 45),
        REACTOR("Reactor", "Special power charges 15% faster", 4, 55),
        TRACTOR("Tractor Beam", "Collect pickups from farther away", 3, 35),
        SCAVENGER("Scavenger", "+20% hearts and bombs dropped", 3, 60),
        SHIELD("Energy Shield", "Start with a shield that blocks 1 hit", 2, 120),
        TARGETING("Targeting Chip", "+4% critical hit chance", 5, 50),
        HEAD_START("Head Start", "Start with 1 random common upgrade", 3, 70),
        PROSPECTOR("Prospector", "+25% diamonds dropped", 4, 80),
        SCANNER("Rarity Scanner", "Rare and legendary cards appear more", 3, 65),
        BOUNTY_BOARD("Bounty Board", "+50% coins from elites and bosses", 2, 70),
        COMBO_KEEPER("Combo Keeper", "Your combo lasts 0.5 seconds longer", 4, 45),
        DRONE_BAY("Drone Bay", "Start every run with a combat drone", 2, 90),
        SECOND_WIND("Second Wind", "Start every run with a Second Life", 1, 250);

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
        WARDEN(   50,  400.0,  45, 2000, new Color(255, 90, 70)),
        RAMMER(   16,    6.0,  70,   30, new Color(255, 60, 140)),
        TURRET(   20,   10.0,   0,   35, new Color(120, 180, 255)),
        LAYER(    17,    6.0,  75,   30, new Color(190, 210, 80)),
        SNIPER(   14,    4.0,  80,   40, new Color(255, 170, 220)),
        MEDIC(    16,    7.0,  80,   40, new Color(110, 255, 200)),
        BULWARK(  22,   14.0,  65,   45, new Color(150, 170, 210)),
        HIVE(     26,   22.0,  40,   60, new Color(230, 170, 60)),
        WRAITH(   15,    6.0, 120,   45, new Color(150, 130, 220)),
        QUEEN(    52,  300.0,  60, 2500, new Color(240, 190, 50)),
        REAPER(   40,  320.0, 110, 3000, new Color(200, 210, 230)),
        COLOSSUS( 66,  420.0,  35, 3500, new Color(255, 140, 60)),
        SINGULARITY(48, 400.0, 30, 4000, new Color(120, 80, 255)),
        SWARMER(   9,    1.0, 190,    6, new Color(255, 120, 200)),
        PULSAR(   18,    9.0,  55,   40, new Color(100, 160, 255)),
        MORTAR(   17,    7.0,  50,   40, new Color(255, 190, 120)),
        SPINNER(  13,    5.0, 290,   35, new Color(200, 255, 255)),
        SPORE(    19,    6.0,  70,   30, new Color(170, 230, 90)),
        TEMPEST(  46,  380.0, 140, 3200, new Color(120, 220, 255)),
        HYDRA(    54,  450.0,  55, 3800, new Color(90, 255, 180)),
        HEAD(     20,   25.0,   0,  150, new Color(60, 220, 150)),
        ECLIPSE(  48,  420.0,  40, 4500, new Color(255, 230, 120)),
        ORB(      13,    1.0,   0,    0, new Color(255, 250, 200));

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
            return this == OVERLORD || this == SERPENT || this == WARDEN || this == QUEEN
                    || this == REAPER || this == COLOSSUS || this == SINGULARITY
                    || this == TEMPEST || this == HYDRA || this == ECLIPSE;
        }

        /** Things bolted in place: bullets and blasts cannot shove them around. */
        boolean isAnchored() {
            return this == TURRET || this == SEGMENT || this == ORB || this == HEAD;
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
        GLASS("Glass Cannon", "+150% damage, but -2 max health", 1, Rarity.LEGENDARY),
        SHIELD_CELL("Shield Cell", "+1 shield that blocks one hit", 4, Rarity.COMMON),
        LONG_BARREL("Long Barrel", "Bullets fly 30% longer and 10% faster", 3, Rarity.COMMON),
        MAGNET("Magnet Field", "Pull in pickups from farther away", 3, Rarity.COMMON),
        BOUNTY("Bounty Hunter", "+30% coins this run", 3, Rarity.COMMON),
        POWER_CELL("Power Cell", "Special power charges 25% faster", 3, Rarity.COMMON),
        DEADEYE("Deadeye", "Critical hits deal +1x damage", 3, Rarity.RARE),
        SHOCKWAVE("Shockwave", "Dashing blasts nearby enemies", 3, Rarity.RARE),
        INCENDIARY("Incendiary", "Hits set enemies on fire", 3, Rarity.RARE),
        NANOBOTS("Nanobots", "Heal 1 health after every wave", 2, Rarity.RARE),
        THORNS("Thorn Armor", "Taking a hit blasts nearby enemies", 3, Rarity.RARE),
        MISSILES("Missile Pod", "+1 homing missile every 1.6 seconds", 3, Rarity.RARE),
        SHIELD_MATRIX("Shield Matrix", "Refill up to 1 more shield each wave", 2, Rarity.RARE),
        BERSERK("Berserker", "+8% damage per combo multiplier", 1, Rarity.LEGENDARY),
        DOUBLE_TAP("Double Tap", "25% chance to fire twice", 1, Rarity.LEGENDARY),
        EXECUTIONER("Executioner", "Non-boss enemies die below 15% health", 1, Rarity.LEGENDARY),
        BULLET_TIME("Bullet Time", "Enemy bullets are 25% slower", 1, Rarity.LEGENDARY),
        OVERCHARGE("Overcharge", "+10% damage and shoot 10% faster", 3, Rarity.COMMON),
        LUCKY("Lucky Charm", "+1 reroll now and more hearts and bombs drop", 3, Rarity.COMMON),
        ADRENALINE("Adrenaline", "Taking a hit gives 2 seconds of triple fire rate", 2, Rarity.COMMON),
        REGEN("Regenerator", "Heal 1 health every 40 seconds (faster per level)", 2, Rarity.RARE),
        SPLINTER("Splinter Rounds", "Hits split into small shards", 2, Rarity.RARE),
        SAW_BLADES("Saw Blades", "+1 big blade circles you and cuts enemies", 3, Rarity.RARE),
        STATIC_FIELD("Static Field", "Zap +1 nearby enemy with lightning every second", 3, Rarity.RARE),
        GIANT_SLAYER("Giant Slayer", "+25% damage to bosses and elites", 3, Rarity.RARE),
        PHOENIX("Phoenix", "Defeating a boss heals you fully", 1, Rarity.LEGENDARY),
        BLACK_HOLE("Black Hole Bomb", "Bombs pull enemies in and hit twice as hard", 1, Rarity.LEGENDARY),
        CHAIN_REACTION("Chain Reaction", "Destroyed enemies explode and hurt their friends", 1, Rarity.LEGENDARY);

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
        // Damage over time from Incendiary rounds.
        double burnTimer, burnDamage, burnTick;
        // Wraiths and the Reaper cannot be touched or hit while phased out.
        boolean phased;
        // The Colossus marks the ground before it slams it. The Singularity pulls harder for a while.
        double slamWarn, pullTime;
        // Hydra heads and Eclipse shield orbs circle the boss they belong to, and die with it.
        Enemy host;
        int phase;

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
        boolean enemy, dead, drone, missile, splinter;
        Color color;
        List<Enemy> alreadyHit;
    }

    /** A marked spot on the ground that explodes when its timer runs out. Step out of the circle! */
    static final class Hazard {
        double x, y, radius, warn, total;
        Color color;
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
        volatile int effectsVolume = 100, musicVolume = 100;

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
            if (!effectsOn || effectsVolume <= 0) {
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
                        applyGain(clip, effectsVolume);
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

        /** Turns a clip down. A sound device without volume control simply plays at full level. */
        private static void applyGain(Clip clip, int percent) {
            try {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float db = percent <= 0 ? gain.getMinimum() : (float) (20 * Math.log10(percent / 100.0));
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
            } catch (IllegalArgumentException e) {
                // No volume control on this device.
            }
        }

        void setMusicVolume(int percent) {
            musicVolume = percent;
            musicWorker.execute(new Runnable() {
                @Override
                public void run() {
                    Clip current = tracks.get(playing);
                    if (current != null) {
                        applyGain(current, musicVolume);
                    }
                }
            });
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
                applyGain(next, musicVolume);
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
        /** Bosses in the order they appear, one every five waves. */
        static final EnemyType[] BOSSES = {EnemyType.OVERLORD, EnemyType.SERPENT, EnemyType.WARDEN, EnemyType.TEMPEST,
            EnemyType.QUEEN, EnemyType.HYDRA, EnemyType.REAPER, EnemyType.COLOSSUS, EnemyType.ECLIPSE, EnemyType.SINGULARITY};
        /** The boss order of older saves, which stored boss kills by position. */
        static final EnemyType[] OLD_BOSS_ORDER = {EnemyType.OVERLORD, EnemyType.SERPENT, EnemyType.WARDEN,
            EnemyType.QUEEN, EnemyType.REAPER, EnemyType.COLOSSUS, EnemyType.SINGULARITY};
        static final Color SHIELD_COLOR = new Color(110, 200, 255);
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
        final int[] bossKills = new int[BOSSES.length];
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

        // Settings, also saved.
        Language language = Language.ENGLISH;
        Map<String, String> strings = new HashMap<String, String>();
        final int[][] keys = new int[Action.values().length][2];
        MouseFunction rightClick = MouseFunction.SPECIAL, middleClick = MouseFunction.NONE;
        int shakeAmount = 100;
        boolean damageNumbers = true;

        // Menu and settings screens.
        int menuIndex, settingsTab, settingsRow, settingsSlot, captureSlot;
        Action capturing;
        State settingsReturn = State.MENU, screenReturn = State.HANGAR;

        State state = State.MENU;
        double clock;
        long lastTick;
        // Window scaling: the game always draws at W x H and is stretched to fit.
        double viewScale = 1, viewX, viewY;
        boolean fullscreen;

        // Input.
        boolean up, down, left, right, firing, fireKeyHeld, autoFire;
        double aimX = W / 2.0, aimY = H / 3.0;
        int mouseX, mouseY;

        // Player.
        double px, py, facing, invulnerable, dashTimer, dashCooldown, dashDirX, dashDirY, fireCooldown, hurtFlash;
        int health, maxHealth, bombs, secondLives, shields;
        double missileTimer, regenTimer, staticTimer, sawAngle;
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
        boolean clearing, newRecord, bossBanner;
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
        final List<Hazard> hazards = new ArrayList<Hazard>();
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
            resetKeys();
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

        // ============================= LANGUAGE =============================

        /** The text for a key in the current language. English is the fallback for anything missing. */
        String t(String key, String english, Object... args) {
            String text = strings.get(key);
            if (text == null) {
                text = english;
            }
            for (int i = 0; i < args.length; i++) {
                text = text.replace("{" + i + "}", String.valueOf(args[i]));
            }
            return text;
        }

        void applyLanguage(Language chosen) {
            language = chosen;
            strings = loadStrings(chosen);
        }

        static Map<String, String> loadStrings(Language chosen) {
            Map<String, String> result = new HashMap<String, String>();
            if (chosen == Language.ENGLISH) {
                return result;
            }
            try (InputStream in = NeonOverdrive.class.getResourceAsStream("lang/" + chosen.code + ".properties")) {
                if (in == null) {
                    System.err.println("Missing language file: lang/" + chosen.code + ".properties");
                    return result;
                }
                Properties data = new Properties();
                data.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String key : data.stringPropertyNames()) {
                    result.put(key, data.getProperty(key));
                }
            } catch (Exception e) {
                System.err.println("Could not read the language file: " + e.getMessage());
            }
            return result;
        }

        String label(Perk p) { return t("perk." + p.name() + ".title", p.title); }
        String about(Perk p) { return t("perk." + p.name() + ".text", p.description); }
        String label(Upgrade u) { return t("upgrade." + u.name() + ".title", u.title); }
        String about(Upgrade u) { return t("upgrade." + u.name() + ".text", u.description); }
        String label(Achievement a) { return t("achievement." + a.name() + ".title", a.title); }
        String about(Achievement a) { return t("achievement." + a.name() + ".text", a.description); }
        String label(Skin k) { return t("skin." + k.name() + ".title", k.title); }
        String about(Skin k) { return t("skin." + k.name() + ".text", k.description); }
        String label(Modifier m) { return t("modifier." + m.name() + ".title", m.title); }
        String about(Modifier m) { return t("modifier." + m.name() + ".text", m.description); }
        String label(Difficulty d) { return t("difficulty." + d.name(), d.title); }
        String label(Rarity r) { return t("rarity." + r.name(), r.label); }
        String label(Action a) { return t("action." + a.name(), a.title); }
        String label(MouseFunction f) { return t("mouse." + f.name(), f.title); }
        String label(MenuItem m) { return t("menu." + m.name(), m.title); }
        String role(Ship s) { return t("ship." + s.name() + ".role", s.role); }
        String specialText(Ship s) { return t("ship." + s.name() + ".special", s.specialText); }
        String label(Special s) {
            String english = s == Special.TIME_WARP ? "TIME WARP" : s.name();
            return t("special." + s.name(), english);
        }

        String unlockHint(Ship s) {
            return s == Ship.PHANTOM ? t("ship.PHANTOM.hint", "or reach wave 8")
                    : s == Ship.TITAN ? t("ship.TITAN.hint", "or beat The Serpent") : "";
        }

        String bossLabel(EnemyType type) {
            String english = type == EnemyType.QUEEN ? "THE HIVE QUEEN" : "THE " + type.name();
            return t("boss." + type.name(), english);
        }

        String themeLabel(Theme theme) {
            return t("theme." + Arrays.asList(themes).indexOf(theme), theme.name);
        }

        String runInfo(int runWave, Ship runShip, boolean daily) {
            return t("run.info", "Wave {0} - {1}", runWave, runShip.title) + (daily ? " " + t("run.daily", "(daily)") : "");
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
            for (int i = 0; i < OLD_BOSS_ORDER.length; i++) {
                bossKills[Arrays.asList(BOSSES).indexOf(OLD_BOSS_ORDER[i])] = readInt(data, "stat.boss." + i);
            }
            for (int i = 0; i < BOSSES.length; i++) {
                if (data.getProperty("stat.boss." + BOSSES[i].name()) != null) {
                    bossKills[i] = readInt(data, "stat.boss." + BOSSES[i].name());
                }
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
            sound.effectsVolume = readSetting(data, "option.soundVolume", 100, 100);
            sound.musicVolume = readSetting(data, "option.musicVolume", 100, 100);
            shakeAmount = readSetting(data, "option.shake", 100, 100);
            damageNumbers = Boolean.parseBoolean(data.getProperty("option.damageNumbers", "true"));
            for (Language l : Language.values()) {
                if (l.name().equals(data.getProperty("option.language"))) {
                    applyLanguage(l);
                }
            }
            for (MouseFunction f : MouseFunction.values()) {
                if (f.name().equals(data.getProperty("option.rightClick"))) {
                    rightClick = f;
                }
                if (f.name().equals(data.getProperty("option.middleClick"))) {
                    middleClick = f;
                }
            }
            for (Action a : Action.values()) {
                for (int slot = 0; slot < 2; slot++) {
                    keys[a.ordinal()][slot] = readSetting(data, "key." + a.name() + "." + slot,
                            keys[a.ordinal()][slot], 65535);
                }
            }
        }

        /** A setting that keeps its default when it is missing or damaged. */
        private static int readSetting(Properties data, String key, int fallback, int max) {
            try {
                String text = data.getProperty(key);
                int value = text == null ? fallback : Integer.parseInt(text.trim());
                return value < 0 || value > max ? fallback : value;
            } catch (NumberFormatException e) {
                System.err.println("Ignoring a damaged setting: " + key);
                return fallback;
            }
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
            for (int i = 0; i < BOSSES.length; i++) {
                data.setProperty("stat.boss." + BOSSES[i].name(), String.valueOf(bossKills[i]));
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
            data.setProperty("option.soundVolume", String.valueOf(sound.effectsVolume));
            data.setProperty("option.musicVolume", String.valueOf(sound.musicVolume));
            data.setProperty("option.shake", String.valueOf(shakeAmount));
            data.setProperty("option.damageNumbers", String.valueOf(damageNumbers));
            data.setProperty("option.language", language.name());
            data.setProperty("option.rightClick", rightClick.name());
            data.setProperty("option.middleClick", middleClick.name());
            for (Action a : Action.values()) {
                for (int slot = 0; slot < 2; slot++) {
                    data.setProperty("key." + a.name() + "." + slot, String.valueOf(keys[a.ordinal()][slot]));
                }
            }
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
                        savedRunInfo = runInfo(readInt(data, "wave"), s, Boolean.parseBoolean(data.getProperty("dailyRun")));
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
            data.setProperty("shields", String.valueOf(shields));
            try (OutputStream out = new FileOutputStream(file)) {
                data.store(out, "Neon Overdrive run in progress");
            } catch (Exception e) {
                System.err.println("Could not save the run: " + e.getMessage());
                return;
            }
            savedRunInfo = runInfo(wave, ship, dailyRun);
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
                // Runs saved by older versions have no shield value, so they keep the starting shields.
                shields = data.getProperty("shields") == null ? shields : readInt(data, "shields");
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
                showNotice(t("notice.continued", "Run continued"));
                return true;
            } catch (RuntimeException e) {
                System.err.println("The saved run is damaged and was dropped: " + e.getMessage());
                runActive = false;
                deleteRunFile();
                goToHangar();
                showNotice(t("notice.damagedRun", "The saved run was damaged and could not be loaded"));
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
            // Lets TAB reach the game, so it can switch settings tabs and be used as a game key.
            setFocusTraversalKeysEnabled(false);
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
                    if (state == State.MENU && !confirmReset) {
                        List<MenuItem> items = menuItems();
                        for (int i = 0; i < items.size(); i++) {
                            if (menuButton(i).contains(mouseX, mouseY)) {
                                menuIndex = i;
                            }
                        }
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    mouseMoved(e);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    mouseMoved(e);
                    if (capturing != null) {
                        capturing = null;
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        useMouseFunction(rightClick);
                    } else if (SwingUtilities.isMiddleMouseButton(e)) {
                        useMouseFunction(middleClick);
                    } else {
                        boolean wasPlaying = state == State.PLAYING;
                        handleClick(mouseX, mouseY);
                        firing = wasPlaying && state == State.PLAYING;
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
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
            fireKeyHeld = false;
        }

        void resetKeys() {
            for (Action a : Action.values()) {
                keys[a.ordinal()][0] = a.firstKey;
                keys[a.ordinal()][1] = a.secondKey;
            }
        }

        Action actionFor(int key) {
            if (key != 0) {
                for (Action a : Action.values()) {
                    if (keys[a.ordinal()][0] == key || keys[a.ordinal()][1] == key) {
                        return a;
                    }
                }
            }
            return null;
        }

        String keyName(int key) {
            return key == 0 ? "---" : KeyEvent.getKeyText(key);
        }

        /** The key shown on screen for an action: its first one that is set. */
        String keyLabel(Action a) {
            int[] slots = keys[a.ordinal()];
            return keyName(slots[0] != 0 ? slots[0] : slots[1]);
        }

        /** Puts a key on an action. If another action had it, the two swap keys, so none is duplicated. */
        void bindKey(Action a, int slot, int key) {
            int old = keys[a.ordinal()][slot];
            for (Action other : Action.values()) {
                int[] slots = keys[other.ordinal()];
                for (int i = 0; i < 2; i++) {
                    if (slots[i] == key && (other != a || i != slot)) {
                        slots[i] = old;
                        if (slots[0] == 0 && slots[1] == 0) {
                            showNotice(t("notice.noKey", "{0} has no key now", label(other)));
                        }
                    }
                }
            }
            keys[a.ordinal()][slot] = key;
            save();
        }

        private void captureKey(int key) {
            Action a = capturing;
            int slot = captureSlot;
            capturing = null;
            if (key == KeyEvent.VK_ESCAPE) {
                return;
            }
            if (key == KeyEvent.VK_DELETE || key == KeyEvent.VK_BACK_SPACE) {
                int other = keys[a.ordinal()][1 - slot];
                if (other == 0 && a != Action.FIRE) {
                    sound.play(Sound.Effect.DENY);
                    showNotice(t("notice.needsKey", "{0} needs at least one key", label(a)));
                } else {
                    keys[a.ordinal()][slot] = 0;
                    save();
                }
                return;
            }
            bindKey(a, slot, key);
            sound.play(Sound.Effect.PICKUP);
        }

        void handleKey(int key, boolean pressed) {
            if (capturing != null) {
                if (pressed) {
                    captureKey(key);
                }
                return;
            }
            if (state == State.SETTINGS) {
                if (pressed) {
                    settingsKey(key);
                }
                return;
            }
            Action action = actionFor(key);
            boolean moved = true;
            if (action == Action.MOVE_UP) {
                up = pressed;
            } else if (action == Action.MOVE_DOWN) {
                down = pressed;
            } else if (action == Action.MOVE_LEFT) {
                left = pressed;
            } else if (action == Action.MOVE_RIGHT) {
                right = pressed;
            } else if (action == Action.FIRE) {
                fireKeyHeld = pressed && state == State.PLAYING;
            } else {
                moved = false;
            }
            if (!pressed) {
                return;
            }
            // Arrow keys always work in menus, even after the movement keys were changed.
            boolean menuUp = key == KeyEvent.VK_UP || action == Action.MOVE_UP;
            boolean menuDown = key == KeyEvent.VK_DOWN || action == Action.MOVE_DOWN;
            boolean menuLeft = key == KeyEvent.VK_LEFT || action == Action.MOVE_LEFT;
            boolean menuRight = key == KeyEvent.VK_RIGHT || action == Action.MOVE_RIGHT;
            if (state == State.MENU && !confirmReset && (menuUp || menuDown)) {
                int count = menuItems().size();
                menuIndex = (menuIndex + (menuUp ? count - 1 : 1)) % count;
                return;
            }
            if (menuLeft || menuRight) {
                cycleShip(menuLeft ? -1 : 1);
            }
            if (!moved || state != State.PLAYING) {
                handleKeyPress(key, action);
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

        private void handleKeyPress(int key, Action action) {
            if (confirmReset) {
                // While the "are you sure?" box is open, only Y and N/ESC do anything.
                if (key == KeyEvent.VK_Y) {
                    resetProgress();
                } else if (key == KeyEvent.VK_N || key == KeyEvent.VK_ESCAPE) {
                    confirmReset = false;
                }
                return;
            }
            // Menu shortcuts are fixed and come first, so a changed game key can never break a menu.
            if (menuShortcut(key) || action == null) {
                return;
            }
            switch (action) {
                case SOUND:
                    sound.effectsOn = !sound.effectsOn;
                    save();
                    break;
                case MUSIC:
                    sound.toggleMusic();
                    save();
                    break;
                case FULLSCREEN:
                    toggleFullscreen();
                    break;
                case AUTO_FIRE:
                    autoFire = !autoFire;
                    save();
                    break;
                case PAUSE:
                    goBack();
                    break;
                case DASH:
                    if (state == State.PLAYING) {
                        tryDash();
                    } else if (state == State.PAUSED) {
                        state = State.PLAYING;
                    }
                    break;
                case BOMB:
                    useBomb();
                    break;
                case SPECIAL:
                    useSpecial();
                    break;
                case REROLL:
                    reroll();
                    break;
                default:
                    break;
            }
        }

        /** ESC and the pause key: pause the game, or step back one screen. */
        private void goBack() {
            if (state == State.PLAYING) {
                state = State.PAUSED;
            } else if (state == State.PAUSED) {
                state = State.PLAYING;
            } else if (state == State.HANGAR) {
                state = State.MENU;
            } else if (state == State.RECORDS || state == State.SKINS || state == State.WORKSHOP) {
                state = screenReturn;
            }
        }

        private void openScreen(State screen) {
            screenReturn = state;
            state = screen;
        }

        private boolean menuShortcut(int key) {
            if (key == KeyEvent.VK_ESCAPE) {
                goBack();
                return true;
            }
            int choice = key >= KeyEvent.VK_1 && key <= KeyEvent.VK_3 ? key - KeyEvent.VK_1 : -1;
            switch (state) {
                case MENU:
                    if (key == KeyEvent.VK_DELETE) {
                        confirmReset = true;
                    } else if (key == KeyEvent.VK_ENTER) {
                        List<MenuItem> items = menuItems();
                        activateMenu(items.get(Math.min(menuIndex, items.size() - 1)));
                    } else if (key == KeyEvent.VK_O) {
                        openSettings();
                    } else {
                        return false;
                    }
                    return true;
                case HANGAR:
                    if (key == KeyEvent.VK_K) {
                        openScreen(State.SKINS);
                    } else if (key == KeyEvent.VK_T) {
                        openScreen(State.RECORDS);
                    } else if (key == KeyEvent.VK_U) {
                        openScreen(State.WORKSHOP);
                    } else if (key == KeyEvent.VK_O) {
                        openSettings();
                    } else if (key == KeyEvent.VK_C && hasSavedRun()) {
                        continueRun();
                    } else if (key == KeyEvent.VK_G) {
                        startDaily();
                    } else if (key == KeyEvent.VK_ENTER) {
                        startGame();
                    } else if (choice >= 0) {
                        difficulty = Difficulty.values()[choice];
                    } else {
                        return false;
                    }
                    return true;
                case SKINS:
                    if (key == KeyEvent.VK_K) {
                        goBack();
                        return true;
                    }
                    return false;
                case WORKSHOP:
                    if (key == KeyEvent.VK_U) {
                        goBack();
                        return true;
                    }
                    return false;
                case RECORDS:
                    if (key == KeyEvent.VK_T || key == KeyEvent.VK_ENTER) {
                        goBack();
                        return true;
                    }
                    return false;
                case PAUSED:
                    if (key == KeyEvent.VK_X) {
                        endRun();
                        goToHangar();
                    } else if (key == KeyEvent.VK_O) {
                        openSettings();
                    } else {
                        return false;
                    }
                    return true;
                case UPGRADE:
                    if (choice >= 0) {
                        chooseCard(choice);
                        return true;
                    }
                    return false;
                case GAME_OVER:
                    if (key == KeyEvent.VK_ENTER && overDelay <= 0) {
                        goToHangar();
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        }

        private void useMouseFunction(MouseFunction function) {
            if (function == MouseFunction.SPECIAL) {
                useSpecial();
            } else if (function == MouseFunction.DASH) {
                tryDash();
            } else if (function == MouseFunction.BOMB) {
                useBomb();
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
                        List<MenuItem> items = menuItems();
                        for (int i = 0; i < items.size(); i++) {
                            if (menuButton(i).contains(x, y)) {
                                activateMenu(items.get(i));
                                return;
                            }
                        }
                    }
                    break;
                case HANGAR:
                    hangarClick(x, y);
                    break;
                case RECORDS:
                    goBack();
                    break;
                case SKINS:
                    skinsClick(x, y);
                    break;
                case WORKSHOP:
                    workshopClick(x, y);
                    break;
                case SETTINGS:
                    settingsClick(x, y);
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
                    if (pauseSettingsButton().contains(x, y)) {
                        openSettings();
                    } else {
                        state = State.PLAYING;
                    }
                    break;
                default:
                    break;
            }
        }

        // ============================ MAIN MENU =============================

        List<MenuItem> menuItems() {
            List<MenuItem> items = new ArrayList<MenuItem>(Arrays.asList(MenuItem.values()));
            if (!hasSavedRun()) {
                items.remove(MenuItem.CONTINUE);
            }
            return items;
        }

        Rectangle menuButton(int index) {
            return new Rectangle(60, 205 + index * 55, 290, 48);
        }

        void activateMenu(MenuItem item) {
            sound.play(Sound.Effect.PICKUP);
            switch (item) {
                case PLAY:
                    state = State.HANGAR;
                    break;
                case CONTINUE:
                    continueRun();
                    break;
                case DAILY:
                    startDaily();
                    break;
                case UPGRADES:
                    openScreen(State.WORKSHOP);
                    break;
                case SKINS:
                    openScreen(State.SKINS);
                    break;
                case RECORDS:
                    openScreen(State.RECORDS);
                    break;
                case SETTINGS:
                    openSettings();
                    break;
                default:
                    quitGame();
                    break;
            }
        }

        private void quitGame() {
            java.awt.Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null) {
                // Goes through the normal close, which saves the game first.
                window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
            }
        }

        // ============================= SETTINGS =============================

        static final int TAB_GENERAL = 0, TAB_CONTROLS = 1, TAB_LANGUAGE = 2;
        static final int OPT_SOUND = 0, OPT_SOUND_VOLUME = 1, OPT_MUSIC = 2, OPT_MUSIC_VOLUME = 3, OPT_SHAKE = 4,
                OPT_NUMBERS = 5, OPT_AUTO_FIRE = 6, OPT_FULLSCREEN = 7, OPTION_COUNT = 8;

        void openSettings() {
            settingsReturn = state;
            releaseInput();
            capturing = null;
            settingsRow = 0;
            settingsSlot = 0;
            state = State.SETTINGS;
        }

        void closeSettings() {
            capturing = null;
            state = settingsReturn;
            save();
        }

        void chooseLanguage(Language chosen) {
            applyLanguage(chosen);
            refreshSavedRun();
            sound.play(Sound.Effect.PICKUP);
            save();
        }

        Rectangle settingsTabButton(int index) {
            return new Rectangle(250 + index * 170, 18, 160, 44);
        }

        Rectangle settingsBackButton() {
            return new Rectangle(40, 628, 220, 50);
        }

        Rectangle resetKeysButton() {
            return new Rectangle(660, 628, 300, 50);
        }

        Rectangle optionRow(int index) {
            return new Rectangle(120, 100 + index * 62, 760, 52);
        }

        Rectangle keyRow(int index) {
            return new Rectangle(40, 76 + index * 34, 920, 31);
        }

        Rectangle keySlot(int index, int slot) {
            Rectangle row = keyRow(index);
            if (index >= Action.values().length) {
                return new Rectangle(500, row.y + 2, 450, row.height - 4);
            }
            return new Rectangle(500 + slot * 230, row.y + 2, 220, row.height - 4);
        }

        Rectangle languageCard(int index) {
            return new Rectangle(100 + index % 3 * 275, 110 + index / 3 * 150, 250, 130);
        }

        private int settingsRowCount() {
            return settingsTab == TAB_GENERAL ? OPTION_COUNT
                    : settingsTab == TAB_CONTROLS ? Action.values().length + 2 : Language.values().length;
        }

        void changeOption(int option, int direction) {
            switch (option) {
                case OPT_SOUND:
                    sound.effectsOn = !sound.effectsOn;
                    break;
                case OPT_SOUND_VOLUME:
                    sound.effectsVolume = (int) clamp(sound.effectsVolume + direction * 10, 0, 100);
                    sound.play(Sound.Effect.COIN);
                    break;
                case OPT_MUSIC:
                    sound.toggleMusic();
                    break;
                case OPT_MUSIC_VOLUME:
                    sound.setMusicVolume((int) clamp(sound.musicVolume + direction * 10, 0, 100));
                    break;
                case OPT_SHAKE:
                    shakeAmount = (int) clamp(shakeAmount + direction * 50, 0, 100);
                    break;
                case OPT_NUMBERS:
                    damageNumbers = !damageNumbers;
                    break;
                case OPT_AUTO_FIRE:
                    autoFire = !autoFire;
                    break;
                default:
                    toggleFullscreen();
                    break;
            }
            save();
        }

        private void cycleMouseFunction(int row, int direction) {
            int count = MouseFunction.values().length;
            boolean right = row == Action.values().length;
            int index = (right ? rightClick : middleClick).ordinal();
            MouseFunction next = MouseFunction.values()[(index + direction + count) % count];
            if (right) {
                rightClick = next;
            } else {
                middleClick = next;
            }
            save();
        }

        private void activateKeyRow(int row, int slot) {
            if (row >= Action.values().length) {
                cycleMouseFunction(row, 1);
            } else {
                capturing = Action.values()[row];
                captureSlot = slot;
            }
        }

        private void settingsKey(int key) {
            int rows = settingsRowCount();
            boolean grid = settingsTab == TAB_LANGUAGE;
            if (key == KeyEvent.VK_ESCAPE) {
                closeSettings();
            } else if (key == KeyEvent.VK_TAB) {
                settingsTab = (settingsTab + 1) % 3;
                settingsRow = 0;
            } else if (key == KeyEvent.VK_UP || key == KeyEvent.VK_DOWN) {
                int step = grid ? 3 : 1;
                settingsRow = (settingsRow + (key == KeyEvent.VK_UP ? rows - step : step)) % rows;
            } else if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_RIGHT) {
                int direction = key == KeyEvent.VK_LEFT ? -1 : 1;
                if (grid) {
                    settingsRow = (settingsRow + direction + rows) % rows;
                } else if (settingsTab == TAB_GENERAL) {
                    changeOption(settingsRow, direction);
                } else if (settingsRow >= Action.values().length) {
                    cycleMouseFunction(settingsRow, direction);
                } else {
                    settingsSlot = 1 - settingsSlot;
                }
            } else if (key == KeyEvent.VK_ENTER) {
                if (grid) {
                    chooseLanguage(Language.values()[settingsRow]);
                } else if (settingsTab == TAB_GENERAL) {
                    changeOption(settingsRow, 1);
                } else {
                    activateKeyRow(settingsRow, settingsSlot);
                }
            }
        }

        private void settingsClick(int x, int y) {
            for (int i = 0; i < 3; i++) {
                if (settingsTabButton(i).contains(x, y)) {
                    settingsTab = i;
                    settingsRow = 0;
                    return;
                }
            }
            if (settingsBackButton().contains(x, y)) {
                closeSettings();
            } else if (settingsTab == TAB_GENERAL) {
                for (int i = 0; i < OPTION_COUNT; i++) {
                    Rectangle row = optionRow(i);
                    if (row.contains(x, y)) {
                        settingsRow = i;
                        changeOption(i, x < row.x + row.width - 150 ? -1 : 1);
                    }
                }
            } else if (settingsTab == TAB_CONTROLS) {
                if (resetKeysButton().contains(x, y)) {
                    resetKeys();
                    rightClick = MouseFunction.SPECIAL;
                    middleClick = MouseFunction.NONE;
                    sound.play(Sound.Effect.UPGRADE);
                    showNotice(t("notice.keysReset", "Controls are back to the defaults"));
                    save();
                    return;
                }
                for (int i = 0; i < settingsRowCount(); i++) {
                    for (int slot = 0; slot < 2; slot++) {
                        if (keySlot(i, slot).contains(x, y)) {
                            settingsRow = i;
                            settingsSlot = slot;
                            activateKeyRow(i, slot);
                            return;
                        }
                    }
                }
            } else {
                for (Language l : Language.values()) {
                    if (languageCard(l.ordinal()).contains(x, y)) {
                        settingsRow = l.ordinal();
                        chooseLanguage(l);
                    }
                }
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

        /** Permanent upgrades have their own screen: five columns, four rows. */
        Rectangle perkCard(int index) {
            return new Rectangle(40 + index % 5 * 186, 92 + index / 5 * 130, 176, 122);
        }

        Rectangle workshopButton() {
            return new Rectangle(40, 402, 920, 118);
        }

        Rectangle workshopBackButton() {
            return new Rectangle(40, 620, 220, 62);
        }

        Rectangle settingsButton() {
            return new Rectangle(450, 16, 190, 66);
        }

        Rectangle pauseSettingsButton() {
            return new Rectangle(W / 2 - 130, 450, 260, 50);
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
            if (workshopButton().contains(x, y)) {
                openScreen(State.WORKSHOP);
            } else if (settingsButton().contains(x, y)) {
                openSettings();
            } else if (skinsButton().contains(x, y)) {
                openScreen(State.SKINS);
            } else if (hasSavedRun() && continueButton().contains(x, y)) {
                continueRun();
            } else if (launchButton().contains(x, y)) {
                startGame();
            } else if (dailyButton().contains(x, y)) {
                startDaily();
            } else if (recordsButton().contains(x, y)) {
                openScreen(State.RECORDS);
            }
        }

        private void workshopClick(int x, int y) {
            for (Perk p : Perk.values()) {
                if (perkCard(p.ordinal()).contains(x, y)) {
                    buyPerk(p);
                    return;
                }
            }
            if (workshopBackButton().contains(x, y)) {
                goBack();
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
                showNotice(t("notice.unlocked", "{0} unlocked!", s.title));
            } else {
                sound.play(Sound.Effect.DENY);
                showNotice(t("notice.needDiamonds", "You need {0} more diamonds for {1}", s.cost - diamonds, s.title));
            }
            checkAchievements();
            save();
        }

        void buyPerk(Perk p) {
            int level = perks[p.ordinal()];
            if (level >= p.max) {
                sound.play(Sound.Effect.DENY);
                showNotice(t("notice.maxed", "{0} is already maxed out", label(p)));
                return;
            }
            int cost = p.costAt(level);
            if (coins < cost) {
                sound.play(Sound.Effect.DENY);
                showNotice(t("notice.needCoins", "You need {0} more coins for {1}", cost - coins, label(p)));
                return;
            }
            coins -= cost;
            perks[p.ordinal()]++;
            sound.play(Sound.Effect.UPGRADE);
            showNotice(t("notice.upgraded", "{0} upgraded!", label(p)));
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
                dailyNote = t("daily.first", "First daily run today: +{0} coins, +{1} {2}  ({3} day streak)", bonus, gems,
                        gems > 1 ? t("word.diamonds", "diamonds") : t("word.diamond", "diamond"), dailyStreak);
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
            unlock(Achievement.VETERAN, bestWave >= 30);
            unlock(Achievement.LEGEND, bestWave >= 50);
            boolean allBosses = true;
            for (int count : bossKills) {
                allBosses &= count > 0;
            }
            unlock(Achievement.MONSTER_HUNTER, allBosses);
            unlock(Achievement.ANNIHILATOR, totalKills >= 10000);
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
                toasts.add(t("toast.achievement", "{0}  +{1} coins  +{2} diamonds", label(a), a.reward, a.diamonds));
                save();
            }
        }

        private void earnShip(Ship s, boolean reached) {
            if (reached && !unlocked[s.ordinal()]) {
                unlocked[s.ordinal()] = true;
                toasts.add(t("toast.ship", "New ship unlocked: {0}", s.title));
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
            grantHeadStart();
            for (int i = 0; i < perk(Perk.DRONE_BAY) && lvl(Upgrade.DRONE) < Upgrade.DRONE.max; i++) {
                applyUpgrade(Upgrade.DRONE);
            }
            nextWave();
        }

        /** How long the combo survives without a kill. */
        double comboDuration() {
            return 2.5 + 0.5 * perk(Perk.COMBO_KEEPER);
        }

        int lvl(Upgrade upgrade) {
            return stacks[upgrade.ordinal()];
        }

        int perk(Perk p) {
            return perks[p.ordinal()];
        }

        /** The Head Start perk hands out free common upgrades at the start of a new run. */
        private void grantHeadStart() {
            for (int i = 0; i < perk(Perk.HEAD_START); i++) {
                List<Upgrade> pool = new ArrayList<Upgrade>();
                for (Upgrade upgrade : Upgrade.values()) {
                    if (upgrade.rarity == Rarity.COMMON && lvl(upgrade) < upgrade.max) {
                        pool.add(upgrade);
                    }
                }
                if (!pool.isEmpty()) {
                    applyUpgrade(pool.get(rng.nextInt(pool.size())));
                }
            }
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
            secondLives = perk(Perk.SECOND_WIND);
            shields = perk(Perk.SHIELD);
            missileTimer = 1;
            regenTimer = 0;
            staticTimer = 1;
            hazards.clear();
            fireKeyHeld = false;
            invulnerable = 0;
            dashTimer = 0;
            dashCooldown = 0;
            fireCooldown = 0;
            hurtFlash = 0;
            specialCharge = 0.5;
            overdriveTimer = 0;
            warpTimer = 0;
            fortressTimer = 0;
            fireDelay = ship.fireDelay * Math.pow(0.94, perk(Perk.FIRE_RATE));
            bulletDamage = ship.damage * modifier.damage * (1 + 0.08 * perk(Perk.DAMAGE));
            bulletRadius = ship.bulletRadius;
            moveSpeed = ship.speed * (1 + 0.05 * perk(Perk.THRUSTERS));
            dashDelay = ship.dashDelay * Math.pow(0.92, perk(Perk.DASH_CORE));
            shots = ship.shots;
            critChance = 0.04 * perk(Perk.TARGETING);
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
            // Shield Matrix tops the shields back up at the start of every wave.
            shields = Math.max(shields, lvl(Upgrade.SHIELD_MATRIX));
            if (wave % 5 == 0) {
                EnemyType boss = BOSSES[(wave / 5 - 1) % BOSSES.length];
                spawnQueue.add(boss);
                for (int i = 0; i < wave / 5 * 3; i++) {
                    spawnQueue.add(EnemyType.CHASER);
                }
                banner = bossLabel(boss);
                bossBanner = true;
                sound.play(Sound.Effect.BOSS);
            } else {
                int count = (int) (Math.min(60, 6 + wave * 3) * difficulty.enemyCount * modifier.enemyCount);
                for (int i = 0; i < count; i++) {
                    spawnQueue.add(rollEnemy());
                }
                banner = t("hud.waveBanner", "WAVE {0}", wave);
                bossBanner = false;
                if ((wave - 1) % 5 == 0 && wave > 1) {
                    banner = themeLabel(currentTheme()).toUpperCase();
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
            // The newest enemies get their own roll, so they never crowd out the older ones.
            int fresh = rng.nextInt(100);
            if (wave >= 14 && fresh < 4) return EnemyType.PULSAR;
            if (wave >= 12 && fresh < 8) return EnemyType.MORTAR;
            if (wave >= 9 && fresh < 12) return EnemyType.SPINNER;
            if (wave >= 7 && fresh < 16) return EnemyType.SPORE;
            if (wave >= 3 && fresh < 20) return EnemyType.SWARMER;
            // The newer enemies join in step by step, on top of the classic mix below.
            int special = rng.nextInt(100);
            if (wave >= 13 && special < 5) return EnemyType.WRAITH;
            if (wave >= 11 && special < 9) return EnemyType.HIVE;
            if (wave >= 10 && special < 14) return EnemyType.BULWARK;
            if (wave >= 9 && special < 18) return EnemyType.MEDIC;
            if (wave >= 8 && special < 23) return EnemyType.SNIPER;
            if (wave >= 6 && special < 28) return EnemyType.LAYER;
            if (wave >= 5 && special < 33) return EnemyType.TURRET;
            if (wave >= 4 && special < 39) return EnemyType.RAMMER;
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
            if (type == EnemyType.ECLIPSE) {
                // Four shield orbs circle the Eclipse and stop bullets. Shoot through the gaps.
                for (int i = 0; i < 4; i++) {
                    Enemy orb = new Enemy(EnemyType.ORB, x, y, 1);
                    orb.host = enemy;
                    orb.phase = i;
                    orb.spawnTotal = enemy.spawnTotal;
                    orb.spawnTimer = enemy.spawnTotal;
                    newEnemies.add(orb);
                }
            }
            if (type == EnemyType.SWARMER) {
                // Swarmers always come as a pack.
                for (int i = 0; i < 3; i++) {
                    Enemy buddy = new Enemy(type, clamp(x + (rng.nextDouble() - 0.5) * 60, 30, W - 30),
                            clamp(y + (rng.nextDouble() - 0.5) * 60, 30, H - 30), hp);
                    buddy.spawnTotal = enemy.spawnTotal;
                    buddy.spawnTimer = enemy.spawnTotal;
                    buddy.wobble = rng.nextDouble() * 10;
                    newEnemies.add(buddy);
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

            if (state == State.PAUSED || state == State.SETTINGS) {
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
            if (state == State.SETTINGS && settingsReturn == State.PAUSED) {
                return; // Settings opened from the pause screen leave the music alone.
            }
            if (state == State.MENU || state == State.HANGAR || state == State.RECORDS || state == State.SKINS
                    || state == State.WORKSHOP || state == State.SETTINGS) {
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
            updateMissiles(dt);
            updateOrbitals(dt);
            updateRunUpgrades(dt);
            updateBullets(dt);
            updateEnemies(dt);
            updateHazards(dt);
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

        /** Regenerator, Static Field and Saw Blades: upgrades that work on their own every frame. */
        private void updateRunUpgrades(double dt) {
            int regen = lvl(Upgrade.REGEN);
            if (regen > 0 && health < maxHealth) {
                regenTimer += dt;
                if (regenTimer >= (regen > 1 ? 25 : 40)) {
                    regenTimer = 0;
                    health++;
                    addText(px, py - 22, t("hud.plusHealth", "+1 HEALTH"), new Color(255, 120, 140), 18);
                    sound.play(Sound.Effect.PICKUP);
                }
            }
            int zaps = lvl(Upgrade.STATIC_FIELD);
            if (zaps > 0) {
                staticTimer -= dt;
                if (staticTimer <= 0) {
                    staticTimer = 1;
                    List<Enemy> hit = new ArrayList<Enemy>();
                    for (int i = 0; i < zaps; i++) {
                        Enemy target = nearestEnemy(px, py, 240, hit);
                        if (target == null) {
                            break;
                        }
                        hit.add(target);
                        Bolt bolt = new Bolt();
                        bolt.x1 = px;
                        bolt.y1 = py;
                        bolt.x2 = target.x;
                        bolt.y2 = target.y;
                        bolt.life = 0.16;
                        bolts.add(bolt);
                        damage(target, Math.max(2, bulletDamage * 1.2), false);
                    }
                    if (!hit.isEmpty()) {
                        sound.play(Sound.Effect.ZAP);
                    }
                }
            }
            int blades = lvl(Upgrade.SAW_BLADES);
            sawAngle -= dt * 2.4;
            for (int i = 0; i < blades; i++) {
                double angle = sawAngle + i * Math.PI * 2 / blades;
                double bx = px + Math.cos(angle) * 125, by = py + Math.sin(angle) * 125;
                for (Enemy e : enemies) {
                    if (!e.dead && e.spawnTimer <= 0 && e.contactCooldown <= 0 && Math.hypot(e.x - bx, e.y - by) < e.r + 16) {
                        e.contactCooldown = 0.25;
                        damage(e, 1 + bulletDamage * 0.8, false);
                        burst(bx, by, 4, new Color(220, 230, 255), 160);
                    }
                }
            }
        }

        /** Marked circles on the ground. When the timer runs out they explode. */
        private void updateHazards(double dt) {
            for (Iterator<Hazard> it = hazards.iterator(); it.hasNext(); ) {
                Hazard h = it.next();
                h.warn -= dt * enemyTimeScale();
                if (h.warn > 0) {
                    continue;
                }
                it.remove();
                addRing(h.x, h.y, h.radius * 1.2, 0.3, h.color);
                addFlash(h.x, h.y, h.radius, 0.18, brighten(h.color));
                burst(h.x, h.y, 18, h.color, 300);
                shake = Math.max(shake, 7);
                sound.play(Sound.Effect.BOOM);
                if (Math.hypot(px - h.x, py - h.y) < h.radius + PLAYER_RADIUS * 0.5 && canBeHurt()) {
                    hurtPlayer();
                }
            }
        }

        private void addHazard(double x, double y, double radius, double warn, Color color) {
            Hazard h = new Hazard();
            h.x = clamp(x, 20, W - 20);
            h.y = clamp(y, 20, H - 20);
            h.radius = radius;
            h.warn = warn;
            h.total = warn;
            h.color = color;
            hazards.add(h);
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

            if ((firing || fireKeyHeld || autoFire) && fireCooldown <= 0) {
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
            double amount = bulletDamage;
            if (lvl(Upgrade.BERSERK) > 0) {
                amount *= 1 + 0.08 * (multiplier() - 1);
            }
            fireVolley(amount, 0);
            if (lvl(Upgrade.DOUBLE_TAP) > 0 && rng.nextDouble() < 0.25) {
                fireVolley(amount, 0.07);
            }
            addParticle(px + Math.cos(facing) * 20, py + Math.sin(facing) * 20,
                    Math.cos(facing) * 60, Math.sin(facing) * 60, 0.08, 8, Color.WHITE);
            sound.play(Sound.Effect.SHOOT);
        }

        private void fireVolley(double amount, double offset) {
            for (int i = 0; i < shots; i++) {
                double angle = facing + offset + (i - (shots - 1) / 2.0) * ship.spread;
                addPlayerBullet(px + Math.cos(angle) * 18, py + Math.sin(angle) * 18, angle, amount, false);
            }
        }

        private void updateMissiles(double dt) {
            int count = lvl(Upgrade.MISSILES);
            if (count == 0) {
                return;
            }
            missileTimer -= dt;
            if (missileTimer > 0) {
                return;
            }
            if (nearestEnemy(px, py, 800, null) == null) {
                missileTimer = 0.2;
                return;
            }
            missileTimer = 1.6;
            for (int i = 0; i < count; i++) {
                double angle = facing + Math.PI + (i - (count - 1) / 2.0) * 0.7;
                Bullet b = addPlayerBullet(px, py, angle, Math.max(2, bulletDamage * 1.5), true);
                b.missile = true;
                b.r = 5;
                b.life = 2.4;
                b.pierce = 0;
                b.bounces = 0;
                b.vx *= 0.65;
                b.vy *= 0.65;
                b.color = new Color(255, 180, 90);
            }
            sound.play(Sound.Effect.DASH);
        }

        private Bullet addPlayerBullet(double x, double y, double angle, double amount, boolean fromDrone) {
            Bullet b = new Bullet();
            b.x = x;
            b.y = y;
            // The first hit check starts at the gun, so enemies touching the ship can still be shot.
            b.sweepX = fromDrone ? x : px;
            b.sweepY = fromDrone ? y : py;
            double speed = 680 * (1 + 0.1 * lvl(Upgrade.LONG_BARREL));
            b.vx = Math.cos(angle) * speed;
            b.vy = Math.sin(angle) * speed;
            b.r = fromDrone ? 3.5 : bulletRadius;
            b.damage = amount;
            // Drones aim up to 460 away, so their bullets must always fly at least that far.
            b.life = (fromDrone ? Math.max(ship.bulletLife, 0.8) : ship.bulletLife) * (1 + 0.3 * lvl(Upgrade.LONG_BARREL));
            b.pierce = pierce;
            b.bounces = bounces;
            b.drone = fromDrone;
            b.color = fromDrone ? new Color(180, 255, 200) : brighten(shipColor());
            newBullets.add(b);
            return b;
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
            int wave2 = lvl(Upgrade.SHOCKWAVE);
            if (wave2 > 0) {
                explode(px, py, 80 + 25 * wave2, Math.max(2, bulletDamage) * (1 + wave2));
            }
        }

        void useBomb() {
            if (state != State.PLAYING || bombs <= 0) {
                return;
            }
            bombs--;
            if (lvl(Upgrade.BLACK_HOLE) > 0) {
                // Drags everything that is not bolted down toward the ship first.
                for (Enemy e : enemies) {
                    if (!e.dead && !e.type.isAnchored() && !e.type.isBoss()) {
                        e.x += (px - e.x) * 0.6;
                        e.y += (py - e.y) * 0.6;
                    }
                }
                addRing(px, py, 500, 0.5, new Color(150, 90, 255));
                blast(px, py, 80);
            } else {
                blast(px, py, 40);
            }
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
                    announce(label(Special.OVERDRIVE) + "!", shipColor());
                    break;
                case NOVA:
                    for (int i = 0; i < 40; i++) {
                        addPlayerBullet(px, py, i * Math.PI * 2 / 40, Math.max(2, bulletDamage * 2), false);
                    }
                    for (Enemy e : enemies) {
                        if (!e.type.isAnchored() && !e.type.isBoss()) {
                            double d = Math.max(1, Math.hypot(e.x - px, e.y - py));
                            if (d < 300) {
                                e.x = clamp(e.x + (e.x - px) / d * 90, e.r, W - e.r);
                                e.y = clamp(e.y + (e.y - py) / d * 90, e.r, H - e.r);
                            }
                        }
                    }
                    announce(label(Special.NOVA) + "!", shipColor());
                    break;
                case TIME_WARP:
                    warpTimer = SPECIAL_TIME_WARP;
                    announce(label(Special.TIME_WARP) + "!", shipColor());
                    break;
                default:
                    fortressTimer = SPECIAL_TIME_FORTRESS;
                    announce(label(Special.FORTRESS) + "!", shipColor());
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
                if (!e.dead && !e.phased && e.spawnTimer <= 0 && e.type != EnemyType.ORB
                        && (exclude == null || !exclude.contains(e))) {
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
                if (!b.enemy && (homing > 0 || b.missile)) {
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
                double rate = b.missile ? Math.max(homing, 7) : homing;
                double turn = clamp(diff, -rate * dt, rate * dt);
                b.vx = Math.cos(current + turn) * speed;
                b.vy = Math.sin(current + turn) * speed;
            }
        }

        private void hitEnemies(Bullet b) {
            for (Enemy e : enemies) {
                if (e.dead || e.phased || e.spawnTimer > 0 || (b.alreadyHit != null && b.alreadyHit.contains(e))) {
                    continue;
                }
                if (distanceToPath(b, e.x, e.y) < e.r + b.r) {
                    if (shieldBlocks(e, b)) {
                        knockBack(e, b);
                        burst(b.x, b.y, 5, Color.WHITE, 150);
                        b.dead = true;
                        return;
                    }
                    boolean crit = rng.nextDouble() < critChance;
                    double amount = b.damage * (crit ? 3 + lvl(Upgrade.DEADEYE) : 1);
                    damage(e, amount, crit);
                    knockBack(e, b);
                    if (frostTime > 0) {
                        e.slowTimer = frostTime;
                    }
                    if (lvl(Upgrade.INCENDIARY) > 0) {
                        Enemy target = e.owner != null ? e.owner : e;
                        target.burnTimer = 2.5;
                        target.burnDamage = Math.max(target.burnDamage, b.damage * 0.25 * lvl(Upgrade.INCENDIARY));
                    }
                    burst(b.x, b.y, 3, e.type.color, 130);
                    if (b.missile) {
                        explode(b.x, b.y, 55, b.damage * 0.7);
                    }
                    if (explosiveRadius > 0 && !b.drone) {
                        explode(b.x, b.y, explosiveRadius, b.damage * 0.6);
                    }
                    if (chainTargets > 0 && !b.drone) {
                        chainLightning(e, b.damage * 0.5);
                    }
                    if (lvl(Upgrade.SPLINTER) > 0 && !b.splinter && !b.drone && !b.missile) {
                        splinter(b, e);
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

        /** Splinter Rounds: a hit throws small shards out sideways, which can hit other enemies. */
        private void splinter(Bullet b, Enemy hit) {
            int count = lvl(Upgrade.SPLINTER) + 1;
            double heading = Math.atan2(b.vy, b.vx);
            for (int i = 0; i < count; i++) {
                double angle = heading + (i - (count - 1) / 2.0) * 0.6;
                // Marked as a drone bullet, so shards never set off explosions or lightning themselves.
                Bullet shard = addPlayerBullet(b.x, b.y, angle, b.damage * 0.4, true);
                shard.splinter = true;
                shard.r = 3;
                shard.life = 0.35;
                shard.pierce = 0;
                shard.bounces = 0;
                shard.color = b.color;
                shard.alreadyHit = new ArrayList<Enemy>();
                shard.alreadyHit.add(hit);
            }
        }

        /** A Bulwark stops anything coming at its front. Get around it, or use blasts and lightning. */
        private boolean shieldBlocks(Enemy e, Bullet b) {
            if (e.type == EnemyType.ORB) {
                return true;
            }
            if (e.type != EnemyType.BULWARK) {
                return false;
            }
            double speed = Math.max(1, Math.hypot(b.vx, b.vy));
            return -(b.vx * Math.cos(e.aimX) + b.vy * Math.sin(e.aimX)) / speed > 0.55;
        }

        /** A small shove in the bullet's direction. Big enemies and elites barely move. */
        private void knockBack(Enemy e, Bullet b) {
            if (e.type.isBoss() || e.owner != null || e.type.isAnchored()) {
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
            if (e.dead || e.phased || e.type == EnemyType.ORB) {
                return;
            }
            e.flash = 0.08;
            if (e.owner != null) {
                // Serpent body parts pass half the damage to the head.
                damage(e.owner, amount * 0.5, crit);
                return;
            }
            if (e.type.isBoss() || e.elite) {
                amount *= 1 + 0.25 * lvl(Upgrade.GIANT_SLAYER);
            }
            runDamage += Math.min(amount, Math.max(0, e.hp));
            if (!crit) {
                e.pendingDamage += amount;
            }
            e.hp -= amount;
            if (lvl(Upgrade.EXECUTIONER) > 0 && !e.type.isBoss() && e.hp > 0 && e.hp < e.maxHp * 0.15) {
                e.hp = 0;
            }
            if (e.type.isBoss()) {
                // Worth about two specials per boss, however strong your weapon is.
                chargeSpecial(Math.min(amount, Math.max(0, e.hp + amount)) / e.maxHp * 2);
            }
            sound.play(Sound.Effect.HIT);
            if (crit) {
                addText(e.x, e.y - e.r, t("hud.crit", "CRIT") + " " + (int) Math.ceil(amount), new Color(255, 240, 150), 18);
            }
            if (e.hp <= 0) {
                kill(e);
            }
        }

        /** No charging while a special is running, so specials can never stay on forever. */
        private void chargeSpecial(double amount) {
            if (overdriveTimer <= 0 && warpTimer <= 0 && fortressTimer <= 0) {
                double rate = 1 + 0.15 * perk(Perk.REACTOR) + 0.25 * lvl(Upgrade.POWER_CELL);
                specialCharge = Math.min(1, specialCharge + amount * rate);
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
            comboTimer = comboDuration();
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
                    addText(px, py - 22, t("hud.plusHealth", "+1 HEALTH"), new Color(255, 120, 140), 18);
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
            if (e.type == EnemyType.SPORE) {
                // Spores burst into a slow ring of seeds when they pop.
                double offset = rng.nextDouble() * Math.PI;
                for (int i = 0; i < 8; i++) {
                    fireEnemyBullet(e.x, e.y, offset + i * Math.PI / 4, 135, e.type.color);
                }
            }
            if (lvl(Upgrade.CHAIN_REACTION) > 0 && !boss && e.type != EnemyType.SEGMENT && e.type != EnemyType.ORB) {
                explode(e.x, e.y, 65 + e.r, Math.max(2, bulletDamage));
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
            announce(t("hud.bossDefeated", "BOSS DEFEATED!"), e.type.color);
            runBosses++;
            totalBosses++;
            bossKills[Arrays.asList(BOSSES).indexOf(e.type)]++;
            unlock(Achievement.FLAWLESS, !hitThisWave);
            unlock(Achievement.NO_MERCY, difficulty == Difficulty.HARD);
            if (lvl(Upgrade.PHOENIX) > 0 && health < maxHealth) {
                health = maxHealth;
                addText(px, py - 26, t("hud.phoenix", "PHOENIX: FULL HEALTH"), new Color(255, 160, 60), 20);
            }
            hazards.clear();
            addPickup(PickupType.HEART, e.x - 30, e.y);
            addPickup(PickupType.BOMB, e.x + 30, e.y);
            int coinCount = (int) (20 * (1 + 0.5 * perk(Perk.BOUNTY_BOARD)));
            for (int i = 0; i < coinCount; i++) {
                addCoin(e.x, e.y);
            }
            // Every boss drops 2 diamonds. They get pulled in when the wave clears, so none are lost.
            addPickup(PickupType.DIAMOND, e.x - 14, e.y + 30);
            addPickup(PickupType.DIAMOND, e.x + 14, e.y + 30);
            clearEnemyBullets();
            for (Enemy other : enemies) {
                if ((other.owner == e || other.host == e) && !other.dead) {
                    other.dead = true;
                    burst(other.x, other.y, 24, other.type.color, 360);
                    addRing(other.x, other.y, 70, 0.3, other.type.color);
                }
            }
            for (Enemy other : newEnemies) {
                if (other.owner == e || other.host == e) {
                    other.dead = true;
                }
            }
        }

        private void dropLoot(Enemy e) {
            if (e.type == EnemyType.ORB) {
                return;
            }
            if (e.elite) {
                int count = (int) (5 * (1 + 0.5 * perk(Perk.BOUNTY_BOARD)));
                for (int i = 0; i < count; i++) {
                    addCoin(e.x, e.y);
                }
            } else if (rng.nextDouble() < 0.14) {
                addCoin(e.x, e.y);
            }
            double roll = rng.nextDouble();
            double luck = 1 + 0.2 * perk(Perk.SCAVENGER) + 0.25 * lvl(Upgrade.LUCKY);
            if (roll < 0.035 * luck) {
                addPickup(PickupType.HEART, e.x, e.y);
            } else if (roll < 0.05 * luck) {
                addPickup(PickupType.BOMB, e.x, e.y);
            }
            // Diamonds are rare: 1 in 20 elites and 1 in 250 normal enemies drop one.
            if (e.type != EnemyType.SEGMENT && rng.nextDouble() < (e.elite ? 0.05 : 0.004) * (1 + 0.25 * perk(Perk.PROSPECTOR))) {
                addPickup(PickupType.DIAMOND, e.x, e.y);
            }
        }

        private void announceCombo() {
            if (combo == 15) {
                announce(t("combo.15", "KILLING SPREE!"), new Color(120, 240, 255));
            } else if (combo == 35) {
                announce(t("combo.35", "RAMPAGE!"), new Color(255, 200, 80));
            } else if (combo == 60) {
                announce(t("combo.60", "UNSTOPPABLE!"), new Color(255, 120, 90));
            } else if (combo == 100) {
                announce(t("combo.100", "GODLIKE!"), new Color(255, 90, 220));
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
                if (e.burnTimer > 0) {
                    e.burnTimer -= dt;
                    e.burnTick -= dt;
                    if (fx.nextDouble() < 0.3) {
                        addParticle(e.x, e.y, (fx.nextDouble() - 0.5) * 40, -50, 0.35, 4, new Color(255, 150, 50));
                    }
                    if (e.burnTick <= 0) {
                        e.burnTick = 0.4;
                        damage(e, e.burnDamage * 0.4, false);
                        if (e.dead) {
                            continue;
                        }
                    }
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

                if (Math.hypot(e.x - px, e.y - py) < e.r + PLAYER_RADIUS && e.blinkWarn <= 0 && !e.phased) {
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
                if (texts.size() <= 28 && damageNumbers) {
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
                case RAMMER:
                    moveRammer(e, dt);
                    break;
                case TURRET:
                    moveTurret(e, dt);
                    break;
                case LAYER:
                    moveLayer(e, dt);
                    break;
                case SNIPER:
                    moveSniper(e, dt);
                    break;
                case MEDIC:
                    moveMedic(e, dt);
                    break;
                case BULWARK:
                    moveBulwark(e, dt);
                    break;
                case HIVE:
                    moveHive(e, dt);
                    break;
                case WRAITH:
                    moveWraith(e, dt);
                    break;
                case QUEEN:
                    moveQueen(e, dt);
                    break;
                case REAPER:
                    moveReaper(e, dt);
                    break;
                case COLOSSUS:
                    moveColossus(e, dt);
                    break;
                case SINGULARITY:
                    moveSingularity(e, dt);
                    break;
                case SWARMER:
                    moveSwarmer(e, dt);
                    break;
                case PULSAR:
                    movePulsar(e, dt);
                    break;
                case MORTAR:
                    moveMortar(e, dt);
                    break;
                case SPINNER:
                    moveSpinner(e, dt);
                    break;
                case TEMPEST:
                    moveTempest(e, dt);
                    break;
                case HYDRA:
                    moveHydra(e, dt);
                    break;
                case HEAD:
                    moveHead(e, dt);
                    break;
                case ECLIPSE:
                    moveEclipse(e, dt);
                    break;
                case ORB:
                    moveOrb(e);
                    break;
                default:
                    moveChaser(e, dt);
                    break;
            }
        }

        // ---------------------------- newer enemies ---------------------------

        /** Keeps a distance from the player while slowly circling around them. */
        private void keepRange(Enemy e, double dt, double near, double far, double circle) {
            double dx = px - e.x, dy = py - e.y, d = Math.max(1, Math.hypot(dx, dy));
            double direction = d > far ? 1 : d < near ? -1 : 0;
            e.x += dx / d * direction * e.type.speed * dt - dy / d * circle * dt;
            e.y += dy / d * direction * e.type.speed * dt + dx / d * circle * dt;
        }

        private double angleToPlayer(Enemy e) {
            return Math.atan2(py - e.y, px - e.x);
        }

        /** Stops, takes aim for a moment, then charges in a straight line. */
        private void moveRammer(Enemy e, double dt) {
            if (e.chargeWarn > 0) {
                e.chargeWarn -= dt;
                e.angle = Math.atan2(e.aimY - e.y, e.aimX - e.x);
                if (e.chargeWarn <= 0) {
                    e.chargeTime = 0.6;
                    e.vx = Math.cos(e.angle) * 520;
                    e.vy = Math.sin(e.angle) * 520;
                    sound.play(Sound.Effect.DASH);
                }
                return;
            }
            if (e.chargeTime > 0) {
                e.chargeTime -= dt;
                e.x += e.vx * dt;
                e.y += e.vy * dt;
                e.angle = Math.atan2(e.vy, e.vx);
                addParticle(e.x, e.y, 0, 0, 0.2, 7, e.type.color);
                if (e.chargeTime <= 0) {
                    e.vx = 0;
                    e.vy = 0;
                    e.attackTimer = 1.3 + rng.nextDouble();
                }
                return;
            }
            moveChaser(e, dt);
            e.angle = angleToPlayer(e);
            e.attackTimer -= dt;
            if (e.attackTimer <= 0 && Math.hypot(px - e.x, py - e.y) < 340) {
                e.chargeWarn = 0.6;
                e.aimX = px;
                e.aimY = py;
            }
        }

        /** Does not move, but keeps turning and firing in four directions. */
        private void moveTurret(Enemy e, double dt) {
            e.angle -= dt * 1.7;
            e.attackTimer -= dt;
            if (e.attackTimer <= 0) {
                e.attackTimer = 1.5;
                for (int i = 0; i < 4; i++) {
                    fireEnemyBullet(e.x, e.y, e.angle + i * Math.PI / 2, 170, e.type.color);
                }
            }
        }

        /** Circles the player and leaves mines behind. */
        private void moveLayer(Enemy e, double dt) {
            keepRange(e, dt, 170, 260, 70);
            e.attackTimer -= dt;
            if (e.attackTimer <= 0) {
                e.attackTimer = 2.4;
                dropMine(e.x, e.y, e.type.color);
            }
        }

        private void dropMine(double x, double y, Color color) {
            Bullet mine = fireEnemyBullet(clamp(x, 16, W - 16), clamp(y, 16, H - 16), 0, 0, color);
            mine.r = 9;
            mine.life = 7;
        }

        /** Tracks the player with a laser sight, holds still for a moment, then fires a very fast shot. */
        private void moveSniper(Enemy e, double dt) {
            if (e.chargeWarn > 0) {
                e.chargeWarn -= dt;
                if (e.chargeWarn > 0.28) {
                    e.aimX = angleToPlayer(e);
                }
                e.angle = e.aimX;
                if (e.chargeWarn <= 0) {
                    fireEnemyBullet(e.x, e.y, e.aimX, 540, e.type.color);
                    e.attackTimer = Math.max(2.2, 3.4 - wave * 0.03);
                    sound.play(Sound.Effect.ZAP);
                }
                return;
            }
            keepRange(e, dt, 300, 400, 25);
            e.angle = angleToPlayer(e);
            e.attackTimer -= dt;
            if (e.attackTimer <= 0) {
                e.chargeWarn = 1.0;
                e.aimX = e.angle;
            }
        }

        /** Stays back and repairs the enemies around it. Worth shooting first. */
        private void moveMedic(Enemy e, double dt) {
            keepRange(e, dt, 230, 320, 40);
            e.attackTimer -= dt;
            if (e.attackTimer > 0) {
                return;
            }
            e.attackTimer = 2.5;
            boolean healed = false;
            for (Enemy other : enemies) {
                if (other != e && !other.dead && !other.type.isBoss() && other.owner == null && other.hp < other.maxHp
                        && Math.hypot(other.x - e.x, other.y - e.y) < 170 + other.r) {
                    other.hp = Math.min(other.maxHp, other.hp + other.maxHp * 0.15);
                    addParticle(other.x, other.y, 0, -60, 0.45, 6, e.type.color);
                    healed = true;
                }
            }
            if (healed) {
                addRing(e.x, e.y, 170, 0.4, e.type.color);
            }
        }

        /** Walks slowly behind a shield that turns toward the player, a bit slower than you can circle it. */
        private void moveBulwark(Enemy e, double dt) {
            double wanted = angleToPlayer(e);
            double diff = Math.atan2(Math.sin(wanted - e.aimX), Math.cos(wanted - e.aimX));
            e.aimX += clamp(diff, -1.3 * dt, 1.3 * dt);
            moveChaser(e, dt);
        }

        private void moveHive(Enemy e, double dt) {
            moveChaser(e, dt);
            e.attackTimer -= dt;
            if (e.attackTimer <= 0) {
                e.attackTimer = 3.5;
                summon(e, EnemyType.MINI, 2);
            }
        }

        /** Fades out of reality for a while, drifting closer. It cannot hit or be hit in that state. */
        private void moveWraith(Enemy e, double dt) {
            moveChaser(e, e.phased ? dt * 1.5 : dt);
            e.attackTimer -= dt;
            if (e.attackTimer <= 0) {
                e.phased = !e.phased;
                e.attackTimer = e.phased ? 1.4 : 2.2;
                burst(e.x, e.y, 8, e.type.color, 120);
            }
        }

        private void fan(Enemy e, int count, double spread, double speed) {
            double center = angleToPlayer(e);
            for (int i = 0; i < count; i++) {
                fireEnemyBullet(e.x, e.y, center + (i - (count - 1) / 2.0) * spread, speed, e.type.color);
            }
            sound.play(Sound.Effect.BOOM);
        }

        /** Spawns her brood, sprays acid and seeds the arena with mines. */
        private void moveQueen(Enemy e, double dt) {
            double rage = e.hp < e.maxHp / 2 ? 1.4 : 1;
            if (e.spiralTime > 0) {
                e.spiralTime -= dt;
                e.attackTimer -= dt;
                if (e.attackTimer <= 0) {
                    e.attackTimer = 0.08;
                    fireEnemyBullet(e.x, e.y, rng.nextDouble() * Math.PI * 2, 150 + rng.nextDouble() * 80, e.type.color);
                }
                if (e.spiralTime <= 0) {
                    e.attackTimer = 1.5 / rage;
                }
                return;
            }
            double targetX = W / 2.0 + Math.cos(clock * 0.6) * 300, targetY = H / 2.0 + Math.sin(clock * 0.9) * 190;
            double dx = targetX - e.x, dy = targetY - e.y, d = Math.max(1, Math.hypot(dx, dy));
            e.x += dx / d * e.type.speed * rage * dt;
            e.y += dy / d * e.type.speed * rage * dt;
            e.attackTimer -= dt;
            if (e.attackTimer > 0) {
                return;
            }
            e.attackTimer = 2.1 / rage;
            switch (e.bossStep++ % 4) {
                case 0:
                    fan(e, rage > 1 ? 9 : 7, 0.16, 230);
                    break;
                case 1:
                    summon(e, EnemyType.MINI, 6);
                    break;
                case 2:
                    for (int i = 0; i < 8; i++) {
                        double angle = i * Math.PI / 4 + rng.nextDouble() * 0.3, far = 130 + rng.nextDouble() * 130;
                        dropMine(e.x + Math.cos(angle) * far, e.y + Math.sin(angle) * far, e.type.color);
                    }
                    addRing(e.x, e.y, 240, 0.4, e.type.color);
                    break;
                default:
                    e.spiralTime = 2;
                    e.attackTimer = 0;
                    break;
            }
        }

        /** Vanishes and reappears next to you, swings a scythe of bullets, and calls up wraiths. */
        private void moveReaper(Enemy e, double dt) {
            double rage = e.hp < e.maxHp / 2 ? 1.35 : 1;
            if (e.blinkWarn > 0) {
                e.blinkWarn -= dt;
                if (e.blinkWarn <= 0) {
                    e.phased = false;
                    burst(e.x, e.y, 30, e.type.color, 300);
                    e.x = e.warpX;
                    e.y = e.warpY;
                    radialBurst(e, 14, 190);
                }
                return;
            }
            if (e.chargeWarn > 0) {
                e.chargeWarn -= dt;
                if (e.chargeWarn <= 0) {
                    e.chargeTime = 0.45;
                    double d = Math.max(1, Math.hypot(e.aimX - e.x, e.aimY - e.y));
                    e.vx = (e.aimX - e.x) / d * 800;
                    e.vy = (e.aimY - e.y) / d * 800;
                    sound.play(Sound.Effect.DASH);
                }
                return;
            }
            if (e.chargeTime > 0) {
                e.chargeTime -= dt;
                e.x += e.vx * dt;
                e.y += e.vy * dt;
                addParticle(e.x, e.y, 0, 0, 0.3, 18, e.type.color);
                return;
            }
            if (e.spiralTime > 0) {
                e.spiralTime -= dt;
                e.attackTimer -= dt;
                if (e.attackTimer <= 0) {
                    e.attackTimer = 0.06;
                    e.aimX += 0.22;
                    fireEnemyBullet(e.x, e.y, e.aimX, 230, e.type.color);
                    fireEnemyBullet(e.x, e.y, e.aimX + Math.PI, 230, e.type.color);
                }
                if (e.spiralTime <= 0) {
                    e.attackTimer = 1.3 / rage;
                }
                return;
            }
            moveChaser(e, dt);
            e.attackTimer -= dt;
            if (e.attackTimer > 0) {
                return;
            }
            e.attackTimer = 1.9 / rage;
            switch (e.bossStep++ % 4) {
                case 0:
                    double angle = rng.nextDouble() * Math.PI * 2;
                    e.warpX = clamp(px + Math.cos(angle) * 210, e.r, W - e.r);
                    e.warpY = clamp(py + Math.sin(angle) * 210, e.r, H - e.r);
                    e.blinkWarn = 0.75;
                    e.phased = true;
                    break;
                case 1:
                    e.spiralTime = 1.7;
                    e.attackTimer = 0;
                    e.aimX = angleToPlayer(e);
                    break;
                case 2:
                    e.chargeWarn = 0.65;
                    e.aimX = px;
                    e.aimY = py;
                    break;
                default:
                    summon(e, EnemyType.WRAITH, 3);
                    break;
            }
        }

        /** Huge and slow. Slams marked ground, sends out rings with one gap, and deploys turrets. */
        private void moveColossus(Enemy e, double dt) {
            double rage = e.hp < e.maxHp / 2 ? 1.3 : 1;
            if (e.slamWarn > 0) {
                e.slamWarn -= dt;
                if (e.slamWarn <= 0) {
                    addRing(e.warpX, e.warpY, 150, 0.35, e.type.color);
                    addFlash(e.warpX, e.warpY, 130, 0.2, new Color(255, 210, 160));
                    burst(e.warpX, e.warpY, 40, e.type.color, 380);
                    shake = Math.max(shake, 22);
                    sound.play(Sound.Effect.BIG_BOOM);
                    if (Math.hypot(px - e.warpX, py - e.warpY) < 140 && canBeHurt()) {
                        hurtPlayer();
                    }
                    for (int i = 0; i < 10; i++) {
                        fireEnemyBullet(e.warpX, e.warpY, i * Math.PI / 5, 170, e.type.color);
                    }
                }
            }
            if (e.spiralTime > 0) {
                e.spiralTime -= dt;
                e.attackTimer -= dt;
                if (e.attackTimer <= 0) {
                    e.attackTimer = 0.24;
                    fan(e, 3, 0.2, 280);
                }
                if (e.spiralTime <= 0) {
                    e.attackTimer = 1.5 / rage;
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
            e.attackTimer = 2.2 / rage;
            switch (e.bossStep++ % 4) {
                case 0:
                    e.slamWarn = 1.0;
                    e.warpX = px;
                    e.warpY = py;
                    break;
                case 1:
                    int count = 30, gap = rng.nextInt(count);
                    for (int i = 0; i < count; i++) {
                        if ((i - gap + count) % count > 4) {
                            fireEnemyBullet(e.x, e.y, i * Math.PI * 2 / count, 150, e.type.color);
                        }
                    }
                    sound.play(Sound.Effect.BOOM);
                    break;
                case 2:
                    summon(e, EnemyType.TURRET, 2);
                    break;
                default:
                    e.spiralTime = 1.5;
                    e.attackTimer = 0;
                    break;
            }
        }

        /** A black hole. It drags you in all the time, and hard during its pull attack. */
        private void moveSingularity(Enemy e, double dt) {
            double rage = e.hp < e.maxHp / 2 ? 1.3 : 1;
            e.pullTime = Math.max(0, e.pullTime - dt);
            if (dashTimer <= 0 && state == State.PLAYING) {
                double dx = e.x - px, dy = e.y - py, d = Math.max(1, Math.hypot(dx, dy));
                double pull = (e.pullTime > 0 ? 165 : 50) * rage;
                px = clamp(px + dx / d * pull * dt, 22, W - 22);
                py = clamp(py + dy / d * pull * dt, 22, H - 22);
            }
            if (e.spiralTime > 0) {
                e.spiralTime -= dt;
                e.attackTimer -= dt;
                if (e.attackTimer <= 0) {
                    e.attackTimer = 0.11;
                    e.aimX += 0.31;
                    for (int i = 0; i < 4; i++) {
                        fireEnemyBullet(e.x, e.y, e.aimX + i * Math.PI / 2, 175, e.type.color);
                    }
                }
                if (e.spiralTime <= 0) {
                    e.attackTimer = 1.6 / rage;
                }
                return;
            }
            double wanderX = W / 2.0 + Math.sin(clock * 0.4) * 220, wanderY = H / 2.0 + Math.cos(clock * 0.53) * 130;
            double dx = wanderX - e.x, dy = wanderY - e.y, d = Math.max(1, Math.hypot(dx, dy));
            e.x += dx / d * e.type.speed * dt;
            e.y += dy / d * e.type.speed * dt;
            e.attackTimer -= dt;
            if (e.attackTimer > 0) {
                return;
            }
            e.attackTimer = 2.2 / rage;
            switch (e.bossStep++ % 4) {
                case 0:
                    e.spiralTime = 3;
                    e.attackTimer = 0;
                    e.aimX = rng.nextDouble() * Math.PI * 2;
                    break;
                case 1:
                    e.pullTime = 2.6;
                    addRing(e.x, e.y, 420, 0.8, e.type.color);
                    for (int i = 0; i < 18; i++) {
                        double angle = i * Math.PI / 9;
                        double x = e.x + Math.cos(angle) * 400, y = e.y + Math.sin(angle) * 400;
                        if (x > 12 && x < W - 12 && y > 12 && y < H - 12) {
                            fireEnemyBullet(x, y, angle + Math.PI, 125, e.type.color).life = 3.2;
                        }
                    }
                    sound.play(Sound.Effect.SPECIAL);
                    break;
                case 2:
                    summon(e, EnemyType.GHOST, 3);
                    break;
                default:
                    radialBurst(e, 22, 120);
                    radialBurst(e, 22, 205);
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
            if (beamsBusy(e, dt, rage)) {
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

        /**
         * Laser beams used by the Warden and the Eclipse: they flicker as a warning, then sweep
         * around for four seconds. Returns true while the beams keep the boss busy.
         */
        private boolean beamsBusy(Enemy e, double dt, double rage) {
            if (e.beamWarn > 0) {
                e.beamWarn -= dt;
                e.beamAngle += e.beamSpin * 0.25 * dt;
                if (e.beamWarn <= 0) {
                    e.beamTime = 4;
                    shake = Math.max(shake, 8);
                    sound.play(Sound.Effect.SPECIAL);
                }
                return true;
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
                return true;
            }
            return false;
        }

        // ------------------------------ newest enemies ------------------------------

        /** Small and quick. Swarmers come in packs of four and weave around each other. */
        private void moveSwarmer(Enemy e, double dt) {
            moveChaser(e, dt);
            e.angle = Math.atan2(e.vy, e.vx);
        }

        /** Creeps closer, then stops and charges a shockwave. Get out of the ring before it fires. */
        private void movePulsar(Enemy e, double dt) {
            if (e.chargeWarn > 0) {
                e.chargeWarn -= dt;
                if (e.chargeWarn <= 0) {
                    e.attackTimer = 2.6 + rng.nextDouble();
                }
                return;
            }
            keepRange(e, dt, 90, 150, 30);
            e.attackTimer -= dt;
            if (e.attackTimer <= 0 && Math.hypot(px - e.x, py - e.y) < 260) {
                e.chargeWarn = 1.1;
                addHazard(e.x, e.y, 135, 1.1, e.type.color);
            }
        }

        /** Stays far back and shells the spot where you are standing. Keep moving! */
        private void moveMortar(Enemy e, double dt) {
            keepRange(e, dt, 320, 440, 20);
            e.angle = angleToPlayer(e);
            e.attackTimer -= dt;
            if (e.attackTimer <= 0) {
                e.attackTimer = Math.max(2.2, 3.4 - wave * 0.03);
                addHazard(px, py, 58, 1.25, e.type.color);
                burst(e.x, e.y, 6, e.type.color, 120);
                sound.play(Sound.Effect.DASH);
            }
        }

        /** A spinning blade that bounces around the arena in straight lines. */
        private void moveSpinner(Enemy e, double dt) {
            if (e.vx == 0 && e.vy == 0) {
                double angle = angleToPlayer(e) + (rng.nextBoolean() ? 0.6 : -0.6);
                e.vx = Math.cos(angle) * e.type.speed;
                e.vy = Math.sin(angle) * e.type.speed;
            }
            e.x += e.vx * dt;
            e.y += e.vy * dt;
            if ((e.x <= e.r && e.vx < 0) || (e.x >= W - e.r && e.vx > 0)) {
                e.vx = -e.vx;
            }
            if ((e.y <= e.r && e.vy < 0) || (e.y >= H - e.r && e.vy > 0)) {
                e.vy = -e.vy;
            }
            e.angle += dt * 10;
        }

        /** A storm boss. Races around the arena in a figure eight and calls down lightning. */
        private void moveTempest(Enemy e, double dt) {
            double rage = e.hp < e.maxHp / 2 ? 1.35 : 1;
            double targetX = W / 2.0 + Math.sin(clock * 0.8) * 340, targetY = H / 2.0 + Math.sin(clock * 1.6) * 200;
            double dx = targetX - e.x, dy = targetY - e.y, d = Math.max(1, Math.hypot(dx, dy));
            double step = Math.min(d, e.type.speed * rage * dt);
            e.x += dx / d * step;
            e.y += dy / d * step;
            if (fx.nextDouble() < 0.4) {
                addParticle(e.x, e.y, (fx.nextDouble() - 0.5) * 80, (fx.nextDouble() - 0.5) * 80, 0.3, 5, e.type.color);
            }
            e.attackTimer -= dt;
            if (e.spiralTime > 0) {
                e.spiralTime -= dt;
                if (e.attackTimer <= 0) {
                    e.attackTimer = 0.28;
                    fan(e, 5, 0.17, 290);
                }
                if (e.spiralTime <= 0) {
                    e.attackTimer = 1.4 / rage;
                }
                return;
            }
            if (e.attackTimer > 0) {
                return;
            }
            e.attackTimer = 2.0 / rage;
            switch (e.bossStep++ % 4) {
                case 0:
                    int strikes = rage > 1 ? 7 : 5;
                    for (int i = 0; i < strikes; i++) {
                        double spread = i == 0 ? 0 : 90 + rng.nextDouble() * 150;
                        double angle = rng.nextDouble() * Math.PI * 2;
                        addHazard(px + Math.cos(angle) * spread, py + Math.sin(angle) * spread, 60, 1.0 + i * 0.16, e.type.color);
                    }
                    sound.play(Sound.Effect.ZAP);
                    break;
                case 1:
                    e.spiralTime = 1.4;
                    e.attackTimer = 0;
                    break;
                case 2:
                    summon(e, EnemyType.SPEEDER, 4);
                    break;
                default:
                    radialBurst(e, 24, 230);
                    break;
            }
        }

        /** Grows a new pair of heads at two thirds and at one third of its health. */
        private void moveHydra(Enemy e, double dt) {
            double rage = e.hp < e.maxHp / 2 ? 1.3 : 1;
            int wanted = e.hp < e.maxHp / 3 ? 2 : e.hp < e.maxHp * 2 / 3 ? 1 : 0;
            while (e.phase < wanted) {
                e.phase++;
                growHeads(e);
            }
            if (e.chargeWarn > 0) {
                e.chargeWarn -= dt;
                if (e.chargeWarn <= 0) {
                    e.chargeTime = 0.5;
                    double d = Math.max(1, Math.hypot(e.aimX - e.x, e.aimY - e.y));
                    e.vx = (e.aimX - e.x) / d * 640;
                    e.vy = (e.aimY - e.y) / d * 640;
                    shake = Math.max(shake, 8);
                    sound.play(Sound.Effect.DASH);
                }
                return;
            }
            if (e.chargeTime > 0) {
                e.chargeTime -= dt;
                e.x += e.vx * dt;
                e.y += e.vy * dt;
                addParticle(e.x, e.y, 0, 0, 0.3, 22, e.type.color);
                return;
            }
            double dx = px - e.x, dy = py - e.y, d = Math.max(1, Math.hypot(dx, dy));
            e.x += dx / d * e.type.speed * rage * dt;
            e.y += dy / d * e.type.speed * rage * dt;
            e.attackTimer -= dt;
            if (e.attackTimer > 0) {
                return;
            }
            e.attackTimer = 2.2 / rage;
            switch (e.bossStep++ % 4) {
                case 0:
                    fan(e, 7, 0.14, 210);
                    break;
                case 1:
                    e.chargeWarn = 0.8;
                    e.aimX = px;
                    e.aimY = py;
                    break;
                case 2:
                    radialBurst(e, 18, 160);
                    break;
                default:
                    summon(e, EnemyType.SPORE, 3);
                    break;
            }
        }

        private void growHeads(Enemy e) {
            for (int i = 0; i < 2; i++) {
                Enemy head = new Enemy(EnemyType.HEAD, e.x, e.y, EnemyType.HEAD.hp * healthScale());
                head.host = e;
                // The first pair sits on opposite sides; the second pair fills the gaps between them.
                head.phase = (e.phase - 1) + i * 2;
                head.spawnTotal = 0.5;
                head.spawnTimer = 0.5;
                head.attackTimer = 1 + i * 0.6;
                newEnemies.add(head);
            }
            addRing(e.x, e.y, 200, 0.4, e.type.color);
            announce(t("hud.hydraHeads", "THE HYDRA GROWS NEW HEADS!"), e.type.color);
            sound.play(Sound.Effect.BOSS);
        }

        /** Hydra heads circle their body and spit at the player. */
        private void moveHead(Enemy e, double dt) {
            if (e.host == null || e.host.dead) {
                e.dead = true;
                return;
            }
            double angle = clock * 0.9 + e.phase * Math.PI / 2;
            e.x = e.host.x + Math.cos(angle) * (e.host.r + 44);
            e.y = e.host.y + Math.sin(angle) * (e.host.r + 44);
            e.angle = angleToPlayer(e);
            e.attackTimer -= dt;
            if (e.attackTimer <= 0) {
                e.attackTimer = 1.9;
                fireEnemyBullet(e.x, e.y, e.angle, 220, e.type.color);
            }
        }

        /** Hides behind four shield orbs, sweeps beams of light and fires bright spirals. */
        private void moveEclipse(Enemy e, double dt) {
            double rage = e.hp < e.maxHp / 2 ? 1.3 : 1;
            if (beamsBusy(e, dt, rage)) {
                return;
            }
            if (e.spiralTime > 0) {
                e.spiralTime -= dt;
                e.attackTimer -= dt;
                if (e.attackTimer <= 0) {
                    e.attackTimer = 0.09;
                    e.aimX += 0.27;
                    fireEnemyBullet(e.x, e.y, e.aimX, 190, e.type.color);
                    fireEnemyBullet(e.x, e.y, e.aimX + Math.PI, 190, e.type.color);
                }
                if (e.spiralTime <= 0) {
                    e.attackTimer = 1.5 / rage;
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
            e.attackTimer = 2.1 / rage;
            switch (e.bossStep++ % 4) {
                case 0:
                    e.beamCount = rage > 1 ? 4 : 3;
                    e.beamAngle = rng.nextDouble() * Math.PI * 2;
                    e.beamSpin = (rng.nextBoolean() ? 1 : -1) * 0.6 * rage;
                    e.beamWarn = 1.2;
                    break;
                case 1:
                    e.spiralTime = 2.4;
                    e.attackTimer = 0;
                    e.aimX = rng.nextDouble() * Math.PI * 2;
                    break;
                case 2:
                    summon(e, EnemyType.WRAITH, 2);
                    break;
                default:
                    radialBurst(e, 28, 140);
                    radialBurst(e, 28, 215);
                    break;
            }
        }

        /** Shield orbs circle the Eclipse. They cannot be destroyed, only avoided. */
        private void moveOrb(Enemy e) {
            if (e.host == null || e.host.dead) {
                e.dead = true;
                return;
            }
            double angle = clock * 1.3 + e.phase * Math.PI / 2;
            e.x = e.host.x + Math.cos(angle) * (e.host.r + 46);
            e.y = e.host.y + Math.sin(angle) * (e.host.r + 46);
        }

        /** Each beam shoots out from the boss in one direction, so there are safe gaps. */
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
            if (e.type.isBoss() || e.type.isAnchored()) {
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

        private Bullet fireEnemyBullet(double x, double y, double angle, double speed, Color color) {
            Bullet b = new Bullet();
            b.x = x;
            b.y = y;
            double scale = modifier.bulletSpeed * (lvl(Upgrade.BULLET_TIME) > 0 ? 0.75 : 1);
            b.vx = Math.cos(angle) * speed * scale;
            b.vy = Math.sin(angle) * speed * scale;
            b.r = 6.5;
            b.life = 6;
            b.enemy = true;
            b.color = color;
            newBullets.add(b);
            return b;
        }

        boolean canBeHurt() {
            return invulnerable <= 0 && dashTimer <= 0 && fortressTimer <= 0 && state == State.PLAYING;
        }

        private void hurtPlayer() {
            if (shields > 0) {
                // A shield takes the whole hit: no lost health, and the combo survives.
                shields--;
                invulnerable = 1;
                shake = Math.max(shake, 10);
                addRing(px, py, 70, 0.3, SHIELD_COLOR);
                burst(px, py, 18, SHIELD_COLOR, 240);
                addText(px, py - 24, t("hud.blocked", "BLOCKED"), SHIELD_COLOR, 17);
                sound.play(Sound.Effect.ZAP);
                return;
            }
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
            if (lvl(Upgrade.ADRENALINE) > 0) {
                overdriveTimer = Math.max(overdriveTimer, 2 * lvl(Upgrade.ADRENALINE));
            }
            if (lvl(Upgrade.THORNS) > 0) {
                explode(px, py, 130, Math.max(2, bulletDamage) * (1 + 1.5 * lvl(Upgrade.THORNS)));
            }
            if (health <= 0) {
                if (secondLives > 0) {
                    secondLives--;
                    health = Math.min(3, maxHealth);
                    invulnerable = 3;
                    announce(t("hud.secondLifeUsed", "SECOND LIFE!"), new Color(255, 190, 60));
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
                double range = (p.type == PickupType.COIN || p.type == PickupType.DIAMOND ? 170 : 130)
                        + 30 * perk(Perk.TRACTOR) + 50 * lvl(Upgrade.MAGNET);
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
                        addText(px, py - 22, t("hud.plusHealth", "+1 HEALTH"), new Color(255, 120, 140), 18);
                    } else {
                        score += 100;
                        addText(px, py - 22, "+100", new Color(255, 120, 140), 18);
                    }
                    sound.play(Sound.Effect.PICKUP);
                    break;
                case BOMB:
                    bombs++;
                    addText(px, py - 22, t("hud.plusBomb", "+1 BOMB"), new Color(255, 200, 120), 18);
                    sound.play(Sound.Effect.PICKUP);
                    break;
                case DIAMOND:
                    runDiamonds++;
                    addText(px, py - 22, t("hud.plusDiamond", "+1 DIAMOND"), DIAMOND_COLOR, 20);
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
            coinFraction += amount * difficulty.coinMultiplier * (1 + 0.25 * perks[Perk.GREED.ordinal()])
                    * (1 + 0.3 * lvl(Upgrade.BOUNTY));
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
                banner = t("hud.waveCleared", "WAVE CLEARED!");
                bossBanner = false;
                bannerTimer = 1.1;
                hazards.clear();
                if (lvl(Upgrade.NANOBOTS) > 0 && health < maxHealth) {
                    health = Math.min(maxHealth, health + lvl(Upgrade.NANOBOTS));
                    addText(px, py - 22, t("hud.repaired", "REPAIRED"), new Color(255, 120, 140), 18);
                }
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
            int scanner = upgrade.rarity == Rarity.COMMON ? 0
                    : perk(Perk.SCANNER) * (upgrade.rarity == Rarity.LEGENDARY ? 2 : 6);
            return upgrade.rarity.weight + scanner + bonus * (upgrade.rarity == Rarity.LEGENDARY ? 1 : 2);
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
                showNotice(t("notice.needCoins", "You need {0} more coins for {1}", cardPrice(index) - runCoins, label(choices.get(index))));
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
                case SHIELD_CELL:
                    shields++;
                    break;
                case OVERCHARGE:
                    bulletDamage *= 1.1;
                    fireDelay *= 0.9;
                    break;
                case LUCKY:
                    rerolls++;
                    break;
                default:
                    // The other upgrades are read from their level while playing.
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
            boolean overGame = state == State.SETTINGS && settingsReturn == State.PAUSED;
            boolean inGame = overGame || (state != State.MENU && state != State.HANGAR && state != State.RECORDS
                    && state != State.SKINS && state != State.WORKSHOP && state != State.SETTINGS);
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
            } else if (state == State.WORKSHOP) {
                drawWorkshop(g);
            } else if (state == State.SETTINGS) {
                if (overGame) {
                    drawWorld(g);
                    dim(g, 0.86f);
                }
                drawSettings(g);
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
            drawHazards(g);
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
                    g.setColor(bossBanner ? new Color(255, 90, 170) : Color.WHITE);
                    fitCentered(g, banner, 190, 58, W - 80);
                    if (bossBanner && (int) (clock * 6) % 2 == 0) {
                        g.setColor(new Color(255, 70, 80));
                        centered(g, t("hud.warning", "WARNING"), 132, 28, true);
                    }
                    alpha(g, 1);
                }
            } else if (state == State.UPGRADE) {
                drawUpgradeScreen(g);
            } else if (state == State.PAUSED) {
                drawPause(g);
            } else if (state == State.GAME_OVER) {
                drawGameOver(g);
            }
        }

        private void drawPause(Graphics2D g) {
            dim(g, 0.68f);
            g.setColor(Color.WHITE);
            centered(g, t("pause.title", "PAUSED"), 290, 56, true);
            g.setColor(new Color(190, 198, 235));
            fitCentered(g, t("pause.resume", "Press {0} or click to keep playing", keyLabel(Action.PAUSE)), 340, 20, W - 80);
            fitCentered(g, t("pause.quit", "Press X to end this run and keep your coins"), 372, 20, W - 80);
            g.setColor(new Color(150, 160, 200));
            fitCentered(g, t("pause.saved", "Closing the game saves the run. Continue it from the start of this wave."), 404, 16, W - 80);
            drawButton(g, pauseSettingsButton(), t("pause.settings", "SETTINGS  [O]"), new Color(170, 200, 255), false);
        }

        /** A rounded button with a colored outline that lights up under the mouse. */
        private void drawButton(Graphics2D g, Rectangle box, String text, Color color, boolean selected) {
            boolean hover = box.contains(mouseX, mouseY);
            g.setColor(selected ? new Color(40, 48, 100) : hover ? new Color(36, 44, 90) : new Color(20, 22, 48));
            g.fillRoundRect(box.x, box.y, box.width, box.height, 16, 16);
            g.setColor(color);
            g.setStroke(new BasicStroke(hover || selected ? 3 : 1.5f));
            g.drawRoundRect(box.x, box.y, box.width, box.height, 16, 16);
            g.setStroke(new BasicStroke(1));
            g.setColor(selected ? color : Color.WHITE);
            fitFont(g, text, box.width - 20, 18, true);
            centeredIn(g, text, box, box.y + box.height / 2 + g.getFontMetrics().getAscent() / 2 - 2);
        }

        private void drawHazards(Graphics2D g) {
            for (Hazard h : hazards) {
                double t = 1 - h.warn / Math.max(0.01, h.total);
                int r = (int) h.radius;
                alpha(g, 0.12f + 0.18f * (float) t);
                g.setColor(h.color);
                g.fillOval((int) h.x - r, (int) h.y - r, r * 2, r * 2);
                int inner = (int) (h.radius * t);
                alpha(g, 0.35f + 0.3f * (float) t);
                g.fillOval((int) h.x - inner, (int) h.y - inner, inner * 2, inner * 2);
                alpha(g, 0.6f + 0.4f * (float) Math.abs(Math.sin(clock * 14)));
                g.setStroke(new BasicStroke(2.5f));
                g.drawOval((int) h.x - r, (int) h.y - r, r * 2, r * 2);
                g.setStroke(new BasicStroke(1));
            }
            alpha(g, 1);
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

        /** Picks the biggest font size up to size that fits the text in maxWidth. Some languages are long. */
        private void fitFont(Graphics2D g, String text, int maxWidth, int size, boolean bold) {
            font(g, size, bold);
            while (size > 9 && g.getFontMetrics().stringWidth(text) > maxWidth) {
                size--;
                font(g, size, bold);
            }
        }

        private void fitCentered(Graphics2D g, String text, int y, int size, int maxWidth) {
            fitFont(g, text, maxWidth, size, true);
            g.drawString(text, (W - g.getFontMetrics().stringWidth(text)) / 2, y);
        }

        private void fitCenteredIn(Graphics2D g, String text, Rectangle box, int y, int size, boolean bold) {
            fitFont(g, text, box.width - 16, size, bold);
            centeredIn(g, text, box, y);
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
                if (e.dead || (e.beamWarn <= 0 && e.beamTime <= 0)) {
                    continue;
                }
                boolean golden = e.type == EnemyType.ECLIPSE;
                for (int i = 0; i < e.beamCount; i++) {
                    double angle = e.beamAngle + i * Math.PI * 2 / e.beamCount;
                    int ex = (int) (e.x + Math.cos(angle) * 1300), ey = (int) (e.y + Math.sin(angle) * 1300);
                    if (e.beamWarn > 0) {
                        alpha(g, 0.3f + 0.5f * (float) Math.abs(Math.sin(clock * 22)));
                        g.setColor(golden ? new Color(255, 220, 90) : new Color(255, 80, 70));
                        g.setStroke(new BasicStroke(3));
                        g.drawLine((int) e.x, (int) e.y, ex, ey);
                    } else {
                        alpha(g, 0.35f);
                        g.setColor(golden ? new Color(255, 200, 60) : new Color(255, 70, 50));
                        g.setStroke(new BasicStroke(34, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.drawLine((int) e.x, (int) e.y, ex, ey);
                        alpha(g, 1);
                        g.setColor(golden ? new Color(255, 250, 220) : new Color(255, 225, 200));
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
            if ((e.type == EnemyType.GHOST || e.type == EnemyType.REAPER) && e.blinkWarn > 0) {
                alpha(g, 0.8f);
                g.setColor(e.type.color);
                g.setStroke(new BasicStroke(2));
                double ring = (e.type == EnemyType.REAPER ? 30 : 12) + e.blinkWarn * 34;
                g.drawOval((int) (e.warpX - ring), (int) (e.warpY - ring), (int) (ring * 2), (int) (ring * 2));
                g.setStroke(new BasicStroke(1));
                alpha(g, 1);
            }
            drawWarnings(g, e);
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
            if (e.phased) {
                // Wraiths and the Reaper are see-through while they cannot be hit.
                alpha(g, 0.22f);
            } else if (e.type == EnemyType.GHOST) {
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
            if (e.type != EnemyType.SEGMENT && e.type != EnemyType.ORB && e.type != EnemyType.SPINNER) {
                drawFace(g, e, (int) r);
            }
            boolean bar = e.type == EnemyType.TANK || e.type == EnemyType.HIVE || e.type == EnemyType.HEAD || e.elite;
            if (bar && e.hp < e.maxHp) {
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
                case RAMMER:
                case SNIPER:
                case SWARMER:
                    return regular(e.x, e.y, r + 2, 3, e.angle);
                case TURRET:
                    return regular(e.x, e.y, r, 4, e.angle);
                case LAYER:
                case HIVE:
                    return regular(e.x, e.y, r, 6, e.angle * 0.3);
                case BULWARK:
                    return regular(e.x, e.y, r, 6, e.aimX);
                case WRAITH:
                    return regular(e.x, e.y, r, 5, Math.sin(clock * 2 + e.wobble) * 0.6);
                case QUEEN:
                    return regular(e.x, e.y, r, 10, e.angle * 0.2);
                case REAPER:
                    return regular(e.x, e.y, r, 3, angleToPlayer(e));
                case SINGULARITY:
                case PULSAR:
                case ECLIPSE:
                case ORB:
                    return regular(e.x, e.y, r, 16, e.angle);
                case MORTAR:
                    return regular(e.x, e.y, r, 5, e.angle * 0.2);
                case SPINNER:
                    return star(e.x, e.y, r + 4, r * 0.45, 6, e.angle);
                case SPORE:
                    return star(e.x, e.y, r, r * 0.78, 9, e.angle * 0.4);
                case TEMPEST:
                    return star(e.x, e.y, r, r * 0.72, 7, e.angle * 0.8);
                case HYDRA:
                    return regular(e.x, e.y, r, 7, e.angle * 0.2);
                case HEAD:
                    return regular(e.x, e.y, r, 5, e.angle);
                default:
                    return regular(e.x, e.y, r, 8, e.angle * 0.3);
            }
        }

        /** A star with points out to outer and dips in to inner. */
        private Polygon star(double x, double y, double outer, double inner, int points, double angle) {
            Polygon polygon = new Polygon();
            for (int i = 0; i < points * 2; i++) {
                double a = angle + i * Math.PI / points, r = i % 2 == 0 ? outer : inner;
                polygon.addPoint((int) Math.round(x + Math.cos(a) * r), (int) Math.round(y + Math.sin(a) * r));
            }
            return polygon;
        }

        /** Shows where an enemy is about to attack: charge paths, laser sights and target circles. */
        private void drawWarnings(Graphics2D g, Enemy e) {
            float flicker = 0.35f + 0.4f * (float) Math.abs(Math.sin(clock * 18));
            if (e.chargeWarn > 0 && (e.type == EnemyType.OVERLORD || e.type == EnemyType.HYDRA
                    || e.type == EnemyType.REAPER || e.type == EnemyType.RAMMER)) {
                double length = e.type == EnemyType.RAMMER ? 312 : e.type == EnemyType.REAPER ? 360 : 396;
                float width = e.type == EnemyType.RAMMER ? (float) e.r * 1.4f : (float) e.r * 1.6f;
                alpha(g, flicker);
                g.setColor(new Color(255, 70, 110));
                g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double d = Math.max(1, Math.hypot(e.aimX - e.x, e.aimY - e.y));
                g.drawLine((int) e.x, (int) e.y, (int) (e.x + (e.aimX - e.x) / d * length), (int) (e.y + (e.aimY - e.y) / d * length));
            }
            if (e.type == EnemyType.SNIPER && e.chargeWarn > 0) {
                // The sight locks on (and stops following you) just before the shot.
                boolean locked = e.chargeWarn <= 0.28;
                alpha(g, locked ? 0.9f : 0.45f);
                g.setColor(locked ? Color.WHITE : new Color(255, 90, 140));
                g.setStroke(new BasicStroke(locked ? 3 : 1.5f));
                g.drawLine((int) e.x, (int) e.y, (int) (e.x + Math.cos(e.aimX) * 1300), (int) (e.y + Math.sin(e.aimX) * 1300));
            }
            if (e.type == EnemyType.COLOSSUS && e.slamWarn > 0) {
                alpha(g, 0.15f + 0.25f * (float) (1 - e.slamWarn));
                g.setColor(e.type.color);
                g.fillOval((int) e.warpX - 140, (int) e.warpY - 140, 280, 280);
                alpha(g, flicker);
                g.setStroke(new BasicStroke(3));
                g.drawOval((int) e.warpX - 140, (int) e.warpY - 140, 280, 280);
            }
            if (e.type == EnemyType.SINGULARITY && e.pullTime > 0) {
                g.setColor(e.type.color);
                g.setStroke(new BasicStroke(2));
                for (int i = 0; i < 3; i++) {
                    double ring = 60 + ((1 - (clock * 0.8 + i / 3.0) % 1.0)) * 360;
                    alpha(g, (float) (0.5 * (1 - ring / 420)));
                    g.drawOval((int) (e.x - ring), (int) (e.y - ring), (int) (ring * 2), (int) (ring * 2));
                }
            }
            g.setStroke(new BasicStroke(1));
            alpha(g, 1);
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
                    break;
                case WARDEN:
                    g.setColor(new Color(120, 25, 25));
                    g.fillPolygon(regular(e.x, e.y, r * 0.7, 4, -e.angle * 0.3));
                    break;
                case TURRET:
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(4));
                    for (int i = 0; i < 4; i++) {
                        double a = e.angle + i * Math.PI / 2;
                        g.drawLine((int) e.x, (int) e.y, (int) (e.x + Math.cos(a) * (r + 7)), (int) (e.y + Math.sin(a) * (r + 7)));
                    }
                    g.setStroke(new BasicStroke(1));
                    break;
                case MEDIC:
                    g.setColor(Color.WHITE);
                    g.fillRect((int) (e.x - r * 0.15), (int) (e.y + r * 0.2), (int) (r * 0.3), (int) (r * 0.7));
                    g.fillRect((int) (e.x - r * 0.35), (int) (e.y + r * 0.4), (int) (r * 0.7), (int) (r * 0.3));
                    break;
                case BULWARK:
                    // The shield: bullets from this side bounce off.
                    g.setColor(new Color(230, 240, 255));
                    g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int deg = (int) Math.toDegrees(-e.aimX);
                    g.drawArc((int) (e.x - r - 7), (int) (e.y - r - 7), (int) (r * 2 + 14), (int) (r * 2 + 14), deg - 55, 110);
                    g.setStroke(new BasicStroke(1));
                    break;
                case QUEEN:
                    g.setColor(new Color(255, 245, 170));
                    for (int i = -1; i <= 1; i++) {
                        int cx = (int) (e.x + i * r * 0.35), top = (int) (e.y - r * 0.95);
                        g.fillPolygon(new int[] {cx - 8, cx, cx + 8}, new int[] {top + 14, top - (i == 0 ? 12 : 4), top + 14}, 3);
                    }
                    break;
                case SINGULARITY:
                    g.setColor(new Color(10, 5, 25));
                    g.fillOval((int) (e.x - r * 0.72), (int) (e.y - r * 0.72), (int) (r * 1.44), (int) (r * 1.44));
                    g.setColor(new Color(190, 160, 255));
                    g.setStroke(new BasicStroke(3));
                    g.drawArc((int) (e.x - r * 0.9), (int) (e.y - r * 0.9), (int) (r * 1.8), (int) (r * 1.8), (int) (clock * 200), 120);
                    g.drawArc((int) (e.x - r * 0.9), (int) (e.y - r * 0.9), (int) (r * 1.8), (int) (r * 1.8), (int) (clock * 200) + 180, 120);
                    g.setStroke(new BasicStroke(1));
                    break;
                case PULSAR:
                    float glow = 0.4f + 0.6f * (float) (e.chargeWarn > 0 ? Math.abs(Math.sin(clock * 20)) : Math.abs(Math.sin(clock * 3)));
                    alpha(g, glow);
                    g.setColor(Color.WHITE);
                    g.fillOval((int) (e.x - r * 0.45), (int) (e.y - r * 0.45), (int) (r * 0.9), (int) (r * 0.9));
                    alpha(g, 1);
                    break;
                case MORTAR:
                    g.setColor(new Color(90, 60, 40));
                    g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine((int) e.x, (int) e.y, (int) (e.x + Math.cos(e.angle) * (r + 6)), (int) (e.y + Math.sin(e.angle) * (r + 6)));
                    g.setStroke(new BasicStroke(1));
                    break;
                case SPINNER:
                    g.setColor(new Color(40, 70, 80));
                    g.fillOval((int) e.x - 4, (int) e.y - 4, 8, 8);
                    break;
                case TEMPEST:
                    g.setColor(new Color(240, 250, 255));
                    g.setStroke(new BasicStroke(3));
                    int zx = (int) e.x, zy = (int) e.y, s = (int) (r * 0.45);
                    g.drawPolyline(new int[] {zx + s / 3, zx - s / 3, zx + s / 4, zx - s / 2},
                            new int[] {zy - s, zy - s / 6, zy + s / 6, zy + s}, 4);
                    g.setStroke(new BasicStroke(1));
                    break;
                case HYDRA:
                    g.setColor(new Color(30, 110, 80));
                    g.fillPolygon(regular(e.x, e.y, r * 0.66, 7, -e.angle * 0.3));
                    break;
                case ECLIPSE:
                    // A dark moon sliding over a bright sun.
                    g.setColor(new Color(20, 14, 30));
                    double slide = Math.sin(clock * 0.7) * r * 0.25;
                    g.fillOval((int) (e.x - r * 0.8 + slide), (int) (e.y - r * 0.8), (int) (r * 1.6), (int) (r * 1.6));
                    break;
                case ORB:
                    g.setColor(Color.WHITE);
                    g.fillOval((int) (e.x - r * 0.5), (int) (e.y - r * 0.5), (int) r, (int) r);
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
            int blades = lvl(Upgrade.SAW_BLADES);
            for (int i = 0; i < blades; i++) {
                double angle = sawAngle + i * Math.PI * 2 / blades;
                double bx = px + Math.cos(angle) * 125, by = py + Math.sin(angle) * 125;
                Polygon saw = star(bx, by, 16, 10, 8, clock * 14);
                glowPolygon(g, saw, new Color(220, 230, 255), 8);
                g.setColor(new Color(220, 230, 255));
                g.fillPolygon(saw);
                g.setColor(new Color(60, 70, 100));
                g.fillOval((int) bx - 4, (int) by - 4, 8, 8);
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
            for (int i = 0; i < shields; i++) {
                int sx = 16 + maxHealth * 28 + 6 + i * 22;
                glowCircle(g, sx + 8, 26, 7, SHIELD_COLOR);
            }
            int infoY = 56;
            if (secondLives > 0) {
                font(g, 13, true);
                g.setColor(new Color(255, 190, 60));
                g.drawString(t("hud.secondLife", "SECOND LIFE READY"), 18, infoY);
                infoY += 18;
            }
            if (dailyRun) {
                font(g, 13, true);
                g.setColor(new Color(120, 220, 255));
                g.drawString(t("hud.daily", "DAILY: {0}", label(modifier)), 18, infoY);
            }

            font(g, 26, true);
            g.setColor(Color.WHITE);
            rightAligned(g, String.valueOf(Math.round(displayScore)), 36);
            font(g, 14, false);
            g.setColor(new Color(170, 180, 220));
            rightAligned(g, t("hud.waveBest", "WAVE {0}   BEST {1}", wave, dailyRun ? dailyBestToday() : highScore), 56);

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
                g.fillRect(W - 78, 124, (int) (60 * Math.max(0, comboTimer) / comboDuration()), 4);
            }

            // Dash, special power and bombs along the bottom. The keys shown are the player's own.
            font(g, 13, true);
            g.setColor(new Color(170, 180, 220));
            fitFont(g, t("hud.dash", "DASH") + " [" + keyLabel(Action.DASH) + "]", 130, 13, true);
            g.drawString(t("hud.dash", "DASH") + " [" + keyLabel(Action.DASH) + "]", 18, H - 36);
            drawBar(g, 18, H - 28, 120, 1 - dashCooldown / dashDelay, shipColor());

            boolean ready = specialCharge >= 1;
            g.setColor(ready ? new Color(255, 225, 120) : new Color(170, 180, 220));
            String specialText = label(ship.special) + " [" + keyLabel(Action.SPECIAL) + "]"
                    + (ready && (int) (clock * 4) % 2 == 0 ? "  " + t("hud.ready", "READY!") : "");
            fitFont(g, specialText, 185, 13, true);
            g.drawString(specialText, 158, H - 36);
            drawBar(g, 158, H - 28, 170, specialCharge, ready ? new Color(255, 225, 120) : new Color(180, 130, 255));

            g.setColor(new Color(170, 180, 220));
            String bombText = t("hud.bomb", "BOMB") + " [" + keyLabel(Action.BOMB) + "]  x" + bombs;
            fitFont(g, bombText, 200, 13, true);
            g.drawString(bombText, 350, H - 36);
            for (int i = 0; i < bombs && i < 8; i++) {
                drawBombIcon(g, 360 + i * 24, H - 18);
            }
            if (autoFire) {
                g.setColor(new Color(150, 240, 150));
                font(g, 13, true);
                rightAligned(g, t("hud.autoFire", "AUTO-FIRE ON"), H - 18);
            }

            for (Enemy e : enemies) {
                if (e.type.isBoss() && !e.dead && e.spawnTimer <= 0) {
                    g.setColor(new Color(50, 25, 55));
                    g.fillRoundRect(230, 46, 540, 18, 8, 8);
                    g.setColor(e.hp < e.maxHp / 2 ? new Color(255, 70, 90) : e.type.color);
                    g.fillRoundRect(230, 46, (int) (540 * Math.max(0, e.hp) / e.maxHp), 18, 8, 8);
                    g.setColor(Color.WHITE);
                    centered(g, bossLabel(e.type), 60, 13, true);
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
            fitCentered(g, t("upgrade.choose", "CHOOSE AN UPGRADE"), 110, 44, W - 60);
            g.setColor(new Color(170, 180, 220));
            fitCentered(g, t("upgrade.help", "Click a card or press 1, 2 or 3. Upgrades cost the coins you picked up this run."), 145, 17, W - 60);
            font(g, 20, true);
            String wallet = t("upgrade.wallet", "{0} coins to spend", runCoins);
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
            fitCenteredIn(g, t("upgrade.reroll", "REROLL [{0}]  ({1} left)", keyLabel(Action.REROLL), rerolls), button, button.y + 32, 18, true);
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
            centeredIn(g, label(upgrade.rarity), box, y + 28);
            g.fillPolygon(regular(x + width / 2.0, y + 84, 34, 6, clock + index));
            g.setColor(new Color(15, 15, 30));
            font(g, 28, true);
            centeredIn(g, String.valueOf(index + 1), box, y + 94);

            g.setColor(Color.WHITE);
            fitCenteredIn(g, label(upgrade), box, y + 158, 23, true);
            g.setColor(new Color(190, 198, 235));
            font(g, 17, false);
            int line = 0;
            for (String text : wrap(g, about(upgrade), width - 40)) {
                if (line < 3) {
                    centeredIn(g, text, box, y + 190 + line * 23);
                }
                line++;
            }
            g.setColor(color);
            font(g, 14, true);
            centeredIn(g, t("upgrade.level", "Level {0} / {1}", stacks[upgrade.ordinal()], upgrade.max), box, y + height - 18);

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
                centeredIn(g, t("upgrade.free", "FREE"), box, y + height - 46);
            } else {
                font(g, 20, true);
                String text = affordable ? String.valueOf(price) : t("upgrade.need", "{0} (need {1})", price, price - runCoins);
                int textX = x + (width - g.getFontMetrics().stringWidth(text)) / 2 + 12;
                drawCoin(g, textX - 20, y + height - 53, 8);
                g.setColor(affordable ? new Color(255, 220, 110) : new Color(255, 110, 120));
                g.drawString(text, textX, y + height - 46);
            }
        }

        /** Splits text into lines that fit. Words too long for a line (and Chinese or Japanese) break anywhere. */
        private List<String> wrap(Graphics2D g, String text, int width) {
            List<String> lines = new ArrayList<String>();
            String current = "";
            for (String word : text.split(" ")) {
                String next = current.isEmpty() ? word : current + " " + word;
                if (g.getFontMetrics().stringWidth(next) <= width) {
                    current = next;
                    continue;
                }
                if (!current.isEmpty() && g.getFontMetrics().stringWidth(word) <= width) {
                    lines.add(current);
                    current = word;
                    continue;
                }
                // Break letter by letter.
                current = current.isEmpty() ? "" : current + " ";
                for (int i = 0; i < word.length(); i++) {
                    String longer = current + word.charAt(i);
                    // Punctuation never starts a line; it stays with the text before it.
                    boolean punctuation = "%)\uff09\u3002\u3001\uff0c\uff01\uff1f\uff1a.,!?:".indexOf(word.charAt(i)) >= 0;
                    if (g.getFontMetrics().stringWidth(longer) > width && !current.trim().isEmpty() && !punctuation) {
                        lines.add(current.trim());
                        current = String.valueOf(word.charAt(i));
                    } else {
                        current = longer;
                    }
                }
            }
            lines.add(current);
            return lines;
        }

        static final Color MENU_ACCENT = new Color(90, 230, 255);

        String about(MenuItem m) {
            switch (m) {
                case PLAY: return t("menu.PLAY.text", "Pick a ship and a difficulty, then fight wave after wave.");
                case CONTINUE: return t("menu.CONTINUE.text", "Pick your saved run back up from the start of its wave.");
                case DAILY: return t("menu.DAILY.text", "Everybody gets the same ship and twist today. Come back tomorrow for a new one.");
                case UPGRADES: return t("menu.UPGRADES.text", "Spend coins on permanent upgrades that help in every run.");
                case SKINS: return t("menu.SKINS.text", "Spend diamonds on new colors for your ships.");
                case RECORDS: return t("menu.RECORDS.text", "Your achievements, best scores and defeated bosses.");
                case SETTINGS: return t("menu.SETTINGS.text", "Sound, screen, controls and language.");
                default: return t("menu.QUIT.text", "Your progress is saved automatically.");
            }
        }

        private void drawMenu(Graphics2D g) {
            double pulse = Math.sin(clock * 2.5);
            font(g, 76, true);
            String title = "NEON OVERDRIVE";
            int titleX = (W - g.getFontMetrics().stringWidth(title)) / 2;
            alpha(g, 0.4f);
            g.setColor(new Color(255, 70, 200));
            g.drawString(title, titleX + 5 + (int) (pulse * 5), 107);
            alpha(g, 1);
            g.setPaint(new GradientPaint(0, 40, new Color(160, 250, 255), 0, 105, new Color(50, 170, 255)));
            g.drawString(title, titleX, 102);
            g.setColor(new Color(170, 180, 230));
            fitCentered(g, t("menu.tagline", "{0} SHIPS  -  {1} BOSSES  -  {2} UPGRADES  -  A NEW CHALLENGE EVERY DAY",
                    Ship.values().length, BOSSES.length, Upgrade.values().length + Perk.values().length), 134, 15, W - 80);

            // A parade of enemies, so new players see what is waiting for them.
            EnemyType[] parade = {EnemyType.CHASER, EnemyType.SPEEDER, EnemyType.SWARMER, EnemyType.BOMBER, EnemyType.SPLITTER,
                EnemyType.SPORE, EnemyType.GHOST, EnemyType.SHOOTER, EnemyType.SPINNER, EnemyType.PULSAR};
            px = W / 2.0;
            py = 700;
            for (int i = 0; i < parade.length; i++) {
                Enemy sample = new Enemy(parade[i], 95 + i * 90, 170 + Math.sin(clock * 3 + i) * 4, 1);
                sample.angle = clock * 2;
                drawEnemy(g, sample);
            }

            List<MenuItem> items = menuItems();
            int selected = Math.min(menuIndex, items.size() - 1);
            for (int i = 0; i < items.size(); i++) {
                drawMenuButton(g, menuButton(i), label(items.get(i)), i == selected, i + 1);
            }

            Rectangle panel = new Rectangle(400, 205, 560, 433);
            g.setColor(new Color(14, 16, 36));
            g.fillRoundRect(panel.x, panel.y, panel.width, panel.height, 22, 22);
            g.setColor(new Color(70, 80, 140));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(panel.x, panel.y, panel.width, panel.height, 22, 22);
            g.setStroke(new BasicStroke(1));
            MenuItem item = items.get(selected);
            g.setColor(MENU_ACCENT);
            fitFont(g, label(item).toUpperCase(), panel.width - 56, 28, true);
            g.drawString(label(item).toUpperCase(), panel.x + 28, panel.y + 44);
            g.setColor(new Color(190, 198, 235));
            font(g, 15, false);
            int line = 0;
            for (String text : wrap(g, about(item), panel.width - 56)) {
                g.drawString(text, panel.x + 28, panel.y + 70 + line * 20);
                line++;
            }
            drawMenuPreview(g, item, panel, panel.y + 92 + line * 20);

            if (noticeTimer > 0) {
                g.setColor(new Color(255, 225, 120));
                fitFont(g, notice, 360, 17, true);
                g.drawString(notice, 580 - g.getFontMetrics().stringWidth(notice) / 2, 668);
            }
            g.setColor(new Color(130, 140, 180));
            fitFont(g, t("menu.hint", "Arrows: choose    ENTER: select    O: settings"), 340, 14, false);
            g.drawString(t("menu.hint", "Arrows: choose    ENTER: select    O: settings"), 60, 676);

            Rectangle reset = resetButton();
            boolean hover = !confirmReset && reset.contains(mouseX, mouseY);
            g.setColor(hover ? new Color(70, 26, 40) : new Color(34, 18, 30));
            g.fillRoundRect(reset.x, reset.y, reset.width, reset.height, 14, 14);
            g.setColor(hover ? new Color(255, 110, 120) : new Color(150, 70, 85));
            g.setStroke(new BasicStroke(hover ? 2.5f : 1.5f));
            g.drawRoundRect(reset.x, reset.y, reset.width, reset.height, 14, 14);
            g.setStroke(new BasicStroke(1));
            fitCenteredIn(g, t("menu.reset", "RESET PROGRESS  [DEL]"), reset, reset.y + 26, 14, true);

            if (confirmReset) {
                drawResetConfirm(g);
            }
        }

        private void drawMenuButton(Graphics2D g, Rectangle b, String text, boolean selected, int number) {
            if (selected) {
                g.setPaint(new GradientPaint(b.x, 0, new Color(30, 90, 150), b.x + b.width, 0, new Color(90, 40, 150)));
            } else {
                g.setColor(b.contains(mouseX, mouseY) ? new Color(28, 32, 66) : new Color(18, 20, 44));
            }
            g.fillRoundRect(b.x, b.y, b.width, b.height, 14, 14);
            g.setColor(selected ? MENU_ACCENT : new Color(60, 66, 110));
            g.setStroke(new BasicStroke(selected ? 2.5f : 1.2f));
            g.drawRoundRect(b.x, b.y, b.width, b.height, 14, 14);
            g.setStroke(new BasicStroke(1));
            int shift = 0;
            if (selected) {
                g.setColor(MENU_ACCENT);
                g.fillRoundRect(b.x + 8, b.y + 10, 5, b.height - 20, 4, 4);
                shift = 6 + (int) (Math.sin(clock * 6) * 2);
            }
            g.setColor(selected ? Color.WHITE : new Color(185, 192, 230));
            fitFont(g, text.toUpperCase(), b.width - 80, 21, true);
            g.drawString(text.toUpperCase(), b.x + 26 + shift, b.y + b.height / 2 + 8);
            if (selected) {
                font(g, 20, true);
                g.setColor(MENU_ACCENT);
                g.drawString(">", b.x + b.width - 30 + shift / 2, b.y + b.height / 2 + 7);
            }
        }

        /** The right half of the main menu shows what the highlighted button leads to. */
        private void drawMenuPreview(Graphics2D g, MenuItem item, Rectangle panel, int top) {
            int left = panel.x + 28, right = panel.x + panel.width - 28;
            switch (item) {
                case PLAY:
                case CONTINUE:
                case DAILY: {
                    Ship shown = item == MenuItem.DAILY ? dailyShip() : ship;
                    int cx = panel.x + 130, cy = top + 120;
                    Color color = item == MenuItem.DAILY ? shown.color : colorFor(shown, skin);
                    Polygon shape = shipShape(shown, cx, cy, -Math.PI / 2 + Math.sin(clock * 1.3) * 0.35, 3.2);
                    alpha(g, 0.18f);
                    g.setColor(color);
                    g.fillOval(cx - 95, cy - 95, 190, 190);
                    alpha(g, 1);
                    glowPolygon(g, shape, color, 18);
                    g.setColor(color);
                    g.fillPolygon(shape);
                    int x = panel.x + 260, y = top + 30;
                    g.setColor(Color.WHITE);
                    font(g, 26, true);
                    g.drawString(shown.title, x, y);
                    g.setColor(new Color(170, 180, 220));
                    fitFont(g, role(shown), right - x, 15, false);
                    g.drawString(role(shown), x, y + 24);
                    g.setColor(new Color(255, 225, 120));
                    font(g, 13, false);
                    int specialLine = 0;
                    for (String text : wrap(g, specialText(shown), right - x)) {
                        g.drawString(text, x, y + 50 + specialLine++ * 17);
                    }
                    y += 70 + specialLine * 17;
                    if (item == MenuItem.DAILY) {
                        g.setColor(new Color(120, 220, 255));
                        fitFont(g, label(dailyModifier()), right - x, 18, true);
                        g.drawString(label(dailyModifier()), x, y);
                        g.setColor(new Color(190, 198, 235));
                        font(g, 13, false);
                        int l = 0;
                        for (String text : wrap(g, about(dailyModifier()), right - x)) {
                            g.drawString(text, x, y + 20 + l++ * 17);
                        }
                        y += 34 + l * 17;
                        drawPreviewRow(g, t("menu.streak", "Daily streak"), String.valueOf(currentStreak()), x, right, y);
                        drawPreviewRow(g, t("menu.bestToday", "Best today"), String.valueOf(dailyBestToday()), x, right, y + 24);
                    } else if (item == MenuItem.CONTINUE && savedRunInfo != null) {
                        g.setColor(new Color(255, 200, 80));
                        fitFont(g, savedRunInfo, right - x, 18, true);
                        g.drawString(savedRunInfo, x, y);
                        drawPreviewRow(g, t("menu.difficulty", "Difficulty"), label(difficulty), x, right, y + 30);
                    } else {
                        drawPreviewRow(g, t("menu.difficulty", "Difficulty"), label(difficulty), x, right, y);
                        drawPreviewRow(g, t("records.bestScore", "Best score"), String.valueOf(highScore), x, right, y + 24);
                        drawPreviewRow(g, t("records.bestWave", "Best wave"), String.valueOf(bestWave), x, right, y + 48);
                    }
                    break;
                }
                case UPGRADES: {
                    int owned = 0, total = 0;
                    for (Perk p : Perk.values()) {
                        owned += perks[p.ordinal()];
                        total += p.max;
                    }
                    drawPreviewRow(g, t("menu.perkLevels", "Upgrade levels bought"), owned + " / " + total, left, right, top + 10);
                    drawBar(g, left, top + 22, right - left, owned / (double) total, new Color(120, 240, 160));
                    for (Perk p : Perk.values()) {
                        int i = p.ordinal();
                        Rectangle box = new Rectangle(left + i % 5 * 102, top + 48 + i / 5 * 56, 96, 50);
                        int level = perks[i];
                        g.setColor(new Color(22, 26, 54));
                        g.fillRoundRect(box.x, box.y, box.width, box.height, 10, 10);
                        g.setColor(level >= p.max ? new Color(120, 240, 160) : Color.WHITE);
                        fitCenteredIn(g, label(p), box, box.y + 20, 11, true);
                        for (int d = 0; d < p.max; d++) {
                            g.setColor(d < level ? new Color(120, 240, 160) : new Color(50, 54, 90));
                            g.fillOval((int) box.getCenterX() - p.max * 6 + d * 12 + 1, box.y + 30, 9, 9);
                        }
                    }
                    break;
                }
                case SKINS: {
                    for (Skin k : Skin.values()) {
                        int i = k.ordinal();
                        Rectangle box = new Rectangle(left + i % 4 * 128, top + 4 + i / 4 * 130, 120, 120);
                        boolean owned = ownedSkins[i];
                        Color color = colorFor(ship, k);
                        g.setColor(k == skin ? new Color(36, 44, 90) : new Color(22, 26, 54));
                        g.fillRoundRect(box.x, box.y, box.width, box.height, 14, 14);
                        if (k == skin) {
                            g.setColor(color);
                            g.setStroke(new BasicStroke(2.5f));
                            g.drawRoundRect(box.x, box.y, box.width, box.height, 14, 14);
                            g.setStroke(new BasicStroke(1));
                        }
                        Polygon shape = shipShape(ship, box.getCenterX(), box.y + 50, clock * 0.8 + i, 1.4);
                        g.setColor(owned ? color : color.darker().darker().darker());
                        g.fillPolygon(shape);
                        g.setColor(owned ? Color.WHITE : new Color(130, 135, 165));
                        fitCenteredIn(g, label(k), box, box.y + 98, 13, true);
                        if (!owned) {
                            g.setColor(DIAMOND_COLOR);
                            fitCenteredIn(g, k.cost + " " + t("word.diamonds", "diamonds"), box, box.y + 113, 10, false);
                        }
                    }
                    break;
                }
                case RECORDS: {
                    int earned = 0;
                    for (boolean done : achieved) {
                        earned += done ? 1 : 0;
                    }
                    int beaten = 0;
                    for (int count : bossKills) {
                        beaten += count > 0 ? 1 : 0;
                    }
                    String[][] rows = {
                        {t("records.achievements", "Achievements"), earned + " / " + achieved.length},
                        {t("records.bossTypes", "Different bosses beaten"), beaten + " / " + BOSSES.length},
                        {t("records.runs", "Runs played"), String.valueOf(totalRuns)},
                        {t("records.time", "Time played"), formatTime(playSeconds)},
                        {t("records.kills", "Enemies destroyed"), String.valueOf(totalKills)},
                        {t("records.bestScore", "Best score"), String.valueOf(highScore)},
                        {t("records.bestWave", "Best wave"), String.valueOf(bestWave)},
                        {t("records.bestCombo", "Best combo"), String.valueOf(bestComboEver)},
                    };
                    for (int i = 0; i < rows.length; i++) {
                        drawPreviewRow(g, rows[i][0], rows[i][1], left, right, top + 16 + i * 32);
                    }
                    break;
                }
                case SETTINGS: {
                    String on = t("option.on", "ON"), off = t("option.off", "OFF");
                    String[][] rows = {
                        {t("settings.language", "Language"), language.nativeName},
                        {t("option.sound", "Sound effects"), sound.effectsOn ? on + "  (" + sound.effectsVolume + "%)" : off},
                        {t("option.music", "Music"), sound.musicOn ? on + "  (" + sound.musicVolume + "%)" : off},
                        {t("option.autoFire", "Auto-fire"), autoFire ? on : off},
                        {label(Action.DASH), keyLabel(Action.DASH)},
                        {label(Action.SPECIAL), keyLabel(Action.SPECIAL)},
                        {label(Action.BOMB), keyLabel(Action.BOMB)},
                        {label(Action.PAUSE), keyLabel(Action.PAUSE)},
                    };
                    for (int i = 0; i < rows.length; i++) {
                        drawPreviewRow(g, rows[i][0], rows[i][1], left, right, top + 16 + i * 32);
                    }
                    break;
                }
                default: {
                    g.setColor(new Color(255, 120, 200));
                    font(g, 30, true);
                    String bye = t("menu.bye", "See you soon, pilot!");
                    fitFont(g, bye, panel.width - 40, 30, true);
                    g.drawString(bye, panel.x + (panel.width - g.getFontMetrics().stringWidth(bye)) / 2, top + 110);
                    Polygon shape = shipShape(ship, panel.getCenterX() + Math.sin(clock) * 140, top + 200, 0, 2);
                    g.setColor(shipColor());
                    g.fillPolygon(shape);
                    break;
                }
            }
        }

        private void drawPreviewRow(Graphics2D g, String label, String value, int left, int right, int y) {
            font(g, 15, true);
            int valueWidth = g.getFontMetrics().stringWidth(value);
            g.setColor(Color.WHITE);
            g.drawString(value, right - valueWidth, y);
            g.setColor(new Color(170, 180, 220));
            fitFont(g, label, right - left - valueWidth - 16, 15, false);
            g.drawString(label, left, y);
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
            fitCenteredIn(g, t("reset.title", "RESET ALL PROGRESS?"), box, box.y + 54, 30, true);
            g.setColor(new Color(200, 205, 235));
            font(g, 16, false);
            List<String> lines = wrap(g, t("reset.text", "Coins, diamonds, ships, skins, permanent upgrades, achievements, records and the saved run are all wiped. Your settings are kept."), box.width - 50);
            for (int i = 0; i < lines.size() && i < 4; i++) {
                centeredIn(g, lines.get(i), box, box.y + 96 + i * 24);
            }
            g.setColor(new Color(255, 200, 120));
            fitCenteredIn(g, t("reset.warning", "This cannot be undone in the game."), box, box.y + 200, 15, true);

            Rectangle yes = resetYesButton(), no = resetNoButton();
            boolean hoverYes = yes.contains(mouseX, mouseY), hoverNo = no.contains(mouseX, mouseY);
            g.setColor(hoverYes ? new Color(220, 60, 80) : new Color(160, 40, 60));
            g.fillRoundRect(yes.x, yes.y, yes.width, yes.height, 16, 16);
            g.setColor(hoverNo ? new Color(70, 80, 140) : new Color(44, 50, 96));
            g.fillRoundRect(no.x, no.y, no.width, no.height, 16, 16);
            g.setColor(Color.WHITE);
            fitCenteredIn(g, t("reset.yes", "YES, RESET  [Y]"), yes, yes.y + 35, 19, true);
            fitCenteredIn(g, t("reset.no", "CANCEL  [N]"), no, no.y + 35, 19, true);
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
            showNotice(t("notice.reset", "Progress reset. A fresh start!"));
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
            fitFont(g, t("hangar.title", "HANGAR"), 200, 36, true);
            g.drawString(t("hangar.title", "HANGAR"), 40, 62);
            drawWallet(g, 60);
            drawSideButton(g, skinsButton(), t("hangar.skins", "SKINS  [K]"), t("hangar.equipped", "{0} equipped", label(skin)), DIAMOND_COLOR);
            drawSideButton(g, settingsButton(), t("hangar.settings", "SETTINGS  [O]"), language.nativeName, new Color(170, 200, 255));

            for (Ship s : Ship.values()) {
                drawShipCard(g, s);
            }
            g.setColor(new Color(170, 180, 220));
            centered(g, t("hangar.difficulty", "DIFFICULTY"), 318, 14, true);
            for (Difficulty d : Difficulty.values()) {
                drawDifficultyButton(g, d);
            }
            drawWorkshopButton(g);

            Rectangle launch = launchButton();
            boolean hover = launch.contains(mouseX, mouseY);
            g.setPaint(new GradientPaint(0, launch.y, hover ? new Color(120, 255, 170) : new Color(80, 225, 140),
                    0, launch.y + launch.height, new Color(30, 150, 90)));
            g.fillRoundRect(launch.x, launch.y, launch.width, launch.height, 22, 22);
            g.setColor(new Color(10, 30, 20));
            if (hasSavedRun()) {
                fitCenteredIn(g, t("hangar.newRun", "NEW RUN"), launch, launch.y + 32, 20, true);
                font(g, 13, true);
                centeredIn(g, "[ENTER]", launch, launch.y + 54);

                Rectangle resume = continueButton();
                boolean hoverResume = resume.contains(mouseX, mouseY);
                g.setPaint(new GradientPaint(0, resume.y, hoverResume ? new Color(255, 225, 120) : new Color(255, 200, 80),
                        0, resume.y + resume.height, new Color(190, 120, 30)));
                g.fillRoundRect(resume.x, resume.y, resume.width, resume.height, 22, 22);
                g.setColor(new Color(40, 22, 5));
                fitCenteredIn(g, t("hangar.continue", "CONTINUE [C]"), resume, resume.y + 32, 20, true);
                fitCenteredIn(g, savedRunInfo, resume, resume.y + 54, 13, true);
            } else {
                fitCenteredIn(g, t("hangar.launch", "LAUNCH  [ENTER]"), launch, launch.y + 45, 30, true);
            }

            int earned = 0;
            for (boolean done : achieved) {
                earned += done ? 1 : 0;
            }
            drawSideButton(g, recordsButton(), t("hangar.records", "RECORDS  [T]"),
                    t("hangar.achievements", "{0} / {1} achievements", earned, achieved.length), new Color(170, 200, 255));
            int best = dailyBestToday();
            drawSideButton(g, dailyButton(), t("hangar.daily", "DAILY RUN  [G]"),
                    dailyShip().title + " - " + label(dailyModifier())
                            + (best > 0 ? " - " + t("hangar.dailyBest", "Best {0}", best) : ""),
                    new Color(120, 220, 255));

            if (noticeTimer > 0) {
                g.setColor(new Color(255, 225, 120));
                fitCentered(g, notice, 662, 20, W - 60);
            } else if (dailyButton().contains(mouseX, mouseY)) {
                g.setColor(new Color(120, 220, 255));
                fitCentered(g, t("hangar.today", "Today: {0}", about(dailyModifier())), 662, 16, W - 60);
            } else if (hasSavedRun() && launch.contains(mouseX, mouseY)) {
                g.setColor(new Color(255, 210, 110));
                fitCentered(g, t("hangar.newRunWarning", "A new run ends your saved run. You keep its coins."), 662, 16, W - 60);
            } else if (hasSavedRun()) {
                g.setColor(new Color(255, 210, 110));
                fitCentered(g, t("hangar.savedRun", "You have a saved run. Press C to continue from the start of its wave."), 662, 16, W - 60);
            } else {
                g.setColor(new Color(130, 140, 180));
                fitCentered(g, t("hangar.help", "Click a ship to choose or buy it. Earn coins by playing."), 662, 16, W - 60);
            }
        }

        /** The big hangar button that opens the permanent upgrades, with a peek at your progress. */
        private void drawWorkshopButton(Graphics2D g) {
            Rectangle box = workshopButton();
            boolean hover = box.contains(mouseX, mouseY);
            g.setColor(hover ? new Color(32, 40, 82) : new Color(20, 22, 48));
            g.fillRoundRect(box.x, box.y, box.width, box.height, 18, 18);
            g.setColor(new Color(120, 240, 160));
            g.setStroke(new BasicStroke(hover ? 3 : 1.5f));
            g.drawRoundRect(box.x, box.y, box.width, box.height, 18, 18);
            g.setStroke(new BasicStroke(1));
            g.setColor(Color.WHITE);
            fitFont(g, t("hangar.workshop", "WORKSHOP - PERMANENT UPGRADES  [U]"), 560, 22, true);
            g.drawString(t("hangar.workshop", "WORKSHOP - PERMANENT UPGRADES  [U]"), box.x + 24, box.y + 38);
            int owned = 0, total = 0, affordable = 0;
            for (Perk p : Perk.values()) {
                owned += perks[p.ordinal()];
                total += p.max;
                if (perks[p.ordinal()] < p.max && coins >= p.costAt(perks[p.ordinal()])) {
                    affordable++;
                }
            }
            g.setColor(new Color(170, 180, 220));
            font(g, 15, false);
            g.drawString(t("hangar.workshopLevels", "{0} / {1} levels bought", owned, total), box.x + 24, box.y + 66);
            drawBar(g, box.x + 24, box.y + 80, 540, owned / (double) total, new Color(120, 240, 160));
            if (affordable > 0) {
                g.setColor(new Color(255, 220, 110));
                String text = t("hangar.canBuy", "{0} upgrades you can buy now!", affordable);
                fitFont(g, text, 300, 18, true);
                g.drawString(text, box.x + box.width - 24 - g.getFontMetrics().stringWidth(text), box.y + 100);
            }
            // A row of little perk icons, lit up as you buy levels.
            for (Perk p : Perk.values()) {
                int i = p.ordinal();
                double fill = perks[i] / (double) p.max;
                int x = box.x + 600 + i % 10 * 30, y = box.y + 18 + i / 10 * 26;
                g.setColor(new Color(40, 44, 80));
                g.fillOval(x, y, 20, 20);
                g.setColor(fill >= 1 ? new Color(120, 240, 160) : new Color(255, 220, 110));
                g.fillArc(x, y, 20, 20, 90, -(int) (360 * fill));
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
            fitCenteredIn(g, title, box, box.y + 30, 19, true);
            g.setColor(new Color(190, 198, 235));
            fitCenteredIn(g, detail, box, box.y + 53, 13, false);
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
            fitFont(g, t("records.title", "RECORDS"), 300, 36, true);
            g.drawString(t("records.title", "RECORDS"), 40, 62);

            int streak = currentStreak();
            String[][] rows = {
                {t("records.runs", "Runs played"), String.valueOf(totalRuns)},
                {t("records.time", "Time played"), formatTime(playSeconds)},
                {t("records.kills", "Enemies destroyed"), String.valueOf(totalKills)},
                {t("records.bosses", "Bosses defeated"), String.valueOf(totalBosses)},
                {t("records.bestScore", "Best score"), String.valueOf(highScore)},
                {t("records.bestWave", "Best wave"), String.valueOf(bestWave)},
                {t("records.bestCombo", "Best combo"), String.valueOf(bestComboEver)},
                {t("records.streak", "Daily streak"), streak == 1 ? t("records.day", "1 day") : t("records.days", "{0} days", streak)},
                {t("records.dailyBest", "Daily best today"), String.valueOf(dailyBestToday())},
            };
            int y = 106;
            for (String[] row : rows) {
                drawRecordRow(g, row[0], row[1], y, new Color(190, 198, 235));
                y += 30;
            }
            y += 8;
            font(g, 14, true);
            g.setColor(new Color(170, 180, 220));
            g.drawString(t("records.byShip", "BEST WAVE BY SHIP"), 40, y);
            y += 26;
            for (Ship s : Ship.values()) {
                drawRecordRow(g, s.title, String.valueOf(shipBestWave[s.ordinal()]), y, s.color);
                y += 26;
            }

            // Achievements in two columns.
            Achievement[] all = Achievement.values();
            int rowsPerColumn = (all.length + 1) / 2;
            for (Achievement a : all) {
                int i = a.ordinal();
                int x = 360 + i / rowsPerColumn * 304, rowY = 78 + i % rowsPerColumn * 44;
                boolean done = achieved[i];
                g.setColor(done ? new Color(30, 44, 62) : new Color(20, 22, 48));
                g.fillRoundRect(x, rowY, 296, 40, 12, 12);
                g.setColor(done ? new Color(120, 240, 160) : new Color(60, 64, 100));
                g.fillOval(x + 9, rowY + 11, 16, 16);
                String reward = done ? t("records.earned", "EARNED") : "+" + a.reward + "  +" + a.diamonds;
                font(g, 12, true);
                int rewardWidth = g.getFontMetrics().stringWidth(reward) + (done ? 0 : 14);
                g.setColor(done ? new Color(120, 240, 160) : new Color(255, 220, 110));
                g.drawString(reward, x + 286 - rewardWidth, rowY + 17);
                if (!done) {
                    drawDiamond(g, x + 286 - 5, rowY + 12, 5);
                }
                g.setColor(done ? Color.WHITE : new Color(150, 155, 185));
                fitFont(g, label(a), 280 - 34 - rewardWidth, 14, true);
                g.drawString(label(a), x + 32, rowY + 17);
                g.setColor(new Color(170, 180, 220));
                fitFont(g, about(a), 256, 11, false);
                g.drawString(about(a), x + 32, rowY + 33);
            }

            // Which bosses you have beaten, and how often.
            int bossY = 78 + rowsPerColumn * 44 + 18;
            font(g, 14, true);
            g.setColor(new Color(170, 180, 220));
            g.drawString(t("records.bossList", "BOSSES"), 360, bossY);
            for (int i = 0; i < BOSSES.length; i++) {
                int x = 360 + i % 2 * 304, rowY = bossY + 22 + i / 2 * 22;
                boolean beaten = bossKills[i] > 0;
                g.setColor(beaten ? BOSSES[i].color : new Color(80, 84, 120));
                g.fillOval(x, rowY - 11, 12, 12);
                font(g, 13, beaten);
                String name = beaten || bestWave >= (i + 1) * 5 ? bossLabel(BOSSES[i]) : "???";
                g.drawString(name, x + 20, rowY);
                String count = "x" + bossKills[i];
                g.setColor(Color.WHITE);
                g.drawString(count, x + 290 - g.getFontMetrics().stringWidth(count), rowY);
            }
            g.setColor(new Color(130, 140, 180));
            fitCentered(g, t("records.back", "Click or press ESC to go back"), 688, 14, W - 60);
        }

        private void drawRecordRow(Graphics2D g, String label, String value, int y, Color labelColor) {
            font(g, 16, true);
            int valueWidth = g.getFontMetrics().stringWidth(value);
            g.setColor(Color.WHITE);
            g.drawString(value, 330 - valueWidth, y);
            g.setColor(labelColor);
            fitFont(g, label, 290 - valueWidth - 10, 16, false);
            g.drawString(label, 40, y);
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
                showNotice(t("hangar.equipped", "{0} equipped", label(k)));
            } else if (diamonds >= k.cost) {
                diamonds -= k.cost;
                ownedSkins[k.ordinal()] = true;
                skin = k;
                sound.play(Sound.Effect.UPGRADE);
                showNotice(t("notice.skinBought", "{0} unlocked and equipped!", label(k)));
            } else {
                sound.play(Sound.Effect.DENY);
                showNotice(t("notice.needDiamonds", "You need {0} more diamonds for {1}", k.cost - diamonds, label(k)));
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
                goBack();
            }
        }

        private void drawSkins(Graphics2D g) {
            g.setColor(Color.WHITE);
            fitFont(g, t("skins.title", "SKINS"), 160, 36, true);
            g.drawString(t("skins.title", "SKINS"), 40, 62);
            drawWallet(g, 60);
            g.setColor(new Color(170, 180, 220));
            String help = t("skins.help", "Skins recolor any ship. Showing: {0}  (arrow keys to switch)", ship.title);
            fitFont(g, help, 520, 14, false);
            g.drawString(help, 200, 58);

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
                fitCenteredIn(g, label(k), box, box.y + 148, 20, true);
                g.setColor(new Color(170, 180, 220));
                fitCenteredIn(g, about(k), box, box.y + 170, 12, false);
                if (equipped) {
                    g.setColor(color);
                    fitCenteredIn(g, t("skins.equipped", "EQUIPPED"), box, box.y + 208, 15, true);
                } else if (owned) {
                    g.setColor(new Color(150, 255, 170));
                    fitCenteredIn(g, t("skins.owned", "OWNED - click to equip"), box, box.y + 208, 14, true);
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

            drawSideButton(g, skinsBackButton(), t("common.back", "BACK  [ESC]"), t("common.backText", "Return to the last screen"),
                    new Color(170, 200, 255));
            if (noticeTimer > 0) {
                g.setColor(new Color(255, 225, 120));
                centeredAt(g, notice, 620, 645, 20, 660);
            } else {
                g.setColor(new Color(130, 140, 180));
                centeredAt(g, t("skins.earn", "Earn diamonds from bosses, achievements, daily runs and rare drops."), 620, 645, 15, 660);
            }
        }

        /** Text centered on x, shrunk to fit maxWidth. Used next to a back button. */
        private void centeredAt(Graphics2D g, String text, int x, int y, int size, int maxWidth) {
            fitFont(g, text, maxWidth, size, true);
            g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, y);
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
            fitCenteredIn(g, role(s), box, box.y + 118, 12, false);
            int statY = box.y + 130;
            drawStat(g, box.x + 14, statY, t("stat.hp", "HP"), s.health / 8.0, s.color);
            drawStat(g, box.x + 14, statY + 13, t("stat.speed", "SPD"), s.speed / 330.0, s.color);
            drawStat(g, box.x + 14, statY + 26, t("stat.damage", "DMG"), s.damage * s.shots / s.fireDelay / 11.0, s.color);
            g.setColor(new Color(200, 205, 235));
            fitCenteredIn(g, specialText(s), box, box.y + 181, 11, false);
            if (!open) {
                g.setColor(DIAMOND_COLOR);
                fitCenteredIn(g, t("hangar.shipCost", "{0} diamonds {1}", s.cost, unlockHint(s)), box, box.y + 198, 12, true);
            } else if (selected) {
                g.setColor(s.color);
                fitCenteredIn(g, t("hangar.selected", "SELECTED"), box, box.y + 198, 13, true);
            }
        }

        private void drawStat(Graphics2D g, int x, int y, String label, double value, Color color) {
            fitFont(g, label, 44, 10, true);
            g.setColor(new Color(150, 160, 200));
            g.drawString(label, x, y + 8);
            g.setColor(new Color(40, 44, 80));
            g.fillRect(x + 48, y + 1, 138, 7);
            g.setColor(color);
            g.fillRect(x + 48, y + 1, (int) (138 * clamp(value, 0.05, 1)), 7);
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
            fitCenteredIn(g, (d.ordinal() + 1) + ". " + label(d) + "  x" + d.scoreMultiplier, box, box.y + 28, 17, true);
        }

        private void drawPerkCard(Graphics2D g, Perk p) {
            Rectangle box = perkCard(p.ordinal());
            int level = perks[p.ordinal()];
            boolean maxed = level >= p.max;
            boolean hover = box.contains(mouseX, mouseY);
            boolean affordable = !maxed && coins >= p.costAt(level);
            g.setColor(hover && !maxed ? new Color(30, 34, 70) : maxed ? new Color(20, 34, 40) : new Color(20, 22, 48));
            g.fillRoundRect(box.x, box.y, box.width, box.height, 14, 14);
            g.setColor(maxed ? new Color(120, 240, 160) : affordable ? new Color(255, 220, 110) : new Color(70, 76, 120));
            g.setStroke(new BasicStroke(hover ? 2.5f : 1.2f));
            g.drawRoundRect(box.x, box.y, box.width, box.height, 14, 14);
            g.setStroke(new BasicStroke(1));
            g.setColor(Color.WHITE);
            fitCenteredIn(g, label(p), box, box.y + 24, 16, true);
            g.setColor(new Color(170, 180, 220));
            font(g, 12, false);
            List<String> lines = wrap(g, about(p), box.width - 16);
            for (int i = 0; i < lines.size() && i < 2; i++) {
                centeredIn(g, lines.get(i), box, box.y + 43 + i * 15);
            }
            for (int i = 0; i < p.max; i++) {
                int dotX = (int) box.getCenterX() - p.max * 9 + i * 18 + 2;
                g.setColor(i < level ? new Color(120, 240, 160) : new Color(50, 54, 90));
                g.fillOval(dotX, box.y + 74, 13, 13);
            }
            if (maxed) {
                g.setColor(new Color(120, 240, 160));
                fitCenteredIn(g, t("workshop.maxed", "MAXED"), box, box.y + 110, 14, true);
            } else {
                int cost = p.costAt(level);
                String price = String.valueOf(cost);
                font(g, 15, true);
                int width = g.getFontMetrics().stringWidth(price) + 20;
                int left = (int) box.getCenterX() - width / 2;
                drawCoin(g, left + 6, box.y + 105, 7);
                g.setColor(affordable ? new Color(255, 220, 110) : new Color(150, 110, 110));
                g.drawString(price, left + 18, box.y + 110);
            }
        }

        private void drawWorkshop(Graphics2D g) {
            g.setColor(Color.WHITE);
            fitFont(g, t("workshop.title", "WORKSHOP"), 360, 36, true);
            g.drawString(t("workshop.title", "WORKSHOP"), 40, 62);
            drawWallet(g, 60);
            g.setColor(new Color(170, 180, 220));
            String help = t("workshop.help", "Permanent upgrades. They last forever and help in every run.");
            fitFont(g, help, 400, 14, false);
            g.drawString(help, 40, 82);
            for (Perk p : Perk.values()) {
                drawPerkCard(g, p);
            }
            drawSideButton(g, workshopBackButton(), t("common.back", "BACK  [ESC]"), t("common.backText", "Return to the last screen"),
                    new Color(170, 200, 255));
            if (noticeTimer > 0) {
                g.setColor(new Color(255, 225, 120));
                centeredAt(g, notice, 620, 655, 20, 660);
            } else {
                g.setColor(new Color(130, 140, 180));
                centeredAt(g, t("workshop.earn", "Click an upgrade to buy its next level. Coins come from every run."), 620, 655, 15, 660);
            }
        }

        // ========================== SETTINGS SCREEN =========================

        String optionLabel(int option) {
            switch (option) {
                case OPT_SOUND: return t("option.sound", "Sound effects");
                case OPT_SOUND_VOLUME: return t("option.soundVolume", "Sound volume");
                case OPT_MUSIC: return t("option.music", "Music");
                case OPT_MUSIC_VOLUME: return t("option.musicVolume", "Music volume");
                case OPT_SHAKE: return t("option.shake", "Screen shake");
                case OPT_NUMBERS: return t("option.numbers", "Damage numbers");
                case OPT_AUTO_FIRE: return t("option.autoFire", "Auto-fire");
                default: return t("option.fullscreen", "Fullscreen");
            }
        }

        /** The value shown for an option, and how full its bar is (or -1 for no bar). */
        String optionValue(int option) {
            String on = t("option.on", "ON"), off = t("option.off", "OFF");
            switch (option) {
                case OPT_SOUND: return sound.effectsOn ? on : off;
                case OPT_SOUND_VOLUME: return sound.effectsVolume + "%";
                case OPT_MUSIC: return sound.musicOn ? on : off;
                case OPT_MUSIC_VOLUME: return sound.musicVolume + "%";
                case OPT_SHAKE:
                    return shakeAmount == 0 ? off : shakeAmount < 100 ? t("option.low", "LOW") : t("option.full", "FULL");
                case OPT_NUMBERS: return damageNumbers ? on : off;
                case OPT_AUTO_FIRE: return autoFire ? on : off;
                default: return fullscreen ? on : off;
            }
        }

        double optionFill(int option) {
            switch (option) {
                case OPT_SOUND_VOLUME: return sound.effectsVolume / 100.0;
                case OPT_MUSIC_VOLUME: return sound.musicVolume / 100.0;
                case OPT_SHAKE: return shakeAmount / 100.0;
                default: return -1;
            }
        }

        private void drawSettings(Graphics2D g) {
            g.setColor(Color.WHITE);
            fitFont(g, t("settings.title", "SETTINGS"), 200, 34, true);
            g.drawString(t("settings.title", "SETTINGS"), 40, 54);
            String[] tabs = {t("settings.general", "General"), t("settings.controls", "Controls"), t("settings.language", "Language")};
            for (int i = 0; i < tabs.length; i++) {
                drawButton(g, settingsTabButton(i), tabs[i], MENU_ACCENT, i == settingsTab);
            }
            if (settingsTab == TAB_GENERAL) {
                drawGeneralTab(g);
            } else if (settingsTab == TAB_CONTROLS) {
                drawControlsTab(g);
            } else {
                drawLanguageTab(g);
            }
            drawButton(g, settingsBackButton(), t("settings.back", "BACK  [ESC]"), new Color(170, 200, 255), false);
            g.setColor(new Color(130, 140, 180));
            String hint = settingsTab == TAB_CONTROLS
                    ? t("settings.controlsHint", "Click a slot, then press a key. DELETE clears it.")
                    : t("settings.hint", "TAB: next tab    Arrows: choose and change    ENTER: select");
            int hintRight = settingsTab == TAB_CONTROLS ? 650 : 960;
            fitFont(g, hint, hintRight - 280, 14, false);
            g.drawString(hint, 280, 659);
            if (noticeTimer > 0) {
                g.setColor(new Color(255, 225, 120));
                fitFont(g, notice, hintRight - 280, 15, true);
                g.drawString(notice, 280, 640);
            }
        }

        private void drawGeneralTab(Graphics2D g) {
            for (int i = 0; i < OPTION_COUNT; i++) {
                Rectangle row = optionRow(i);
                boolean selected = i == settingsRow, hover = row.contains(mouseX, mouseY);
                g.setColor(selected ? new Color(32, 40, 84) : hover ? new Color(26, 30, 62) : new Color(18, 20, 44));
                g.fillRoundRect(row.x, row.y, row.width, row.height, 14, 14);
                if (selected) {
                    g.setColor(MENU_ACCENT);
                    g.setStroke(new BasicStroke(2));
                    g.drawRoundRect(row.x, row.y, row.width, row.height, 14, 14);
                    g.setStroke(new BasicStroke(1));
                }
                g.setColor(Color.WHITE);
                fitFont(g, optionLabel(i), 330, 19, true);
                g.drawString(optionLabel(i), row.x + 24, row.y + 33);

                // Left half of the value area turns the option down, the right half turns it up.
                int valueLeft = row.x + row.width - 300, valueRight = row.x + row.width - 20;
                double fill = optionFill(i);
                if (fill >= 0) {
                    drawBar(g, valueLeft + 32, row.y + 21, 130, fill, MENU_ACCENT);
                }
                String value = optionValue(i);
                boolean lit = value.equals(t("option.on", "ON"));
                g.setColor(lit ? new Color(120, 240, 160) : value.equals(t("option.off", "OFF")) ? new Color(255, 120, 130) : Color.WHITE);
                fitFont(g, value, fill >= 0 ? 80 : 200, 18, true);
                int valueX = fill >= 0 ? valueRight - 22 - g.getFontMetrics().stringWidth(value)
                        : (valueLeft + valueRight - g.getFontMetrics().stringWidth(value)) / 2;
                g.drawString(value, valueX, row.y + 33);
                g.setColor(selected || hover ? MENU_ACCENT : new Color(90, 100, 150));
                font(g, 22, true);
                g.drawString("<", valueLeft, row.y + 34);
                g.drawString(">", valueRight - 12, row.y + 34);
            }
        }

        private void drawControlsTab(Graphics2D g) {
            font(g, 11, true);
            g.setColor(new Color(130, 140, 180));
            g.drawString(t("controls.action", "ACTION"), 56, 73);
            g.drawString(t("controls.key1", "KEY 1"), 510, 73);
            g.drawString(t("controls.key2", "KEY 2"), 740, 73);
            Action[] actions = Action.values();
            for (int i = 0; i < actions.length + 2; i++) {
                Rectangle row = keyRow(i);
                boolean selectedRow = i == settingsRow;
                g.setColor(selectedRow ? new Color(28, 34, 72) : i % 2 == 0 ? new Color(18, 20, 44) : new Color(22, 24, 50));
                g.fillRoundRect(row.x, row.y, row.width, row.height, 10, 10);
                String name;
                if (i < actions.length) {
                    name = label(actions[i]);
                    if (actions[i] == Action.FIRE) {
                        name += "  (" + t("controls.leftMouse", "+ left mouse button") + ")";
                    }
                } else {
                    name = i == actions.length ? t("controls.rightMouse", "Right mouse button") : t("controls.middleMouse", "Middle mouse button");
                }
                g.setColor(selectedRow ? Color.WHITE : new Color(190, 198, 235));
                fitFont(g, name, 430, 15, selectedRow);
                g.drawString(name, row.x + 16, row.y + 21);

                if (i >= actions.length) {
                    Rectangle slot = keySlot(i, 0);
                    drawKeySlot(g, slot, "<   " + label(i == actions.length ? rightClick : middleClick) + "   >", selectedRow, false);
                    continue;
                }
                for (int s = 0; s < 2; s++) {
                    Rectangle slot = keySlot(i, s);
                    boolean waiting = capturing == actions[i] && captureSlot == s;
                    String text = waiting ? t("controls.press", "PRESS A KEY...") : keyName(keys[i][s]);
                    drawKeySlot(g, slot, text, selectedRow && settingsSlot == s, waiting);
                }
            }
            Rectangle reset = resetKeysButton();
            drawButton(g, reset, t("controls.reset", "RESET TO DEFAULTS"), new Color(255, 150, 120), false);
        }

        private void drawKeySlot(Graphics2D g, Rectangle slot, String text, boolean selected, boolean waiting) {
            boolean hover = slot.contains(mouseX, mouseY);
            if (waiting) {
                alpha(g, 0.6f + 0.4f * (float) Math.abs(Math.sin(clock * 6)));
                g.setColor(new Color(255, 200, 80));
            } else {
                g.setColor(hover ? new Color(50, 58, 110) : selected ? new Color(44, 52, 104) : new Color(32, 36, 72));
            }
            g.fillRoundRect(slot.x, slot.y, slot.width, slot.height, 8, 8);
            alpha(g, 1);
            if (selected || hover) {
                g.setColor(MENU_ACCENT);
                g.drawRoundRect(slot.x, slot.y, slot.width, slot.height, 8, 8);
            }
            g.setColor(waiting ? new Color(40, 22, 5) : text.equals("---") ? new Color(110, 115, 150) : Color.WHITE);
            fitCenteredIn(g, text, slot, slot.y + slot.height / 2 + 5, 14, true);
        }

        private void drawLanguageTab(Graphics2D g) {
            for (Language l : Language.values()) {
                Rectangle card = languageCard(l.ordinal());
                boolean chosen = l == language, selected = l.ordinal() == settingsRow, hover = card.contains(mouseX, mouseY);
                g.setColor(chosen ? new Color(34, 48, 96) : hover || selected ? new Color(28, 32, 68) : new Color(18, 20, 44));
                g.fillRoundRect(card.x, card.y, card.width, card.height, 18, 18);
                g.setColor(chosen ? new Color(120, 240, 160) : selected || hover ? MENU_ACCENT : new Color(60, 66, 110));
                g.setStroke(new BasicStroke(chosen || selected ? 3 : 1.2f));
                g.drawRoundRect(card.x, card.y, card.width, card.height, 18, 18);
                g.setStroke(new BasicStroke(1));
                font(g, 13, true);
                g.setColor(new Color(130, 140, 180));
                g.drawString(l.code.toUpperCase(), card.x + 16, card.y + 26);
                g.setColor(Color.WHITE);
                fitCenteredIn(g, l.nativeName, card, card.y + 74, 28, true);
                g.setColor(new Color(170, 180, 220));
                fitCenteredIn(g, l.englishName, card, card.y + 100, 14, false);
                if (chosen) {
                    g.setColor(new Color(120, 240, 160));
                    g.fillOval(card.x + card.width - 36, card.y + 12, 22, 22);
                    g.setColor(new Color(15, 40, 25));
                    g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = card.x + card.width - 25, cy = card.y + 23;
                    g.drawPolyline(new int[] {cx - 6, cx - 1, cx + 6}, new int[] {cy, cy + 5, cy - 5}, 3);
                    g.setStroke(new BasicStroke(1));
                }
            }
        }

        private void drawGameOver(Graphics2D g) {
            dim(g, 0.7f);
            g.setColor(new Color(255, 90, 120));
            fitCentered(g, dailyRun ? t("over.daily", "DAILY RUN OVER") : t("over.title", "GAME OVER"), 170, dailyRun ? 60 : 74, W - 60);
            g.setColor(Color.WHITE);
            fitCentered(g, t("over.score", "Score: {0}", score), 236, 38, W - 60);
            if (newRecord) {
                g.setColor(new Color(255, 225, 90));
                fitCentered(g, dailyRun ? t("over.bestToday", "NEW BEST TODAY!") : t("over.record", "NEW RECORD!"), 274, 26, W - 60);
            } else {
                g.setColor(new Color(170, 180, 220));
                fitCentered(g, dailyRun ? t("over.dailyBest", "Best today: {0}", dailyBestToday()) : t("over.best", "Best: {0}", highScore), 274, 22, W - 60);
            }
            g.setColor(new Color(190, 198, 235));
            String setup = dailyRun ? t("hud.daily", "DAILY: {0}", label(modifier)) : label(difficulty);
            fitCentered(g, ship.title + "  |  " + setup + "  |  " + t("over.wave", "Reached wave {0}", wave), 322, 21, W - 60);
            fitCentered(g, t("over.kills", "Kills: {0}     Best combo: {1}     Bosses: {2}", kills, bestCombo, runBosses), 354, 21, W - 60);
            fitCentered(g, t("over.time", "Time: {0}     Damage dealt: {1}", formatTime(runTime), Math.round(runDamage)), 386, 21, W - 60);
            g.setColor(new Color(255, 220, 110));
            fitCentered(g, t("over.coins", "+{0} coins  (total {1})", runCoins, coins), 424, 24, W - 60);
            g.setColor(DIAMOND_COLOR);
            fitCentered(g, t("over.diamonds", "+{0} diamonds  (total {1})", runDiamonds, diamonds), 448, 18, W - 60);
            if (!dailyNote.isEmpty()) {
                g.setColor(new Color(120, 220, 255));
                fitCentered(g, dailyNote, 470, 18, W - 60);
            }
            if (runAchievements > 0) {
                g.setColor(new Color(255, 200, 80));
                fitCentered(g, t("over.achievements", "Achievements earned: {0}", runAchievements), 496, 18, W - 60);
            }
            if (overDelay <= 0 && (int) (clock * 2) % 2 == 0) {
                g.setColor(new Color(150, 255, 170));
                fitCentered(g, t("over.continue", "Click or press ENTER for the hangar"), 548, 28, W - 60);
            }
        }

        static double clamp(double value, double low, double high) {
            return Math.max(low, Math.min(high, value));
        }
    }
}
