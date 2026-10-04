# Card Scanner — Visa Card Camera Scanner → Excel

An Android application that scans a physical Visa card with the phone camera using a
fast, Google-Pay-style scanning interface, keeps **only** masked card data, and exports
the records to a formatted `.xlsx` workbook.

The app is a **card inventory / record-keeping tool**. It is **not** a payment terminal:
it cannot authorize, charge, or process payments, and it never reads a CVV/CVC, magnetic
stripe, or EMV chip data.

---

## 1. Security model (read this first)

The single most important property of this app is what it **cannot** do.

| Prohibited | How it is prevented |
| --- | --- |
| Store the complete PAN | No field or database column can hold a PAN. `CardRecord` has only `maskedPan` + `last4`. |
| Export the complete PAN | The exporter serializes only masked data, then re-scans the finished workbook and **rejects** any 13–19 digit PAN-like run. |
| Retain the PAN in memory | The PAN lives in a `TransientPan` whose digit array is zeroed on `consume()`; a second `consume()` throws. |
| Store CVV / CVC / CID / PIN | There is no recognition path and no column for them. The scanner has no "back of card" mode at all. |
| Store magnetic-stripe / EMV data | No track or chip parsing exists anywhere in the codebase. |
| Save card photographs | Capture is in-memory (`ImageCapture` → `Bitmap`); the bitmap is recycled right after OCR. Nothing is written to files, MediaStore, or cache. |
| Upload card data | The app declares **no `INTERNET` permission**, so it is technically incapable of network access. No analytics, ads, or crash-reporting SDK is present. |
| Cloud OCR | ML Kit **bundled** on-device Latin text recognition (`com.google.mlkit:text-recognition`), not the Play Services variant. Works offline. |
| Leak via backups | `android:allowBackup="false"` plus `data_extraction_rules.xml` excluding every domain from cloud backup and device transfer. |
| Leak via screenshots | `FLAG_SECURE` is applied in `MainActivity.onCreate` for the whole app. |
| Leak via logs | OCR text is never logged. `LogSanitizer` masks PAN-shaped runs in the few diagnostic messages that exist. |
| Leak via navigation/IPC | The pending scan result is held in activity-scoped ViewModels, never in Intent extras or navigation arguments. |

### PAN lifecycle

```
camera frame (RAM)
      ↓  CardDetector          (card silhouette, quality gate)
      ↓  ImageProcessor        (crop + contrast, RAM only)
      ↓  ML Kit OCR            (on-device)
      ↓  PanDetector           (digit extraction → length → Luhn → Visa prefix)
      ↓  TransientPan          ← the complete number exists ONLY here
      ↓  consume()             → masked PAN + last 4 ; digit array zeroed
      ↓  OcrResult             ← only SafeCardData crosses the module boundary
      ↓  Room / Excel          ← masked + last 4 only, forever
```

`PanResult.Valid` is the only carrier of the transient object, and it is consumed inside
`CardOcrEngine.analyzeLines` in a single expression, so no reference to it survives.

---

## 2. Requirements

- Android Studio (current stable) with Android SDK **35**
- JDK **17** (bundled with recent Android Studio)
- Android device or emulator running **Android 10 (API 29)** or newer
- A physical device is recommended — the emulator has no real camera

## 3. Build

```bash
# from the project root
./gradlew assembleDebug        # debug APK
./gradlew test                 # JVM unit tests (security + logic)
./gradlew lint                 # Android lint
./gradlew connectedAndroidTest # instrumented tests (device/emulator required)
```

On Windows use `gradlew.bat` instead of `./gradlew`.

APK output: `app/build/outputs/apk/debug/app-debug.apk`

For a release build:

```bash
./gradlew assembleRelease
```

The release variant enables R8 minification and resource shrinking and, by default,
signs with the debug keystore so the build is runnable out of the box. **Replace the
signing config with your own keystore before distributing.**

> **Note on wrapper JARs.** This repository contains the Gradle wrapper configuration
> (`gradle/wrapper/gradle-wrapper.properties`) but not the `gradle-wrapper.jar` binary.
> Android Studio regenerates it on first sync. From the command line, either open the
> project in Android Studio once, or run `gradle wrapper` with a locally installed
> Gradle 8.9+.

