# App quality and verification report

Date: 2026-10-29

## Scope and scientific limits

Verification covers the Android and OpenHarmony app projects only. Android performs offline image analysis using hand-built color/texture features and the bundled demonstration model. Those image features do not match the preprocessing used to train the model, and the model has not been validated on laboratory-labelled peanut samples. Its score is not an aflatoxin concentration and must not be used to make food-safety decisions.

## Verification results

- Android `testDebugUnitTest`: **44 executions, 0 failures, 0 errors**. Fourteen Robolectric test methods ran on API 26, 28, and 34, plus two tests on API 34. Coverage includes launch, picker behavior and rapid taps, bounded image decoding, finite feature extraction, model reference output and invalid-input rejection, successful and failed image workflows, UI state restoration, record persistence, version-1 database migration, export JSON and document-picker intent, and refusal of an unsupported database upgrade. The actual document-provider write was not exercised on a device.
- Android `lintDebug`: **0 errors, 12 warnings**. Ten dependency update suggestions and two potential root-background overdraw notices remain. These are not build blockers; dependency upgrades need compatibility review.
- Android `assembleDebug`: **success**. APK package metadata reports min SDK 26 and target SDK 34. `apksigner` verified its v2 debug signature. No Android emulator or physical device was attached (`adb devices -l` was empty); runtime checks are JVM/Robolectric only.
- OpenHarmony `assembleHap` for the checked-in API 10 project: **success**. The HAP archive was generated, but Hvigor reported `No signingConfig found for product default`; it is unsigned and cannot be installed as delivered. No OpenHarmony emulator or phone runtime test was available. This is not an API 12 build.
- Added `.github/workflows/android-ci.yml` to run Android unit tests, lint, APK assembly, and upload reports/artifact on pushes and pull requests. The workflow YAML parsed locally; GitHub Actions has not run it yet because this branch has not been pushed with an authorized GitHub identity.

## Changes in this verification pass

- Moved Android image analysis from an Activity-owned raw thread into an `AndroidViewModel` coroutine using `Dispatchers.IO` and observable state, so Activity recreation does not retain a destroyed screen or launch a second analysis.
- Added picker/analyzing state guards and error recovery, and close database helpers/results on lifecycle exit.
- Added lifecycle tests and explicit activity teardown to the Robolectric suite.
- Added Android history schema version 2. A version-1 database is migrated without deleting records; provenance for old records remains unknown. Unsupported future migrations are refused rather than dropping data.
- New Android records contain SHA-256 digests of the model and scaler assets, a feature-pipeline version, and a digest of the 256×256 grayscale ARGB pixels supplied to feature extraction. This pixel digest is not a hash of the original image file. The history screen exports a versioned JSON document without image URIs; it contains neither source images nor raw spectra, so it does not by itself permit complete experiment reproduction.
- OpenHarmony has not yet received the new provenance and export features; its HAP result above is for the existing implementation.
- Kept the demo-only disclaimer visible; do not infer toxin concentration or safety from this model output.

## What these tests establish

The tests establish deterministic app logic, resource lifecycle behavior under Robolectric, basic input validation, and successful compilation of both platform projects with the available local SDKs. They do not establish behavior on OEM Android builds, Android 15/16, HarmonyOS/OpenHarmony devices, picker permission behavior across vendors, performance or memory under real workloads, analytical sensitivity/specificity, concentration calibration, repeatability, or food-safety suitability.

For scientific validation, freeze the spectrum/image acquisition protocol and preprocessing, use independent peanut samples with reference laboratory measurements, pre-register split strategy and metrics, report calibration and external validation separately, and test device/batch transfer. Do not train/test split repeated measurements from the same physical sample across both folds.
