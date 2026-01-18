package com.example.hajimi24;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownUtils {

    public static String renderMarkdown(String mdContent) {
        if (mdContent == null) return "";
        StringBuilder sb = new StringBuilder();

        String[] lines = mdContent.split("\n");
        boolean inCodeBlock = false;

        for (String line : lines) {
            String t = line.trim();

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
                sb.append(escapeHtml(line)).append("\n");
                continue;
            }

            if (t.isEmpty()) { sb.append("<p></p>"); continue; }

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

    private static String parseInline(String text) {
        if (text == null) return "";
        text = text.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<a href='$2'>$1</a>");
        text = text.replaceAll("`(.*?)`", "<code>$1</code>");
        text = text.replaceAll("\\[(\\d+)\\]", "<sup class='cite'><a href='#ref-$1'>[$1]</a></sup>");
        text = text.replaceAll("\\*\\*\\*(.*?)\\*\\*\\*", "<b><i>$1</i></b>");
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
        text = text.replaceAll("(?<!\\*)\\*(?!\\*)(.*?)\\*", "<i>$1</i>");
        return text;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final String ACADEMIC_STYLE =
            "<!DOCTYPE html><html><head>" +
                    "<meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                    "<script>window.MathJax = { tex: { inlineMath: [['$', '$']], displayMath: [['$$', '$$']] }, svg: { fontCache: 'global' } };</script>" +
                    "<script src='mathjax/tex-svg.js'></script>" +
                    "<style>" +
                    "  :root { --bg: #ffffff; --text: #1a1a1a; --h-color: #000000; --hr: #eaeaea; --quote-bg: #f8f9fa; --quote-border: #ccc; --cite: #0366d6; --code-bg: #f0f0f0; }" +
                    "  /* 核心：夜间模式色彩定义 */" +
                    "  @media (prefers-color-scheme: dark) {" +
                    "    :root { --bg: #121212; --text: #d1d1d1; --h-color: #ffffff; --hr: #333; --quote-bg: #1e1e1e; --quote-border: #444; --cite: #58a6ff; --code-bg: #2d2d2d; }" +
                    "  }" +
                    "  body { font-family: serif; line-height: 1.7; color: var(--text); background: var(--bg); padding: 25px 18px; text-align: justify; scroll-behavior: smooth; }" +
                    "  h1, h2, h3 { color: var(--h-color); text-align: left; }" +
                    "  h1 { font-size: 1.5em; text-align: center; margin-bottom: 1.2em; }" +
                    "  h2 { font-size: 1.25em; border-bottom: 1.5px solid var(--hr); padding-bottom: 4px; margin-top: 1.5em; }" +
                    "  p { margin: 0.8em 0; text-indent: 2em; }" +
                    "  blockquote { margin: 1.2em 0; padding: 12px 20px; border-left: 4px solid var(--quote-border); background: var(--quote-bg); text-indent: 0; font-style: italic; }" +
                    "  hr { border: 0; border-top: 1px solid var(--hr); margin: 2em 0; }" +
                    "  a { color: var(--cite); text-decoration: none; }" +
                    "  code { font-family: monospace; background: var(--code-bg); padding: 2px 4px; border-radius: 4px; font-size: 0.9em; }" +
                    "  pre { background: var(--code-bg); padding: 15px; border-radius: 8px; overflow-x: auto; margin: 1em 0; border: 1px solid var(--hr); }" +
                    "  .ref-definition { font-size: 0.9em; margin-bottom: 5px; color: var(--text); }" +
                    "  /* 关键：公式自动适应文字颜色 */" +
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
