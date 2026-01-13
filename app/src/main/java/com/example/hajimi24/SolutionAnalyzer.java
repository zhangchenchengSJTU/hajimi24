package com.example.hajimi24;

import java.util.Stack;

public class SolutionAnalyzer {

    // 规则 4.1: 避免纯加减
    public static boolean hasOnlyAddSub(String solution) {
        if (solution == null) return true;
        return !solution.contains(" * ") && !solution.contains(" / ");
    }

    // 规则 4.2: 必须含有除法
    public static boolean hasNoDivision(String solution) {
        if (solution == null) return true;
        return !solution.contains(" / ");
    }

    // 规则 4.3: 避免平凡乘法
    public static boolean hasTrivialFinalMultiply(String solution) {
        if (solution == null) return false;
        try {
            int lastOpIndex = -1;
            int depth = 0;
            for (int i = solution.length() - 1; i >= 0; i--) {
                char c = solution.charAt(i);
                if (c == ')') depth++;
                else if (c == '(') depth--;
                else if (depth == 0 && (c == '+' || c == '-' || c == '*' || c == '/')) {
                    lastOpIndex = i;
                    break;
                }
            }

            if (lastOpIndex == -1 || solution.charAt(lastOpIndex) != '*') {
                return false;
            }

            String leftExpr = solution.substring(0, lastOpIndex).trim();
            String rightExpr = solution.substring(lastOpIndex + 1).trim();

            double leftVal = evaluate(leftExpr);
            double rightVal = evaluate(rightExpr);

            // 检查求值是否失败
            if (Double.isNaN(leftVal) || Double.isNaN(rightVal)) return false;

            int[] factors = {1, 2, 3, 4, 6, 8, 12, 24};
            for (int factor : factors) {
                if (Math.abs(leftVal * rightVal - 24.0) < 0.001) {
                    if ((Math.abs(leftVal - factor) < 0.001 && Math.abs(rightVal - (24.0 / factor)) < 0.001) ||
                            (Math.abs(rightVal - factor) < 0.001 && Math.abs(leftVal - (24.0 / factor)) < 0.001)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // 规则 4.4: 包含分数加减
    public static boolean hasNoFractionCalculation(String solution) {
        if (solution == null) return true;
        boolean hasFraction = solution.contains("/");
        boolean hasAddSub = solution.contains(" + ") || solution.contains(" - ");
        return !(hasFraction && hasAddSub);
    }

    // --- 核心修复：一个纯Java实现的、基于逆波兰表达式的求值器 ---
    private static double evaluate(String expression) {
        try {
            // 移除最外层可能存在的括号
            if (expression.startsWith("(") && expression.endsWith(")")) {
                expression = expression.substring(1, expression.length() - 1).trim();
            }
            return evaluatePostfix(toPostfix(expression));
        } catch (Exception e) {
            return Double.NaN; // 返回一个特殊值表示失败
        }
    }

    private static int precedence(char op) {
        if (op == '+' || op == '-') return 1;
        if (op == '*' || op == '/') return 2;
        return 0;
    }

    private static String toPostfix(String infix) {
        StringBuilder postfix = new StringBuilder();
        Stack<Character> ops = new Stack<>();
        String[] tokens = infix.split(" ");

        for(String token : tokens) {
            if(token.isEmpty()) continue;
            char firstChar = token.charAt(0);
            if(Character.isDigit(firstChar) || (firstChar == '-' && token.length() > 1)) {
                postfix.append(token).append(" ");
            } else if (firstChar == '(') {
                ops.push(firstChar);
            } else if (firstChar == ')') {
                while (!ops.isEmpty() && ops.peek() != '(') {
                    postfix.append(ops.pop()).append(" ");
                }
                ops.pop();
            } else { // Operator
                while (!ops.isEmpty() && precedence(firstChar) <= precedence(ops.peek())) {
                    postfix.append(ops.pop()).append(" ");
                }
                ops.push(firstChar);
            }
        }

        while (!ops.isEmpty()) {
            postfix.append(ops.pop()).append(" ");
        }
        return postfix.toString();
    }

    private static double evaluatePostfix(String postfix) {
        Stack<Double> values = new Stack<>();
        String[] tokens = postfix.split(" ");

        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (isNumeric(token)) {
                values.push(Double.parseDouble(token));
            } else {
                double val2 = values.pop();
                double val1 = values.pop();
                switch (token.charAt(0)) {
                    case '+': values.push(val1 + val2); break;
                    case '-': values.push(val1 - val2); break;
                    case '*': values.push(val1 * val2); break;
                    case '/': values.push(val1 / val2); break;
                }
            }
        }
        return values.pop();
    }

    private static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch(NumberFormatException e){
            return false;
        }
    }
}
