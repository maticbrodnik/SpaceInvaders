import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

public class SpaceInvaders extends JFrame implements Runnable {
    final private static String SHIP = "ship";
    final private static String INVADER = "invader";

    private volatile boolean running = false; // Flag to indicate whether the game is running
    private Thread gameThread; // Variable to store the current game thread

    // Define the shape of the ship and invader using points
    final private static HashMap<String, Point[]> SHAPE = new HashMap<>();
    // Define the color of the ship and invader
    final private static HashMap<String, Color> COLOR = new HashMap<>();

    final private int X = 21; // Number of columns in the game grid
    final private int Y = 20; // Number of rows in the game grid
    final private int SIZE = Toolkit.getDefaultToolkit().getScreenSize().height / 2 / Y; // Size of each grid cell
    final private int MIN = 100; // Minimum game width
    final private int MAX = 750; // Maximum game width
    private int DELAY = 20; // Delay between game updates (adjust this value to control game speed)

    final private JLabel SHIP_LABEL = new JLabel() {
        @Override
        public void paint(Graphics g) {
            g.setColor(COLOR.get(SHIP));
            for (Point p : SHAPE.get(SHIP)) {
                // Draw each part of the ship using rectangles
                g.fillRect((location.x + p.x) * SIZE, (location.y + p.y) * SIZE, SIZE, SIZE);
            }
        }
    };

    final private JLabel GAME_OVER = new JLabel("Game over", JLabel.CENTER);
    final private JLabel SCORE = new JLabel("0", JLabel.RIGHT);
    final private JButton TRY_AGAIN;


    final private JButton START = new JButton("Start");
    final private JButton QUIT = new JButton("Quit");

    private ArrayList<Color[]> grid = new ArrayList<>(Y);

    private ArrayList<Missile> missiles = new ArrayList<>();
    private ArrayList<Invader> invaders;

    private Point location; // Current location of the ship
    private Point[] ship; // Shape of the ship

    private int score = 0; // Current score
    private boolean gameOver = false; // Flag to indicate game over state

    public SpaceInvaders() {
        JLayeredPane board = new JLayeredPane();
        JPanel statusPanel = new JPanel(new BorderLayout());

        board.setOpaque(true);
        board.setBackground(Color.black);
        board.add(new JPanel() {
            @Override
            public void paint(Graphics g) {
                // Paint the game grid, missiles, and invaders
                for (int i = 0; i < Y; i++) {
                    Color[] row = new Color[X];
                    Arrays.fill(row, Color.BLACK);
                    grid.add(row);
                }
                for (Missile missile : missiles) {
                    // Draw each missile using orange rectangles
                    g.setColor(Color.orange);
                    g.fillRect(missile.getX() * SIZE, missile.getY() * SIZE, SIZE, SIZE);
                }
                for (Invader invader : invaders) {
                    // Draw each invader using the defined shape and color
                    g.setColor(invader.getColor());
                    for (Point p : SHAPE.get(INVADER)) {
                        g.fillRect((invader.getX() + p.x) * SIZE, (invader.getY() + p.y) * SIZE, SIZE, SIZE);
                    }
                }
            }
        });
        board.add(SHIP_LABEL);
        board.add(GAME_OVER);

        board.setPreferredSize(new Dimension(X * SIZE, Y * SIZE));
        for (Component c : board.getComponents()) {
            c.setSize(board.getPreferredSize());
        }

        statusPanel.add(SCORE, BorderLayout.NORTH);
        statusPanel.add(START, BorderLayout.WEST);
        statusPanel.add(QUIT, BorderLayout.EAST);

        SHIP_LABEL.setVisible(false);
        setupKeyListeners();

        GAME_OVER.setVisible(false);
        GAME_OVER.setFont(new Font(Font.DIALOG_INPUT, Font.BOLD, GAME_OVER.getHeight() / 12));

        SCORE.setFont(GAME_OVER.getFont());

        START.setFont(GAME_OVER.getFont());
        START.setBorder(null);
        START.setContentAreaFilled(false);
        START.setFocusPainted(false);
        START.addActionListener(e -> {
            if (!gameOver) {
                new Thread(this).start();
            }
        });

        QUIT.setFont(GAME_OVER.getFont());
        QUIT.setBorder(null);
        QUIT.setContentAreaFilled(false);
        QUIT.setFocusPainted(false);
        QUIT.addActionListener(e -> {
            if (gameOver) {
                System.exit(0);
            }
        });
        QUIT.setVisible(false);
        QUIT.requestFocus();

        add(board, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);


        TRY_AGAIN = new JButton("Try Again");
        TRY_AGAIN.setFont(GAME_OVER.getFont());
        TRY_AGAIN.setBorder(null);
        TRY_AGAIN.setContentAreaFilled(false);
        TRY_AGAIN.setFocusPainted(false);
        
        TRY_AGAIN.setVisible(false);
        TRY_AGAIN.addActionListener(e -> {
            if (gameOver) {
                resetGame();
                if (gameThread != null && gameThread.isAlive()) {
                    gameThread.interrupt();
                }
                running = true;
                gameThread = new Thread(this);
                gameThread.start();
                TRY_AGAIN.setVisible(false);
            }
        });


        statusPanel.add(TRY_AGAIN, BorderLayout.CENTER);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setVisible(true);
        pack();
    }

