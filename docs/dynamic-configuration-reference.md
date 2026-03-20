# Carapace Dynamic Configuration Reference

This document tracks configuration properties that are intended to be applied at runtime through UI/API configuration reload flows (without a full process restart).

Sources of truth in code:
- `carapace-server/src/main/java/org/carapaceproxy/core/RuntimeServerConfiguration.java`
- `carapace-server/src/main/java/org/carapaceproxy/server/mapper/StandardEndpointMapper.java`
- `carapace-server/src/main/java/org/carapaceproxy/core/HttpProxyServer.java` (dynamic apply path)

## Global dynamic properties

### Connections manager
- `connectionsmanager.maxconnectionsperendpoint`
- `connectionsmanager.idletimeout`
- `connectionsmanager.maxlifetime`
- `connectionsmanager.stuckrequesttimeout`
- `connectionsmanager.backendsunreachableonstuckrequests`
- `connectionsmanager.connecttimeout`
- `connectionsmanager.borrowtimeout`
- `connectionsmanager.disposetimeout`
- `connectionsmanager.keepaliveidle`
- `connectionsmanager.keepaliveinterval`
- `connectionsmanager.keepalivecount`

### Mapper and defaults
- `mapper.class`
- `default.action.notfound`
- `default.action.internalerror`
- `default.action.maintenance`
- `default.action.badrequest`
- `default.action.serviceunavailable`
- `mapper.forcedirector.parameter`
- `mapper.forcebackend.parameter`
- `mapper.debug`
- `mapper.debug.name`

### Cache
- `cache.maxsize`
- `cache.maxfilesize`
- `cache.requests.secure.disablewithoutpublic`
- `cache.cachealways`

### Access log
- `accesslog.path`
- `accesslog.format.timestamp`
- `accesslog.format`
- `accesslog.queue.maxcapacity`
- `accesslog.flush.interval`
- `accesslog.failure.wait`
- `accesslog.maxsize`
- `accesslog.advanced.enabled`
- `accesslog.advanced.body.size`

### Health manager
- `healthmanager.period`
- `healthmanager.warmupperiod`
- `healthmanager.tolerant`
- `healthmanager.connecttimeout`

### Certificates, OCSP, truststore
- `dynamiccertificatesmanager.period`
- `dynamiccertificatesmanager.keypairssize`
- `dynamiccertificatesmanager.domainschecker.ipaddresses`
- `dynamiccertificatesmanager.localcertificates.store.path`
- `dynamiccertificatesmanager.localcertificates.peers.ids`
- `dynamiccertificatesmanager.errors.maxattempts`
- `ocspstaplingmanager.period`
- `truststore.ssltruststorefile`
- `truststore.ssltruststorepassword`
- `ocsp.enabled`

### Client/server runtime behavior
- `clients.idle.timeout`
- `response.compression.threshold`
- `request.compression.enabled`
- `carapace.maxheadersize`
- `carapace.maintenancemode.enabled`
- `carapace.http10backwardcompatibility.enabled`

## Indexed dynamic families

### Certificates
Prefix: `certificate.<n>.`
- `hostname`
- `san`
- `file`
- `password`
- `mode`
- `daysbeforerenewal`

### Listeners
Prefix: `listener.<n>.`
- `host`
- `port`
- `ssl`
- `sslciphers`
- `defaultcertificate`
- `sslprotocols`
- `sobacklog`
- `keepalive`
- `keepaliveidle`
- `keepaliveinterval`
- `keepalivecount`
- `maxkeepaliverequests`
- `forwarded`
- `trustedips`
- `protocol`

### Filters
Prefix: `filter.<n>.`
- `type`
- `match`
- `param`
- `regexp`
- `value`

### Connection pools
Prefix: `connectionpool.<n>.`
- `id`
- `domain`
- `maxconnectionsperendpoint`
- `borrowtimeout`
- `connecttimeout`
- `stuckrequesttimeout`
- `idletimeout`
- `maxlifetime`
- `disposetimeout`
- `keepaliveidle`
- `keepaliveinterval`
- `keepalivecount`
- `enabled`
- `keepalive`

### Custom headers
Prefix: `header.<n>.`
- `id`
- `name`
- `value`
- `mode`

### Actions
Prefix: `action.<n>.`
- `id`
- `enabled`
- `type`
- `file`
- `director`
- `code`
- `headers`
- `redirect.location`
- `redirect.proto`
- `redirect.host`
- `redirect.port`
- `redirect.path`

### Backends
Prefix: `backend.<n>.`
- `id`
- `enabled`
- `host`
- `port`
- `probePath`
- `safeCapacity`
- `ssl`
- `cacertificate`
- `cacertificatepassword`
- `probescheme`

### Directors
Prefix: `director.<n>.`
- `id`
- `enabled`
- `backends`

### Routes
Prefix: `route.<n>.`
- `id`
- `action`
- `enabled`
- `match`
- `erroraction`
- `maintenanceaction`

## Realm-related dynamic input

Runtime reload reconfigures the active realm instance. For the built-in file realm:
- `userrealm.path` points to the external user properties file.
- In that file, users are defined as `user.<username>=<password>`.

## Current caveats (as implemented)

- `listener.<n>.enabled` appears in samples/tests but is not parsed by the listener configuration builder.
- `listener.<n>.ocsp` appears in sample config but is not parsed; OCSP control is global via `ocsp.enabled` and `ocspstaplingmanager.period`.
