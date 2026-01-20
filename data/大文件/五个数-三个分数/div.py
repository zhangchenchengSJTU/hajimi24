import os

def find_balanced_paren_range(s, start_lparen):
    """返回从 start_lparen 开始的匹配右括号的索引"""
    count = 0
    for i in range(start_lparen, len(s)):
        if s[i] == '(':
            count += 1
        elif s[i] == ')':
            count -= 1
            if count == 0:
                return i
    return -1

def find_top_level_div(s):
    """在字符串中寻找不在括号内的 ' / ' 运算符位置"""
    count = 0
    for i in range(len(s) - 2):
        if s[i] == '(':
            count += 1
        elif s[i] == ')':
            count -= 1
        elif count == 0:
            # 检查是否为 ' / '
            if s[i:i+3] == " / ":
                return i
    return -1

def get_left_operand(s, div_start_idx):
    """
    从除号 ' / ' 向左寻找 A。
    如果左边紧邻 ')', 找匹配的 '('
    否则，找空格之前的 token
    """
    prefix = s[:div_start_idx].rstrip()
    if prefix.endswith(')'):
        count = 0
        for i in range(len(prefix)-1, -1, -1):
            if prefix[i] == ')': count += 1
            elif prefix[i] == '(': count -= 1
            if count == 0:
                return prefix[i:], i
    else:
        # 寻找空格作为边界
        space_idx = prefix.rfind(' ')
        if space_idx == -1: # 行首
            return prefix, 0
        else:
            return prefix[space_idx+1:], space_idx + 1
    return None, -1

def transform_logic(line):
    """
    处理 A / (B / C) -> (A * C) / B
    严格识别 " / "
    """
    # 寻找模式 " / ("
    # 注意：我们必须从左往右反复扫描，因为嵌套可能存在
    i = 0
    while i < len(line):
        idx = line.find(" / (", i)
        if idx == -1:
            break
        
        # 除号起始位置
        div_start = idx
        # 左括号位置
        lparen_idx = idx + 3
        # 寻找对应的右括号
        rparen_idx = find_balanced_paren_range(line, lparen_idx)
        
        if rparen_idx == -1:
            i = idx + 1
            continue
        
        # 提取括号内部内容 (B / C)
        inner = line[lparen_idx+1 : rparen_idx]
        # 在内部寻找顶层的 " / "
        inner_div_pos = find_top_level_div(inner)
        
        if inner_div_pos != -1:
            # 确定 A, B, C
            A, A_start = get_left_operand(line, div_start)
            B = inner[:inner_div_pos].strip()
            C = inner[inner_div_pos+3:].strip()
            
            if A is not None:
                # 构造新片段 (A * C) / B
                # 遵循您的空格规范：运算符左右有空格
                new_segment = f"({A} * {C}) / {B}"
                
                # 拼接回原行
                line = line[:A_start] + new_segment + line[rparen_idx+1:]
                # 转换后，从 A_start 开始继续寻找（处理可能的嵌套）
                i = A_start
                continue
        
        i = idx + 1
    return line

def main():
    files = [f for f in os.listdir('.') if f.endswith('.txt')]
    for filename in files:
        print(f"Processing {filename}...")
        with open(filename, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        output = []
        for line in lines:
            output.append(transform_logic(line))
            
        with open(filename, 'w', encoding='utf-8') as f:
            f.writelines(output)

if __name__ == "__main__":
    main()
