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

            // 1. 处理分式并将 i 替换为 \mathrm{i}
            if (divMode == 1 && !isStructureMode && val.contains("/")) {
                String[] p = val.split("/");
                // 分子分母分别替换
                result = "\\cfrac{" + p[0].replace("i", "\\mathrm{i}") + "}{" + p[1].replace("i", "\\mathrm{i}") + "}";
            } else {
                result = val.replace("i", "\\mathrm{i}");
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

    public static int getLatexWidth(String s) {
        if (s == null || s.trim().isEmpty()) return 0;

        int totalWidth = 0;
        int i = 0;
        while (i < s.length()) {
            if (s.startsWith("\\cfrac{", i)) {
                int firstOpen = i + 6;
                int firstClose = findMatchingBrace(s, firstOpen);
                if (firstClose != -1) {
                    int secondOpen = s.indexOf("{", firstClose);
                    if (secondOpen != -1) {
                        int secondClose = findMatchingBrace(s, secondOpen);
                        if (secondClose != -1) {
                            // 【核心逻辑】：分式占用宽度取分子分母的 Max
                            totalWidth += Math.max(getLatexWidth(s.substring(firstOpen + 1, firstClose)),
                                    getLatexWidth(s.substring(secondOpen + 1, secondClose)));
                            i = secondClose + 1; continue;
                        }
                    }
                }
            } else if (s.startsWith("\\mathrm{", i)) {
                // 【新增】：统计 mathrm 内部内容的视觉宽度
                int open = i + 7;
                int close = findMatchingBrace(s, open);
                if (close != -1) {
                    totalWidth += getLatexWidth(s.substring(open + 1, close));
                    i = close + 1; continue;
                }
            } else if (s.startsWith("\\left(", i) || s.startsWith("\\right)", i)) {
                totalWidth += 1;
                i += 7;
            } else if (s.startsWith("\\times", i) || s.startsWith("\\cdot", i) || s.startsWith("\\div", i)) {
                totalWidth += 1; // 运算符
                i++;
                while (i < s.length() && Character.isLetter(s.charAt(i))) i++;
            } else if (s.startsWith("\\quad", i)) {
                totalWidth += 2; // 空格
                i += 5;
            } else if (s.charAt(i) == '\\' || s.charAt(i) == '{' || s.charAt(i) == '}' || Character.isWhitespace(s.charAt(i))) {
                i++; // 忽略语法控制符
            } else {
                totalWidth += 1; // 普通数字、算子、或 🐱
                i++;
            }
        }
        return totalWidth;
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
                    // 【核心逻辑适配】：
                    // 如果移除 \text{}，则判断逻辑改为：只要不是以 \right) 或 \left( 包装的，即视为数字或分式
                    boolean leftIsBracket = lStr.endsWith("\\right)");
                    boolean rightIsBracket = rStr.startsWith("\\left(");

                    // 只要不是括号包裹，就认为是数字或分式（如 10 或 \cfrac{...}{...}）
                    boolean leftIsNumOrFrac = !leftIsBracket;
                    boolean rightIsNumOrFrac = !rightIsBracket;

                    if ((leftIsBracket && rightIsBracket) || (leftIsNumOrFrac && rightIsBracket) || (leftIsBracket && rightIsNumOrFrac)) {
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

        String suffixLatex = "";
        // 1. 匹配并提取后缀关键词和数字
        Pattern pSuffix = Pattern.compile("(?i)(mod|base)\\s*(\\d+)");
        Matcher mSuffix = pSuffix.matcher(expression);
        if (mSuffix.find()) {
            String keyword = mSuffix.group(1).toLowerCase(); // mod 或 base
            String value = mSuffix.group(2);                // 数字

            // 【核心修改】：使用 \operatorname 替换 \text
            // 结果形如：\operatorname{mod} 73
            suffixLatex = "\\operatorname{" + keyword + "} " + value;
        }

        // 2. 清洗原始表达式，移除所有后缀内容
        String cleanExpr = expression.replaceAll("(?i)\\s*(mod|base)\\s*\\d+.*", "").trim();

        try {
            Map<String, String> placeholderMap = new HashMap<>();
            String placeholderExpression = createPlaceholders(cleanExpr, numbers, placeholderMap);
            Node root = parse(placeholderExpression);
            String latex = root.toLatex(placeholderMap, isStructureMode, 0, false, mulMode, divMode);

            // 3. 拼接带括号的算子后缀
            if (!suffixLatex.isEmpty()) {
                // 输出结果形如： \quad \left(\operatorname{mod} 73\right)
                latex += " \\quad \\left(" + suffixLatex + "\\right)";
            }
            return latex;
        } catch (Exception e) {
            return "";
        }
    }


    public static Spanned formatAnswer(String expression, List<Fraction> numbers) { return format(expression, numbers, false); }
    public static Spanned formatStructure(String expression, List<Fraction> numbers) { return format(expression, numbers, true); }
    public static String getAnswerAsPlainText(String expression, List<Fraction> numbers) { return getPlainText(expression, numbers, false); }
    public static String getStructureAsPlainText(String expression, List<Fraction> numbers) { return getPlainText(expression, numbers, true); }

    private static Spanned format(String expression, List<Fraction> numbers, boolean isStructureMode) {
        if (expression == null) return Html.fromHtml("", Html.FROM_HTML_MODE_LEGACY);

        String suffix = "";
        Pattern pSuffix = Pattern.compile("(?i)(mod|base)\\s*(\\d+)");
        Matcher mSuffix = pSuffix.matcher(expression);
        if (mSuffix.find()) {
            suffix = mSuffix.group(1).toLowerCase() + " " + mSuffix.group(2);
        }
        String cleanExpr = expression.replaceAll("(?i)\\s*(mod|base)\\s*\\d+.*", "").trim();

        try {
            Map<String, String> placeholderMap = new HashMap<>();
            String placeholderExpression = createPlaceholders(cleanExpr, numbers, placeholderMap);
            Node root = parse(placeholderExpression);
            String html = root.toHtml(placeholderMap, isStructureMode);
            if (!suffix.isEmpty()) html += "&nbsp;&nbsp;&nbsp;<b>(" + suffix + ")</b>";
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } catch (Exception e) {
            return Html.fromHtml(expression.replace("*", "×"), Html.FROM_HTML_MODE_LEGACY);
        }
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

    /**
     * 计算 LaTeX 字符串的“垂直高度”
     * 规则：普通文本高度为 1，分数 \cfrac{A}{B} 的高度为 高度(A) + 高度(B)
     */
    public static int getLatexHeight(String s) {
        if (s == null || s.trim().isEmpty()) return 0;

        int maxHeight = 1; // 默认至少 1 层
        int i = 0;
        while (i < s.length()) {
            if (s.startsWith("\\cfrac{", i)) {
                int firstOpen = i + 6;
                int firstClose = findMatchingBrace(s, firstOpen);
                if (firstClose != -1) {
                    int secondOpen = s.indexOf("{", firstClose);
                    if (secondOpen != -1) {
                        int secondClose = findMatchingBrace(s, secondOpen);
                        if (secondClose != -1) {
                            String num = s.substring(firstOpen + 1, firstClose);
                            String den = s.substring(secondOpen + 1, secondClose);

                            // 分式内部高度是叠加的
                            int fractionHeight = getLatexHeight(num) + getLatexHeight(den);
                            // 但在这一行中，总高度取最大值 (Max)
                            maxHeight = Math.max(maxHeight, fractionHeight);

                            i = secondClose + 1;
                            continue;
                        }
                    }
                }
            }
            i++;
        }
        return maxHeight;
    }

    private static int calculateVisualHeight(String s) {
        // 基本情况：如果不包含分数命令，高度就是 1 层
        if (!s.contains("\\cfrac")) return 1;

        int maxHeight = 1;
        int i = 0;
        while (i < s.length()) {
            // 匹配顶层的 \cfrac{
            if (s.startsWith("\\cfrac{", i)) {
                int firstOpen = i + 6;
                int firstClose = findMatchingBrace(s, firstOpen);
                if (firstClose != -1) {
                    // 寻找分母部分的起始 {
                    int secondOpen = s.indexOf("{", firstClose);
                    if (secondOpen != -1) {
                        int secondClose = findMatchingBrace(s, secondOpen);
                        if (secondClose != -1) {
                            // 递归提取分子和分母
                            String num = s.substring(firstOpen + 1, firstClose);
                            String den = s.substring(secondOpen + 1, secondClose);

                            // 【核心逻辑】：高度相加
                            int h = calculateVisualHeight(num) + calculateVisualHeight(den);

                            // 如果一行有多个分数，取最高的那一个
                            if (h > maxHeight) maxHeight = h;

                            // 跳过已处理的部分
                            i = secondClose + 1;
                            continue;
                        }
                    }
                }
            }
            i++;
        }
        return maxHeight;
    }

    private static int findMatchingBrace(String s, int openIdx) {
        if (openIdx < 0 || openIdx >= s.length() || s.charAt(openIdx) != '{') return -1;
        int balance = 0;
        for (int i = openIdx; i < s.length(); i++) {
            if (s.charAt(i) == '{') balance++;
            else if (s.charAt(i) == '}') balance--;
            if (balance == 0) return i;
        }
        return -1;
    }



}
