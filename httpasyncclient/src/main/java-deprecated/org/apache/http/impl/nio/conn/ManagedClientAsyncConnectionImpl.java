/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.http.impl.nio.conn;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSession;

import org.apache.http.HttpConnectionMetrics;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.RouteTracker;
import org.apache.http.impl.conn.ConnectionShutdownException;
import org.apache.http.nio.conn.ClientAsyncConnection;
import org.apache.http.nio.conn.ClientAsyncConnectionFactory;
import org.apache.http.nio.conn.ClientAsyncConnectionManager;
import org.apache.http.nio.conn.ManagedClientAsyncConnection;
import org.apache.http.nio.conn.scheme.AsyncScheme;
import org.apache.http.nio.conn.scheme.AsyncSchemeRegistry;
import org.apache.http.nio.conn.scheme.LayeringStrategy;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ssl.SSLIOSession;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

@Deprecated
class ManagedClientAsyncConnectionImpl implements ManagedClientAsyncConnection {

    private final ClientAsyncConnectionManager manager;
    private final ClientAsyncConnectionFactory connFactory;
    private volatile HttpPoolEntry poolEntry;
    private volatile boolean reusable;
    private volatile long duration;

    ManagedClientAsyncConnectionImpl(
            final ClientAsyncConnectionManager manager,
            final ClientAsyncConnectionFactory connFactory,
            final HttpPoolEntry poolEntry) {
        super();
        this.manager = manager;
        this.connFactory = connFactory;
        this.poolEntry = poolEntry;
        this.reusable = true;
        this.duration = Long.MAX_VALUE;
    }

    HttpPoolEntry getPoolEntry() {
        return this.poolEntry;
    }

    HttpPoolEntry detach() {
        final HttpPoolEntry local = this.poolEntry;
        this.poolEntry = null;
        return local;
    }

    public ClientAsyncConnectionManager getManager() {
        return this.manager;
    }

    private ClientAsyncConnection getConnection() {
        final HttpPoolEntry local = this.poolEntry;
        if (local == null) {
            return null;
        }
        final IOSession session = local.getConnection();
        return (ClientAsyncConnection) session.getAttribute(IOEventDispatch.CONNECTION_KEY);
    }

    private ClientAsyncConnection ensureConnection() {
        final HttpPoolEntry local = this.poolEntry;
        if (local == null) {
            throw new ConnectionShutdownException();
        }
        final IOSession session = local.getConnection();
        return (ClientAsyncConnection) session.getAttribute(IOEventDispatch.CONNECTION_KEY);
    }

    private HttpPoolEntry ensurePoolEntry() {
        final HttpPoolEntry local = this.poolEntry;
        if (local == null) {
            throw new ConnectionShutdownException();
        }
        return local;
    }

    @Override
    public void close() throws IOException {
        final ClientAsyncConnection conn = getConnection();
        if (conn != null) {
            conn.close();
        }
    }

    @Override
    public void shutdown() throws IOException {
        final ClientAsyncConnection conn = getConnection();
        if (conn != null) {
            conn.shutdown();
        }
    }

    @Override
    public boolean isOpen() {
        final ClientAsyncConnection conn = getConnection();
        if (conn != null) {
            return conn.isOpen();
        }
        return false;
    }

    @Override
    public boolean isStale() {
        return isOpen();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        final ClientAsyncConnection conn = ensureConnection();
        conn.setSocketTimeout(timeout);
    }

    @Override
    public int getSocketTimeout() {
        final ClientAsyncConnection conn = ensureConnection();
        return conn.getSocketTimeout();
    }

    @Override
    public HttpConnectionMetrics getMetrics() {
        final ClientAsyncConnection conn = ensureConnection();
        return conn.getMetrics();
    }

    @Override
    public InetAddress getLocalAddress() {
        final ClientAsyncConnection conn = ensureConnection();
        return conn.getLocalAddress();
    }

    @Override
    public int getLocalPort() {
        final ClientAsyncConnection conn = ensureConnection();
        return conn.getLocalPort();
    }

