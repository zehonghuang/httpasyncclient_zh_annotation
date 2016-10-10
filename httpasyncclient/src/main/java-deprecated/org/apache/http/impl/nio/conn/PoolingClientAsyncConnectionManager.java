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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.nio.conn.ClientAsyncConnectionFactory;
import org.apache.http.nio.conn.ClientAsyncConnectionManager;
import org.apache.http.nio.conn.ManagedClientAsyncConnection;
import org.apache.http.nio.conn.scheme.AsyncSchemeRegistry;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.pool.ConnPoolControl;
import org.apache.http.pool.PoolStats;
import org.apache.http.util.Args;

@Deprecated
public class PoolingClientAsyncConnectionManager
                              implements ClientAsyncConnectionManager, ConnPoolControl<HttpRoute> {

    private final Log log = LogFactory.getLog(getClass());

    private final ConnectingIOReactor ioreactor;
    private final HttpNIOConnPool pool;
    private final AsyncSchemeRegistry schemeRegistry;
    private final ClientAsyncConnectionFactory connFactory;

    public PoolingClientAsyncConnectionManager(
            final ConnectingIOReactor ioreactor,
            final AsyncSchemeRegistry schemeRegistry,
            final long timeToLive, final TimeUnit tunit) {
        super();
        Args.notNull(ioreactor, "I/O reactor");
        Args.notNull(schemeRegistry, "Scheme registory");
        Args.notNull(tunit, "Time unit");
        this.ioreactor = ioreactor;
        this.pool = new HttpNIOConnPool(this.log, ioreactor, schemeRegistry, timeToLive, tunit);
        this.schemeRegistry = schemeRegistry;
        this.connFactory = createClientAsyncConnectionFactory();
    }

    public PoolingClientAsyncConnectionManager(
            final ConnectingIOReactor ioreactor,
            final AsyncSchemeRegistry schemeRegistry) throws IOReactorException {
        this(ioreactor, schemeRegistry, -1, TimeUnit.MILLISECONDS);
    }

    public PoolingClientAsyncConnectionManager(
            final ConnectingIOReactor ioreactor) throws IOReactorException {
        this(ioreactor, AsyncSchemeRegistryFactory.createDefault());
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            shutdown();
        } finally {
            super.finalize();
        }
    }

    protected ClientAsyncConnectionFactory createClientAsyncConnectionFactory() {
        return new DefaultClientAsyncConnectionFactory();
    }

    @Override
    public AsyncSchemeRegistry getSchemeRegistry() {
        return this.schemeRegistry;
    }

    @Override
    public void execute(final IOEventDispatch eventDispatch) throws IOException {
        this.ioreactor.execute(eventDispatch);
    }

    @Override
    public IOReactorStatus getStatus() {
        return this.ioreactor.getStatus();
    }

    @Override
    public void shutdown(final long waitMs) throws IOException {
        this.log.debug("Connection manager is shutting down");
        this.pool.shutdown(waitMs);
        this.log.debug("Connection manager shut down");
    }

    @Override
    public void shutdown() throws IOException {
        this.log.debug("Connection manager is shutting down");
        this.pool.shutdown(2000);
        this.log.debug("Connection manager shut down");
    }

    private String format(final HttpRoute route, final Object state) {
        final StringBuilder buf = new StringBuilder();
        buf.append("[route: ").append(route).append("]");
        if (state != null) {
            buf.append("[state: ").append(state).append("]");
        }
        return buf.toString();
    }

    private String formatStats(final HttpRoute route) {
        final StringBuilder buf = new StringBuilder();
        final PoolStats totals = this.pool.getTotalStats();
        final PoolStats stats = this.pool.getStats(route);
        buf.append("[total kept alive: ").append(totals.getAvailable()).append("; ");
        buf.append("route allocated: ").append(stats.getLeased() + stats.getAvailable());
        buf.append(" of ").append(stats.getMax()).append("; ");
        buf.append("total allocated: ").append(totals.getLeased() + totals.getAvailable());
        buf.append(" of ").append(totals.getMax()).append("]");
        return buf.toString();
    }

    private String format(final HttpPoolEntry entry) {
        final StringBuilder buf = new StringBuilder();
        buf.append("[id: ").append(entry.getId()).append("]");
        buf.append("[route: ").append(entry.getRoute()).append("]");
        final Object state = entry.getState();
        if (state != null) {
            buf.append("[state: ").append(state).append("]");
        }
        return buf.toString();
    }

    @Override
    public Future<ManagedClientAsyncConnection> leaseConnection(
            final HttpRoute route,
            final Object state,
            final long connectTimeout,
            final TimeUnit tunit,
            final FutureCallback<ManagedClientAsyncConnection> callback) {
        Args.notNull(route, "HTTP route");
        Args.notNull(tunit, "Time unit");
        if (this.log.isDebugEnabled()) {
            this.log.debug("Connection request: " + format(route, state) + formatStats(route));
        }
        final BasicFuture<ManagedClientAsyncConnection> future = new BasicFuture<ManagedClientAsyncConnection>(
                callback);
        this.pool.lease(route, state, connectTimeout, tunit, new InternalPoolEntryCallback(future));
        return future;
    }

    @Override
    public void releaseConnection(
            final ManagedClientAsyncConnection conn,
            final long keepalive,
            final TimeUnit tunit) {
        Args.notNull(conn, "HTTP connection");
        if (!(conn instanceof ManagedClientAsyncConnectionImpl)) {
            throw new IllegalArgumentException("Connection class mismatch, " +
                 "connection not obtained from this manager");
        }
        Args.notNull(tunit, "Time unit");
        final ManagedClientAsyncConnectionImpl managedConn = (ManagedClientAsyncConnectionImpl) conn;
        final ClientAsyncConnectionManager manager = managedConn.getManager();
        if (manager != null && manager != this) {
            throw new IllegalArgumentException("Connection not obtained from this manager");
        }
        if (this.pool.isShutdown()) {
            return;
        }

        synchronized (managedConn) {
            final HttpPoolEntry entry = managedConn.getPoolEntry();
            if (entry == null) {
                return;
            }
            try {
                if (managedConn.isOpen() && !managedConn.isMarkedReusable()) {
                    try {
                        managedConn.shutdown();
                    } catch (final IOException iox) {
                        if (this.log.isDebugEnabled()) {
                            this.log.debug("I/O exception shutting down released connection", iox);
                        }
                    }
                }
                if (managedConn.isOpen()) {
                    entry.updateExpiry(keepalive, tunit != null ? tunit : TimeUnit.MILLISECONDS);
                    if (this.log.isDebugEnabled()) {
                        String s;
                        if (keepalive > 0) {
                            s = "for " + keepalive + " " + tunit;
                        } else {
                            s = "indefinitely";
                        }
                        this.log.debug("Connection " + format(entry) + " can be kept alive " + s);
                    }
                    // Do not time out pooled connection
                    managedConn.setSocketTimeout(0);
                }
            } finally {
                this.pool.release(managedConn.detach(), managedConn.isMarkedReusable());
            }
            if (this.log.isDebugEnabled()) {
                this.log.debug("Connection released: " + format(entry) + formatStats(entry.getRoute()));
            }
        }
    }

    @Override
    public PoolStats getTotalStats() {
        return this.pool.getTotalStats();
    }

    @Override
    public PoolStats getStats(final HttpRoute route) {
        return this.pool.getStats(route);
    }

    @Override
    public void setMaxTotal(final int max) {
        this.pool.setMaxTotal(max);
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        this.pool.setDefaultMaxPerRoute(max);
    }

    @Override
    public void setMaxPerRoute(final HttpRoute route, final int max) {
        this.pool.setMaxPerRoute(route, max);
    }

    @Override
    public int getMaxTotal() {
        return this.pool.getMaxTotal();
    }

    @Override
    public int getDefaultMaxPerRoute() {
        return this.pool.getDefaultMaxPerRoute();
    }

    @Override
    public int getMaxPerRoute(final HttpRoute route) {
        return this.pool.getMaxPerRoute(route);
    }

    public void closeIdleConnections(final long idleTimeout, final TimeUnit tunit) {
        if (log.isDebugEnabled()) {
            log.debug("Closing connections idle longer than " + idleTimeout + " " + tunit);
        }
        this.pool.closeIdle(idleTimeout, tunit);
    }

    public void closeExpiredConnections() {
        log.debug("Closing expired connections");
        this.pool.closeExpired();
    }

    class InternalPoolEntryCallback implements FutureCallback<HttpPoolEntry> {

        private final BasicFuture<ManagedClientAsyncConnection> future;

        public InternalPoolEntryCallback(
                final BasicFuture<ManagedClientAsyncConnection> future) {
            super();
            this.future = future;
        }

        @Override
        public void completed(final HttpPoolEntry entry) {
            if (log.isDebugEnabled()) {
                log.debug("Connection leased: " + format(entry) + formatStats(entry.getRoute()));
            }
            final ManagedClientAsyncConnection conn = new ManagedClientAsyncConnectionImpl(
                    PoolingClientAsyncConnectionManager.this,
                    PoolingClientAsyncConnectionManager.this.connFactory,
                    entry);
            if (!this.future.completed(conn)) {
                pool.release(entry, true);
            }
        }

        @Override
        public void failed(final Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Connection request failed", ex);
            }
            this.future.failed(ex);
        }

        @Override
        public void cancelled() {
            log.debug("Connection request cancelled");
            this.future.cancel(true);
        }

    }

}
