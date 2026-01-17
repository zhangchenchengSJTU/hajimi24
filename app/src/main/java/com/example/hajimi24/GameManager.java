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
    private String rawProblemLineCache;

    private Stack<Fraction[]> undoStack = new Stack<>();
    private Stack<Fraction[]> redoStack = new Stack<>();

    private List<Problem> problemSet = new ArrayList<>();
    private int currentProblemIndex = -1;

    // [新增] 保存当前题目对象的引用
    private Problem currentProblem;

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

            // [新增] 记录当前题目
            this.currentProblem = prob;

            currentNumberCount = prob.numbers.size();
            for (int i = 0; i < currentNumberCount; i++) cardValues[i] = prob.numbers.get(i);
            currentLevelSolution = prob.solution;
            rawProblemLineCache = prob.line;
        } else {
            // 随机模式
            this.currentProblem = null; // 随机模式没有特定题目对象

            Random rand = new Random();
            while(true) {
                List<Fraction> nums = new ArrayList<>();
                for(int i=0; i<currentNumberCount; i++) nums.add(new Fraction(rand.nextInt(13)+1, 1));
                String sol = Solver.solve(nums);
                if(sol != null) {
                    for(int i=0; i<currentNumberCount; i++) cardValues[i] = nums.get(i);
                    currentLevelSolution = sol;

                    StringBuilder sb = new StringBuilder();
                    sb.append("['");
                    for (int i = 0; i < nums.size(); i++) {
                        sb.append(nums.get(i).toString());
                        if (i < nums.size() - 1) sb.append("', '");
                    }
                    sb.append("']->");
                    sb.append(sol);
                    rawProblemLineCache = sb.toString();
                    break;
                }
            }
        }
    }

    // [新增] 公开方法，供 MainActivity 调用以获取 modulus
    public Problem getCurrentProblem() {
        return this.currentProblem;
    }

    public void setProblemSet(List<Problem> problems) {
        this.problemSet = problems;
        Collections.shuffle(this.problemSet);
        this.currentProblemIndex = -1;
    }

    public boolean performCalculation(int idx1, int idx2, String op) throws ArithmeticException {
        Fraction f1 = cardValues[idx1];
        Fraction f2 = cardValues[idx2];
        Fraction result = null;

        switch (op) {
            case "+": result = f1.add(f2); break;
            case "-": result = f1.sub(f2); break;
            case "*": result = f1.multiply(f2); break;
            case "/": result = f1.divide(f2); break;
        }

        // --- 核心修复：使用正确的模运算逻辑 ---
        if (currentProblem != null && currentProblem.modulus != null) {
            // 不再只是 result.getRe() % m，而是完整的 (re * inv(de)) % mod
            result = result.applyMod(currentProblem.modulus);
        }
        // ------------------------------------

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

        if (count != 1 || last == null) return false;

        // 核心修改：计算目标值。如果是 base 题目，目标是 2*radix + 4 (即对应进制下的 "24")
        int targetValue = 24;
        if (currentProblem != null && currentProblem.radix != null) {
            targetValue = 2 * currentProblem.radix + 4;
        }

        return last.isValue(targetValue);
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
        saveToUndo();
        redoStack.clear();
        System.arraycopy(initialValues, 0, cardValues, 0, 5);
    }

    public String getOrCalculateSolution() {
        int count = 0;
        for (Fraction f : cardValues) if (f != null) count++;
        if (count == currentNumberCount && currentLevelSolution != null) return currentLevelSolution;

        List<Fraction> nums = new ArrayList<>();
        for (Fraction f : cardValues) if (f != null) nums.add(f);

        // 这里也需要 Mod 支持
        Integer mod = (currentProblem != null) ? currentProblem.modulus : null;
        return Solver.solve(nums, mod);
    }

    public String getRawProblemLine() {
        return rawProblemLineCache;
    }

    public String getShareText() {
        StringBuilder sb = new StringBuilder();
        sb.append("哈基米问你: ");

        // 判断是否有 mod
        if (currentProblem != null && currentProblem.modulus != null) {
            sb.append("在模 ").append(currentProblem.modulus).append(" 的意义下, ");
        }

        // 遍历初始数值
        for (int i = 0; i < currentNumberCount; i++) {
            if (i > 0) sb.append(", ");
            if (initialValues[i] != null) sb.append(initialValues[i].toString());
        }

        // 分进制显示目标
        if (currentProblem != null && currentProblem.radix != null) {
            sb.append(" 如何计算 ").append(currentProblem.radix).append(" 进制下的二十四点?");
        } else {
            sb.append(" 如何计算二十四点?");
        }

        return sb.toString();
    }
}
