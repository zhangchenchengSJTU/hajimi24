package com.example.hajimi24;

public class Fraction {
    // 核心数据结构：表示复数 (re + im * i) / de
    private final long re;
    private final long im;
    private final long de;

    // 兼容旧代码的构造函数（纯实数分数）
    public Fraction(long num, long den) {
        this(num, 0, den);
    }

    // 全能构造函数（复数分数）
    public Fraction(long re, long im, long de) {
        if (de == 0) throw new ArithmeticException("Division by zero");
        if (de < 0) { re = -re; im = -im; de = -de; }

        // 约分：计算三者的最大公约数
        long common = gcd(Math.abs(re), gcd(Math.abs(im), de));
        this.re = re / common;
        this.im = im / common;
        this.de = de / common;
    }

    public static Fraction parse(String s, int radix) {
        // 数据清洗：去空格，去括号
        s = s.trim().replace(" ", "").replace("(", "").replace(")", "");

        long nRe = 0, nIm = 0, nDe = 1;

        // 1. 处理分母 (例如 "1/2" 在 16 进制下)
        if (s.contains("/")) {
            String[] parts = s.split("/");
            s = parts[0];
            try {
                // 使用 radix 解析分母
                nDe = Long.parseLong(parts[1], radix);
            } catch (NumberFormatException e) {
                nDe = 1;
            }
        }

        // 2. 处理分子
        if (s.endsWith("i")) {
            // 复数逻辑 (注意：如果进制 > 18，'i' 可能会被误判为数字，这里假设进制 <= 16)
            String work = s.substring(0, s.length() - 1);

            if (work.isEmpty()) { nIm = 1; }
            else if (work.equals("+")) { nIm = 1; }
            else if (work.equals("-")) { nIm = -1; }
            else {
                int splitIdx = -1;
                // 从后往前找符号，排除掉可能的字母数字
                for (int k = work.length() - 1; k >= 0; k--) {
                    char c = work.charAt(k);
                    if (c == '+' || c == '-') {
                        if (k == 0) { /* 负号在首位，不是分隔符 */ }
                        else { splitIdx = k; break; }
                    }
                }

                if (splitIdx != -1) {
                    String reStr = work.substring(0, splitIdx);
                    String imStr = work.substring(splitIdx); // 包含符号

                    // 解析实部
                    try { nRe = Long.parseLong(reStr, radix); } catch (Exception e) {}

                    // 解析虚部
                    if (imStr.equals("+")) nIm = 1;
                    else if (imStr.equals("-")) nIm = -1;
                    else {
                        try {
                            // 处理 "+A", "-B" 这种情况
                            // parseLong 不支持带 '+' 号 (Base 10 除外)，需手动处理
                            if (imStr.startsWith("+")) {
                                nIm = Long.parseLong(imStr.substring(1), radix);
                            } else {
                                // parseLong 支持 "-"
                                nIm = Long.parseLong(imStr, radix);
                            }
                        } catch (Exception e) {}
                    }
                } else {
                    // 纯虚数
                    try { nIm = Long.parseLong(work, radix); } catch (Exception e) {}
                }
            }
        } else {
            // 纯实数 (最常见情况)
            try {
                nRe = Long.parseLong(s, radix);
            } catch (Exception e) {}
        }

        return new Fraction(nRe, nIm, nDe);
    }

    // 复数加法
    public Fraction add(Fraction o) {
        return new Fraction(
                this.re * o.de + o.re * this.de,
                this.im * o.de + o.im * this.de,
                this.de * o.de
        );
    }

    // 复数减法
    public Fraction sub(Fraction o) {
        return new Fraction(
                this.re * o.de - o.re * this.de,
                this.im * o.de - o.im * this.de,
                this.de * o.de
        );
    }

    // 复数乘法: (a+bi)(c+di) = (ac-bd) + (ad+bc)i
    public Fraction multiply(Fraction o) {
        return new Fraction(
                this.re * o.re - this.im * o.im,
                this.re * o.im + this.im * o.re,
                this.de * o.de
        );


    }

    // 复数除法: (a+bi)/(c+di) = (a+bi)(c-di) / (c^2+d^2)
    public Fraction divide(Fraction o) {
        long denomTerm = o.re * o.re + o.im * o.im;
        if (denomTerm == 0) throw new ArithmeticException("Divide by zero complex");

        // 分子 = (re+im*i) * o.de * (o.re-o.im*i)
        // 分母 = de * o.de^2 * (o.re^2/o.de^2 + o.im^2/o.de^2)
        // 简化公式： A/B = (n1/d1) / (n2/d2) = (n1 * d2) / (d1 * n2)
        // n1 * conj(n2) / (d1 * n2 * conj(n2)) * d2 ?
        // 标准推导: (re/de + im/de i) / (ore/ode + oim/ode i)
        // 结果的分子实部: (re * ore + im * oim) * ode
        // 结果的分子虚部: (im * ore - re * oim) * ode
        // 结果的分母: de * (ore^2 + oim^2)

        long newRe = (this.re * o.re + this.im * o.im) * o.de;
        long newIm = (this.im * o.re - this.re * o.im) * o.de;
        long newDe = this.de * denomTerm;

        return new Fraction(newRe, newIm, newDe);
    }

    // 检查是否等于目标值（比如24），必须是实数
    public boolean isValue(int val) {
        return de == 1 && im == 0 && re == val;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // 构建分子字符串
        if (im == 0) {
            sb.append(re);
        } else {
            if (re != 0) sb.append(re);

            if (im > 0 && re != 0) sb.append("+");

            if (im == 1) sb.append("i");
            else if (im == -1) sb.append("-i");
            else sb.append(im).append("i");
        }

        // 构建分母
        if (de != 1) {
            // 如果分子是复数，加括号包裹
            if (im != 0) {
                return "(" + sb.toString() + ")/" + de;
            } else {
                return sb.toString() + "/" + de;
            }
        }


        return sb.toString();





    }

    private static long gcd(long a, long b) { return b == 0 ? a : gcd(b, a % b); }

}