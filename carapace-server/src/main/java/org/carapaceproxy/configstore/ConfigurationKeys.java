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
package org.carapaceproxy.configstore;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The configuration properties this server understands, and the report of the ones it does not.
 * <br>
 * The configuration is read by pulling the keys that are needed, one by one, so a key nobody asks for
 * is simply never seen: a typo in a property name is accepted by the validation and then does nothing.
 * This class exists to say so out loud. It only ever logs; no configuration is ever rejected because of it.
 */
public final class ConfigurationKeys {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationKeys.class);

    /**
     * Properties read as they are.
     */
    private static final Set<String> FLAT = Set.of(
            // connections manager, the global fallback of every connection pool
            "connectionsmanager.maxconnectionsperendpoint",
            "connectionsmanager.idletimeout",
            "connectionsmanager.maxlifetime",
            "connectionsmanager.stuckrequesttimeout",
            "connectionsmanager.backendsunreachableonstuckrequests",
            "connectionsmanager.connecttimeout",
            "connectionsmanager.borrowtimeout",
            "connectionsmanager.disposetimeout",
            "connectionsmanager.keepaliveidle",
            "connectionsmanager.keepaliveinterval",
            "connectionsmanager.keepalivecount",
            // cache
            "cache.maxsize",
            "cache.maxfilesize",
            "cache.requests.secure.disablewithoutpublic",
            "cache.cachealways",
            // access log
            "accesslog.path",
            "accesslog.format",
            "accesslog.format.timestamp",
            "accesslog.queue.maxcapacity",
            "accesslog.flush.interval",
            "accesslog.failure.wait",
            "accesslog.maxsize",
            "accesslog.advanced.enabled",
            "accesslog.advanced.body.size",
            // backend health
            "healthmanager.period",
            "healthmanager.warmupperiod",
            "healthmanager.tolerant",
            "healthmanager.connecttimeout",
            // certificates
            "dynamiccertificatesmanager.period",
            "dynamiccertificatesmanager.keypairssize",
            "dynamiccertificatesmanager.domainschecker.ipaddresses",
            "dynamiccertificatesmanager.localcertificates.store.path",
            "dynamiccertificatesmanager.localcertificates.peers.ids",
            "dynamiccertificatesmanager.errors.maxattempts",
            "ocspstaplingmanager.period",
            "ocsp.enabled",
            "truststore.ssltruststorefile",
            "truststore.ssltruststorepassword",
            "aws.accesskey",
            "aws.secretkey",
            // requests and responses
            "clients.idle.timeout",
            "response.compression.threshold",
            "request.compression.enabled",
            "carapace.maxheadersize",
            "carapace.maintenancemode.enabled",
            "carapace.http10backwardcompatibility.enabled",
            // mapper
            "mapper.class",
            "mapper.debug",
            "mapper.debug.name",
            "mapper.forcedirector.parameter",
            "mapper.forcebackend.parameter",
            "default.action.notfound",
            "default.action.internalerror",
            "default.action.maintenance",
            "default.action.badrequest",
            "default.action.serviceunavailable",
            // admin interface; note that listener.offset.port is flat, unlike the rest of listener.*
            "http.admin.enabled",
            "http.admin.host",
            "http.admin.port",
            "https.admin.port",
            "https.admin.sslcertfile",
            "https.admin.sslcertfilepassword",
            "admin.advertised.host",
            "admin.accesslog.path",
            "admin.accesslog.format.timezone",
            "admin.accesslog.retention.days",
            "listener.offset.port",
            "userrealm.class",
            "userrealm.path",
            // clustering and storage
            "config.type",
            "mode",
            "peer.id",
            "zkAddress",
            "zkSecure",
            "zkTimeout",
            "replication.factor",
            // the whole boot configuration is handed to the BookKeeper stats provider, which reads these
            "prometheusStatsHttpEnable",
            "prometheusStatsHttpAddress",
            "prometheusStatsHttpPort",
            "prometheusStatsLatencyRolloverSeconds",
            "httpServerEnabled"
    );

    /**
     * Families of properties in the form {@code <prefix>.<index>.<field>}, mapped to the fields they accept.
     */
    private static final Map<String, Set<String>> INDEXED = Map.of(
            "certificate", Set.of(
                    "hostname", "san", "file", "password", "mode", "daysbeforerenewal"),
            "listener", Set.of(
                    "port", "ssl", "protocol", "host", "sslciphers", "defaultcertificate", "sslprotocols",
                    "sobacklog", "keepalive", "keepaliveidle", "keepaliveinterval", "keepalivecount",
                    "maxkeepaliverequests", "forwarded", "trustedips"),
            "header", Set.of(
                    "id", "name", "value", "mode"),
            "action", Set.of(
                    "id", "enabled", "type", "file", "director", "code", "headers",
                    "redirect.location", "redirect.proto", "redirect.host", "redirect.port", "redirect.path"),
            "backend", Set.of(
                    "id", "enabled", "host", "port", "probePath", "safeCapacity", "ssl",
                    "cacertificate", "cacertificatepassword", "probescheme"),
            "director", Set.of(
                    "id", "enabled", "backends"),
            "route", Set.of(
                    "id", "action", "enabled", "match", "erroraction", "maintenanceaction")
    );

    /**
     * Prefixes whose content is forwarded verbatim to something else, so that any suffix under them is legal.
     */
    private static final Set<String> OPEN_PREFIXES = Set.of("db.", "zookeeper.");

    /**
     * Indexed families whose fields are forwarded verbatim, so that any field under them is legal.
     */
    private static final Set<String> OPEN_INDEXED = Set.of("filter");

    /**
     * Properties that used to mean something, mapped to what to do instead.
     */
    private static final Map<String, String> RETIRED_FLAT = Map.of(
            "cache.allocator.usepooledbytebufallocator",
            "it is a JVM system property, and is not read from the configuration"
    );

    /**
     * Retired indexed properties, keyed either by family or by {@code <prefix>.<field>}.
     */
    private static final Map<String, String> RETIRED_INDEXED = Map.of(
            "connectionpool",
            "connection pools are state, and are managed through the /api/connectionpools endpoints",
            "listener.enabled",
            "a listener is enabled by giving it a port",
            "listener.ocsp",
            "OCSP stapling is configured globally, with ocsp.enabled"
    );

    /**
     * The mappers and user realms shipped with the server. Anything else can read properties of its own,
     * which this class has no way of knowing about.
     */
    private static final Set<String> SHIPPED_PLUGINS = Set.of(
            "org.carapaceproxy.server.mapper.StandardEndpointMapper",
            "org.carapaceproxy.user.SimpleUserRealm",
            "org.carapaceproxy.user.FileUserRealm"
    );

    private static final Pattern INDEXED_KEY = Pattern.compile("^([a-zA-Z]+)\\.(\\d+)\\.(.+)$");

    private ConfigurationKeys() {
    }

    /**
     * Log a warning for every property of the store that this server does not understand,
     * and for every property that it used to understand and no longer does.
     * <br>
     * When a mapper or a user realm that is not shipped with the server is configured, only the retired
     * properties are reported: a custom implementation is free to read properties nobody here knows about.
     *
     * @param store the configuration to go through
     */
    public static void warnAboutUnknownProperties(final ConfigurationStore store) {
        final var customPlugin = isCustomPlugin(store, "mapper.class") || isCustomPlugin(store, "userrealm.class");
        if (customPlugin) {
            LOG.info("A custom mapper or user realm is configured: unrecognized properties will not be reported");
        }
        store.forEach((key, value) -> {
            final var advice = retirementAdviceFor(key);
            if (advice.isPresent()) {
                LOG.warn("Property {} is no longer used: {}", key, advice.get());
            } else if (!customPlugin && !isKnown(key)) {
                LOG.warn("Property {} is not recognized, and will be ignored", key);
            }
        });
    }

    private static boolean isCustomPlugin(final ConfigurationStore store, final String key) {
        final var classname = store.getProperty(key, null);
        return classname != null && !classname.isBlank() && !SHIPPED_PLUGINS.contains(classname.trim());
    }

    /**
     * Tell whether a property is one this server reads.
     * <br>
     * A {@link #retirementAdviceFor(String) retired} property is not known.
     *
     * @param key the name of the property
     * @return whether anything reads the property
     */
    public static boolean isKnown(final String key) {
        if (FLAT.contains(key) || OPEN_PREFIXES.stream().anyMatch(key::startsWith)) {
            return true;
        }
        final var matcher = INDEXED_KEY.matcher(key);
        if (!matcher.matches()) {
            return false;
        }
        final var prefix = matcher.group(1);
        return OPEN_INDEXED.contains(prefix) || INDEXED.getOrDefault(prefix, Set.of()).contains(matcher.group(3));
    }

    /**
     * Tell whether a property used to be read by this server, and what to do instead now that it is not.
     *
     * @param key the name of the property
     * @return what to do instead, or empty when the property was never retired
     */
    public static Optional<String> retirementAdviceFor(final String key) {
        final var flat = RETIRED_FLAT.get(key);
        if (flat != null) {
            return Optional.of(flat);
        }
        final var matcher = INDEXED_KEY.matcher(key);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        final var family = RETIRED_INDEXED.get(matcher.group(1));
        return Optional.ofNullable(family != null ? family : RETIRED_INDEXED.get(matcher.group(1) + "." + matcher.group(3)));
    }
}