    private synchronized boolean moveShip(int direction) {
        int newX = location.x + direction;
        if (newX >= 0 && newX + (ship.length - 6) <= X - 1) {
            // Move the ship to the new location if within the game bounds
            location = new Point(newX, location.y);
            SHIP_LABEL.repaint();
            return true;
        }
        return false;
    }

    private void fireMissile() {
        // Create a new missile at the ship's current location
        Missile missile = new Missile(location.x, location.y);
        missiles.add(missile);
    }

    private void checkMissileCollisions() {
        for (int i = missiles.size() - 1; i >= 0; i--) {
            Missile missile = missiles.get(i);
            missile.move();

            // Remove missile if it reaches the top of the screen
            if (missile.getY() <= 0) {
                missiles.remove(i);
                continue;
            }

            // Check for collision with invaders
            for (int j = invaders.size() - 1; j >= 0; j--) {
                Invader invader = invaders.get(j);
                if (missile.getX() >= invader.getX() && missile.getX() <= invader.getX() + 2
                        && missile.getY() == invader.getY()) {
                    missiles.remove(i);
                    invaders.remove(j);
                    score += 10;
                    SCORE.setText(String.valueOf(score));

                    if (invaders.isEmpty()) {
                        SHIP_LABEL.setVisible(false);
                        GAME_OVER.setVisible(true);
                        GAME_OVER.setText("You won!");
                        gameOver = true;
                        //The commented line will show Quit option during Wave Cleared text.
                        //QUIT.setVisible(true);
                        return;
                    }
                    break;
                }
            }
        }
    }

