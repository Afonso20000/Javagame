import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/** Throwaway checks: rules, every feature, bots on each ship/difficulty, screenshots, speed. */
public class OdCheck {
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
        p.coins = 0;
        p.highScore = 0;
        Arrays.fill(p.perks, 0);
        Arrays.fill(p.unlocked, false);
        p.unlocked[0] = true;
        p.unlocked[1] = true;
        p.ship = NeonOverdrive.Ship.STRIKER;
        p.difficulty = NeonOverdrive.Difficulty.NORMAL;
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

    static void click(NeonOverdrive.GamePanel p, Rectangle r) {
        p.handleClick(r.x + r.width / 2, r.y + r.height / 2);
    }

    static NeonOverdrive.Enemy add(NeonOverdrive.GamePanel p, NeonOverdrive.EnemyType t, double x, double y, double hp) {
        NeonOverdrive.Enemy e = new NeonOverdrive.Enemy(t, x, y, hp);
        p.enemies.add(e);
        return e;
    }

    static void bot(NeonOverdrive.GamePanel p, double skill) {
        double fx = 0, fy = 0, nearest = 1e9;
        NeonOverdrive.Enemy target = null;
        for (NeonOverdrive.Enemy e : p.enemies) {
            if (e.dead) continue;
            double d = Math.max(1, Math.hypot(e.x - p.px, e.y - p.py));
            double reach = 170 + e.r * 2;
            if (d < reach) {
                double w = (reach - d) / reach / d * e.r;
                fx -= (e.x - p.px) * w;
                fy -= (e.y - p.py) * w;
            }
            if (e.spawnTimer <= 0 && d < nearest) {
                nearest = d;
                target = e;
            }
        }
        int close = 0;
        for (NeonOverdrive.Bullet b : p.bullets) {
            if (!b.enemy || b.dead) continue;
            double d = Math.max(1, Math.hypot(b.x - p.px, b.y - p.py));
            if (d < 130) {
                fx -= (b.x - p.px) / d / d * 60;
                fy -= (b.y - p.py) / d / d * 60;
            }
            if (d < 45) close++;
        }
        fx += (500 - p.px) * 0.0012;
        fy += (350 - p.py) * 0.0012;
        p.left = fx < -0.08;
        p.right = fx > 0.08;
        p.up = fy < -0.08;
        p.down = fy > 0.08;
        if (target != null) {
            p.aimX = target.x;
            p.aimY = target.y;
        }
        p.firing = target != null;
        if ((nearest < 55 + (target == null ? 0 : target.r) || close > 0) && p.canBeHurt() && p.rng.nextDouble() < skill) p.tryDash();
        if (p.health <= 1 && nearest < 90 && p.bombs > 0) p.useBomb();
        if (p.specialCharge >= 1 && target != null) p.useSpecial();
    }

    static int[] playMany(NeonOverdrive.Ship ship, NeonOverdrive.Difficulty d, int runs, long seedBase, int[] coinsOut) {
        int[] waves = new int[runs];
        for (int seed = 0; seed < runs; seed++) {
            NeonOverdrive.GamePanel p = panel(seedBase + seed);
            p.unlocked[ship.ordinal()] = true;
            p.ship = ship;
            p.difficulty = d;
            p.startGame();
            int frames = 0;
            while (p.state != NeonOverdrive.State.GAME_OVER && frames++ < 60 * 60 * 25) {
                if (p.state == NeonOverdrive.State.UPGRADE) {
                    p.update(0.4);
                    p.chooseCard(p.rng.nextInt(p.choices.size()));
                    continue;
                }
                bot(p, 0.7);
                p.update(DT);
            }
            waves[seed] = p.wave;
            coinsOut[0] += p.runCoins;
        }
        Arrays.sort(waves);
        return waves;
    }

