# 24 点数据压缩工具

把 `data/` 下的 24 点题库从旧文本格式转成 Wordle 风格的紧凑编码，
供 App 端 `ProblemRepository` 直接加载。

## 新旧格式

旧格式（每行一条）：

```
[1] ['1', '1', '1', '8'] -> (1 + 1 + 1) * 8
```

新格式（`data_compact/`，解码后还原成旧格式行）：

- 文件头：`#mod 37` / `#base 5`，模/进制只写一次
- 紧凑行：`数字列表:表达式`，如 `1,1,1,8:(1 + 1 + 1) * 8`
- 每个文件内按行排序后做差分编码：每行第一个字符是前缀长度标记
  （ASCII 33..126 去掉 `#`，即 0..92），后面是剩余部分
- 所有文件共享一份 BPE 字典 `rep.dict`

`rep.dict` 为无分隔符定长格式，每 3 字符一条：
`<占位符><两字符原文>`（例如 `G /H -,I (...`）。

## 设计取舍

- 表达式**保留原样（含空格）**：App 端解码后还原旧格式行，
  `isProblemValid` / `parseLineToProblem` / 界面显示全部零改动。
- BPE **不压缩换行**：输出文件保持一行一条题目，git diff 仍是行级。
- 占位符只用**可打印 ASCII**：无 UTF-8/latin-1 编码混用问题
  （注意：`wordle/` 下旧实现是 latin-1 字节 + UTF-8 字典，编码不一致，本方案刻意避开）。
- 数字列表显式保留：表达式里的数字顺序与题目列表几乎从不一致，
  反推不可靠（尤其复数/分数/进制）。

## 用法

```bash
# 转换 data/ -> data_compact/（含 rep.dict），自带往返校验
python tools/convert_24data.py --in data --out data_compact

# Java 侧端到端校验（需 JDK）：解码 data_compact 并与 data 逐行比对
javac -encoding UTF-8 -d build tools/VerifyDecode.java
java -cp build VerifyDecode data_compact data
```

上线步骤：

1. 把 `data_compact/` 下所有文件（保持目录结构）替换到 GitHub 的 `data/` 目录，
   并把 `rep.dict` 放到 `data/rep.dict`；
2. `app/src/main/assets/data/rep.dict` 已内置同款字典（离线兜底）；
3. App 端 `ProblemRepository` 自动识别新旧格式，旧文件仍可正常加载。

内置的 `app/src/main/assets/5num/5num.txt` 已用同一份全局字典转换
（396 KB -> 132 KB），加载路径不变。

## 重新生成全部数据（含 5num）

`convert_24data.py` 只处理 `data/`。若题库扩充，建议把 5num.txt 一并加入语料
重新构建全局字典，再用同款字典转换 5num：

```bash
python tools/convert_24data.py --in data --out data_compact
# 用 tools/convert_24data.py 里的 process_file + apply_bpe 按 rep.dict 转换 5num.txt
```
