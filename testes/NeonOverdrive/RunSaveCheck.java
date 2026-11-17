import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Random;
import javax.imageio.ImageIO;

/** Checks that quitting mid-run saves the run, and that it can be continued, replaced or dropped. */
public class RunSaveCheck {
    static int failures;
    static String dir;
    static File saveTest, runTest;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }

    /** A panel that uses the test save file, as if the game had just been opened. */
    static NeonOverdrive.GamePanel open(long seed) {
        NeonOverdrive.GamePanel p = new NeonOverdrive.GamePanel();
        p.saveFile = saveTest;
        p.loadSave();
        p.refreshSavedRun();
        p.rng = new Random(seed);
        p.state = NeonOverdrive.State.HANGAR;
        return p;
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
        saveTest = new File(dir, "run-save-test.properties");
        runTest = new File(dir, "run-save-test_run.properties");
        saveTest.delete();
        runTest.delete();

        // ---------- Play a few waves, then close the window mid-wave ----------
        NeonOverdrive.GamePanel a = open(1);
        a.coins = 100;
        a.startGame();
        check(runTest.exists(), "starting a run saves it straight away");
        a.applyUpgrade(NeonOverdrive.Upgrade.MULTI);
        a.applyUpgrade(NeonOverdrive.Upgrade.DRONE);
        a.applyUpgrade(NeonOverdrive.Upgrade.POWER);
        a.applyUpgrade(NeonOverdrive.Upgrade.SECOND_LIFE);
        a.score = 5000;
        a.kills = 80;
        a.runCoins = 33;
        a.bombs = 4;
        a.nextWave();
        a.nextWave();
        int wave = a.wave, health = a.health, shots = a.shots, drones = a.drones.size();
        double damage = a.bulletDamage;
        // Things that happen after the wave started are not part of the save.
        a.score = 9999;
        a.health = 1;
        a.saveOnExit();
        check(runTest.exists(), "closing the window mid-run keeps the run file");
        check(a.coins == 100 && a.totalRuns == 0, "closing does not end the run or bank its coins yet");

        NeonOverdrive.GamePanel b = open(2);
        check(b.hasSavedRun(), "reopening the game finds the saved run");
        check(b.coins == 100, "progress coins are unchanged after reopening");
        shot(b, "run-01-hangar-continue");
        b.handleKey(KeyEvent.VK_C, true);
        check(b.state == NeonOverdrive.State.PLAYING && b.runActive, "C in the hangar continues the run");
        check(b.wave == wave, "the run continues on the wave it was saved on (" + b.wave + ")");
        check(b.score == 5000 && b.kills == 80 && b.runCoins == 33, "score, kills and run coins come back");
        check(b.health == health && b.bombs == 4 && b.secondLives == 1, "health, bombs and second life come back");
        check(b.shots == shots && Math.abs(b.bulletDamage - damage) < 1e-9 && b.drones.size() == drones,
                "weapon upgrades and drones come back");
        check(b.stacks[NeonOverdrive.Upgrade.SECOND_LIFE.ordinal()] == 1, "upgrade stacks come back, so maxed cards stay hidden");
        check(!b.spawnQueue.isEmpty(), "the wave starts again with enemies to fight");
        check(b.ship == a.ship && b.difficulty == a.difficulty, "same ship and difficulty");
        shot(b, "run-02-continued");

        // Dying ends the run for good.
        b.endRun();
        check(!runTest.exists() && !b.hasSavedRun(), "ending the run deletes the saved run");
        check(b.coins == 133 && b.totalRuns == 1, "ending the run banks its coins once");

        // ---------- Close on the upgrade screen ----------
        NeonOverdrive.GamePanel c = open(3);
        c.startGame();
        c.rollChoices();
        c.state = NeonOverdrive.State.UPGRADE;
        c.saveRun();
        java.util.List<NeonOverdrive.Upgrade> cards = new java.util.ArrayList<NeonOverdrive.Upgrade>(c.choices);
        int cWave = c.wave;
        c.saveOnExit();
        NeonOverdrive.GamePanel d = open(4);
        { java.awt.Rectangle r = d.continueButton(); d.handleClick(r.x + r.width / 2, r.y + r.height / 2); }
        check(d.state == NeonOverdrive.State.UPGRADE && d.choices.equals(cards) && d.wave == cWave,
                "closing on the upgrade screen brings back the same cards");
        d.cardDelay = 0;
        d.runCoins = 1000;
        d.chooseCard(0);
        check(d.state == NeonOverdrive.State.PLAYING && d.wave == cWave + 1, "picking a card goes on to the next wave");
        int leftover = d.runCoins;
        d.saveOnExit();

        // ---------- A new run replaces the saved one ----------
        NeonOverdrive.GamePanel e = open(5);
        e.runCoins = 0;
        int before = e.coins, runsBefore = e.totalRuns;
        e.state = NeonOverdrive.State.HANGAR;
        e.mouseX = e.launchButton().x + 5;
        e.mouseY = e.launchButton().y + 5;
        shot(e, "run-03-hangar-new-run-hover");
        { java.awt.Rectangle r = e.launchButton(); e.handleClick(r.x + r.width / 2, r.y + r.height / 2); }
        check(e.wave == 1 && e.state == NeonOverdrive.State.PLAYING, "NEW RUN starts at wave 1");
        check(e.coins == before + leftover && e.totalRuns == runsBefore + 1, "the replaced run is counted once, and its leftover coins are kept");
        e.endRun();
        check(!runTest.exists(), "no saved run left behind");

        // ---------- Daily runs ----------
        NeonOverdrive.GamePanel f = open(6);
        Arrays.fill(f.unlocked, true);
        f.dateOverride = LocalDate.of(2026, 11, 17);
        NeonOverdrive.Ship daily = f.dailyShip();
        f.ship = daily == NeonOverdrive.Ship.TITAN ? NeonOverdrive.Ship.STRIKER : NeonOverdrive.Ship.TITAN;
        NeonOverdrive.Ship own = f.ship;
        f.startDaily();
        NeonOverdrive.Modifier mod = f.modifier;
        f.saveOnExit();
        NeonOverdrive.GamePanel g = open(7);
        g.dateOverride = LocalDate.of(2026, 11, 17);
        check(g.ship == own, "with a saved daily run, the hangar still shows your own ship");
        g.continueRun();
        check(g.dailyRun && g.ship == daily && g.modifier == mod, "a daily run continues with its ship and modifier");
        g.endRun();
        g.goToHangar();
        check(g.ship == own && !g.dailyRun, "after the daily run, your own ship is back");

        // ---------- Damaged files ----------
        java.nio.file.Files.write(runTest.toPath(), "ship=STRIKER\nwave=banana\n".getBytes());
        NeonOverdrive.GamePanel h = open(8);
        boolean ok = h.continueRun();
        check(!ok && !runTest.exists() && h.state == NeonOverdrive.State.HANGAR && !h.runActive,
                "a damaged saved run is dropped without crashing");

        // ---------- Tests with no save file never write one ----------
        NeonOverdrive.GamePanel n = new NeonOverdrive.GamePanel();
        n.saveFile = null;
        n.startGame();
        n.saveOnExit();
        check(!n.hasSavedRun(), "with no save file there is no saved run");

        saveTest.delete();
        runTest.delete();
        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
