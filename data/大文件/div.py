import os
import re

# 优先级定义
PRECEDENCE = {
    '+': 1,
    '-': 1,
    '*': 2,
    '/': 2  # 注意：这里的 / 是带空格的除法运算符 " / "
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
    
    # 从右往左扫描，符合左结合律
    for i in range(len(s) - 1, -1, -1):
        if s[i] == ')':
            count += 1
        elif s[i] == '(':
            count -= 1
        elif count == 0:
            # 严格识别带空格的运算符
            current_op = None
            idx = -1
            
            if i >= 1 and i < len(s)-1:
                if s[i-1:i+2] == " / ":
                    current_op, idx = "/", i
                elif s[i-1:i+2] == " * ":
                    current_op, idx = "*", i
                elif s[i-1:i+2] == " + ":
                    current_op, idx = "+", i
                elif s[i-1:i+2] == " - ":
                    current_op, idx = "-", i
            
            if current_op:
                prec = PRECEDENCE[current_op]
                # 寻找优先级最低的（主运算符）
                if prec < min_prec:
                    min_prec = prec
                    best_op_idx = idx
                elif prec == min_prec and best_op_idx == -1:
                    best_op_idx = idx
                    
    return best_op_idx

def parse(s):
    """将字符串解析为树"""
    s = s.strip()
    
    # 剥离外层冗余括号
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
            
    idx = find_main_op(s)
    if idx == -1:
        return s # 原子节点
    
    op = s[idx]
    left = parse(s[:idx-1])
    right = parse(s[idx+2:])
    return Node(op, left, right)

def to_str(node, parent_op=None, is_right=False):
    """根据优先级规则转回字符串"""
    if isinstance(node, str):
        return node
    
    op = node.op
    left_str = to_str(node.left, op, False)
    right_str = to_str(node.right, op, True)
    
    current_expr = f"{left_str} {op} {right_str}"
    
    need_parens = False
    if parent_op:
        p_prec = PRECEDENCE[parent_op]
        c_prec = PRECEDENCE[op]
        
        if c_prec < p_prec:
            need_parens = True
        elif c_prec == p_prec:
            # 只有当处于 减法/除法 的右侧时，同级运算才需要括号
            if is_right and parent_op in ['-', '/']:
                need_parens = True
            
    return f"({current_expr})" if need_parens else current_expr

def process_line(line):
    if " -> " not in line:
        return line
    
    # 拆分前缀和表达式
    parts = line.split(" -> ", 1)
    prefix = parts[0]
    expr = parts[1].strip()
    
    try:
        ast = parse(expr)
        simplified = to_str(ast)
        return f"{prefix} -> {simplified}\n"
    except Exception:
        return line

def run_simplification():
    # 使用 os.walk 遍历当前目录及所有子目录
    root_dir = '.'
    processed_files = 0
    
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.txt'):
                file_path = os.path.join(root, file)
                print(f"正在处理: {file_path}")
                
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        lines = f.readlines()
                    
                    new_lines = [process_line(l) for l in lines]
                    
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.writelines(new_lines)
                    processed_files += 1
                except Exception as e:
                    print(f"无法读取文件 {file_path}: {e}")

    print(f"\n处理完成！共优化了 {processed_files} 个文件。")

if __name__ == "__main__":
    run_simplification()
