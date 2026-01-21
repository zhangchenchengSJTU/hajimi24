import os

def find_balanced_paren_range(s, lparen_idx):
    """从左括号位置开始，寻找对应的右括号索引"""
    count = 0
    for i in range(lparen_idx, len(s)):
        if s[i] == '(':
            count += 1
        elif s[i] == ')':
            count -= 1
            if count == 0:
                return i
    return -1

def find_balanced_paren_backwards(s, rparen_idx):
    """从右括号位置开始，反向寻找对应的左括号索引"""
    count = 0
    for i in range(rparen_idx, -1, -1):
        if s[i] == ')':
            count += 1
        elif s[i] == '(':
            count -= 1
            if count == 0:
                return i
    return -1

def find_top_level_op(s, op_with_spaces):
    """在字符串中寻找不在括号内的特定运算符位置"""
    count = 0
    for i in range(len(s) - len(op_with_spaces) + 1):
        if s[i] == '(':
            count += 1
        elif s[i] == ')':
            count -= 1
        elif count == 0:
            if s[i : i + len(op_with_spaces)] == op_with_spaces:
                return i
    return -1

def get_operands(s, div_idx):
    """获取 ' / ' 运算符两边的完整操作数"""
    # 获取左操作数 A
    prefix = s[:div_idx].rstrip()
    if prefix.endswith(')'):
        l_idx = find_balanced_paren_backwards(prefix, len(prefix)-1)
        A, A_start = prefix[l_idx:], l_idx
    else:
        space_idx = prefix.rfind(' ')
        A, A_start = (prefix, 0) if space_idx == -1 else (prefix[space_idx+1:], space_idx+1)
    
    # 获取右操作数 B
    suffix = s[div_idx+3:].lstrip()
    if suffix.startswith('('):
        r_idx = find_balanced_paren_range(suffix, 0)
        B, B_end = suffix[:r_idx+1], div_idx + 3 + suffix.find('(') + r_idx + 1
    else:
        # 寻找空格或行尾作为边界
        space_idx = suffix.find(' ')
        if space_idx == -1:
            B, B_end = suffix, len(s)
        else:
            B, B_end = suffix[:space_idx], div_idx + 3 + (len(s[div_idx+3:]) - len(suffix)) + space_idx
    
    return A, A_start, B, B_end

def transform_logic(line):
    """
    循环应用两条规则：
    1. A / (B / C) -> (A * C) / B
    2. (A / B) / C -> A / (B * C)
    """
    changed = True
    while changed:
        changed = False
        
        # --- 规则 1: A / (B / C) ---
        idx1 = line.find(" / (")
        if idx1 != -1:
            div_start = idx1
            l_paren = idx1 + 3
            r_paren = find_balanced_paren_range(line, l_paren)
            if r_paren != -1:
                inner = line[l_paren+1 : r_paren]
                inner_div = find_top_level_op(inner, " / ")
                if inner_div != -1:
                    A, A_start, _, _ = get_operands(line, div_start)
                    B = inner[:inner_div].strip()
                    C = inner[inner_div+3:].strip()
                    line = line[:A_start] + f"({A} * {C}) / {B}" + line[r_paren+1:]
                    changed = True
                    continue

        # --- 规则 2: (A / B) / C ---
        idx2 = line.find(") / ")
        if idx2 != -1:
            div_start = idx2 + 2 # 指向 " / " 之前的那个空格
            r_paren = idx2
            l_paren = find_balanced_paren_backwards(line, r_paren)
            if l_paren != -1:
                inner = line[l_paren+1 : r_paren]
                inner_div = find_top_level_op(inner, " / ")
                if inner_div != -1:
                    # 我们需要获取 C 的结束位置 C_end 才能拼接
                    _, _, C, C_end = get_operands(line, div_start)
                    A = inner[:inner_div].strip()
                    B = inner[inner_div+3:].strip()
                    line = line[:l_paren] + f"{A} / ({B} * {C})" + line[C_end:]
                    changed = True
                    continue
                    
    return line

def main():
    # 获取当前目录下所有 txt
    files = [f for f in os.listdir('.') if f.endswith('.txt')]
    if not files:
        print("未发现 .txt 文件")
        return

    for filename in files:
        print(f"处理中: {filename}")
        try:
            with open(filename, 'r', encoding='utf-8') as f:
                lines = f.readlines()
            
            new_lines = [transform_logic(l) for l in lines]
            
            with open(filename, 'w', encoding='utf-8') as f:
                f.writelines(new_lines)
            print(f"成功更新: {filename}")
        except Exception as e:
            print(f"处理 {filename} 时出错: {e}")

if __name__ == "__main__":
    main()
