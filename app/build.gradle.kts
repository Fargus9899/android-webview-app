plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ╔═══════════════════════════════════════════════════════════════╗
// ║                   CONFIGURE YOUR APP HERE                     ║
// ╚═══════════════════════════════════════════════════════════════╝
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
// ═════════════════════════════════════════════════════════════════

android {
    namespace = "com.webapp.app"
    compileSdk = 35

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        resValue("string", "app_name", appName.replace("'", "\\'"))
        resValue("color", "brand", brandColor)
        buildConfigField("String", "APP_URL", "\"$appUrl\"")
        buildConfigField("boolean", "ENABLE_PULL_TO_REFRESH", "$enablePullToRefresh")
        buildConfigField("String", "DEFAULT_THEME", "\"$defaultTheme\"")
        buildConfigField("boolean", "SHOW_THEME_BUTTON", "$showThemeButton")
        buildConfigField("boolean", "AUTO_DARKEN_WEBSITES", "$autoDarkenWebsites")
        buildConfigField("boolean", "OPEN_LINKS_IN_APP", "$openLinksInApp")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.12.1")
}
