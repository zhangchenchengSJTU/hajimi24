package com.example.hajimi24;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class Solver {
    public static class Problem {
        List<Fraction> numbers;
        String solution;
        public Problem(List<Fraction> n, String s) { this.numbers = n; this.solution = s; }
    }

    // 默认求解入口 (兼容旧代码)
    public static String solve(List<Fraction> nums) {
        return solve(nums, null, 24);
    }

    // 支持 Mod 的旧入口 (兼容旧代码)
    public static String solve(List<Fraction> nums, Integer modulus) {
        return solve(nums, modulus, 24);
    }

    /**
     * 核心入口：支持 Mod 和自定义目标值 (用于不同进制)
     */
    public static String solve(List<Fraction> nums, Integer modulus, int targetValue) {
        if (modulus == null) {
            return solveRecFromFractions(new ArrayList<>(nums), targetValue);
        } else {
            return solveRecMod(new ArrayList<>(nums), modulus, targetValue);
        }
    }

    /**
     * 所有解入口
     */
    public static List<String> solveAll(List<Fraction> nums, Integer modulus, int targetValue) {
        Set<String> resultSet = new HashSet<>();
        if (modulus == null) {
            List<Expr> list = new ArrayList<>();
            for(Fraction f : nums) list.add(new Expr(f, f.toString()));
            solveExprAll(list, resultSet, targetValue);
        } else {
            List<Expr> list = new ArrayList<>();
            for(Fraction f : nums) {
                long val = ((long)getDoubleValue(f)) % modulus;
                if (val < 0) val += modulus;
                String s = Long.toString(val, f.getRadix()).toUpperCase();
                list.add(new Expr(new Fraction(val, 0, 1, f.getRadix()), s));
            }
            solveExprAllMod(list, resultSet, modulus, targetValue);
        }
        return new ArrayList<>(resultSet);
    }

    static class Expr {
        Fraction val;
        String str;
        Expr(Fraction v, String s) { val=v; str=s; }
    }

    private static String solveRecFromFractions(List<Fraction> nums, int targetValue) {
        List<Expr> list = new ArrayList<>();
        for(Fraction f : nums) list.add(new Expr(f, f.toString()));
        return solveRec(list, targetValue);
    }

    // 标准递归 (单解) - 核心修复：手动透传 radix
    private static String solveRec(List<Expr> list, int targetValue) {
        if (list.size() == 1) {
            Fraction f = list.get(0).val;
            if (f.isValue(targetValue)) {
                String res = list.get(0).str;
                // [修复点] 这里判断 radix 以追加 base 后缀
                if (f.getRadix() != 10) res += " base " + f.getRadix();
                return res;
            }
            return null;
        }

        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i == j) continue;
                Expr a = list.get(i), b = list.get(j);
                List<Expr> next = new ArrayList<>();
                for (int k = 0; k < list.size(); k++) if (k!=i && k!=j) next.add(list.get(k));

                int r = a.val.getRadix(); // 获取当前操作数的进制

                // Add
                Fraction res = a.val.add(b.val);
                next.add(new Expr(new Fraction(res.getRe(), res.getIm(), res.getDe(), r), "("+a.str+" + "+b.str+")"));
                String sol = solveRec(next, targetValue); if(sol!=null) return sol; next.remove(next.size()-1);

                // Sub
                res = a.val.sub(b.val);
                next.add(new Expr(new Fraction(res.getRe(), res.getIm(), res.getDe(), r), "("+a.str+" - "+b.str+")"));
                sol = solveRec(next, targetValue); if(sol!=null) return sol; next.remove(next.size()-1);

                // Mul
                res = a.val.multiply(b.val);
                next.add(new Expr(new Fraction(res.getRe(), res.getIm(), res.getDe(), r), "("+a.str+" * "+b.str+")"));
                sol = solveRec(next, targetValue); if(sol!=null) return sol; next.remove(next.size()-1);

                // Div
                if (!b.val.isValue(0)) {
                    res = a.val.divide(b.val);
                    next.add(new Expr(new Fraction(res.getRe(), res.getIm(), res.getDe(), r), "("+a.str+" / "+b.str+")"));
                    sol = solveRec(next, targetValue); if(sol!=null) return sol; next.remove(next.size()-1);
                }
            }
        }
        return null;
    }

    // 标准递归 (所有解) - 核心修复：手动透传 radix
    private static void solveExprAll(List<Expr> list, Set<String> results, int targetValue) {
        if (list.size() == 1) {
            Fraction f = list.get(0).val;
            if (f.isValue(targetValue)) {
                String res = list.get(0).str;
                if (f.getRadix() != 10) res += " base " + f.getRadix();
                results.add(res);
            }
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i == j) continue;
                Expr a = list.get(i), b = list.get(j);
                List<Expr> next = new ArrayList<>();
                for (int k = 0; k < list.size(); k++) if (k!=i && k!=j) next.add(list.get(k));

                int r = a.val.getRadix();

                Fraction res = a.val.add(b.val);
                next.add(new Expr(new Fraction(res.getRe(), res.getIm(), res.getDe(), r), "("+a.str+" + "+b.str+")"));
                solveExprAll(next, results, targetValue); next.remove(next.size()-1);

                res = a.val.sub(b.val);
                next.add(new Expr(new Fraction(res.getRe(), res.getIm(), res.getDe(), r), "("+a.str+" - "+b.str+")"));
                solveExprAll(next, results, targetValue); next.remove(next.size()-1);

                res = a.val.multiply(b.val);
                next.add(new Expr(new Fraction(res.getRe(), res.getIm(), res.getDe(), r), "("+a.str+" * "+b.str+")"));
                solveExprAll(next, results, targetValue); next.remove(next.size()-1);

                if (!b.val.isValue(0)) {
                    try {
                        res = a.val.divide(b.val);
                        next.add(new Expr(new Fraction(res.getRe(), res.getIm(), res.getDe(), r), "("+a.str+" / "+b.str+")"));
                        solveExprAll(next, results, targetValue); next.remove(next.size()-1);
                    } catch (Exception e) {}
                }
            }
        }
    }

    private static String solveRecMod(List<Fraction> nums, int mod, int targetValue) {
        List<Expr> list = new ArrayList<>();
        for(Fraction f : nums) {
            long val = ((long)getDoubleValue(f)) % mod;
            if (val < 0) val += mod;
            String s = Long.toString(val, f.getRadix()).toUpperCase();
            list.add(new Expr(new Fraction(val, 0, 1, f.getRadix()), s));
        }
        return solveExprMod(list, mod, targetValue);
    }

    private static String solveExprMod(List<Expr> list, int mod, int targetValue) {
        if (list.size() == 1) {
            Fraction f = list.get(0).val;
            long val = ((long)getDoubleValue(f)) % mod;
            if (val < 0) val += mod;
            if (val == targetValue) {
                String suffix = " mod " + mod;
                if (f.getRadix() != 10) suffix = " base " + f.getRadix() + suffix;
                return list.get(0).str + suffix;
            }
            return null;
        }

        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i == j) continue;
                Expr a = list.get(i), b = list.get(j);
                List<Expr> next = new ArrayList<>();
                for (int k = 0; k < list.size(); k++) if (k!=i && k!=j) next.add(list.get(k));
                long va = (long)getDoubleValue(a.val), vb = (long)getDoubleValue(b.val);

                int r = a.val.getRadix();

                long res = (va + vb) % mod;
                next.add(new Expr(new Fraction(res, 0, 1, r), "("+a.str+" + "+b.str+")"));
                String sol = solveExprMod(next, mod, targetValue); if(sol!=null) return sol; next.remove(next.size()-1);

                res = (va - vb) % mod; if(res < 0) res += mod;
                next.add(new Expr(new Fraction(res, 0, 1, r), "("+a.str+" - "+b.str+")"));
                sol = solveExprMod(next, mod, targetValue); if(sol!=null) return sol; next.remove(next.size()-1);

                res = (va * vb) % mod;
                next.add(new Expr(new Fraction(res, 0, 1, r), "("+a.str+" * "+b.str+")"));
                sol = solveExprMod(next, mod, targetValue); if(sol!=null) return sol; next.remove(next.size()-1);

                if (vb != 0) {
                    long invB = modInverse(vb, mod);
                    if (invB != -1) {
                        res = (va * invB) % mod;
                        next.add(new Expr(new Fraction(res, 0, 1, r), "("+a.str+" / "+b.str+")"));
                        sol = solveExprMod(next, mod, targetValue); if(sol!=null) return sol; next.remove(next.size()-1);
                    }
                }
            }
        }
        return null;
    }

    private static void solveExprAllMod(List<Expr> list, Set<String> results, int mod, int targetValue) {
        if (list.size() == 1) {
            Fraction f = list.get(0).val;
            long val = ((long)getDoubleValue(f)) % mod;
            if (val < 0) val += mod;
            if (val == targetValue) {
                String suffix = " mod " + mod;
                if (f.getRadix() != 10) suffix = " base " + f.getRadix() + suffix;
                results.add(list.get(0).str + suffix);
            }
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i == j) continue;
                Expr a = list.get(i), b = list.get(j);
                List<Expr> next = new ArrayList<>();
                for (int k = 0; k < list.size(); k++) if (k!=i && k!=j) next.add(list.get(k));
                long va = (long)getDoubleValue(a.val), vb = (long)getDoubleValue(b.val);

                int r = a.val.getRadix();

                long resAdd = (va + vb) % mod;
                next.add(new Expr(new Fraction(resAdd, 0, 1, r), "("+a.str+" + "+b.str+")"));
                solveExprAllMod(next, results, mod, targetValue); next.remove(next.size()-1);

                long resSub = (va - vb) % mod; if (resSub < 0) resSub += mod;
                next.add(new Expr(new Fraction(resSub, 0, 1, r), "("+a.str+" - "+b.str+")"));
                solveExprAllMod(next, results, mod, targetValue); next.remove(next.size()-1);

                long resMul = (va * vb) % mod;
                next.add(new Expr(new Fraction(resMul, 0, 1, r), "("+a.str+" * "+b.str+")"));
                solveExprAllMod(next, results, mod, targetValue); next.remove(next.size()-1);

                if (vb != 0) {
                    long invB = modInverse(vb, mod);
                    if (invB != -1) {
                        long resDiv = (va * invB) % mod;
                        next.add(new Expr(new Fraction(resDiv, 0, 1, r), "("+a.str+" / "+b.str+")"));
                        solveExprAllMod(next, results, mod, targetValue); next.remove(next.size()-1);
                    }
                }
            }
        }
    }

    private static double getDoubleValue(Fraction f) {
        String s = f.toString(10);
        try {
            if (s.contains("/")) {
                String[] p = s.split("/");
                return Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
            }
            return Double.parseDouble(s);
        } catch (Exception e) { return 0; }
    }

    private static long modInverse(long a, long m) {
        a = a % m; if (a < 0) a += m;
        long m0 = m, y = 0, x = 1;
        if (m == 1) return 0;
        while (a > 1) {
            if (m == 0) return -1;
            long q = a / m, t = m;
            m = a % m; a = t; t = y;
            y = x - q * y; x = t;
        }
        return (a == 1) ? (x < 0 ? x + m0 : x) : -1;
    }
}
