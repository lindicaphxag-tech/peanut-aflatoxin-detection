# App verification report

Date: 2026-10-28

## Scope

Verification covers the Android and OpenHarmony app projects only. The Android app is an offline image-analysis demo. Its hand-built image features do not match the preprocessing used to train the included model, and the model has not been validated on laboratory-labelled peanut samples. Its output is not an aflatoxin measurement and must not be used to decide food safety.

## Results

- Android `testDebugUnitTest`: **33 executions, 0 failures, 0 errors**. The 11 Robolectric test methods ran on API 26, 28, and 34. Coverage includes screen launch, image-picker intent, valid and unreadable image handling, finite 16-feature extraction, bounded image decode size, model input/threshold validation, a reference scaler/model inference value, result persistence/display, history display, and refusal to upgrade the history database without a migration.
- Android `lintDebug`: **0 errors, 10 warnings**. The remaining warnings are eight dependency-version suggestions and two intentional background overdraw reports. No Android instrumentation or physical-device test ran.
- Android `assembleDebug`: **success**. The generated debug APK uses the standard debug signing key.
- OpenHarmony `assembleHap` (API 10 project): **success**. The SDK emitted `No signingConfig found for product default`; the resulting HAP is unsigned and is not installable as delivered. No Harmony emulator or physical device test ran. The project SDK also reports that the `phone` device type is unsupported by that SDK version.

## App changes checked

- Added input and scaler validation and capped Android bitmap decode dimensions before resizing.
- Added tests that cover success and failure paths for image selection and inference.
- Removed destructive database upgrade behavior; until a migration is written, upgrades fail without dropping history.
- Added Android cloud-backup and device-transfer exclusions for private app data, including Android 12+ extraction rules. See the [Android backup configuration guidance](https://developer.android.com/about/versions/12/behavior-changes-12#backup-restore).
- Corrected Android history row inflation so the parent layout parameters are applied, and moved visible Android labels into string resources.
- Clearly identify both app outputs as an unvalidated demo and avoid calling the score an aflatoxin concentration.

## Remaining validation

This verification establishes buildability and deterministic app logic only. It does not establish analytical sensitivity, specificity, calibration, repeatability, device equivalence, or food-safety performance. Those claims require a frozen preprocessing/model pipeline, independent laboratory-labelled samples, a predefined evaluation protocol, and suitable near-infrared hardware. Device installation, picker permission behavior across OEMs, rotation/process-death behavior, Harmony signing, and Harmony runtime behavior remain unverified.
