bash -lc cat > TwentyFour5Fractions.java <<'EOF'
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 5 numbers (reduced 1-digit fractions, incl integers) -> 24, using + - * /.
 *
 * Output only puzzles with exactly ONE solution under the provided AST equivalence.
 *
 * Default: pool = {a/b | 1<=a<=9, 1<=b<=9, gcd(a,b)=1}, includes improper fractions and integers (b=1).
 *
 * Combination model: multiset of 5 numbers WITH repetition, order-insensitive.
 * Enumeration order: i0<=i1<=i2<=i3<=i4 over pool indices sorted by numeric value.
 * ID: 1-based lexicographic rank in that enumeration.
 */
public class TwentyFour5Fractions {

    // ------------------------- Config -------------------------

    private static final int DEFAULT_THREADS = 16;
    private static final Rational TARGET = new Rational(24, 1);

    // ------------------------- Main -------------------------

    public static void main(String[] args) throws Exception {
        Args cfg = Args.parse(args);

        int threads = cfg.threads;
        String outPath = cfg.outPath;

        // 1) Build pool
        List<Token> pool = buildPool(cfg.includeZero);
        pool.sort(Comparator.comparing(t -> t.value));
        int N = pool.size();

        System.out.println("Pool size = " + N);
        if (cfg.printPool) {
            for (int i = 0; i < pool.size(); i++) {
                System.out.println(i + "\t" + pool.get(i).text + "\t" + pool.get(i).value);
            }
        }

        // 2) Precompute combinations for ranking offsets (N up to 55 => choose up to N+4)
        Comb comb = new Comb(N + 4);

        // 3) Prepare temp dir
        Path tmpDir = Paths.get(cfg.tmpDir);
        Files.createDirectories(tmpDir);

        // 4) Create tasks split by prefix (i0,i1) for load balancing
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        List<Future<Path>> futures = new ArrayList<>();

        for (int i0 = 0; i0 < N; i0++) {
            long offsetFirst = offsetFirst(i0, N, comb); // combos with first < i0
            for (int i1 = i0; i1 < N; i1++) {
                long offsetPrefix = offsetFirst + offsetSecondWithinFirst(i0, i1, N, comb); // combos with (first==i0 and second < i1)

                final int fi0 = i0;
                final int fi1 = i1;
                final long baseOffset = offsetPrefix;

                futures.add(exec.submit(() -> {
                    String partName = String.format(Locale.ROOT, "part_%02d_%02d.txt", fi0, fi1);
                    Path partPath = tmpDir.resolve(partName);
                    try (BufferedWriter w = Files.newBufferedWriter(partPath, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

                        int[] idx = new int[5];
                        idx[0] = fi0;
                        idx[1] = fi1;

                        long local = 0;

                        // Enumerate i2<=i3<=i4 starting from i1
                        for (int i2 = fi1; i2 < N; i2++) {
                            idx[2] = i2;
                            for (int i3 = i2; i3 < N; i3++) {
                                idx[3] = i3;
                                for (int i4 = i3; i4 < N; i4++) {
                                    idx[4] = i4;

                                    long id = baseOffset + local + 1; // 1-based
                                    local++;

                                    if (cfg.limit > 0 && id > cfg.limit) {
                                        return partPath;
                                    }

                                    // Build puzzle tokens
                                    String[] toks = new String[5];
                                    Rational[] vals = new Rational[5];
                                    for (int k = 0; k < 5; k++) {
                                        Token t = pool.get(idx[k]);
                                        toks[k] = t.text;
                                        vals[k] = t.value;
                                    }

                                    Solver.Result res = Solver.uniqueSolution(toks, vals);
                                    if (res.unique) {
                                        String line = formatLine(id, toks, res.expr);
                                        w.write(line);
                                        w.newLine();
                                    }
                                }
                            }
                        }
                    }
                    return partPath;
                }));
            }
        }

        exec.shutdown();

        // 5) Wait for tasks
        for (Future<Path> f : futures) {
            f.get();
        }

        // 6) Merge parts in lexicographic order to output
        Path out = Paths.get(outPath);
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            long lines = 0;
            // Merge by i0,i1 ordering
            for (int i0 = 0; i0 < N; i0++) {
                for (int i1 = i0; i1 < N; i1++) {
                    Path part = tmpDir.resolve(String.format(Locale.ROOT, "part_%02d_%02d.txt", i0, i1));
                    if (!Files.exists(part)) continue;
                    try (BufferedReader r = Files.newBufferedReader(part, StandardCharsets.UTF_8)) {
                        String s;
                        while ((s = r.readLine()) != null) {
                            if (!s.isEmpty()) {
                                w.write(s);
                                w.newLine();
                                lines++;
                            }
                        }
                    }
                    if (cfg.deleteParts) Files.deleteIfExists(part);
                }
            }
            System.out.println("Wrote unique puzzles lines = " + lines);
        }

        System.out.println("Done. Output: " + out.toAbsolutePath());
    }

