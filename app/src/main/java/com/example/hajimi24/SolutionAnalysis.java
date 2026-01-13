// SolutionAnalysis.java
package com.example.hajimi24;

import java.util.regex.Pattern;

public class SolutionAnalysis {
    public final boolean hasOnlyAddSub;
    public final boolean hasNoDivision;
    public final boolean hasTrivialFinalMultiply;
    public final boolean hasFractionCalculation;
    public final boolean hasDivisionStorm;

    public SolutionAnalysis(String solution, int numberCount) {
        if (solution == null || solution.isEmpty()) {
            this.hasOnlyAddSub = true;
            this.hasNoDivision = true;
            // ... (全设为不符合条件的状态)
            this.hasTrivialFinalMultiply = false;
            this.hasFractionCalculation = false;
            this.hasDivisionStorm = false;
            return;
        }

        // --- 开始分析 ---
        this.hasOnlyAddSub = !solution.contains("*") && !solution.contains("/");
        this.hasNoDivision = !solution.contains("/");

        int divisionCount = 0;
        for (char c : solution.toCharArray()) if (c == '/') divisionCount++;
        this.hasDivisionStorm = (numberCount == 3 && divisionCount >= 1) || ((numberCount == 4 || numberCount == 5) && divisionCount >= 2);

        this.hasTrivialFinalMultiply = checkTrivialFinalMultiply(solution);
        this.hasFractionCalculation = checkFractionCalculation(solution);
    }

    private boolean checkTrivialFinalMultiply(String sol) {
        int lastMulIndex = -1;
        int depth = 0;
        for (int i = 0; i < sol.length(); i++) {
            char c = sol.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == '*' && depth == 0) lastMulIndex = i;
        }
        if (lastMulIndex == -1) return false;

        String left = sol.substring(0, lastMulIndex).trim();
        String right = sol.substring(lastMulIndex + 1).trim();

        if (left.startsWith("(") && left.endsWith(")")) left = left.substring(1, left.length() - 1).trim();
        if (right.startsWith("(") && right.endsWith(")")) right = right.substring(1, right.length() - 1).trim();

        return !isComplexExpression(left) && !isComplexExpression(right);
    }

    private boolean isComplexExpression(String s) {
        return s.contains("+") || s.contains("-") || s.contains("*") || s.contains("/");
    }

    private boolean checkFractionCalculation(String sol) {
        Pattern pattern = Pattern.compile("\\([0-9]+/[0-9]+\\)\\s*[+\\-]\\s*\\([0-9]+/[0-9]+\\)");
        return pattern.matcher(sol).find();
    }
}
