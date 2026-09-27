# Keep rules are added per feature as needed.

# Tesseract4Android calls back into these classes from JNI and ships no consumer rules.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
