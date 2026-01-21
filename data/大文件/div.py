import os

# 优先级定义：* / 高于 + -
PRECEDENCE = {
    '+': 1,
    '-': 1,
    '*': 2,
    '/': 2  # 仅针对带空格的 " / "
}

class Node:
    def __init__(self, op, left, right):
        self.op = op
        self.left = left
        self.right = right

def find_main_op(s):
    """
    寻找表达式中的主运算符（优先级最低且最靠右的）。
    严格识别被空格包围的运算符，如 " + ", " - ", " * ", " / "。
    """
    count = 0
    best_op_idx = -1
    min_prec = 3
    
    # 从右往左扫描以满足左结合律
    for i in range(len(s) - 1, -1, -1):
        if s[i] == ')':
            count += 1
        elif s[i] == '(':
            count -= 1
        elif count == 0:
            # 只有当符号前后都有空格时，才识别为运算符，从而保护 5/9 这种分数
            if i >= 1 and i < len(s) - 1:
                char = s[i]
                if char in PRECEDENCE and s[i-1] == ' ' and s[i+1] == ' ':
                    prec = PRECEDENCE[char]
                    # 优先级更低或相等（右侧优先）作为主运算符
                    if prec < min_prec:
                        min_prec = prec
                        best_op_idx = i
                    elif prec == min_prec and best_op_idx == -1:
                        best_op_idx = i
                        
    return best_op_idx

def strip_outer_parens(s):
    """健壮地移除外层多余括号，如 ((a+b)) -> a+b """
    s = s.strip()
    while s.startswith('(') and s.endswith(')'):
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
    return s

def parse(s):
    """解析字符串为 AST"""
    s = strip_outer_parens(s)
    idx = find_main_op(s)
    
    if idx == -1:
        return s # 原子节点（数字或 5/9 这种分数）
    
    op = s[idx]
    # 使用切片分割，不再使用硬编码位移，防止吞字
    left_part = s[:idx].strip()
    right_part = s[idx+1:].strip()
    
    return Node(op, parse(left_part), parse(right_part))

def to_str(node, parent_op=None, is_right=False):
    """将 AST 还原为最简字符串"""
    if isinstance(node, str):
        return node
    
    op = node.op
    left_str = to_str(node.left, op, False)
    right_str = to_str(node.right, op, True)
    
    # 核心还原逻辑
    current_expr = f"{left_str} {op} {right_str}"
    
    need_parens = False
    if parent_op:
        p_prec = PRECEDENCE[parent_op]
        c_prec = PRECEDENCE[op]
        
        if c_prec < p_prec:
            # 子运算优先级低，需括号：a * (b + c)
            need_parens = True
        elif c_prec == p_prec:
            # 优先级相等时，减法和除法的右侧需括号：a - (b + c), a / (b * c)
            if is_right and parent_op in ['-', '/']:
                need_parens = True
            
    return f"({current_expr})" if need_parens else current_expr

def process_line(line):
    if " -> " not in line:
        return line
    
    try:
        parts = line.split(" -> ", 1)
        prefix = parts[0]
        expr = parts[1].strip()
        
        ast = parse(expr)
        simplified = to_str(ast)
        return f"{prefix} -> {simplified}\n"
    except Exception:
        # 如果解析出错，保留原样，不破坏数据
        return line

def run_recursive():
    processed = 0
    for root, dirs, files in os.walk('.'):
        for file in files:
            if file.endswith('.txt'):
                path = os.path.join(root, file)
                print(f"正在处理: {path}")
                try:
                    with open(path, 'r', encoding='utf-8') as f:
                        lines = f.readlines()
                    
                    new_lines = [process_line(l) for l in lines]
                    
                    with open(path, 'w', encoding='utf-8') as f:
                        f.writelines(new_lines)
                    processed += 1
                except Exception as e:
                    print(f"错误 {path}: {e}")
                    
    print(f"\n成功化简了 {processed} 个文件。")

if __name__ == "__main__":
    run_recursive()
