package com.example.hajimi24;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class GameManager {
    public Fraction[] cardValues = new Fraction[5];
    public Fraction[] initialValues = new Fraction[5];
    private Stack<Fraction[]> undoStack = new Stack<>();
    private Stack<Fraction[]> redoStack = new Stack<>();

    private List<Problem> fullProblemSet = new ArrayList<>();
    private int currentProblemIndex = -1;
    public String currentLevelSolution = null;
    public int currentNumberCount = 4;
    public int solvedCount = 0;

    public GameManager() { }

    public void setProblemSet(List<Problem> problems) {
        this.fullProblemSet = problems;
        Collections.shuffle(this.fullProblemSet);
        this.currentProblemIndex = -1;
    }

    public void startNewGame(GameModeSettings settings) {
        undoStack.clear();
        redoStack.clear();

        Problem nextProblem = findNextProblem(settings);

        if (nextProblem != null) {
            currentNumberCount = nextProblem.numbers.size();
            for (int i = 0; i < currentNumberCount; i++) {
                cardValues[i] = nextProblem.numbers.get(i);
            }
            currentLevelSolution = nextProblem.solution;
        } else {
            // ... (无符合条件题目的处理)
        }

        System.arraycopy(cardValues, 0, initialValues, 0, 5);
    }

    private Problem findNextProblem(GameModeSettings settings) {
        if (fullProblemSet.isEmpty()) return null;

        for (int i = 0; i < fullProblemSet.size(); i++) {
            currentProblemIndex = (currentProblemIndex + 1) % fullProblemSet.size();
            Problem prob = fullProblemSet.get(currentProblemIndex);

            // --- 核心筛选逻辑：直接调用 SolutionAnalyzer ---
            if (settings.avoidPureAddSub && SolutionAnalyzer.hasOnlyAddSub(prob.solution)) continue;
            if (settings.mustHaveDivision && SolutionAnalyzer.hasNoDivision(prob.solution)) continue;
            if (settings.avoidTrivialFinalMultiply && SolutionAnalyzer.hasTrivialFinalMultiply(prob.solution)) continue;
            if (settings.requireFractionCalc && SolutionAnalyzer.hasNoFractionCalculation(prob.solution)) continue;

            // (除法风暴和数字上界规则可以类似地在这里添加)

            return prob; // 找到合规题目
        }

        return null; // 遍历一圈未找到
    }

    public boolean performCalculation(int idx1, int idx2, String op) throws ArithmeticException {
        if (cardValues[idx1] == null || cardValues[idx2] == null) return false;
        saveToUndo();
        redoStack.clear();
        Fraction f1 = cardValues[idx1];
        Fraction f2 = cardValues[idx2];
        Fraction result = null;
        switch (op) {
            case "+": result = f1.add(f2); break;
            case "-": result = f1.sub(f2); break;
            case "*": result = f1.multiply(f2); break;
            case "/": result = f1.divide(f2); break;
        }
        cardValues[idx2] = result;
        cardValues[idx1] = null;
        return true;
    }

    public boolean checkWin() {
        int count = 0;
        Fraction last = null;
        for (Fraction f : cardValues) if (f != null) { count++; last = f; }
        return count == 1 && last != null && last.isValue(24);
    }

    private void saveToUndo() {
        Fraction[] state = new Fraction[5];
        System.arraycopy(cardValues, 0, state, 0, 5);
        undoStack.push(state);
    }

    public boolean undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(cardValues.clone());
            cardValues = undoStack.pop();
            return true;
        }
        return false;
    }

    public boolean redo() {
        if (!redoStack.isEmpty()) {
            saveToUndo();
            cardValues = redoStack.pop();
            return true;
        }
        return false;
    }

    public void resetCurrentLevel() {
        if (initialValues != null) {
            saveToUndo();
            redoStack.clear();
            System.arraycopy(initialValues, 0, cardValues, 0, 5);
        }
    }
}
