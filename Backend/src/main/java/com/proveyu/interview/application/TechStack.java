package com.proveyu.interview.application;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Finds the technologies in a role, job description or skill list, and checks that an interview question stays inside them, so
 * a Java interview never gets a Python question. Works without the model: the model is told the stack, and every question it
 * writes is checked again here, since small models drift.
 *
 * <p>Each technology belongs to a family (Java, Python, JavaScript, ...). A question may mention anything from the families of
 * the detected technologies (Spring Boot is fine for a Java role); a technology from any other family makes it off-stack.
 * General topics (OOP, data structures, HTTP, Git, projects) are not technologies here and are always allowed.
 */
final class TechStack {
    private TechStack() {}

    record Tech(String name, String family, Pattern pattern) {}

    private static final List<Tech> CATALOG = new ArrayList<>();
    /** Families that come with another: a React role may be asked about HTML and CSS, an Android role about Java or Kotlin. */
    private static final Map<String, Set<String>> IMPLIES = Map.of(
            "javascript", Set.of("web"),
            "android", Set.of("java", "kotlin"),
            "dotnet", Set.of("web"),
            "python-data", Set.of("python"),
            "python", Set.of("python-data"));

    private static void add(String name, String family, String regex) {
        CATALOG.add(new Tech(name, family, Pattern.compile(regex, Pattern.CASE_INSENSITIVE)));
    }

    private static void addCaseSensitive(String name, String family, String regex) {
        CATALOG.add(new Tech(name, family, Pattern.compile(regex)));
    }

