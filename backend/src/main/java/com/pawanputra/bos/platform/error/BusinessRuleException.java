package com.pawanputra.bos.platform.error;

/** The request is well-formed but not allowed by a domain rule. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