## 4. First run

1. Grant the camera permission when prompted (camera is the only permission requested).
2. Point the camera at a Visa card placed on a flat, well-lit surface.
3. Hold the card inside the rounded frame — the border turns green and the app captures
   automatically once the frame is sharp, well lit, and stable.
4. Confirm the recognized **safe** fields; optionally edit the cardholder name, expiry
   date, and notes. There is no field for entering a card number.
5. `Confirm & Save` stores the record locally. Duplicates (same brand + last 4 + expiry)
   prompt before saving again.
6. `Export` in the history screen writes `Card_Records_YYYY-MM-DD_HH-mm.xlsx` to a
   location you choose via the system file picker.

## 5. Architecture

```
UI (Compose) → ViewModel → Scanner/Recognition layer → Secure data processor
             → Local repository (Room) → Excel export
```

```
app/src/main/java/com/example/cardscanner/
├── camera/
│   ├── CardCameraManager.kt   CameraX binding, in-memory still capture, torch
│   ├── CardDetector.kt        ID-1 silhouette detection, quality, stability tracker
│   └── ImageProcessor.kt      crop + contrast normalization (RAM only)
├── ocr/
│   ├── CardOcrEngine.kt       ML Kit wrapper + safe-field reduction
│   ├── PanDetector.kt         PAN candidate detection and validation
│   ├── ExpiryDetector.kt      MM/YY(YY) → (month, year)
│   ├── CardholderDetector.kt  name-line heuristics
│   └── OcrLine.kt             module-private raw OCR line
├── security/
│   ├── PanSanitizer.kt        masking + `TransientPan`
│   ├── LuhnValidator.kt       Luhn checksum + brand prefix rules
│   ├── LogSanitizer.kt        masks PAN-shaped text in diagnostics
│   └── SensitiveDataGuard.kt  FLAG_SECURE
├── data/
│   ├── CardRecord.kt          domain model (no PAN-capable field)
│   ├── CardRecordEntity.kt    Room entity + duplicate index
│   ├── CardDao.kt             queries, search, duplicate check
│   ├── CardDatabase.kt        Room database + Instant converter
│   └── CardRepository.kt      local-only repository
├── export/
│   ├── ExcelExporter.kt       approved columns → workbook → security scan
│   ├── XlsxWriter.kt          dependency-free OOXML writer
│   └── ExportSecurityValidator.kt  rejects PAN-like content
└── ui/
    ├── CardScannerApp.kt      navigation host
    ├── AppViewModel.kt        shared, activity-scoped state holder
    ├── theme/Theme.kt
    ├── scanner/               scanner screen + state machine
    ├── confirmation/          confirm/edit/save + duplicate dialog
    └── history/               searchable history + Excel export
```

### Why not Apache POI?

The original brief suggested Apache POI. POI is a poor fit for Android: it is very large
(method-count and APK-size), depends on `java.awt`-adjacent APIs, and requires core
library desugaring plus broad R8 keep rules that routinely break release builds.
`XlsxWriter` produces a standards-compliant `.xlsx` (bold header, frozen top row,
auto-filters, sized columns, alternating row fill, real date formatting, sheet named
`Card Records`) using only the JDK `java.util.zip` API — which also makes the whole
export path unit-testable on the JVM.

## 6. Excel schema

Sheet name: `Card Records`

| # | Column | Notes |
| --- | --- | --- |
| 1 | Record ID | stable UUID |
| 2 | Card Brand | always `VISA` |
| 3 | Masked Card Number | `**** **** **** 1234` |
| 4 | Last 4 Digits | `1234` (numeric) |
| 5 | Expiration Month | `12` |
| 6 | Expiration Year | `2029` |
| 7 | Cardholder Name | as recognized or edited |
| 8 | Scanned Date | `dd/mm/yyyy hh:mm` |
| 9 | Notes | free text |

Formatting applied: bold white-on-indigo header, frozen first row, auto-filter, widened
columns, alternating row shading, and centered numeric/date columns.

## 7. Tests

`./gradlew test` (JVM, no device needed):

