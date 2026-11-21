# AutoLedger — Local-First Double-Entry Expense Manager with Auto UPI/SMS Capture

AutoLedger combines **Money Manager's** offline-first double-entry ledger with **FinArt-style** automatic Indian bank SMS and UPI notification tracking.

## Features
- **Double-Entry Accounts & Transfers:** Cash, Bank Accounts (with optional last-4-digit masked account matching), Cards, and UPI Wallets. Every Income, Expense, and Transfer updates balances atomically in Room SQLite.
- **Instant Auto-Capture (SMS + Notifications):** Automatically reads incoming Indian bank SMS (HDFC, SBI, ICICI, Axis, Kotak, and generic banks) and UPI notifications (Google Pay, PhonePe, Paytm, BHIM, CRED, Amazon Pay).
- **OTP Filter & Deduplication:** Automatically ignores OTPs, payment requests, and bill-due alerts, and deduplicates matching SMS + UPI notification pairs via UTR and a 2-minute window.
- **Smart Auto-Categorizer + Memory:** Categorizes transactions automatically and learns from your edits (tap any transaction to change its category and future payments to that merchant will auto-categorize).
- **One-Time 90-Day SMS Backfill:** Import your past 90 days of bank/UPI SMS in one tap.
- **Monthly Charts & Category Budgets:** Donut chart breakdown and customizable monthly category budget progress bars.
- **CSV (Excel) Export & SQLite Backup/Restore:** Export all transactions to an Excel-compatible CSV or back up/restore the raw SQLite database locally.

---

## Easiest Way to Build Your APK (No Android Studio Needed)

You can compile the `.apk` for free in ~3 minutes using **GitHub Actions**:

1. Unzip `AutoLedger-Android-Project.zip` on your computer (or phone).
2. Sign in to [GitHub](https://github.com) and create a **new private repository** (e.g., `AutoLedger`).
3. Upload all files inside the `AutoLedger` folder to your repository (make sure the `.github/workflows/build-apk.yml` file is included; using Git CLI `git init && git add . && git commit -m "init" && git push` preserves hidden `.github` folders automatically).
4. Open the **Actions** tab in your GitHub repository → click **Build AutoLedger APK** → wait for the green checkmark.
5. Scroll down to **Artifacts** and download **`AutoLedger-APK`** (`app-debug.apk`).
6. Transfer `app-debug.apk` to your Android phone and tap to install.

## Alternative: Build Locally in Android Studio
1. Install Android Studio (Ladybug or newer) with JDK 17.
2. Open the unzipped `AutoLedger` folder.
3. Wait for Gradle Sync to finish, then go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. Find the APK at `app/build/outputs/apk/debug/app-debug.apk`.

## First-Time Setup on Your Phone
1. Open the **More** tab and grant **Bank SMS Access** and **Notification Listener** access.
2. Tap **Battery & OEM Background Fix** and set battery to **Unrestricted** (on Xiaomi/Redmi/POCO, also enable *Autostart* and *Read service SMS messages*).
3. Open the **Accounts** tab, tap your bank accounts (HDFC, SBI, ICICI, etc.), and enter the **last 4 digits** of each account number so SMS alerts automatically link to the right account.
4. Go back to **More** and tap **One-Time Scan: Past 90 Days SMS** to import your recent history.
