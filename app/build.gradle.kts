plugins {
    id("com.android.application")
}

android {
    namespace = "com.qiuzh.deepseekchat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.qiuzh.deepseekchat"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.1.3"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            // Debug 签名，方便直接安装试用；正式发布请换成自己的 keystore
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = false
    }
}
