package com.joblens.api.error;

import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import java.net.URI;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds the one response body shape the API uses for every failure (RFC 9457 problem details,
 * extended with {@code code}, {@code recoveryAction}, {@code fieldErrors} and {@code traceId}).
 *
 * <p>{@code traceId} is a correlation handle only. It is safe to show to users and safe to log,
 * and it is never derived from request content.
 */
@Component
public class ProblemDetailFactory {

    public ProblemDetail create(ErrorCode errorCode, String detail, List<ApiException.FieldError> fieldErrors,
            String traceId) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problem.setType(URI.create(errorCode.type()));
        problem.setTitle(errorCode.title());
        problem.setProperty("code", errorCode.name());
        problem.setProperty("recoveryAction", errorCode.recoveryAction());
        problem.setProperty("fieldErrors", fieldErrors);
        problem.setProperty("traceId", traceId);
        return problem;
    }
}
