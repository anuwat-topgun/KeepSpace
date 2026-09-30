# KeepSpace localization

KeepSpace uses one catalogue to generate native resources for iOS and Android. The
supported locale set matches IPTV Prime: English, Spanish, Brazilian Portuguese,
French, German, Italian, Arabic, Turkish, Russian, Simplified Chinese, Japanese,
Korean, Thai, Indonesian, Vietnamese, Dutch, Polish, Romanian, Greek, Ukrainian,
Hindi, Malay, Traditional Chinese, Swedish, and Czech.

`locales/en.json` is canonical. Other locale files contain reviewed translations and
fall back to English when a new key is not translated yet. Never edit generated
`Localizable.strings`, `InfoPlist.strings`, Android `strings.xml`, or `I18nCatalog.kt`
directly.

```sh
python3 i18n/extract.py       # update canonical static UI copy
python3 i18n/translate_missing.py  # bootstrap missing translations for review
python3 i18n/build.py         # generate native resources
sh i18n/check.sh              # CI drift check
```

Product names, provider content, URLs, file paths, and technical identifiers are not
translated. Interpolated sentences must use explicit format/plural resources; the
extractor deliberately ignores them so a runtime value is never mistaken for a key.
