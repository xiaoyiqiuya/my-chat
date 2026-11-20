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
        versionCode = 5
        versionName = "0.1.4"
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

/**
 * 构建前校验离线资源：index.html 引用的脚本/样式必须存在且非空。
 * 之前出现过 main.*.js 被写成 0 字节、装出来白屏的情况，这里直接让构建失败。
 */
val verifyWebAssets by tasks.registering {
    val www = file("src/main/assets/www")
    doLast {
        val index = www.resolve("index.html")
        check(index.isFile) { "assets/www/index.html 缺失，无法打包离线页面" }
        val html = index.readText()
        val refs = Regex("(?:src|href)=\"([^\"]+)\"").findAll(html).map { it.groupValues[1] }
        for (ref in refs) {
            if (ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("//")) continue
            val target = www.resolve(ref)
            check(target.isFile) { "index.html 引用的资源不存在：$ref" }
            check(target.length() > 0L) { "index.html 引用的资源是空文件：$ref（会导致白屏）" }
        }
        val main = www.resolve("static/js").listFiles()?.firstOrNull {
            it.name.startsWith("main.") && it.name.endsWith(".js")
        }
        check(main != null && main.length() > 0L) { "assets/www 里没有有效的 main.*.js，装出来会白屏" }
    }
}

tasks.named("preBuild") {
    dependsOn(verifyWebAssets)
}
