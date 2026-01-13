package com.example.hajimi24;

import java.util.List;

public class GameState {
    private int score;
    private int bestScore;
    private String currentEquation;
    private List<Integer> cardNumbers;
    private boolean[] cardsUsed;

    // 构造函数
    public GameState(int score, int bestScore, String currentEquation, List<Integer> cardNumbers, boolean[] cardsUsed) {
        this.score = score;
        this.bestScore = bestScore;
        this.currentEquation = currentEquation;
        this.cardNumbers = cardNumbers;
        this.cardsUsed = cardsUsed;
    }

    // --- Getter 方法 ---
    public int getScore() { return score; }
    public int getBestScore() { return bestScore; }
    public String getCurrentEquation() { return currentEquation; }
    public List<Integer> getCardNumbers() { return cardNumbers; }
    public int getCardNumber(int index) { return cardNumbers.get(index); }
    public boolean isCardUsed(int index) { return cardsUsed[index]; }
}
