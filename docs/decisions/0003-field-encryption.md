# 0003: Encrypt sensitive fields individually with AES-256-GCM

Status: accepted

## Context

VitalSafe stores health readings. A stolen database or backup must not reveal them.

## Decision

- Reading values and notes are encrypted before they reach the database, using AES-256-GCM with a fresh
  random 12-byte IV for every value. GCM also detects tampering.
- The key comes from the `ENCRYPTION_KEY` environment variable (32 random bytes, base64). It is different
  from `JWT_SECRET`, never committed, and backed up separately from database backups.
- Stored values look like `v1.<base64>`. The version prefix lets a future key change tell old values from new.
- Reading type, unit, time and owner stay in plain text, because the database must sort, filter and chart them.

## Consequences

- A leaked database or backup shows no readings and no notes without the key.
- The plain-text columns reveal that a user records a given kind of reading, and when.
- This is not end-to-end encryption. The operator holds the key and the running server can decrypt.
- If the key is lost, the encrypted fields cannot be recovered. Losing it is equivalent to deleting the data.
- Nothing ties a ciphertext to its row, so someone with write access to the database could copy one
  encrypted value onto another row. Binding ciphertexts to row identifiers is a possible later hardening.
- There is no key rotation tooling yet. The version prefix is the hook for adding it.

## Generating a key

PowerShell:

    $b = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    [Convert]::ToBase64String($b)