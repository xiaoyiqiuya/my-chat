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
        versionCode = 9
        versionName = "0.1.8"
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
        // 双构建：modern/ 与 legacy/ 各有一份 main.*.js
        for (bundle in listOf("modern", "legacy")) {
            val jsDir = www.resolve(bundle).resolve("static/js")
            val main = jsDir.listFiles()?.firstOrNull { it.name.startsWith("main.") && it.name.endsWith(".js") }
            check(main != null && main.length() > 0L) {
                "assets/www/$bundle 缺少有效的 main.*.js，装出来会白屏"
            }
            check(html.contains("$bundle/static/js/${main.name}")) {
                "index.html 没有引用 $bundle/static/js/${main.name}，双构建选择器与产物不一致"
            }
        }
        check(html.contains("modern/static/css/main.") && html.contains("legacy/static/css/main.")) {
            "index.html 里缺少 modern/legacy 的样式引用"
        }
    }
}

tasks.named("preBuild") {
    dependsOn(verifyWebAssets)
}
