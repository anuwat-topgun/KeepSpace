#!/bin/zsh
set -euo pipefail

# Builds the Play-uploadable AAB with the upload key kept outside the repository,
# then verifies both the JAR signature and the expected upload-certificate SHA-1.
# Passwords are read from macOS Keychain and are never printed.

SCRIPT_DIR="${0:A:h}"
KEYSTORE_FILE="${KEYSTORE_FILE:-/Users/topgun/.keepspace/credentials/keepspace-upload.jks}"
KEY_ALIAS="${KEY_ALIAS:-keepspace-upload}"
EXPECTED_SHA1="26:F7:E4:4A:BA:F2:4B:C4:F1:0A:29:4B:81:4C:3E:94:E8:86:AE:8D"

if [[ ! -f "$KEYSTORE_FILE" ]]; then
  print -u2 "Upload keystore not found: $KEYSTORE_FILE"
  exit 1
fi

export KEYSTORE_FILE KEY_ALIAS
export KEYSTORE_PASSWORD="$(/usr/bin/security find-generic-password -s com.keepspace.android.upload.keystore.password -w)"
export KEY_PASSWORD="$(/usr/bin/security find-generic-password -s com.keepspace.android.upload.key.password -w)"

if [[ -z "$KEYSTORE_PASSWORD" || -z "$KEY_PASSWORD" ]]; then
  print -u2 "The Android upload-key passwords are missing from macOS Keychain."
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -x /opt/homebrew/opt/openjdk@17/bin/java ]]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
  else
    export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  fi
fi

cd "$SCRIPT_DIR"
./gradlew clean bundleRelease

BUNDLE="$SCRIPT_DIR/app/build/outputs/bundle/release/app-release.aab"
VERIFY_OUTPUT="$("$JAVA_HOME/bin/jarsigner" -verify "$BUNDLE" 2>&1)" || {
  print -u2 "$VERIFY_OUTPUT"
  exit 1
}
if [[ "$VERIFY_OUTPUT" != *"jar verified."* ]]; then
  print -u2 "Release bundle does not contain a valid JAR signature."
  exit 1
fi

ACTUAL_SHA1="$("$JAVA_HOME/bin/keytool" -printcert -jarfile "$BUNDLE" | /usr/bin/awk -F': ' '/SHA1:/{print $2; exit}')"
if [[ "$ACTUAL_SHA1" != "$EXPECTED_SHA1" ]]; then
  print -u2 "Unexpected upload certificate SHA-1: $ACTUAL_SHA1"
  exit 1
fi

print "Signed release bundle verified: $BUNDLE"
print "Upload certificate SHA-1: $ACTUAL_SHA1"
