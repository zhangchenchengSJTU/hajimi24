import os
import re

# 优先级定义
PRECEDENCE = {
    '+': 1,
    '-': 1,
    '*': 2,
    '×': 2,
    '/': 2  # 注意：这里的 / 是带空格的除法运算符
}

class Node:
    def __init__(self, op, left, right):
        self.op = op
        self.left = left
        self.right = right

def find_main_op(s):
    """寻找表达式中的主运算符（优先级最低且最靠右的）"""
    count = 0
    best_op_idx = -1
    min_prec = 3
    
    # 从右往左扫描，以符合左结合律 (a - b - c -> (a-b)-c)
    for i in range(len(s) - 1, -1, -1):
        if s[i] == ')':
            count += 1
        elif s[i] == '(':
            count -= 1
        elif count == 0:
            # 检查是否为运算符
            # 优先处理带空格的运算符 " / ", " * ", " + ", " - "
            part = s[i-1:i+2] if i > 0 and i < len(s)-1 else ""
            
            current_op = None
            idx = -1
            
            if s[i:i+3] == " / ":
                current_op, idx = "/", i+1
            elif s[i:i+3] == " * ":
                current_op, idx = "*", i+1
            elif s[i:i+3] == " + ":
                current_op, idx = "+", i+1
            elif s[i:i+3] == " - ":
                current_op, idx = "-", i+1
            
            if current_op:
                prec = PRECEDENCE[current_op]
                if prec < min_prec:
                    min_prec = prec
                    best_op_idx = idx
                elif prec == min_prec and best_op_idx == -1:
                    best_op_idx = idx
                    
    return best_op_idx

def parse(s):
    """将字符串解析为 AST 树"""
    s = s.strip()
    
    # 移除外层冗余括号
    while s.startswith('(') and s.endswith(')'):
        # 检查是否是匹配的一对
        count = 0
        is_pair = True
        for i in range(len(s) - 1):
            if s[i] == '(': count += 1
            elif s[i] == ')': count -= 1
            if count == 0:
                is_pair = False
                break
        if is_pair:
            s = s[1:-1].strip()
        else:
            break
            
    idx = find_main_op(s)
    if idx == -1:
        return s # 原子节点（数字或分数）
    
    op = s[idx]
    # 注意除法占3位 " / "
    left = parse(s[:idx-1])
    right = parse(s[idx+2:])
    return Node(op, left, right)

def to_str(node, parent_op=None, is_right=False):
    """根据优先级规则将 AST 转回字符串"""
    if isinstance(node, str):
        return node
    
    op = node.op
    left_str = to_str(node.left, op, False)
    right_str = to_str(node.right, op, True)
    
    current_expr = f"{left_str} {op} {right_str}"
    
    # 判断是否需要加括号
    need_parens = False
    if parent_op:
        p_prec = PRECEDENCE[parent_op]
        c_prec = PRECEDENCE[op]
        
        if c_prec < p_prec:
            # 子运算优先级低，必须加括号：a * (b + c)
            need_parens = True
        elif c_prec == p_prec:
            # 优先级相同时，处理非结合情况
            if is_right:
                # 如果是减法或除法的右项，通常需要括号：a - (b + c) 或 a / (b * c)
                if parent_op in ['-', '/']:
                    need_parens = True
            # 左项优先级相同一般不需要：(a + b) + c -> a + b + c
            
    return f"({current_expr})" if need_parens else current_expr

def process_line(line):
    if " -> " not in line:
        return line
    
    prefix, expr = line.split(" -> ", 1)
    try:
        # 解析并简化
        ast = parse(expr.strip())
        simplified_expr = to_str(ast)
        return f"{prefix} -> {simplified_expr}\n"
    except:
        return line

def main():
    files = [f for f in os.listdir('.') if f.endswith('.txt')]
    for filename in files:
        print(f"正在简化括号: {filename}")
        with open(filename, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        new_lines = [process_line(l) for l in lines]
        
        with open(filename, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)

if __name__ == "__main__":
    main()
