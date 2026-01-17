package com.example.hajimi24;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MarkdownUtils {

    /**
     * 将 Markdown 字符串渲染为带有 MathJax 渲染能力的 HTML
     */
    public static String renderMarkdown(String mdContent) {
        StringBuilder sb = new StringBuilder();
        if (mdContent == null) return "";

        String[] lines = mdContent.split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) { sb.append("<p></p>"); continue; }

            // 基础 Markdown 元素解析
            if (t.startsWith("### ")) line = "<h3>" + t.substring(4) + "</h3>";
            else if (t.startsWith("## ")) line = "<h2>" + t.substring(3) + "</h2>";
            else if (t.startsWith("# ")) line = "<h1>" + t.substring(2) + "</h1>";
            else if (t.startsWith("- ")) line = "<li>" + t.substring(2) + "</li>";
            else if (t.startsWith("> ")) line = "<blockquote>" + t.substring(2) + "</blockquote>";
            else if (t.startsWith("---")) line = "<hr>";
            else line = "<p>" + t + "</p>";

            // 基础富文本：粗体、斜体、引用
            line = line.replaceAll("\\*\\*\\*(.*?)\\*\\*\\*", "<b><i>$1</i></b>");
            line = line.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
            line = line.replaceAll("(?<!\\*)\\*(?!\\*)(.*?)\\*", "<i>$1</i>");
            line = line.replaceAll("\\[(\\d+)\\]", "<span class='cite'>[$1]</span>");

            sb.append(line).append("\n");
        }
        return ACADEMIC_STYLE.replace("PLACEHOLDER_CONTENT", sb.toString());
    }

    private static final String ACADEMIC_STYLE =
            "<!DOCTYPE html><html><head>" +
                    "<meta charset='UTF-8'>" +
                    "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +

                    // --- MathJax 3.x 离线精简配置 ---
                    "<script>" +
                    "  window.MathJax = {" +
                    "    tex: {" +
                    "      inlineMath: [['$', '$'], ['\\\\(', '\\\\)']]," +
                    "      displayMath: [['$$', '$$'], ['\\\\[', '\\\\]']]" +
                    "    }," +
                    "    svg: { fontCache: 'global' }" +
                    "  };" +
                    "</script>" +
                    // 修改点：配合 BaseURL，直接使用相对路径引用 assets/mathjax/ 下的文件
                    "<script src='mathjax/tex-svg.js'></script>" +

                    "<style>" +
                    "  :root { --bg: #ffffff; --text: #1a1a1a; --h-color: #000000; --hr: #eaeaea; --quote-bg: #f8f9fa; --quote-border: #ccc; --cite: #0366d6; }" +
                    "  @media (prefers-color-scheme: dark) {" +
                    "    :root { --bg: #121212; --text: #d1d1d1; --h-color: #ffffff; --hr: #333; --quote-bg: #1e1e1e; --quote-border: #444; --cite: #58a6ff; }" +
                    "  }" +
                    "  body { font-family: 'serif'; line-height: 1.7; color: var(--text); background: var(--bg); padding: 25px 18px; text-align: justify; }" +
                    "  h1 { font-size: 1.5em; text-align: center; margin-bottom: 1.2em; color: var(--h-color); }" +
                    "  h2 { font-size: 1.25em; border-bottom: 1px solid var(--hr); padding-bottom: 4px; margin-top: 1.5em; color: var(--h-color); }" +
                    "  p { margin: 0.8em 0; text-indent: 2em; }" +
                    "  blockquote { margin: 1.2em 0; padding: 12px 20px; border-left: 4px solid var(--quote-border); background: var(--quote-bg); text-indent: 0; font-style: italic; }" +
                    "  hr { border: 0; border-top: 1px solid var(--hr); margin: 2em 0; }" +
                    "  mjx-container { color: inherit !important; fill: currentColor !important; }" +
                    "</style>" +
                    "</head><body>PLACEHOLDER_CONTENT</body></html>";

    /**
     * 用于加载 assets 目录下内置的 md 文件 (如 help.md)
     */
    public static String loadMarkdownFromAssets(Context context, String filename) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(filename), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        // 调用统一的渲染方法
        return renderMarkdown(sb.toString());
    }
}
