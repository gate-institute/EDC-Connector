/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - improvements
 *
 */

package org.eclipse.edc.web.jersey.mapper;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.ApiErrorDetail;
import org.eclipse.edc.web.spi.exception.AuthenticationFailedException;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.eclipse.edc.web.spi.exception.EdcApiException;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.eclipse.edc.web.spi.exception.NotAuthorizedException;
import org.eclipse.edc.web.spi.exception.ObjectConflictException;
import org.eclipse.edc.web.spi.exception.ObjectNotFoundException;
import org.eclipse.edc.web.spi.exception.ValidationFailureException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static jakarta.ws.rs.core.Response.Status.BAD_GATEWAY;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;

/**
 * Exception mapper that catches all the `EdcApiException` exceptions, map them to a 4xx response code with a detailed response body
 */
public class EdcApiExceptionMapper implements ExceptionMapper<EdcApiException> {
    private final Monitor monitor;
    private final Map<Class<? extends EdcApiException>, Response.Status> exceptionMap;

    public EdcApiExceptionMapper(Monitor monitor) {
        this.monitor = monitor;
        exceptionMap = Map.of(
                AuthenticationFailedException.class, UNAUTHORIZED,
                NotAuthorizedException.class, FORBIDDEN,
                InvalidRequestException.class, BAD_REQUEST,
                ObjectNotFoundException.class, NOT_FOUND,
                ObjectConflictException.class, CONFLICT,
                BadGatewayException.class, BAD_GATEWAY,
                ValidationFailureException.class, BAD_REQUEST
        );
    }

    @Override
    public Response toResponse(EdcApiException exception) {
        var status = exceptionMap.getOrDefault(exception.getClass(), INTERNAL_SERVER_ERROR);

        Stream<ApiErrorDetail> errorDetails;
        if (exception instanceof ValidationFailureException validationFailure) {
            errorDetails = validationFailure.getViolations().stream()
                    .map(violation -> ApiErrorDetail.Builder.newInstance()
                            .message(violation.message())
                            .path(violation.path())
                            .value(violation.value())
                            .type(exception.getType())
                            .build());
        } else {
            errorDetails = exception.getMessages().stream()
                    .map(message -> ApiErrorDetail.Builder.newInstance()
                            .message(message)
                            .type(exception.getType())
                            .build()
                    );
        }

        List<ApiErrorDetail> list = errorDetails.toList();
        String logErrors = IntStream.range(0, list.size())
                .mapToObj(i -> String.format("  [%d] %s", i, list.get(i).getMessage()))
                .collect(Collectors.joining("\n", "API Errors:\n", ""));
        monitor.warning(logErrors);

        return Response.status(status)
                .entity(list)
                .build();
    }
}