    static {
        // Java (the lookahead keeps "JavaScript" out)
        add("Java", "java", "\\bjava\\b(?!\\s*script)(?!-?script)");
        add("Spring Boot", "java", "\\bspring\\s*boot\\b");
        add("Spring", "java", "\\bspring\\b(?!\\s*boot)(?=\\s*(framework|mvc|security|data|cloud|,|/|\\)|and\\b|$))");
        add("Hibernate", "java", "\\bhibernate\\b");
        add("JPA", "java", "\\bjpa\\b");
        add("J2EE", "java", "\\b(j2ee|jakarta\\s*ee|java\\s*ee)\\b");
        add("Servlets", "java", "\\bservlets?\\b|\\bjsp\\b");
        add("Maven", "java", "\\bmaven\\b");
        add("JUnit", "java", "\\bjunit\\b");
        add("JVM", "java", "\\bjvm\\b");
        add("Kotlin", "kotlin", "\\bkotlin\\b");
        add("Android", "android", "\\bandroid\\b");
        add("Scala", "scala", "\\bscala\\b");
        // Python
        add("Python", "python", "\\bpython\\b");
        add("Django", "python", "\\bdjango\\b");
        add("Flask", "python", "\\bflask\\b");
        add("FastAPI", "python", "\\bfast\\s*api\\b");
        add("Pandas", "python-data", "\\bpandas\\b");
        add("NumPy", "python-data", "\\bnumpy\\b");
        add("scikit-learn", "python-data", "\\bscikit|\\bsklearn\\b");
        add("TensorFlow", "python-data", "\\btensorflow\\b");
        add("PyTorch", "python-data", "\\bpytorch\\b");
        // JavaScript and the web
        add("JavaScript", "javascript", "\\bjava\\s*-?script\\b|\\bES6\\b");
        add("TypeScript", "javascript", "\\btype\\s*script\\b");
        add("Node.js", "javascript", "\\bnode\\s*\\.?\\s*js\\b");  // plain "node" is a linked-list word
        add("Express", "javascript", "\\bexpress(\\.?js)\\b");
        addCaseSensitive("React", "javascript", "\\bReact(\\.?js)?\\b(?!\\s*Native)|\\breact\\.?js\\b");  // "react" is also a verb
        add("React Native", "javascript", "\\breact\\s*native\\b");
        add("Angular", "javascript", "\\bangular(js)?\\b");
        add("Vue", "javascript", "\\bvue(\\.?js)?\\b");
        add("Next.js", "javascript", "\\bnext\\.?js\\b");
        add("jQuery", "javascript", "\\bjquery\\b");
        add("HTML", "web", "\\bhtml5?\\b");
        add("CSS", "web", "\\bcss3?\\b|\\btailwind\\b|\\bbootstrap\\b");
        // C family and .NET
        add("C++", "cpp", "(?<![\\w])c\\s*\\+\\+|\\bcpp\\b|\\bstl\\b");
        add("C#", "dotnet", "(?<![\\w])c\\s*#|\\bc\\s*sharp\\b");
        add(".NET", "dotnet", "(?<![\\w])\\.net\\b|\\bdotnet\\b|\\basp\\.?net\\b");
        addCaseSensitive("C", "c", "(?<![\\w+#.])C(?![\\w+#])(?=\\s*(language|programming|,|/|and\\b|\\)|$))|\\bC language\\b|\\bembedded C\\b");
        // Other languages
        add("Go", "go", "\\bgolang\\b|\\bgo\\s*(lang|language|programming|developer)\\b");
        addCaseSensitive("Rust", "rust", "\\bRust\\b");
        add("Ruby", "ruby", "\\bruby\\b|\\brails\\b");
        add("PHP", "php", "\\bphp\\b|\\blaravel\\b");
        addCaseSensitive("Swift", "swift", "\\bSwift\\b|\\biOS\\b");
        add("R language", "r", "\\bR\\s*(language|programming)\\b|\\bR\\s*studio\\b");
        add("MATLAB", "matlab", "\\bmatlab\\b");
        // Data
        add("SQL", "database", "\\bsql\\b|\\bpl/?sql\\b|\\bt-?sql\\b");
        add("MySQL", "database", "\\bmysql\\b");
        add("PostgreSQL", "database", "\\bpostgre(s|sql)?\\b");
        add("Oracle DB", "database", "\\boracle\\b");
        add("MongoDB", "database", "\\bmongo(db)?\\b");
        add("Redis", "database", "\\bredis\\b");
        addCaseSensitive("Spark", "bigdata", "\\b(Apache )?Spark\\b|\\b[Pp]y[Ss]park\\b");  // "spark" is also a verb
        add("Hadoop", "bigdata", "\\bhadoop\\b");
        add("Kafka", "bigdata", "\\bkafka\\b");
        add("Power BI", "bi", "\\bpower\\s*bi\\b");
        add("Tableau", "bi", "\\btableau\\b");
        addCaseSensitive("Excel", "bi", "\\b(MS[- ]?)?Excel\\b");  // "excel" is also a verb
        // Cloud and DevOps
        add("AWS", "cloud", "\\baws\\b|\\bamazon web services\\b");
        add("Azure", "cloud", "\\bazure\\b");
        add("GCP", "cloud", "\\bgcp\\b|\\bgoogle cloud\\b");
        add("Docker", "devops", "\\bdocker\\b");
        add("Kubernetes", "devops", "\\bkubernetes\\b|\\bk8s\\b");
        add("Jenkins", "devops", "\\bjenkins\\b");
        add("Linux", "linux", "\\blinux\\b|\\bunix\\b|\\bshell scripting\\b|\\bbash\\b");
        // Testing and mobile cross-platform
        add("Selenium", "testing", "\\bselenium\\b");
        add("Flutter", "flutter", "\\bflutter\\b|\\bdart\\b");
    }

    /** Technologies mentioned in the texts, in catalog order, without repeats. */
    static List<String> detect(String... texts) {
        String all = String.join("\n", Arrays.stream(texts).filter(Objects::nonNull).toList());
        if (all.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (Tech t : CATALOG) if (t.pattern().matcher(all).find() && !out.contains(t.name())) out.add(t.name());
        return out;
    }

    /** The families a question may draw from, given the detected technologies (plus the families they imply). */
    static Set<String> families(Collection<String> techNames) {
        Set<String> out = new HashSet<>();
        for (Tech t : CATALOG) if (techNames.contains(t.name())) out.add(t.family());
        for (String f : new ArrayList<>(out)) out.addAll(IMPLIES.getOrDefault(f, Set.of()));
        return out;
    }

    /**
     * The first technology in the question that is outside the stack, or null when the question is fine. With no stack (nothing
     * detected) every question is fine.
     */
    static String offStack(String question, Collection<String> stack) {
        if (stack == null || stack.isEmpty() || question == null) return null;
        Set<String> allowed = families(stack);
        for (Tech t : CATALOG) {
            if (!allowed.contains(t.family()) && t.pattern().matcher(question).find()) return t.name();
        }
        return null;
    }
}
