import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * SCHEDULE RUSH - a 2D game about getting to class on time.
 *
 * Read your timetable, find the right room and get through its door before the
 * bell rings. Survive the whole week from Monday to Friday.
 *
 * Compile:  javac ScheduleRush.java
 * Run:      java ScheduleRush
 *
 * To use your own timetable, edit TIMES and SCHEDULE below. Every subject must
 * match the name of a room in ROOM_NAMES (you can rename those too).
 */
public class ScheduleRush {

    // ======================== EDIT YOUR TIMETABLE HERE ========================
    static final String[] ROOM_NAMES = {"Math", "English", "Science", "History", "Art", "Gym"};
    static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
    static final String[] TIMES = {"08:30", "09:30", "10:45", "13:00", "14:00"};
    static final String[][] SCHEDULE = {
        {"Math",    "English", "Science", "Art",     "Gym"},
        {"History", "Science", "Math",    "English", "Art"},
        {"English", "Gym",     "History", "Math",    "Science"},
        {"Science", "Art",     "English", "Gym",     "History"},
        {"Gym",     "Math",    "Art",     "History", "English"},
    };
    /** Seconds allowed per class on each day. The week gets harder as it goes. */
    static final double[] SECONDS_PER_CLASS = {12, 11, 10, 9, 8};
    // ==========================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("Schedule Rush");
                GamePanel panel = new GamePanel();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setResizable(false);
                frame.add(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                panel.requestFocusInWindow();
                panel.start();
            }
        });
    }

    enum State { MENU, PLAYING, PAUSED, DAY_END, GAME_OVER, WIN }

    enum PickupType { COFFEE, CLOCK, HOMEWORK }

    /** A classroom: a solid block on the map with one door. */
    static final class Room {
        final String name;
        final Color color;
        final int col, row, w, h, doorCol, doorRow, exitRow;

        Room(String name, Color color, int col, int row, int w, int h, int doorCol, int doorRow, int exitRow) {
            this.name = name;
            this.color = color;
            this.col = col;
            this.row = row;
            this.w = w;
            this.h = h;
            this.doorCol = doorCol;
            this.doorRow = doorRow;
            this.exitRow = exitRow;
        }
    }

    /** A hall monitor who walks around a fixed route. Touching one costs time. */
    static final class Monitor {
        final int[][] route;
        int next = 1;
        double x, y;

        Monitor(int[][] route) {
            this.route = route;
            x = GamePanel.center(route[0][0]);
            y = GamePanel.center(route[0][1]);
        }
    }

    static final class Pickup {
        final PickupType type;
        final double x, y;

        Pickup(PickupType type, double x, double y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }
    }

    static final class GamePanel extends JPanel implements ActionListener, KeyListener {
        static final int TILE = 40, COLS = 18, ROWS = 16;
        static final int MAP_W = COLS * TILE, MAP_H = ROWS * TILE, SIDE_W = 280;

        static final int FLOOR = 0, WALL = 1, LOCKER = 2, PLANT = 3, ROOM = 4;

        static final int PENDING = 0, ON_TIME = 1, LATE = 2;
        static final int MAX_HEARTS = 3;

        static final double PLAYER_SPEED = 180, COFFEE_MULT = 1.5, COFFEE_TIME = 5;
        static final double PLAYER_HALF = 12, HIT_DISTANCE = 26, PICKUP_DISTANCE = 26;
        static final double MONITOR_PENALTY = 4, WRONG_DOOR_PENALTY = 3, CLOCK_BONUS = 4, INVULNERABLE_TIME = 1.5;

        static final int[][][] MONITOR_ROUTES = {
            {{1, 4}, {16, 4}},
            {{16, 11}, {1, 11}},
            {{6, 4}, {6, 11}},
            {{11, 11}, {11, 4}},
            {{7, 5}, {10, 5}, {10, 10}, {7, 10}},
        };

        final int[][] tiles = new int[ROWS][COLS];
        final Room[] rooms = new Room[6];
        final List<Monitor> monitors = new ArrayList<Monitor>();
        final List<Pickup> pickups = new ArrayList<Pickup>();
        final int[][] results = new int[DAYS.length][TIMES.length];
        Random random = new Random();

        State state = State.MENU;
        int day, period, hearts, score, bestScore;
        double timeLeft, playerX, playerY;
        double coffeeTimer, invulnerableTimer, messageTimer, flashTimer, clock;
        String message = "";
        Color messageColor = Color.WHITE;
        boolean up, down, left, right;

        private final Timer timer = new Timer(16, this);
        private long lastTick;

        GamePanel() {
            setPreferredSize(new Dimension(MAP_W + SIDE_W, MAP_H));
            setBackground(new Color(30, 34, 45));
            setFocusable(true);
            addKeyListener(this);
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    releaseKeys();
                }
            });
            buildMap();
            checkTimetable();
        }

        void start() {
            lastTick = System.nanoTime();
            timer.start();
        }

        static double center(int tile) {
            return tile * TILE + TILE / 2.0;
        }

        // ------------------------------- SETUP -------------------------------

        private void buildMap() {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    boolean border = r == 0 || c == 0 || r == ROWS - 1 || c == COLS - 1;
                    tiles[r][c] = border ? WALL : FLOOR;
                }
            }
            Color[] colors = {
                new Color(66, 133, 214), new Color(214, 92, 92), new Color(70, 170, 110),
                new Color(190, 140, 60), new Color(170, 95, 195), new Color(40, 165, 170),
            };
            int[] cols = {1, 7, 13};
            for (int i = 0; i < 3; i++) {
                rooms[i] = new Room(ROOM_NAMES[i], colors[i], cols[i], 1, 4, 3, cols[i] + 1, 3, 4);
                rooms[i + 3] = new Room(ROOM_NAMES[i + 3], colors[i + 3], cols[i], 12, 4, 3, cols[i] + 2, 12, 11);
            }
            for (Room room : rooms) {
                fill(room.col, room.row, room.w, room.h, ROOM);
                tiles[room.doorRow][room.doorCol] = FLOOR;
            }
            fill(3, 6, 3, 1, LOCKER);
            fill(3, 9, 3, 1, LOCKER);
            fill(12, 6, 3, 1, LOCKER);
            fill(12, 9, 3, 1, LOCKER);
            fill(8, 7, 2, 2, PLANT);
        }

        private void fill(int col, int row, int w, int h, int type) {
            for (int r = row; r < row + h; r++) {
                for (int c = col; c < col + w; c++) {
                    tiles[r][c] = type;
                }
            }
        }

        /** Stops with a clear message if the timetable names a room that does not exist. */
        private void checkTimetable() {
            for (String[] daySchedule : SCHEDULE) {
                for (String subject : daySchedule) {
                    if (roomNamed(subject) == null) {
                        throw new IllegalArgumentException("SCHEDULE uses \"" + subject
                                + "\", which is not in ROOM_NAMES.");
                    }
                }
            }
        }

        Room roomNamed(String name) {
            for (Room room : rooms) {
                if (room.name.equals(name)) {
                    return room;
                }
            }
            return null;
        }

        Room targetRoom() {
            return roomNamed(SCHEDULE[day][period]);
        }

        void startGame() {
            day = 0;
            hearts = MAX_HEARTS;
            score = 0;
            for (int[] row : results) {
                java.util.Arrays.fill(row, PENDING);
            }
            startDay();
        }

        void startDay() {
            period = 0;
            timeLeft = SECONDS_PER_CLASS[day];
            playerX = center(8);
            playerY = center(5);
            coffeeTimer = 0;
            invulnerableTimer = 0;
            flashTimer = 0;
            releaseKeys();
            monitors.clear();
            for (int i = 0; i <= day && i < MONITOR_ROUTES.length; i++) {
                monitors.add(new Monitor(MONITOR_ROUTES[i]));
            }
            pickups.clear();
            topUpPickups();
            showMessage(DAYS[day] + " - first class: " + SCHEDULE[day][0], new Color(255, 225, 120));
            state = State.PLAYING;
        }

        private void releaseKeys() {
            up = false;
            down = false;
            left = false;
            right = false;
        }

        private void topUpPickups() {
            PickupType[] types = PickupType.values();
            while (pickups.size() < 3) {
                int c = 1 + random.nextInt(COLS - 2);
                int r = 1 + random.nextInt(ROWS - 2);
                boolean farFromPlayer = Math.hypot(center(c) - playerX, center(r) - playerY) > 2 * TILE;
                if (tiles[r][c] == FLOOR && !isDoor(c, r) && farFromPlayer) {
                    pickups.add(new Pickup(types[random.nextInt(types.length)], center(c), center(r)));
                }
            }
        }

        private boolean isDoor(int col, int row) {
            return roomAtDoor(col, row) != null;
        }

        private Room roomAtDoor(int col, int row) {
            for (Room room : rooms) {
                if (room.doorCol == col && room.doorRow == row) {
                    return room;
                }
            }
            return null;
        }

        // ------------------------------- UPDATE ------------------------------

        @Override
        public void actionPerformed(ActionEvent e) {
            long now = System.nanoTime();
            double dt = Math.min((now - lastTick) / 1e9, 0.05);
            lastTick = now;
            update(dt);
            repaint();
        }

        void update(double dt) {
            clock += dt;
            messageTimer = Math.max(0, messageTimer - dt);
            flashTimer = Math.max(0, flashTimer - dt);
            if (state != State.PLAYING) {
                return;
            }
            coffeeTimer = Math.max(0, coffeeTimer - dt);
            invulnerableTimer = Math.max(0, invulnerableTimer - dt);

            movePlayer(dt);
            moveMonitors(dt);
            collectPickups();

            Room door = roomAtDoor((int) (playerX / TILE), (int) (playerY / TILE));
            if (door != null) {
                enterDoor(door);
                return;
            }

            timeLeft -= dt;
            if (timeLeft <= 0) {
                bellRang();
            }
        }

        private void movePlayer(double dt) {
            double dx = (right ? 1 : 0) - (left ? 1 : 0);
            double dy = (down ? 1 : 0) - (up ? 1 : 0);
            if (dx == 0 && dy == 0) {
                return;
            }
            double length = Math.hypot(dx, dy);
            double speed = PLAYER_SPEED * (coffeeTimer > 0 ? COFFEE_MULT : 1);
            double stepX = dx / length * speed * dt;
            double stepY = dy / length * speed * dt;
            // Moving one axis at a time lets the player slide along walls.
            if (!hitsWall(playerX + stepX, playerY)) {
                playerX += stepX;
            }
            if (!hitsWall(playerX, playerY + stepY)) {
                playerY += stepY;
            }
        }

        private boolean hitsWall(double x, double y) {
            int c1 = (int) ((x - PLAYER_HALF) / TILE), c2 = (int) ((x + PLAYER_HALF) / TILE);
            int r1 = (int) ((y - PLAYER_HALF) / TILE), r2 = (int) ((y + PLAYER_HALF) / TILE);
            for (int r = r1; r <= r2; r++) {
                for (int c = c1; c <= c2; c++) {
                    if (tiles[r][c] != FLOOR) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void moveMonitors(double dt) {
            double speed = 70 + 12 * day;
            for (Monitor m : monitors) {
                double tx = center(m.route[m.next][0]), ty = center(m.route[m.next][1]);
                double dist = Math.hypot(tx - m.x, ty - m.y);
                double step = speed * dt;
                if (dist <= step) {
                    m.x = tx;
                    m.y = ty;
                    m.next = (m.next + 1) % m.route.length;
                } else {
                    m.x += (tx - m.x) / dist * step;
                    m.y += (ty - m.y) / dist * step;
                }
                boolean touching = Math.hypot(m.x - playerX, m.y - playerY) < HIT_DISTANCE;
                if (touching && invulnerableTimer <= 0) {
                    timeLeft -= MONITOR_PENALTY;
                    invulnerableTimer = INVULNERABLE_TIME;
                    showMessage("\"No running in the halls!\"  -" + (int) MONITOR_PENALTY + "s", new Color(255, 170, 90));
                    Toolkit.getDefaultToolkit().beep();
                }
            }
        }

        private void collectPickups() {
            boolean collected = false;
            for (Iterator<Pickup> it = pickups.iterator(); it.hasNext(); ) {
                Pickup p = it.next();
                if (Math.hypot(p.x - playerX, p.y - playerY) < PICKUP_DISTANCE) {
                    it.remove();
                    collected = true;
                    switch (p.type) {
                        case COFFEE:
                            coffeeTimer = COFFEE_TIME;
                            showMessage("Coffee! Speed boost", new Color(230, 190, 140));
                            break;
                        case CLOCK:
                            timeLeft += CLOCK_BONUS;
                            showMessage("Extra time: +" + (int) CLOCK_BONUS + "s", new Color(150, 220, 255));
                            break;
                        default:
                            score += 50;
                            showMessage("Homework handed in: +50", new Color(190, 240, 160));
                            break;
                    }
                }
            }
            if (collected) {
                topUpPickups();
            }
        }

        private void enterDoor(Room door) {
            Room target = targetRoom();
            if (door != target) {
                // Step back out of the door so the penalty only applies once.
                playerX = center(door.doorCol);
                playerY = center(door.exitRow);
                timeLeft -= WRONG_DOOR_PENALTY;
                showMessage("Wrong class! This is " + door.name + "  -" + (int) WRONG_DOOR_PENALTY + "s",
                        new Color(255, 130, 130));
                Toolkit.getDefaultToolkit().beep();
                if (timeLeft <= 0) {
                    bellRang();
                }
                return;
            }
            int earned = 100 + (int) (timeLeft * 10) * (day + 1);
            score += earned;
            results[day][period] = ON_TIME;
            showMessage("On time for " + target.name + "!  +" + earned, new Color(150, 240, 150));
            // The next class starts from the door of this one.
            playerX = center(door.doorCol);
            playerY = center(door.exitRow);
            nextPeriod();
        }

        private void bellRang() {
            results[day][period] = LATE;
            hearts--;
            flashTimer = 0.5;
            Toolkit.getDefaultToolkit().beep();
            showMessage("BRRRING! Late for " + SCHEDULE[day][period] + "!", new Color(255, 110, 110));
            if (hearts <= 0) {
                endGame(State.GAME_OVER);
            } else {
                nextPeriod();
            }
        }

        private void nextPeriod() {
            period++;
            if (period < TIMES.length) {
                timeLeft = SECONDS_PER_CLASS[day];
            } else if (day == DAYS.length - 1) {
                endGame(State.WIN);
            } else {
                releaseKeys();
                state = State.DAY_END;
            }
        }

        private void endGame(State end) {
            bestScore = Math.max(bestScore, score);
            releaseKeys();
            state = end;
        }

        int countResults(int kind) {
            int count = 0;
            for (int[] row : results) {
                for (int value : row) {
                    if (value == kind) {
                        count++;
                    }
                }
            }
            return count;
        }

        String grade() {
            double share = countResults(ON_TIME) / (double) (DAYS.length * TIMES.length);
            if (share >= 0.9) return "A";
            if (share >= 0.75) return "B";
            if (share >= 0.6) return "C";
            if (share >= 0.4) return "D";
            return "F";
        }

        private void showMessage(String text, Color color) {
            message = text;
            messageColor = color;
            messageTimer = 2.2;
        }

        // ------------------------------ DRAWING ------------------------------

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            render((Graphics2D) graphics);
        }

        void render(Graphics2D g) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fillRect(0, 0, MAP_W + SIDE_W, MAP_H);
            drawMap(g);
            if (state == State.MENU) {
                drawMenu(g);
                return;
            }
            drawPickups(g);
            for (Monitor m : monitors) {
                drawMonitor(g, m);
            }
            drawPlayer(g);
            drawSidePanel(g);
            if (flashTimer > 0) {
                g.setColor(new Color(220, 40, 40, (int) (140 * flashTimer / 0.5)));
                g.fillRect(0, 0, MAP_W, MAP_H);
            }
            if (messageTimer > 0 && state == State.PLAYING) {
                drawMessage(g);
            }
            if (state == State.PAUSED) {
                drawOverlay(g, "Paused", new String[] {"Press P to keep playing"});
            } else if (state == State.DAY_END) {
                drawOverlay(g, DAYS[day] + " is over!", new String[] {
                    "On time so far: " + countResults(ON_TIME) + "    Late: " + countResults(LATE),
                    "Score: " + score,
                    "",
                    "Press ENTER for " + DAYS[day + 1],
                });
            } else if (state == State.WIN || state == State.GAME_OVER) {
                drawReport(g);
            }
        }

        private void drawMap(Graphics2D g) {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int x = c * TILE, y = r * TILE;
                    switch (tiles[r][c]) {
                        case WALL:
                            g.setColor(new Color(58, 62, 78));
                            g.fillRect(x, y, TILE, TILE);
                            break;
                        case LOCKER:
                            g.setColor(new Color(92, 118, 150));
                            g.fillRect(x, y, TILE, TILE);
                            g.setColor(new Color(60, 80, 106));
                            g.drawRect(x + 1, y + 1, TILE - 3, TILE - 3);
                            g.fillRect(x + TILE - 12, y + 16, 4, 8);
                            break;
                        case PLANT:
                            g.setColor(new Color(120, 92, 66));
                            g.fillRect(x, y, TILE, TILE);
                            g.setColor(new Color(72, 150, 84));
                            g.fillOval(x + 3, y + 3, TILE - 6, TILE - 6);
                            break;
                        default:
                            g.setColor((r + c) % 2 == 0 ? new Color(226, 222, 208) : new Color(214, 210, 196));
                            g.fillRect(x, y, TILE, TILE);
                            break;
                    }
                }
            }
            for (Room room : rooms) {
                drawRoom(g, room);
            }
        }

        private void drawRoom(Graphics2D g, Room room) {
            int x = room.col * TILE, y = room.row * TILE, w = room.w * TILE, h = room.h * TILE;
            g.setColor(room.color);
            g.fillRect(x, y, w, h);
            g.setColor(room.color.darker());
            g.setStroke(new BasicStroke(3));
            g.drawRect(x + 1, y + 1, w - 3, h - 3);
            g.setStroke(new BasicStroke(1));

            // Keeps the name clear of the door, which sits on one edge of the room.
            int textY = room.doorRow == room.row ? y + h / 2 + 34 : y + h / 2 - 6;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            g.setColor(Color.WHITE);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(room.name, x + (w - metrics.stringWidth(room.name)) / 2, textY);

            int doorX = room.doorCol * TILE, doorY = room.doorRow * TILE;
            g.setColor(new Color(112, 74, 44));
            g.fillRect(doorX + 2, doorY, TILE - 4, TILE);
            g.setColor(new Color(240, 205, 110));
            g.fillOval(doorX + TILE - 13, doorY + TILE / 2 - 3, 6, 6);

            // Monday is the tutorial: the right door glows. After that, read the timetable!
            boolean hint = state == State.PLAYING && day == 0 && room == targetRoom();
            if (hint) {
                int alpha = 130 + (int) (120 * Math.abs(Math.sin(clock * 4)));
                g.setColor(new Color(255, 225, 90, alpha));
                g.setStroke(new BasicStroke(4));
                g.drawRect(doorX, doorY, TILE - 1, TILE - 1);
                g.setStroke(new BasicStroke(1));
            }
        }

        private void drawPickups(Graphics2D g) {
            double bob = Math.sin(clock * 4) * 2;
            for (Pickup p : pickups) {
                int x = (int) p.x, y = (int) (p.y + bob);
                switch (p.type) {
                    case COFFEE:
                        g.setColor(Color.WHITE);
                        g.fillRoundRect(x - 8, y - 9, 14, 18, 4, 4);
                        g.setColor(new Color(110, 70, 40));
                        g.fillRect(x - 8, y - 9, 14, 6);
                        g.setColor(Color.WHITE);
                        g.setStroke(new BasicStroke(2));
                        g.drawOval(x + 4, y - 3, 7, 8);
                        g.setStroke(new BasicStroke(1));
                        break;
                    case CLOCK:
                        g.setColor(Color.WHITE);
                        g.fillOval(x - 10, y - 10, 20, 20);
                        g.setColor(new Color(40, 110, 200));
                        g.setStroke(new BasicStroke(2));
                        g.drawOval(x - 10, y - 10, 20, 20);
                        g.drawLine(x, y, x, y - 7);
                        g.drawLine(x, y, x + 5, y);
                        g.setStroke(new BasicStroke(1));
                        break;
                    default:
                        g.setColor(new Color(250, 250, 240));
                        g.fillRect(x - 8, y - 10, 16, 20);
                        g.setColor(new Color(90, 150, 90));
                        g.drawRect(x - 8, y - 10, 16, 20);
                        g.drawLine(x - 5, y - 5, x + 5, y - 5);
                        g.drawLine(x - 5, y, x + 5, y);
                        g.drawLine(x - 5, y + 5, x + 2, y + 5);
                        break;
                }
            }
        }

        private void drawMonitor(Graphics2D g, Monitor m) {
            int x = (int) m.x, y = (int) m.y;
            g.setColor(new Color(0, 0, 0, 50));
            g.fillOval(x - 13, y - 9, 28, 26);
            g.setColor(new Color(240, 130, 40));
            g.fillOval(x - 14, y - 14, 28, 28);
            g.setColor(new Color(250, 240, 120));
            g.fillRect(x - 14, y - 2, 28, 4);
            g.setColor(new Color(245, 205, 165));
            g.fillOval(x - 8, y - 12, 16, 16);
            g.setColor(Color.BLACK);
            g.fillOval(x - 5, y - 7, 3, 3);
            g.fillOval(x + 2, y - 7, 3, 3);
        }

        private void drawPlayer(Graphics2D g) {
            if (invulnerableTimer > 0 && (int) (clock * 12) % 2 == 0) {
                return;
            }
            int x = (int) playerX, y = (int) playerY;
            if (coffeeTimer > 0) {
                g.setColor(new Color(255, 200, 90, 110));
                g.fillOval(x - 19, y - 19, 38, 38);
            }
            g.setColor(new Color(0, 0, 0, 50));
            g.fillOval(x - 12, y - 8, 26, 24);
            g.setColor(new Color(200, 60, 70));
            g.fillRoundRect(x - 14, y - 8, 28, 18, 8, 8);
            g.setColor(new Color(40, 90, 190));
            g.fillOval(x - 12, y - 12, 24, 24);
            g.setColor(new Color(245, 205, 165));
            g.fillOval(x - 8, y - 11, 16, 16);
            g.setColor(new Color(70, 45, 30));
            g.fillArc(x - 8, y - 12, 16, 12, 0, 180);
            g.setColor(Color.BLACK);
            g.fillOval(x - 5, y - 4, 3, 3);
            g.fillOval(x + 2, y - 4, 3, 3);
        }

        private void drawSidePanel(Graphics2D g) {
            int x = MAP_W + 16, w = SIDE_W - 32;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            g.setColor(Color.WHITE);
            g.drawString(DAYS[day], x, 36);

            boolean dayFinished = period >= TIMES.length;
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.setColor(new Color(170, 178, 195));
            g.drawString("GO TO", x, 70);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            g.setColor(new Color(255, 225, 120));
            g.drawString(dayFinished ? "Home!" : SCHEDULE[day][period], x, 106);

            if (!dayFinished) {
                double share = Math.max(0, Math.min(1, timeLeft / SECONDS_PER_CLASS[day]));
                g.setColor(new Color(60, 66, 84));
                g.fillRoundRect(x, 120, w, 16, 8, 8);
                g.setColor(share > 0.5 ? new Color(90, 200, 120) : share > 0.25 ? new Color(240, 190, 70) : new Color(235, 85, 85));
                g.fillRoundRect(x, 120, (int) (w * share), 16, 8, 8);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                g.setColor(Color.WHITE);
                g.drawString(String.format("%.1f s until the bell", Math.max(0, timeLeft)), x, 156);
            }

            g.setColor(new Color(170, 178, 195));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.drawString("TODAY'S TIMETABLE", x, 192);
            for (int i = 0; i < TIMES.length; i++) {
                int y = 204 + i * 42;
                boolean current = i == period;
                g.setColor(current ? new Color(74, 84, 112) : new Color(44, 49, 64));
                g.fillRoundRect(x, y, w, 36, 8, 8);
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
                g.setColor(new Color(170, 178, 195));
                g.drawString(TIMES[i], x + 10, y + 23);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                g.setColor(Color.WHITE);
                g.drawString(SCHEDULE[day][i], x + 70, y + 24);
                drawStatus(g, results[day][i], x + w - 26, y + 10);
            }

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            g.setColor(Color.WHITE);
            g.drawString("Score: " + score, x, 450);
            for (int i = 0; i < MAX_HEARTS; i++) {
                drawHeart(g, x + i * 34, 468, i < hearts);
            }

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.setColor(new Color(170, 178, 195));
            g.drawString("Move: arrow keys or WASD", x, 530);
            g.drawString("Pause: P", x, 550);
            g.drawString("Avoid the orange hall monitors!", x, 570);
            g.drawString("Coffee = speed    Clock = +time", x, 590);
            g.drawString("Homework = +50 points", x, 610);
        }

        private void drawStatus(Graphics2D g, int status, int x, int y) {
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (status == ON_TIME) {
                g.setColor(new Color(110, 220, 130));
                g.drawLine(x, y + 8, x + 6, y + 15);
                g.drawLine(x + 6, y + 15, x + 16, y + 1);
            } else if (status == LATE) {
                g.setColor(new Color(240, 100, 100));
                g.drawLine(x + 1, y + 1, x + 15, y + 15);
                g.drawLine(x + 15, y + 1, x + 1, y + 15);
            }
            g.setStroke(new BasicStroke(1));
        }

        private void drawHeart(Graphics2D g, int x, int y, boolean full) {
            g.setColor(full ? new Color(235, 75, 95) : new Color(70, 75, 92));
            g.fillOval(x, y, 15, 15);
            g.fillOval(x + 13, y, 15, 15);
            g.fillPolygon(new int[] {x + 1, x + 27, x + 14}, new int[] {y + 11, y + 11, y + 27}, 3);
        }

        private void drawMessage(Graphics2D g) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            FontMetrics metrics = g.getFontMetrics();
            int w = metrics.stringWidth(message) + 32;
            int x = (MAP_W - w) / 2, y = TILE * 7 + 10;
            g.setColor(new Color(20, 22, 30, 215));
            g.fillRoundRect(x, y, w, 40, 14, 14);
            g.setColor(messageColor);
            g.drawString(message, x + 16, y + 27);
        }

        private void drawCentered(Graphics2D g, String text, int y, int width) {
            g.drawString(text, (width - g.getFontMetrics().stringWidth(text)) / 2, y);
        }

        private void drawMenu(Graphics2D g) {
            int width = MAP_W + SIDE_W;
            g.setColor(new Color(16, 18, 26, 225));
            g.fillRect(0, 0, width, MAP_H);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
            g.setColor(new Color(255, 225, 120));
            drawCentered(g, "SCHEDULE RUSH", 150, width);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
            g.setColor(Color.WHITE);
            String[] lines = {
                "Read your timetable and reach the right classroom before the bell.",
                "Be late three times and you are sent to the principal's office.",
                "",
                "Wrong door: -3 seconds      Hall monitor: -4 seconds",
                "Each day has less time and more hall monitors.",
                "On Monday the right door glows. After that, you are on your own!",
            };
            for (int i = 0; i < lines.length; i++) {
                drawCentered(g, lines[i], 225 + i * 36, width);
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            if ((int) (clock * 2) % 2 == 0) {
                g.setColor(new Color(150, 240, 150));
                drawCentered(g, "Press ENTER to start", 520, width);
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            g.setColor(new Color(170, 178, 195));
            drawCentered(g, "Move with the arrow keys or WASD", 565, width);
        }

        private void drawOverlay(Graphics2D g, String title, String[] lines) {
            g.setColor(new Color(16, 18, 26, 205));
            g.fillRect(0, 0, MAP_W, MAP_H);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 46));
            g.setColor(new Color(255, 225, 120));
            drawCentered(g, title, 240, MAP_W);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
            g.setColor(Color.WHITE);
            for (int i = 0; i < lines.length; i++) {
                drawCentered(g, lines[i], 295 + i * 34, MAP_W);
            }
        }

        private void drawReport(Graphics2D g) {
            boolean won = state == State.WIN;
            drawOverlay(g, won ? "You made it to Friday!" : "Sent to the principal!", new String[] {
                "REPORT CARD",
                "On time: " + countResults(ON_TIME) + " of " + (DAYS.length * TIMES.length) + " classes",
                "Grade: " + grade(),
                "Score: " + score + "    Best: " + bestScore,
                "",
                "Press ENTER to play again",
            });
        }

        // ------------------------------- INPUT -------------------------------

        @Override
        public void keyPressed(KeyEvent e) {
            setDirection(e.getKeyCode(), true);
            int key = e.getKeyCode();
            if (key == KeyEvent.VK_ENTER) {
                if (state == State.MENU || state == State.WIN || state == State.GAME_OVER) {
                    startGame();
                } else if (state == State.DAY_END) {
                    day++;
                    startDay();
                }
            } else if (key == KeyEvent.VK_P) {
                if (state == State.PLAYING) {
                    state = State.PAUSED;
                    releaseKeys();
                } else if (state == State.PAUSED) {
                    state = State.PLAYING;
                }
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            setDirection(e.getKeyCode(), false);
        }

        @Override
        public void keyTyped(KeyEvent e) {
        }

        private void setDirection(int key, boolean pressed) {
            switch (key) {
                case KeyEvent.VK_UP:
                case KeyEvent.VK_W:
                    up = pressed;
                    break;
                case KeyEvent.VK_DOWN:
                case KeyEvent.VK_S:
                    down = pressed;
                    break;
                case KeyEvent.VK_LEFT:
                case KeyEvent.VK_A:
                    left = pressed;
                    break;
                case KeyEvent.VK_RIGHT:
                case KeyEvent.VK_D:
                    right = pressed;
                    break;
                default:
                    break;
            }
        }
    }
}
