package com.example.hajimi24;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SolutionNormalizer {

    public static List<String> distinct(List<String> solutions) {
        List<String> result = new ArrayList<>();
        Map<String, String> map = new HashMap<>();

        for (String sol : solutions) {
            try {
                // 1. 预处理：去掉可能的编号前缀如 "[1] "
                String cleanSol = sol.replaceAll("^\\[\\d+\\]\\s*", "").trim();

                // 2. 增强后缀剥离：匹配 =结果、mod、base 等，支持非数字（如复数）后缀
                String mathPart = cleanSol;
                String suffix = "";
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\s*(=|\\b(mod|base)\\b).*$");
                java.util.regex.Matcher m = p.matcher(cleanSol);
                if (m.find()) {
                    suffix = m.group();
                    mathPart = cleanSol.substring(0, m.start()).trim();
                }

                // 3. 分式原子化保护：
                // 将形如 "2/3" 或 "9/4" 的数值分式强制加上括号，防止解析器将其关联错误
                // 解决 a / 2/3 变成 (a/2)/3 的问题，确保其被视为 a / (2/3)
                mathPart = mathPart.replaceAll("(?<![\\d\\.])(\\d+(\\.\\d+)?/\\d+(\\.\\d+)?)(?![\\d\\.])", "($1)");

                // 4. 生成指纹
                Node root = parse(mathPart);
                String signature = root.getSignature() + "|" + suffix.trim();

                if (map.containsKey(signature)) {
                    String existing = map.get(signature);
                    // 保留更短或字典序更小的
                    if (sol.length() < existing.length() || (sol.length() == existing.length() && sol.compareTo(existing) < 0)) {
                        map.put(signature, sol);
                    }
                } else {
                    map.put(signature, sol);
                }
            } catch (Exception e) {
                if (!result.contains(sol)) result.add(sol);
            }
        }
        result.addAll(map.values());
        return result;
    }

    private interface Node {
        String getSignature();
    }

    private static class ValueNode implements Node {
        String val;
        ValueNode(String v) { this.val = v; }
        @Override public String getSignature() { return "V(" + val + ")"; }
    }

    private static class OpNode implements Node {
        String type;
        List<Node> positives = new ArrayList<>();
        List<Node> negatives = new ArrayList<>();

        OpNode(String type) { this.type = type; }

        void addTerm(Node child, boolean isPositive) {
            // 过滤一元负号产生的空节点 (例如 "-i+1" 会产生一个空左节点)
            if (child instanceof ValueNode && ((ValueNode) child).val.isEmpty()) return;

            if (child instanceof OpNode && ((OpNode) child).type.equals(this.type)) {
                OpNode childOp = (OpNode) child;
                if (isPositive) {
                    this.positives.addAll(childOp.positives);
                    this.negatives.addAll(childOp.negatives);
                } else {
                    // 处理符号/分母反转： - (a - b) -> -a + b ; / (a / b) -> / a * b
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
            List<String> posSigs = new ArrayList<>();
            List<String> negSigs = new ArrayList<>();
            for (Node n : positives) posSigs.add(n.getSignature());
            for (Node n : negatives) negSigs.add(n.getSignature());
            Collections.sort(posSigs);
            Collections.sort(negSigs);
            return type + "{P" + posSigs + ",N" + negSigs + "}";
        }
    }

    private static Node parse(String expr) {
        expr = stripOuterParens(expr.trim());
        int splitIdx = findMainOperatorIndex(expr);

        if (splitIdx == -1) return new ValueNode(expr);

        char op = expr.charAt(splitIdx);
        Node left = parse(expr.substring(0, splitIdx));
        Node right = parse(expr.substring(splitIdx + 1));

        if (op == '+' || op == '-') {
            OpNode node = new OpNode("SUM");
            node.addTerm(left, true);
            node.addTerm(right, op == '+');
            return node;
        } else if (op == '*' || op == '/' || op == '×') {
            OpNode node = new OpNode("PROD");
            node.addTerm(left, true);
            node.addTerm(right, op == '*' || op == '×');
            return node;
        }
        return new ValueNode(expr);
    }

    private static String stripOuterParens(String s) {
        while (s.startsWith("(") && s.endsWith(")")) {
            int balance = 0;
            boolean strip = true;
            for (int i = 0; i < s.length() - 1; i++) {
                if (s.charAt(i) == '(') balance++;
                else if (s.charAt(i) == ')') balance--;
                if (balance == 0) { strip = false; break; }
            }
            if (strip) s = s.substring(1, s.length() - 1).trim();
            else break;
        }
        return s;
    }

    private static int findMainOperatorIndex(String s) {
        int balance = 0, bestIdx = -1, minPrec = 999;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') balance++;
            else if (c == '(') balance--;
            else if (balance == 0) {
                int prec = -1;
                if (c == '+' || c == '-') prec = 1;
                else if (c == '*' || c == '/' || c == '×') prec = 2;
                if (prec != -1 && prec < minPrec) {
                    minPrec = prec;
                    bestIdx = i;
                }
            }
        }
        return bestIdx;
    }
}
