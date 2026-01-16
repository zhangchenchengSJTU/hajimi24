package com.example.hajimi24;

import java.util.List;

public class Problem {
    public List<Fraction> numbers;
    public String solution;
    public String line;
    // 存储模数 (如果题目包含 mod)
    public Integer modulus;
    // 新增：存储进制 (如果题目包含 base，例如 base 8)
    public Integer radix;

    // 基础构造函数
    public Problem(List<Fraction> n, String s, String l) {
        this(n, s, l, null, null);
    }

    // 兼容旧的带模数构造函数
    public Problem(List<Fraction> n, String s, String l, Integer mod) {
        this(n, s, l, mod, null);
    }

    // 新增：全参数构造函数，支持模数和进制
    public Problem(List<Fraction> n, String s, String l, Integer mod, Integer radix) {
        this.numbers = n;
        this.solution = s;
        this.line = l;
        this.modulus = mod;
        this.radix = radix;
    }
}
