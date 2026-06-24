# Contacts SMS Call Backup Manager

A complete, secure, and modern Android application written entirely in **Java** that allows users to backup and restore Contacts, SMS messages, and Call Logs to internal storage or a custom folder selected via the Storage Access Framework (SAF).

## Features

- **Contacts Backup**:
  - Read all contacts using the `ContactsContract` API.
  - Extract: Display Name, Mobile Numbers, Email Addresses, and Organization.
  - Export to CSV, JSON, and XML format.

- **SMS Backup**:
  - Read all SMS messages (Inbox, Sent, Drafts, etc.) from the device content provider.
  - Extract: Sender/Receiver Address, Message Body, Date/Time, Type, and Thread ID.
  - Export to CSV, JSON, and XML format.

- **Call Log Backup**:
  - Read call history logs using the `CallLog` API.
  - Extract: Contact Name, Phone Number, Call Type (Incoming, Outgoing, Missed, Rejected, Blocked, etc.), Duration, and Date/Time.
  - Export to CSV, JSON, and XML format.

- **Storage & Backup Locations**:
  - **Internal App Storage**: Fallback directory within the app's secure sandbox.
  - **External Custom Folder**: User-selected directory (including SD Card) using Android's Storage Access Framework (SAF) with persisted permissions.

- **Background Processing & Notifications**:
  - Utilizes `WorkManager` for reliable background processing.
  - Displays persistent foreground service notifications showing real-time progress percentages during backup.

- **Security & Privacy**:
  - Zero internet permission (`android.permission.INTERNET` is not requested).
  - All data processed and stored strictly offline on-device.
  - No analytics or third-party tracking libraries.

---

## Technical Architecture

- **Language**: Java 8 compatible (No Kotlin files)
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)
- **Background Engine**: WorkManager for robust deferrable background jobs
- **UI styling**: Material Design components, CoordinatorLayout, DrawerLayout, Grid layouts, and custom status tracking panels.

---

## Required Permissions

- `android.permission.READ_CONTACTS`: Query device address book.
- `android.permission.READ_SMS`: Query SMS database.
- `android.permission.READ_CALL_LOG`: Retrieve call history.
- `android.permission.POST_NOTIFICATIONS`: Display background backup progress on Android 13+.
- `android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_DATA_SYNC`: WorkManager foreground task compatibility.

---

## How to Build and Run

1. Open the project root folder in **Android Studio**.
2. Wait for Gradle sync to complete.
3. Build the project using `Build` -> `Make Project` or run `./gradlew assembleDebug` from the terminal.
4. Deploy the application to an emulator or physical device.
