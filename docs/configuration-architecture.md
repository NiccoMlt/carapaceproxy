# Configuration Architecture

This document collects concise diagrams and explanations about Carapace configuration storage, runtime configuration model, server composition, and the main interactions (boot, dynamic update, certificate update, request flow).

TL;DR: Static (boot) configuration is read by `ServerMain` -> `HttpProxyServer.configureAtBoot(...)`. Dynamic configuration is stored either in-memory (file properties) or in HerdDB (`HerdDBConfigurationStore`) and is applied via `HttpProxyServer.applyDynamicConfiguration(...)` after validation by `RuntimeServerConfiguration.configure(...)`.

---

## 1. Configuration storage model (compact class diagram)

```mermaid
classDiagram
    class ConfigurationStore {
      <<interface>>
      +getProperty(key, default)
      +forEach(consumer)
      +findMaxIndexForPrefix(prefix)
      +reload()
      +commitConfiguration(newStore)
      +loadCertificateForDomain(domain)
      +saveCertificate(cert)
    }

    class HerdDBConfigurationStore {
      -properties : Map
      -datasource : HerdDBEmbeddedDataSource
      +commitConfiguration(newStore)
      +reload()
      +load/save certificates, keypairs, tokens (DB)
    }

    class PropertiesConfigurationStore {
      -properties : Properties
      -certificates : Map
      +in-memory load/save certificates & keypairs & tokens
    }

    class CertificateData {
      -domain
      -subjectAltNames
      -chain
      -state
      -pendingOrderLocation
      -pendingChallengesData
      +error(msg)
      +success(state)
    }

    ConfigurationStore <|.. HerdDBConfigurationStore
    ConfigurationStore <|.. PropertiesConfigurationStore

    HerdDBConfigurationStore "1" *-- "0..n" CertificateData : persists
    PropertiesConfigurationStore "1" *-- "0..n" CertificateData : caches
```

Caption: `ConfigurationStore` is the abstraction used across the server. `HerdDBConfigurationStore` implements persistence, schema management and commit semantics. `PropertiesConfigurationStore` is a memory-backed store used by file-based dynamic configs or transient payloads. `CertificateData` models ACME certificates and is persisted by the store implementations.

---

## 2. Runtime configuration model (compact class diagram)

```mermaid
classDiagram
    class RuntimeServerConfiguration {
      -listeners : Map<EndpointKey,NetworkListenerConfiguration>
      -certificates : Map<String,SSLCertificateConfiguration>
      -connectionPools : Map<String,ConnectionPoolConfiguration>
      -requestFilters : List<RequestFilterConfiguration>
      +configure(ConfigurationStore)
      +addListener(...)
      +addCertificate(...)
    }

    class NetworkListenerConfiguration {
      +host
      +port
      +ssl
      +protocols
      +defaultCertificate
      +getKey()
    }

    class SSLCertificateConfiguration {
      +id
      +hostname
      +file
      +mode
      +isDynamic()
    }

    class ConnectionPoolConfiguration {
      +id
      +domain
      +maxConnectionsPerEndpoint
      +connectTimeout
    }

    RuntimeServerConfiguration *-- NetworkListenerConfiguration
    RuntimeServerConfiguration *-- SSLCertificateConfiguration
    RuntimeServerConfiguration *-- ConnectionPoolConfiguration
```

Caption: `RuntimeServerConfiguration.configure(ConfigurationStore)` parses and validates dynamic properties into the runtime model. Validation failures are raised as `ConfigurationNotValidException` and prevent applying invalid state.

---

## 3. Server composition (compact class diagram)

```mermaid
classDiagram
    class HttpProxyServer {
      -currentConfiguration : RuntimeServerConfiguration
      -dynamicConfigurationStore : ConfigurationStore
      -mapper : EndpointMapper
      -groupMembershipHandler : GroupMembershipHandler
      -listeners : Listeners
      -proxyRequestsManager : ProxyRequestsManager
      -dynamicCertificatesManager : DynamicCertificatesManager
      -trustStoreManager : TrustStoreManager
      -configurationLock : ReentrantLock
      +configureAtBoot(ConfigurationStore)
      +applyDynamicConfigurationFromAPI(ConfigurationStore)
    }

    class Listeners {
      -currentConfiguration
      -listeningChannels : Map<EndpointKey,ListeningChannel>
      +reloadConfiguration(RuntimeServerConfiguration)
    }

    class ProxyRequestsManager {
      -connectionsManager
      +reloadConfiguration(RuntimeServerConfiguration, Collection<BackendConfiguration>)
    }

    HttpProxyServer *-- RuntimeServerConfiguration : currentConfiguration
    HttpProxyServer o-- ConfigurationStore : dynamicConfigurationStore
    HttpProxyServer *-- Listeners : listeners
    HttpProxyServer *-- ProxyRequestsManager : proxyRequestsManager
    HttpProxyServer *-- TrustStoreManager : trustStoreManager
    HttpProxyServer o-- DynamicCertificatesManager : dynamicCertificatesManager
```

Caption: `HttpProxyServer` holds the authoritative `currentConfiguration` object and coordinates subsystem reloads. `configurationLock` serializes local concurrent config applies. In cluster mode, changes are persisted then propagated via group events.

---

## 4. Interaction sequences

### 4.1 Boot-time configuration load

