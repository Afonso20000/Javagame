import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.Random;
import javax.imageio.ImageIO;

/** Checks the two currencies: coins buy permanent upgrades, diamonds buy ships and skins. */
public class CurrencyCheck {
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
        p.coins = 0;
        p.diamonds = 0;
        return p;
    }

    static void click(NeonOverdrive.GamePanel p, Rectangle r) {
        p.handleClick(r.x + r.width / 2, r.y + r.height / 2);
    }

    static void shot(NeonOverdrive.GamePanel p, String name) throws Exception {
        BufferedImage img = new BufferedImage(NeonOverdrive.W, NeonOverdrive.H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        p.render(g);
        g.dispose();
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }

    public static void main(String[] args) throws Exception {
        dir = args[0];
        new File(dir).mkdirs();

        // ---------- Spending ----------
        NeonOverdrive.GamePanel m = panel(1);
        // Permanent upgrades are bought in the workshop.
        m.state = NeonOverdrive.State.WORKSHOP;
        m.coins = 100;
        m.diamonds = 100;
        click(m, m.perkCard(0));
        m.state = NeonOverdrive.State.HANGAR;
        check(m.perks[0] == 1 && m.coins == 60 && m.diamonds == 100, "permanent upgrades cost coins only");
        m.coins = 100000;
        m.diamonds = 9;
        click(m, m.shipCard(NeonOverdrive.Ship.PHANTOM.ordinal()));
        check(!m.unlocked[NeonOverdrive.Ship.PHANTOM.ordinal()] && m.coins == 100000, "a ship cannot be bought with coins");
        m.diamonds = 10;
        click(m, m.shipCard(NeonOverdrive.Ship.PHANTOM.ordinal()));
        check(m.unlocked[NeonOverdrive.Ship.PHANTOM.ordinal()] && m.diamonds == 0, "Phantom costs 10 diamonds");

        // ---------- Skins ----------
        m.handleKey(KeyEvent.VK_K, true);
        check(m.state == NeonOverdrive.State.SKINS, "K opens the skins");
        m.diamonds = 3;
        click(m, m.skinCard(NeonOverdrive.Skin.CRIMSON.ordinal()));
        check(m.skin == NeonOverdrive.Skin.DEFAULT && !m.ownedSkins[NeonOverdrive.Skin.CRIMSON.ordinal()] && m.diamonds == 3,
                "not enough diamonds cannot buy a skin");
        m.diamonds = 10;
        click(m, m.skinCard(NeonOverdrive.Skin.CRIMSON.ordinal()));
        check(m.skin == NeonOverdrive.Skin.CRIMSON && m.diamonds == 6, "buying a skin costs diamonds and equips it");
        check(m.shipColor().equals(NeonOverdrive.Skin.CRIMSON.color), "the ship is drawn in the skin color");
        click(m, m.skinCard(NeonOverdrive.Skin.DEFAULT.ordinal()));
        check(m.skin == NeonOverdrive.Skin.DEFAULT && m.shipColor().equals(m.ship.color), "the factory color is free and always owned");
        click(m, m.skinCard(NeonOverdrive.Skin.CRIMSON.ordinal()));
        check(m.skin == NeonOverdrive.Skin.CRIMSON && m.diamonds == 6, "equipping an owned skin is free");
        m.mouseX = m.skinCard(2).x + 10;
        m.mouseY = m.skinCard(2).y + 10;
        shot(m, "cur-01-skins");
        m.handleKey(KeyEvent.VK_ESCAPE, true);
        check(m.state == NeonOverdrive.State.HANGAR, "ESC leaves the skins");
        click(m, m.skinsButton());
        check(m.state == NeonOverdrive.State.SKINS, "the skins button opens the skins");
        click(m, m.skinsBackButton());
        check(m.state == NeonOverdrive.State.HANGAR, "the back button leaves the skins");
        m.diamonds = 12;
        m.coins = 4835;
        m.mouseX = 0;
        m.mouseY = 0;
        shot(m, "cur-02-hangar");

        // ---------- Saving ----------
        File save = new File(dir, "currency-test.properties");
        save.delete();
        m.saveFile = save;
        m.save();
        NeonOverdrive.GamePanel r = panel(2);
        r.saveFile = save;
        r.loadSave();
        check(r.diamonds == 12 && r.skin == NeonOverdrive.Skin.CRIMSON && r.ownedSkins[NeonOverdrive.Skin.CRIMSON.ordinal()]
                && !r.ownedSkins[NeonOverdrive.Skin.GOLD.ordinal()], "diamonds and skins are saved");
        java.nio.file.Files.write(save.toPath(), "coins=50\nselectedSkin=GOLD\nskin.GOLD=false\n".getBytes());
        NeonOverdrive.GamePanel cheat = panel(3);
        cheat.saveFile = save;
        cheat.loadSave();
        check(cheat.skin == NeonOverdrive.Skin.DEFAULT && cheat.diamonds == 0 && cheat.coins == 50,
                "an old save without diamonds loads, and an unowned skin is not equipped");
        save.delete();

        // ---------- Earning ----------
        NeonOverdrive.GamePanel a = panel(4);
        a.unlocked[NeonOverdrive.Ship.PHANTOM.ordinal()] = false;
        a.bestWave = 10;
        a.checkAchievements();
        check(a.achieved[NeonOverdrive.Achievement.DEEP_RUN.ordinal()] && a.coins == 30 && a.diamonds == 3,
                "an achievement pays coins and diamonds");

        NeonOverdrive.GamePanel b = panel(5);
        b.startGame();
        b.spawnQueue.clear();
        b.wave = 4;
        b.nextWave();
        // Skip the boss spawn delay, then kill the boss.
        for (int i = 0; i < 200 && b.enemies.isEmpty(); i++) b.update(DT);
        NeonOverdrive.Enemy boss = null;
        for (NeonOverdrive.Enemy e : b.enemies) if (e.type.isBoss()) boss = e;
        check(boss != null, "wave 5 spawns a boss");
        boss.spawnTimer = 0;
        int beforeBoss = b.diamonds;
        b.damage(boss, boss.hp + 1, false);
        check(boss.dead, "the boss dies");
        int achievementGems = b.diamonds - beforeBoss;
        check(achievementGems >= NeonOverdrive.Achievement.BOSS_SLAYER.diamonds, "boss achievements pay their diamonds right away (" + achievementGems + ")");
        // Clear the boss's escorts so the wave can end.
        b.spawnQueue.clear();
        for (NeonOverdrive.Enemy e : b.enemies) e.dead = true;
        b.newEnemies.clear();
        for (int i = 0; i < 60 * 8 && b.state == NeonOverdrive.State.PLAYING; i++) {
            b.invulnerable = 999;
            b.update(DT);
        }
        check(b.runDiamonds == 2, "a boss drops 2 diamonds, and the wave clear pulls them in (" + b.runDiamonds + ")");
        check(b.diamonds == beforeBoss + achievementGems, "run diamonds are not banked until the run ends");
        int beforeEnd = b.diamonds;
        b.endRun();
        check(b.diamonds == beforeEnd + 2, "ending the run banks its diamonds");

        // Rare drops: kill many elites and count.
        NeonOverdrive.GamePanel d = panel(6);
        d.startGame();
        d.spawnQueue.clear();
        int drops = 0;
        for (int i = 0; i < 2000; i++) {
            NeonOverdrive.Enemy e = new NeonOverdrive.Enemy(NeonOverdrive.EnemyType.CHASER, 500, 350, 1);
            e.elite = true;
            d.pickups.clear();
            invokeDropLoot(d, e);
            for (Object p : d.pickups) if (((NeonOverdrive.Pickup) p).type == NeonOverdrive.PickupType.DIAMOND) drops++;
        }
        check(drops > 50 && drops < 160, "about 1 in 20 elites drop a diamond (" + drops + " of 2000)");

        NeonOverdrive.GamePanel day = panel(7);
        day.dateOverride = LocalDate.of(2026, 11, 17);
        day.startDaily();
        day.endRun();
        check(day.diamonds >= 1 && day.dailyNote.contains("diamond"), "the first daily run of the day pays a diamond");

        // ---------- Upgrade cards cost the coins picked up this run ----------
        NeonOverdrive.GamePanel u = panel(8);
        u.startGame();
        u.spawnQueue.clear();
        u.choices.clear();
        u.choices.add(NeonOverdrive.Upgrade.RAPID);       // common: 5
        u.choices.add(NeonOverdrive.Upgrade.MULTI);       // rare: 10
        u.choices.add(NeonOverdrive.Upgrade.SECOND_LIFE); // legendary: 20
        u.state = NeonOverdrive.State.UPGRADE;
        u.cardDelay = 0;
        u.coins = 5000;
        u.runCoins = 12;
        check(u.cardPrice(0) == 5 && u.cardPrice(1) == 10 && u.cardPrice(2) == 20, "prices go up with rarity");
        u.chooseCard(2);
        check(u.state == NeonOverdrive.State.UPGRADE && u.runCoins == 12 && u.stacks[NeonOverdrive.Upgrade.SECOND_LIFE.ordinal()] == 0,
                "a card you cannot afford is refused");
        check(u.noticeTimer > 0 && u.notice.contains("8 more coins"), "and it says how many coins are missing");
        shot(u, "cur-03-upgrade-prices");
        u.chooseCard(1);
        check(u.state == NeonOverdrive.State.PLAYING && u.runCoins == 2 && u.stacks[NeonOverdrive.Upgrade.MULTI.ordinal()] == 1,
                "buying a card takes its price from this run's coins");
        check(u.coins == 5000, "your saved coins are never spent on cards");
        check(u.wave == 2 && u.cardCost(NeonOverdrive.Upgrade.SECOND_LIFE) == 22, "prices rise 10% per wave");
        u.wave = 1;
        u.stacks[NeonOverdrive.Upgrade.RAPID.ordinal()] = 2;
        check(u.cardCost(NeonOverdrive.Upgrade.RAPID) == 8, "each level you own makes the next one dearer");

        // Broke: the cheapest card is free, the others are not.
        u.choices.clear();
        u.choices.add(NeonOverdrive.Upgrade.SECOND_LIFE);
        u.choices.add(NeonOverdrive.Upgrade.POWER);
        u.choices.add(NeonOverdrive.Upgrade.MULTI);
        u.state = NeonOverdrive.State.UPGRADE;
        u.cardDelay = 0;
        u.runCoins = 2;
        check(u.cardPrice(1) == 0 && u.cardPrice(0) == 20 && u.cardPrice(2) == 13, "when you cannot afford any card, the cheapest is free");
        shot(u, "cur-04-upgrade-broke");
        u.chooseCard(0);
        check(u.state == NeonOverdrive.State.UPGRADE, "the other cards stay locked");
        u.chooseCard(1);
        check(u.state == NeonOverdrive.State.PLAYING && u.runCoins == 2 && u.stacks[NeonOverdrive.Upgrade.POWER.ordinal()] == 1,
                "the free card costs nothing");
        u.runCoins = 5;
        u.wave = 1;
        u.choices.clear();
        u.choices.add(NeonOverdrive.Upgrade.POWER);
        u.choices.add(NeonOverdrive.Upgrade.CRIT);
        check(u.cardPrice(1) == 5 && u.cardPrice(0) == 7, "no free card while you can afford one");

        // ---------- Reset all progress from the main menu ----------
        File resetSave = new File(dir, "reset-test.properties");
        File resetRun = new File(dir, "reset-test_run.properties");
        File resetBackup = new File(dir, "reset-test_before_reset.properties");
        resetSave.delete();
        resetRun.delete();
        resetBackup.delete();
        NeonOverdrive.GamePanel z = panel(9);
        z.saveFile = resetSave;
        z.coins = 4835;
        z.diamonds = 40;
        java.util.Arrays.fill(z.unlocked, true);
        java.util.Arrays.fill(z.ownedSkins, true);
        z.skin = NeonOverdrive.Skin.RAINBOW;
        z.ship = NeonOverdrive.Ship.TITAN;
        z.perks[0] = 3;
        z.achieved[0] = true;
        z.totalKills = 4348;
        z.highScore = 999;
        z.dailyStreak = 4;
        z.dailyLastDate = "2026-11-04";
        z.sound.effectsOn = false;
        z.autoFire = true;
        z.startGame();
        z.save();
        check(resetRun.exists(), "(setup) a saved run exists");
        z.runActive = false; // like reopening the game after quitting mid-run
        int coinsBefore = z.coins, gemsBefore = z.diamonds;
        z.state = NeonOverdrive.State.MENU;

        click(z, z.resetButton());
        check(z.confirmReset && z.coins > 0, "the reset button only asks first");
        shot(z, "cur-05-reset-confirm");
        z.handleKey(KeyEvent.VK_N, true);
        check(!z.confirmReset && z.coins == coinsBefore && z.state == NeonOverdrive.State.MENU, "N cancels and keeps everything");
        z.handleKey(KeyEvent.VK_DELETE, true);
        check(z.confirmReset, "DEL also opens the question");
        z.handleKey(KeyEvent.VK_ENTER, true);
        check(z.confirmReset && z.state == NeonOverdrive.State.MENU, "ENTER does not skip past the question");
        click(z, z.resetNoButton());
        check(!z.confirmReset && z.coins == coinsBefore, "the cancel button keeps everything");
        click(z, z.resetButton());
        click(z, z.resetYesButton());
        check(!z.confirmReset && z.coins == 0 && z.diamonds == 0 && z.highScore == 0 && z.totalKills == 0,
                "yes wipes coins, diamonds and records");
        check(z.unlocked[0] && z.unlocked[1] && !z.unlocked[2] && !z.unlocked[3] && z.ship == NeonOverdrive.Ship.STRIKER,
                "only the two starting ships are left");
        check(z.skin == NeonOverdrive.Skin.DEFAULT && z.ownedSkins[0] && !z.ownedSkins[NeonOverdrive.Skin.RAINBOW.ordinal()],
                "skins are wiped");
        check(z.perks[0] == 0 && !z.achieved[0] && z.dailyStreak == 0 && z.dailyLastDate.isEmpty(),
                "permanent upgrades, achievements and the daily streak are wiped");
        check(!z.sound.effectsOn && z.autoFire, "sound and auto-fire settings are kept");
        check(!resetRun.exists() && !z.hasSavedRun(), "the saved run is deleted");
        check(resetBackup.exists(), "a backup of the old save is kept");
        shot(z, "cur-06-after-reset");
        NeonOverdrive.GamePanel z2 = panel(10);
        z2.saveFile = resetSave;
        z2.coins = 123;
        z2.loadSave();
        check(z2.coins == 0 && z2.diamonds == 0 && z2.skin == NeonOverdrive.Skin.DEFAULT && !z2.unlocked[3], "the reset is saved to disk");
        NeonOverdrive.GamePanel z3 = panel(11);
        z3.saveFile = resetBackup;
        z3.loadSave();
        check(z3.coins == coinsBefore && z3.diamonds == gemsBefore, "the backup still has the old progress");
        resetSave.delete();
        resetBackup.delete();

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void invokeDropLoot(NeonOverdrive.GamePanel p, NeonOverdrive.Enemy e) throws Exception {
        java.lang.reflect.Method m = NeonOverdrive.GamePanel.class.getDeclaredMethod("dropLoot", NeonOverdrive.Enemy.class);
        m.setAccessible(true);
        m.invoke(p, e);
    }
}