    private void setupKeyListeners() {
    // Set up key listeners to control the ship's movement and firing
    SHIP_LABEL.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                moveShip(-1);
            } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                moveShip(1);
            } else if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                if (missiles.isEmpty()) { // Check if there are no existing missiles
                    fireMissile();
                }
            }
        }
    });

    SHIP_LABEL.setFocusable(true);
    SHIP_LABEL.requestFocus();
}


    @Override
    public void run() {
        running = true; // Set the running flag to true
        gameThread = Thread.currentThread(); // Store the current game thread
        START.setVisible(false);
        grid = new ArrayList<>(Arrays.asList(new Color[Y][X]));
        SCORE.setText("0");
        GAME_OVER.setVisible(false);
        invaders = createInvaders(1);

        while (running) {
            if (!gameOver) {
                // Start a new game if the game over screen is not visible
                SHIP_LABEL.setVisible(true);
                SHIP_LABEL.requestFocus();
                location = new Point((X - 1) / 2, Y - 1);
                ship = SHAPE.get(SHIP);
                SHIP_LABEL.repaint();

                while (SHIP_LABEL.isVisible()) {
                    // Game loop - update the game state and repaint the board
                    for (Missile missile : missiles) {
                        missile.move();
                    }
                    checkMissileCollisions();
                    moveInvaders();
                    checkInvaderCollisions();
                    repaint(); // Repaint the board to update missile and invader positions

                    try {
                        Thread.sleep(DELAY);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                // If the game over screen is visible, wait for a short duration and show the quit button
                try {
                    Thread.sleep(1200);
                } catch (Exception ex) {
                    // Handle exceptions
                }
                //QUIT.setVisible(true);
                //QUIT.requestFocus();
            }
        }
    }

    private void resetGame() {
        running = false; // Set the running flag to false to stop the previous thread
        SHIP_LABEL.setVisible(false);
        GAME_OVER.setVisible(false);
        gameOver = false;
        QUIT.setVisible(false);
        score = 0;
        SCORE.setText("0");
        missiles.clear();
        invaders = createInvaders(1); // Create invaders for the first wave

        if (gameThread != null) {
            gameThread.interrupt(); // Interrupt the current game thread if it's still running
            gameThread = null; // Reset the game thread variable
        }

        // Reset the grid colors
        grid = new ArrayList<>(Y);
        for (int i = 0; i < Y; i++) {
            Color[] row = new Color[X];
            Arrays.fill(row, Color.BLACK);
            grid.add(row);
        }

        // Hide the "Try Again" button
        TRY_AGAIN.setVisible(false);

        // Set up the key listeners for ship movement and firing
        setupKeyListeners();
        SHIP_LABEL.requestFocus();
    }

    private void startNewWave() {
        SHIP_LABEL.setVisible(false);
        GAME_OVER.setVisible(true);
        GAME_OVER.setText("Wave Cleared!");
        score += 100;
        SCORE.setText(String.valueOf(score));

        try {
            Thread.sleep(1200);
        } catch (Exception ex) {
            // Handle exceptions
        }

        clearMissiles(); // Clear the drawn missiles from the screen
        invaders = createInvaders(score / 100 + 1); // Pass the wave number to createInvaders()

        QUIT.setVisible(false); // Hide the Quit button

        SHIP_LABEL.setVisible(true);
        SHIP_LABEL.revalidate();
        SHIP_LABEL.requestFocus();
        location = new Point((X - 1) / 2, Y - 1);
        ship = SHAPE.get(SHIP);
        SHIP_LABEL.repaint();

        GAME_OVER.setVisible(false); // Hide the "Wave Cleared" text

        if (!gameOver) {
            QUIT.setVisible(false); // Hide the Quit button again if the game is still ongoing
        }

        setupKeyListeners();
        TRY_AGAIN.setVisible(false); // Hide the "Try Again" button
    }

    private void clearMissiles() {
        missiles.clear();
    }

    private ArrayList<Invader> createInvaders(int wave) {
        ArrayList<Invader> invaders = new ArrayList<>();
        int invaderY = 1;
        int invaderCount = 0;
        int maxInvadersPerWave = 8 + wave * 4; // Adjust the formula to determine the number of invaders per wave

        for (int row = 0; row < 5; row++) {
            int invaderX = 1;
            for (int col = 0; col < 8; col++) {
                if (invaderCount >= maxInvadersPerWave) {
                    return invaders;
                }
                Invader invader = new Invader(invaderX, invaderY);
                invaders.add(invader);
                invaderX += 3;
                invaderCount++;
            }
            invaderY += 2;
        }

        return invaders;
    }


    private void moveInvaders() {
        int delay = DELAY; // Initial delay value

        ArrayList<Invader> invadersCopy = new ArrayList<>(invaders);
        for (Invader invader : invadersCopy) {
            invader.move();
        }

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void checkInvaderCollisions() {
        for (Invader invader : invaders) {
            // Check if any invaders have reached the bottom row
            if (invader.getY() >= Y - 2) {
                SHIP_LABEL.setVisible(false);
                GAME_OVER.setVisible(true);
                GAME_OVER.setText("Game Over");
                TRY_AGAIN.setVisible(true);
                gameOver = true;
                QUIT.setVisible(true);
                score = 0;
                return;
            }
        }
        if (invaders.isEmpty()) {
            // Start a new wave if all invaders have been cleared
            startNewWave();
        }
    }


    public static void main(String[] args) {
        // Define the shape and color of the ship
        SHAPE.put(SHIP, new Point[]{new Point(-1, 0), new Point(0, 0), new Point(1, 0),
                new Point(0, -1), new Point(-1, 1), new Point(1, 1),});
        COLOR.put(SHIP, Color.blue);

        // Define the shape and color of the invader
        SHAPE.put(INVADER, new Point[]{new Point(0, 0)});
        COLOR.put(INVADER, Color.green);

        new SpaceInvaders();
    }

    public class Missile {
        private int x;
        private int y;

        public Missile(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public void move() {
            y--; // Move the missile upward
        }
    }

    public class Invader {
        private int x;
        private int y;
        private boolean moveRight = true;

        public Invader(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public Color getColor() {
            return COLOR.get(INVADER);
        }

        public void move() {
            // Change the direction when reaching the game boundaries
            if (moveRight) {
                x++;
                if (x + 1 >= X) {
                    y++;
                    moveRight = false;
                }
            } else {
                x--;
                if (x <= 0) {
                    y++;
                    moveRight = true;
                }
            }
        }
    }
}
