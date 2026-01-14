package com.example.hajimi24;

import java.util.List;

public class Problem {
    public List<Fraction> numbers;
    public String solution;
    // 新增字段：存储原始题目字符串
    public String line;

    // 更新构造函数：接收 line 参数
    public Problem(List<Fraction> n, String s, String l) {
        this.numbers = n;
        this.solution = s;
        this.line = l;
    }
}