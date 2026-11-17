import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Random;
import javax.imageio.ImageIO;

/** Throwaway checks: rules, a bot playing many runs, screenshots, render speed. */
public class NeonCheck {
    static final double DT = 1 / 60.0;
    static int failures;
    static String dir;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }

    static NeonSwarm.GamePanel panel(long seed) {
        NeonSwarm.GamePanel p = new NeonSwarm.GamePanel();
        p.scoreFile = null;
        p.highScore = 0;
        p.rng = new Random(seed);
        return p;
    }

    static void shot(NeonSwarm.GamePanel p, String name) throws Exception {
        BufferedImage img = new BufferedImage(NeonSwarm.W, NeonSwarm.H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        p.render(g);
        g.dispose();
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }

    /** Runs from danger, aims at the nearest enemy, dashes and bombs when things get close. */
    static void bot(NeonSwarm.GamePanel p, double skill) {
        double fx = 0, fy = 0, nearest = 1e9;
        NeonSwarm.Enemy target = null;
        for (NeonSwarm.Enemy e : p.enemies) {
            if (e.dead) continue;
            double d = Math.max(1, Math.hypot(e.x - p.px, e.y - p.py));
            double reach = 170 + e.type.radius * 2;
            if (d < reach) {
                double w = (reach - d) / reach / d * e.type.radius;
                fx -= (e.x - p.px) * w;
                fy -= (e.y - p.py) * w;
            }
            if (e.spawnTimer <= 0 && d < nearest) {
                nearest = d;
                target = e;
            }
        }
        int close = 0;
        for (NeonSwarm.Bullet b : p.bullets) {
            if (!b.enemy || b.dead) continue;
            double d = Math.max(1, Math.hypot(b.x - p.px, b.y - p.py));
            if (d < 130) {
                fx -= (b.x - p.px) / d / d * 60;
                fy -= (b.y - p.py) / d / d * 60;
            }
            if (d < 45) close++;
        }
        // Drift back toward the middle so it does not get cornered.
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
        if ((nearest < 55 + (target == null ? 0 : target.type.radius) || close > 0) && p.canBeHurt() && p.rng.nextDouble() < skill) {
            p.tryDash();
        }
        if (p.health <= 1 && nearest < 90 && p.bombs > 0) p.useBomb();
    }

    public static void main(String[] args) throws Exception {
        dir = args[0];
        new File(dir).mkdirs();

        NeonSwarm.GamePanel m = panel(1);
        shot(m, "1-menu");
        m.handleKey(KeyEvent.VK_ENTER, true);
        check(m.state == NeonSwarm.State.PLAYING && m.wave == 1, "ENTER starts wave 1");
        check(m.spawnQueue.size() == 9, "wave 1 has 9 enemies");

        // Bullets hurt enemies; killing scores.
        NeonSwarm.GamePanel a = panel(2);
        a.startGame();
        a.spawnQueue.clear();
        NeonSwarm.Enemy target = new NeonSwarm.Enemy(NeonSwarm.EnemyType.CHASER, 700, 350, 1);
        a.enemies.add(target);
        a.aimX = 700;
        a.aimY = 350;
        a.firing = true;
        for (int i = 0; i < 60 && !target.dead; i++) a.update(DT);
        check(target.dead && a.score >= 10 && a.kills == 1, "shooting kills an enemy and scores");

        // Clearing the wave leads to upgrade cards, then wave 2 with the upgrade applied.
        a.firing = false;
        for (int i = 0; i < 200 && a.state == NeonSwarm.State.PLAYING; i++) a.update(DT);
        check(a.state == NeonSwarm.State.UPGRADE && a.choices.size() == 3, "wave clear opens 3 upgrade cards");
        shot(a, "3-upgrade");
        a.handleKey(KeyEvent.VK_1, true);
        check(a.state == NeonSwarm.State.UPGRADE, "card click ignored during the short safety delay");
        for (int i = 0; i < 30; i++) a.update(DT);
        NeonSwarm.Upgrade chosen = a.choices.get(0);
        a.handleKey(KeyEvent.VK_1, true);
        check(a.state == NeonSwarm.State.PLAYING && a.wave == 2 && a.stacks[chosen.ordinal()] == 1, "choosing a card starts wave 2");

        // Each upgrade has an effect and every upgrade can reach its max safely.
        NeonSwarm.GamePanel u = panel(3);
        u.startGame();
        double rate = u.fireDelay, dmg = u.bulletDamage;
        u.applyUpgrade(NeonSwarm.Upgrade.RAPID);
        u.applyUpgrade(NeonSwarm.Upgrade.POWER);
        u.applyUpgrade(NeonSwarm.Upgrade.MULTI);
        u.applyUpgrade(NeonSwarm.Upgrade.VITALITY);
        check(u.fireDelay < rate && u.bulletDamage > dmg && u.bulletCount == 2 && u.maxHealth == 6 && u.health == 6, "upgrades change the player");

        // Damage, invulnerability, dash immunity, game over.
        NeonSwarm.GamePanel h = panel(4);
        h.startGame();
        h.spawnQueue.clear();
        h.enemies.add(new NeonSwarm.Enemy(NeonSwarm.EnemyType.TANK, h.px, h.py, 500));
        h.update(DT);
        check(h.health == 4, "touching an enemy costs one heart");
        for (int i = 0; i < 20; i++) h.update(DT);
        check(h.health == 4, "brief invulnerability after a hit");
        h.invulnerable = 0;
        h.aimX = h.px + 100;
        h.aimY = h.py;
        h.update(DT);
        h.invulnerable = 0;
        h.dashCooldown = 0;
        int before = h.health;
        h.enemies.clear();
        h.enemies.add(new NeonSwarm.Enemy(NeonSwarm.EnemyType.TANK, h.px, h.py, 500));
        h.tryDash();
        h.invulnerable = 0;
        h.update(DT);
        check(h.health == before, "dashing dodges damage");

        NeonSwarm.GamePanel o = panel(5);
        o.startGame();
        o.score = 123;
        o.spawnQueue.clear();
        o.health = 1;
        o.enemies.add(new NeonSwarm.Enemy(NeonSwarm.EnemyType.TANK, o.px, o.py, 500));
        o.update(DT);
        check(o.state == NeonSwarm.State.GAME_OVER && o.highScore == 123 && o.newRecord, "last heart = game over and new record");
        o.handleKey(KeyEvent.VK_ENTER, true);
        check(o.state == NeonSwarm.State.GAME_OVER, "no instant restart while the explosion plays");
        for (int i = 0; i < 90; i++) o.update(DT);
        shot(o, "6-gameover");
        o.handleKey(KeyEvent.VK_ENTER, true);
        check(o.state == NeonSwarm.State.PLAYING && o.score == 0 && o.health == 5 && o.wave == 1 && o.enemies.isEmpty(), "restart resets everything");

        // Pause, bomb, splitter.
        NeonSwarm.GamePanel s = panel(6);
        s.startGame();
        s.spawnQueue.clear();
        NeonSwarm.Enemy sp = new NeonSwarm.Enemy(NeonSwarm.EnemyType.SPLITTER, 800, 500, 8);
        s.enemies.add(sp);
        s.handleKey(KeyEvent.VK_P, true);
        double y = sp.x;
        for (int i = 0; i < 30; i++) s.update(DT);
        check(sp.x == y, "pause freezes the world");
        shot(s, "7-paused");
        s.handleKey(KeyEvent.VK_P, true);
        s.useBomb();
        s.update(DT);
        check(sp.dead && s.bombs == 0 && s.enemies.size() == 3, "bomb kills, splitter becomes 3 minis");
        s.useBomb();
        check(s.bombs == 0, "no bomb when you have none");

        // Boss: all its attacks run, it dies, leaves rewards, wave completes.
        NeonSwarm.GamePanel b = panel(7);
        b.startGame();
        b.wave = 4;
        b.nextWave();
        check(b.wave == 5 && b.spawnQueue.contains(NeonSwarm.EnemyType.BOSS), "wave 5 has a boss");
        b.spawnQueue.retainAll(Arrays.asList(NeonSwarm.EnemyType.BOSS));
        b.health = 999;
        b.px = 120;
        b.py = 580;
        boolean sawBullets = false, sawCharge = false, sawMinions = false, sawSpiral = false;
        NeonSwarm.Enemy boss = null;
        for (int i = 0; i < 60 * 40; i++) {
            b.update(DT);
            b.health = 999;
            for (NeonSwarm.Enemy e : b.enemies) {
                if (e.type == NeonSwarm.EnemyType.BOSS) boss = e;
                else sawMinions = true;
            }
            if (boss != null) {
                sawCharge |= boss.chargeWarn > 0;
                sawSpiral |= boss.spiralTime > 0;
                if (boss.chargeWarn > 0.3 && boss.chargeWarn < 0.32) shot(b, "4-boss-charge");
            }
            sawBullets |= !b.bullets.isEmpty();
            if (i == 60 * 14) shot(b, "4b-boss");
        }
        check(sawBullets && sawCharge && sawMinions && sawSpiral, "boss uses radial, charge, summon and spiral attacks");
        check(boss.x >= 56 && boss.x <= 944 && boss.y >= 56 && boss.y <= 644, "boss stays in the arena");
        b.damage(boss, 99999, false);
        check(boss.dead && b.pickups.size() == 2, "dead boss drops heart and bomb");

        // All upgrades maxed: nothing breaks, and measure render speed under load.
        NeonSwarm.GamePanel x = panel(8);
        x.startGame();
        for (NeonSwarm.Upgrade up : NeonSwarm.Upgrade.values()) {
            for (int i = 0; i < up.max; i++) x.applyUpgrade(up);
        }
        x.wave = 18;
        x.nextWave();
        x.health = 999;
        x.firing = true;
        long worstUpdate = 0;
        for (int i = 0; i < 60 * 45 && x.state == NeonSwarm.State.PLAYING; i++) {
            x.health = 999;
            bot(x, 1);
            long t0 = System.nanoTime();
            x.update(DT);
            worstUpdate = Math.max(worstUpdate, System.nanoTime() - t0);
        }
        System.out.println("maxed run reached wave " + x.wave + ", enemies " + x.enemies.size());
        NeonSwarm.GamePanel full = panel(9);
        full.startGame();
        full.bulletCount = 5;
        full.orbitals = 4;
        full.spawnQueue.clear();
        Random r = new Random(1);
        for (int i = 0; i < 80; i++) {
            full.enemies.add(new NeonSwarm.Enemy(NeonSwarm.EnemyType.values()[r.nextInt(6)], r.nextDouble() * 1000, r.nextDouble() * 700, 9999));
        }
        full.invulnerable = 999;
        full.firing = true;
        full.autoFire = true;
        for (int i = 0; i < 120; i++) { full.aimX = r.nextInt(1000); full.update(DT); full.damage(full.enemies.get(i % 80), 1, false); }
        BufferedImage img = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int warm = 0; warm < 60; warm++) full.render(g);
        long t0 = System.nanoTime();
        for (int i = 0; i < 60; i++) full.render(g);
        System.out.printf("render under load: %.1f ms per frame, %d enemies, %d particles, worst update %.1f ms%n",
                (System.nanoTime() - t0) / 60 / 1e6, full.enemies.size(), full.particles.size(), worstUpdate / 1e6);

        // Bot plays full games through the real update loop.
        int runs = 60;
        int[] waves = new int[runs];
        long totalSeconds = 0;
        boolean played = false, midDone = false;
        for (int seed = 0; seed < runs; seed++) {
            NeonSwarm.GamePanel p = panel(1000 + seed);
            p.startGame();
            int frames = 0;
            while (p.state != NeonSwarm.State.GAME_OVER && frames++ < 60 * 60 * 20) {
                if (p.state == NeonSwarm.State.UPGRADE) {
                    p.update(0.4);
                    p.chooseCard(p.rng.nextInt(p.choices.size()));
                    continue;
                }
                bot(p, 0.7);
                p.update(DT);
                if (!played && p.wave == 7 && p.enemies.size() >= 6 && !p.clearing) {
                    shot(p, "2-gameplay");
                    played = true;
                }
                if (!midDone && p.wave == 12 && p.enemies.size() >= 12) {
                    shot(p, "5-late");
                    midDone = true;
                }
            }
            waves[seed] = p.wave;
            totalSeconds += frames / 60;
        }
        Arrays.sort(waves);
        System.out.println("bot waves reached, sorted: " + Arrays.toString(waves));
        System.out.println("bot median wave " + waves[runs / 2] + ", average run " + totalSeconds / runs + "s");
        check(waves[runs / 2] >= 5, "a simple bot usually gets past the first boss");
        check(waves[runs - 1] < 120, "the game eventually ends");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