- `RecognitionSecurityTest` — Luhn rejection (Test 6), non-Visa → unsupported (Test 7),
  transient PAN destruction (Test 1), safe-field-only result, log masking (Test 5).
- `ExcelExportSecurityTest` — no PAN in workbook (Test 2), PAN-bearing workbook rejected,
  approved columns only (Test 9), masked form present.
- `DetectionAndModelTest` — card shape accepted, tiny object rejected, blank frame
  rejected, duplicate key semantics.

`./gradlew connectedAndroidTest` (device/emulator):

- `CardDaoSecurityTest` — masked-only storage (Test 1), no CVV column (Test 3), duplicate
  detection (Test 8), search cannot reveal a PAN (Test 10).
- `SecurityEnvironmentTest` — no image files on disk (Test 4), `FLAG_SECURE` applied.

### Spec test coverage map

| Spec test | Where |
| --- | --- |
| 1. No complete PAN in Room | `CardDaoSecurityTest.roomStoresOnlyMaskedAndLast4`, `RecognitionSecurityTest.transientPanIsDestroyedAfterConsumption` |
| 2. No complete PAN in Excel | `ExcelExportSecurityTest.noCompletePanIsWrittenToExcel` |
| 3. No CVV can be saved | `CardDaoSecurityTest.schemaHasNoCvvColumn` |
| 4. No card images on disk | `SecurityEnvironmentTest.noCardImagesAreWrittenToDisk` |
| 5. No OCR text in logs | `RecognitionSecurityTest.logSanitizerMasksPanShapedText` |
| 6. Luhn-invalid rejected | `RecognitionSecurityTest.luhnRejectsInvalidNumbers` |
| 7. Non-Visa → unsupported | `RecognitionSecurityTest.nonVisaIsRejectedAsUnsupported` |
| 8. Duplicate detected | `CardDaoSecurityTest.duplicateIsDetected` |
| 9. Excel columns whitelisted | `ExcelExportSecurityTest.excelExportContainsOnlyApprovedColumns` |
| 10. Search cannot reveal PAN | `CardDaoSecurityTest.searchCannotRevealCompletePan` |

## 8. Localization & accessibility

- All strings externalized: `res/values/strings.xml` (English) and
  `res/values-ar/strings.xml` (Arabic).
- `android:supportsRtl="true"` — the Arabic locale lays out right-to-left automatically.
- TalkBack content descriptions on the scan frame, quality indicator, and all icon
  buttons; layout supports large font scales, portrait and landscape, light and dark.

## 9. Permissions

Only `android.permission.CAMERA`. File export uses the Storage Access Framework, so no
storage permission is required. Manifest application tag:

```xml
<application
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    ... />
```

## 10. Offline guarantee

Camera, card detection, OCR, recognition, local history, and Excel generation all work
with the device in airplane mode. There is no network code in the project: no HTTP
client dependency, no analytics SDK, no ad SDK, no crash reporter, and no `INTERNET`
permission in the manifest.

## 11. Known limitations

- **Card detection** uses gradient-profile edge detection rather than a learned model.
  It reliably finds a card on a contrasting, reasonably flat background but can be
  fooled by strong rectangular patterns. Replace `CardDetector` with an OpenCV
  `Imgproc.findContours`-based or ML-based detector if you need production-grade
  robustness.
- **OCR** handles horizontal, upright card faces. Perspective normalization is limited to
  cropping and contrast normalization; heavily angled cards may need a manual retry.
- **`last4` is stored as an integer** in the spreadsheet (`1234`, not `"1234"`) so that
  Excel treats it as a number; leading zeros are impossible for a card's last four digits
  in practice but would be dropped if they occurred.
- Detection thresholds in `CardDetector.Config` and `StabilityTracker` are tuned
  heuristically; adjust `requiredStableFrames` if you want faster or more deliberate
  auto-capture.
- The release build is signed with the debug keystore by default — replace it before
  distribution.

## 12. License / usage

Intended for legitimate card inventory and record-keeping where the operator is
authorized to handle the physical cards. Because no full PAN, CVV, or track data is ever
stored, the app keeps only the truncated representation described above.
