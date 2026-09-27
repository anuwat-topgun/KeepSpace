# Thai OCR spike (Android) — results, 27 Sep 2026

Test: `ThaiOcrSpikeTest` (instrumented, not shipped). 8 images: Thai receipt/slip screenshots, Thai chat and
shopping screenshots, 2 Thai paper-receipt photos, 1 English receipt photo (control). Scored on the app's own
classifier + receipt extractor (kind, merchant, amount, date = 25 checks). Emulator: Pixel Tablet arm64 on Apple Silicon.

| Engine | Fields correct | CER on Thai | ms / image* |
|---|---|---|---|
| ML Kit Latin (today) | 13 / 25 | 0.58 – 0.89 | ~25 |
| Tesseract fast tha+eng | 16 / 25 | 0.02 – 0.30 | ~165 |
| **Tesseract fast tha+eng + fixes** | **21 / 25** | 0.01 – 0.30 | ~170 |
| Tesseract best tha+eng + fixes | 22 / 25 | 0.01 – 0.14 | ~205 |
| Tesseract fast tha only + fixes | 18 / 25 (breaks English) | — | — |

\* steady-state, emulator; real mid-range phones likely 2–4× slower.

Fixes (post-OCR): sara am written as ํ+า → ำ; "กุย." → "ก.ย." between day and year; drop lines with
Tesseract confidence < 50.

Remaining failures: tone marks sometimes dropped ("ตะกรา", "รานกาแฟบานสวน") so Thai chat/shopping keywords
miss (needs tone-insensitive keyword matching + Thai chat tuning); one noise line ("๕ vwya") with the fast model.
Test images are synthetic (PIL, Ayuthaya font without Thai shaping) — must re-check with real receipts/slips.

Size: +7.5 MB native (arm64, per device with App Bundle) + models 5.2 MB (tha fast 1.07 + eng fast 4.1).

Recommendation: Tesseract fast tha+eng + fixes, run only on receipt candidates / screenshots whose ML Kit text
looks like misread Thai. Next: tone-insensitive matching (both platforms), gating, real-sample test.
