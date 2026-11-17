"""Technical MCQs: generated pseudocode / output / data-structure / SQL questions (answers computed by running the logic in
Python), plus a hand-written bank of CS fundamentals."""
from common import mcq, near, Skip, fixed


# ------------------------------------------------------------------ generated

def pseudo_loop(rng, lv):
    a, b, step = rng.randint(1, 5), rng.randint(8, 20), rng.choice([1, 2, 3])
    op = rng.choice(["sum", "sumsq", "evens", "prod"] if lv > 1 else ["sum", "evens"])
    lines, total = [], 0
    if op == "sum":
        total = sum(range(a, b + 1, step)); body = "s = s + i"
    elif op == "sumsq":
        total = sum(i * i for i in range(a, b + 1, step)); body = "s = s + i * i"
    elif op == "evens":
        total = sum(1 for i in range(a, b + 1, step) if i % 2 == 0); body = "if i mod 2 == 0 then s = s + 1"
    else:
        b = a + rng.randint(3, 5); total = 1
        for i in range(a, b + 1, step):
            total *= i
        body = "s = s * i"
    init = 1 if op == "prod" else 0
    code = f"s = {init}\nfor i = {a} to {b} step {step}\n    {body}\nend for\nprint s"
    return mcq(rng, f"What is the output of this pseudocode?\n\n{code}", total, near(rng, total, max(3, total // 8)),
               f"The loop takes i = {', '.join(map(str, range(a, b + 1, step)))}; the result is {total}.")


def pseudo_nested(rng, lv):
    n = rng.randint(3, 7)
    kind = rng.choice(["full", "tri", "strict"])
    if kind == "full":
        cnt = n * n; cond = f"for j = 1 to {n}"
    elif kind == "tri":
        cnt = n * (n + 1) // 2; cond = "for j = 1 to i"
    else:
        cnt = n * (n - 1) // 2; cond = f"for j = i + 1 to {n}"
    code = f"count = 0\nfor i = 1 to {n}\n    {cond}\n        count = count + 1\n    end for\nend for\nprint count"
    return mcq(rng, f"What is printed?\n\n{code}", cnt, [n * n, n * (n + 1) // 2, n * (n - 1) // 2, cnt + n, 2 * n],
               f"Counting the inner iterations gives {cnt}.")


def pseudo_while(rng, lv):
    n = rng.randint(100, 9999)
    kind = rng.choice(["digits", "rev", "sumd"])
    if kind == "digits":
        ans = len(str(n)); body = "c = c + 1\n    n = n div 10"; init, out = "c = 0", "c"
    elif kind == "rev":
        ans = int(str(n)[::-1]); body = "r = r * 10 + (n mod 10)\n    n = n div 10"; init, out = "r = 0", "r"
    else:
        ans = sum(map(int, str(n))); body = "s = s + (n mod 10)\n    n = n div 10"; init, out = "s = 0", "s"
    code = f"n = {n}\n{init}\nwhile n > 0\n    {body}\nend while\nprint {out}"
    return mcq(rng, f"What is the output? (div = integer division)\n\n{code}", ans,
               near(rng, ans, max(2, ans // 10)) + [n, int(str(n)[::-1]) if kind != "rev" else n], "Trace the loop digit by digit.")


def pseudo_recursion(rng, lv):
    kind = rng.choice(["fact", "fib", "pow", "sumto"])
    n = rng.randint(4, 7)
    if kind == "fact":
        from math import factorial
        ans, f = factorial(n), "f(n) = 1 if n <= 1 else n * f(n - 1)"
    elif kind == "fib":
        a, b = 0, 1
        for _ in range(n):
            a, b = b, a + b
        ans, f = a, "f(n) = n if n < 2 else f(n - 1) + f(n - 2)"
    elif kind == "pow":
        base = rng.choice([2, 3])
        ans, f = base ** n, f"f(n) = 1 if n == 0 else {base} * f(n - 1)"
    else:
        ans, f = n * (n + 1) // 2, "f(n) = 0 if n == 0 else n + f(n - 1)"
    return mcq(rng, f"Given the function {f}, what is f({n})?", ans, near(rng, ans, max(3, ans // 6)), f"Expanding the recursion gives f({n}) = {ans}.")


def bitwise(rng, lv):
    a, b = rng.randint(5, 60), rng.randint(3, 40)
    op = rng.choice(["&", "|", "^", "<<", ">>"])
    if op in ("<<", ">>"):
        b = rng.randint(1, 3)
    ans = {"&": a & b, "|": a | b, "^": a ^ b, "<<": a << b, ">>": a >> b}[op]
    return mcq(rng, f"What is the value of {a} {op} {b} (bitwise, on integers)?", ans, near(rng, ans, max(3, ans // 4)) + [a & b, a | b, a ^ b],
               f"{a} = {a:b}₂, {b} = {b:b}₂ → result {ans:b}₂ = {ans}.")


def base_convert(rng, lv):
    n = rng.randint(20, 255)
    kind = rng.choice(["bin2dec", "dec2bin", "hex2dec", "dec2hex"] if lv > 1 else ["bin2dec", "dec2bin"])
    if kind == "bin2dec":
        return mcq(rng, f"What is the decimal value of the binary number {n:b}?", n, near(rng, n, 8), f"Sum of powers of 2: {n}.")
    if kind == "dec2bin":
        wrong = [f"{x:b}" for x in near(rng, n, 6)]
        return mcq(rng, f"What is {n} in binary?", f"{n:b}", wrong, f"{n} = {n:b}₂.")
    if kind == "hex2dec":
        return mcq(rng, f"What is the decimal value of hexadecimal {n:X}?", n, near(rng, n, 10), f"{n:X}₁₆ = {n}.")
    return mcq(rng, f"What is {n} in hexadecimal?", f"{n:X}", [f"{x:X}" for x in near(rng, n, 12)], f"{n} = {n:X}₁₆.")


def c_output(rng, lv):
    kind = rng.choice(["post", "div", "ternary", "shortcircuit"])
    if kind == "post":
        x = rng.randint(2, 9)
        y = x * 2 + 1
        ans = f"{y} {x + 1}"
        code = f"int x = {x};\nint y = x++ * 2 + 1;\nprintf(\"%d %d\", y, x);"
        wrong = [f"{(x + 1) * 2 + 1} {x + 1}", f"{y} {x}", f"{(x + 1) * 2 + 1} {x}"]
        expl = f"x++ uses {x} and then increments x: y = {y}, x = {x + 1}."
    elif kind == "div":
        a, b = rng.randint(7, 40), rng.randint(2, 6)
        ans = f"{a // b} {a % b}"
        code = f"int a = {a}, b = {b};\nprintf(\"%d %d\", a / b, a % b);"
        wrong = [f"{a / b:.1f} {a % b}", f"{a // b + 1} {a % b}", f"{a % b} {a // b}"]
        expl = f"Integer division: {a}/{b} = {a // b}, remainder {a % b}."
    elif kind == "ternary":
        a, b = rng.sample(range(1, 50), 2)
        ans = str(max(a, b) * 2)
        code = f"int a = {a}, b = {b};\nint m = (a > b) ? a : b;\nprintf(\"%d\", m * 2);"
        wrong = [str(min(a, b) * 2), str(a + b), str(max(a, b))]
        expl = f"m = max({a}, {b}) = {max(a, b)}; m * 2 = {ans}."
    else:
        a = rng.randint(0, 1)
        b = rng.randint(1, 9)
        res = 1 if (a and b > 0) else 0
        code = f"int a = {a}, b = {b};\nif (a && ++b) {{ }}\nprintf(\"%d\", b);"
        ans = str(b + 1 if a else b)
        wrong = [str(b + 1 if not a else b), str(b + 2), "0"]
        expl = ("&& stops at a false left side, so ++b never runs." if not a else "a is true, so ++b runs.") + f" b = {ans}."
    return mcq(rng, f"What does this C code print?\n\n{code}", ans, wrong, expl)


def stack_queue(rng, lv):
    ops, st, q = [], [], []
    vals = rng.sample(range(1, 50), 6)
    vi = 0
    for _ in range(rng.randint(6, 9)):
        if (rng.random() < 0.6 or not st) and vi < len(vals):
            st.append(vals[vi]); q.append(vals[vi]); ops.append(f"push({vals[vi]})"); vi += 1
        else:
            st.pop(); q.pop(0); ops.append("pop()")
    if not st:
        raise Skip
    use_q = rng.random() < 0.5
    name = "queue (push = enqueue, pop = dequeue)" if use_q else "stack"
    ans = (q[0] if use_q else st[-1])
    return mcq(rng, f"Starting with an empty {name}, these operations run: {', '.join(ops)}. What is at the {'front' if use_q else 'top'} now?",
               ans, [x for x in vals if x != ans], f"Simulating the operations, the {'front' if use_q else 'top'} element is {ans}.")


def bst_inorder(rng, lv):
    keys = rng.sample(range(1, 99), rng.randint(6, 8))
    kind = rng.choice(["pre", "height"] if lv > 1 else ["min_path"])
    root = None
    nodes = {}

    def insert(k):
        nonlocal root
        if root is None:
            root = k; nodes[k] = [None, None]; return
        cur = root
        while True:
            side = 0 if k < cur else 1
            if nodes[cur][side] is None:
                nodes[cur][side] = k; nodes[k] = [None, None]; return
            cur = nodes[cur][side]
    for k in keys:
        insert(k)

    def pre(n):
        return [] if n is None else [n] + pre(nodes[n][0]) + pre(nodes[n][1])

    def height(n):
        return 0 if n is None else 1 + max(height(nodes[n][0]), height(nodes[n][1]))
    seq = ", ".join(map(str, keys))
    if kind == "pre":
        ans = " ".join(map(str, pre(root)))
        wrong = [" ".join(map(str, sorted(keys))), " ".join(map(str, keys)), " ".join(map(str, sorted(keys, reverse=True)))]
        return mcq(rng, f"Keys are inserted into an empty binary search tree in this order: {seq}. What is the preorder traversal?", ans, wrong,
                   "Build the BST, then visit root, left subtree, right subtree.")
    if kind == "height":
        h = height(root)
        return mcq(rng, f"Keys are inserted into an empty BST in this order: {seq}. What is the height of the tree (number of nodes on the longest root-to-leaf path)?",
                   h, [x for x in range(2, 9) if x != h], f"The longest path has {h} nodes.")
    smallest = min(keys)
    return mcq(rng, f"Keys {seq} are inserted into an empty binary search tree. Which key is reached by always going left from the root?",
               smallest, [k for k in keys if k != smallest], "Always going left reaches the minimum key.")


def complexity(rng, lv):
    items = [
        ("for i = 1 to n\n    for j = 1 to n\n        x = x + 1", "O(n²)"),
        ("i = 1\nwhile i < n\n    i = i * 2", "O(log n)"),
        ("for i = 1 to n\n    j = 1\n    while j < n\n        j = j * 2", "O(n log n)"),
        ("for i = 1 to n\n    x = x + i", "O(n)"),
        ("for i = 1 to n\n    for j = 1 to 100\n        x = x + 1", "O(n)"),
        ("for i = 1 to n\n    for j = i to n\n        for k = 1 to n\n            x = x + 1", "O(n³)"),
        ("i = n\nwhile i > 0\n    i = i div 3", "O(log n)"),
    ]
    code, ans = rng.choice(items)
    return mcq(rng, f"What is the time complexity of this code?\n\n{code}", ans, ["O(n)", "O(n²)", "O(log n)", "O(n log n)", "O(n³)", "O(1)"],
               f"Count how many times the innermost statement runs: {ans}.")


def sql_count(rng, lv):
    depts = ["HR", "IT", "Sales"]
    names = rng.sample(["Amit", "Bina", "Chetan", "Divya", "Ekta", "Faiz", "Gaurav", "Hina", "Irfan", "Jaya"], 7)
    rows = [(i + 1, names[i], rng.choice(depts), rng.choice(range(30, 91, 5)) * 1000) for i in range(7)]
    table = "Employee(id, name, dept, salary)\n" + "\n".join(f"{r[0]} | {r[1]} | {r[2]} | {r[3]}" for r in rows)
    kind = rng.choice(["count", "groupmax", "where_avg"])
    if kind == "count":
        t = rng.choice(range(40, 81, 10)) * 1000
        ans = sum(1 for r in rows if r[3] > t)
        q = f"SELECT COUNT(*) FROM Employee WHERE salary > {t};"
        wrong = [ans + 1, ans - 1 if ans > 0 else ans + 2, len(rows), ans + 2]
    elif kind == "groupmax":
        d = rng.choice(depts)
        ds = [r for r in rows if r[2] == d]
        if not ds:
            raise Skip
        ans = max(r[3] for r in ds)
        q = f"SELECT MAX(salary) FROM Employee WHERE dept = '{d}';"
        wrong = [max(r[3] for r in rows), min(r[3] for r in ds), sum(r[3] for r in ds)]
    else:
        g = {}
        for r in rows:
            g.setdefault(r[2], 0)
            g[r[2]] += 1
        ans = sum(1 for v in g.values() if v >= 2)
        q = "SELECT dept FROM Employee GROUP BY dept HAVING COUNT(*) >= 2;  -- how many rows does this return?"
        wrong = [len(g), 0, 1, 2, 3]
    return mcq(rng, f"Table:\n{table}\n\nWhat does this query return?\n{q}", ans, [w for w in wrong if w != ans], "Evaluate the query row by row on the table shown.")


GENERATORS = [pseudo_loop, pseudo_nested, pseudo_while, pseudo_recursion, bitwise, base_convert, c_output, stack_queue, bst_inorder,
              complexity, sql_count]
PSEUDO = [pseudo_loop, pseudo_nested, pseudo_while, pseudo_recursion, c_output, bitwise]

# ------------------------------------------------------------------ hand-written CS fundamentals (question, correct, 3 wrong, why)
CS = [
    ("Which OOP concept lets a subclass provide its own version of a method defined in the parent class?", "Method overriding", "Method overloading", "Encapsulation", "Abstraction", "Overriding replaces the inherited behaviour."),
    ("Wrapping data and the methods that use it into one unit, hiding internal details, is called", "Encapsulation", "Inheritance", "Polymorphism", "Compilation", "Encapsulation bundles state and behaviour."),
    ("Which is NOT a pillar of object-oriented programming?", "Compilation", "Inheritance", "Polymorphism", "Abstraction", "The four pillars are encapsulation, abstraction, inheritance and polymorphism."),
    ("In Java, which keyword is used to inherit from a class?", "extends", "implements", "inherits", "super", "Classes use extends; interfaces use implements."),
    ("An abstract class in Java", "cannot be instantiated directly", "cannot have constructors", "must have only abstract methods", "cannot be extended", "You create objects only of its concrete subclasses."),
    ("Two methods in the same class with the same name but different parameters is an example of", "Method overloading", "Method overriding", "Inheritance", "Encapsulation", "Overloading = same name, different parameter lists."),
    ("Which access modifier makes a member visible only inside its own class (Java)?", "private", "protected", "public", "default", "private restricts access to the declaring class."),
    ("What does the ‘static’ keyword on a Java method mean?", "It belongs to the class, not to an object", "It cannot be changed", "It runs in a separate thread", "It is private", "Static members are shared at class level."),
    ("A constructor in Java", "has the same name as the class and no return type", "must return void", "is called only once per program", "cannot take parameters", "Constructors initialise new objects."),
    ("Which principle says a class should have only one reason to change?", "Single Responsibility Principle", "Open/Closed Principle", "Liskov Substitution", "Dependency Inversion", "S in SOLID."),
    ("Which normal form removes partial dependencies on a composite key?", "2NF", "1NF", "3NF", "BCNF", "2NF requires full functional dependency on the whole key."),
    ("A PRIMARY KEY column", "must be unique and not null", "can contain duplicates", "can be null once", "must be a number", "Primary keys identify rows uniquely."),
    ("Which SQL clause filters groups after aggregation?", "HAVING", "WHERE", "ORDER BY", "LIMIT", "WHERE filters rows; HAVING filters groups."),
    ("Which join returns all rows from the left table and matching rows from the right?", "LEFT JOIN", "INNER JOIN", "RIGHT JOIN", "CROSS JOIN", "Unmatched right-side columns are NULL."),
    ("ACID properties of transactions are", "Atomicity, Consistency, Isolation, Durability", "Accuracy, Concurrency, Integrity, Durability", "Atomicity, Concurrency, Isolation, Dependency", "Availability, Consistency, Isolation, Durability", "Standard transaction guarantees."),
    ("Which SQL command removes all rows but keeps the table structure, and is usually faster than DELETE?", "TRUNCATE", "DROP", "REMOVE", "ALTER", "TRUNCATE empties the table; DROP deletes the table itself."),
    ("A foreign key", "refers to the primary key of another table", "must be unique", "cannot be null in any case", "is always an integer", "It links rows across tables."),
    ("Which is a DDL command?", "CREATE", "SELECT", "INSERT", "UPDATE", "DDL defines structure: CREATE, ALTER, DROP."),
    ("An index on a column mainly helps to", "speed up searches on that column", "save disk space", "enforce foreign keys", "back up the table", "Indexes trade write cost and space for faster reads."),
    ("Which isolation problem occurs when a transaction reads uncommitted changes of another?", "Dirty read", "Phantom read", "Deadlock", "Lost index", "Reading uncommitted data is a dirty read."),
    ("What is a deadlock?", "Processes waiting forever for resources held by each other", "A process that finishes early", "Memory that cannot be freed", "A crashed disk", "Circular wait among processes."),
    ("Which scheduling algorithm can cause starvation of long jobs?", "Shortest Job First", "Round Robin", "First Come First Served", "Multilevel with ageing", "Short jobs keep jumping ahead."),
    ("Virtual memory allows", "programs larger than physical RAM to run", "faster CPU clock speed", "more CPU cores", "unlimited disk space", "Pages are moved between RAM and disk."),
    ("Thrashing happens when", "the system spends most time swapping pages", "the CPU overheats", "a disk is full", "too few processes run", "Too many page faults."),
    ("A semaphore is used for", "process synchronisation", "memory allocation", "disk formatting", "network routing", "It controls access to shared resources."),
    ("Which is NOT a necessary condition for deadlock?", "Preemption", "Mutual exclusion", "Hold and wait", "Circular wait", "Deadlock needs NO preemption."),
    ("The difference between a process and a thread is that threads", "share the memory of their process", "always run on different machines", "cannot run in parallel", "have separate address spaces", "Threads of one process share its address space."),
    ("Paging divides memory into", "fixed-size blocks", "variable-size segments", "files", "registers", "Pages and frames have a fixed size."),
    ("A context switch is", "saving one process's state and loading another's", "changing the user", "switching the network", "restarting the OS", "The CPU moves between processes."),
    ("Which layer of the OSI model handles routing between networks?", "Network layer", "Transport layer", "Data link layer", "Session layer", "Layer 3 routes packets."),
    ("TCP differs from UDP because TCP is", "connection-oriented and reliable", "faster with no handshake", "used only for video", "a network layer protocol", "TCP guarantees ordered delivery."),
    ("The default port for HTTPS is", "443", "80", "21", "25", "HTTP uses 80, HTTPS 443."),
    ("DNS is used to", "translate domain names into IP addresses", "encrypt web traffic", "assign MAC addresses", "send e-mails", "Name to address resolution."),
    ("How many bits are in an IPv4 address?", "32", "64", "128", "16", "IPv6 uses 128 bits."),
    ("Which device forwards frames using MAC addresses?", "Switch", "Router", "Hub", "Modem", "Switches work at layer 2."),
    ("Which protocol automatically assigns IP addresses to devices?", "DHCP", "DNS", "FTP", "ARP", "Dynamic Host Configuration Protocol."),
    ("ARP is used to find", "the MAC address for an IP address", "the IP for a domain", "the fastest route", "open ports", "Address Resolution Protocol."),
    ("Which data structure works on LIFO order?", "Stack", "Queue", "Linked list", "Heap", "Last in, first out."),
    ("Searching a balanced binary search tree takes", "O(log n)", "O(n)", "O(1)", "O(n²)", "Height of a balanced BST is log n."),
    ("Which sorting algorithm has worst-case O(n log n) time?", "Merge sort", "Quick sort", "Bubble sort", "Insertion sort", "Quick sort's worst case is O(n²)."),
    ("Accessing an array element by index takes", "O(1)", "O(n)", "O(log n)", "O(n log n)", "Index arithmetic gives direct access."),
    ("Which data structure is best for a breadth-first search?", "Queue", "Stack", "Heap", "Hash set only", "BFS processes nodes in FIFO order."),
    ("A hash table gives average-case lookup of", "O(1)", "O(log n)", "O(n)", "O(n²)", "With a good hash and load factor."),
    ("Which traversal of a BST gives keys in sorted order?", "Inorder", "Preorder", "Postorder", "Level order", "Left, root, right."),
    ("Dijkstra's algorithm does not work correctly with", "negative edge weights", "undirected graphs", "weighted graphs", "cycles", "It assumes non-negative weights."),
    ("A min-heap always keeps", "the smallest element at the root", "elements sorted in an array", "the largest element at the root", "leaves at the same depth only", "Heap property."),
    ("Detecting a cycle in a linked list in O(1) extra space uses", "two pointers moving at different speeds", "a hash set", "sorting the list", "recursion", "Floyd's tortoise and hare."),
    ("Which language feature lets Python handle runtime errors gracefully?", "try / except", "for / else", "lambda", "yield", "Exceptions are caught with try/except."),
    ("In Python, which of these is immutable?", "tuple", "list", "dict", "set", "Tuples cannot be changed after creation."),
    ("What does ‘len([1, [2, 3], 4])’ return in Python?", "3", "4", "2", "Error", "The nested list counts as one element."),
    ("Java code is compiled into", "bytecode that runs on the JVM", "machine code for one CPU only", "Python scripts", "HTML", "Write once, run anywhere."),
    ("Which collection in Java does not allow duplicate elements?", "Set", "List", "ArrayList", "Vector", "Sets store unique elements."),
    ("Garbage collection in Java", "frees memory of objects no longer referenced", "deletes unused files", "removes unused classes from source", "clears the screen", "Automatic memory management."),
    ("Git command to copy a remote repository to your machine:", "git clone", "git push", "git commit", "git merge", "clone downloads the repository."),
    ("In Agile, a short fixed period in which a team delivers work is a", "sprint", "milestone", "waterfall", "backlog", "Usually 1 to 4 weeks."),
    ("Which testing checks individual functions in isolation?", "Unit testing", "System testing", "Acceptance testing", "Load testing", "Smallest testable parts."),
    ("Cloud model where you rent virtual machines and manage the OS yourself:", "IaaS", "SaaS", "PaaS", "FaaS", "Infrastructure as a Service."),
    ("Gmail is an example of", "SaaS", "IaaS", "PaaS", "On-premise software", "Software delivered over the internet."),
    ("REST APIs commonly use which HTTP method to create a resource?", "POST", "GET", "DELETE", "HEAD", "POST creates; GET reads."),
    ("HTTP status 404 means", "Not Found", "Server Error", "Unauthorized", "OK", "The resource does not exist."),
    ("In MS Excel, which function adds a range of cells?", "SUM", "ADD", "TOTAL", "COUNT", "=SUM(A1:A10)."),
    ("In MS Excel, which function counts cells that meet a condition?", "COUNTIF", "COUNT", "SUMIF", "IFCOUNT", "=COUNTIF(range, criteria)."),
    ("In MS Word, Ctrl + Z", "undoes the last action", "saves the document", "closes Word", "zooms in", "Undo."),
    ("Which shortcut copies selected text on Windows?", "Ctrl + C", "Ctrl + V", "Ctrl + X", "Ctrl + P", "C for copy; V pastes."),
    ("In PowerPoint, the view that shows small thumbnails of all slides for reordering is", "Slide Sorter", "Reading View", "Outline Only", "Notes Page", "Slide Sorter shows all slides."),
    ("Which of these is an input device?", "Scanner", "Monitor", "Printer", "Speaker", "Scanners send data into the computer."),
    ("1 KB is equal to", "1024 bytes", "1000 bits", "1024 bits", "100 bytes", "Binary prefix: 2¹⁰ bytes."),
    ("RAM is", "volatile memory", "permanent storage", "a type of ROM", "an output device", "Contents are lost when power is off."),
    ("Which is an example of an operating system?", "Linux", "Oracle Database", "Chrome", "MS Excel", "Linux manages hardware and processes."),
    ("Phishing is", "tricking users into revealing sensitive information", "a type of firewall", "a backup method", "a programming language", "Fake mails or sites that steal data."),
    ("A firewall", "filters network traffic by rules", "speeds up the CPU", "stores passwords", "compresses files", "Allows or blocks traffic."),
    ("Which of these is a symmetric encryption algorithm?", "AES", "RSA", "SHA-256", "ECDSA", "AES uses one shared key; SHA is a hash."),
    ("Big-O notation describes", "how running time grows with input size", "exact running time in seconds", "memory address size", "the number of programmers needed", "Asymptotic growth."),
    ("Recursion without a base case usually leads to", "stack overflow", "faster execution", "compile-time error always", "an infinite file", "Calls never stop."),
    ("Which of these is NOT a valid variable name in C?", "2total", "total2", "_total", "totalSum", "Names cannot start with a digit."),
    ("In C, which operator gives the address of a variable?", "&", "*", "->", "%", "* dereferences; & takes the address."),
    ("In C, the size of ‘char’ is", "1 byte", "2 bytes", "4 bytes", "depends on the compiler always 8 bytes", "char is defined as one byte."),
    ("Which loop in C always runs its body at least once?", "do-while", "while", "for", "none", "The condition is checked after the body."),
    ("Which header file is needed for printf in C?", "stdio.h", "stdlib.h", "string.h", "math.h", "Standard input/output."),
    ("A pointer that points to memory that has been freed is called a", "dangling pointer", "null pointer", "void pointer", "wild card", "Using it is undefined behaviour."),
    ("Microservices architecture means", "an app built as small independently deployable services", "one large deployable unit", "only front-end code", "a database type", "Each service owns one capability."),
    ("A load balancer", "spreads requests across several servers", "stores session data permanently", "compiles code", "encrypts databases", "Improves availability and throughput."),
    ("Caching mainly improves", "response time for repeated requests", "data accuracy", "disk capacity", "code readability", "Serve repeated reads from fast storage."),
    ("CAP theorem says a distributed system cannot fully guarantee all three of", "Consistency, Availability, Partition tolerance", "Cost, Accuracy, Performance", "Caching, Atomicity, Persistence", "Concurrency, Access, Privacy", "During a partition, choose C or A."),
    ("Which is a NoSQL database?", "MongoDB", "MySQL", "PostgreSQL", "Oracle Database", "MongoDB stores documents."),
    ("Idempotent HTTP method (same effect if repeated):", "PUT", "POST", "PATCH (always)", "CONNECT", "PUT replaces a resource to the same state."),
    ("Which design pattern ensures a class has only one instance?", "Singleton", "Factory", "Observer", "Adapter", "Singleton controls instantiation."),
    ("Which design pattern notifies dependents when an object changes state?", "Observer", "Builder", "Proxy", "Prototype", "Publish–subscribe style."),
    ("Binary search requires the array to be", "sorted", "of even length", "unsorted", "stored in a linked list", "It halves a sorted range each step."),
    ("The worst-case time of searching an unsorted array is", "O(n)", "O(log n)", "O(1)", "O(n log n)", "You may have to check every element."),
    ("Which traversal uses a stack (explicitly or via recursion)?", "Depth-first search", "Breadth-first search", "Level-order traversal", "Dijkstra with a queue", "DFS goes deep first."),
    ("Dynamic programming is useful when a problem has", "overlapping subproblems and optimal substructure", "no repeated work", "only one solution", "random inputs", "Store and reuse subproblem results."),
    ("A greedy algorithm", "makes the locally best choice at each step", "tries every possibility", "always uses recursion", "needs sorted input always", "Works when local choices lead to a global optimum."),
]


def cs_pool(rng):
    out = []
    for q, c, a, b, d, why in CS:
        out.append(fixed(q, [c, a, b, d], 0, why, rng))
    rng.shuffle(out)
    return out
