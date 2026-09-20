package com.proveyu.shared.error;

import org.springframework.http.HttpStatus;

public class SlotNotFoundException extends DomainException {
    public SlotNotFoundException() {
        super("Requested slot was not found.", HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND");
    }
}
