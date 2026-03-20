# Secrets, Keystores, and Certificates Storage

This document describes how Carapace stores and uses secrets/certificates in standalone and cluster modes, both at rest and at runtime.

Primary code sources:
- `carapace-server/src/main/java/org/carapaceproxy/core/RuntimeServerConfiguration.java`
- `carapace-server/src/main/java/org/carapaceproxy/core/ListeningChannel.java`
- `carapace-server/src/main/java/org/carapaceproxy/core/TrustStoreManager.java`
- `carapace-server/src/main/java/org/carapaceproxy/server/certificates/DynamicCertificatesManager.java`
- `carapace-server/src/main/java/org/carapaceproxy/configstore/HerdDBConfigurationStore.java`
- `carapace-server/src/main/java/org/carapaceproxy/configstore/PropertiesConfigurationStore.java`

## At rest

### Property-configured certificate and truststore references

- TLS certificate config is declared via `certificate.<n>.*` properties, including:
  - `certificate.<n>.file`
  - `certificate.<n>.password`
  - `certificate.<n>.mode` (`static`, `acme`, `manual`)
- Global truststore is declared via:
  - `truststore.ssltruststorefile`
  - `truststore.ssltruststorepassword`
- Backend-specific CA truststore is declared via:
  - `backend.<n>.cacertificate`
  - `backend.<n>.cacertificatepassword`

These values are plain property values in the active configuration store.

### Database mode (HerdDB)

When `config.type=database`, dynamic certificate state/material is persisted in HerdDB:

- `digital_certificates` table:
  - `domain` (PK)
  - `subjectAltNames`
  - `chain` (Base64-encoded PKCS12 keystore bytes)
  - `state`, `pendingOrder`, `pendingChallenges`, `attemptCount`, `message`
- `keypairs` table:
  - `domain` (PK)
  - `privateKey` (Base64)
  - `publicKey` (Base64)
- `acme_challenge_tokens` table:
  - `id` (PK)
  - `data` (challenge token payload)

Notes:
- Persistence is implemented as application-level string serialization (Base64/JSON), not encrypted by Carapace itself.

### File mode (`PropertiesConfigurationStore`)

When using file-backed properties as dynamic store:

- Dynamic cert state, generated keypairs, and ACME challenge tokens are kept in in-memory maps.
- They are not durably persisted by `PropertiesConfigurationStore` itself.
- Static keystores/truststores still come from files referenced by properties.

### Optional local filesystem export of dynamic certs

If enabled via runtime config (`dynamiccertificatesmanager.localcertificates.store.path`), Carapace exports non-manual dynamic cert material to disk as:

- `privatekey.pem`
- `chain.pem`
- `fullchain.pem`

under a domain-specific directory.

## Runtime (in memory)

### Certificate model and loading

- Parsed certificate configuration is represented by `SSLCertificateConfiguration`.
- At listener startup/reload, Carapace resolves certificate material in this order:
  1. dynamic certificate bytes from `DynamicCertificatesManager` cache (`getCertificateForDomain`), then
  2. fallback to `certificate.<n>.file` + `certificate.<n>.password` on disk.
- Material is loaded as `KeyStore`, then wrapped into `KeyManagerFactory`, then `SslContext`.

### Dynamic certificate cache

- `DynamicCertificatesManager` keeps a runtime map of `CertificateData` per domain.
- `CertificateData` includes lifecycle state and decoded keystore bytes (`keystoreData`) used by listeners.

### Trust material in runtime

- `TrustStoreManager` loads configured truststore into a `TrustManagerFactory`.
- CA certificates are also exposed as an in-memory map for components like OCSP/backend TLS checks.

## Standalone vs cluster behavior

### Standalone

- `config.type=file`: dynamic ACME artifacts are volatile (in-memory only in store), static files still apply.
- `config.type=database`: dynamic cert/key/token state is durable in HerdDB.

### Cluster

- Dynamic state is expected to be persisted in HerdDB and shared across peers.
- ZooKeeper is used for coordination (events/mutex/peer discovery), not for storing secret payloads.
- Certificate lifecycle execution is serialized cluster-wide via distributed mutex; updates are propagated by events and reloaded from DB.

## Security scope note

- Carapace stores sensitive values (passwords, private keys, keystore blobs) in configuration/database according to the configured backend.
- Protection at rest is delegated to infrastructure controls (filesystem permissions, DB security, disk encryption, secret management around property injection).
