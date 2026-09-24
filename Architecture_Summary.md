# App Blocker Architecture & Functionality Summary

This document summarizes the core components and logic implemented in this project to serve as a blueprint for future professional development.

## 1. Core Architecture
- **Dual-Layer Detection**: Synchronized logic between `UsageStatsManager` (polling) and `AccessibilityService` (event-driven) to achieve near-zero latency.
- **Service Persistence**: Uses a `Foreground Service` with a persistent notification and a `WorkManager` keep-alive mechanism to prevent system termination.
- **Direct Boot Support**: 
    - Database is stored in `Device Protected Storage`.
    - Components are marked `directBootAware="true"`.
    - Migration logic handles the transition from `LOCKED_BOOT` to `USER_UNLOCKED`.

## 2. Key Modules & Logic
### Detection & Blocking (`AppBlockerService.kt` & `AppBlockerAccessibilityService.kt`)
- **Dual-Service Coordination**: Synchronized logic between `UsageStatsManager` (polling) and `AccessibilityService` (event-driven) to achieve near-zero latency. Both services share a common state for blocked apps, schedules, and strict mode settings.
- **40ms Polling**: High-frequency check using `UsageStatsManager` in `AppBlockerService` for reactive blocking even when accessibility events are delayed.
- **Window State Tracking**: `AccessibilityService` catches app launches instantly and provides secondary protection.
- **Strict Mode Enforcement**: When active, only allowed apps are accessible. Both services propagate the `STRICT_MODE` flag to `LockOverlayActivity` to disable bypasses (like back navigation).
- **Statistics Integration**: Both services contribute to `DailyFocusStats` in Room, incrementing `appsBlockedToday` consistently. `AppBlockerService` additionally tracks device unlocks, focus session counts, and total focus time (based on screen-off duration).
- **Keyword detection**: `AccessibilityService` scans screen content for blocked keywords during active schedules, providing deep content-level blocking.
- **Grace Periods**: 
    - 1.5s window after a correct PIN entry.
    - 1.0s window after System UI (notifications/shade) interactions to prevent "lock-loops".

### Security & Recovery
- **Lock Overlay**: A `singleTop` Activity that intercepts back presses and stays on top.
- **Backdoor PIN**: Implementation of `0000` as a hardcoded emergency bypass for debugging and recovery.
- **Device Admin (Planned)**: Use of Device Administration API to prevent unauthorized uninstallation.

### Whitelisting Logic
- **Critical Whitelist**: Hardcoded package names for Phone, SMS, Contacts, and WhatsApp to ensure emergency communication is never blocked.
- **Dynamic Whitelist**: Automatically detects and whitelists the user's default Launcher and Dialer.

### Data Management (`Room`)
- **AppDatabase**: Reactive flows (`Flow`) for real-time updates of blocked apps and schedules.
- **InternetSchedules**: Supports time-range blocking and day-of-week bitmasks.

## 3. OEM Optimizations
- **Manufacturer Intents**: Specialized flows for Xiaomi (MIUI), Oppo, Vivo, and Huawei to guide users through "Auto-start" and "Background Pop-up" permission settings.

## 4. Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Database**: Room
- **Background**: Coroutines, WorkManager, Foreground Services
- **API Targets**: Android 8.0 (API 26) through Android 14+

## 5. File Mapping (for migration)
- **Core Logic**: `AppBlockerService.kt`, `AppBlockerAccessibilityService.kt`
- **Blocking UI**: `LockOverlayActivity.kt`
- **Management UI**: `MainActivity.kt`, `MainViewModel.kt`
- **Persistence**: `data/AppDatabase.kt`, `data/BlockedApp.kt` (Room + Device Protected Storage)
- **Reliability**: `BlockerWorker.kt` (WorkManager), `BootReceiver.kt` (Direct Boot), `AdminReceiver.kt` (Uninstall Protection)
- **Config**: `AndroidManifest.xml` (Permissions & Service declarations), `res/xml/accessibility_service_config.xml`, `res/xml/device_admin_receiver.xml`

## 6. Pro-Tips for "Professional" Version
- Use **Dagger/Hilt** for dependency injection.
- Implement **Clean Architecture** (Data, Domain, UI layers).
- Add **Remote Sync** for parental control (Firebase/Ktor).
- Improve **Battery efficiency** by dynamically scaling polling rates based on user activity.
