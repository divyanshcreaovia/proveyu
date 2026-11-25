package com.proveyu.student.prep.application;

import com.proveyu.shared.error.DomainException;
import org.springframework.http.HttpStatus;

/**
 * The code runner failed for a reason that is not the student's fault: judge busy, unreachable, timed out, bad token,
 * or an internal judge error. The message is safe to show the student; the details go to the developer log.
 * On Submit this is not recorded as a wrong answer: the student is asked to try again.
 */
public class CodeRunnerUnavailableException extends DomainException {

    public enum Reason { BUSY, UNREACHABLE, TIMEOUT, AUTH, INTERNAL }

    private final Reason reason;

    public CodeRunnerUnavailableException(Reason reason) {
        super(messageFor(reason), HttpStatus.SERVICE_UNAVAILABLE, "RUNNER_" + reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    private static String messageFor(Reason reason) {
        return switch (reason) {
            case BUSY -> "The code judge is busy. Try again in a few seconds.";
            case TIMEOUT -> "The code judge is taking too long. Try again in a moment.";
            case UNREACHABLE, AUTH, INTERNAL -> "The code judge is not available right now. Please try again in a few minutes.";
        };
    }
}
