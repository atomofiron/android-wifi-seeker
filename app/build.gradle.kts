plugins {
    alias(libs.plugins.android.application)
}

android { // key alias raslav_2016
    val packageName = "ru.raslav.wirelessscan"
    namespace = packageName
    compileSdk = 37

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        minSdk = 23
        targetSdk = 37
        versionCode = 20
        versionName = "2.2.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
            isShrinkResources = true
            val providerAuthority = packageName + snapshots
            manifestPlaceholders["PROVIDER"] = providerAuthority
            buildConfigField("String", "AUTHORITY", "\"$providerAuthority\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    implementation(libs.insets)
    implementation("org.simpleframework:simple-xml:2.7.1") {
        exclude("stax", "stax")
        exclude("stax-api", "stax-api")
        exclude("xpp3", "xpp3")
    }
}