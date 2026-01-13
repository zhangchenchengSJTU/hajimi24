// SolutionAnalyzer.java
package com.example.hajimi24;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SolutionAnalyzer {

    private final String solution;
    private final int numberCount;

    public SolutionAnalyzer(String solution, int numberCount) {
        this.solution = solution;
        this.numberCount = numberCount;
    }

    // 规则 (2): 检查是否只包含加减法
    public boolean hasOnlyAddSub() {
        return !solution.contains("*") && !solution.contains("/");
    }

    // 规则 (3): 检查是否不包含除法
    public boolean hasNoDivision() {
        return !solution.contains("/");
    }

    // 规则 (4): 检查最后一步是否为平凡乘法
    public boolean hasTrivialFinalMultiply() {
        // ... (复杂逻辑，通过分析括号匹配来找到最后一层运算)
        // 伪代码:
        // 1. 找到不在任何括号内的最后一个 '*' 运算符。
        // 2. 如果没找到，返回 false。
        // 3. 检查该 '*' 左边和右边的表达式，看它们是否都是整数。
        // 4. 如果都是整数，返回 true。
        return false; // 占位
    }

    // 规则 (5): 检查是否包含分数加减
    public boolean hasFractionCalculation() {
        // 使用正则表达式寻找 (分数) + (分数) 或 (分数) - (分数)
        Pattern pattern = Pattern.compile("\\([0-9]+/[0-9]+\\)\\s*[+\\-]\\s*\\([0-9]+/[0-9]+\\)");
        return pattern.matcher(solution).find();
    }

    // 规则 (6): 检查是否满足“除法风暴”
    public boolean hasDivisionStorm() {
        int divisionCount = 0;
        for (char c : solution.toCharArray()) {
            if (c == '/') divisionCount++;
        }

        if (numberCount == 3) return divisionCount >= 1;
        if (numberCount == 4 || numberCount == 5) return divisionCount >= 2;
        return false;
    }
}

