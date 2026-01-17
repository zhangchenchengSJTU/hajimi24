// ExpressionHelper.java 完整修复代码
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
        String toLatex(Map<String, String> map, boolean isStructureMode, int parentPrec, boolean isRight, int mulMode, int divMode);
    }

    private static class ValueNode implements Node {
        private final String placeholder;
        ValueNode(String placeholder) { this.placeholder = placeholder; }

        private String getValue(Map<String, String> map, boolean isStructureMode) {
            if (isStructureMode) return "🐱";
            String val = map.get(placeholder);
            return (val != null) ? val : "";
        }

        private int getEffectivePrec(String val) {
            if (val.contains("+") || (val.lastIndexOf("-") > 0)) return 1;
            return 999;
        }

        @Override public String toHtml(Map<String, String> map, boolean isStructureMode) { return getValue(map, isStructureMode); }
        @Override public String toPlainText(Map<String, String> map, boolean isStructureMode) { return getValue(map, isStructureMode); }

        @Override
        public String toLatex(Map<String, String> map, boolean isStructureMode, int parentPrec, boolean isRight, int mulMode, int divMode) {
            String val = getValue(map, isStructureMode);
            String result;

            // 除法模式 1: 将数字分数转换为垂直分式
            if (divMode == 1 && !isStructureMode && val.contains("/")) {
                String[] p = val.split("/");
                result = "\\cfrac{" + p[0] + "}{" + p[1] + "}";
            } else {
                result = "\\text{" + val + "}";
            }

            int myPrec = getEffectivePrec(val);
            if (isStructureMode) myPrec = 999;

            boolean needBrackets = false;
            if (parentPrec > myPrec) needBrackets = true;
            if (parentPrec == 1 && isRight && myPrec == 1) needBrackets = true;

            if (needBrackets) return "\\left(" + result + "\\right)";
            return result;
        }
    }

    private static class OperatorNode implements Node {
        final char op;
        final Node left, right;
        OperatorNode(char op, Node right, Node left) { this.op = op; this.left = left; this.right = right; }

        private int getPrec(int divMode) {
            if (op == '*' || op == '×') return 2;
            if (op == '/') return (divMode == 2) ? 2 : 3;
            return 1;
        }

        @Override public String toPlainText(Map<String, String> map, boolean isStructureMode) {
            String l = left.toPlainText(map, isStructureMode);
            String r = right.toPlainText(map, isStructureMode);
            char dOp = (op == '*') ? '×' : (op == '/') ? '÷' : op;
            return "(" + l + " " + dOp + " " + r + ")";
        }

        @Override public String toHtml(Map<String, String> map, boolean isStructureMode) {
            String l = left.toHtml(map, isStructureMode);
            String r = right.toHtml(map, isStructureMode);
            return "(" + l + " " + ((op == '*') ? '×' : op) + " " + r + ")";
        }

        @Override
        public String toLatex(Map<String, String> map, boolean isStructureMode, int parentPrec, boolean isRight, int mulMode, int divMode) {
            int myPrec = getPrec(divMode);
            String lStr, rStr;

            if (op == '/' && (divMode == 0 || divMode == 1)) {
                lStr = left.toLatex(map, isStructureMode, 0, false, mulMode, divMode);
                rStr = right.toLatex(map, isStructureMode, 0, true, mulMode, divMode);
                return "\\cfrac{" + lStr + "}{" + rStr + "}";
            }

            lStr = left.toLatex(map, isStructureMode, myPrec, false, mulMode, divMode);
            rStr = right.toLatex(map, isStructureMode, myPrec, true, mulMode, divMode);

            String latexOp;
            if (op == '*' || op == '×') {
                if (mulMode == 0) latexOp = " \\times ";
                else if (mulMode == 1) latexOp = " \\cdot ";
                else {
                    boolean leftIsBracket = lStr.endsWith("\\right)");
                    boolean rightIsBracket = rStr.startsWith("\\left(");
                    // 识别 \text{..} 或 \cfrac{..}{..} 为数字
                    boolean leftIsNum = (lStr.startsWith("\\text{") || lStr.startsWith("\\cfrac{")) && lStr.endsWith("}");
                    boolean rightIsNum = (rStr.startsWith("\\text{") || rStr.startsWith("\\cfrac{")) && rStr.endsWith("}");

                    if ((leftIsBracket && rightIsBracket) || (leftIsNum && rightIsBracket) || (leftIsBracket && rightIsNum)) {
                        latexOp = " ";
                    } else {
                        latexOp = " \\cdot ";
                    }
                }
            } else if (op == '/') {
                latexOp = " \\div ";
            } else {
                latexOp = " " + op + " ";
            }

            String result = lStr + latexOp + rStr;
            boolean needBrackets = false;
            if (parentPrec > myPrec) needBrackets = true;
            if (parentPrec == myPrec && isRight && (parentPrec == 1 || parentPrec == 2)) needBrackets = true;

            if (needBrackets) return "\\left(" + result + "\\right)";
            return result;
        }
    }

    public static String getAsLatex(String expression, List<Fraction> numbers, boolean isStructureMode, int mulMode, int divMode) {
        if (expression == null) return "";
        String suffix = "";
        Pattern p = Pattern.compile("\\s*(mod|base)\\s*\\d+.*$");
        Matcher m = p.matcher(expression);
        if (m.find()) { suffix = m.group().trim(); expression = expression.substring(0, m.start()).trim(); }
        try {
            Map<String, String> placeholderMap = new HashMap<>();
            String placeholderExpression = createPlaceholders(expression, numbers, placeholderMap);
            Node root = parse(placeholderExpression);
            String latex = root.toLatex(placeholderMap, isStructureMode, 0, false, mulMode, divMode);
            if (!suffix.isEmpty()) latex += " \\quad \\left(\\text{" + suffix + "}\\right)";
            return latex;
        } catch (Exception e) { return ""; }
    }

    public static Spanned formatAnswer(String expression, List<Fraction> numbers) { return format(expression, numbers, false); }
    public static Spanned formatStructure(String expression, List<Fraction> numbers) { return format(expression, numbers, true); }
    public static String getAnswerAsPlainText(String expression, List<Fraction> numbers) { return getPlainText(expression, numbers, false); }
    public static String getStructureAsPlainText(String expression, List<Fraction> numbers) { return getPlainText(expression, numbers, true); }

    private static Spanned format(String expression, List<Fraction> numbers, boolean isStructureMode) {
        if (expression == null) return Html.fromHtml("", Html.FROM_HTML_MODE_LEGACY);
        String suffix = "";
        Pattern p = Pattern.compile("\\s*(mod|base)\\s*\\d+.*$");
        Matcher m = p.matcher(expression);
        if (m.find()) { suffix = m.group().trim(); expression = expression.substring(0, m.start()).trim(); }
        try {
            Map<String, String> placeholderMap = new HashMap<>();
            String placeholderExpression = createPlaceholders(expression, numbers, placeholderMap);
            Node root = parse(placeholderExpression);
            String html = root.toHtml(placeholderMap, isStructureMode);
            if (!suffix.isEmpty()) html += "&nbsp;&nbsp;&nbsp;<b>(" + suffix + ")</b>";
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } catch (Exception e) { return Html.fromHtml(expression.replace("*", "×"), Html.FROM_HTML_MODE_LEGACY); }
    }

    private static String getPlainText(String expression, List<Fraction> numbers, boolean isStructureMode) {
        if (expression == null) return "";
        String suffix = "";
        Pattern p = Pattern.compile("\\s*(mod|base)\\s*\\d+.*$");
        Matcher m = p.matcher(expression);
        if (m.find()) { suffix = m.group().trim(); expression = expression.substring(0, m.start()).trim(); }
        try {
            Map<String, String> placeholderMap = new HashMap<>();
            String placeholderExpression = createPlaceholders(expression, numbers, placeholderMap);
            Node root = parse(placeholderExpression);
            String suffixPart = suffix.isEmpty() ? "" : "   (" + suffix + ")";
            return root.toPlainText(placeholderMap, isStructureMode) + suffixPart;
        } catch (Exception e) { return expression + (suffix.isEmpty() ? "" : " " + suffix); }
    }

    private static String createPlaceholders(String expr, List<Fraction> nums, Map<String, String> map) {
        List<String> ns = new ArrayList<>();
        for (Fraction f : nums) ns.add(f.toString());
        ns.sort((a, b) -> b.length() - a.length());
        StringBuilder pb = new StringBuilder();
        for (String s : ns) { if (pb.length() > 0) pb.append("|"); pb.append(Pattern.quote(s)); }
        Matcher matcher = Pattern.compile(pb.toString()).matcher(expr);
        StringBuffer sb = new StringBuffer();
        int i = 0;
        while (matcher.find()) { String p = "#" + i + "#"; map.put(p, matcher.group(0)); matcher.appendReplacement(sb, p); i++; }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static Node parse(String expr) {
        expr = expr.replaceAll("(mod|base)\\s*\\d+.*", "").trim().replaceAll("\\s", "");
        Stack<Node> values = new Stack<>(); Stack<Character> ops = new Stack<>();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '(') { ops.push(c); i++; }
            else if (c == ')') {
                while (!ops.isEmpty() && ops.peek() != '(') values.push(new OperatorNode(ops.pop(), values.pop(), values.pop()));
                if (!ops.isEmpty()) ops.pop(); i++;
            } else if (c == '#') {
                int j = expr.indexOf('#', i + 1);
                values.push(new ValueNode(expr.substring(i, j + 1))); i = j + 1;
            } else {
                while (!ops.isEmpty() && hasPrecedence(c, ops.peek())) values.push(new OperatorNode(ops.pop(), values.pop(), values.pop()));
                ops.push(c); i++;
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
