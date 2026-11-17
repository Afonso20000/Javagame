import java.awt.event.KeyEvent;
import java.util.Random;

public class Trace {
    public static void main(String[] args) throws Exception {
        ScheduleRush.GamePanel b = new ScheduleRush.GamePanel();
        b.random = new Random(0);
        b.startGame();
        int frames = 0, frameInClass = 0, shortest = 999999, day = 0, period = 0;
        double peak = 0;
        boolean shot = false;
        while ((b.state == ScheduleRush.State.PLAYING || b.state == ScheduleRush.State.DAY_END) && frames++ < 200000) {
            if (b.state == ScheduleRush.State.DAY_END) {
                GameCheck.press(b, KeyEvent.VK_ENTER);
                continue;
            }
            if (b.day != day || b.period != period) {
                day = b.day;
                period = b.period;
                frameInClass = 0;
                peak = 0;
            }
            GameCheck.botStep(b);
            int d = b.day, pr = b.period;
            b.update(GameCheck.DT);
            frameInClass++;
            peak = Math.max(peak, b.timeLeft);
            if (d == 3 && pr == 2 && !shot && frameInClass == 40) {
                GameCheck.shot(b, args[0], "2-playing-thursday");
                shot = true;
            }
            if (b.day != d || b.period != pr || b.state != ScheduleRush.State.PLAYING) {
                System.out.printf("day %d class %d: %.2fs of play, highest timer %.2f%n", d, pr, frameInClass / 60.0, peak);
                shortest = Math.min(shortest, frameInClass);
            }
        }
        System.out.printf("shortest class %.2fs, end state %s, on time %d%n", shortest / 60.0, b.state, b.countResults(ScheduleRush.GamePanel.ON_TIME));
        GameCheck.shot(b, args[0], "4-win");
    }
}
