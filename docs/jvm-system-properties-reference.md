# Carapace JVM System Properties Reference

This document tracks JVM system properties (`-D...`) used by Carapace server code.

These are not regular configuration-store properties and are typically read directly via `System.getProperty(...)`, `Boolean.getBoolean(...)`, or `Integer.getInteger(...)`.

## Runtime/system behavior

- `cache.allocator.usepooledbytebufallocator`
  - Enables pooled Netty `ByteBuf` allocator for cache internals.

## ACME/dynamic certificates tuning

- `carapace.acme.testmode`
  - Enables ACME testing mode.
- `carapace.acme.default.keypairssize`
  - Default RSA key size for generated ACME key pairs.
- `carapace.acme.default.daysbeforerenewal`
  - Default days-before-renewal threshold.
- `carapace.acme.dnschallengereachabilitycheck.limit`
  - Maximum DNS challenge reachability check attempts.

## Persistence/coordination startup helpers

- `herd.waitfortablespace.timeout`
  - Timeout used while waiting for HerdDB tablespace availability.
- `pidfile`
  - Path/name used by PID file locking utilities.

## Working directory related

- `user.dir`
  - Used as base for runtime path resolution in launcher/locking/wrapper flows.

## Notes

- These properties are process-level flags and require process restart to change effective behavior.
- Static and dynamic configuration-store properties are documented separately in:
  - `docs/static-configuration-reference.md`
  - `docs/dynamic-configuration-reference.md`
