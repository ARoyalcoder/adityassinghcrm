package com.pawanputra.bos.platform.error;

/** The request is valid but clashes with the current state of the resource (duplicate, stale version). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
