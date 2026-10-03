#!/usr/bin/env python3
"""Update the canonical English catalogue from static UI strings.

The application predates localization and intentionally keeps readable English at the
call sites.  Apple uses that English as the lookup key.  Android's generated catalogue
maps the same source string to a semantic resource name.  This extractor only accepts
static text; interpolated sentences must use the explicit formatting helpers instead.
"""

from __future__ import annotations

import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "i18n/locales/en.json"

SCAN_ROOTS = (
    ROOT / "ios/SmartStorage/Features",
    ROOT / "ios/SmartStorage/Shell",
    ROOT / "ios/SmartStorage/DesignSystem/Components",
    ROOT / "android/app/src/main/java/com/smartstorage/cleaner/ui",
)

# Generated catalogues must never be fed back into extraction. Debug routes are identifiers,
# not visible copy.
SKIP_FILES = {"I18nCatalog.kt", "DebugLaunch.swift"}

# Permission prompts are defined in project.yml rather than in a view source file.
EXTRA = {
    "KeepSpace needs access to analyze and back up the photos and videos you choose. AI analysis stays on this device; backups go directly to your selected Google Drive or OneDrive account.": "permissions.photo_library",
    "KeepSpace saves a compressed copy only when you choose to compress a video. You review the result before removing the original.": "permissions.photo_library_add",
    # Model-driven UI strings do not appear as literals at their final Text call sites.
    "Very safe": "safety.very_safe",
    "Safe": "safety.safe",
    "Review first": "safety.review_first",
    "Exact Duplicates": "review.exact_duplicates",
    "Blurry Photos": "review.blurry_photos",
    "Old Screenshots": "review.old_screenshots",
    "Old Screen Recordings": "review.old_screen_recordings",
    "Expired Tickets": "review.expired_tickets",
    "Identical copies of the same file. One copy of each is kept — your favourite, or else the oldest.": "review.duplicates_explanation",
    "Extra shots from bursts. The best photo of each group is kept.": "review.similar_explanation",
    "Photos that came out blurry or shaky.": "review.blurry_explanation",
    "Screenshots older than 30 days. Receipts and upcoming tickets are left out.": "review.old_screenshots_explanation",
    "Screen recordings older than 30 days.": "review.old_recordings_explanation",
    "Your biggest videos. Nothing is selected until you choose.": "review.large_videos_explanation",
    "Boarding passes and tickets for dates that have passed.": "review.expired_explanation",
    "Sorted by what's in them, read on this device. Nothing is selected until you choose.": "review.screenshot_category_explanation",
    # Paywall strings with runtime values (prices, counts, dates); the scanner skips interpolated code, so they are listed here.
    "Start %d-Day Free Trial": "paywall.start_trial",
    "Subscribe · %@ / year": "paywall.subscribe_yearly",
    "Subscribe · %@ / month": "paywall.subscribe_monthly",
    "Buy Lifetime · %@": "paywall.buy_lifetime",
    "%d days free, then %@ per year. Renews automatically until you cancel in your account settings.": "paywall.fine_print_trial",
    "Renews automatically at %@ per year until you cancel.": "paywall.fine_print_yearly",
    "Renews automatically at %@ per month until you cancel.": "paywall.fine_print_monthly",
    "%d days free": "paywall.days_free",
    "Save %d%%": "paywall.save_percent",
    "≈ %@ / month": "paywall.per_month_equivalent",
    "%@ of %@ used": "subscription.bytes_used",
    "%d of %d files used": "subscription.files_used",
    "%d of %d used": "subscription.count_used",
    "Resets %@": "subscription.resets",
    "Free trial ends %@, then renews.": "subscription.trial_renews",
    "Free trial ends %@.": "subscription.trial_ends",
    "Renews %@": "subscription.renews",
    "Ends %@": "subscription.ends",
    "Pro · renews %@": "subscription.pro_renews",
    "Pro · ends %@": "subscription.pro_ends",
    "Free includes %@ of cleanup each month. It resets on %@.": "quota_gate.exhausted_message",
    "Your selection is %@. You can free %@ more on Free.": "quota_gate.partial_message",
    "%@ of %@ used this month · Resets %@": "quota_gate.meter",
    "Delete %@ (safest first)": "quota_gate.delete_safest",
    # Store-layer notices (LibraryStore / CloudStore) and a few strings the scanner can't see.
    "%d item (%@) moved to Recently Deleted. Empty it in Photos to free the space now.": "notice.deleted_one_ios",
    "%d items (%@) moved to Recently Deleted. Empty it in Photos to free the space now.": "notice.deleted_many_ios",
    "%d item (%@) moved to Trash. Empty it in your gallery to free the space now.": "notice.deleted_one_android",
    "%d items (%@) moved to Trash. Empty it in your gallery to free the space now.": "notice.deleted_many_android",
    "%d item (%@) deleted.": "notice.deleted_one_direct",
    "%d items (%@) deleted.": "notice.deleted_many_direct",
    "Couldn't delete: %@": "notice.delete_failed",
    "Compressed %@ → %@. The original is in Recently Deleted.": "notice.compressed_ios",
    "Compressed %@ → %@. The original is in Trash.": "notice.compressed_android",
    "Analysis cache cleared. KeepSpace will look at your library again.": "notice.cache_cleared",
    "%@ connected. Backups go directly to this account.": "notice.cloud_connected",
    "%@ disconnected. Existing cloud files were not changed.": "notice.cloud_disconnected",
    "Connect %@ before starting a backup.": "notice.connect_first",
    "No %@ are available to back up.": "notice.nothing_to_back_up",
    "Queued %d items for %@.": "notice.queued",
    "Pending backup cancelled.": "notice.backup_cancelled",
    "remaining": "insights.remaining",
    "photos added": "insights.photos_added",
    "videos added": "insights.videos_added",
    "Shopping": "screenshot.shopping",
    "Receipts": "screenshot.receipts",
    "Chats": "screenshot.chats",
    "QR Codes": "screenshot.qr_codes",
    "Tickets": "screenshot.tickets",
    "Other": "common.other",
    "Food & Drink": "receipt_category.food_and_drink",
    "Groceries": "receipt_category.groceries",
    "Transport": "receipt_category.transport",
    "Bills": "receipt_category.bills",
    "Health": "receipt_category.health",
    "Transfer": "receipt_category.transfer",
    "1080p · High quality": "compression.1080p_high_quality",
    "720p · Smaller file": "compression.720p_smaller_file",
    "Suggest deletion": "rules.suggest_deletion",
    "Keep on device": "rules.keep_on_device",
    "Suggest deletion after 30 days": "rules.suggest_deletion_after_30_days",
    "Favorites": "rules.favorites",
    "Waiting": "cloud_status.waiting",
    "Uploading": "cloud_status.uploading",
    "Verifying": "cloud_status.verifying",
    "Backed up": "cloud_status.backed_up",
    "Not backed up": "cloud_status.not_backed_up",
    "Failed": "cloud_status.failed",
    "Cancelled": "cloud_status.cancelled",
    "Sharpest image": "best_shot.sharpest_image",
    "Details are the clearest in this group.": "best_shot.sharpest_detail",
    "Best faces": "best_shot.best_faces",
    "Faces are the clearest and best framed.": "best_shot.best_faces_detail",
    "Best exposure": "best_shot.best_exposure",
    "Well-balanced lighting.": "best_shot.best_exposure_detail",
    "Your favorite": "best_shot.your_favorite",
    "You marked this photo as a favorite.": "best_shot.your_favorite_detail",
}

