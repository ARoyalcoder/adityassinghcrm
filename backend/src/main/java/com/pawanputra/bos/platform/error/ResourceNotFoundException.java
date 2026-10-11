package com.pawanputra.bos.platform.error;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "%s '%s' was not found".formatted(resource, identifier));
    }
}
