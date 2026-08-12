package com.joblens.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The headers every API response carries, applied in one place so none can be forgotten.
 *
 * <p>{@code no-store} is the one that matters most here. A resume and a job posting are personal
 * documents that this product deliberately never persists, and a response holding extracted resume
 * text has no business sitting in a browser disk cache or an intermediary after the tab is closed.
 * The rest close off the ways a JSON endpoint can be turned into something else: sniffed into a
 * different content type, framed, or used as a document that loads subresources.
 */
@Component
@Order(ResponseHeadersFilter.ORDER)
public class ResponseHeadersFilter extends OncePerRequestFilter {

    /** Runs early so the headers are on the response whatever produces the body, errors included. */
    static final int ORDER = -100;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Frame-Options", "DENY");
        // The API serves JSON only: it never needs to load anything, and nothing may frame it.
        response.setHeader("Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");

        chain.doFilter(request, response);
    }
}