COMMON = {
    "About", "Back", "Cancel", "Category", "Content", "Date", "Day", "Delete",
    "Destination", "Disconnect", "Done", "Enabled", "Folder", "Home", "Library",
    "Next", "Notifications", "OK", "Off", "On", "Photo", "Photos", "Preview",
    "Privacy Policy", "Reset", "Retry", "Review", "Save", "Settings", "Size",
    "Terms of Use", "Time", "Today", "Video", "Videos", "Week",
}

NON_TRANSLATABLE = {
    "KeepSpace", "BKK", "TYO", "iPhone", "iPad", "Google Drive", "OneDrive",
    "AndroidX, Jetpack Compose, Room, Media3", "Kotlin, Kotlin coroutines",
    "ML Kit (text, face, barcode)", "Tesseract OCR (Thai text)",
    "Tesseract Thai & English models", "Tesseract4Android", "Apache 2.0",
    "Google APIs terms", "UTC", "5 GB", "10 GB", "20 GB",
}

STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')
ASCII_WORD = re.compile(r"[A-Za-z]")
TECHNICAL = re.compile(r"^[a-z0-9_.:/{}-]+$")


def decode(value: str) -> str:
    return value.replace(r"\n", "\n").replace(r'\"', '"').replace(r"\\", "\\")