    public static void main(String[] args) throws Exception {
        dir = args[0];
        new File(dir).mkdirs();

        // ---------- Menus and hangar ----------
        NeonOverdrive.GamePanel m = panel(1);
        shot(m, "01-menu");
        m.handleKey(KeyEvent.VK_ENTER, true);
        check(m.state == NeonOverdrive.State.HANGAR, "ENTER on menu opens the hangar");
        m.coins = 500;
        click(m, m.perkCard(0));
        m.noticeTimer = 0;
        shot(m, "02-hangar");
        check(m.perks[0] == 1 && m.coins == 460, "buying a perk costs coins");
        m.coins = 5000;
        m.diamonds = 5;
        click(m, m.shipCard(3));
        check(!m.unlocked[3] && m.diamonds == 5 && m.coins == 5000 && m.ship == NeonOverdrive.Ship.STRIKER,
                "coins cannot buy a ship, and not enough diamonds cannot either");
        m.diamonds = 20;
        click(m, m.shipCard(3));
        check(m.unlocked[3] && m.diamonds == 0 && m.coins == 5000 && m.ship == NeonOverdrive.Ship.TITAN,
                "buying a ship costs diamonds, not coins");
        m.coins = 460;
        click(m, m.shipCard(1));
        check(m.ship == NeonOverdrive.Ship.BLASTER, "clicking an owned ship selects it");
        m.handleKey(KeyEvent.VK_RIGHT, true);
        m.handleKey(KeyEvent.VK_RIGHT, false);
        check(m.ship == NeonOverdrive.Ship.TITAN, "arrow key skips locked ships");
        click(m, m.difficultyButton(2));
        check(m.difficulty == NeonOverdrive.Difficulty.HARD, "difficulty button works");
        m.difficulty = NeonOverdrive.Difficulty.EASY;
        click(m, m.launchButton());
        check(m.state == NeonOverdrive.State.PLAYING && m.wave == 1, "launch starts wave 1");
        check(m.maxHealth == 8 + 1 + 2, "health = ship + perk + easy bonus");

        // Save round trip.
        File saveTest = new File(dir, "save-test.properties");
        saveTest.delete();
        NeonOverdrive.GamePanel s1 = panel(2);
        s1.saveFile = saveTest;
        s1.coins = 77;
        s1.highScore = 1234;
        s1.unlocked[3] = true;
        s1.perks[2] = 2;
        s1.ship = NeonOverdrive.Ship.TITAN;
        s1.difficulty = NeonOverdrive.Difficulty.HARD;
        s1.save();
        NeonOverdrive.GamePanel s2 = panel(2);
        s2.saveFile = saveTest;
        s2.loadSave();
        check(s2.coins == 77 && s2.highScore == 1234 && s2.unlocked[3] && s2.perks[2] == 2
                && s2.ship == NeonOverdrive.Ship.TITAN && s2.difficulty == NeonOverdrive.Difficulty.HARD, "save file round trip");
        java.nio.file.Files.write(saveTest.toPath(), "coins=banana".getBytes());
        NeonOverdrive.GamePanel s3 = panel(2);
        s3.saveFile = saveTest;
        s3.loadSave();
        check(s3.coins == 0, "a broken save file does not crash");

        // ---------- Combat ----------
        NeonOverdrive.GamePanel a = game(3);
        NeonOverdrive.Enemy target = add(a, NeonOverdrive.EnemyType.CHASER, 700, 350, 1);
        a.aimX = 700;
        a.aimY = 350;
        a.firing = true;
        for (int i = 0; i < 60 && !target.dead; i++) a.update(DT);
        check(target.dead && a.score >= 10 && a.kills == 1, "shooting kills and scores");
        a.firing = false;
        for (int i = 0; i < 400 && a.state == NeonOverdrive.State.PLAYING; i++) a.update(DT);
        check(a.state == NeonOverdrive.State.UPGRADE && a.choices.size() == 3, "wave clear opens upgrade cards");
        a.handleKey(KeyEvent.VK_R, true);
        check(a.rerolls == 1, "reroll ignored during the safety delay");
        run(a, 0.5);
        List<NeonOverdrive.Upgrade> before = new ArrayList<NeonOverdrive.Upgrade>(a.choices);
        boolean changed = false;
        a.rerolls = 30;
        for (int i = 0; i < 20; i++) { a.reroll(); changed |= !a.choices.equals(before); }
        check(changed && a.rerolls == 10, "reroll changes the cards and uses a charge");
        a.rerolls = 0;
        List<NeonOverdrive.Upgrade> kept = new ArrayList<NeonOverdrive.Upgrade>(a.choices);
        a.reroll();
        check(a.choices.equals(kept) && a.rerolls == 0, "no reroll when none left");
        NeonOverdrive.Upgrade chosen = a.choices.get(1);
        a.runCoins = 1000;
        a.handleKey(KeyEvent.VK_2, true);
        check(a.state == NeonOverdrive.State.PLAYING && a.wave == 2 && a.stacks[chosen.ordinal()] == 1, "choosing a card starts the next wave");

        // Rarity odds shift to rarer cards later.
        NeonOverdrive.GamePanel r = game(4);
        int early = 0, late = 0;
        for (int i = 0; i < 3000; i++) { r.wave = 1; r.rollChoices(); for (NeonOverdrive.Upgrade u : r.choices) if (u.rarity == NeonOverdrive.Rarity.LEGENDARY) early++; }
        for (int i = 0; i < 3000; i++) { r.wave = 25; r.rollChoices(); for (NeonOverdrive.Upgrade u : r.choices) if (u.rarity == NeonOverdrive.Rarity.LEGENDARY) late++; }
        check(early > 0 && late > early, "legendary cards appear, more often later (" + early + " vs " + late + ")");

        // Every upgrade maxed: applying them all is safe and they do not show again.
        NeonOverdrive.GamePanel all = game(5);
        for (NeonOverdrive.Upgrade u : NeonOverdrive.Upgrade.values()) for (int i = 0; i < u.max; i++) all.applyUpgrade(u);
        all.rollChoices();
        check(all.choices.isEmpty() && all.drones.size() == 3 && all.chainTargets == 6 && all.maxHealth == 8, "all upgrades stack to their limits");

        // Glass cannon can't kill you.
        NeonOverdrive.GamePanel glass = game(6);
        glass.ship = NeonOverdrive.Ship.PHANTOM;
        glass.startGame();
        glass.spawnQueue.clear();
        glass.applyUpgrade(NeonOverdrive.Upgrade.GLASS);
        check(glass.maxHealth == 1 && glass.health == 1 && glass.bulletDamage > 2, "glass cannon leaves at least 1 health");

        // Damage, dash, second life, game over and coins.
        NeonOverdrive.GamePanel h = game(7);
        add(h, NeonOverdrive.EnemyType.TANK, h.px, h.py, 500);
        h.update(DT);
        check(h.health == 4, "touching an enemy costs a heart");
        run(h, 0.3);
        check(h.health == 4, "invulnerable after a hit");

        NeonOverdrive.GamePanel d = game(8);
        add(d, NeonOverdrive.EnemyType.TANK, d.px + 40, d.py, 500);
        d.aimX = d.px + 100;
        d.aimY = d.py;
        d.update(DT);
        d.tryDash();
        for (int i = 0; i < 8; i++) d.update(DT);
        check(d.health == 5, "dashing through an enemy is safe");

        NeonOverdrive.GamePanel l = game(9);
        l.applyUpgrade(NeonOverdrive.Upgrade.SECOND_LIFE);
        l.health = 1;
        NeonOverdrive.Enemy killer = add(l, NeonOverdrive.EnemyType.TANK, l.px, l.py, 5000);
        l.update(DT);
        check(l.state == NeonOverdrive.State.PLAYING && l.health == 3 && l.secondLives == 0 && killer.hp < 5000, "second life revives with a blast");

        NeonOverdrive.GamePanel o = game(10);
        o.score = 123;
        o.runCoins = 9;
        o.coins = 1;
        o.health = 1;
        add(o, NeonOverdrive.EnemyType.TANK, o.px, o.py, 500);
        o.update(DT);
        check(o.state == NeonOverdrive.State.GAME_OVER && o.highScore == 123 && o.newRecord && o.coins == 10, "game over keeps coins and record");
        o.handleKey(KeyEvent.VK_ENTER, true);
        check(o.state == NeonOverdrive.State.GAME_OVER, "no instant skip of the game over screen");
        run(o, 1.2);
        shot(o, "10-gameover");
        o.handleKey(KeyEvent.VK_ENTER, true);
        check(o.state == NeonOverdrive.State.HANGAR, "game over goes to the hangar");
        o.startGame();
        check(o.score == 0 && o.health == 5 && o.wave == 1 && o.enemies.isEmpty() && o.drones.isEmpty(), "new run resets everything");

        // Pause and quit banks coins.
        NeonOverdrive.GamePanel q = game(11);
        NeonOverdrive.Enemy frozen = add(q, NeonOverdrive.EnemyType.CHASER, 900, 600, 5);
        q.runCoins = 5;
        q.handleKey(KeyEvent.VK_P, true);
        double fx = frozen.x;
        run(q, 0.5);
        check(frozen.x == fx, "pause freezes the world");
        shot(q, "11-paused");
        q.handleKey(KeyEvent.VK_X, true);
        check(q.state == NeonOverdrive.State.HANGAR && q.coins == 5, "quitting from pause keeps coins");

        // Coins.
        NeonOverdrive.GamePanel c = game(12);
        c.perks[NeonOverdrive.Perk.GREED.ordinal()] = 2;
        c.difficulty = NeonOverdrive.Difficulty.HARD;
        c.earnCoins(10);
        check(c.runCoins == 22, "coins scale with perk and difficulty (10 * 1.5 * 1.5 = 22)");

        // ---------- Enemies ----------
        NeonOverdrive.GamePanel b = game(13);
        NeonOverdrive.Enemy bomber = add(b, NeonOverdrive.EnemyType.BOMBER, b.px + 70, b.py, 500);
        NeonOverdrive.Enemy near = add(b, NeonOverdrive.EnemyType.CHASER, b.px + 110, b.py, 3);
        b.update(DT);
        check(bomber.fuse > 0, "bomber lights its fuse near the player");
        run(b, 0.9);
        check(bomber.dead && near.dead && b.health == 4, "bomber blows up, hurting player and enemies");

        NeonOverdrive.GamePanel b2 = game(14);
        NeonOverdrive.Enemy far = add(b2, NeonOverdrive.EnemyType.BOMBER, 900, 600, 1);
        b2.damage(far, 5, false);
        b2.update(DT);
        check(far.dead && b2.health == 5, "shooting a bomber far away is safe");

        NeonOverdrive.GamePanel gh = game(15);
        NeonOverdrive.Enemy ghost = add(gh, NeonOverdrive.EnemyType.GHOST, 900, 650, 500);
        gh.invulnerable = 99;
        ghost.attackTimer = 0.01;
        run(gh, 0.1);
        check(ghost.blinkWarn > 0 && Math.hypot(ghost.warpX - gh.px, ghost.warpY - gh.py) > 100, "ghost warns before blinking, not on top of you");
        double oldX = ghost.x;
        run(gh, 0.8);
        check(Math.abs(ghost.x - oldX) > 100, "ghost teleports");

        NeonOverdrive.GamePanel sp = game(16);
        NeonOverdrive.Enemy split = add(sp, NeonOverdrive.EnemyType.SPLITTER, 800, 500, 1);
        sp.useBomb();
        sp.update(DT);
        check(split.dead && sp.bombs == 0 && sp.enemies.size() == 3, "splitter becomes 3 minis");
        sp.useBomb();
        check(sp.bombs == 0, "no bomb when you have none");

        // Elite and wave contents.
        NeonOverdrive.GamePanel w = game(17);
        w.wave = 11;
        w.invulnerable = 99;
        w.nextWave();
        boolean sawElite = false, sawBomber = false, sawGhost = false;
        for (int i = 0; i < 60 * 25; i++) {
            w.invulnerable = 99;
            w.update(DT);
            for (NeonOverdrive.Enemy e : w.enemies) {
                sawElite |= e.elite;
                sawBomber |= e.type == NeonOverdrive.EnemyType.BOMBER;
                sawGhost |= e.type == NeonOverdrive.EnemyType.GHOST;
            }
            if (i == 60 * 14) shot(w, "07-wave12-toxic");
        }
        check(sawElite && sawBomber && sawGhost, "late waves have elites, bombers and ghosts");

        // Specials.
        NeonOverdrive.GamePanel s = game(18);
        s.specialCharge = 0.5;
        s.useSpecial();
        check(s.overdriveTimer == 0 && s.specialCharge == 0.5, "special needs full charge");
        s.specialCharge = 1;
        s.useSpecial();
        check(s.overdriveTimer > 0 && s.specialCharge == 0, "Striker overdrive");
        NeonOverdrive.GamePanel sn = panel(19);
        sn.ship = NeonOverdrive.Ship.BLASTER;
        sn.startGame();
        sn.spawnQueue.clear();
        NeonOverdrive.Enemy pushed = add(sn, NeonOverdrive.EnemyType.CHASER, sn.px + 100, sn.py, 50);
        sn.specialCharge = 1;
        sn.useSpecial();
        sn.update(DT);
        check(sn.bullets.size() >= 40 && pushed.x > sn.px + 180, "Blaster nova fires a ring and knocks back");
        NeonOverdrive.GamePanel sw = panel(20);
        sw.ship = NeonOverdrive.Ship.PHANTOM;
        sw.startGame();
        sw.spawnQueue.clear();
        NeonOverdrive.Enemy slowA = add(sw, NeonOverdrive.EnemyType.TANK, 900, 100, 50);
        run(sw, 0.5);
        double dist1 = slowA.x;
        run(sw, 0.5);
        double normal = dist1 - slowA.x;
        sw.specialCharge = 1;
        sw.useSpecial();
        double dist2 = slowA.x;
        run(sw, 0.5);
        check(sw.warpTimer > 0 && (dist2 - slowA.x) < normal * 0.5, "Phantom time warp slows enemies");
        NeonOverdrive.GamePanel st = panel(21);
        st.ship = NeonOverdrive.Ship.TITAN;
        st.startGame();
        st.spawnQueue.clear();
        NeonOverdrive.Enemy burn = add(st, NeonOverdrive.EnemyType.TANK, st.px + 30, st.py, 500);
        st.specialCharge = 1;
        st.useSpecial();
        run(st, 1);
        check(st.health == st.maxHealth && burn.hp < 500, "Titan fortress: invincible and burns foes");
        shot(st, "08-fortress");
        st.damage(new NeonOverdrive.Enemy(NeonOverdrive.EnemyType.CHASER, 900, 600, 1), 5, false);
        check(st.specialCharge == 0, "special does not recharge while it is active");
        run(st, 4);
        check(st.fortressTimer == 0, "fortress runs out");

        // A huge hit on a boss cannot refill the special more than a fair share.
        NeonOverdrive.GamePanel cb = game(33);
        cb.specialCharge = 0;
        NeonOverdrive.Enemy cbBoss = add(cb, NeonOverdrive.EnemyType.WARDEN, 800, 200, 1000);
        cb.damage(cbBoss, 100, false);
        check(Math.abs(cb.specialCharge - 0.2) < 0.001, "boss damage charges special by share of boss health");

        // Upgrades in action.
        NeonOverdrive.GamePanel z = game(22);
        z.applyUpgrade(NeonOverdrive.Upgrade.CHAIN);
        z.applyUpgrade(NeonOverdrive.Upgrade.FROST);
        z.applyUpgrade(NeonOverdrive.Upgrade.DRONE);
        NeonOverdrive.Enemy zapA = add(z, NeonOverdrive.EnemyType.TANK, 700, 350, 500);
        NeonOverdrive.Enemy zapB = add(z, NeonOverdrive.EnemyType.TANK, 700, 460, 500);
        z.aimX = 700;
        z.aimY = 350;
        z.firing = true;
        double dronePre = zapA.hp;
        run(z, 0.8);
        check(zapB.hp < 500, "chain lightning hits a second enemy");
        check(zapA.slowTimer > 0, "frost slows");
        z.firing = false;
        z.update(DT);
        double noFire = zapA.hp + zapB.hp;
        run(z, 2);
        check(zapA.hp + zapB.hp < noFire, "drone shoots by itself");

        // Vampire heals.
        NeonOverdrive.GamePanel v = game(23);
        v.applyUpgrade(NeonOverdrive.Upgrade.VAMPIRE);
        v.health = 3;
        for (int i = 0; i < 40; i++) v.damage(new NeonOverdrive.Enemy(NeonOverdrive.EnemyType.MINI, 900, 650, 1), 5, false);
        check(v.health == 4, "vampire heals after 40 kills");

        // ---------- Bosses ----------
        NeonOverdrive.GamePanel ov = game(24);
        ov.wave = 4;
        ov.nextWave();
        check(ov.spawnQueue.get(0) == NeonOverdrive.EnemyType.OVERLORD, "wave 5 = Overlord");
        ov.spawnQueue.retainAll(Arrays.asList(NeonOverdrive.EnemyType.OVERLORD));
        ov.px = 120;
        ov.py = 500;
        boolean radial = false, charge = false, minions = false, spiral = false, chargeShot = false;
        NeonOverdrive.Enemy lord = null;
        for (int i = 0; i < 60 * 40; i++) {
            ov.invulnerable = 99;
            ov.update(DT);
            for (NeonOverdrive.Enemy e : ov.enemies) {
                if (e.type == NeonOverdrive.EnemyType.OVERLORD) lord = e;
                else minions = true;
            }
            if (lord != null) {
                charge |= lord.chargeWarn > 0;
                spiral |= lord.spiralTime > 0;
                if (!chargeShot && lord.chargeWarn > 0.2 && lord.chargeWarn < 0.4 && Math.hypot(lord.x - ov.px, lord.y - ov.py) > 250) {
                    shot(ov, "04-overlord-charge");
                    chargeShot = true;
                }
                if (lord.chargeWarn > 0.2 && !chargeShot) { lord.x = 700; lord.y = 200; }
            }
            radial |= ov.bullets.size() > 10;
        }
        check(radial && charge && minions && spiral, "Overlord uses all four attacks");
        check(lord.x >= 56 && lord.x <= 944 && lord.y >= 56 && lord.y <= 644, "Overlord stays in the arena");
        check(ov.bossAlive(), "boss detected as alive");
        ov.enemies.removeIf(e -> e != lord2(ov));
        NeonOverdrive.Enemy lordNow = lord;
        ov.damage(lordNow, 1e7, false);
        check(lordNow.dead && ov.pickups.size() >= 22, "boss drops heart, bomb and coins");

        NeonOverdrive.GamePanel sr = game(25);
        sr.wave = 9;
        sr.nextWave();
        check(sr.spawnQueue.get(0) == NeonOverdrive.EnemyType.SERPENT, "wave 10 = Serpent");
        sr.spawnQueue.retainAll(Arrays.asList(NeonOverdrive.EnemyType.SERPENT));
        boolean serpentBullets = false;
        for (int i = 0; i < 60 * 12; i++) {
            sr.invulnerable = 99;
            sr.update(DT);
            serpentBullets |= !sr.bullets.isEmpty();
            if (i == 60 * 8) shot(sr, "05-serpent");
        }
        NeonOverdrive.Enemy head = null;
        NeonOverdrive.Enemy body = null;
        for (NeonOverdrive.Enemy e : sr.enemies) {
            if (e.type == NeonOverdrive.EnemyType.SERPENT) head = e;
            if (e.type == NeonOverdrive.EnemyType.SEGMENT) body = e;
        }
        check(head != null && body != null && serpentBullets, "Serpent has a body and shoots");
        double headBefore = head.hp;
        sr.damage(body, 10, false);
        check(Math.abs(headBefore - head.hp - 5) < 0.01 && !body.dead, "hitting the body gives half damage to the head");
        double oneBomb = head.hp;
        sr.bombs = 1;
        sr.useBomb();
        check(Math.abs(oneBomb - head.hp - 40) < 0.01, "a bomb hits the Serpent once, not per segment");
        sr.damage(head, 1e7, false);
        sr.update(DT);
        boolean anyBody = false;
        for (NeonOverdrive.Enemy e : sr.enemies) anyBody |= e.type == NeonOverdrive.EnemyType.SEGMENT && !e.dead;
        check(!anyBody, "killing the head destroys the body");
        for (int i = 0; i < 60 * 8 && sr.state == NeonOverdrive.State.PLAYING; i++) { sr.invulnerable = 99; sr.update(DT); }
        check(sr.state == NeonOverdrive.State.UPGRADE, "wave ends after the Serpent dies");

        NeonOverdrive.GamePanel wd = game(26);
        wd.wave = 14;
        wd.nextWave();
        check(wd.spawnQueue.get(0) == NeonOverdrive.EnemyType.WARDEN, "wave 15 = Warden");
        wd.spawnQueue.retainAll(Arrays.asList(NeonOverdrive.EnemyType.WARDEN));
        boolean warned = false, fired = false, beamShot = false;
        NeonOverdrive.Enemy warden = null;
        for (int i = 0; i < 60 * 12; i++) {
            wd.invulnerable = 99;
            wd.update(DT);
            for (NeonOverdrive.Enemy e : wd.enemies) if (e.type == NeonOverdrive.EnemyType.WARDEN) warden = e;
            if (warden != null) {
                warned |= warden.beamWarn > 0;
                fired |= warden.beamTime > 0;
                if (!beamShot && warden.beamTime > 2) { shot(wd, "06-warden-beams"); beamShot = true; }
            }
        }
        check(warned && fired, "Warden warns before firing beams");
        // Beam geometry: on the beam vs. opposite side vs. off to the side.
        NeonOverdrive.GamePanel bg = game(27);
        NeonOverdrive.Enemy beamer = add(bg, NeonOverdrive.EnemyType.WARDEN, 500, 350, 5000);
        beamer.beamCount = 1;
        beamer.beamAngle = 0;
        bg.px = 800; bg.py = 350;
        boolean onBeam = bg.touchingBeam(beamer);
        bg.px = 200; bg.py = 350;
        boolean behind = bg.touchingBeam(beamer);
        bg.px = 800; bg.py = 500;
        boolean aside = bg.touchingBeam(beamer);
        check(onBeam && !behind && !aside, "beam hits only what is on it");
        NeonOverdrive.GamePanel bh = game(28);
        NeonOverdrive.Enemy b3 = add(bh, NeonOverdrive.EnemyType.WARDEN, 200, 350, 5000);
        b3.beamCount = 2;
        b3.beamAngle = 0;
        b3.beamSpin = 0;
        b3.beamTime = 2;
        b3.spawnTimer = 0;
        bh.update(DT);
        check(bh.health == 4, "standing in a beam hurts");

        // Boss 4 cycles back, tougher.
        NeonOverdrive.GamePanel cy = game(29);
        cy.wave = 19;
        cy.nextWave();
        check(cy.spawnQueue.get(0) == NeonOverdrive.EnemyType.OVERLORD, "wave 20 cycles back to the Overlord");

        // Themes change every 5 waves.
        NeonOverdrive.GamePanel th = game(30);
        th.wave = 1;
        NeonOverdrive.Theme t1 = th.currentTheme();
        th.wave = 5;
        NeonOverdrive.Theme t5 = th.currentTheme();
        th.wave = 6;
        NeonOverdrive.Theme t6 = th.currentTheme();
        check(t1 == t5 && t1 != t6, "background changes after each boss");

        // Window scaling maps the mouse correctly.
        NeonOverdrive.GamePanel sc = panel(31);
        sc.viewScale = 2;
        sc.viewX = 100;
        sc.viewY = 50;
        check(sc.worldX(1100) == 500 && sc.worldY(750) == 350, "mouse position maps correctly when scaled");

        // Music.
        for (NeonOverdrive.Sound.Track t : new NeonOverdrive.Sound.Track[] {NeonOverdrive.Sound.Track.MENU, NeonOverdrive.Sound.Track.GAME, NeonOverdrive.Sound.Track.BOSS}) {
            byte[] data = NeonOverdrive.Sound.synthesize(t);
            int peak = 0;
            long sum = 0;
            for (int i = 0; i < data.length; i += 2) {
                int sampleValue = (short) ((data[i] & 0xff) | (data[i + 1] << 8));
                peak = Math.max(peak, Math.abs(sampleValue));
                sum += Math.abs(sampleValue);
            }
            double seconds = data.length / 2 / 22050.0;
            System.out.printf("music %s: %.1fs, peak %.2f, average %.3f%n", t, seconds, peak / 32767.0, sum / (data.length / 2.0) / 32767.0);
            check(peak > 3000 && peak <= 10000 && seconds > 10, "music track is audible, not too loud, loops over 10s");
        }
        NeonOverdrive.Sound real = new NeonOverdrive.Sound();
        real.load();
        for (int i = 0; i < 80 && (real.loadedEffects() < 14 || real.loadedTracks() < 3); i++) Thread.sleep(100);
        check(real.loadedEffects() == 14 && real.loadedTracks() == 3, "sound device loaded 14 effects and 3 music tracks");

        // ---------- Speed under load ----------
        NeonOverdrive.GamePanel full = game(32);
        full.applyUpgrade(NeonOverdrive.Upgrade.MULTI);
        for (int i = 0; i < 4; i++) full.applyUpgrade(NeonOverdrive.Upgrade.ORBITALS);
        Random rnd = new Random(1);
        for (int i = 0; i < 90; i++) add(full, NeonOverdrive.EnemyType.values()[rnd.nextInt(8)], rnd.nextDouble() * 1000, rnd.nextDouble() * 700, 9999);
        full.invulnerable = 999;
        full.autoFire = true;
        long worst = 0;
        for (int i = 0; i < 240; i++) {
            full.aimX = rnd.nextInt(1000);
            long t0 = System.nanoTime();
            full.update(DT);
            worst = Math.max(worst, System.nanoTime() - t0);
            full.damage(full.enemies.get(i % 80), 1, false);
        }
        BufferedImage img = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int warm = 0; warm < 60; warm++) full.render(g);
        long t0 = System.nanoTime();
        for (int i = 0; i < 60; i++) full.render(g);
        double ms = (System.nanoTime() - t0) / 60 / 1e6;
        System.out.printf("render %.1f ms, worst update %.1f ms, %d enemies, %d bullets, %d particles%n", ms, worst / 1e6, full.enemies.size(), full.bullets.size(), full.particles.size());
        check(ms < 12, "fast enough for 60 FPS");

