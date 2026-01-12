package com.example.hajimi24;

import java.util.ArrayList;
import java.util.List;

public class Solver {
    // 题目数据结构
    public static class Problem {
        List<Fraction> numbers;
        String solution;
        public Problem(List<Fraction> n, String s) { this.numbers = n; this.solution = s; }
    }

    // 求解入口
    public static String solve(List<Fraction> nums) {
        return solveRec(new ArrayList<>(nums), new ArrayList<>());
    }

    // 内部表达式类
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

    private static String solveExpr(List<Expr> list) {
        if (list.size() == 1) return list.get(0).val.isValue(24) ? list.get(0).str : null;

        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i == j) continue;
                Expr a = list.get(i), b = list.get(j);
                List<Expr> next = new ArrayList<>();
                for (int k = 0; k < list.size(); k++) if (k!=i && k!=j) next.add(list.get(k));

                // 加
                next.add(new Expr(a.val.add(b.val), "("+a.str+"+"+b.str+")"));
                String r = solveExpr(next); if(r!=null) return r; next.remove(next.size()-1);

                // 减
                next.add(new Expr(a.val.sub(b.val), "("+a.str+"-"+b.str+")"));
                r = solveExpr(next); if(r!=null) return r; next.remove(next.size()-1);

                // 乘
                next.add(new Expr(a.val.multiply(b.val), "("+a.str+"*"+b.str+")"));
                r = solveExpr(next); if(r!=null) return r; next.remove(next.size()-1);

                // 除
                if (b.val.num != 0) {
                    next.add(new Expr(a.val.divide(b.val), "("+a.str+"/"+b.str+")"));
                    r = solveExpr(next); if(r!=null) return r; next.remove(next.size()-1);
                }
            }
        }
        return null;
    }
}
