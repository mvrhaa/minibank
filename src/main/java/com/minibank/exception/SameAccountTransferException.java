package com.minibank.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class SameAccountTransferException extends RuntimeException {

    public SameAccountTransferException() {
        super("Source and destination accounts must be different");
    }
}
