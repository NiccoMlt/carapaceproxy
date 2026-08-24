/*
 Licensed to Diennea S.r.l. under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. Diennea S.r.l. licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

 */
package org.carapaceproxy.server.config;

/**
 * A connection pool as it is stored, before the global defaults are applied to it.
 * <br>
 * Every numeric component is nullable: a {@code null} means that the pool does not pin that value,
 * and inherits whatever the global {@code connectionsmanager.*} configuration says at load time.
 * {@link #resolve(ConnectionPoolConfiguration) Resolving} the entry turns it into the
 * {@link ConnectionPoolConfiguration} the runtime uses, where every value is set.
 *
 * @param id                        identifier of the pool, and key of the in-memory pool map
 * @param domain                    regular expression matched against the request hostname
 * @param enabled                   whether the pool should be used at all
 * @param keepAlive                 whether to enable TCP keep-alive on the pooled connections
 * @param maxConnectionsPerEndpoint maximum number of connections per endpoint, {@code null} to inherit
 * @param borrowTimeout             timeout to borrow a connection from the pool, {@code null} to inherit
 * @param connectTimeout            timeout to establish a connection, {@code null} to inherit
 * @param stuckRequestTimeout       timeout after which a request is considered stuck, {@code null} to inherit
 * @param idleTimeout               timeout after which an idle connection is closed, {@code null} to inherit
 * @param maxLifeTime               maximum lifetime of a connection, {@code null} to inherit
 * @param disposeTimeout            timeout to dispose of a pool that is no longer in use, {@code null} to inherit
 * @param keepaliveIdle             idle time before the first keep-alive probe, {@code null} to inherit
 * @param keepaliveInterval         interval between keep-alive probes, {@code null} to inherit
 * @param keepaliveCount            number of unacknowledged keep-alive probes before dropping, {@code null} to inherit
 */
public record ConnectionPoolEntry(
        String id,
        String domain,
        boolean enabled,
        boolean keepAlive,
        Integer maxConnectionsPerEndpoint,
        Integer borrowTimeout,
        Integer connectTimeout,
        Integer stuckRequestTimeout,
        Integer idleTimeout,
        Integer maxLifeTime,
        Integer disposeTimeout,
        Integer keepaliveIdle,
        Integer keepaliveInterval,
        Integer keepaliveCount) {

    public ConnectionPoolEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Invalid connection pool: id cannot be empty");
        }
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("Invalid connection pool " + id + ": domain cannot be empty");
        }
    }

    /**
     * Apply the global defaults to this entry, so that it can be used by the runtime.
     *
     * @param defaults the default connection pool, which carries the global {@code connectionsmanager.*} values
     * @return the pool configuration, with every value set
     */
    public ConnectionPoolConfiguration resolve(final ConnectionPoolConfiguration defaults) {
        return new ConnectionPoolConfiguration(
                id,
                domain,
                inherit(maxConnectionsPerEndpoint, defaults.getMaxConnectionsPerEndpoint()),
                inherit(borrowTimeout, defaults.getBorrowTimeout()),
                inherit(connectTimeout, defaults.getConnectTimeout()),
                inherit(stuckRequestTimeout, defaults.getStuckRequestTimeout()),
                inherit(idleTimeout, defaults.getIdleTimeout()),
                inherit(maxLifeTime, defaults.getMaxLifeTime()),
                inherit(disposeTimeout, defaults.getDisposeTimeout()),
                inherit(keepaliveIdle, defaults.getKeepaliveIdle()),
                inherit(keepaliveInterval, defaults.getKeepaliveInterval()),
                inherit(keepaliveCount, defaults.getKeepaliveCount()),
                keepAlive,
                enabled
        );
    }

    private static int inherit(final Integer pinned, final int inherited) {
        return pinned != null ? pinned : inherited;
    }
}
