package com.example.hajimi24;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownUtils {

    /**
     * 将 Markdown 字符串渲染为带有 MathJax 渲染能力和高级解析能力的 HTML
     */
    public static String renderMarkdown(String mdContent) {
        if (mdContent == null) return "";
        StringBuilder sb = new StringBuilder();

        String[] lines = mdContent.split("\n");
        boolean inCodeBlock = false;

        for (String line : lines) {
            String t = line.trim();

            // 1. 处理多行代码块 (```)
            if (t.startsWith("```")) {
                if (!inCodeBlock) {
                    sb.append("<pre><code>");
                    inCodeBlock = true;
                } else {
                    sb.append("</code></pre>");
                    inCodeBlock = false;
                }
                continue;
            }

            if (inCodeBlock) {
                // 代码块内不解析 Markdown 语法，仅进行基础 HTML 转义
                sb.append(escapeHtml(line)).append("\n");
                continue;
            }

            if (t.isEmpty()) { sb.append("<p></p>"); continue; }

            // 2. 块级元素解析
            String processedLine = line;
            if (t.startsWith("### ")) {
                processedLine = "<h3>" + parseInline(t.substring(4)) + "</h3>";
            } else if (t.startsWith("## ")) {
                processedLine = "<h2>" + parseInline(t.substring(3)) + "</h2>";
            } else if (t.startsWith("# ")) {
                processedLine = "<h1>" + parseInline(t.substring(2)) + "</h1>";
            } else if (t.startsWith("- ")) {
                processedLine = "<li>" + parseInline(t.substring(2)) + "</li>";
            } else if (t.startsWith("> ")) {
                processedLine = "<blockquote>" + parseInline(t.substring(2)) + "</blockquote>";
            } else if (t.startsWith("---")) {
                processedLine = "<hr>";
            } else if (t.matches("^\\[\\d+\\].*")) {
                // 3. 处理参考文献定义，如 [1] 内容 -> <div id='ref-1'>[1] 内容</div>
                int closingBracket = t.indexOf(']');
                String num = t.substring(1, closingBracket);
                processedLine = "<div id='ref-" + num + "' class='ref-definition'>" + parseInline(t) + "</div>";
            } else {
                processedLine = "<p>" + parseInline(t) + "</p>";
            }

            sb.append(processedLine).append("\n");
        }
        return ACADEMIC_STYLE.replace("PLACEHOLDER_CONTENT", sb.toString());
    }

    /**
     * 解析行内元素：加粗、斜体、链接、行内代码、引用跳转
     */
    private static String parseInline(String text) {
        if (text == null) return "";

        // a. 处理超链接 [文字](url)
        text = text.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<a href='$2'>$1</a>");

        // b. 处理行内代码 `code`
        text = text.replaceAll("`(.*?)`", "<code>$1</code>");

        // c. 处理引用跳转 [n] -> <a href='#ref-n'>[n]</a>
        text = text.replaceAll("\\[(\\d+)\\]", "<sup class='cite'><a href='#ref-$1'>[$1]</a></sup>");

        // d. 处理加粗和斜体
        text = text.replaceAll("\\*\\*\\*(.*?)\\*\\*\\*", "<b><i>$1</i></b>");
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
        text = text.replaceAll("(?<!\\*)\\*(?!\\*)(.*?)\\*", "<i>$1</i>");

        return text;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static final String ACADEMIC_STYLE =
            "<!DOCTYPE html><html><head>" +
                    "<meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                    "<script>" +
                    "  window.MathJax = { tex: { inlineMath: [['$', '$']], displayMath: [['$$', '$$']] }, svg: { fontCache: 'global' } };" +
                    "</script>" +
                    "<script src='mathjax/tex-svg.js'></script>" +
                    "<style>" +
                    "  :root { --bg: #ffffff; --text: #1a1a1a; --h-color: #000000; --hr: #eaeaea; --quote-bg: #f8f9fa; --quote-border: #ccc; --cite: #0366d6; --code-bg: #f0f0f0; }" +
                    "  @media (prefers-color-scheme: dark) {" +
                    "    :root { --bg: #121212; --text: #d1d1d1; --h-color: #ffffff; --hr: #333; --quote-bg: #1e1e1e; --quote-border: #444; --cite: #58a6ff; --code-bg: #2d2d2d; }" +
                    "  }" +
                    "  body { font-family: 'serif'; line-height: 1.7; color: var(--text); background: var(--bg); padding: 25px 18px; text-align: justify; scroll-behavior: smooth; }" +
                    "  h1 { font-size: 1.5em; text-align: center; margin-bottom: 1.2em; color: var(--h-color); }" +
                    "  h2 { font-size: 1.25em; border-bottom: 1px solid var(--hr); padding-bottom: 4px; margin-top: 1.5em; color: var(--h-color); }" +
                    "  p { margin: 0.8em 0; text-indent: 2em; }" +
                    "  blockquote { margin: 1.2em 0; padding: 12px 20px; border-left: 4px solid var(--quote-border); background: var(--quote-bg); text-indent: 0; font-style: italic; }" +
                    "  li { margin-left: 1.2em; margin-bottom: 0.4em; text-indent: 0; }" +
                    "  hr { border: 0; border-top: 1px solid var(--hr); margin: 2em 0; }" +
                    "  a { color: var(--cite); text-decoration: none; }" +
                    "  a:hover { text-decoration: underline; }" +
                    "  code { font-family: monospace; background: var(--code-bg); padding: 2px 4px; border-radius: 4px; font-size: 0.9em; }" +
                    "  pre { background: var(--code-bg); padding: 15px; border-radius: 8px; overflow-x: auto; margin: 1em 0; }" +
                    "  pre code { background: none; padding: 0; }" +
                    "  .ref-definition { font-size: 0.9em; margin-bottom: 5px; color: var(--text); }" +
                    "  .cite a { font-weight: bold; padding: 0 2px; }" +
                    "  mjx-container { color: inherit !important; fill: currentColor !important; }" +
                    "</style>" +
                    "</head><body>PLACEHOLDER_CONTENT</body></html>";

    public static String loadMarkdownFromAssets(Context context, String filename) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(filename), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        return renderMarkdown(sb.toString());
    }
}
