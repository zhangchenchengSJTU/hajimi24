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

    // 标准求解入口 (单解)
    public static String solve(List<Fraction> nums) {
        return solve(nums, null);
    }

    // 支持 Mod 的求解入口 (单解)
    public static String solve(List<Fraction> nums, Integer modulus) {
        if (modulus == null) {
            return solveRecFromFractions(new ArrayList<>(nums));
        } else {
            return solveRecMod(new ArrayList<>(nums), modulus);
        }
    }

    // 标准求解入口 (所有解)
    public static List<String> solveAll(List<Fraction> nums) {
        return solveAll(nums, null);
    }

    // --- 新增: 支持 Mod 的求解入口 (所有解) ---
    public static List<String> solveAll(List<Fraction> nums, Integer modulus) {
        if (modulus == null) {
            // 没有模数，走旧逻辑
            Set<String> resultSet = new HashSet<>();
            List<Expr> list = new ArrayList<>();
            for(Fraction f : nums) list.add(new Expr(f, f.toString()));
            solveExprAll(list, resultSet);
            return new ArrayList<>(resultSet);
        } else {
            // 有模数，走新逻辑
            Set<String> resultSet = new HashSet<>();
            List<Expr> list = new ArrayList<>();
            // 预处理：将分数转换为整数 (Mod N)
            for(Fraction f : nums) {
                long val = ((long)getDoubleValue(f)) % modulus;
                if (val < 0) val += modulus;
                list.add(new Expr(new Fraction(val, 1), f.toString()));
            }
            solveExprAllMod(list, resultSet, modulus);
            return new ArrayList<>(resultSet);
        }
    }

    static class Expr {
        Fraction val;
        String str;
        Expr(Fraction v, String s) { val=v; str=s; }
    }

    private static String solveRecFromFractions(List<Fraction> nums) {
        List<Expr> list = new ArrayList<>();
        for(Fraction f : nums) list.add(new Expr(f, f.toString()));
        return solveRec(list);
    }

    // 标准递归 (单解)
    private static String solveRec(List<Expr> list) {
        if (list.size() == 1) return list.get(0).val.isValue(24) ? list.get(0).str : null;

        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i == j) continue;
                Expr a = list.get(i), b = list.get(j);
                List<Expr> next = new ArrayList<>();
                for (int k = 0; k < list.size(); k++) if (k!=i && k!=j) next.add(list.get(k));

                next.add(new Expr(a.val.add(b.val), "("+a.str+" + "+b.str+")"));
                String r = solveRec(next); if(r!=null) return r; next.remove(next.size()-1);

                next.add(new Expr(a.val.sub(b.val), "("+a.str+" - "+b.str+")"));
                r = solveRec(next); if(r!=null) return r; next.remove(next.size()-1);

                next.add(new Expr(a.val.multiply(b.val), "("+a.str+" * "+b.str+")"));
                r = solveRec(next); if(r!=null) return r; next.remove(next.size()-1);

                if (!b.val.isValue(0)) {
                    next.add(new Expr(a.val.divide(b.val), "("+a.str+" / "+b.str+")"));
                    r = solveRec(next); if(r!=null) return r; next.remove(next.size()-1);
                }
            }
        }
        return null;
    }

    // 标准递归 (所有解)
    private static void solveExprAll(List<Expr> list, Set<String> results) {
        if (list.size() == 1) {
            if (list.get(0).val.isValue(24)) {
                results.add(list.get(0).str);
            }
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i == j) continue;
                Expr a = list.get(i), b = list.get(j);
                List<Expr> next = new ArrayList<>();
                for (int k = 0; k < list.size(); k++) if (k!=i && k!=j) next.add(list.get(k));

                next.add(new Expr(a.val.add(b.val), "("+a.str+" + "+b.str+")"));
                solveExprAll(next, results);
                next.remove(next.size()-1);

                next.add(new Expr(a.val.sub(b.val), "("+a.str+" - "+b.str+")"));
                solveExprAll(next, results);
                next.remove(next.size()-1);

                next.add(new Expr(a.val.multiply(b.val), "("+a.str+" * "+b.str+")"));
                solveExprAll(next, results);
                next.remove(next.size()-1);

                try {
                    if (!b.val.isValue(0)) {
                        next.add(new Expr(a.val.divide(b.val), "("+a.str+" / "+b.str+")"));
                        solveExprAll(next, results);
                        next.remove(next.size()-1);
                    }
                } catch (Exception e) {}
            }
        }
    }

    // Mod 递归 (单解)
    private static String solveRecMod(List<Fraction> nums, int mod) {
        List<Expr> list = new ArrayList<>();
        for(Fraction f : nums) {
            long val = ((long)getDoubleValue(f)) % mod;
            if (val < 0) val += mod;
            list.add(new Expr(new Fraction(val, 1), f.toString()));
        }
        return solveExprMod(list, mod);
    }

    private static String solveExprMod(List<Expr> list, int mod) {
        if (list.size() == 1) {
            long val = ((long)getDoubleValue(list.get(0).val)) % mod;
            if (val < 0) val += mod;
            return (val == 24) ? (list.get(0).str + " mod " + mod) : null;
        }
        // ... (省略重复代码，逻辑与下面 solveExprAllMod 结构相同，只是找到就返回)
        // 为节省篇幅，这里复用 solveExprAllMod 的逻辑思想
        // 实际运行时请确保这里是完整的递归逻辑 (如上一版 Solver.java 所示)

        // 为了确保代码完整性，这里快速写一下:
        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i == j) continue;
                Expr a = list.get(i), b = list.get(j);
                List<Expr> next = new ArrayList<>();
                for (int k = 0; k < list.size(); k++) if (k!=i && k!=j) next.add(list.get(k));
                long va = (long)getDoubleValue(a.val), vb = (long)getDoubleValue(b.val);

                // Add
                long res = (va + vb) % mod;
                next.add(new Expr(new Fraction(res, 1), "("+a.str+" + "+b.str+")"));
                String r = solveExprMod(next, mod); if(r!=null) return r; next.remove(next.size()-1);

                // Sub
                res = (va - vb) % mod; if(res < 0) res += mod;
                next.add(new Expr(new Fraction(res, 1), "("+a.str+" - "+b.str+")"));
                r = solveExprMod(next, mod); if(r!=null) return r; next.remove(next.size()-1);

                // Mul
                res = (va * vb) % mod;
                next.add(new Expr(new Fraction(res, 1), "("+a.str+" * "+b.str+")"));
                r = solveExprMod(next, mod); if(r!=null) return r; next.remove(next.size()-1);

                // Div
                if (vb != 0) {
                    long invB = modInverse(vb, mod);
                    if (invB != -1) {
                        res = (va * invB) % mod;
                        next.add(new Expr(new Fraction(res, 1), "("+a.str+" / "+b.str+")"));
                        r = solveExprMod(next, mod); if(r!=null) return r; next.remove(next.size()-1);
                    }
                }
            }
        }
        return null;
    }

    // --- 新增: Mod 递归 (所有解) ---
    private static void solveExprAllMod(List<Expr> list, Set<String> results, int mod) {
        if (list.size() == 1) {
            long val = ((long)getDoubleValue(list.get(0).val)) % mod;
            if (val < 0) val += mod;
            if (val == 24) {
                results.add(list.get(0).str + " mod " + mod);
            }
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i == j) continue;
                Expr a = list.get(i), b = list.get(j);
                List<Expr> next = new ArrayList<>();
                for (int k = 0; k < list.size(); k++) if (k!=i && k!=j) next.add(list.get(k));

                long va = (long)getDoubleValue(a.val);
                long vb = (long)getDoubleValue(b.val);

                // Mod Add
                long resAdd = (va + vb) % mod;
                next.add(new Expr(new Fraction(resAdd, 1), "("+a.str+" + "+b.str+")"));
                solveExprAllMod(next, results, mod);
                next.remove(next.size()-1);

                // Mod Sub
                long resSub = (va - vb) % mod;
                if (resSub < 0) resSub += mod;
                next.add(new Expr(new Fraction(resSub, 1), "("+a.str+" - "+b.str+")"));
                solveExprAllMod(next, results, mod);
                next.remove(next.size()-1);

                // Mod Mul
                long resMul = (va * vb) % mod;
                next.add(new Expr(new Fraction(resMul, 1), "("+a.str+" * "+b.str+")"));
                solveExprAllMod(next, results, mod);
                next.remove(next.size()-1);

                // Mod Div
                if (vb != 0) {
                    long invB = modInverse(vb, mod);
                    if (invB != -1) {
                        long resDiv = (va * invB) % mod;
                        next.add(new Expr(new Fraction(resDiv, 1), "("+a.str+" / "+b.str+")"));
                        solveExprAllMod(next, results, mod);
                        next.remove(next.size()-1);
                    }
                }
            }
        }
    }

    private static double getDoubleValue(Fraction f) {
        String s = f.toString();
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
            long q = a / m;
            long t = m; m = a % m; a = t;
            t = y; y = x - q * y; x = t;
        }
        if (x < 0) x += m0;
        return (a == 1) ? x : -1;
    }
}
