package com.proveyu.student.codeground;

import com.proveyu.student.codeground.CodeGroundContent.Arg;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Starter programs (an empty solve() plus the code that reads the input and prints the answer) in the four languages, for problems
 * added by an admin. The built-in problems get theirs from {@code scripts/codeground/lib.mjs}; the two must give identical text,
 * which CodeGroundAdminIT checks for every built-in problem.
 */
public final class CodeGroundStarters {

    public static final List<String> ARG_TYPES = List.of("int", "str", "ints", "words", "rows", "tree");
    public static final List<String> RETURN_TYPES = List.of("int", "long", "double", "bool", "string", "int[]", "string[]", "int[][]", "string[][]");

    /** Printed by every harness just before the answer. */
    static final String MARK = "##PROVEYU-ANSWER##";
    private static final String FOOTER = "Input is read and the answer is printed for you: no need to change below";

    private static final Map<String, String> TEXT = Map.of("int", "an integer", "str", "a string (may contain spaces)",
            "ints", "integers separated by spaces (may be empty)", "words", "words separated by spaces",
            "rows", "a count k, then k lines of integers", "tree", "a binary tree in level order, \"null\" for a missing child");
    private static final Map<String, String> CALL = Map.of("int", "readInt()", "str", "readLine()", "ints", "readInts()",
            "words", "readWords()", "rows", "readRows()", "tree", "readTree()");
    private static final Map<String, String> PY_CALL = Map.of("int", "_int()", "str", "_line()", "ints", "_ints()",
            "words", "_words()", "rows", "_rows()", "tree", "_tree()");
    private static final Map<String, String> JAVA_ARG = Map.of("int", "int", "str", "String", "ints", "int[]", "words", "String[]",
            "rows", "int[][]", "tree", "Integer[]");
    private static final Map<String, String> JAVA_RET = Map.of("int", "int", "long", "long", "double", "double", "bool", "boolean",
            "string", "String", "int[]", "int[]", "string[]", "String[]", "int[][]", "int[][]", "string[][]", "String[][]");
    private static final Map<String, String> JAVA_ZERO = Map.of("int", "0", "long", "0", "double", "0", "bool", "false", "string", "\"\"",
            "int[]", "new int[0]", "string[]", "new String[0]", "int[][]", "new int[0][]", "string[][]", "new String[0][]");
    private static final Map<String, String> CPP_ARG = Map.of("int", "int", "str", "string", "ints", "vector<int>",
            "words", "vector<string>", "rows", "vector<vector<int>>", "tree", "vector<int>");
    private static final Map<String, String> CPP_RET = Map.of("int", "int", "long", "long long", "double", "double", "bool", "bool",
            "string", "string", "int[]", "vector<int>", "string[]", "vector<string>", "int[][]", "vector<vector<int>>",
            "string[][]", "vector<vector<string>>");
    private static final Map<String, String> CPP_ZERO = Map.of("int", "0", "long", "0", "double", "0", "bool", "false", "string", "\"\"",
            "int[]", "{}", "string[]", "{}", "int[][]", "{}", "string[][]", "{}");

    private CodeGroundStarters() {}

    public static List<String> inputFormat(List<Arg> args) {
        return args.stream().map(a -> a.name() + ": " + TEXT.get(a.type())).toList();
    }

    public static Map<String, String> starters(List<Arg> args, String returnType) {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("javascript", javascript(args));
        all.put("python", python(args));
        all.put("java", java(args, returnType));
        all.put("cpp", cpp(args, returnType));
        return all;
    }

    /** Where solve() sits in a starter: from its signature line to its last line. */
    private static int[] solveLines(String language, List<String> lines) {
        int i = 0;
        while (i < lines.size() && !lines.get(i).contains("Write your code here")) i++;
        return new int[] {i - 1, language.equals("python") ? i + 1 : i + 2};
    }

    /** The empty solve() of a starter, without the class indent: what an admin fills in as the reference solution. */
    public static String solveTemplate(String language, String starter) {
        List<String> lines = List.of(starter.split("\n", -1));
        int[] at = solveLines(language, lines);
        return lines.subList(at[0], at[1] + 1).stream()
                .map(l -> language.equals("java") && l.startsWith("    ") ? l.substring(4) : l).collect(Collectors.joining("\n"));
    }

    /** The starter with its empty solve() replaced by a solution (which may bring helper functions). */
    public static String withSolution(String language, String starter, String solution) {
        List<String> lines = List.of(starter.split("\n", -1));
        int[] at = solveLines(language, lines);
        String indent = language.equals("java") ? "    " : "";
        List<String> out = new ArrayList<>(lines.subList(0, at[0]));
        for (String l : solution.replace("\r\n", "\n").stripTrailing().split("\n", -1)) out.add(l.isEmpty() ? l : indent + l);
        out.addAll(lines.subList(at[1] + 1, lines.size()));
        return String.join("\n", out);
    }

