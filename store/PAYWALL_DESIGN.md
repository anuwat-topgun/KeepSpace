# KeepSpace Pro — Paywall design

Design spec for the Free/Pro split and paywall, ready to implement. Decisions and the build checklist live in
[`todo.md`](../todo.md) §3.5; this document is the screen-level design.

**Status: the paywall (§3.1) and the Subscription screen (§3.3) are implemented on iOS and Android; the quota gate (§3.2) is wired into Review (every category and group) and Best Shot; locks in context (§3.4) are wired for every Pro feature (PRO chip + explainer or paywall); the Receipts and Storage Rules screens have no entry point in navigation yet, so they are gated at the destination.** Visuals: [`paywall/paywall-overview.png`](paywall/paywall-overview.png)
(source: [`paywall/paywall-mockup.html`](paywall/paywall-mockup.html), colours are the app's own tokens). The mockups are static HTML:
icons are stand-ins for SF Symbols / Material icons, and prices are the launch defaults — **the real app must read price,
currency, trial length and eligibility from the store, never hard-code them.**

## 1. Principles

1. **Trust first.** KeepSpace's promise is "your photos never leave your device". The paywall repeats it and never uses pressure
   (no countdown timers, no fake discounts, no weekly price, no guilt copy).
2. **Never sell safety.** Safety Score, protected Memories, confirmation before delete, the 30-day trash, and *viewing* scan
   results are free forever. Only *actions beyond the free allowance* and Pro features are gated.
3. **Gate the action, not the information.** Free users see every category and how much it could free. That is the value that
   earns the upgrade.
4. **Never trap a user.** A free user over the limit can always finish with a smaller delete. Lapsed Pro never deletes or hides
   anything the user already has.
5. **Only sell what exists.** Pro lists only shipped features. Auto-filing into cloud via rules and backup verification are not
   built (`todo.md` §3.3–3.4) and must not appear on the paywall or store page until they ship.

## 2. Plans and prices

| Plan | Launch price | Notes |
|---|---|---|
| Free | — | Scan everything, see all categories, Safety Score, protected Memories, Best Shot, Forecast, Weekly Smart Clean; delete ≈ **1 GB / month**; backup ≈ **100 files / month**, 1 cloud account; **1** storage rule |
| Pro · Yearly | $19.99 / year (~฿690) | **7-day free trial**; pre-selected; ≈ $1.67 / month shown as secondary text |
| Pro · Monthly | $2.99 / month (~฿99) | |
| Pro · Lifetime | $39.99 once (~฿1,390) | One-time purchase, no renewal |

Pro adds: unlimited cleanup (whole categories at once), video compression, smart screenshots (per-category browsing), Receipt
Filing, AI Taste, unlimited backup and rules, every cloud account. Allowance numbers are launch defaults to tune from real data.

Product IDs: `com.keepspace.app.pro.annual`, `com.keepspace.app.pro.monthly` (same subscription group), `com.keepspace.app.pro.lifetime`.

## 3. Screens

### 3.1 Paywall (full screen, modal)
Layout, top to bottom: close (×, top-left) · Restore (top-right) · compact header (app icon, "KeepSpace Pro", tagline) ·
plan picker (3 cards) · benefits card (6 rows) · **pinned footer**: primary button, fine print, trust line.

- **Benefits (6):** Unlimited cleanup · Compress videos · Smart screenshots · Receipt Filing · AI Taste · Unlimited backup & rules.
  One line of explanation each. When opened from a feature, that row is highlighted and scrolled into view.
- **Plan picker:** radio cards. Selected = accent border + tinted fill + filled radio (never colour alone). Yearly pre-selected with a
  "Best value" badge. The **billed price is the largest price on the card**; the per-month equivalent is small secondary text.
  A "Save N%" chip may be shown only when computed from the store prices and ≥ 20 %.
- **Primary button and fine print follow the selected plan:**

  | Selected | Button | Fine print |
  |---|---|---|
  | Yearly, trial available | Start 7-Day Free Trial | "7 days free, then {price} per year. Renews automatically until you cancel in your account settings." |
  | Yearly, trial used | Subscribe · {price} / year | "Renews automatically at {price} per year until you cancel." |
  | Monthly | Subscribe · {price} / month | "Renews automatically at {price} per month until you cancel." |
  | Lifetime | Buy Lifetime · {price} | "One-time purchase. No subscription, no renewal." |

  Terms and Privacy links always sit under the fine print. Trust line: "Your photos never leave your device. Pro doesn't change that."
- **States:** *loading prices* → skeleton cards, button disabled · *store unavailable / offline* → "Can't reach the store. Check your connection." with Retry; Free keeps working ·
  *purchase pending* (Ask to Buy / slow payment) → "Waiting for approval", Pro unlocks when it clears · *cancelled by user* → return to the plan picker, no error ·
  *failed* → plain message + Retry, never blame the user · *already Pro* → never shown; the entry points show the plan status instead ·
  *success* → brief confirmation, dismiss, and land back where the user was (the gated action is offered again, not auto-run).
- **Tablet / landscape:** centred column, max width ≈ 560 pt, same order. On wide screens benefits and plans may sit side by side; the footer stays pinned.
- **Dark mode:** same layout with the app's dark tokens (see the second mockup).

### 3.2 Quota gate (bottom sheet)
Shown when the user taps Delete on a selection larger than the remaining free cleanup allowance. It is a sheet over the review screen, not a full paywall.
- Title "You're close to this month's free limit"; body "Your selection is {size}. You can free {remaining} more on Free."; meter "{used} of {limit} used this month · Resets {date}".
- Buttons: **Unlock Unlimited Cleanup** (opens the paywall) · **Delete {remaining} (safest first)** · Not now. "Nothing is deleted without your confirmation."
- "Delete {remaining} (safest first)" trims the selection to fit, ordering by Safety Score (very safe → review first), then goes through the normal system delete confirmation.
- **Allowance used up** (remaining = 0): title "You've used this month's free cleanup"; only Unlock and Not now; no partial option.
- Counting: only bytes the user actually confirmed deleting count. A cancelled system dialog uses nothing. Resets on the 1st of the month, device time.

### 3.3 Settings → Subscription
- **Free:** "You're on the Free plan." A usage card (cleanup, backups, rules, each with a meter that turns amber near the limit; reset date), a list of what Pro adds with PRO chips, **Upgrade to Pro**, **Restore Purchases**.
- **Pro (subscription):** plan name, renewal or end date, "Manage Subscription" (opens the system subscription page), Restore Purchases. **Pro (lifetime):** "Lifetime — thank you", Restore Purchases only.
- The existing Settings row "Subscription · KeepSpace Pro" gets the live status as its subtitle ("Free", "Pro · renews Oct 28", "Pro · Lifetime").

### 3.4 Locks in context
Pro features stay visible. Tapping one opens the paywall focused on that feature. Rules:
- A small PRO chip on the row or button; never blur, hide or fake-load content.
- Smart-screenshot categories: counts and sizes visible to everyone; opening a category's contents is Pro.
- Compress, Receipt Filing, AI Taste: the entry exists on Free; tapping shows a one-screen explainer ("what this does") with Unlock, not an error.
- Rule Builder: the first rule is free; creating a second opens the paywall. Cloud: a second account, or backups past the allowance, opens it.

> **Implementation note:** the plan picker sits *above* the benefits (the mockups had them the other way round). On a phone the six benefit rows pushed every price below the fold, so the price was only visible in the fine print — the billed price should be seen before the button.

## 4. Where it opens

| Trigger | What opens |
|---|---|
| Delete over the free allowance (Review, Best Shot, Cleanup Plan) | Quota gate (3.2) |
| Compress video, open a screenshot category, Receipt Filing, AI Taste, 2nd rule, 2nd cloud account, backup past allowance | Paywall, that benefit highlighted |
| Settings → Subscription → Upgrade | Paywall |
| Never | App launch, onboarding, or while a scan is running |

At most one paywall presentation per session after a dismissal, unless the user taps something locked again.

## 5. Copy and localization
All strings go through the existing catalogue (`i18n/locales/en.json` is canonical; run `python3 i18n/extract.py`, then `build.py`).
Strings with runtime values (prices, sizes, dates, counts) must use explicit format/plural resources, because the extractor deliberately
skips interpolated sentences. Prices and currency come from the store's localized price strings, never from our catalogue.
Test the paywall in all 25 locales, including **Arabic (right-to-left)** and long languages (German), at the largest Dynamic Type / font scale.

## 6. Store rules to satisfy (verify against the current guidelines before submitting)
- Show the real localized price from StoreKit 2 / Play Billing; the billed amount is the most prominent price.
- Auto-renew disclosure next to the button for subscriptions; how to cancel; Terms and Privacy links; Restore Purchases reachable from the paywall and Settings.
- Free trial: say it's a trial, the length, and the price after it; a user who already had the trial doesn't see trial wording.
- Lifetime = non-consumable (iOS) / one-time product (Android). Enable Family Sharing for the iOS products unless there is a reason not to.
- No claims about features that don't exist yet. Review notes should explain how a reviewer can test purchase and restore.
- App Privacy / Data safety answers don't change (still no data collected); the privacy policy and terms must mention subscriptions.

## 7. Accessibility
- Plan cards are one radio group; VoiceOver/TalkBack reads "Yearly, $19.99 per year, 7 days free, best value, selected".
- Selection is shown by border, fill and radio mark, not colour alone. Targets ≥ 44 pt. Contrast meets WCAG AA in both themes.
- The footer is pinned; the content above it scrolls so large text never pushes the button off screen.
- Meters have text equivalents ("0.9 of 1 GB used").

## 8. Entitlement behaviour (no backend)
Entitlement comes straight from the store (`Transaction.currentEntitlements` / Play Billing purchases), cached locally so the app works offline with a short grace period.
There are no accounts. When Pro ends: **nothing is deleted** — rules beyond the first are switched off (kept), a second cloud account stays connected but queues nothing new,
files already backed up stay in the user's cloud, and AI Taste keeps its learned weights but stops learning.

## 9. Open items
- Final allowances (≈ 1 GB cleanup, ≈ 100 backup files) and the 7-day trial length — tune from real conversion data.
- Regional prices per storefront (set in App Store Connect and Play Console).
- Whether Lifetime is shown to everyone or only after the first paywall dismissal.
- Owner tasks: create the products, agreements/tax/banking, Small Business Program (see `todo.md` §3.5).
