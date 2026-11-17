public class SoundCheck {
    public static void main(String[] args) throws Exception {
        NeonSwarm.Sound sound = new NeonSwarm.Sound();
        sound.load();
        for (int i = 0; i < 50 && sound.loadedCount() < 10; i++) Thread.sleep(100);
        System.out.println("sound effects loaded: " + sound.loadedCount() + " of 10");
        // Rapid repeat playing must not block the game thread.
        long t0 = System.nanoTime();
        for (int i = 0; i < 200; i++) sound.play(NeonSwarm.Sound.Effect.values()[i % 10]);
        System.out.printf("200 play calls took %.2f ms on the caller%n", (System.nanoTime() - t0) / 1e6);
    }
}