        // ---------- Bots ----------
        boolean shotPlay = false;
        NeonOverdrive.GamePanel demo = panel(500);
        demo.startGame();
        for (int frames = 0; frames < 60 * 60 * 20 && demo.state != NeonOverdrive.State.GAME_OVER; frames++) {
            if (demo.state == NeonOverdrive.State.UPGRADE) {
                if (demo.wave == 3) shot(demo, "03-upgrade");
                demo.update(0.4);
                demo.chooseCard(demo.rng.nextInt(demo.choices.size()));
                continue;
            }
            bot(demo, 0.7);
            demo.update(DT);
            if (!shotPlay && demo.wave == 4 && demo.enemies.size() > 8) {
                shot(demo, "09-gameplay");
                shotPlay = true;
            }
        }

        int runs = 30;
        for (NeonOverdrive.Ship ship : NeonOverdrive.Ship.values()) {
            int[] coinTotal = new int[1];
            int[] waves = playMany(ship, NeonOverdrive.Difficulty.NORMAL, runs, 1000, coinTotal);
            System.out.println(ship + " NORMAL median " + waves[runs / 2] + " best " + waves[runs - 1] + " worst " + waves[0] + " coins/run " + coinTotal[0] / runs + " " + Arrays.toString(waves));
        }
        int[] c1 = new int[1], c2 = new int[1];
        int[] easy = playMany(NeonOverdrive.Ship.STRIKER, NeonOverdrive.Difficulty.EASY, runs, 2000, c1);
        int[] hard = playMany(NeonOverdrive.Ship.STRIKER, NeonOverdrive.Difficulty.HARD, runs, 2000, c2);
        System.out.println("EASY median " + easy[runs / 2] + " best " + easy[runs - 1] + "; HARD median " + hard[runs / 2] + " best " + hard[runs - 1]);
        check(easy[runs / 2] >= hard[runs / 2], "easy reaches at least as far as hard");
        check(easy[runs - 1] < 70 && hard[runs - 1] < 70, "no run is endless");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static NeonOverdrive.Enemy lord2(NeonOverdrive.GamePanel p) {
        for (NeonOverdrive.Enemy e : p.enemies) if (e.type == NeonOverdrive.EnemyType.OVERLORD) return e;
        return null;
    }
}
