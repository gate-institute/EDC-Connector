/*
 *  Copyright (c) 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - Initial implementation
 *
 */

package org.eclipse.edc.connector.core.base;

import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.regex.Pattern;
import javax.net.SocketFactory;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;

public class OkHttpClientFactory {

    /**
     * Create an OkHttpClient instance
     *
     * @param configuration       the configuration
     * @param okHttpEventListener used to instrument OkHttp client for collecting metrics, can be null
     * @param monitor             the monitor
     * @return the OkHttpClient
     */
    @NotNull
    public static OkHttpClient create(OkHttpClientConfiguration configuration, EventListener okHttpEventListener, Monitor monitor) {
        var builder = new OkHttpClient.Builder()
                .connectTimeout(configuration.getConnectTimeout(), SECONDS)
                .readTimeout(configuration.getReadTimeout(), SECONDS);

        if (configuration.getSendBufferSize() > 0 || configuration.getReceiveBufferSize() > 0) {
            builder.socketFactory(new CustomSocketFactory(configuration.getSendBufferSize(), configuration.getReceiveBufferSize()));
        }

        ofNullable(okHttpEventListener).ifPresent(builder::eventListener);

        String ua = configuration.getUserAgent();
        if (!ua.isEmpty()) {
            monitor.info("Adding UserAgentInterceptor HTTP interceptor with value: " + ua);
            builder.addInterceptor(new UserAgentInterceptor(ua));
        }

        monitor.info("Adding CloseConnection HTTP interceptor");
        builder.addInterceptor(new CloseConnection());

        monitor.info("Adding RequestIdInterceptor");
        builder.addInterceptor(new RequestIdInterceptor());

        monitor.info("Adding LoggingInterceptor");
        builder.addInterceptor(new LoggingInterceptor(monitor, configuration.getLoggingFilterPattern()));

        if (configuration.isEnforceHttps()) {
            builder.addInterceptor(new EnforceHttps());
        } else {
            monitor.info("HTTPS enforcement it not enabled, please enable it in a production environment");
        }

        return builder.build();
    }

    private static class UserAgentInterceptor implements Interceptor {
        private String value;

        UserAgentInterceptor(String value) {
            super();
            this.value = value;
        }

        @NotNull
        @Override
        public Response intercept(@NotNull Chain chain) throws IOException {
            Request req = chain.request()
                    .newBuilder()
                    .header("user-agent", this.value)
                    .build();
            return chain.proceed(req);
        }
    }

    private static class CloseConnection implements Interceptor {
        @NotNull
        @Override
        public Response intercept(@NotNull Chain chain) throws IOException {
            Request req = chain.request()
                    .newBuilder()
                    .header("Connection", "close")
                    .build();
            return chain.proceed(req);
        }
    }

    public static class RequestIdInterceptor implements Interceptor {
        private static final String HEADER_NAME = "request-id";
        private static final char[] ALPHANUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
        private static final SecureRandom RNG = new SecureRandom();
        private static final int ID_LENGTH = 10;

        @Override
        public Response intercept(Chain chain) throws IOException {
            String requestId = generateRequestId();
            Request newReq = chain.request().newBuilder()
                    .header(HEADER_NAME, requestId)
                    .build();

            return chain.proceed(newReq);
        }

        private String generateRequestId() {
            char[] buf = new char[ID_LENGTH];
            for (int i = 0; i < ID_LENGTH; i++) {
                buf[i] = ALPHANUM[RNG.nextInt(ALPHANUM.length)];
            }
            return new String(buf);
        }
    }

    public static class LoggingInterceptor implements Interceptor {
        private static final String HEADER_NAME = "request-id";
        private Monitor monitor;
        private Pattern pattern;

        public LoggingInterceptor(Monitor monitor, String loggingFilterPattern) {
            super();
            this.monitor = monitor;
            this.pattern = Pattern.compile(loggingFilterPattern);
            monitor.info("Logging outgoing HTTP requests matching path filter pattern: %s".formatted(pattern.toString()));
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            // grab the request-id (or use "unknown" if it's not present)
            String requestId = request.header(HEADER_NAME);
            if (requestId == null) {
                requestId = "unknown";
            }

            HttpUrl url = request.url();
            String path = url.uri().getPath();
            boolean shouldLog = pattern.matcher(path).matches();

            if (shouldLog) {
                monitor.info("[%s] Making HTTP request %s %s%n".formatted(
                        requestId,
                        request.method(),
                        request.url()
                ));
            }

            Response response = chain.proceed(request);

            if (shouldLog) {
                monitor.debug("[%s] Received HTTP response %d".formatted(
                        requestId,
                        response.code()
                ));
            }

            return response;
        }
    }

    private static class EnforceHttps implements Interceptor {
        @NotNull
        @Override
        public Response intercept(@NotNull Chain chain) throws IOException {
            var request = chain.request();
            if (!request.isHttps()) {
                throw new EdcException(format("HTTP call to %s blocked due to HTTPS enforcement enabled", request.url()));
            }
            return chain.proceed(request);
        }
    }

    private static class CustomSocketFactory extends SocketFactory {

        private final int sendBufferSize;
        private final int receiveBufferSize;

        CustomSocketFactory(int sendBufferSize, int receiveBufferSize) {
            this.sendBufferSize = sendBufferSize;
            this.receiveBufferSize = receiveBufferSize;
        }

        @Override
        public Socket createSocket() throws IOException {
            return updateSendBufferSize(new Socket());
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return updateSendBufferSize(new Socket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return updateSendBufferSize(new Socket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return updateSendBufferSize(new Socket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return updateSendBufferSize(new Socket(address, port, localAddress, localPort));
        }

        private Socket updateSendBufferSize(Socket socket) throws IOException {
            if (receiveBufferSize > 0) {
                socket.setReceiveBufferSize(receiveBufferSize);
            }
            if (sendBufferSize > 0) {
                socket.setSendBufferSize(sendBufferSize);
            }
            return socket;
        }
    }
}
