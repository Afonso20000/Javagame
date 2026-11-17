import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Random;
import javax.imageio.ImageIO;

/** Throwaway checks: rules, reachability, and a bot playing with the real update loop. */
public class GameCheck {
    static final double DT = 1 / 60.0;
    static int failures = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }

    static void shot(ScheduleRush.GamePanel p, String dir, String name) throws Exception {
        BufferedImage img = new BufferedImage(1000, 640, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        p.render(g);
        g.dispose();
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }

    /** BFS from the player to the target door, treating other doors as walls. Returns next tile or null. */
    static int[] nextStep(ScheduleRush.GamePanel p, ScheduleRush.Room target) {
        int C = ScheduleRush.GamePanel.COLS, R = ScheduleRush.GamePanel.ROWS;
        int sc = (int) (p.playerX / 40), sr = (int) (p.playerY / 40);
        int[][] from = new int[R][C];
        for (int[] row : from) java.util.Arrays.fill(row, -1);
        ArrayDeque<int[]> q = new ArrayDeque<int[]>();
        q.add(new int[] {target.doorCol, target.doorRow});
        from[target.doorRow][target.doorCol] = -2;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            if (cur[0] == sc && cur[1] == sr) {
                int v = from[sr][sc];
                return v == -2 ? new int[] {sc, sr} : new int[] {v % 100, v / 100};
            }
            for (int[] d : dirs) {
                int c = cur[0] + d[0], r = cur[1] + d[1];
                if (c < 0 || r < 0 || c >= C || r >= R || from[r][c] != -1) continue;
                if (p.tiles[r][c] != ScheduleRush.GamePanel.FLOOR) continue;
                boolean otherDoor = false;
                for (ScheduleRush.Room o : p.rooms) {
                    if (o != target && o.doorCol == c && o.doorRow == r) otherDoor = true;
                }
                if (otherDoor) continue;
                from[r][c] = cur[1] * 100 + cur[0];
                q.add(new int[] {c, r});
            }
        }
        return null;
    }

    static boolean botStep(ScheduleRush.GamePanel p) {
        int[] n = nextStep(p, p.targetRoom());
        if (n == null) return false;
        double tx = n[0] * 40 + 20, ty = n[1] * 40 + 20;
        p.left = p.playerX > tx + 2;
        p.right = p.playerX < tx - 2;
        p.up = p.playerY > ty + 2;
        p.down = p.playerY < ty - 2;
        return true;
    }

    static void press(ScheduleRush.GamePanel p, int key) {
        p.keyPressed(new KeyEvent(p, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED));
    }

    public static void main(String[] args) throws Exception {
        String dir = args[0];
        ScheduleRush.GamePanel p = new ScheduleRush.GamePanel();
        p.random = new Random(7);
        shot(p, dir, "1-menu");
        press(p, KeyEvent.VK_ENTER);
        check(p.state == ScheduleRush.State.PLAYING, "ENTER starts the game");

        // Every door reachable from spawn on the real grid.
        for (ScheduleRush.Room r : p.rooms) check(nextStep(p, r) != null, "door reachable: " + r.name);

        // Monday first class is Math; go through wrong door (English) deliberately.
        p.monitors.clear();
        p.pickups.clear();
        double before = p.timeLeft;
        ScheduleRush.Room english = p.roomNamed("English");
        p.playerX = english.doorCol * 40 + 20;
        p.playerY = 4 * 40 + 20;
        p.up = true;
        for (int i = 0; i < 40 && p.timeLeft > before - 2; i++) p.update(DT);
        p.up = false;
        check(p.timeLeft < before - 2.5 && p.period == 0, "wrong door costs 3s and does not advance");
        check((int) (p.playerY / 40) == 4, "wrong door pushes player back out");
        double t2 = p.timeLeft;
        for (int i = 0; i < 5; i++) p.update(DT);
        check(p.timeLeft > t2 - 1, "wrong door penalty applies once");

        // Bell: tardy loses a heart and moves on.
        p.timeLeft = 0.01;
        p.update(DT);
        check(p.hearts == 2 && p.period == 1 && p.results[0][0] == ScheduleRush.GamePanel.LATE, "bell = late, -1 heart, next class");

        // Pausing freezes time.
        press(p, KeyEvent.VK_P);
        double t3 = p.timeLeft;
        for (int i = 0; i < 60; i++) p.update(DT);
        check(p.timeLeft == t3, "pause freezes the timer");
        press(p, KeyEvent.VK_P);

        // Monitor hit is a one-off with invulnerability.
        ScheduleRush.GamePanel h = new ScheduleRush.GamePanel();
        h.startGame();
        h.pickups.clear();
        h.monitors.clear();
        h.monitors.add(new ScheduleRush.Monitor(new int[][] {{8, 5}, {9, 5}}));
        double t4 = h.timeLeft;
        h.update(DT);
        h.update(DT);
        check(t4 - h.timeLeft > 3.9 && t4 - h.timeLeft < 4.2, "monitor costs 4s once while invulnerable");

        // Three lates = game over.
        ScheduleRush.GamePanel o = new ScheduleRush.GamePanel();
        o.startGame();
        for (int i = 0; i < 3; i++) { o.timeLeft = 0.001; o.update(DT); }
        check(o.state == ScheduleRush.State.GAME_OVER, "three lates = game over");
        shot(o, dir, "5-gameover");
        press(o, KeyEvent.VK_ENTER);
        check(o.state == ScheduleRush.State.PLAYING && o.score == 0 && o.hearts == 3 && o.countResults(ScheduleRush.GamePanel.LATE) == 0, "ENTER restarts cleanly after game over");

        // Bot plays whole weeks through the real update loop (takes the shortest path, never dodges).
        int wins = 0, totalOnTime = 0, runs = 40, minOn = 99;
        double worstMargin = 99;
        boolean shotsDone = false;
        for (int seed = 0; seed < runs; seed++) {
            ScheduleRush.GamePanel b = new ScheduleRush.GamePanel();
            b.random = new Random(seed);
            b.startGame();
            int frames = 0;
            while ((b.state == ScheduleRush.State.PLAYING || b.state == ScheduleRush.State.DAY_END) && frames++ < 200000) {
                if (b.state == ScheduleRush.State.DAY_END) {
                    if (!shotsDone && b.day == 0) shot(b, dir, "3-dayend");
                    press(b, KeyEvent.VK_ENTER);
                    continue;
                }
                if (!botStep(b)) { check(false, "bot found no path"); break; }
                int beforePeriod = b.period;
                double left = b.timeLeft;
                b.update(DT);
                if (b.period != beforePeriod && left < worstMargin && b.results[b.day][beforePeriod] == ScheduleRush.GamePanel.ON_TIME) worstMargin = left;
                if (!shotsDone && b.day == 3 && b.period == 2 && b.timeLeft < 8.5 && b.timeLeft > 8.4) {
                    shot(b, dir, "2-playing-thursday");
                }
            }
            if (!shotsDone && b.state == ScheduleRush.State.WIN) { shot(b, dir, "4-win"); shotsDone = true; }
            if (b.state == ScheduleRush.State.WIN) wins++;
            int on = b.countResults(ScheduleRush.GamePanel.ON_TIME);
            totalOnTime += on;
            minOn = Math.min(minOn, on);
        }
        System.out.println("bot: wins " + wins + "/" + runs + ", avg on-time " + (totalOnTime / (double) runs) + "/25, worst run " + minOn + ", tightest margin " + worstMargin + "s");
        check(wins > 0, "week is winnable by a bot that never dodges");
        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