    @Override
    public InetAddress getRemoteAddress() {
        final ClientAsyncConnection conn = ensureConnection();
        return conn.getRemoteAddress();
    }

    @Override
    public int getRemotePort() {
        final ClientAsyncConnection conn = ensureConnection();
        return conn.getRemotePort();
    }

    @Override
    public int getStatus() {
        final ClientAsyncConnection conn = ensureConnection();
        return conn.getStatus();
    }

    @Override
    public HttpRequest getHttpRequest() {
        final ClientAsyncConnection conn = ensureConnection();
        return conn.getHttpRequest();
    }

    @Override
    public HttpResponse getHttpResponse() {
        final ClientAsyncConnection conn = ensureConnection();
        return conn.getHttpResponse();
    }

    @Override
    public HttpContext getContext() {
        final ClientAsyncConnection conn = ensureConnection();
        return conn.getContext();
    }

    @Override
    public void requestInput() {
        final ClientAsyncConnection conn = ensureConnection();
        conn.requestInput();
    }

    @Override
    public void suspendInput() {
        final ClientAsyncConnection conn = ensureConnection();
        conn.suspendInput();
    }

    @Override
    public void requestOutput() {
        final ClientAsyncConnection conn = ensureConnection();
        conn.requestOutput();
    }

    @Override
    public void suspendOutput() {
        final ClientAsyncConnection conn = ensureConnection();
        conn.suspendOutput();
    }

    @Override
    public void submitRequest(final HttpRequest request) throws IOException, HttpException {
        final ClientAsyncConnection conn = ensureConnection();
        conn.submitRequest(request);
    }

    @Override
    public boolean isRequestSubmitted() {
        final ClientAsyncConnection conn = ensureConnection();
        return conn.isRequestSubmitted();
    }

    @Override
    public void resetOutput() {
        final ClientAsyncConnection conn = ensureConnection();
        conn.resetOutput();
    }

    @Override
    public void resetInput() {
        final ClientAsyncConnection conn = ensureConnection();
        conn.resetInput();
    }

    @Override
    public boolean isSecure() {
        final ClientAsyncConnection conn = ensureConnection();
        return conn.getIOSession() instanceof SSLIOSession;
    }

    @Override
    public HttpRoute getRoute() {
        final HttpPoolEntry entry = ensurePoolEntry();
        return entry.getEffectiveRoute();
    }

    @Override
    public SSLSession getSSLSession() {
        final ClientAsyncConnection conn = ensureConnection();
        final IOSession iosession = conn.getIOSession();
        if (iosession instanceof SSLIOSession) {
            return ((SSLIOSession) iosession).getSSLSession();
        }
        return null;
    }

    @Override
    public Object getState() {
        final HttpPoolEntry entry = ensurePoolEntry();
        return entry.getState();
    }

    @Override
    public void setState(final Object state) {
        final HttpPoolEntry entry = ensurePoolEntry();
        entry.setState(state);
    }

    @Override
    public void markReusable() {
        this.reusable = true;
    }

    @Override
    public void unmarkReusable() {
        this.reusable = false;
    }

    @Override
    public boolean isMarkedReusable() {
        return this.reusable;
    }

    @Override
    public void setIdleDuration(final long duration, final TimeUnit unit) {
        if(duration > 0) {
            this.duration = unit.toMillis(duration);
        } else {
            this.duration = -1;
        }
    }

    private AsyncSchemeRegistry getSchemeRegistry(final HttpContext context) {
        AsyncSchemeRegistry reg = (AsyncSchemeRegistry) context.getAttribute(
                ClientContext.SCHEME_REGISTRY);
        if (reg == null) {
            reg = this.manager.getSchemeRegistry();
        }
        return reg;
    }