    private static String names(List<Arg> args) {
        return args.stream().map(Arg::name).collect(Collectors.joining(", "));
    }

    private static String each(List<Arg> args, Function<Arg, String> line) {
        return args.stream().map(line).collect(Collectors.joining("\n"));
    }

    private static String javascript(List<Arg> args) {
        Set<String> need = args.stream().map(Arg::type).collect(Collectors.toCollection(HashSet::new));
        if (need.contains("rows")) need.addAll(List.of("int", "ints"));
        if (need.contains("tree")) need.add("words");
        Map<String, String> readers = Map.of(
                "int", "const readInt = () => Number(readLine());",
                "ints", "const readInts = () => readLine().trim().split(/\\s+/).filter(Boolean).map(Number);",
                "words", "const readWords = () => readLine().trim().split(/\\s+/).filter(Boolean);",
                "rows", "const readRows = () => Array.from({ length: readInt() }, readInts);",
                "tree", "const readTree = () => readWords().map(w => (w === 'null' ? null : Number(w)));");
        List<String> body = new ArrayList<>(List.of(
                "const lines = require('fs').readFileSync(0, 'utf8').replace(/\\r/g, '').split('\\n');",
                "let at = 0;",
                "const readLine = () => lines[at++] || '';"));
        for (String k : List.of("int", "ints", "words", "rows", "tree")) if (need.contains(k)) body.add(readers.get(k));
        for (Arg a : args) body.add("const " + a.name() + " = " + CALL.get(a.type()) + ";");
        body.add("const answer = solve(" + names(args) + ");");
        body.add("console.log('\\n" + MARK + "');");
        body.add("console.log(Array.isArray(answer) ? (answer.some(Array.isArray) ? answer.map(r => r.join(' ')).join('\\n') : answer.join(' ')) : String(answer));");
        return "function solve(" + names(args) + ") {\n  // Write your code here and return the answer\n  \n}\n\n// ----- " + FOOTER + " -----\n"
                + String.join("\n", body) + "\n";
    }

    private static String python(List<Arg> args) {
        return "def solve(" + names(args) + "):\n    # Write your code here and return the answer\n    pass\n\n\n# ----- " + FOOTER + " -----\n"
                + """
                import sys
                _lines = sys.stdin.read().replace('\\r', '').split('\\n')
                _at = 0


                def _line():
                    global _at
                    s = _lines[_at] if _at < len(_lines) else ''
                    _at += 1
                    return s


                def _int():
                    s = _line().strip()
                    return int(s) if s else 0


                def _ints():
                    return [int(x) for x in _line().split()]


                def _words():
                    return _line().split()


                def _rows():
                    return [_ints() for _ in range(_int())]


                def _tree():
                    return [None if w == 'null' else int(w) for w in _words()]


                def _fmt(v):
                    if isinstance(v, bool):
                        return 'true' if v else 'false'
                    if isinstance(v, float):
                        return str(int(v)) if v == int(v) and abs(v) < 1e15 else repr(v)
                    return str(v)


                def _show(v):
                    if isinstance(v, (list, tuple)):
                        if any(isinstance(x, (list, tuple)) for x in v):
                            return '\\n'.join(' '.join(_fmt(x) for x in row) for row in v)
                        return ' '.join(_fmt(x) for x in v)
                    return _fmt(v)


                """
                + each(args, a -> a.name() + " = " + PY_CALL.get(a.type())) + "\n"
                + "_answer = solve(" + names(args) + ")\nprint('\\n" + MARK + "')\nprint(_show(_answer))\n";
    }

