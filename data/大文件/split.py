import os
import math

def split_file_by_line_count(input_filename, m):
    """
    input_filename: 输入文件名
    m: 每个文件的目标行数 (line = m)
    """
    if not os.path.exists(input_filename):
        print(f"错误: 找不到文件 '{input_filename}'")
        return

    # 1. 统计总行数 n
    print(f"正在读取 '{input_filename}' 的总行数...")
    n = 0
    with open(input_filename, 'r', encoding='utf-8') as f:
        for _ in f:
            n += 1
    
    if n == 0:
        print("错误: 文件为空")
        return

    # 2. 计算份数 k = 上取整(n/m)
    k = math.ceil(n / m)
    
    # 3. 为了“尽可能平均”，重新计算每份的精确行数
    # 这样可以避免最后一个文件特别小的问题
    actual_lines_per_file = math.ceil(n / k)
    
    print(f"总行数(n): {n}")
    print(f"设定行数(m): {m}")
    print(f"拆分份数(k): {k}")
    print(f"实际每份平均行数: {actual_lines_per_file}")

    # 4. 开始拆分
    base_name = input_filename.replace('.txt', '')
    
    with open(input_filename, 'r', encoding='utf-8') as f:
        for i in range(1, k + 1):
            output_filename = f"{base_name}{i}.txt"
            
            current_written = 0
            with open(output_filename, 'w', encoding='utf-8') as out_f:
                for _ in range(actual_lines_per_file):
                    line_content = f.readline()
                    if not line_content:
                        break
                    out_f.write(line_content)
                    current_written += 1
            
            print(f"已生成: {output_filename} ({current_written} 行)")
            
            if not line_content:
                break

    print("\n拆分完成！")

if __name__ == "__main__":
    # --- 你可以在这里设置变量 ---
    target_line_count = 20000  # 这里就是你令 line = m 的值
    file_to_split = '五个数-四个分数-大文件.txt'
    
    split_file_by_line_count(file_to_split, target_line_count)
