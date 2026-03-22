# ZooKeeper Stored Data Reference

This document describes what Carapace stores in ZooKeeper in cluster mode.

Primary source:
- `carapace-server/src/main/java/org/carapaceproxy/cluster/impl/ZooKeeperGroupMembershipHandler.java`

Related emitters/consumers:
- `carapace-server/src/main/java/org/carapaceproxy/core/HttpProxyServer.java`
- `carapace-server/src/main/java/org/carapaceproxy/server/certificates/DynamicCertificatesManager.java`

## Important scope note

- Dynamic configuration key/value data is not persisted in ZooKeeper.
- Dynamic configuration is persisted in the configured `ConfigurationStore` (typically HerdDB, table `proxy_config`).
- ZooKeeper is used for peer membership, event broadcast, and distributed mutexes.

## ZNode structure and payloads

### `/proxy/peers/<peerId>` (ephemeral)

Purpose:
- Cluster peer presence and peer metadata discovery.

Lifecycle:
- Created as `EPHEMERAL` at peer startup.
- Automatically removed when the peer session closes/expires.

Data format:
- JSON object (`Map<String, String>`) written via `storeLocalPeerInfo(...)`.

Known keys written by `HttpProxyServer`:
- `peer_admin_server_host`
- `peer_admin_server_port`
- `peer_admin_server_https_port`

### `/proxy/events/<eventId>` (persistent)

Purpose:
- Cluster event channel. Event delivery is implemented as a `setData` on a persistent znode.

Lifecycle:
- Path is created as `PERSISTENT` if missing.
- Reused for subsequent events with the same `eventId`.

Data format:
- JSON object (`Map<String, Object>`) with mandatory field:
  - `origin`: peer id of sender
- Additional event-specific fields may be present.

Used event ids and payload schemas:
- `configurationChange`
  - payload: `{ "origin": "<peerId>" }`
  - consumer: remote peers reload configuration from store and reapply runtime config.
- `certificates_state_changed`
  - payload: `{ "origin": "<peerId>" }`
  - consumer: remote peers reload dynamic certificates state from DB.
- `certificates_request_store`
  - payload: `{ "origin": "<peerId>", "certs": ["domain1", "domain2", ...] }`
  - special case: empty `certs` list means "all known certificates".

Delivery behavior:
- Self-originated events are ignored by receivers (comparison on `origin`).
- On ZooKeeper reconnection, callbacks receive `reconnected()` and may trigger reload actions.

### `/proxy/mutex/<mutexId>` (lock path)

Purpose:
- Distributed mutual exclusion using Curator `InterProcessMutex`.

Usage:
- Created on demand by `executeInMutex(...)`.
- Used to serialize cluster-wide jobs (for example certificate lifecycle tasks).

Data format:
- Managed by Curator lock internals; Carapace does not store custom payload schema here.

## Cluster APIs backed by ZooKeeper peer data

- `/cluster/localpeer`
- `/cluster/peers`
- `/cluster/peers/{peerId}`

These read peer ids and peer metadata from `/proxy/peers/*` via `GroupMembershipHandler`.
