package com.proveyu.student.prep.application;

/** Judge0 status ids (CE) mapped to what the platform needs. Ids 1 and 2 mean "not finished yet": the poller keeps waiting. */
public enum Judge0Verdict {
    IN_QUEUE, PROCESSING, ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, COMPILATION_ERROR, RUNTIME_ERROR, INTERNAL_ERROR, EXEC_FORMAT_ERROR, UNKNOWN;

    public static Judge0Verdict of(int statusId) {
        return switch (statusId) {
            case 1 -> IN_QUEUE;
            case 2 -> PROCESSING;
            case 3 -> ACCEPTED;
            case 4 -> WRONG_ANSWER;
            case 5 -> TIME_LIMIT_EXCEEDED;
            case 6 -> COMPILATION_ERROR;
            case 7, 8, 9, 10, 11, 12 -> RUNTIME_ERROR;
            case 13 -> INTERNAL_ERROR;
            case 14 -> EXEC_FORMAT_ERROR;
            default -> UNKNOWN;
        };
    }

    /** True once Judge0 has finished the submission (status id greater than 2). */
    public static boolean isFinished(int statusId) {
        return statusId > 2;
    }
}
