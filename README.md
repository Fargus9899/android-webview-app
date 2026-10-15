# WebApp — Turn Any Website into a Native Android App

A lightweight, production-ready Android Studio template that wraps any **HTTPS** website in a native Android app. Set your URL, name, color, and icon, then build an APK in minutes.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Target SDK](https://img.shields.io/badge/targetSdk-35-blue)
![License](https://img.shields.io/badge/license-MIT-green)

## Features

- **Native splash screen** in your brand color, shown until the first page loads
- **Loading progress bar** and **pull-to-refresh**
- **Back button** navigates your site's history before closing the app
- **Offline screen** with a "Try again" button that resumes where you left off
- **Smart link handling**: your domain and subdomains stay in-app; external links, `tel:`, `mailto:`, WhatsApp, and Maps open in the right app
- **File uploads** from the device
- **Downloads** saved to the `Downloads` folder with a notification
- **Fullscreen video** support
- **Persistent cookies and logins**
- **Light / Dark / Automatic theme** picker with a draggable floating button; websites follow the theme via `prefers-color-scheme` (with optional automatic darkening)
- **In-app browser** for external links: slides up smoothly with a Firefox-style bottom toolbar (close ✕ on the left, page title & domain, share, menu with reload / copy link / open in browser)
- **Edge-to-edge UI** with status bar icons that adapt automatically to your brand color
- **Secure by default**: HTTPS only, no file access, no JavaScript bridge, hardened `intent://` handling

## Requirements

- [Android Studio](https://developer.android.com/studio) (Ladybug or newer)
- Android SDK 35 (install it from **Tools → SDK Manager**)
- JDK 17 or newer (bundled with Android Studio)

## Quick Start

### 1. Get the code

```bash
git clone https://github.com/<your-username>/<your-repo>.git
```

Or click **Use this template** on GitHub to create your own copy.

Then open the folder in Android Studio via **File → Open**. The first Gradle sync downloads dependencies and may take a few minutes.

### 2. Configure your app

Everything lives in one block at the top of `app/build.gradle.kts`:

```kotlin
val appName = "My Website"                  // Name shown under the icon
val appUrl = "https://www.example.com"      // Website to load (HTTPS only)
val appId = "com.example.mywebsite"         // Unique ID (e.g. com.yourname.appname)
val brandColor = "#0F172A"                  // Splash, system bars, and buttons
val appVersionCode = 1                      // Increment for every release
val appVersionName = "1.0.0"
val enablePullToRefresh = true              // Pull down to reload

// Appearance
val defaultTheme = "system"                 // "system", "light" or "dark" (first launch)
val showThemeButton = true                  // Floating button to switch theme
val autoDarkenWebsites = true               // Darkens sites without a native dark mode

// In-app browser
val openLinksInApp = true                   // External links open in the in-app browser
```

> **Tip:** with `showThemeButton = false` you can still open the theme picker from your own website: add a link to `webapp://theme` (e.g. `<a href="webapp://theme">Theme</a>`).

Click **Sync Now** in the yellow banner after editing.

> **Tip:** `appId` must be unique on the Play Store and can't be changed after publishing. Use lowercase letters, numbers, dots, and underscores only.

### 3. Set your icon

1. In the **Project** panel (Android view), right-click `app` → **New → Image Asset**
2. **Icon type:** Launcher Icons (Adaptive and Legacy) · **Name:** `ic_launcher`
3. **Foreground Layer → Source Asset:** select **Image** and pick your PNG (512×512 or larger, transparent background)
4. Adjust **Resize** until your logo fits inside the safe-zone circle
5. **Background Layer:** select **Color** and use the same value as `brandColor`
6. Click **Next → Finish** and confirm overwriting the existing files

### 4. Build the APK

**Debug build (for testing):**
**Build → Build App Bundle(s) / APK(s) → Build APK(s)**, then click **locate** in the notification.
Output: `app/build/outputs/apk/debug/app-debug.apk`

**Release build (for distribution):**
**Build → Generate Signed App Bundle or APK…** → choose **APK** (or **Android App Bundle** for Google Play) → create a new keystore → select **release**.

> ⚠️ **Back up your keystore and passwords.** Without them you can never publish an update to your app.

## Continuous Integration

This repo includes a GitHub Actions workflow (`.github/workflows/build.yml`) that builds a debug APK on every push and pull request to `main`. Download it from the **Actions** tab → select a run → **Artifacts**.

## Troubleshooting

| Problem | Solution |
|---|---|
| `SDK location not found` | Go to **File → Project Structure → SDK Location** and set the Android SDK path, or install SDK 35 from **Tools → SDK Manager**. |
| Gradle JDK incompatible | Go to **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** and select **jbr-17** or **jbr-21**. |
| Blank page or SSL error | The URL must start with `https://`. Cleartext HTTP is blocked for security. |
| "App not installed" on device | An app with the same `appId` but a different signature is already installed. Uninstall it first. |
| Google Sign-In fails | Google blocks OAuth inside WebViews. Use email login or a native sign-in flow instead. |
| A download doesn't start | `blob:` URLs generated by JavaScript aren't supported by WebView. Link directly to the file instead. |

## Project Structure

```
app/
├── build.gradle.kts                  # App configuration (start here)
└── src/main/
    ├── AndroidManifest.xml
    ├── java/com/webapp/app/
    │   └── MainActivity.kt           # WebView setup and app logic
    └── res/
        ├── layout/activity_main.xml  # WebView, progress bar, offline screen
        ├── values/                   # Strings, colors, themes
        ├── drawable/                 # Icons and shapes
        └── mipmap-anydpi-v26/        # Adaptive launcher icon
```

## Contributing

Contributions are welcome! Please open an issue to discuss significant changes before submitting a pull request.

## License

Released under the [MIT License](LICENSE).
