package com.example.hajimi24;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SolutionNormalizer {

    /**
     * 对表达式列表进行去重.
     * 保留最短的、字典序最小的那个表达式作为代表.
     */
    public static List<String> distinct(List<String> solutions) {
        List<String> result = new ArrayList<>();
        // 指纹 -> 原始表达式
        Map<String, String> map = new HashMap<>();

        for (String sol : solutions) {
            try {
                // 1. 解析并生成指纹
                Node root = parse(sol);
                String signature = root.getSignature();

                // 2. 择优保留：如果指纹已存在，保留较短的那个；如果长度一样，保留字典序较小的
                if (map.containsKey(signature)) {
                    String existing = map.get(signature);
                    if (sol.length() < existing.length() || (sol.length() == existing.length() && sol.compareTo(existing) < 0)) {
                        map.put(signature, sol);
                    }
                } else {
                    map.put(signature, sol);
                }
            } catch (Exception e) {
                // 如果解析出错（极其罕见），则不去重，直接保留
                if (!result.contains(sol)) result.add(sol);
            }
        }
        result.addAll(map.values());
        return result;
    }

    // --- 内部 AST 节点定义 ---

    private interface Node {
        String getSignature();
    }

    private static class ValueNode implements Node {
        String val;
        ValueNode(String v) { this.val = v; }
        @Override public String getSignature() { return "V(" + val + ")"; }
    }

    // 用于处理加减法 (SUM) 或 乘除法 (PROD)
    private static class OpNode implements Node {
        String type; // "SUM" or "PROD"
        List<Node> positives = new ArrayList<>();
        List<Node> negatives = new ArrayList<>();

        OpNode(String type) { this.type = type; }

        void addTerm(Node child, boolean isPositive) {
            // --- 核心逻辑：结合律与符号处理 ---
            // 如果子节点类型与当前节点一致，进行合并（结合律）
            // 例如：(a + b) + c -> a + b + c
            // 例如：a - (b - c) -> a - b + c  (此时 child 是 Sum, 且 isPositive 为 false)
            if (child instanceof OpNode && ((OpNode) child).type.equals(this.type)) {
                OpNode childOp = (OpNode) child;
                if (isPositive) {
                    // + (a - b) -> +a -b
                    this.positives.addAll(childOp.positives);
                    this.negatives.addAll(childOp.negatives);
                } else {
                    // - (a - b) -> -a +b  (符号反转)
                    // / (a / b) -> /a *b  (分子分母反转)
                    this.positives.addAll(childOp.negatives);
                    this.negatives.addAll(childOp.positives);
                }
            } else {
                if (isPositive) this.positives.add(child);
                else this.negatives.add(child);
            }
        }

        @Override
        public String getSignature() {
            // --- 核心逻辑：交换律 ---
            // 对子节点的指纹进行排序
            List<String> posSigs = new ArrayList<>();
            List<String> negSigs = new ArrayList<>();
            for (Node n : positives) posSigs.add(n.getSignature());
            for (Node n : negatives) negSigs.add(n.getSignature());

            Collections.sort(posSigs);
            Collections.sort(negSigs);

            return type + "{P" + posSigs + ",N" + negSigs + "}";
        }
    }

    // --- 解析器 ---

    private static Node parse(String expr) {
        // 去除外层多余括号
        expr = stripOuterParens(expr.trim());

        // 寻找主运算符 (分割点)
        int splitIdx = findMainOperatorIndex(expr);

        if (splitIdx == -1) {
            // 已经是叶子节点 (数字)
            return new ValueNode(expr);
        }

        char op = expr.charAt(splitIdx);
        String leftStr = expr.substring(0, splitIdx);
        String rightStr = expr.substring(splitIdx + 1);

        Node left = parse(leftStr);
        Node right = parse(rightStr);

        // 构建节点
        if (op == '+' || op == '-') {
            OpNode node = new OpNode("SUM");
            node.addTerm(left, true);      // 左边总是正的
            node.addTerm(right, op == '+'); // 右边看符号
            return node;
        } else if (op == '*' || op == '/' || op == '×') {
            OpNode node = new OpNode("PROD");
            node.addTerm(left, true);      // 左边总是分子
            node.addTerm(right, op == '*' || op == '×'); // 右边看符号
            return node;
        }
        return new ValueNode(expr);
    }

    private static String stripOuterParens(String s) {
        while (s.startsWith("(") && s.endsWith(")")) {
            int balance = 0;
            boolean strip = true;
            for (int i = 0; i < s.length() - 1; i++) {
                char c = s.charAt(i);
                if (c == '(') balance++;
                else if (c == ')') balance--;
                if (balance == 0) {
                    strip = false;
                    break;
                }
            }
            if (strip) s = s.substring(1, s.length() - 1).trim();
            else break;
        }
        return s;
    }

    private static int findMainOperatorIndex(String s) {
        int balance = 0;
        int bestIdx = -1;
        int minPrec = 999;

        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') balance++;
            else if (c == '(') balance--;
            else if (balance == 0) {
                int prec = -1;
                if (c == '+' || c == '-') prec = 1;
                else if (c == '*' || c == '/' || c == '×') prec = 2;

                if (prec != -1) {
                    if (prec < minPrec) {
                        minPrec = prec;
                        bestIdx = i;
                    }
                }
            }
        }
        return bestIdx;
    }
}
