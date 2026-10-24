import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 端到端验证: 用与 ProblemRepository 完全相同的解码逻辑
 * 解码 data_compact 下的文件，与 data 下的原始文件逐行比对。
 * 用法: java VerifyDecode <data_compact根> <data根>
 */
public class VerifyDecode {

    private static final Map<Character, String> repDict = new HashMap<>();
    private static final Pattern NUMS_PATTERN = Pattern.compile("\\['(.*?)'\\]");

    /** 与 ProblemRepository.compactMarkerIndex 一致 */
    private static int compactMarkerIndex(char c) {
        return c < '#' ? c - 33 : c - 34;
    }

    private static String readAllText(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static void parseRepDict(String text) {
        repDict.clear();
        String s = text.replace("\r", "").replace("\n", "").trim();
        for (int i = 0; i + 3 <= s.length(); i += 3) {
            repDict.put(s.charAt(i), s.substring(i + 1, i + 3));
        }
    }

    private static List<String> decodeCompactToOldLines(String content) {
        StringBuilder sb = new StringBuilder(content.length() * 2);
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            String rep = repDict.get(c);
            sb.append(rep != null ? rep : c);
        }
        List<String> result = new ArrayList<>();
        String prev = "";
        String mode = "";
        for (String line : sb.toString().split("\n")) {
            String s = line.trim();
            if (s.isEmpty()) continue;
            if (s.startsWith("#")) {
                mode = s.substring(1).trim();
                prev = "";
                continue;
            }
            int k = compactMarkerIndex(s.charAt(0));
            if (k < 0) k = 0;
            if (k > prev.length()) k = prev.length();
            String cur = prev.substring(0, k) + s.substring(1);
            int colon = cur.indexOf(':');
            if (colon <= 0) continue;
            String[] nums = cur.substring(0, colon).split(",");
            String expr = cur.substring(colon + 1);
            StringBuilder lineSb = new StringBuilder("['");
            for (int i = 0; i < nums.length; i++) {
                if (i > 0) lineSb.append("', '");
                lineSb.append(nums[i]);
            }
            lineSb.append("'] -> ").append(expr);
            if (!mode.isEmpty()) lineSb.append(" ").append(mode);
            result.add(lineSb.toString());
            prev = cur;
        }
        return result;
    }

    /** 归一化: 提取 ['...'] 数字列表和 -> 后的表达式（模拟 app 解析） */
    private static String normalize(String line) {
        Matcher m = NUMS_PATTERN.matcher(line);
        String nums = m.find() ? m.group(1) : "";
        int arrow = line.indexOf("->");
        String sol = arrow >= 0 ? line.substring(arrow + 2).trim() : "";
        return nums + " => " + sol;
    }

    public static void main(String[] args) throws Exception {
        Path compactRoot = Paths.get(args[0]);
        Path origRoot = Paths.get(args[1]);
        parseRepDict(readAllText(compactRoot.resolve("rep.dict")));

        int files = 0;
        int totalLines = 0;
        int mismatches = 0;
        try (Stream<Path> stream = Files.walk(compactRoot)) {
            List<Path> paths = stream.filter(p -> p.toString().endsWith(".txt")).sorted().toList();
            for (Path compactFile : paths) {
                Path rel = compactRoot.relativize(compactFile);
                Path origFile = origRoot.resolve(rel);
                if (!Files.exists(origFile)) {
                    System.out.println("MISSING ORIGINAL: " + rel);
                    mismatches++;
                    continue;
                }
                String content = readAllText(compactFile);
                List<String> decoded = decodeCompactToOldLines(content);
                List<String> expected = new ArrayList<>();
                for (String line : readAllText(origFile).split("\n")) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) continue;
                    if (s.indexOf("->") < 0) continue;
                    expected.add(s);
                }
                List<String> dn = new ArrayList<>();
                List<String> en = new ArrayList<>();
                decoded.forEach(l -> dn.add(normalize(l)));
                expected.forEach(l -> en.add(normalize(l)));
                Collections.sort(dn);
                Collections.sort(en);
                files++;
                totalLines += dn.size();
                if (!dn.equals(en)) {
                    mismatches++;
                    System.out.println("MISMATCH: " + rel
                            + " decoded=" + dn.size() + " expected=" + en.size());
                    for (int i = 0; i < Math.min(dn.size(), en.size()); i++) {
                        if (!dn.get(i).equals(en.get(i))) {
                            System.out.println("  decoded  : " + dn.get(i));
                            System.out.println("  expected : " + en.get(i));
                            break;
                        }
                    }
                    if (dn.size() != en.size()) {
                        for (int i = 0; i < Math.min(3, dn.size()); i++) {
                            if (!en.contains(dn.get(i)))
                                System.out.println("  extra decoded: " + dn.get(i));
                        }
                        for (int i = 0; i < Math.min(3, en.size()); i++) {
                            if (!dn.contains(en.get(i)))
                                System.out.println("  missing expected: " + en.get(i));
                        }
                    }
                }
            }
        }
        System.out.println("files=" + files + " lines=" + totalLines
                + " mismatches=" + mismatches);
        if (mismatches > 0) System.exit(1);
        System.out.println("VERIFY OK");
    }
}
