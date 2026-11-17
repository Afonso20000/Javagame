import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Random;
import javax.imageio.ImageIO;

/** Checks for the bug fixes, game feel, daily run, achievements and records. Also takes screenshots. */
public class NewFeaturesCheck {
    static final double DT = 1 / 60.0;
    static int failures;
    static String dir;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }

    static NeonOverdrive.GamePanel panel(long seed) {
        NeonOverdrive.GamePanel p = new NeonOverdrive.GamePanel();
        p.saveFile = null;
        p.rng = new Random(seed);
        return p;
    }

    static NeonOverdrive.GamePanel game(long seed) {
        NeonOverdrive.GamePanel p = panel(seed);
        p.startGame();
        p.spawnQueue.clear();
        return p;
    }

    static void run(NeonOverdrive.GamePanel p, double seconds) {
        for (int i = 0; i < seconds * 60; i++) p.update(DT);
    }

    static void shot(NeonOverdrive.GamePanel p, String name) throws Exception {
        BufferedImage img = new BufferedImage(NeonOverdrive.W, NeonOverdrive.H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        p.render(g);
        g.dispose();
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }

    static NeonOverdrive.Enemy add(NeonOverdrive.GamePanel p, NeonOverdrive.EnemyType t, double x, double y, double hp) {
        NeonOverdrive.Enemy e = new NeonOverdrive.Enemy(t, x, y, hp);
        p.enemies.add(e);
        return e;
    }

    static void click(NeonOverdrive.GamePanel p, java.awt.Rectangle r) {
        p.handleClick(r.x + r.width / 2, r.y + r.height / 2);
    }

    public static void main(String[] args) throws Exception {
        dir = args[0];
        new File(dir).mkdirs();
        File saveTest = new File(dir, "new-save-test.properties");

        // ---------- Bug fixes ----------
        saveTest.delete();
        NeonOverdrive.GamePanel quit = game(1);
        quit.saveFile = saveTest;
        quit.runCoins = 42;
        quit.score = 999;
        quit.saveRun();
        quit.saveOnExit();
        NeonOverdrive.GamePanel quit2 = panel(1);
        quit2.saveFile = saveTest;
        quit2.loadSave();
        quit2.refreshSavedRun();
        check(quit2.hasSavedRun() && quit2.coins == 0, "closing the window mid-run keeps the run to continue later");
        quit2.startGame();
        check(quit2.coins == 42 && quit2.highScore == 999 && quit2.totalRuns == 1,
                "starting a new run instead still keeps the saved run's coins and score");
        quit2.saveFile = null;
        quit2.endRun();
        check(quit2.coins == 42 && quit2.totalRuns == 2, "the saved run can never be counted twice");
        new File(dir, "new-save-test_run.properties").delete();

        saveTest.delete();
        NeonOverdrive.GamePanel opt = panel(2);
        opt.saveFile = saveTest;
        opt.handleKey(KeyEvent.VK_M, true);
        opt.handleKey(KeyEvent.VK_N, true);
        opt.handleKey(KeyEvent.VK_F, true);
        NeonOverdrive.GamePanel opt2 = panel(2);
        opt2.saveFile = saveTest;
        opt2.loadSave();
        check(!opt2.sound.effectsOn && !opt2.sound.musicOn && opt2.autoFire, "sound, music and auto-fire settings are remembered");

        NeonOverdrive.GamePanel dr = panel(3);
        dr.ship = NeonOverdrive.Ship.BLASTER;
        dr.startGame();
        dr.spawnQueue.clear();
        dr.applyUpgrade(NeonOverdrive.Upgrade.DRONE);
        NeonOverdrive.Enemy farTarget = add(dr, NeonOverdrive.EnemyType.TANK, dr.px + 430, dr.py, 500);
        run(dr, 2.5);
        check(farTarget.hp < 500, "Blaster drones can reach targets at the edge of their aim");

        NeonOverdrive.GamePanel sc = game(4);
        sc.wave = 15;
        NeonOverdrive.Enemy boss = add(sc, NeonOverdrive.EnemyType.OVERLORD, 800, 200, 5000);
        boss.bossStep = 2;
        boss.attackTimer = 0;
        sc.invulnerable = 99;
        sc.update(DT);
        sc.update(DT);
        boolean tougher = false;
        for (NeonOverdrive.Enemy e : sc.enemies) tougher |= e.type == NeonOverdrive.EnemyType.CHASER && e.hp > NeonOverdrive.EnemyType.CHASER.hp;
        check(tougher, "boss minions get tougher in later waves");
        NeonOverdrive.GamePanel sm = game(5);
        sm.wave = 15;
        NeonOverdrive.Enemy splitter = add(sm, NeonOverdrive.EnemyType.SPLITTER, 800, 500, 1);
        sm.damage(splitter, 5, false);
        sm.update(DT);
        boolean miniTougher = false;
        for (NeonOverdrive.Enemy e : sm.enemies) miniTougher |= e.type == NeonOverdrive.EnemyType.MINI && e.hp > NeonOverdrive.EnemyType.MINI.hp;
        check(miniTougher, "splitter minis get tougher in later waves");

        // Enemies sitting right on top of the ship can be shot (a wave could never end before this).
        for (NeonOverdrive.EnemyType type : new NeonOverdrive.EnemyType[] {NeonOverdrive.EnemyType.MINI, NeonOverdrive.EnemyType.CHASER}) {
            NeonOverdrive.GamePanel pb = game(30);
            NeonOverdrive.Enemy onTop = add(pb, type, pb.px + 1, pb.py, 1);
            pb.invulnerable = 99;
            pb.aimX = pb.px + 1;
            pb.aimY = pb.py;
            pb.firing = true;
            for (int i = 0; i < 30 && !onTop.dead; i++) {
                onTop.x = pb.px + 1;
                onTop.y = pb.py;
                pb.update(DT);
            }
            check(onTop.dead, "point-blank shots hit a " + type);
        }
        // A very slow frame must not let a bullet jump over a thin target.
        NeonOverdrive.GamePanel sw = game(31);
        NeonOverdrive.Enemy thin = add(sw, NeonOverdrive.EnemyType.MINI, 800, 350, 500);
        NeonOverdrive.Bullet fast = new NeonOverdrive.Bullet();
        fast.x = 750;
        fast.y = 350;
        fast.sweepX = 750;
        fast.sweepY = 350;
        fast.vx = 6800;
        fast.r = 2;
        fast.damage = 1;
        fast.life = 1;
        fast.color = java.awt.Color.WHITE;
        sw.bullets.add(fast);
        sw.update(0.02);
        check(thin.hp < 500, "fast bullets cannot skip through an enemy");

        // ---------- A damaged save only loses the damaged line ----------
        java.nio.file.Files.write(saveTest.toPath(), "coins=banana\nhighScore=500\nselectedShip=NOPE\n".getBytes());
        NeonOverdrive.GamePanel bad = panel(6);
        bad.saveFile = saveTest;
        bad.highScore = 0;
        bad.loadSave();
        check(bad.highScore == 500, "one damaged line in the save does not lose the rest");
        saveTest.delete();

        // An old save, from before this update, still loads.
        java.nio.file.Files.write(saveTest.toPath(), "coins=70\nhighScore=10\nship.TITAN=true\nselectedShip=TITAN\ndifficulty=HARD\nperk.HULL=2\n".getBytes());
        NeonOverdrive.GamePanel old = panel(6);
        old.saveFile = saveTest;
        old.loadSave();
        check(old.coins == 70 && old.ship == NeonOverdrive.Ship.TITAN && old.perks[0] == 2 && old.sound.effectsOn && old.sound.musicOn && !old.autoFire,
                "a save from the old version loads with safe defaults");
        saveTest.delete();

        // ---------- Game feel ----------
        NeonOverdrive.GamePanel f = game(7);
        NeonOverdrive.Enemy tank = add(f, NeonOverdrive.EnemyType.TANK, 700, 350, 500);
        f.aimX = 700;
        f.aimY = 350;
        f.firing = true;
        boolean sawDamageText = false, pushed = false;
        for (int i = 0; i < 90; i++) {
            f.update(DT);
            pushed |= Math.abs(tank.pushX) > 1;
            for (NeonOverdrive.FloatText t : f.texts) sawDamageText |= t.text.matches("\\d+");
        }
        f.firing = false;
        check(sawDamageText, "hits show damage numbers");
        check(pushed, "bullets knock enemies back");
        NeonOverdrive.GamePanel fb = game(8);
        NeonOverdrive.Enemy heavy = add(fb, NeonOverdrive.EnemyType.WARDEN, 800, 200, 5000);
        fb.aimX = 800;
        fb.aimY = 200;
        fb.firing = true;
        run(fb, 1);
        check(heavy.pushX == 0 && heavy.pushY == 0, "bosses are not knocked back");

        NeonOverdrive.GamePanel k = game(9);
        NeonOverdrive.Enemy victim = add(k, NeonOverdrive.EnemyType.CHASER, 800, 500, 1);
        k.damage(victim, 5, false);
        boolean shards = false, flash = false;
        for (NeonOverdrive.Particle p : k.particles) shards |= p.shard;
        for (NeonOverdrive.Ring r : k.rings) flash |= r.flash;
        check(shards && flash, "kills make shards and a flash");

        NeonOverdrive.GamePanel t = game(10);
        t.aimX = 900;
        t.aimY = t.py;
        t.update(DT);
        t.tryDash();
        for (int i = 0; i < 4; i++) t.update(DT);
        check(!t.afterimages.isEmpty(), "dashing leaves a trail");
        shot(t, "20-dash-trail");
        run(t, 1);
        check(t.afterimages.isEmpty(), "the dash trail fades away");

        // ---------- Daily run ----------
        NeonOverdrive.GamePanel d = panel(11);
        Arrays.fill(d.unlocked, false);
        d.unlocked[0] = true;
        d.ship = NeonOverdrive.Ship.STRIKER;
        d.difficulty = NeonOverdrive.Difficulty.HARD;
        d.dateOverride = LocalDate.of(2026, 11, 4);
        NeonOverdrive.Ship dailyA = d.dailyShip();
        NeonOverdrive.Modifier modA = d.dailyModifier();
        NeonOverdrive.GamePanel d2 = panel(99);
        d2.dateOverride = LocalDate.of(2026, 11, 4);
        check(d2.dailyShip() == dailyA && d2.dailyModifier() == modA, "the same date gives the same daily");
        boolean variety = false;
        boolean[] seenMods = new boolean[NeonOverdrive.Modifier.values().length];
        boolean[] seenShips = new boolean[NeonOverdrive.Ship.values().length];
        for (int i = 0; i < 365; i++) {
            d2.dateOverride = LocalDate.of(2026, 1, 1).plusDays(i);
            seenMods[d2.dailyModifier().ordinal()] = true;
            seenShips[d2.dailyShip().ordinal()] = true;
            variety |= d2.dailyModifier() != modA;
        }
        boolean allMods = true, allShips = true;
        for (int i = 1; i < seenMods.length; i++) allMods &= seenMods[i];
        for (boolean s : seenShips) allShips &= s;
        check(variety && allMods && allShips && !seenMods[0], "over a year every ship and modifier appears, and never NONE");

        d.state = NeonOverdrive.State.HANGAR;
        d.handleKey(KeyEvent.VK_G, true);
        check(d.state == NeonOverdrive.State.PLAYING && d.dailyRun && d.ship == dailyA && d.difficulty == NeonOverdrive.Difficulty.NORMAL, "G starts the daily with its own ship, on Normal");
        d.score = 500;
        d.runCoins = 3;
        d.highScore = 100;
        d.endRun();
        check(d.highScore == 100 && d.dailyBest == 500 && d.newRecord, "the daily has its own record");
        check(d.dailyStreak == 1 && d.coins >= 3 + 25, "first daily of the day pays a bonus");
        int afterFirst = d.coins;
        d.goToHangar();
        check(d.ship == NeonOverdrive.Ship.STRIKER && d.difficulty == NeonOverdrive.Difficulty.HARD && !d.dailyRun && d.modifier == NeonOverdrive.Modifier.NONE, "your own ship and difficulty come back");
        check(d.achieved[NeonOverdrive.Achievement.DAILY_PILOT.ordinal()], "finishing a daily earns Daily Pilot");

        d.startDaily();
        d.score = 50;
        d.endRun();
        check(d.dailyBest == 500 && d.dailyStreak == 1 && d.coins == afterFirst, "a second try the same day gives no extra bonus");
        d.goToHangar();
        d.dateOverride = LocalDate.of(2026, 11, 5);
        check(d.currentStreak() == 1 && d.dailyBestToday() == 0, "next day: the streak is alive and the daily best is fresh");
        d.startDaily();
        d.endRun();
        d.goToHangar();
        check(d.dailyStreak == 2, "playing the next day grows the streak");
        d.dateOverride = LocalDate.of(2026, 11, 20);
        check(d.currentStreak() == 0, "missing days ends the streak");
        d.startDaily();
        d.endRun();
        d.goToHangar();
        check(d.dailyStreak == 1, "the streak restarts after a gap");

        // A daily run must not save the borrowed ship.
        saveTest.delete();
        NeonOverdrive.GamePanel ds = panel(12);
        ds.saveFile = saveTest;
        Arrays.fill(ds.unlocked, true);
        ds.ship = dailyA == NeonOverdrive.Ship.STRIKER ? NeonOverdrive.Ship.TITAN : NeonOverdrive.Ship.STRIKER;
        NeonOverdrive.Ship own = ds.ship;
        ds.dateOverride = LocalDate.of(2026, 11, 4);
        ds.startDaily();
        ds.handleKey(KeyEvent.VK_M, true);
        ds.saveOnExit();
        NeonOverdrive.GamePanel ds2 = panel(12);
        ds2.saveFile = saveTest;
        ds2.loadSave();
        check(ds2.ship == own, "closing during a daily keeps your own ship selected");
        saveTest.delete();
        new File(dir, "new-save-test_run.properties").delete();

        // Every modifier does what it says.
        NeonOverdrive.GamePanel base = game(13);
        int baseHealth = base.maxHealth, baseBombs = base.bombs;
        double baseDamage = base.bulletDamage;
        for (NeonOverdrive.Modifier m : NeonOverdrive.Modifier.values()) {
            if (m == NeonOverdrive.Modifier.NONE) continue;
            NeonOverdrive.GamePanel g = panel(14);
            g.dailyRun = true;
            g.modifier = m;
            g.startGame();
            boolean ok;
            switch (m) {
                case GLASS_ARENA: ok = g.maxHealth == baseHealth - 2 && Math.abs(g.bulletDamage - baseDamage * 1.6) < 0.001; break;
                case BULLET_STORM: ok = g.bombs == baseBombs + 3; break;
                case OVERCLOCK: ok = Math.abs(g.bulletDamage - baseDamage * 1.25) < 0.001; break;
                case BOMB_BONANZA: ok = g.bombs == baseBombs + 5; break;
                case SWARM: ok = g.spawnQueue.size() == 13; break;
                default: ok = true; break;
            }
            check(ok, "modifier " + m + " sets up the run");
        }
        NeonOverdrive.GamePanel hunt = panel(15);
        hunt.dailyRun = true;
        hunt.modifier = NeonOverdrive.Modifier.ELITE_HUNT;
        hunt.startGame();
        boolean sawElite = false;
        for (int i = 0; i < 60 * 25; i++) {
            hunt.invulnerable = 99;
            hunt.update(DT);
            for (NeonOverdrive.Enemy e : hunt.enemies) sawElite |= e.elite;
        }
        check(sawElite && hunt.wave == 1, "Elite Hunt puts elites in wave 1");
        NeonOverdrive.GamePanel normal = game(16);
        normal.nextWave();
        check(normal.modifier == NeonOverdrive.Modifier.NONE, "normal runs have no modifier");

        // Bot plays one run of each modifier, to shake out crashes.
        for (NeonOverdrive.Modifier m : NeonOverdrive.Modifier.values()) {
            NeonOverdrive.GamePanel g = panel(200 + m.ordinal());
            g.dailyRun = m != NeonOverdrive.Modifier.NONE;
            g.modifier = m;
            g.startGame();
            for (int frames = 0; frames < 60 * 60 * 5 && g.state != NeonOverdrive.State.GAME_OVER; frames++) {
                if (g.state == NeonOverdrive.State.UPGRADE) {
                    g.update(0.4);
                    g.chooseCard(g.rng.nextInt(g.choices.size()));
                    continue;
                }
                g.autoFire = true;
                g.invulnerable = 99;
                double nearest = 1e9;
                for (NeonOverdrive.Enemy e : g.enemies) {
                    double dist = Math.hypot(e.x - g.px, e.y - g.py);
                    if (!e.dead && e.spawnTimer <= 0 && dist < nearest) {
                        nearest = dist;
                        g.aimX = e.x;
                        g.aimY = e.y;
                    }
                }
                g.update(DT);
            }
            System.out.println("modifier " + m + " reached wave " + g.wave);
            check(g.wave >= 6, "a bot plays through bosses with modifier " + m + " without crashing");
        }

        // ---------- Achievements and ship unlocks ----------
        NeonOverdrive.GamePanel a = game(17);
        a.wave = 9;
        a.nextWave();
        check(a.achieved[NeonOverdrive.Achievement.DEEP_RUN.ordinal()] && a.coins == 30, "reaching wave 10 pays out once");
        check(a.unlocked[NeonOverdrive.Ship.PHANTOM.ordinal()], "reaching wave 8 earns the Phantom");
        a.wave = 9;
        a.nextWave();
        check(a.coins == 30, "an achievement only pays once");
        run(a, 0.1);
        check(a.toastTimer > 0 && !a.toastText.isEmpty(), "achievements show a pop-up");
        shot(a, "21-toast");

        NeonOverdrive.GamePanel b = game(18);
        NeonOverdrive.Enemy serpent = add(b, NeonOverdrive.EnemyType.SERPENT, 800, 200, 1);
        b.damage(serpent, 5, false);
        check(b.bossKills[1] == 1 && b.achieved[NeonOverdrive.Achievement.BOSS_SLAYER.ordinal()] && b.achieved[NeonOverdrive.Achievement.FLAWLESS.ordinal()], "killing a boss unhurt earns Boss Slayer and Flawless");
        check(b.unlocked[NeonOverdrive.Ship.TITAN.ordinal()], "beating the Serpent earns the Titan");
        NeonOverdrive.GamePanel b2 = game(19);
        b2.health = 3;
        add(b2, NeonOverdrive.EnemyType.CHASER, b2.px, b2.py, 50);
        b2.update(DT);
        NeonOverdrive.Enemy boss2 = add(b2, NeonOverdrive.EnemyType.WARDEN, 800, 200, 1);
        b2.damage(boss2, 5, false);
        check(!b2.achieved[NeonOverdrive.Achievement.FLAWLESS.ordinal()], "taking a hit that wave denies Flawless");
        NeonOverdrive.GamePanel b3 = panel(20);
        b3.difficulty = NeonOverdrive.Difficulty.HARD;
        b3.startGame();
        b3.damage(add(b3, NeonOverdrive.EnemyType.OVERLORD, 800, 200, 1), 5, false);
        check(b3.achieved[NeonOverdrive.Achievement.NO_MERCY.ordinal()], "a Hard boss kill earns No Mercy");

        NeonOverdrive.GamePanel c = game(21);
        c.bestComboEver = 49;
        c.combo = 49;
        c.damage(add(c, NeonOverdrive.EnemyType.MINI, 900, 650, 1), 5, false);
        check(c.achieved[NeonOverdrive.Achievement.RAMPAGE.ordinal()], "a 50 combo earns Rampage");

        // Records survive a save round trip.
        saveTest.delete();
        NeonOverdrive.GamePanel r1 = panel(22);
        r1.saveFile = saveTest;
        r1.totalRuns = 7;
        r1.totalKills = 1234;
        r1.bestWave = 21;
        r1.playSeconds = 3725;
        r1.shipBestWave[2] = 9;
        r1.bossKills[1] = 2;
        r1.achieved[3] = true;
        r1.dailyLastDate = "2026-11-04";
        r1.dailyBest = 777;
        r1.dailyStreak = 4;
        r1.save();
        NeonOverdrive.GamePanel r2 = panel(22);
        r2.saveFile = saveTest;
        r2.loadSave();
        check(r2.totalRuns == 7 && r2.totalKills == 1234 && r2.bestWave == 21 && r2.playSeconds == 3725 && r2.shipBestWave[2] == 9
                && r2.bossKills[1] == 2 && r2.achieved[3] && r2.dailyBest == 777 && r2.dailyStreak == 4 && "2026-11-04".equals(r2.dailyLastDate),
                "records, achievements and the daily streak are saved");
        saveTest.delete();

        // ---------- Screens ----------
        NeonOverdrive.GamePanel s = panel(23);
        s.state = NeonOverdrive.State.HANGAR;
        s.coins = 180;
        shot(s, "22-hangar");
        click(s, s.recordsButton());
        check(s.state == NeonOverdrive.State.RECORDS, "records button opens the records");
        s.totalRuns = 12;
        s.totalKills = 1860;
        s.totalBosses = 5;
        s.bestWave = 17;
        s.bestComboEver = 62;
        s.highScore = 48210;
        s.playSeconds = 4100;
        s.shipBestWave[0] = 17;
        s.shipBestWave[1] = 11;
        s.dailyLastDate = s.today().toString();
        s.dailyBest = 9120;
        s.dailyStreak = 2;
        for (int i = 0; i < 5; i++) s.achieved[i] = true;
        shot(s, "23-records");
        s.handleKey(KeyEvent.VK_ESCAPE, true);
        check(s.state == NeonOverdrive.State.HANGAR, "ESC leaves the records");
        s.handleKey(KeyEvent.VK_T, true);
        check(s.state == NeonOverdrive.State.RECORDS, "T opens the records");
        s.handleClick(500, 300);
        check(s.state == NeonOverdrive.State.HANGAR, "a click leaves the records");
        click(s, s.dailyButton());
        check(s.state == NeonOverdrive.State.PLAYING && s.dailyRun, "daily button starts the daily");
        s.health = 1;
        s.spawnQueue.clear();
        run(s, 0.5);
        for (int i = 0; i < 12; i++) add(s, NeonOverdrive.EnemyType.values()[i % 8], 120 + i * 65, 120 + i * 30, 500);
        run(s, 0.2);
        shot(s, "24-daily-last-heart");
        s.bombs = 11;
        s.useBomb();
        run(s, 0.07);
        shot(s, "25-bomb-blast");
        s.health = 1;
        s.invulnerable = 0;
        s.score = 9500;
        s.runBosses = 2;
        s.runAchievements = 2;
        s.runCoins = 64;
        s.runDamage = 21840;
        s.runTime = 512;
        s.endRun();
        s.state = NeonOverdrive.State.GAME_OVER;
        s.clock = 0;
        shot(s, "26-daily-gameover");

        NeonOverdrive.GamePanel km = game(24);
        NeonOverdrive.Enemy[] several = new NeonOverdrive.Enemy[6];
        for (int i = 0; i < 6; i++) several[i] = add(km, NeonOverdrive.EnemyType.values()[i], 300 + i * 80, 200, 1);
        km.update(DT);
        for (NeonOverdrive.Enemy e : several) km.damage(e, 5, false);
        run(km, 0.05);
        shot(km, "27-kill-explosions");

        // ---------- Speed with all the new effects ----------
        NeonOverdrive.GamePanel full = game(25);
        Random rnd = new Random(1);
        for (int i = 0; i < 90; i++) add(full, NeonOverdrive.EnemyType.values()[rnd.nextInt(8)], rnd.nextDouble() * 1000, rnd.nextDouble() * 700, 9999);
        full.invulnerable = 999;
        full.autoFire = true;
        full.health = 1;
        for (int i = 0; i < 240; i++) {
            full.aimX = rnd.nextInt(1000);
            full.update(DT);
            if (i % 6 == 0) full.damage(add(full, NeonOverdrive.EnemyType.CHASER, rnd.nextDouble() * 1000, rnd.nextDouble() * 700, 1), 5, false);
            full.damage(full.enemies.get(i % 80), 3, false);
        }
        BufferedImage img = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int warm = 0; warm < 60; warm++) full.render(g);
        long t0 = System.nanoTime();
        for (int i = 0; i < 60; i++) full.render(g);
        double ms = (System.nanoTime() - t0) / 60 / 1e6;
        System.out.printf("render %.1f ms with %d enemies, %d particles, %d texts, red vignette on%n", ms, full.enemies.size(), full.particles.size(), full.texts.size());
        check(ms < 12, "still fast enough for 60 FPS");

        // ---------- Sound ----------
        byte[] data = NeonOverdrive.Sound.renderEffect(1, 900, 350, 0.07, 0.13, 0.05);
        int peak = 0;
        for (int i = 0; i < data.length; i += 2) peak = Math.max(peak, Math.abs((short) ((data[i] & 0xff) | (data[i + 1] << 8))));
        check(peak > 1000 && peak < 32767, "shoot sound is audible and does not clip (peak " + peak + ")");
        int first = Math.abs((short) ((data[0] & 0xff) | (data[1] << 8)));
        check(first < 100, "sounds start without a click");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
