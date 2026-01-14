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

    public static String solve(List<Fraction> nums) {
        return solveRec(new ArrayList<>(nums), new ArrayList<>());
    }

    // --- 新增：查找所有解的方法 ---
    public static List<String> solveAll(List<Fraction> nums) {
        Set<String> resultSet = new HashSet<>(); // 使用 Set 去重
        solveRecAll(new ArrayList<>(nums), resultSet);
        return new ArrayList<>(resultSet);
    }
    // ---------------------------

    static class Expr {
        Fraction val;
        String str;
        Expr(Fraction v, String s) { val=v; str=s; }
    }

    private static String solveRec(List<Fraction> nums, List<String> exprs) {
        List<Expr> list = new ArrayList<>();
        for(Fraction f : nums) list.add(new Expr(f, f.toString()));
        return solveExpr(list);
    }
    // --- 新增：递归查找所有解 ---
    private static void solveRecAll(List<Fraction> nums, Set<String> results) {
        List<Expr> list = new ArrayList<>();
        for(Fraction f : nums) list.add(new Expr(f, f.toString()));
        solveExprAll(list, results);
    }
    // --------------------------
    private static String solveExpr(List<Expr> list) {
        if (list.size() == 1) return list.get(0).val.isValue(24) ? list.get(0).str : null;

        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i == j) continue;
                Expr a = list.get(i), b = list.get(j);
                List<Expr> next = new ArrayList<>();
                for (int k = 0; k < list.size(); k++) if (k!=i && k!=j) next.add(list.get(k));

                // 核心：运算符两侧加空格，与 MainActivity 的匹配逻辑对应

                // 加
                next.add(new Expr(a.val.add(b.val), "("+a.str+" + "+b.str+")"));
                String r = solveExpr(next); if(r!=null) return r; next.remove(next.size()-1);

                // 减
                next.add(new Expr(a.val.sub(b.val), "("+a.str+" - "+b.str+")"));
                r = solveExpr(next); if(r!=null) return r; next.remove(next.size()-1);

                // 乘
                next.add(new Expr(a.val.multiply(b.val), "("+a.str+" * "+b.str+")"));
                r = solveExpr(next); if(r!=null) return r; next.remove(next.size()-1);

                // 除
                if (!b.val.isValue(0)) {
                    next.add(new Expr(a.val.divide(b.val), "("+a.str+" / "+b.str+")"));
                    r = solveExpr(next); if(r!=null) return r; next.remove(next.size()-1);
                }
            }
        }
        return null;
    }
    // --- 新增：遍历所有组合的核心逻辑 ---
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

                // 加
                next.add(new Expr(a.val.add(b.val), "("+a.str+" + "+b.str+")"));
                solveExprAll(next, results);
                next.remove(next.size()-1);

                // 减
                next.add(new Expr(a.val.sub(b.val), "("+a.str+" - "+b.str+")"));
                solveExprAll(next, results);
                next.remove(next.size()-1);

                // 乘
                next.add(new Expr(a.val.multiply(b.val), "("+a.str+" * "+b.str+")"));
                solveExprAll(next, results);
                next.remove(next.size()-1);

                // 除
                try {
                    // 注意：这里需要更健壮的零检查，或者捕获异常，因为 Fraction 可能包含复杂的零判断
                    if (!b.val.isValue(0)) {
                        next.add(new Expr(a.val.divide(b.val), "("+a.str+" / "+b.str+")"));
                        solveExprAll(next, results);
                        next.remove(next.size()-1);
                    }
                } catch (Exception e) {
                    // 忽略除零错误
                }
            }
        }
    }
}
