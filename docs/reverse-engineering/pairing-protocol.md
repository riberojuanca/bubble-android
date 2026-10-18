# Pairing protocol

Bubble pairs with wireless debugging by itself, bit-compatible with AOSP's `adb pair`, without any third-party library. The implementation is in [`PairingClient.java`](../../bubble-app/src/main/java/org/bubble/app/PairingClient.java); this page documents the protocol it follows.

## Primary sources

AOSP `platform/packages/modules/adb`, branch `main`:

- `pairing_connection/pairing_connection.cpp`: TLS and packet exchange
- `pairing_auth/pairing_auth.cpp`: SPAKE2 roles and names, encryption
- `pairing_auth/aes_128_gcm.cpp`: HKDF and nonces
- `tls/tls_connection.cpp`: TLS 1.3 and keying-material export
- `crypto/rsa_2048_key.cpp`: adb public-key format
- `proto/pairing.proto`: packet types

SPAKE2 itself: BoringSSL `crypto/curve25519/spake25519.cc`. Public-key encoding: `system/core/libcrypto_utils/android_pubkey.c`.

## Steps (client side)

### 1. TLS 1.3

Connect to the pairing port with TLS 1.3 exactly (min = max), presenting the app's key and self-signed certificate. Accept any server certificate: trust comes from the PAKE that follows.

Export 64 bytes of keying material (RFC 5705) with the label **`"adb-label\0"`** (AOSP passes `sizeof`, which includes the terminating null) and an empty context. On Android: `android.net.ssl.SSLSockets.exportKeyingMaterial(socket, "adb-label\0", null, 64)`.

### 2. Password

`password = ASCII(6-digit code) || exported_keying_material(64)`. This binds the PAKE to this TLS session.

### 3. SPAKE2 over Ed25519 (BoringSSL `spake25519`)

- Client role: **Alice**. Names, null included: `"adb pair client\0"` (own), `"adb pair server\0"` (peer).
- Points M and N, 32 bytes little-endian (from BoringSSL):
  - M: `5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e`
  - N: `10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778`
- `generate_msg`: private scalar = 64 random bytes → `sc_reduce` → ×8. `password_scalar = sc_reduce(SHA-512(password))`, with BoringSSL's adjustment that clears the low 3 bits by adding multiples of the group order. Message: `P* = P + password_scalar·M` (32 bytes).
- `process_msg`: `Q = Q* − password_scalar·N`; `dh = private·Q`; key = `SHA-512` over the length-prefixed (8-byte little-endian length) concatenation of: own name, peer name, own message, peer message, `dh`, `SHA-512(password)`. 64 bytes.

The Ed25519 point arithmetic (decode, add, subtract, scalar multiplication) is ported from the reference implementation.

### 4. AES-128-GCM

- `key = HKDF-SHA256(key_material, salt = empty, info = "adb pairing_auth aes-128-gcm key", 16)`. Here the info has **no** null byte (AOSP uses `sizeof − 1`).
- 16-byte tag. Nonce: 12-byte little-endian counter, separate for encryption and decryption, each starting at 0 and incremented per message.

### 5. Packets

Header, 6 bytes: `uint8 version = 1`, `uint8 type` (0 = `SPAKE2_MSG`, 1 = `PEER_INFO`), `uint32 payload size` big-endian. Maximum payload 16384.

1. Send `SPAKE2_MSG` with our 32-byte message.
2. Read the peer's `SPAKE2_MSG`; derive the key.
3. Send `PEER_INFO`, encrypted.
4. Read `PEER_INFO`, decrypt; it must be 8192 bytes.

### 6. PeerInfo

8192 bytes: `uint8 type` (0 = `ADB_RSA_PUB_KEY`) and `uint8 data[8191]` with the adb public key, null-terminated, zero-padded. The key: `base64(android_pubkey_encode(RSA)) + " bubble@device"`. `android_pubkey_encode` is adb's own binary format (modulus and exponent in little-endian words with a header).

On success the daemon stores the key in `adb_keys` permanently, and from then on a normal adb-TLS connection is enough.

## Pitfalls found

- The two different null-byte conventions (label and names with `\0`; HKDF info without) are the usual cause of a silent failure.
- The key never leaves the keystore: TLS client authentication signs through the provider, so pairing works with a non-exportable StrongBox key.
