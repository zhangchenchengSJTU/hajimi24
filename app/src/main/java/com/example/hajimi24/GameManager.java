package com.example.hajimi24;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Stack;

public class GameManager {

    public Fraction[] cardValues = new Fraction[5];
    public Fraction[] initialValues = new Fraction[5];
    private String rawProblemLineCache; // 我们将使用这个字段来缓存当前题目的字符串

    private Stack<Fraction[]> undoStack = new Stack<>();
    private Stack<Fraction[]> redoStack = new Stack<>();

    private List<Problem> problemSet = new ArrayList<>();
    private int currentProblemIndex = -1;
    public String currentLevelSolution = null;
    public int currentNumberCount = 4;
    public int solvedCount = 0;

    public void startNewGame(boolean isRandomMode) {
        undoStack.clear();
        redoStack.clear();
        generateLevel(isRandomMode);
        System.arraycopy(cardValues, 0, initialValues, 0, 5);
    }

    private void generateLevel(boolean isRandomMode) {
        Arrays.fill(cardValues, null);

        if (!isRandomMode && !problemSet.isEmpty()) {
            currentProblemIndex++;
            if (currentProblemIndex >= problemSet.size()) {
                currentProblemIndex = 0;
                Collections.shuffle(problemSet);
            }
            Problem prob = problemSet.get(currentProblemIndex);
            currentNumberCount = prob.numbers.size();
            for (int i = 0; i < currentNumberCount; i++) cardValues[i] = prob.numbers.get(i);
            currentLevelSolution = prob.solution;

            // [修复]：从 Problem 对象获取原始题目字符串并缓存
            rawProblemLineCache = prob.line;
        } else {
            // 随机模式逻辑
            Random rand = new Random();
            while(true) {
                List<Fraction> nums = new ArrayList<>();
                for(int i=0; i<currentNumberCount; i++) nums.add(new Fraction(rand.nextInt(13)+1, 1));
                String sol = Solver.solve(nums); // 假设 Solver 类存在
                if(sol != null) {
                    for(int i=0; i<currentNumberCount; i++) cardValues[i] = nums.get(i);
                    currentLevelSolution = sol;

                    // [修复]：随机模式下手动构建题目字符串并缓存
                    StringBuilder sb = new StringBuilder();
                    sb.append("['");
                    for (int i = 0; i < nums.size(); i++) {
                        sb.append(nums.get(i).toString());
                        if (i < nums.size() - 1) {
                            sb.append("', '");
                        }
                    }
                    sb.append("']->");
                    sb.append(sol);
                    rawProblemLineCache = sb.toString();

                    break;
                }
            }
        }
    }

    public void setProblemSet(List<Problem> problems) {
        this.problemSet = problems;
        Collections.shuffle(this.problemSet);
        this.currentProblemIndex = -1;
    }

    // 计算逻辑，返回是否成功
    public boolean performCalculation(int idx1, int idx2, String op) throws ArithmeticException {
        Fraction f1 = cardValues[idx1];
        Fraction f2 = cardValues[idx2];
        Fraction result = null;

        switch (op) {
            case "+": result = f1.add(f2); break;
            case "-": result = f1.sub(f2); break;
            case "*": result = f1.multiply(f2); break;
            case "/": result = f1.divide(f2); break; // 可能抛出异常
        }

        saveToUndo();
        redoStack.clear();
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

    // 撤销/重做/重置 逻辑
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
        saveToUndo();
        redoStack.clear();
        System.arraycopy(initialValues, 0, cardValues, 0, 5);
    }

    // 获取当前解
    public String getOrCalculateSolution() {
        int count = 0;
        for (Fraction f : cardValues) if (f != null) count++;
        // 如果是初始状态且有预设解
        if (count == currentNumberCount && currentLevelSolution != null) return currentLevelSolution;
        // 否则实时计算
        List<Fraction> nums = new ArrayList<>();
        for (Fraction f : cardValues) if (f != null) nums.add(f);
        return Solver.solve(nums);
    }

    /**
     * [修复后的方法]
     * 获取当前题目的原始、未经处理的完整字符串.
     * @return 原始题目字符串, 如果没有题目则返回 null.
     */
    public String getRawProblemLine() {
        return rawProblemLineCache;
    }
    public String getShareText() {
        StringBuilder sb = new StringBuilder();
        sb.append("哈基米问你: ");

        // 遍历初始数值 (initialValues)，而不是当前的 cardValues
        for (int i = 0; i < currentNumberCount; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            if (initialValues[i] != null) {
                sb.append(initialValues[i].toString());
            }
        }
        sb.append(" 如何计算 24 点?");
        return sb.toString();
    }
}
