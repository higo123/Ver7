# PVC-Hub clean rebuild audit

## Source baseline

This package is reconstructed from the archived `PVC-Hub-Complete-Source-Premium-Minimal-GitHub-Fix(1).zip` (67 files) and the project requirements supplied on 20/11/2026. The archive contains the Android application source, resources, manifest, Gradle project files, CI workflow, README, and security remediation notes.

The current `main` branch was inspected at commit `90a2afba0fa23f2c135b62ef2e2b353ec1a13a61`. The latest observed CI run was `44898941451` (21/11/2026). Android SDK setup and package verification passed; Gradle configuration failed before any build task because `app/build.gradle.kts` began with `excludes += setOf(...)` and lacked its `plugins {}` and `android {}` scopes.

## Repairs in this clean tree

1. Recreate the normal Android project root from the complete source archive rather than retaining the duplicated `Ver6-main/` directory wrapper.
2. Restore the full `app/build.gradle.kts` structure from the hardened source baseline.
3. Add `androidx.compose.material:material-icons-extended`, required by `InboxScreen.kt` imports and calls to `Icons.Default.AccessTime`.
4. Use the SDK installation commands from the latest repository workflow run, which was observed to pass: platform-tools, Android platform 35, and build-tools 35.0.0.
5. Preserve the source package's security model: no committed keystore, no passwords in Gradle, and release signing only from GitHub Actions secrets.
6. Include the original product requirements document unchanged.

## Product requirements baseline

- Product: PVC Hub, unified Android inbox.
- UI direction: Premium Minimal, comfortable density, neutral surfaces, accent `#00A479`, light/dark theme support.
- Primary navigation: Hộp thư, Ưu tiên, Đã hoãn, Thiết lập.
- Keep Email, Điện thoại, Chat, Mạng xã hội and individual email accounts in the side drawer.
- Do not create fake Danh bạ or AI tabs before the underlying data/features exist.
- Preserve existing core email, SMS, missed-call, notification capture, Peek, snooze, backup/restore, widget, and Quick Settings functions as represented by the source baseline.
- Future features (bulk actions, undo, advanced search, follow-up reminders, contact profiles, AI) remain roadmap items unless implemented in source.

## File map

- `app/src/main/AndroidManifest.xml`: Android permissions, activity, services, receivers, FileProvider.
- `app/src/main/java/vn/pvc/hub/MainActivity.kt`, `PvcHubApp.kt`: app entry.
- `data/`: account preferences, Room entities/DAO/database, filter rules.
- `sources/`: Gmail IMAP/SMTP, SMS, call log, notification capture, Peek, workers, backup, widgets, Quick Settings.
- `ui/`: inbox, reading, setup, settings, lock, theme, dialogs and components.
- `app/src/main/res/`: launcher, widget, themes, colors, strings and FileProvider paths.
- Root Gradle files: Android Gradle Plugin 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Java 17.
- `.github/workflows/build.yml`: CI checks `testDebugUnitTest`, `lintDebug`, `assembleDebug`; signed release only when all secrets exist.

## Security warning

The previous source lineage contained a committed application keystore and hard-coded password. The security remediation document correctly treats that signing key as compromised. This clean package excludes keystores and nested source archives. Do not reuse the old key. A new release key may prevent in-place updates; signing lineage/upgrade compatibility must be planned and tested before distributing a replacement-signed release.

## Validation status

Static checks performed for this package: source tree extracted; 67 project files present before adding this audit/spec; Gradle structure restored; `AccessTime` dependency added; no keystore/JKS/APK/local.properties included; CI SDK install steps aligned with the observed passing run.

Not yet verified: Android Gradle execution, Kotlin compilation, unit tests, lint, APK assembly, install/upgrade on physical devices, and live provider integration. The current execution environment has no confirmed Android SDK/Gradle runtime. Therefore this package is a clean, internally consistent source handoff, not a claimed successful build.