    @Override
    public synchronized void open(
            final HttpRoute route,
            final HttpContext context,
            final HttpParams params) throws IOException {
        final HttpPoolEntry entry = ensurePoolEntry();
        final RouteTracker tracker = entry.getTracker();
        if (tracker.isConnected()) {
            throw new IllegalStateException("Connection already open");
        }

        final HttpHost target = route.getTargetHost();
        final HttpHost proxy = route.getProxyHost();
        IOSession iosession = entry.getConnection();

        if (proxy == null) {
            final AsyncScheme scheme = getSchemeRegistry(context).getScheme(target);
            final LayeringStrategy layeringStrategy = scheme.getLayeringStrategy();
            if (layeringStrategy != null) {
                iosession = layeringStrategy.layer(iosession);
            }
        }

        final ClientAsyncConnection conn = this.connFactory.create(
                "http-outgoing-" + entry.getId(),
                iosession,
                params);
        iosession.setAttribute(IOEventDispatch.CONNECTION_KEY, conn);

        if (proxy == null) {
            tracker.connectTarget(conn.getIOSession() instanceof SSLIOSession);
        } else {
            tracker.connectProxy(proxy, false);
        }
    }

    @Override
    public synchronized void tunnelProxy(
            final HttpHost next, final HttpParams params) throws IOException {
        final HttpPoolEntry entry = ensurePoolEntry();
        final RouteTracker tracker = entry.getTracker();
        if (!tracker.isConnected()) {
            throw new IllegalStateException("Connection not open");
        }
        tracker.tunnelProxy(next, false);
    }

    @Override
    public synchronized void tunnelTarget(
            final HttpParams params) throws IOException {
        final HttpPoolEntry entry = ensurePoolEntry();
        final RouteTracker tracker = entry.getTracker();
        if (!tracker.isConnected()) {
            throw new IllegalStateException("Connection not open");
        }
        if (tracker.isTunnelled()) {
            throw new IllegalStateException("Connection is already tunnelled");
        }
        tracker.tunnelTarget(false);
    }

    @Override
    public synchronized void layerProtocol(
            final HttpContext context, final HttpParams params) throws IOException {
        final HttpPoolEntry entry = ensurePoolEntry();
        final RouteTracker tracker = entry.getTracker();
        if (!tracker.isConnected()) {
            throw new IllegalStateException("Connection not open");
        }
        if (!tracker.isTunnelled()) {
            throw new IllegalStateException("Protocol layering without a tunnel not supported");
        }
        if (tracker.isLayered()) {
            throw new IllegalStateException("Multiple protocol layering not supported");
        }
        final HttpHost target = tracker.getTargetHost();
        final AsyncScheme scheme = getSchemeRegistry(context).getScheme(target);
        final LayeringStrategy layeringStrategy = scheme.getLayeringStrategy();
        if (layeringStrategy == null) {
            throw new IllegalStateException(scheme.getName() +
                    " scheme does not provider support for protocol layering");
        }
        final IOSession iosession = entry.getConnection();
        final ClientAsyncConnection conn = (ClientAsyncConnection) iosession.getAttribute(
                IOEventDispatch.CONNECTION_KEY);
        conn.upgrade((SSLIOSession) layeringStrategy.layer(iosession));
        tracker.layerProtocol(layeringStrategy.isSecure());
    }

    @Override
    public synchronized void releaseConnection() {
        if (this.poolEntry == null) {
            return;
        }
        this.manager.releaseConnection(this, this.duration, TimeUnit.MILLISECONDS);
        this.poolEntry = null;
    }

    @Override
    public synchronized void abortConnection() {
        if (this.poolEntry == null) {
            return;
        }
        this.reusable = false;
        final IOSession iosession = this.poolEntry.getConnection();
        final ClientAsyncConnection conn = (ClientAsyncConnection) iosession.getAttribute(
                IOEventDispatch.CONNECTION_KEY);
        try {
            conn.shutdown();
        } catch (final IOException ignore) {
        }
        this.manager.releaseConnection(this, this.duration, TimeUnit.MILLISECONDS);
        this.poolEntry = null;
    }

    @Override
    public synchronized String toString() {
        if (this.poolEntry != null) {
            return this.poolEntry.toString();
        }
        return "released";
    }

}
