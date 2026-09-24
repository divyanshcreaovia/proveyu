package com.proveyu.shared.error;

import org.springframework.http.HttpStatus;

public class SlotFullException extends DomainException {
    public SlotFullException() {
        super("The requested date slot is fully booked.", HttpStatus.GONE, "SLOT_FULL");
    }
}
