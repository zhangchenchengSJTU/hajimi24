package com.example.hajimi24;

public class Fraction {
    long num, den;

    public Fraction(long num, long den) {
        if (den == 0) throw new ArithmeticException("Division by zero");
        if (den < 0) { num = -num; den = -den; }
        long g = gcd(Math.abs(num), den);
        this.num = num / g;
        this.den = den / g;
    }

    public static Fraction parse(String s) {
        if (s.contains("/")) {
            String[] p = s.split("/");
            return new Fraction(Long.parseLong(p[0].trim()), Long.parseLong(p[1].trim()));
        } else {
            return new Fraction(Long.parseLong(s.trim()), 1);
        }
    }

    public Fraction add(Fraction o) { return new Fraction(num * o.den + o.num * den, den * o.den); }
    public Fraction sub(Fraction o) { return new Fraction(num * o.den - o.num * den, den * o.den); }
    public Fraction multiply(Fraction o) { return new Fraction(num * o.num, den * o.den); }
    public Fraction divide(Fraction o) { return new Fraction(num * o.den, den * o.num); }

    public boolean isValue(int val) { return den == 1 && num == val; }

    @Override
    public String toString() {
        if (den == 1) return String.valueOf(num);
        return num + "/" + den;
    }

    private static long gcd(long a, long b) { return b == 0 ? a : gcd(b, a % b); }
}
