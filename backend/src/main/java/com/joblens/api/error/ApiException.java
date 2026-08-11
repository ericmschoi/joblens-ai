package com.joblens.api.error;

import java.util.List;

/**
 * The single exception type the API layer translates into an RFC 9457 problem detail.
 *
 * <p>The {@code detail} carried here is user-facing English copy. Never place exception messages
 * from parsers, HTTP clients or the file system into it; log those separately instead.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;
    private final List<FieldError> fieldErrors;

    public ApiException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, List.of(), null);
    }

    public ApiException(ErrorCode errorCode, String detail, Throwable cause) {
        this(errorCode, detail, List.of(), cause);
    }

    public ApiException(ErrorCode errorCode, String detail, List<FieldError> fieldErrors, Throwable cause) {
        super(errorCode.name(), cause);
        this.errorCode = errorCode;
        this.detail = detail;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String detail() {
        return detail;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    /** A single field-level validation problem. {@code message} is user-facing English copy. */
    public record FieldError(String field, String message) {}
}
