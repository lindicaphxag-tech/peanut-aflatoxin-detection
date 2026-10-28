# App quality and verification report

Date: 2026-10-28

## Scope and scientific limits

Verification covers the Android and OpenHarmony app projects only. Android performs offline image analysis using hand-built color/texture features and the bundled demonstration model. Those image features do not match the preprocessing used to train the model, and the model has not been validated on laboratory-labelled peanut samples. Its score is not an aflatoxin concentration and must not be used to make food-safety decisions.

## Verification results

- Android `testDebugUnitTest`: **39 executions, 0 failures, 0 errors**. Thirteen Robolectric test methods ran on API 26, 28, and 34. They cover launch, picker behavior and rapid taps, bounded image decoding, finite feature extraction, model reference output and invalid-input rejection, successful and failed image workflows, UI state restoration after Activity recreation, result persistence, history display, and safe refusal of an unsupported database upgrade.
- Android `lintDebug`: **0 errors, 10 warnings**. Eight dependency update suggestions and two potential root-background overdraw notices remain. These are not build blockers; dependency upgrades need compatibility review.
- Android `assembleDebug`: **success**. APK package metadata reports min SDK 26 and target SDK 34. `apksigner` verified its v2 debug signature. No Android emulator or physical device was attached (`adb devices -l` was empty); runtime checks are JVM/Robolectric only.
- OpenHarmony `assembleHap` for the checked-in API 10 project: **success**. The HAP archive was generated, but Hvigor reported `No signingConfig found for product default`; it is unsigned and cannot be installed as delivered. No OpenHarmony emulator or phone runtime test was available. This is not an API 12 build.

## Changes in this verification pass

- Moved Android image analysis from an Activity-owned raw thread into an `AndroidViewModel` coroutine using `Dispatchers.IO` and observable state, so Activity recreation does not retain a destroyed screen or launch a second analysis.
- Added picker/analyzing state guards and error recovery, and close database helpers/results on lifecycle exit.
- Added lifecycle tests and explicit activity teardown to the Robolectric suite.
- Preserved user history by refusing an unimplemented database migration rather than dropping the table.
- Kept the demo-only disclaimer visible; do not infer toxin concentration or safety from this model output.

## What these tests establish

The tests establish deterministic app logic, resource lifecycle behavior under Robolectric, basic input validation, and successful compilation of both platform projects with the available local SDKs. They do not establish behavior on OEM Android builds, Android 15/16, HarmonyOS/OpenHarmony devices, picker permission behavior across vendors, performance or memory under real workloads, analytical sensitivity/specificity, concentration calibration, repeatability, or food-safety suitability.

For scientific validation, freeze the spectrum/image acquisition protocol and preprocessing, use independent peanut samples with reference laboratory measurements, pre-register split strategy and metrics, report calibration and external validation separately, and test device/batch transfer. Do not train/test split repeated measurements from the same physical sample across both folds.
