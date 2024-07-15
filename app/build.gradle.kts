plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    val packageName = "ru.raslav.wirelessscan"
    namespace = packageName
    compileSdk = 34

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        minSdk = 21
        targetSdk = 34
        versionCode = 39
        versionName = "2.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildTypes {
        val fileProvider = ".FileProvider"
        getByName("debug") {
            applicationIdSuffix = ".debug"
            val providerAuthority = packageName + applicationIdSuffix + fileProvider
            manifestPlaceholders["PROVIDER"] = providerAuthority
            buildConfigField("String", "AUTHORITY", "\"$providerAuthority\"")
        }
        getByName("release") {
            isMinifyEnabled = true
            val providerAuthority = packageName + fileProvider
            manifestPlaceholders["PROVIDER"] = providerAuthority
            buildConfigField("String", "AUTHORITY", "\"$providerAuthority\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("androidx.preference:preference-ktx:1.2.1")

    implementation("io.github.atomofiron:extended-insets:2.0.0-rc1")
    implementation("org.simpleframework:simple-xml:2.7.1") {
        exclude("stax", "stax")
        exclude("stax-api", "stax-api")
        exclude("xpp3", "xpp3")
    }
}