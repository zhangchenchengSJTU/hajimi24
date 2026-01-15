package com.example.hajimi24;

import java.util.List;

public class Problem {
    public List<Fraction> numbers;
    public String solution;
    public String line;
    // 新增：存储模数 (如果题目包含 mod)
    public Integer modulus;

    public Problem(List<Fraction> n, String s, String l) {
        this(n, s, l, null);
    }

    public Problem(List<Fraction> n, String s, String l, Integer mod) {
        this.numbers = n;
        this.solution = s;
        this.line = l;
        this.modulus = mod;
    }
}
