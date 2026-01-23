import os
import re
import string
from collections import Counter

def run_java_safe_bpe():
    target_files = [f"wdb{i}.txt" for i in range(5, 13)]
    file_contents = {}
    original_chars = set()

    # 1. 预处理
    print("正在进行 Java 兼容性预处理...")
    for filename in target_files:
        if os.path.exists(filename):
            # 使用 latin-1 确保单字节映射
            with open(filename, 'r', encoding='latin-1') as f:
                content = f.read()
                # 换行转逗号，删除空格
                content = content.replace('\r\n', '\n').replace('\r', '\n').replace('\n', ',')
                content = content.replace(' ', '').replace('\t', '')
                file_contents[filename] = content
                original_chars.update(set(content))
        else:
            print(f"跳过: {filename}")

    if not file_contents: return

    # 2. 构建 Java 安全的占位符池
    # 避开数字、基本运算符、逗号
    # 避开 Java 中极其麻烦的转义字符: \ (92), " (34), ' (39)
    forbidden_for_java = set("0123456789+-*/,")
    troublesome_java = {chr(92), chr(34), chr(39)} 
    
    placeholder_pool = []
    # 使用 ASCII 33-255 范围
    for i in range(33, 256):
        c = chr(i)
        if (c not in original_chars and 
            c not in forbidden_for_java and 
            c not in troublesome_java and 
            i != 127):
            placeholder_pool.append(c)
    
    # 优先级：字母优先
    letters = [c for c in string.ascii_letters if c in placeholder_pool]
    others = [c for c in placeholder_pool if c not in string.ascii_letters]
    final_pool = letters + others

    print(f"可用 Java 安全占位符: {len(final_pool)} 个")

    replacement_dict = {} # 格式: {占位符: 原始串}
    
    # 3. 递归压缩逻辑
    for idx, char_to_assign in enumerate(final_pool):
        bigram_counts = Counter()
        
        # 统计逻辑：不含已分配占位符，且严格按逗号切分
        pool_regex = "".join(re.escape(c) for c in final_pool)
        regex_pattern = f'[^{pool_regex}]+'

        for content in file_contents.values():
            segments = re.findall(regex_pattern, content)
            for seg in segments:
                # 禁用逗号：切分段落
                parts = seg.split(',')
                for p in parts:
                    if len(p) < 2: continue
                    for i in range(len(p) - 1):
                        bigram = p[i:i+2]
                        bigram_counts[bigram] += 1
        
        if not bigram_counts: break
        best_bigram, freq = bigram_counts.most_common(1)[0]
        if freq < 2: break

        replacement_dict[char_to_assign] = best_bigram
        
        # 执行替换
        for filename in file_contents:
            file_contents[filename] = file_contents[filename].replace(best_bigram, char_to_assign)
        
        print(f"[{idx+1}] '{char_to_assign}' 替换 '{best_bigram}' ({freq}次)")

    # 4. 保存压缩文件
    for filename, content in file_contents.items():
        with open(f"java_ready_{filename}", 'w', encoding='latin-1') as f:
            f.write(content)

    # 5. 输出指定的字典格式: a)+,b(1,...
    dict_entries = []
    for k, v in replacement_dict.items():
        dict_entries.append(f"{k}{v}")
    
    final_dict_string = ",".join(dict_entries)
    
    with open("java_dict.txt", 'w', encoding='utf-8') as f:
        f.write(final_dict_string)

    print("\n✅ 任务完成！")
    print(f"压缩文件: java_ready_wdb*.txt")
    print(f"Java 专用字典已保存至: java_dict.txt")
    print(f"字典预览: {final_dict_string[:50]}...")

if __name__ == "__main__":
    run_java_safe_bpe()
