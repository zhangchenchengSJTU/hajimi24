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
        // 修改：增加优先级和“是否为右操作数”的判定
        String toLatex(Map<String, String> map, boolean isStructureMode, int parentPrec, boolean isRight);
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
        @Override
        public String toLatex(Map<String, String> map, boolean isStructureMode, int parentPrec, boolean isRight) {
            String val = getValue(map, isStructureMode);
            // 数值节点永远不需要外层括号
            return "\\text{" + val + "}";
        }
    }

    private static class OperatorNode implements Node {
        final char op;
        final Node left, right;
        OperatorNode(char op, Node right, Node left) { this.op = op; this.left = left; this.right = right; }

        private int getPrec() {
            if (op == '*' || op == '×') return 2;
            if (op == '/' ) return 3; // 内部优先级
            return 1; // +, -
        }

        @Override
        public String toPlainText(Map<String, String> map, boolean isStructureMode) {
            String l = left.toPlainText(map, isStructureMode);
            String r = right.toPlainText(map, isStructureMode);
            char displayOp = (op == '*') ? '×' : (op == '/') ? '÷' : op;
            return "(" + l + " " + displayOp + " " + r + ")";
        }

        @Override
        public String toHtml(Map<String, String> map, boolean isStructureMode) {
            String l = left.toHtml(map, isStructureMode);
            String r = right.toHtml(map, isStructureMode);
            char displayOp = (op == '*') ? '×' : op;
            return "(" + l + " " + displayOp + " " + r + ")";
        }

        @Override
        public String toLatex(Map<String, String> map, boolean isStructureMode, int parentPrec, boolean isRight) {
            int myPrec = getPrec();
            String lStr, rStr;

            if (op == '/') {
                lStr = left.toLatex(map, isStructureMode, 0, false);
                rStr = right.toLatex(map, isStructureMode, 0, true);
                return "\\cfrac{" + lStr + "}{" + rStr + "}";
            } else {
                lStr = left.toLatex(map, isStructureMode, myPrec, false);
                rStr = right.toLatex(map, isStructureMode, myPrec, true);
            }

            String result = (op == '*' || op == '×') ? lStr + "\\cdot " + rStr : lStr + " " + op + " " + rStr;

            // 括号化简逻辑
            boolean needBrackets = false;
            if (parentPrec > myPrec) needBrackets = true;
            if (parentPrec == 1 && isRight && myPrec == 1) needBrackets = true;

            // 关键：确保 \left( 直接包裹数学内容，不被 \text 包裹，才能自适应分式高度
            if (needBrackets) return "\\left(" + result + "\\right)";
            return result;
        }

        // 兼容原有的无参调用
        public String toLatex(Map<String, String> map, boolean isStructureMode) {
            return toLatex(map, isStructureMode, 0, false);
        }
    }

    public static String getAsLatex(String expression, List<Fraction> numbers, boolean isStructureMode) {
        if (expression == null) return "";

        // --- 修复：补充提取后缀逻辑 ---
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

            // 初始 parentPrec 为 0
            String latex = root.toLatex(placeholderMap, isStructureMode, 0, false);

            if (!suffix.isEmpty()) {
                // LaTeX 间距与后缀括号
                latex += " \\quad \\left(\\text{" + suffix + "}\\right)";
            }
            return latex;
        } catch (Exception e) { return ""; }
    }

    public static Spanned formatAnswer(String expression, List<Fraction> numbers) { return format(expression, numbers, false); }
    public static Spanned formatStructure(String expression, List<Fraction> numbers) { return format(expression, numbers, true); }
    public static String getAnswerAsPlainText(String expression, List<Fraction> numbers) { return getPlainText(expression, numbers, false); }
    public static String getStructureAsPlainText(String expression, List<Fraction> numbers) { return getPlainText(expression, numbers, true); }

    private static Spanned format(String expression, List<Fraction> numbers, boolean isStructureMode) {
        if (expression == null) return Html.fromHtml("", Html.FROM_HTML_MODE_LEGACY);

        // --- 核心修改：提取并格式化后缀 ---
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
            String html = root.toHtml(placeholderMap, isStructureMode);

            // 将后缀加回去：增加间距并加括号
            if (!suffix.isEmpty()) {
                html += "&nbsp;&nbsp;&nbsp;<b>(" + suffix + ")</b>";
            }
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } catch (Exception e) {
            // 异常情况下的显示也保持一致
            String suffixPart = suffix.isEmpty() ? "" : "&nbsp;&nbsp;&nbsp;<b>(" + suffix + ")</b>";
            String mainPart = isStructureMode ? "解析结构失败" : expression.replace("*", "×");
            return Html.fromHtml(mainPart + suffixPart, Html.FROM_HTML_MODE_LEGACY);
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
            // 增加间距并加括号
            String suffixPart = suffix.isEmpty() ? "" : "   (" + suffix + ")";
            return root.toPlainText(placeholderMap, isStructureMode) + suffixPart;
        } catch (Exception e) {
            String suffixPart = suffix.isEmpty() ? "" : "   (" + suffix + ")";
            return (isStructureMode ? "解析结构失败" : expression.replace("*", "×")) + suffixPart;
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
