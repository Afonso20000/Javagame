import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Opens the real window, toggles fullscreen both ways, checks scaling, then exits. */
public class FsCheck {
    public static void main(String[] args) throws Exception {
        final JFrame frame = new JFrame("Neon Overdrive fullscreen test");
        final NeonOverdrive.GamePanel[] holder = new NeonOverdrive.GamePanel[1];
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { e.printStackTrace(); System.exit(2); });
        SwingUtilities.invokeAndWait(() -> {
            NeonOverdrive.GamePanel panel = new NeonOverdrive.GamePanel();
            panel.saveFile = null;
            holder[0] = panel;
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.start();
        });
        NeonOverdrive.GamePanel p = holder[0];
        Thread.sleep(600);
        System.out.println("windowed size " + p.getWidth() + "x" + p.getHeight() + " scale " + p.viewScale);
        SwingUtilities.invokeAndWait(() -> { p.handleKey(KeyEvent.VK_ENTER, true); p.startGame(); });
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> p.handleKey(KeyEvent.VK_F11, true));
        Thread.sleep(1000);
        System.out.println("fullscreen size " + p.getWidth() + "x" + p.getHeight() + " scale " + p.viewScale + " viewX " + p.viewX + " viewY " + p.viewY + " state " + p.state + " undecorated " + frame.isUndecorated());
        int cx = (int) (p.viewX + 500 * p.viewScale), cy = (int) (p.viewY + 350 * p.viewScale);
        System.out.println("screen centre maps to " + p.worldX(cx) + "," + p.worldY(cy));
        SwingUtilities.invokeAndWait(() -> p.handleKey(KeyEvent.VK_F11, true));
        Thread.sleep(800);
        System.out.println("back to window " + p.getWidth() + "x" + p.getHeight() + " scale " + p.viewScale + " undecorated " + frame.isUndecorated());
        System.exit(0);
    }
}
