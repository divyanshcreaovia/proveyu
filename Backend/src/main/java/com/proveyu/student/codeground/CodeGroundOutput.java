package com.proveyu.student.codeground;

import com.proveyu.student.prep.application.CodeExecutionService;

/** Reads what the judge printed: the answer, apart from what the program printed while debugging. */
final class CodeGroundOutput {

    private static final String ANSWER_MARK = CodeGroundStarters.MARK + "\n";

    record Parts(String actual, String printed) {}

    private CodeGroundOutput() {}

    /** Without a marker, a clean run's whole output is the answer. */
    static Parts split(CodeExecutionService.Output o) {
        String out = o.stdout() == null ? "" : o.stdout().replace("\r\n", "\n");
        int at = out.lastIndexOf(ANSWER_MARK);
        if (at >= 0) {
            String printed = out.substring(0, at);
            if (printed.endsWith("\n")) printed = printed.substring(0, printed.length() - 1); // the starter's own line break
            return new Parts(out.substring(at + ANSWER_MARK.length()), printed);
        }
        return o.exitCode() == 0 && !o.timedOut() ? new Parts(out, "") : new Parts("", out);
    }

    static boolean passed(CodeExecutionService.Output o, String expected) {
        return !o.timedOut() && o.exitCode() == 0 && CodeExecutionService.sameOutput(expected, split(o).actual());
    }
}