```mermaid
sequenceDiagram
    participant ServerMain
    participant BootStore as ConfigurationStore(boot)
    participant HttpProxyServer
    participant DynamicStore as ConfigurationStore(dynamic)
    participant RuntimeConfig as RuntimeServerConfiguration

    ServerMain->>HttpProxyServer: configureAtBoot(BootStore)
    HttpProxyServer->>HttpProxyServer: readClusterConfiguration(BootStore)
    alt config.type == "file"
        HttpProxyServer->>DynamicStore: dynamicConfigurationStore = BootStore
    else
        HttpProxyServer->>DynamicStore: dynamicConfigurationStore = new HerdDBConfigurationStore(...)
    end
    HttpProxyServer->>HttpProxyServer: applyStaticConfiguration(BootStore)
    HttpProxyServer->>HttpProxyServer: initGroupMembership()
    HttpProxyServer->>HttpProxyServer: applyDynamicConfiguration(null, atBoot=true)
    HttpProxyServer->>RuntimeConfig: buildValidConfiguration(dynamicConfigurationStore)
    RuntimeConfig-->>HttpProxyServer: validated configuration
    HttpProxyServer->>Listeners: listeners.reloadConfiguration(newConfiguration)
    HttpProxyServer->>ProxyRequestsManager: proxyRequestsManager.reloadConfiguration(newConfiguration, backends)
```

Caption: Boot first applies static keys, chooses the dynamic store, then validates & applies the dynamic configuration. HerdDB initialization may create tables and load properties.

---

### 4.2 Dynamic configuration update (API -> commit -> cluster)

```mermaid
sequenceDiagram
    participant AdminAPI
    participant HttpProxyServer
    participant NewStore as ConfigurationStore(new)
    participant DynStore as ConfigurationStore(dynamic)
    participant Group as GroupMembershipHandler

    AdminAPI->>HttpProxyServer: applyDynamicConfigurationFromAPI(NewStore)
    HttpProxyServer->>HttpProxyServer: buildValidConfiguration(NewStore)
    HttpProxyServer->>HttpProxyServer: reload subsystems (listeners, proxy, truststore, certs)
    HttpProxyServer->>DynStore: commitConfiguration(NewStore)
    HttpProxyServer->>Group: fireEvent("configurationChange")
    Group->>Peers: peers reload and call applyDynamicConfiguration(null, atBoot=true)
```

Caption: After validation the server commits the new properties and fires a group event; peers reload from the dynamic store and re-apply the configuration.

---

### 4.3 Certificate update flow (compact)

```mermaid
sequenceDiagram
    participant AdminAPI
    participant HttpProxyServer
    participant DynStore as ConfigurationStore(dynamic)
    participant Props as Properties

    AdminAPI->>HttpProxyServer: updateDynamicCertificateForDomain(CertificateData)
    HttpProxyServer->>DynStore: saveCertificate(cert)   
    HttpProxyServer->>DynStore: asProperties(null) -> props
    HttpProxyServer->>HttpProxyServer: modify props to add/update certificate.<n>.* entries
    HttpProxyServer->>HttpProxyServer: applyDynamicConfigurationFromAPI(new PropertiesConfigurationStore(props))
```

Caption: Certificate update writes structured certificate data to the DB and also ensures properties contain the `certificate.<n>.*` metadata so that `RuntimeServerConfiguration` parsing recognizes the certificate.

---

### 4.4 Request forwarding path (runtime usage of state)

```mermaid
sequenceDiagram
    participant Client
    participant Listeners
    participant HttpProxyServer
    participant ProxyMgr as ProxyRequestsManager
    participant Backend

    Client->>Listeners: HTTP request arrives
    Listeners->>HttpProxyServer: wrap into ProxyRequest
    HttpProxyServer->>HttpProxyServer: parent.getMapper().map(request) // uses currentConfiguration
    HttpProxyServer->>ProxyMgr: processRequest(ProxyRequest)
    ProxyMgr->>Backend: forward request (connections manager, SSL contexts)
    Backend-->>ProxyMgr: response
    ProxyMgr-->>Client: deliver response
```

Caption: Runtime operations depend on the current configuration state: listeners, mapper, connection pools and truststore are consulted during request processing.

---

## 5. Complexity summary & recommendations (short)

Key complexity points
- Dual representation: configuration properties (key/value) + structured DB objects (certs/keypairs) create duplicated state and synchronization burden.
- No explicit config versioning/CAS in the commit path; cluster commits are eventual and may race.
- Validation occurs before commit but peers reload asynchronously; rollout coordination is limited.

Recommendations (high level)
- Adopt a versioned canonical dynamic configuration (JSON) in DB plus separate tables for binary objects (certs/keypairs).
- Use optimistic locking / CAS (config version) on commits to prevent accidental overwrites.
- Separate validation from commit; add a "dry-run" validation API that produces the diffs and required subsystem actions.
- Implement per-subsystem reconcilers to compute minimal changes and perform graceful swaps.
- Consolidate certificate metadata references in the canonical config, keep `CertificateData` as binary/state in its table.
- Add audit metadata for config commits (who/when/version) and expose via admin API.

---

## 6. How to preview / render diagrams locally

Options:
- Use the online editor: https://mermaid.live/ (paste a mermaid fenced block and render)
- Use VS Code with a Mermaid preview extension
- Install Mermaid CLI (Node) and render file snippets:

```powershell
npm i -g @mermaid-js/mermaid-cli
mmdc -i docs/configuration-architecture.mmd -o docs/configuration-architecture.svg
```

Note: the repo Markdown files embed Mermaid blocks; your Markdown renderer must support Mermaid to preview inline.

---

See also
- `carapace-server/src/main/java/org/carapaceproxy/core/HttpProxyServer.java`
- `carapace-server/src/main/java/org/carapaceproxy/core/RuntimeServerConfiguration.java`
- `carapace-server/src/main/java/org/carapaceproxy/configstore/HerdDBConfigurationStore.java`





