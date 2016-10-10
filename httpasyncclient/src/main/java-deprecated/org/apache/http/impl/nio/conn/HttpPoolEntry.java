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
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.RouteTracker;
import org.apache.http.nio.conn.ClientAsyncConnection;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.pool.PoolEntry;

@Deprecated
class HttpPoolEntry extends PoolEntry<HttpRoute, IOSession> {

    private final Log log;
    private final RouteTracker tracker;

    HttpPoolEntry(final Log log, final String id, final HttpRoute route, final IOSession session,
            final long timeToLive, final TimeUnit tunit) {
        super(id, route, session, timeToLive, tunit);
        this.log = log;
        this.tracker = new RouteTracker(route);
    }

    @Override
    public boolean isExpired(final long now) {
        final boolean expired = super.isExpired(now);
        if (expired && this.log.isDebugEnabled()) {
            this.log.debug("Connection " + this + " expired @ " + new Date(getExpiry()));
        }
        return expired;
    }

    public ClientAsyncConnection getOperatedClientConnection() {
        final IOSession session = getConnection();
        return (ClientAsyncConnection) session.getAttribute(IOEventDispatch.CONNECTION_KEY);
    }

    @Override
    public void close() {
        try {
            getOperatedClientConnection().shutdown();
        } catch (final IOException ex) {
            if (this.log.isDebugEnabled()) {
                this.log.debug("I/O error shutting down connection", ex);
            }
        }
    }

    @Override
    public boolean isClosed() {
        final IOSession session = getConnection();
        return session.isClosed();
    }

    HttpRoute getPlannedRoute() {
        return super.getRoute();
    }

    RouteTracker getTracker() {
        return this.tracker;
    }

    HttpRoute getEffectiveRoute() {
        return this.tracker.toRoute();
    }

}
