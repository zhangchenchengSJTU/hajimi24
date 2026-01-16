package com.example.hajimi24;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MarkdownUtils {

    private static final String ACADEMIC_STYLE =
            "<!DOCTYPE html><html><head>" +
                    "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                    "<link href='https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@400;700&display=swap' rel='stylesheet'>" +
                    "<link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css'>" +
                    "<script defer src='https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js'></script>" +
                    "<script defer src='https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/contrib/auto-render.min.js'></script>" +
                    "<style>" +
                    "  /* 日间模式变量 */" +
                    "  :root { --bg: #ffffff; --text: #000000; --h-color: #000000; --hr: #000000; --quote-bg: #f9f9f9; --quote-border: #333; --cite: #0000EE; }" +
                    "  /* 夜间模式变量 */" +
                    "  @media (prefers-color-scheme: dark) {" +
                    "    :root { --bg: #121212; --text: #e0e0e0; --h-color: #ffffff; --hr: #444; --quote-bg: #1e1e1e; --quote-border: #bb86fc; --cite: #bb86fc; }" +
                    "  }" +
                    "  body { font-family: 'Noto Serif SC', serif; line-height: 1.8; color: var(--text); background: var(--bg); padding: 30px 20px; text-align: justify; }" +
                    "  h1 { font-size: 1.6em; text-align: center; margin-bottom: 1.2em; font-family: sans-serif; color: var(--h-color); }" +
                    "  h2 { font-size: 1.3em; border-bottom: 1.5px solid var(--hr); padding-bottom: 5px; margin-top: 1.5em; font-family: sans-serif; color: var(--h-color); }" +
                    "  h3 { font-size: 1.1em; margin-top: 1.2em; font-family: sans-serif; font-weight: bold; color: var(--h-color); }" + // 三级标题
                    "  p { margin: 1em 0; text-indent: 2em; }" +
                    "  blockquote { margin: 1.5em 0; padding: 15px 25px; border-left: 5px solid var(--quote-border); background: var(--quote-bg); text-indent: 0; }" +
                    "  i, em { font-family: 'Noto Serif SC', serif; font-style: italic; }" +
                    "  i:contains-chinese, i { text-emphasis: none; font-family: 'STKaiti', 'KaiTi', serif; font-style: normal; }" + // 自动回退楷体
                    "  .cite { vertical-align: super; font-size: 0.75em; color: var(--cite); font-weight: bold; }" +
                    "  hr { border: 0; border-top: 1px solid var(--hr); margin: 2em 0; }" +
                    "</style>" +
                    "<script>" +
                    "  document.addEventListener('DOMContentLoaded', function() {" +
                    "    renderMathInElement(document.body, { delimiters: [{left: '$$', right: '$$', display: true}, {left: '$', right: '$', display: false}] });" +
                    "  });" +
                    "</script>" +
                    "</head><body>PLACEHOLDER_CONTENT</body></html>";

    public static String loadMarkdownFromAssets(Context context, String filename) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(filename), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty()) { sb.append("<p></p>"); continue; }

                // 标题解析（注意顺序：先匹配长的）
                if (t.startsWith("### ")) line = "<h3>" + t.substring(4) + "</h3>";
                else if (t.startsWith("## ")) line = "<h2>" + t.substring(3) + "</h2>";
                else if (t.startsWith("# ")) line = "<h1>" + t.substring(2) + "</h1>";
                else if (t.startsWith("- ")) line = "<li>" + t.substring(2) + "</li>";
                else if (t.startsWith("> ")) line = "<blockquote>" + t.substring(2) + "</blockquote>";
                else if (t.startsWith("---")) line = "<hr>";
                else line = "<p>" + t + "</p>";

                // 富文本解析
                line = line.replaceAll("\\*\\*\\*(.*?)\\*\\*\\*", "<b><i>$1</i></b>");
                line = line.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
                line = line.replaceAll("(?<!\\*)\\*(?!\\*)(.*?)\\*", "<i>$1</i>");
                line = line.replaceAll("\\[(\\d+)\\]", "<span class='cite'>[$1]</span>");

                sb.append(line).append("\n");
            }
        }
        return ACADEMIC_STYLE.replace("PLACEHOLDER_CONTENT", sb.toString());
    }
}
