package com.example.hajimi24;

public class Fraction {
    // 核心修改：支持复数 (re + im * i) / den
    // re: 实部分子, im: 虚部分子, den: 公分母
    public final long re;
    public final long im;
    public final long den;

    // 构造函数：完整复数构造
    public Fraction(long re, long im, long den) {
        if (den == 0) throw new ArithmeticException("Division by zero");
        if (den < 0) { re = -re; im = -im; den = -den; }
        long common = gcd(Math.abs(re), gcd(Math.abs(im), den));
        this.re = re / common;
        this.im = im / common;
        this.den = den / common;
    }

    // 构造函数：兼容旧代码的实数构造 (re/den)
    public Fraction(long num, long den) {
        this(num, 0, den);
    }

    // 解析逻辑：增强以支持 "i", "(1+i)", "3-2i" 等格式
    public static Fraction parse(String s) {
        s = s.trim();
        // 去除可能存在的括号，例如 (1+i) -> 1+i
        if (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1);
        }
        // 如果输入包含除号 (如 1/2)，虽然题目数据多为整数/高斯整数，但保留以防万一
        if (s.contains("/")) {
            String[] parts = s.split("/");
            return parse(parts[0]).divide(parse(parts[1]));
        }

        long re = 0, im = 0;
        try {
            if (!s.contains("i")) {
                re = Long.parseLong(s);
            } else {
                // 处理复数格式：寻找实部和虚部的分隔符 (+ 或 -)
                // 忽略末尾的 'i' 字符寻找分隔符
                int splitIdx = -1;
                for (int k = s.length() - 2; k >= 0; k--) {
                    char c = s.charAt(k);
                    if (c == '+' || c == '-') {
                        splitIdx = k;
                        break;
                    }
                }

                if (splitIdx == -1) {
                    // 纯虚数情况： "i", "-i", "3i"
                    String part = s.substring(0, s.length() - 1); // 去掉 i
                    if (part.isEmpty() || part.equals("+")) im = 1;
                    else if (part.equals("-")) im = -1;
                    else im = Long.parseLong(part);
                } else {
                    // 复数情况： "1+i", "3-5i"
                    String reStr = s.substring(0, splitIdx);
                    String imStr = s.substring(splitIdx, s.length() - 1); // 包含符号，去掉 i

                    if (reStr.isEmpty()) re = 0;
                    else re = Long.parseLong(reStr);

                    if (imStr.equals("+")) im = 1;
                    else if (imStr.equals("-")) im = -1;
                    else im = Long.parseLong(imStr);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析数字: " + s);
        }
        return new Fraction(re, im, 1);
    }

    // 运算逻辑更新：复数运算
    public Fraction add(Fraction o) {
        return new Fraction(re * o.den + o.re * den, im * o.den + o.im * den, den * o.den);
    }

    public Fraction sub(Fraction o) {
        return new Fraction(re * o.den - o.re * den, im * o.den - o.im * den, den * o.den);
    }

    public Fraction multiply(Fraction o) {
        return new Fraction(re * o.re - im * o.im, re * o.im + im * o.re, den * o.den);
    }

    public Fraction divide(Fraction o) {
        // 复数除法：分子分母同乘共轭
        long magSq = o.re * o.re + o.im * o.im;
        return new Fraction(
                o.den * (re * o.re + im * o.im),
                o.den * (im * o.re - re * o.im),
                den * magSq
        );
    }

    // 辅助方法：判断是否为0 (用于除法检查)
    public boolean isZero() {
        return re == 0 && im == 0;
    }

    // 判断结果是否为目标值 (对于24点，虚部必须为0)
    public boolean isValue(int val) {
        return den == 1 && im == 0 && re == val;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        // 如果是复杂分数，加括号
        boolean isComplex = (den != 1);
        if (isComplex) sb.append("(");

        if (im == 0) {
            sb.append(re);
        } else {
            if (re != 0) sb.append(re);
            // 处理虚部符号
            if (im > 0 && re != 0) sb.append("+");

            if (im == 1) sb.append("i");
            else if (im == -1) sb.append("-i");
            else sb.append(im).append("i");
        }

        if (isComplex) sb.append(")/").append(den);
        return sb.toString();
    }

    private static long gcd(long a, long b) { return b == 0 ? a : gcd(b, a % b); }
}
