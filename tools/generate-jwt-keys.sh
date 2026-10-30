#!/usr/bin/env bash
# Prints a fresh Ed25519 key pair for std-dive-logger access tokens (ws signs, import-ws and the
# gas tracker verify) as PEM, plus the env var lines. Nothing is written to disk.
# Use a different pair per environment (local, dev, prod). Needs OpenSSL >= 1.1.1 - macOS's
# /usr/bin/openssl is LibreSSL without Ed25519: `brew install openssl@3` or set OPENSSL=<path>.
set -euo pipefail

OPENSSL="${OPENSSL:-openssl}"
if ! private_pem="$("$OPENSSL" genpkey -algorithm ed25519 2>/dev/null)"; then
  echo "error: '$OPENSSL' cannot generate Ed25519 keys ($("$OPENSSL" version 2>&1 || true))." >&2
  echo "       Install OpenSSL >= 1.1.1 (brew install openssl@3) or set OPENSSL=/path/to/openssl." >&2
  exit 1
fi
public_pem="$(printf '%s\n' "$private_pem" | "$OPENSSL" pkey -pubout)"
# kid = first 16 chars of base64url(SHA-256(public key X.509 DER)) - what ws puts in the header.
kid="$(printf '%s\n' "$public_pem" | "$OPENSSL" pkey -pubin -outform DER \
  | "$OPENSSL" dgst -sha256 -binary | "$OPENSSL" base64 -A | tr '+/' '-_' | tr -d '=' | cut -c1-16)"

# One line with literal \n - the form GitHub secrets, kube-secret literals and .env files need.
one_line() { printf '%s\n' "$1" | awk '{ printf "%s%s", (NR > 1 ? "\\n" : ""), $0 }'; }

cat <<OUT
# Ed25519 access-token key pair, kid ${kid}
# The PRIVATE key is a secret (ws only). The public key is not sensitive.

${private_pem}
${public_pem}

# Env vars (ws needs both; import-ws only the public key; real newlines work as well):
STD_DIVE_LOGGER_WS_JWT_SIGNING_KEY='$(one_line "$private_pem")'
STD_DIVE_LOGGER_WS_JWT_PUBLIC_KEY='$(one_line "$public_pem")'

# GitHub environment (dev / prod, each its own pair) - the values without the quotes:
#   secret   JWT_SIGNING_KEY = the STD_DIVE_LOGGER_WS_JWT_SIGNING_KEY value
#   secret   JWT_PUBLIC_KEY  = the STD_DIVE_LOGGER_WS_JWT_PUBLIC_KEY value
# Gas tracker: add the public key to ch.sthomas.stddivegastracker.auth.public-keys.
OUT
