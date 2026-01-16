package com.example.hajimi24;

public class Fraction {
    private final long re;
    private final long im;
    private final long de;
    private final int radix; // 新增：记录显示进制

    public long getRe() { return re; }
    public long getIm() { return im; }
    public long getDe() { return de; }
    public int getRadix() { return radix; }

    // 2参数：纯实数
    public Fraction(long num, long den) {
        this(num, 0, den, 10);
    }


    // 全能构造函数 (核心：确保 radix 被赋值)
    public Fraction(long re, long im, long de, int radix) {
        if (de == 0) throw new ArithmeticException("Division by zero");
        if (de < 0) { re = -re; im = -im; de = -de; }
        long common = gcd(Math.abs(re), gcd(Math.abs(im), de));
        this.re = re / common;
        this.im = im / common;
        this.de = de / common;
        this.radix = radix;
    }

    // 计算逻辑：必须传递 radix
    public Fraction add(Fraction o) {
        return new Fraction(re * o.de + o.re * de, im * o.de + o.im * de, de * o.de, this.radix);
    }
    public static Fraction parse(String s) {
        return parse(s, 10);
    }
    public Fraction applyMod(int mod) {
        try {
            // 计算分母的模逆元
            long invDe = modInverse(this.de, mod);
            if (invDe == -1) throw new ArithmeticException("Modular inverse not found");

            // 结果 = (分子 * 分母逆元) % mod
            long nRe = ((this.re % mod + mod) % mod * invDe) % mod;
            long nIm = ((this.im % mod + mod) % mod * invDe) % mod;

            // 返回一个新的分数，分母固定为 1，并保留原有进制属性
            return new Fraction(nRe, nIm, 1, this.radix);
        } catch (Exception e) {
            // 如果无法求逆（例如分母是模数的倍数），保持原样或处理异常
            return this;
        }
    }
    public static Fraction parse(String s, int radix) {
        s = s.trim().replace(" ", "").replace("(", "").replace(")", "");
        long nRe = 0, nIm = 0, nDe = 1;

        if (s.contains("/")) {
            String[] parts = s.split("/");
            try { nDe = Long.parseLong(parts[1], radix); } catch (Exception e) { nDe = 1; }
            s = parts[0];
        }

        if (s.endsWith("i")) {
            String work = s.substring(0, s.length() - 1);
            if (work.isEmpty()) nIm = 1;
            else if (work.equals("+")) nIm = 1;
            else if (work.equals("-")) nIm = -1;
            else {
                int splitIdx = -1;
                for (int k = work.length() - 1; k >= 0; k--) {
                    char c = work.charAt(k);
                    if ((c == '+' || c == '-') && k != 0) { splitIdx = k; break; }
                }
                if (splitIdx != -1) {
                    try { nRe = Long.parseLong(work.substring(0, splitIdx), radix); } catch (Exception e) {}
                    String imStr = work.substring(splitIdx);
                    if (imStr.equals("+")) nIm = 1;
                    else if (imStr.equals("-")) nIm = -1;
                    else { try { nIm = Long.parseLong(imStr, radix); } catch (Exception e) {} }
                } else {
                    try { nIm = Long.parseLong(work, radix); } catch (Exception e) {}
                }
            }
        } else {
            try { nRe = Long.parseLong(s, radix); } catch (Exception e) {}
        }
        return new Fraction(nRe, nIm, nDe, radix);
    }


    public Fraction sub(Fraction o) {
        return new Fraction(re * o.de - o.re * de, im * o.de - o.im * de, de * o.de, this.radix);
    }

    public Fraction multiply(Fraction o) {
        return new Fraction(re * o.re - im * o.im, re * o.im + im * o.re, de * o.de, this.radix);
    }

    public Fraction divide(Fraction o) {
        long denomTerm = o.re * o.re + o.im * o.im;
        if (denomTerm == 0) throw new ArithmeticException("Divide by zero complex");
        long newRe = (re * o.re + im * o.im) * o.de;
        long newIm = (im * o.re - re * o.im) * o.de;
        long newDe = de * denomTerm;
        return new Fraction(newRe, newIm, newDe, this.radix);
    }

    public boolean isValue(int val) {
        return de == 1 && im == 0 && re == val;
    }

    @Override
    public String toString() {
        return toString(this.radix); // 默认使用该数字自身的进制
    }

    public String toString(int radix) {
        StringBuilder sb = new StringBuilder();
        if (im == 0) {
            sb.append(Long.toString(re, radix).toUpperCase());
        } else {
            if (re != 0) sb.append(Long.toString(re, radix).toUpperCase());
            if (im > 0 && re != 0) sb.append("+");
            if (im == 1) sb.append("i");
            else if (im == -1) sb.append("-i");
            else sb.append(Long.toString(im, radix).toUpperCase()).append("i");
        }
        if (de != 1) {
            if (im != 0) return "(" + sb.toString() + ")/" + Long.toString(de, radix).toUpperCase();
            else return sb.toString() + "/" + Long.toString(de, radix).toUpperCase();
        }
        return sb.toString();
    }

    public String toModString(int mod) {
        // mod 运算默认使用 10 进制显示结果
        return toModString(mod, 10);
    }

    public String toModString(int mod, int radix) {
        try {
            long invDe = modInverse(de, mod);
            long valRe = (re % mod + mod) % mod;
            long valIm = (im % mod + mod) % mod;
            valRe = (valRe * invDe) % mod;
            valIm = (valIm * invDe) % mod;
            StringBuilder sb = new StringBuilder();
            if (valIm == 0) {
                sb.append(Long.toString(valRe, radix).toUpperCase());
            } else {
                if (valRe != 0) sb.append(Long.toString(valRe, radix).toUpperCase()).append("+");
                if (valIm == 1) sb.append("i");
                else sb.append(Long.toString(valIm, radix).toUpperCase()).append("i");
            }
            return sb.toString();
        } catch (Exception e) {
            return toString(radix);
        }
    }

    private static long modInverse(long a, long m) {
        long m0 = m, y = 0, x = 1;
        if (m == 1) return 0;
        a = (a % m + m) % m;
        while (a > 1) {
            long q = a / m, t = m;
            m = a % m; a = t; t = y;
            y = x - q * y; x = t;
        }
        return x < 0 ? x + m0 : x;
    }

    private static long gcd(long a, long b) { return b == 0 ? a : gcd(b, a % b); }
}
