package com.joblens.api;

import com.joblens.api.error.ProblemDetailFactory;
import com.joblens.config.JoblensProperties;
import com.joblens.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Caps the size of a JSON request body.
 *
 * <p>Uploads are already bounded by the multipart limits, but the confirmation and analysis
 * endpoints take JSON carrying whole documents, and an unbounded body is buffered and parsed before
 * any controller sees it. The declared length is checked first because that is free; a body sent
 * without a length (chunked) is counted as it is read, so the cap holds either way.
 */
@Component
@Order(RequestBodyLimitFilter.ORDER)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    /** After the response headers filter, before anything that reads a body. */
    static final int ORDER = ResponseHeadersFilter.ORDER + 1;

    private static final Logger LOG = LoggerFactory.getLogger(RequestBodyLimitFilter.class);

    private final long maxBytes;
    private final ProblemDetailFactory problems;
    private final ObjectMapper objectMapper;

    RequestBodyLimitFilter(JoblensProperties properties, ProblemDetailFactory problems,
            ObjectMapper objectMapper) {
        this.maxBytes = properties.api().maxJsonRequestBytes();
        this.problems = problems;
        this.objectMapper = objectMapper;
    }

    /** Signals the cap from inside the stream, where there is no way to return a status directly. */
    public static final class BodyTooLargeException extends IOException {

        private static final long serialVersionUID = 1L;

        BodyTooLargeException(long limit) {
            super("request body exceeded " + limit + " bytes");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        if (!isJson(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (request.getContentLengthLong() > maxBytes) {
            reject(response);
            return;
        }
        chain.doFilter(new LimitedBodyRequest(request, maxBytes), response);
    }

    private static boolean isJson(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        try {
            MediaType parsed = MediaType.parseMediaType(contentType);
            return parsed.isCompatibleWith(MediaType.APPLICATION_JSON)
                    || parsed.getSubtype().endsWith("+json");
        } catch (org.springframework.http.InvalidMediaTypeException e) {
            return false;
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        LOG.warn("code={} traceId={} limitBytes={}", ErrorCode.REQUEST_TOO_LARGE, traceId, maxBytes);

        ProblemDetail problem = problems.create(ErrorCode.REQUEST_TOO_LARGE,
                "The request body is larger than this API accepts.", List.of(), traceId);

        response.setStatus(ErrorCode.REQUEST_TOO_LARGE.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
        response.getOutputStream().flush();
    }

    /** Counts the body as the message converter reads it, so a chunked body cannot slip the cap. */
    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

        private final long limit;

        LimitedBodyRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {

                private long read;

                private int count(int bytes) throws IOException {
                    if (bytes > 0) {
                        read += bytes;
                        if (read > limit) {
                            throw new BodyTooLargeException(limit);
                        }
                    }
                    return bytes;
                }

                @Override
                public int read() throws IOException {
                    int value = delegate.read();
                    count(value < 0 ? -1 : 1);
                    return value;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    return count(delegate.read(buffer, offset, length));
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    delegate.setReadListener(listener);
                }
            };
        }
    }
}
