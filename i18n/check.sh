#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 i18n/extract.py
python3 i18n/validate.py
python3 i18n/build.py
git diff --exit-code -- i18n/locales/en.json ios/SmartStorage/Resources/Localization android/app/src/main/res android/app/src/main/java/com/smartstorage/cleaner/ui/i18n/I18nCatalog.kt
