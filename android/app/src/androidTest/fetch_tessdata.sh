#!/bin/sh
# Downloads the Tesseract models used by ThaiOcrSpikeTest (gitignored; ~13 MB, Apache-2.0).
set -e
cd "$(dirname "$0")/assets"
mkdir -p tess_fast/tessdata tess_best/tessdata
base=https://github.com/tesseract-ocr
curl -sSL -o tess_fast/tessdata/tha.traineddata $base/tessdata_fast/raw/main/tha.traineddata
curl -sSL -o tess_fast/tessdata/eng.traineddata $base/tessdata_fast/raw/main/eng.traineddata
curl -sSL -o tess_best/tessdata/tha.traineddata $base/tessdata_best/raw/main/tha.traineddata
cp tess_fast/tessdata/eng.traineddata tess_best/tessdata/eng.traineddata
ls -l tess_fast/tessdata tess_best/tessdata
