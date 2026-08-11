package com.joblens.api.error;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates every failure into the single problem-detail shape defined by {@link ProblemDetailFactory}.
 *
 * <p>Logging rule for the whole application: log the {@link ErrorCode} and the trace id, never the
 * request body, uploaded document, extracted text or any part of it. Unexpected failures are logged
 * with a stack trace because those originate in our own code, not in user content.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException ex) {
        String traceId = newTraceId();
        ErrorCode code = ex.errorCode();
        if (code.status().is5xxServerError()) {
            LOG.error("code={} traceId={} status={}", code, traceId, code.status().value(), ex);
        } else {
            LOG.warn("code={} traceId={} status={}", code, traceId, code.status().value());
        }
        ProblemDetail body = problems.create(code, ex.detail(), ex.fieldErrors(), traceId);
        return ResponseEntity.status(code.status()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        String traceId = newTraceId();
        LOG.error("code={} traceId={}", ErrorCode.INTERNAL_ERROR, traceId, ex);
        ProblemDetail body = problems.create(ErrorCode.INTERNAL_ERROR,
                "The request could not be completed.", List.of(), traceId);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String traceId = newTraceId();
        List<ApiException.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiException.FieldError(error.getField(),
                        error.getDefaultMessage() == null ? "This value is not valid." : error.getDefaultMessage()))
                .toList();
        LOG.warn("code={} traceId={} fieldCount={}", ErrorCode.VALIDATION_FAILED, traceId, fieldErrors.size());
        ProblemDetail body = problems.create(ErrorCode.VALIDATION_FAILED,
                "Some of the submitted values are not valid.", fieldErrors, traceId);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String traceId = newTraceId();
        LOG.warn("code={} traceId={}", ErrorCode.REQUEST_NOT_READABLE, traceId);
        ProblemDetail body = problems.create(ErrorCode.REQUEST_NOT_READABLE,
                "The request body could not be read.", List.of(), traceId);
        return ResponseEntity.status(ErrorCode.REQUEST_NOT_READABLE.status()).body(body);
    }

    /**
     * Normalises everything the framework itself rejects — unknown paths, wrong HTTP methods,
     * unacceptable media types — into the same problem-detail shape.
     *
     * <p>Without this, Spring's own message would reach the client (for example
     * {@code "No static resource api/v1/…"}), which is both off-contract and a small disclosure of
     * how requests are routed internally.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String traceId = newTraceId();
        ErrorCode code = mapFrameworkStatus(statusCode);
        if (statusCode.is5xxServerError()) {
            LOG.error("code={} traceId={} status={}", code, traceId, statusCode.value(), ex);
        } else {
            LOG.warn("code={} traceId={} status={}", code, traceId, statusCode.value());
        }
        ProblemDetail problem = problems.create(code, frameworkDetail(code), List.of(), traceId);
        return ResponseEntity.status(statusCode).headers(headers).body(problem);
    }

    private static ErrorCode mapFrameworkStatus(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> statusCode.is4xxClientError() ? ErrorCode.REQUEST_INVALID : ErrorCode.INTERNAL_ERROR;
        };
    }

    private static String frameworkDetail(ErrorCode code) {
        return switch (code) {
            case RESOURCE_NOT_FOUND -> "That address does not exist.";
            case METHOD_NOT_ALLOWED -> "That request is not supported at this address.";
            case UNSUPPORTED_MEDIA_TYPE -> "That request format is not supported.";
            case REQUEST_INVALID -> "The request could not be processed as sent.";
            default -> "The request could not be completed.";
        };
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
