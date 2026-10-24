#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 data/ 下的 24 点文本数据转换成 Wordle 风格的紧凑编码。

流水线（逐文件）:
  1. 解析 '[n] ['a', 'b', ...] -> 表达式' 行，识别文件级 ' mod N' / ' base N'
  2. 紧凑行:  'a,b,...:表达式(保留空格)'   -- 数字显式保留，表达式原样保留
  3. 每文件内排序 + 差分编码:  每行 = chr(33+公共前缀长度) + 剩余部分
  4. 文件内容 = 可选 '#mod N'/'#base N' 头 + 差分行
最后对所有文件内容做一次共享 BPE 字典压缩（纯 ASCII 占位符），
字典写到 <out>/rep.dict，格式为无分隔符的三字符定长块：
<占位符><两字符原文> 连续拼接（每 3 字符一条），例如 G /H -,I (,J *...

设计取舍:
  - 表达式保留原样（含空格），保证旧解析/过滤/显示逻辑零改动
  - BPE 不压缩换行，输出文件保持一行一条题目的行结构，方便 git 追踪
  - 占位符只用可打印 ASCII（33..126），无编码/编码混用问题

用法:
  python tools/convert_24data.py [--in data] [--out data_compact]
"""

import argparse
import os
import re
import sys
import time
from collections import Counter

# 紧凑格式中不允许出现的字符（BPE 占位符也不允许使用）
FORBIDDEN = set("0123456789+-*/,:()#\n\r'\"\\ ")

# 差分前缀长度标记字母表：可打印 ASCII 33..126，排除 '#'（文件头专用字符）。
# k 的范围是 0..92，行首为 '#' 的一定是文件头，不会与数据行混淆。
MARKER_ALPHABET = "".join(chr(i) for i in range(33, 127) if chr(i) != "#")
MAX_PREFIX = len(MARKER_ALPHABET) - 1  # 92


def split_nums(nums_str):
    """'1', '1', '8' -> ['1', '1', '8']"""
    return [t.strip().strip("'") for t in nums_str.split(",") if t.strip()]


def strip_suffix(expr):
    """去掉表达式末尾的 ' mod 37' / ' base 5'，返回 (表达式, kind, value)"""
    m = re.search(r" (mod|base) (\d+)$", expr)
    if m:
        return expr[: m.start()].strip(), m.group(1), m.group(2)
    return expr, None, None


def parse_old_line(s):
    """解析旧格式行，返回 (nums, expr) 或 None"""
    m = re.match(r"^\[\d+\] \[(.*?)\] -> (.*)$", s)
    if not m:
        return None
    return split_nums(m.group(1)), m.group(2).strip()


def lcp(a, b, cap=MAX_PREFIX):
    i = 0
    m = min(len(a), len(b), cap)
    while i < m and a[i] == b[i]:
        i += 1
    return i


def front_code(lines):
    """排序后的紧凑行 -> 差分行列表（chr(33+lcp)+后缀）"""
    lines = sorted(lines)
    enc = []
    prev = ""
    for line in lines:
        k = lcp(line, prev)
        enc.append(MARKER_ALPHABET[k] + line[k:])
        prev = line
    return enc


def decode_front_code(diff_lines):
    """差分行 -> 紧凑行（用于校验）"""
    out = []
    prev = ""
    for line in diff_lines:
        if not line:
            continue
        if line.startswith("#"):
            out.append(line)
            prev = ""
            continue
        k = MARKER_ALPHABET.index(line[0])
        cur = prev[: max(0, min(k, len(prev)))] + line[1:]
        out.append(cur)
        prev = cur
    return out


def build_bpe_dict(streams, max_rounds=None):
    """
    对全部文件内容（差分流拼接）做 BPE。
    返回按轮次顺序的字典 [(placeholder, bigram), ...]。
    占位符只使用可打印 ASCII（33..126），保证 UTF-8/latin-1 兼容。
    """
    content = "".join(streams)
    used = set(content)
    pool = [
        chr(i)
        for i in range(33, 127)
        if chr(i) not in FORBIDDEN and chr(i) not in used
    ]
    pool.sort(key=lambda c: (not c.isalpha(), c))
    if max_rounds is None:
        max_rounds = len(pool)

    pool_re = "".join(re.escape(c) for c in pool)
    seg_re = re.compile(r"[^" + pool_re + r"]+")

    dict_map = []
    t0 = time.time()
    for r in range(max_rounds):
        if not pool:
            break
        counts = Counter()
        for seg in seg_re.findall(content):
            if len(seg) < 2:
                continue
            for i in range(len(seg) - 1):
                bg = seg[i:i + 2]
                # 不压缩换行：保持行结构（一行一条题目），利于版本追踪
                if "\n" in bg or "\r" in bg:
                    continue
                counts[bg] += 1
        if not counts:
            break
        bg, freq = counts.most_common(1)[0]
        if freq < 2:
            break
        ch = pool.pop(0)
        dict_map.append((ch, bg))
        content = content.replace(bg, ch)
        if (r + 1) % 25 == 0:
            print(
                f"    BPE round {r + 1}: {ch!r} <- {bg!r} (freq {freq}), "
                f"content {len(content)/1e6:.2f} MB, {time.time()-t0:.0f}s",
                file=sys.stderr,
            )
    print(f"    BPE done: {len(dict_map)} rounds, {time.time()-t0:.0f}s", file=sys.stderr)
    return dict_map


def apply_bpe(text, dict_map):
    for ch, bg in dict_map:
        text = text.replace(bg, ch)
    return text


def expand_bpe(text, dict_map):
    rev = {ch: bg for ch, bg in dict_map}
    return "".join(rev.get(c, c) for c in text)


def process_file(path):
    """解析单个文件，返回 (header, diff_lines, compact_lines)"""
    with open(path, "rb") as fh:
        text = fh.read().decode("utf-8")
    header = None
    compact = []
    for line in text.split("\n"):
        s = line.rstrip("\r").strip()
        if not s or s.startswith("#"):
            continue
        parsed = parse_old_line(s)
        if parsed is None:
            continue
        nums, expr = parsed
        expr2, kind, val = strip_suffix(expr)
        if kind is not None:
            if header is None:
                header = f"#{kind} {val}"
            elif header != f"#{kind} {val}":
                # 异常：同文件内 mod/base 不一致，后缀保留在行内
                expr2 = expr
        compact.append(",".join(nums) + ":" + expr2)
    return header, front_code(compact), compact


def reconstruct_old_line(compact_line, header):
    """紧凑行 -> 旧格式行（与 parse_old_line 互逆）"""
    nums_s, expr = compact_line.split(":", 1)
    nums = nums_s.split(",")
    if header:
        expr = expr + " " + header[1:].replace(" ", " ", 1)  # '#mod 37' -> ' mod 37'
    return "['" + "', '".join(nums) + "'] -> " + expr


def main():
    ap = argparse.ArgumentParser(description="Convert 24-point data to compact encoding")
    ap.add_argument("--in", dest="in_dir", default="data")
    ap.add_argument("--out", dest="out_dir", default="data_compact")
    ap.add_argument("--max-rounds", type=int, default=None)
    args = ap.parse_args()

    files = []
    for dp, dn, fn in os.walk(args.in_dir):
        for f in sorted(fn):
            if f.endswith(".txt"):
                files.append(os.path.join(dp, f))
    files.sort()
    print(f"files: {len(files)}")

    # ---- 1. 解析 + 紧凑 + 差分 ----
    records = []  # (rel_path, header, diff_text, compact_lines)
    t0 = time.time()
    for path in files:
        rel = os.path.relpath(path, args.in_dir)
        header, enc, compact = process_file(path)
        diff_text = ((header + "\n") if header else "") + "\n".join(enc) + "\n"
        records.append((rel, header, diff_text, compact))
    print(f"parse+diff: {time.time()-t0:.0f}s")

    orig_bytes = sum(
        os.path.getsize(os.path.join(args.in_dir, r[0])) for r in records
    )
    compact_bytes = sum(
        sum(len(x) + 1 for x in r[3]) for r in records
    )
    diff_bytes = sum(len(r[2].encode("ascii")) for r in records)
    print(f"orig:     {orig_bytes/1e6:9.2f} MB")
    print(f"compact:  {compact_bytes/1e6:9.2f} MB   {orig_bytes/compact_bytes:.2f}x")
    print(f"diff:     {diff_bytes/1e6:9.2f} MB   {orig_bytes/diff_bytes:.2f}x")

    # ---- 2. 共享 BPE 字典 ----
    streams = [r[2] for r in records]
    dict_map = build_bpe_dict(streams, args.max_rounds)

    # ---- 3. 写出 ----
    os.makedirs(args.out_dir, exist_ok=True)
    bpe_bytes = 0
    for rel, header, diff_text, compact in records:
        enc_text = apply_bpe(diff_text, dict_map)
        bpe_bytes += len(enc_text.encode("ascii"))
        out_path = os.path.join(args.out_dir, rel)
        os.makedirs(os.path.dirname(out_path), exist_ok=True)
        with open(out_path, "w", encoding="ascii", newline="\n") as fh:
            fh.write(enc_text)

    # 无分隔符定长格式：每 3 字符一条 (占位符 + 两字符原文)
    dict_str = "".join(ch + bg for ch, bg in dict_map)
    with open(os.path.join(args.out_dir, "rep.dict"), "w", encoding="ascii", newline="\n") as fh:
        fh.write(dict_str + "\n")

    print(f"bpe:      {bpe_bytes/1e6:9.2f} MB   {orig_bytes/bpe_bytes:.2f}x")
    print(f"rep.dict: {len(dict_str)/1024:.1f} KB  ({len(dict_map)} entries)")

    # ---- 4. 往返校验 ----
    bad = 0
    total_lines = 0
    for rel, header, diff_text, compact in records:
        enc_text = apply_bpe(diff_text, dict_map)
        back_diff = expand_bpe(enc_text, dict_map)
        back_compact = decode_front_code(back_diff.split("\n"))
        expected = sorted(compact)
        got = [c for c in back_compact if not c.startswith("#")]
        total_lines += len(expected)
        if got != expected:
            bad += 1
            print(f"  MISMATCH: {rel}: {len(expected)} lines vs {len(got)}")
            for i, (a, b) in enumerate(zip(expected, got)):
                if a != b:
                    print(f"    line {i}: {a!r} != {b!r}")
                    break
    print(f"verify: {total_lines:,} lines, {len(records) - bad}/{len(records)} files OK")
    if bad:
        sys.exit(1)
    print("done.")


if __name__ == "__main__":
    main()
