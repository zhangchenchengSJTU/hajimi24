package com.example.hajimi24;

import android.content.Context;
import android.text.Html;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MarkdownUtils {

    public static CharSequence loadMarkdownFromAssets(Context context, String filename) {
        StringBuilder sb = new StringBuilder();
        try {
            InputStream is = context.getAssets().open(filename);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("# ")) {
                    line = "<h3>" + line.substring(2) + "</h3>";
                } else if (line.startsWith("---")) {
                    line = "<hr>";
                } else if (line.startsWith("- ")) {
                    line = "• " + line.substring(2) + "<br>";
                }
                line = line.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
                line = line.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<a href=\"$2\">$1</a>");

                if (!line.startsWith("<h") && !line.startsWith("<hr") && !line.isEmpty()) {
                    line += "<br>";
                }
                sb.append(line).append("\n");
            }
            br.close();
        } catch (Exception e) {
            return "无法读取说明书: " + e.getMessage();
        }
        return Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_LEGACY);
    }
}