def candidate(value: str) -> bool:
    if not value or not ASCII_WORD.search(value):
        return False
    if "\\(" in value or "$" in value or "${" in value:
        return False
    if value.startswith(("http://", "https://")) or TECHNICAL.fullmatch(value):
        return False
    if value.startswith(")") or " as? " in value or value.endswith("??"):
        return False
    if " " not in value and value[:1].islower() and any(character.isupper() for character in value):
        return False
    if value in NON_TRANSLATABLE:
        return False
    # SF Symbols and route fragments are implementation detail, not copy.
    if " " not in value and "." in value and value.lower() == value:
        return False
    if value in {"%,d", "%02d:%02d", "+"}:
        return False
    return len(value.strip()) > 1


def namespace(path: Path) -> str:
    stem = re.sub(r"(?:Screen|View)$", "", path.stem)
    words = re.sub(r"(?<!^)(?=[A-Z])", "_", stem).lower()
    return words or "ui"


def slug(text: str) -> str:
    value = re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")
    if not value:
        value = "text"
    return value[:56].rstrip("_")


def main() -> None:
    occurrences: dict[str, list[Path]] = defaultdict(list)
    for directory in SCAN_ROOTS:
        for path in directory.rglob("*"):
            if path.suffix not in {".swift", ".kt"} or path.name in SKIP_FILES or "i18n" in path.parts:
                continue
            for line in path.read_text(encoding="utf-8").splitlines():
                # The simple string scanner cannot safely parse nested quotes inside Swift/Kotlin
                # interpolation. Dynamic sentences use explicit format resources instead.
                if r"\(" in line or "${" in line:
                    continue
                for match in STRING.finditer(line):
                    value = decode(match.group(1)).strip()
                    if candidate(value):
                        occurrences[value].append(path)

    previous: dict[str, str] = {}
    if OUT.exists():
        previous = json.loads(OUT.read_text(encoding="utf-8")).get("strings", {})
    key_for_source = {source: key for key, source in previous.items()}

    strings: dict[str, str] = dict(EXTRA)
    # EXTRA is source -> key for readability above; canonical JSON is key -> source.
    strings = {key: source for source, key in EXTRA.items()}
    used = set(strings)
    for source in sorted(occurrences, key=str.casefold):
        existing = key_for_source.get(source)
        if existing and existing not in used:
            key = existing
        else:
            prefix = "common" if source in COMMON or len(set(occurrences[source])) > 1 else namespace(occurrences[source][0])
            base = f"{prefix}.{slug(source)}"
            key = base
            if key in used:
                key = f"{base}_{hashlib.sha1(source.encode()).hexdigest()[:8]}"
        strings[key] = source
        used.add(key)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"strings": strings}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"catalogued {len(strings)} static UI strings")


if __name__ == "__main__":
    main()
