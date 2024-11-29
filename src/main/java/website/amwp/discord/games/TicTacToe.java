package website.amwp.discord.games;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import java.util.*;
import java.awt.Color;

public class TicTacToe {
    private char[][] board;
    private char currentPlayer;
    private final User player1;
    private final User player2;
    private final String mode; // "bot" or "pvp"
    private final String difficulty; // only used in bot mode
    private boolean gameOver;
    private final Random random;

    // Constructor for PvP mode
    public TicTacToe(User player1, User player2) {
        this.board = new char[3][3];
        this.currentPlayer = 'X';
        this.player1 = player1;
        this.player2 = player2;
        this.mode = "pvp";
        this.difficulty = null;
        this.gameOver = false;
        this.random = new Random();
        
        initializeBoard();
    }

    // Constructor for Bot mode
    public TicTacToe(User player, String difficulty) {
        this.board = new char[3][3];
        this.currentPlayer = 'X';
        this.player1 = player;
        this.player2 = null;
        this.mode = "bot";
        this.difficulty = difficulty;
        this.gameOver = false;
        this.random = new Random();
        
        initializeBoard();
    }

    private void initializeBoard() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                board[i][j] = ' ';
            }
        }
    }

    public List<Button> getButtons() {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                String id = i + "," + j;
                String label = board[i][j] == ' ' ? "⬜" : (board[i][j] == 'X' ? "❌" : "⭕");
                buttons.add(Button.primary(id, label).withDisabled(board[i][j] != ' ' || gameOver));
            }
        }
        return buttons;
    }

    public EmbedBuilder makeMove(int row, int col, User player) {
        // Check if it's the correct player's turn
        if (mode.equals("pvp")) {
            if ((currentPlayer == 'X' && !player.equals(player1)) || 
                (currentPlayer == 'O' && !player.equals(player2))) {
                return getGameBoard("It's not your turn!");
            }
        }

        if (board[row][col] != ' ' || gameOver) {
            return getGameBoard("Invalid move!");
        }

        // Make the player's move
        board[row][col] = currentPlayer;
        
        if (checkWin(currentPlayer)) {
            gameOver = true;
            return getGameBoard(getPlayerName(player) + " wins! 🎉");
        }
        
        if (isBoardFull()) {
            gameOver = true;
            return getGameBoard("It's a draw! 🤝");
        }

        // Switch players
        currentPlayer = (currentPlayer == 'X') ? 'O' : 'X';

        // If playing against bot, make bot move
        if (mode.equals("bot") && currentPlayer == 'O') {
            makeBotMove();
            
            if (checkWin('O')) {
                gameOver = true;
                return getGameBoard("Bot wins! 🤖");
            }
            
            if (isBoardFull()) {
                gameOver = true;
                return getGameBoard("It's a draw! 🤝");
            }
            
            currentPlayer = 'X';
        }

        return getGameBoard(getCurrentPlayerName() + "'s turn!");
    }

    private String getCurrentPlayerName() {
        if (mode.equals("pvp")) {
            return currentPlayer == 'X' ? player1.getName() : player2.getName();
        } else {
            return currentPlayer == 'X' ? player1.getName() : "Bot";
        }
    }

    private String getPlayerName(User player) {
        if (player.equals(player1)) return player1.getName();
        if (mode.equals("pvp") && player.equals(player2)) return player2.getName();
        return "Bot";
    }

    private void makeBotMove() {
        switch (difficulty.toLowerCase()) {
            case "easy":
                makeEasyMove();
                break;
            case "medium":
                if (random.nextBoolean()) {
                    makeSmartMove();
                } else {
                    makeEasyMove();
                }
                break;
            case "hard":
                makeSmartMove();
                break;
        }
    }

    private void makeEasyMove() {
        List<int[]> emptySpots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j] == ' ') {
                    emptySpots.add(new int[]{i, j});
                }
            }
        }
        if (!emptySpots.isEmpty()) {
            int[] move = emptySpots.get(random.nextInt(emptySpots.size()));
            board[move[0]][move[1]] = 'O';
        }
    }

    private void makeSmartMove() {
        // Try to win
        if (findWinningMove('O')) return;
        
        // Block player's winning move
        if (findWinningMove('X')) return;
        
        // Take center if available
        if (board[1][1] == ' ') {
            board[1][1] = 'O';
            return;
        }
        
        // Take corners
        List<int[]> corners = Arrays.asList(
            new int[]{0,0}, new int[]{0,2}, 
            new int[]{2,0}, new int[]{2,2}
        );
        Collections.shuffle(corners);
        for (int[] corner : corners) {
            if (board[corner[0]][corner[1]] == ' ') {
                board[corner[0]][corner[1]] = 'O';
                return;
            }
        }
        
        // Take any available spot
        makeEasyMove();
    }

    private boolean findWinningMove(char player) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j] == ' ') {
                    board[i][j] = player;
                    if (checkWin(player)) {
                        board[i][j] = 'O';
                        return true;
                    }
                    board[i][j] = ' ';
                }
            }
        }
        return false;
    }

    private boolean checkWin(char player) {
        // Check rows
        for (int i = 0; i < 3; i++) {
            if (board[i][0] == player && board[i][1] == player && board[i][2] == player) {
                return true;
            }
        }
        
        // Check columns
        for (int j = 0; j < 3; j++) {
            if (board[0][j] == player && board[1][j] == player && board[2][j] == player) {
                return true;
            }
        }
        
        // Check diagonals
        if (board[0][0] == player && board[1][1] == player && board[2][2] == player) {
            return true;
        }
        if (board[0][2] == player && board[1][1] == player && board[2][0] == player) {
            return true;
        }
        
        return false;
    }

    private boolean isBoardFull() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j] == ' ') {
                    return false;
                }
            }
        }
        return true;
    }

    public EmbedBuilder getGameBoard(String message) {
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.BLUE)
            .setTitle("Tic Tac Toe" + (mode.equals("bot") ? " - " + difficulty : ""))
            .setDescription(
                mode.equals("pvp") 
                    ? String.format("Player 1: %s (❌)\nPlayer 2: %s (⭕)\n\n%s", 
                        player1.getName(), player2.getName(), message)
                    : String.format("Player: %s (❌)\nBot: ⭕\n\n%s", 
                        player1.getName(), message)
            )
            .setFooter(gameOver ? "Game Over!" : "Click a button to make your move!", null);
        return embed;
    }

    public boolean isValidPlayer(User player) {
        return player.equals(player1) || (mode.equals("pvp") && player.equals(player2));
    }
} 