package com.proveyu.student.codeground;

import com.proveyu.student.codeground.CodeGroundContent.Arg;
import com.proveyu.student.codeground.CodeGroundContent.Slide;
import com.proveyu.student.codeground.CodeGroundContent.Spec;
import com.proveyu.student.prep.domain.PrepModel.IoCase;

import java.util.*;
import java.util.regex.Pattern;

/** Checks what an admin writes before it is stored. Every message says what to fix. */
final class CodeGroundValidator {

    private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9-]{1,58}[a-z0-9]");
    private static final Pattern NAME = Pattern.compile("[a-z][a-zA-Z0-9]{0,20}");
    private static final Pattern INT = Pattern.compile("-?\\d{1,10}");
    private static final Set<String> LEVELS = Set.of("EASY", "MEDIUM", "HARD");
    private static final Set<String> LANGUAGES = Set.of("javascript", "python", "java", "cpp");
    private static final int MAX_CASES = 10;
    private static final int MAX_IO = 10_000;

    /** Keywords of the four languages, and names the input / print code already uses. */
    private static final Set<String> RESERVED = Set.of((
            "abstract and args arguments array as assert async at auto await bool boolean break byte case catch char class compl const continue"
            + " debugger def default del delete do double elif else enum eval except explicit export extends extern false final finally float for"
            + " friend from function global goto if implements import in inline instanceof int interface is lambda let lines long main map mutable"
            + " namespace native new nonlocal not null nullptr of operator or package pass private protected public raise register require return"
            + " set short show signed sizeof solve static strictfp string struct super switch synchronized sys template this throw throws transient"
            + " true try typedef typeid typename typeof union unsigned using var vector virtual void volatile wchar while with yield list dict str"
            + " len max min sum sorted input print answer console process queue stack count find sort swap abs readLine readInt readInts readWords"
            + " readRows readTree output").split(" "));

    private CodeGroundValidator() {}

    static List<String> topic(String key, String title, String summary, List<Slide> guide) {
        List<String> errors = new ArrayList<>();
        key(errors, "Topic key", key);
        length(errors, "Topic title", title, 3, 120);
        length(errors, "Topic summary", summary, 3, 300);
        if (guide != null) {
            if (guide.size() > 20) errors.add("A guide has at most 20 slides");
            for (int i = 0; i < guide.size(); i++) {
                Slide s = guide.get(i);
                if (s == null || blank(s.tag()) || blank(s.title()) || s.points() == null) {
                    errors.add("Guide slide " + (i + 1) + " needs tag, title and points");
                } else if (s.code() != null && !LANGUAGES.containsAll(s.code().keySet())) {
                    errors.add("Guide slide " + (i + 1) + ": code languages are javascript, python, java, cpp");
                }
            }
        }
        return errors;
    }

    static List<String> problem(Spec s) {
        List<String> errors = new ArrayList<>();
        if (s == null) return List.of("The problem is empty");
        key(errors, "Problem key", s.key());
        key(errors, "Topic key", s.topicKey());
        length(errors, "Title", s.title(), 3, 120);
        if (s.difficulty() == null || !LEVELS.contains(s.difficulty())) errors.add("Difficulty is EASY, MEDIUM or HARD");
        length(errors, "Statement", s.statement(), 10, 4000);
        length(errors, "Hint", s.hint(), 3, 500);
        if (s.note() != null && s.note().length() > 500) errors.add("Note is at most 500 characters");

        List<Arg> args = s.args() == null ? List.of() : s.args();
        boolean argsOk = !args.isEmpty() && args.size() <= 5;
        if (!argsOk) errors.add("solve() takes 1 to 5 parameters");
        Set<String> names = new HashSet<>();
        for (Arg a : args) {
            if (a == null || a.name() == null || !NAME.matcher(a.name()).matches()) {
                errors.add("Parameter names are letters and digits, starting with a small letter");
                argsOk = false;
            } else if (RESERVED.contains(a.name())) {
                errors.add("Parameter name '" + a.name() + "' is reserved in one of the languages; choose another");
                argsOk = false;
            } else if (!names.add(a.name())) {
                errors.add("Parameter name '" + a.name() + "' is used twice");
                argsOk = false;
            }
            if (a == null || a.type() == null || !CodeGroundStarters.ARG_TYPES.contains(a.type())) {
                errors.add("Parameter types are " + String.join(", ", CodeGroundStarters.ARG_TYPES));
                argsOk = false;
            }
        }
        if (s.returnType() == null || !CodeGroundStarters.RETURN_TYPES.contains(s.returnType())) {
            errors.add("Return type is one of " + String.join(", ", CodeGroundStarters.RETURN_TYPES));
        }

        List<Arg> checked = argsOk ? args : null;
        cases(errors, "Test", s.tests(), checked);
        cases(errors, "Hidden test", s.hiddenTests(), checked);
        if (s.tests() != null && s.hiddenTests() != null) {
            Set<String> visible = new HashSet<>();
            s.tests().forEach(c -> visible.add(String.valueOf(c.input()).strip()));
            if (s.hiddenTests().stream().anyMatch(c -> visible.contains(String.valueOf(c.input()).strip()))) {
                errors.add("A hidden test repeats a visible test's input");
            }
        }
        if (s.solution() == null || blank(s.solution().code())) errors.add("Give a reference solution; it is used to check the tests");
        else if (!LANGUAGES.contains(s.solution().language())) errors.add("The solution language is javascript, python, java or cpp");
        else if (!s.solution().code().contains("solve")) errors.add("The reference solution must define solve()");
        else if (s.solution().code().length() > 20_000) errors.add("The reference solution is too long");
        return errors;
    }

    private static void cases(List<String> errors, String what, List<IoCase> cases, List<Arg> args) {
        if (cases == null || cases.size() < 2 || cases.size() > MAX_CASES) {
            errors.add("Give 2 to " + MAX_CASES + " " + what.toLowerCase() + "s");
            if (cases == null) return;
        }
        for (int i = 0; i < Math.min(cases.size(), MAX_CASES); i++) {
            IoCase c = cases.get(i);
            String name = what + " " + (i + 1);
            if (c == null || c.input() == null || blank(c.output())) {
                errors.add(name + " needs an input and an expected output");
            } else if (c.input().length() > MAX_IO || c.output().length() > MAX_IO) {
                errors.add(name + " is longer than " + MAX_IO + " characters");
            } else if (args != null) {
                String problem = inputProblem(args, c.input());
                if (problem != null) errors.add(name + ": " + problem);
            }
        }
    }

    /** Reads the input the way the starters do; null when it fits the parameters. */
    static String inputProblem(List<Arg> args, String input) {
        List<String> lines = new ArrayList<>(List.of(input.replace("\r\n", "\n").split("\n", -1)));
        int at = 0;
        for (Arg a : args) {
            String line = at < lines.size() ? lines.get(at) : "";
            at++;
            switch (a.type()) {
                case "int" -> {
                    if (!isInt(line.strip())) return "'" + a.name() + "' needs one whole number on its line";
                }
                case "ints" -> {
                    if (!tokens(line).stream().allMatch(CodeGroundValidator::isInt)) return "'" + a.name() + "' needs numbers separated by spaces";
                }
                case "tree" -> {
                    if (!tokens(line).stream().allMatch(t -> t.equals("null") || isInt(t))) return "'" + a.name() + "' needs numbers or null";
                }
                case "rows" -> {
                    if (!isInt(line.strip()) || Long.parseLong(line.strip()) < 0 || Long.parseLong(line.strip()) > 10_000) {
                        return "'" + a.name() + "' starts with the number of rows on its own line";
                    }
                    for (int r = Integer.parseInt(line.strip()); r > 0; r--, at++) {
                        if (at >= lines.size() || !tokens(lines.get(at)).stream().allMatch(CodeGroundValidator::isInt)) {
                            return "'" + a.name() + "' has fewer rows of numbers than it says";
                        }
                    }
                }
                default -> { /* str and words accept any line */ }
            }
        }
        for (int i = at; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) return "there are more lines than the parameters read";
        }
        return null;
    }

    private static List<String> tokens(String line) {
        return line.isBlank() ? List.of() : List.of(line.strip().split("\\s+"));
    }

    private static boolean isInt(String t) {
        if (!INT.matcher(t).matches()) return false;
        long v = Long.parseLong(t);
        return v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE;
    }

    private static void key(List<String> errors, String what, String key) {
        if (key == null || !KEY.matcher(key).matches()) errors.add(what + " is 3-60 small letters, digits and hyphens, e.g. two-sum");
    }

    private static void length(List<String> errors, String what, String value, int min, int max) {
        if (value == null || value.strip().length() < min || value.length() > max) errors.add(what + " needs " + min + " to " + max + " characters");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
