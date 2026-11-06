plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// رقم الإصدار بيزيد تلقائياً مع كل بيلد على GitHub (GITHUB_RUN_NUMBER)
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.education.nativeboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.education.nativeboard"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
