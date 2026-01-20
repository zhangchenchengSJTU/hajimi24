import os

def split_large_file(input_filename, num_chunks=10):
    # 检查文件是否存在
    if not os.path.exists(input_filename):
        print(f"错误: 找不到文件 '{input_filename}'")
        return

    # 1. 统计总行数
    print(f"正在读取 '{input_filename}' 的总行数...")
    with open(input_filename, 'r', encoding='utf-8') as f:
        total_lines = sum(1 for line in f)
    
    if total_lines == 0:
        print("错误: 文件为空")
        return

    # 2. 计算每个小文件应有的行数 (向上取整)
    lines_per_file = (total_lines + num_chunks - 1) // num_chunks
    print(f"总行数: {total_lines}, 预计每个文件行数: {lines_per_file}")

    # 3. 开始拆分
    base_name = input_filename.replace('.txt', '')
    
    with open(input_filename, 'r', encoding='utf-8') as f:
        for i in range(1, num_chunks + 1):
            output_filename = f"{base_name}{i}.txt"
            
            # 写入当前块的行
            current_written = 0
            with open(output_filename, 'w', encoding='utf-8') as out_f:
                for _ in range(lines_per_file):
                    line = f.readline()
                    if not line:
                        break
                    out_f.write(line)
                    current_written += 1
            
            print(f"已生成: {output_filename} (包含 {current_written} 行)")
            
            # 如果已经读完文件，提前结束
            if not line:
                break

    print("\n拆分完成！")

if __name__ == "__main__":
    # 执行拆分
    split_large_file('四个数-高斯整数-大文件.txt', 10)