    // ------------------------- Formatting -------------------------

    private static String formatLine(long id, String[] toks, String expr) {
        // `[137916] ['2/9', '1/2', '5/7', '7/6', '9'] -> (((1/2 * 9) + (5/7 * 7/6)) / 2/9)`
        StringBuilder sb = new StringBuilder(256);
        sb.append('[').append(id).append("] ");
        sb.append('[');
        for (int i = 0; i < toks.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append('\'').append(toks[i]).append('\'');
        }
        sb.append("] -> ");
        sb.append(expr);
        return sb.toString();
    }

    // ------------------------- Pool -------------------------

    private static List<Token> buildPool(boolean includeZero) {
        List<Token> pool = new ArrayList<>();
        // 1-digit: assume 0..9 for numerator if includeZero else 1..9; denominator 1..9.
        int numStart = includeZero ? 0 : 1;
        for (int b = 1; b <= 9; b++) {
            for (int a = numStart; a <= 9; a++) {
                if (a == 0) {
                    // 0/b reduces to 0/1; include only once as "0" if requested
                    if (b == 1) {
                        pool.add(new Token("0", new Rational(0, 1)));
                    }
                    continue;
                }
                if (gcd(a, b) == 1) {
                    Rational v = new Rational(a, b);
                    String s = (b == 1) ? Integer.toString(a) : (a + "/" + b);
                    pool.add(new Token(s, v));
                }
            }
        }
        // If includeZero, we added "0" exactly once when b==1.
        return pool;
    }

    private static final class Token {
        final String text;
        final Rational value;
        Token(String text, Rational value) {
            this.text = text;
            this.value = value;
        }
    }

    // ------------------------- Offsets for lexicographic rank -------------------------

    /**
     * Number of 5-multisets with first element strictly less than i0.
     * Count for fixed first a: choose((N-a)+4-1,4) = C(N-a+3,4)
     */
    private static long offsetFirst(int i0, int N, Comb comb) {
        long sum = 0;
        for (int a = 0; a < i0; a++) {
            sum += comb.C((N - a) + 3, 4);
        }
        return sum;
    }

    /**
     * Within fixed i0, number of 5-multisets with second element < i1.
     * For second b in [i0, i1-1], remaining 3 indices from b..N-1 with repetition => C((N-b)+3-1,3)=C(N-b+2,3)
     */
    private static long offsetSecondWithinFirst(int i0, int i1, int N, Comb comb) {
        long sum = 0;
        for (int b = i0; b < i1; b++) {
            sum += comb.C((N - b) + 2, 3);
        }
        return sum;
    }

    // ------------------------- Rational -------------------------

    private static final class Rational implements Comparable<Rational> {
        final long n; // numerator
        final long d; // denominator (positive)

        Rational(long n, long d) {
            if (d == 0) throw new ArithmeticException("denominator=0");
            if (d < 0) { n = -n; d = -d; }
            long g = gcd(Math.abs(n), d);
            this.n = n / g;
            this.d = d / g;
        }

        static Rational add(Rational a, Rational b) {
            long g = gcd(a.d, b.d);
            long ad = a.d / g;
            long bd = b.d / g;
            // denom = ad * b.d = a.d/g * b.d
            long denom = ad * b.d;
            long numer = a.n * bd + b.n * ad;
            return new Rational(numer, denom);
        }

        static Rational sub(Rational a, Rational b) {
            long g = gcd(a.d, b.d);
            long ad = a.d / g;
            long bd = b.d / g;
            long denom = ad * b.d;
            long numer = a.n * bd - b.n * ad;
            return new Rational(numer, denom);
        }

