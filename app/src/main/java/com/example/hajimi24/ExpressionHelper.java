package com.example.hajimi24;

import android.text.Html;
import android.text.Spanned;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpressionHelper {

    private interface Node {
        String toHtml(Map<String, String> map, boolean isStructureMode);
        String toPlainText(Map<String, String> map, boolean isStructureMode);
    }

    private static class ValueNode implements Node {
        private final String placeholder;
        ValueNode(String placeholder) { this.placeholder = placeholder; }

        private String getValue(Map<String, String> map, boolean isStructureMode) {
            if (isStructureMode) return "🐱";
            String val = map.get(placeholder);
            return (val != null) ? val : "";
        }

        @Override public String toHtml(Map<String, String> map, boolean isStructureMode) { return getValue(map, isStructureMode); }
        @Override public String toPlainText(Map<String, String> map, boolean isStructureMode) { return getValue(map, isStructureMode); }
    }

    private static class OperatorNode implements Node {
        final char op;
        final Node left;
        final Node right;

        OperatorNode(char op, Node right, Node left) {
            this.op = op; this.left = left; this.right = right;
        }

        private String render(Node node, Map<String, String> map, boolean isStructureMode, boolean isHtml) {
            return isHtml ? node.toHtml(map, isStructureMode) : node.toPlainText(map, isStructureMode);
        }

        private String process(Map<String, String> map, boolean isStructureMode, boolean isHtml) {
            String leftStr = render(left, map, isStructureMode, isHtml);
            String rightStr = render(right, map, isStructureMode, isHtml);
            char displayOp = (op == '*') ? '×' : op;
            String operatorStr = isHtml ? " <font color='#228B22'>" + displayOp + "</font> " : " " + displayOp + " ";
            if (left instanceof OperatorNode) leftStr = "(" + leftStr + ")";
            if (right instanceof OperatorNode) rightStr = "(" + rightStr + ")";
            return leftStr + operatorStr + rightStr;
        }

        @Override public String toHtml(Map<String, String> map, boolean isStructureMode) { return process(map, isStructureMode, true); }
        @Override public String toPlainText(Map<String, String> map, boolean isStructureMode) { return process(map, isStructureMode, false); }
    }

    public static Spanned formatAnswer(String expression, List<Fraction> numbers) { return format(expression, numbers, false); }
    public static Spanned formatStructure(String expression, List<Fraction> numbers) { return format(expression, numbers, true); }
    public static String getAnswerAsPlainText(String expression, List<Fraction> numbers) { return getPlainText(expression, numbers, false); }
    public static String getStructureAsPlainText(String expression, List<Fraction> numbers) { return getPlainText(expression, numbers, true); }

    private static Spanned format(String expression, List<Fraction> numbers, boolean isStructureMode) {
        if (expression == null) return Html.fromHtml("", Html.FROM_HTML_MODE_LEGACY);

        // --- 核心修改：统一处理 Mod/Base 后缀显示 ---
        String suffix = "";
        // 匹配 mod n 或 base n (甚至两者并存)
        Pattern p = Pattern.compile("\\s*(mod|base)\\s*\\d+.*$");
        Matcher m = p.matcher(expression);
        if (m.find()) {
            suffix = m.group().trim();
            expression = expression.substring(0, m.start()).trim();
        }

        try {
            Map<String, String> placeholderMap = new HashMap<>();
            String placeholderExpression = createPlaceholders(expression, numbers, placeholderMap);
            Node root = parse(placeholderExpression);
            String html = root.toHtml(placeholderMap, isStructureMode);

            // 将后缀加回去
            if (!suffix.isEmpty()) {
                html += " <b>" + suffix + "</b>";
            }
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } catch (Exception e) {
            String fallback = isStructureMode ? "解析结构失败" : expression.replace("*", "×") + " " + suffix;
            return Html.fromHtml(fallback, Html.FROM_HTML_MODE_LEGACY);
        }
    }

    private static String getPlainText(String expression, List<Fraction> numbers, boolean isStructureMode) {
        if (expression == null) return "";

        String suffix = "";
        Pattern p = Pattern.compile("\\s*(mod|base)\\s*\\d+.*$");
        Matcher m = p.matcher(expression);
        if (m.find()) {
            suffix = m.group().trim();
            expression = expression.substring(0, m.start()).trim();
        }

        try {
            Map<String, String> placeholderMap = new HashMap<>();
            String placeholderExpression = createPlaceholders(expression, numbers, placeholderMap);
            Node root = parse(placeholderExpression);
            return root.toPlainText(placeholderMap, isStructureMode) + " " + suffix;
        } catch (Exception e) {
            return (isStructureMode ? "解析结构失败" : expression.replace("*", "×")) + " " + suffix;
        }
    }

    private static String createPlaceholders(String expression, List<Fraction> numbers, Map<String, String> map) {
        List<String> numStrList = new ArrayList<>();
        // 注意：由于 Fraction.toString() 已能自动按进制返回字符，这里能正确匹配 'A'
        for (Fraction f : numbers) numStrList.add(f.toString());
        Collections.sort(numStrList, (a, b) -> b.length() - a.length());

        StringBuilder patternBuilder = new StringBuilder();
        for (String s : numStrList) {
            if (patternBuilder.length() > 0) patternBuilder.append("|");
            patternBuilder.append(Pattern.quote(s));
        }

        Pattern pattern = Pattern.compile(patternBuilder.toString());
        Matcher matcher = pattern.matcher(expression);
        StringBuffer sb = new StringBuffer();
        int i = 0;

        while (matcher.find()) {
            String placeholder = "#" + i + "#";
            map.put(placeholder, matcher.group(0));
            matcher.appendReplacement(sb, placeholder);
            i++;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static Node parse(String expression) {
        // --- 核心修改：剥离后缀防止解析非法字符 ---
        expression = expression.replaceAll("(mod|base)\\s*\\d+.*", "").trim();

        expression = expression.replaceAll("\\s", "");
        Stack<Node> values = new Stack<>();
        Stack<Character> ops = new Stack<>();
        int i = 0;
        while (i < expression.length()) {
            char c = expression.charAt(i);
            if (c == '(') { ops.push(c); i++; }
            else if (c == ')') {
                while (!ops.isEmpty() && ops.peek() != '(') {
                    values.push(new OperatorNode(ops.pop(), values.pop(), values.pop()));
                }
                if (!ops.isEmpty()) ops.pop();
                i++;
            } else if (c == '#') {
                int j = expression.indexOf('#', i + 1);
                values.push(new ValueNode(expression.substring(i, j + 1)));
                i = j + 1;
            } else {
                while (!ops.isEmpty() && hasPrecedence(c, ops.peek())) {
                    values.push(new OperatorNode(ops.pop(), values.pop(), values.pop()));
                }
                ops.push(c);
                i++;
            }
        }
        while (!ops.isEmpty()) values.push(new OperatorNode(ops.pop(), values.pop(), values.pop()));
        return values.pop();
    }

    private static boolean hasPrecedence(char op1, char op2) {
        if (op2 == '(' || op2 == ')') return false;
        int p1 = (op1 == '*' || op1 == '/') ? 2 : 1;
        int p2 = (op2 == '*' || op2 == '/') ? 2 : 1;
        return p1 <= p2;
    }
}
