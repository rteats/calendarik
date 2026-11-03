# Next phase: encrypted two-person synchronization

This document is a **design**, not an implemented feature.

- Each person owns personal events; both may edit joint events.
- Store durable local events and an outbox of signed operations.
- Pair through a QR code containing a randomly generated enrollment secret.
  Establish separately authenticated identities (do not trust an editable
  local profile selector).
- Use authenticated AEAD encryption (AES-GCM with unique nonces) **before**
  sending data to Firestore. Separate transport authentication, data access
  rules and end-to-end encryption. Never commit account secrets.
- Version every encrypted envelope; preserve and test old decoders.
- Acknowledge revisions per peer; a tombstone may be garbage-collected only
  after all required acknowledgements.
- Merge independent edits; report conflicts when both change the same event
  field while offline, rather than silently choosing a winner.
- Only the two enrolled devices may write; validate membership in Firestore
  Security Rules. Relying on a hard-to-guess collection path alone is NOT
  authorization.
- Cloud data is encrypted; a server still learns metadata such as access
  times, request sizes and account identifiers.
- Room remains the read source for UI and widget. WorkManager handles
  deferred sync. The widget itself must not open network connections.

Prerequisites for implementing and testing live sync: a Firebase project,
Firestore, Authentication, approved rules, and two test accounts/devices.
The project config should be supplied via local config or CI secrets.

