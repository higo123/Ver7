# PVC-Hub security remediation (Ver6 source archive)

## Applied in this source tree

- Removed the committed application keystore and the nested full-source ZIP that duplicated it.
- Added ignore rules for keystores and local signing properties.
- Debug builds now use Android's standard debug signing key. Release signing is read only from environment variables and is unsigned when those values are absent.
- CI no longer uploads debug APKs as user-installable artifacts. It publishes a Release APK only when signing secrets are configured and APK signature verification succeeds.
- SMTP port 465 uses implicit TLS with hostname verification. Other configured SMTP ports require STARTTLS and hostname verification.
- IMAPS connections enable hostname verification.
- Broad notification auto-detection is disabled by default and a one-time preference migration disables it on existing installations. Users can opt in again.
- Notification diagnostics no longer persist message text or sender/title content.
- Backup import enforces a 25 MB input limit, checks app/version, caps record count, and validates key fields before database insertion.
- Update metadata downloads are bounded while streaming, redirects are disabled, and APK URLs must use HTTPS.

## Critical signing-key incident

The previous repository archive contained `app/pvc-hub.keystore` and hard-coded passwords in `app/build.gradle.kts`. Treat that signing key as compromised. Removing it from the current tree does not erase copies from old Git commits, releases, forks, backups, or the nested ZIP previously distributed. Revoke access to old copies where possible and do not reuse that key for new releases.

Android app updates normally require a compatible signing identity. Before publishing with a replacement key, evaluate Android signing-key rotation using a valid signing lineage and test upgrades on devices with the currently installed app. Do not publish a newly signed APK until upgrade compatibility has been verified.

## Configure GitHub Actions for a signed release

Create these repository Actions secrets:

- `PVC_RELEASE_KEYSTORE_BASE64`: base64-encoded keystore file.
- `PVC_RELEASE_STORE_PASSWORD`: keystore password.
- `PVC_RELEASE_KEY_ALIAS`: key alias.
- `PVC_RELEASE_KEY_PASSWORD`: private-key password.

Never use the compromised key as the replacement release key. Store the replacement signing material in a restricted, backed-up secret-management process.

## Validation still required

This archive was edited and statically inspected in a container without an installed Android SDK/Gradle runtime. A successful Android build, device testing, IMAP/SMTP provider compatibility tests, migration testing, and signed-upgrade testing have not yet been demonstrated.
