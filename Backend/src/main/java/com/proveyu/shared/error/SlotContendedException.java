package com.proveyu.shared.error;

import org.springframework.http.HttpStatus;

public class SlotContendedException extends DomainException {
    public SlotContendedException() {
        super("High concurrency detected on this slot. Please retry.", HttpStatus.CONFLICT, "SLOT_CONTENDED_RETRY");
    }
}