    private static String java(List<Arg> args, String ret) {
        String params = args.stream().map(a -> JAVA_ARG.get(a.type()) + " " + a.name()).collect(Collectors.joining(", "));
        return "import java.util.*;\nimport java.io.*;\n\npublic class Main {\n\n    static " + JAVA_RET.get(ret) + " solve(" + params + ") {\n"
                + "        // Write your code here and return the answer\n        return " + JAVA_ZERO.get(ret) + ";\n    }\n\n    // ----- " + FOOTER + " -----\n"
                + "    static List<String> lines = new ArrayList<>();\n    static int at = 0;\n\n"
                + "    public static void main(String[] args) throws IOException {\n"
                + "        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));\n"
                + "        for (String l = in.readLine(); l != null; l = in.readLine()) lines.add(l);\n"
                + each(args, a -> "        " + JAVA_ARG.get(a.type()) + " " + a.name() + " = " + CALL.get(a.type()) + ";") + "\n"
                + "        " + JAVA_RET.get(ret) + " output_ = solve(" + names(args) + ");\n"
                + "        System.out.println(\"\\n" + MARK + "\");\n"
                + "        System.out.println(show(output_));\n    }\n\n"
                + """
                    static String readLine() { return at < lines.size() ? lines.get(at++) : ""; }
                    static int readInt() { String s = readLine().trim(); return s.isEmpty() ? 0 : Integer.parseInt(s); }
                    static String[] readWords() { String s = readLine().trim(); return s.isEmpty() ? new String[0] : s.split("\\\\s+"); }
                    static int[] readInts() { String[] w = readWords(); int[] a = new int[w.length]; for (int i = 0; i < w.length; i++) a[i] = Integer.parseInt(w[i]); return a; }
                    static int[][] readRows() { int k = readInt(); int[][] r = new int[k][]; for (int i = 0; i < k; i++) r[i] = readInts(); return r; }
                    static Integer[] readTree() { String[] w = readWords(); Integer[] t = new Integer[w.length]; for (int i = 0; i < w.length; i++) t[i] = w[i].equals("null") ? null : Integer.valueOf(w[i]); return t; }

                    static String show(int v) { return String.valueOf(v); }
                    static String show(long v) { return String.valueOf(v); }
                    static String show(boolean v) { return String.valueOf(v); }
                    static String show(String v) { return String.valueOf(v); }
                    static String show(double v) { return v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString(); }
                    static String show(int[] v) { StringBuilder b = new StringBuilder(); for (int i = 0; i < v.length; i++) b.append(i > 0 ? " " : "").append(v[i]); return b.toString(); }
                    static String show(String[] v) { return String.join(" ", v); }
                    static String show(int[][] v) { StringBuilder b = new StringBuilder(); for (int i = 0; i < v.length; i++) b.append(i > 0 ? "\\n" : "").append(show(v[i])); return b.toString(); }
                    static String show(String[][] v) { StringBuilder b = new StringBuilder(); for (int i = 0; i < v.length; i++) b.append(i > 0 ? "\\n" : "").append(show(v[i])); return b.toString(); }
                """
                + "}\n";
    }

    private static String cpp(List<Arg> args, String ret) {
        String params = args.stream().map(a -> CPP_ARG.get(a.type()) + " " + a.name()).collect(Collectors.joining(", "));
        boolean tree = args.stream().anyMatch(a -> a.type().equals("tree"));
        return "#include <bits/stdc++.h>\nusing namespace std;\n" + (tree ? "\nconst int NONE = INT_MIN; // a missing node in the tree\n" : "") + "\n"
                + CPP_RET.get(ret) + " solve(" + params + ") {\n    // Write your code here and return the answer\n    return " + CPP_ZERO.get(ret) + ";\n}\n\n"
                + "// ----- " + FOOTER + " -----\n"
                + """
                vector<string> LINES;
                size_t AT = 0;
                string readLine() { return AT < LINES.size() ? LINES[AT++] : string(); }
                int readInt() { stringstream s(readLine()); int v = 0; s >> v; return v; }
                vector<string> readWords() { stringstream s(readLine()); vector<string> v; string w; while (s >> w) v.push_back(w); return v; }
                vector<int> readInts() { vector<int> v; for (const string& w : readWords()) v.push_back(stoi(w)); return v; }
                vector<vector<int>> readRows() { int k = readInt(); vector<vector<int>> r; for (int i = 0; i < k; i++) r.push_back(readInts()); return r; }
                vector<int> readTree() { vector<int> t; for (const string& w : readWords()) t.push_back(w == "null" ? INT_MIN : stoi(w)); return t; }

                string show(int v) { return to_string(v); }
                string show(long long v) { return to_string(v); }
                string show(bool v) { return v ? "true" : "false"; }
                string show(const string& v) { return v; }
                string show(double v) { ostringstream o; o << setprecision(15) << v; return o.str(); }
                template <class T> string show(const vector<T>& v) {
                    string out;
                    for (size_t i = 0; i < v.size(); i++) out += (i ? " " : "") + show(v[i]);
                    return out;
                }
                template <class T> string show(const vector<vector<T>>& v) {
                    string out;
                    for (size_t i = 0; i < v.size(); i++) out += (i ? "\\n" : "") + show(v[i]);
                    return out;
                }

                int main() {
                    for (string l; getline(cin, l);) { if (!l.empty() && l.back() == '\\r') l.pop_back(); LINES.push_back(l); }
                """
                + each(args, a -> "    " + CPP_ARG.get(a.type()) + " " + a.name() + " = " + CALL.get(a.type()) + ";") + "\n"
                + "    " + CPP_RET.get(ret) + " output_ = solve(" + names(args) + ");\n"
                + "    fflush(stdout); // what you printed with printf comes first\n"
                + "    cout << \"\\n" + MARK + "\\n\" << show(output_) << endl;\n    return 0;\n}\n";
    }
}