        static Rational mul(Rational a, Rational b) {
            // cross-cancel to reduce overflow
            long g1 = gcd(Math.abs(a.n), b.d);
            long g2 = gcd(Math.abs(b.n), a.d);
            long an = a.n / g1;
            long bd = b.d / g1;
            long bn = b.n / g2;
            long ad = a.d / g2;
            return new Rational(an * bn, ad * bd);
        }

        static Rational div(Rational a, Rational b) {
            if (b.n == 0) throw new ArithmeticException("divide by zero");
            // a / b = a * (b.d / b.n)
            long g1 = gcd(Math.abs(a.n), Math.abs(b.n));
            long g2 = gcd(a.d, b.d);
            long an = a.n / g1;
            long bn = b.n / g1;
            long ad = a.d / g2;
            long bd = b.d / g2;
            // (an/ad) * (bd/bn)
            long g3 = gcd(Math.abs(an), Math.abs(bn));
            long g4 = gcd(bd, ad);
            long an2 = an / g3;
            long bn2 = bn / g3;
            long bd2 = bd / g4;
            long ad2 = ad / g4;
            return new Rational(an2 * bd2, ad2 * bn2);
        }

        @Override
        public int compareTo(Rational o) {
            // compare n/d vs o.n/o.d via cross multiplication (safe for our magnitude)
            long left = this.n * o.d;
            long right = o.n * this.d;
            return Long.compare(left, right);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Rational)) return false;
            Rational r = (Rational) obj;
            return this.n == r.n && this.d == r.d;
        }

        @Override
        public int hashCode() {
            return (int)(31L * n + d);
        }

        @Override
        public String toString() {
            if (d == 1) return Long.toString(n);
            return n + "/" + d;
        }
    }

    // ------------------------- AST nodes (same signature logic as provided) -------------------------

    private interface Node {
        String getSignature();
    }

    private static final class ValueNode implements Node {
        final String val;
        private String sig;
        ValueNode(String v) { this.val = v; }
        @Override
        public String getSignature() {
            if (sig == null) sig = "V(" + val + ")";
            return sig;
        }
    }

    private static final class OpNode implements Node {
        final String type; // SUM or PROD
        final List<Node> positives = new ArrayList<>();
        final List<Node> negatives = new ArrayList<>();
        private String sig;

        OpNode(String type) { this.type = type; }

        void addTerm(Node child, boolean isPositive) {
            // mimic provided code: filter empty ValueNode
            if (child instanceof ValueNode && ((ValueNode) child).val.isEmpty()) {
                return;
            }

            if (child instanceof OpNode && ((OpNode) child).type.equals(this.type)) {
                OpNode childOp = (OpNode) child;
                if (isPositive) {
                    this.positives.addAll(childOp.positives);
                    this.negatives.addAll(childOp.negatives);
                } else {
                    this.positives.addAll(childOp.negatives);
                    this.negatives.addAll(childOp.positives);
                }
            } else {
                if (isPositive) this.positives.add(child);
                else this.negatives.add(child);
            }
        }

        @Override
        public String getSignature() {
            if (sig != null) return sig;

            List<String> posSigs = new ArrayList<>(positives.size());
            List<String> negSigs = new ArrayList<>(negatives.size());
            for (Node n : positives) posSigs.add(n.getSignature());
            for (Node n : negatives) negSigs.add(n.getSignature());

            Collections.sort(posSigs);
            Collections.sort(negSigs);

            sig = type + "{P" + posSigs + ",N" + negSigs + "}";
            return sig;
        }
    }

    // ------------------------- Expr -------------------------

    private static final class Expr {
        final Rational value;
        final Node node;
        final String expr;
        final String sig;

        Expr(Rational value, Node node, String expr) {
            this.value = value;
            this.node = node;
            this.expr = expr;
            this.sig = node.getSignature();
        }

        static Expr leaf(String token, Rational v) {
            return new Expr(v, new ValueNode(token), token);
        }
    }

    // ------------------------- Solver -------------------------

    private static final class Solver {

        static final class Result {
            final boolean unique;
            final String expr;
            Result(boolean unique, String expr) {
                this.unique = unique;
                this.expr = expr;
            }
        }

        /**
         * Returns unique solution (AST-distinct) if exists; otherwise unique=false.
         */
        static Result uniqueSolution(String[] tokens, Rational[] values) {
            Expr[] buf0 = new Expr[5];
            for (int i = 0; i < 5; i++) buf0[i] = Expr.leaf(tokens[i], values[i]);
            Arrays.sort(buf0, Comparator.comparing(e -> e.sig));

            // map signature -> best expression string for that signature
            HashMap<String, String> sol = new HashMap<>(4);
            HashSet<String> visited = new HashSet<>(256);

            Expr[][] stack = new Expr[5][];
            stack[0] = buf0;
            stack[1] = new Expr[4];
            stack[2] = new Expr[3];
            stack[3] = new Expr[2];
            stack[4] = new Expr[1];

            dfs(stack, 0, 5, sol, visited);

            if (sol.size() == 1) {
                String only = sol.values().iterator().next();
                return new Result(true, only);
            }
            return new Result(false, null);
        }

        private static void dfs(Expr[][] stack, int depth, int size,
                                HashMap<String, String> sol, HashSet<String> visited) {
            if (sol.size() >= 2) return;

            Expr[] cur = stack[depth];

            // visited key: signatures list (sorted) with separators
            String key = stateKey(cur, size);
            if (!visited.add(key)) return;

            if (size == 1) {
                Expr e = cur[0];
                if (e.value.equals(TARGET)) {
                    // keep best representative (shortest, then lexicographically smallest)
                    String existing = sol.get(e.sig);
                    if (existing == null || e.expr.length() < existing.length() ||
                            (e.expr.length() == existing.length() && e.expr.compareTo(existing) < 0)) {
                        sol.put(e.sig, e.expr);
                    }
                }
                return;
            }

            // pair enumeration with duplicate skipping (by signature)
            for (int i = 0; i < size; i++) {
                if (i > 0 && cur[i].sig.equals(cur[i - 1].sig)) continue;
                for (int j = i + 1; j < size; j++) {
                    if (j > i + 1 && cur[j].sig.equals(cur[j - 1].sig)) continue;

                    Expr a = cur[i];
                    Expr b = cur[j];

                    // Build next array excluding i,j, then append candidate
                    Expr[] next = stack[depth + 1];
                    int p = 0;
                    for (int k = 0; k < size; k++) {
                        if (k == i || k == j) continue;
                        next[p++] = cur[k];
                    }

                    // Generate candidates
                    // +
                    Expr add = combine(a, b, '+');
                    next[p] = add;
                    sortSmall(next, p + 1);
                    dfs(stack, depth + 1, p + 1, sol, visited);
                    if (sol.size() >= 2) return;

                    // *
                    Expr mul = combine(a, b, '*');
                    next[p] = mul;
                    sortSmall(next, p + 1);
                    dfs(stack, depth + 1, p + 1, sol, visited);
                    if (sol.size() >= 2) return;

                    // - : a-b and b-a (unless identical signature)
                    Expr sub1 = combine(a, b, '-');
                    next[p] = sub1;
                    sortSmall(next, p + 1);
                    dfs(stack, depth + 1, p + 1, sol, visited);
                    if (sol.size() >= 2) return;

                    if (!a.sig.equals(b.sig)) {
                        Expr sub2 = combine(b, a, '-');
                        next[p] = sub2;
                        sortSmall(next, p + 1);
                        dfs(stack, depth + 1, p + 1, sol, visited);
                        if (sol.size() >= 2) return;
                    }

                    // / : a/b and b/a (skip division by zero)
                    if (b.value.n != 0) {
                        Expr div1;
                        try {
                            div1 = combine(a, b, '/');
                        } catch (ArithmeticException ex) {
                            div1 = null;
                        }
                        if (div1 != null) {
                            next[p] = div1;
                            sortSmall(next, p + 1);
                            dfs(stack, depth + 1, p + 1, sol, visited);
                            if (sol.size() >= 2) return;
                        }
                    }
                    if (!a.sig.equals(b.sig) && a.value.n != 0) {
                        Expr div2;
                        try {
                            div2 = combine(b, a, '/');
                        } catch (ArithmeticException ex) {
                            div2 = null;
                        }
                        if (div2 != null) {
                            next[p] = div2;
                            sortSmall(next, p + 1);
                            dfs(stack, depth + 1, p + 1, sol, visited);
                            if (sol.size() >= 2) return;
                        }
                    }
                }
            }
        }

        private static Expr combine(Expr left, Expr right, char op) {
            Rational v;
            Node node;
            String expr;

            // For commutative ops, build deterministic order by signature
            if (op == '+' || op == '*') {
                if (left.sig.compareTo(right.sig) > 0) {
                    Expr tmp = left;
                    left = right;
                    right = tmp;
                }
            }

            switch (op) {
                case '+':
                    v = Rational.add(left.value, right.value);
                    node = makeSum(left.node, right.node, true);
                    expr = "(" + left.expr + " + " + right.expr + ")";
                    break;
                case '-':
                    v = Rational.sub(left.value, right.value);
                    node = makeSum(left.node, right.node, false);
                    expr = "(" + left.expr + " - " + right.expr + ")";
                    break;
                case '*':
                    v = Rational.mul(left.value, right.value);
                    node = makeProd(left.node, right.node, true);
                    expr = "(" + left.expr + " * " + right.expr + ")";
                    break;
                case '/':
                    v = Rational.div(left.value, right.value);
                    node = makeProd(left.node, right.node, false);
                    expr = "(" + left.expr + " / " + right.expr + ")";
                    break;
                default:
                    throw new IllegalArgumentException("op=" + op);
            }
            return new Expr(v, node, expr);
        }

        private static Node makeSum(Node left, Node right, boolean rightPositive) {
            OpNode n = new OpNode("SUM");
            n.addTerm(left, true);
            n.addTerm(right, rightPositive);
            return n;
        }

        private static Node makeProd(Node left, Node right, boolean rightPositive) {
            OpNode n = new OpNode("PROD");
            n.addTerm(left, true);
            n.addTerm(right, rightPositive);
            return n;
        }

        private static String stateKey(Expr[] arr, int size) {
            // signatures already sorted
            StringBuilder sb = new StringBuilder(size * 16);
            for (int i = 0; i < size; i++) {
                if (i > 0) sb.append('|');
                sb.append(arr[i].sig);
            }
            return sb.toString();
        }

        private static void sortSmall(Expr[] arr, int size) {
            // insertion sort by signature for very small arrays (<=5)
            for (int i = 1; i < size; i++) {
                Expr x = arr[i];
                int j = i - 1;
                while (j >= 0 && arr[j].sig.compareTo(x.sig) > 0) {
                    arr[j + 1] = arr[j];
                    j--;
                }
                arr[j + 1] = x;
            }
        }
    }

    // ------------------------- Combinations -------------------------

    private static final class Comb {
        private final long[][] c;
        Comb(int maxN) {
            c = new long[maxN + 1][maxN + 1];
            for (int n = 0; n <= maxN; n++) {
                c[n][0] = 1;
                c[n][n] = 1;
                for (int k = 1; k < n; k++) {
                    c[n][k] = c[n - 1][k - 1] + c[n - 1][k];
                }
            }
        }
        long C(int n, int k) {
            if (k < 0 || k > n) return 0;
            return c[n][k];
        }
    }

    // ------------------------- Utils -------------------------

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return Math.abs(a);
    }

    // ------------------------- Args -------------------------

    private static final class Args {
        int threads = DEFAULT_THREADS;
        String outPath = "output.txt";
        String tmpDir = "tmp_parts";
        boolean deleteParts = true;
        long limit = -1; // optional: process only first N puzzles by ID
        boolean includeZero = false;
        boolean printPool = false;

        static Args parse(String[] args) {
            Args a = new Args();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--threads":
                        a.threads = Integer.parseInt(args[++i]);
                        break;
                    case "--out":
                        a.outPath = args[++i];
                        break;
                    case "--tmp":
                        a.tmpDir = args[++i];
                        break;
                    case "--keep-parts":
                        a.deleteParts = false;
                        break;
                    case "--limit":
                        a.limit = Long.parseLong(args[++i]);
                        break;
                    case "--include-zero":
                        a.includeZero = true;
                        break;
                    case "--print-pool":
                        a.printPool = true;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown arg: " + args[i]);
                }
            }
            return a;
        }
    }
}
EOF
