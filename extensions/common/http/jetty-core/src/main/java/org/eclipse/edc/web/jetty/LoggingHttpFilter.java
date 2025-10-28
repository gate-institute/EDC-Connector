/*
 *  Copyright (c) 2025 GATE Institute
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       GATE Institute
 *
 */

package org.eclipse.edc.web.jetty;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;
import java.util.regex.Pattern;

public class LoggingHttpFilter implements Filter {
    private final Monitor monitor;
    private final Pattern pattern;

    public LoggingHttpFilter(Monitor monitor, String loggingFilterPattern) {
        super();
        this.monitor = monitor;
        this.pattern = Pattern.compile(loggingFilterPattern);

        monitor.info("Logging incoming HTTP requests matching path filter pattern: %s".formatted(pattern.toString()));
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String requestUri = request.getRequestURI();
        boolean shouldLog = pattern.matcher(requestUri).matches();

        if (!shouldLog) {
            chain.doFilter(request, response);
            return;
        }

        String requestId = request.getHeader("request-id");

        // 1. log incoming request’s "request-id" header
        if (requestId == null) {
            requestId = "";
        }

        monitor.info("[%s] Received HTTP request %s %s".formatted(
                requestId,
                request.getMethod(),
                request.getRequestURI()
        ));

        // 2. set outgoing response’s "request-id" header to that of the input
        if (requestId != null) {
            response.setHeader("request-id", requestId);
        }

        // proceed with the next filter / servlet
        chain.doFilter(request, response);

        // 3. log outgoing response’s "request-id" header
        monitor.debug("[%s] Sending HTTP response %d".formatted(
                requestId,
                response.getStatus()
        ));
    }
}
