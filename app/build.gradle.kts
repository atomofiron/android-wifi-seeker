plugins {
    id("com.android.application")
    kotlin("android")
}

android { // key alias raslav_2016
    val packageName = "ru.raslav.wirelessscan"
    namespace = packageName
    compileSdk = 36

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        minSdk = 21
        targetSdk = 36
        versionCode = 19
        versionName = "2.2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildTypes {
        val snapshots = ".snapshots"
        getByName("debug") {
            applicationIdSuffix = ".debug"
            val providerAuthority = packageName + applicationIdSuffix + snapshots
            manifestPlaceholders["PROVIDER"] = providerAuthority
            buildConfigField("String", "AUTHORITY", "\"$providerAuthority\"")
        }
        getByName("release") {
            isMinifyEnabled = true
            val providerAuthority = packageName + snapshots
            manifestPlaceholders["PROVIDER"] = providerAuthority
            buildConfigField("String", "AUTHORITY", "\"$providerAuthority\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.preference:preference-ktx:1.2.1")

    implementation("io.github.atomofiron:extended-insets:2.0.0")
    implementation("org.simpleframework:simple-xml:2.7.1") {
        exclude("stax", "stax")
        exclude("stax-api", "stax-api")
        exclude("xpp3", "xpp3")
    }
}