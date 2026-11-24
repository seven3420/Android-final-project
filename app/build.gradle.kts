plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.edu.sicnu.cs.stu.niuwenya.studentc"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.edu.sicnu.cs.stu.niuwenya.studentc"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // C-任务表：开启 View Binding，页面统一用 ActivityXxxBinding 取控件
    buildFeatures {
        viewBinding = true
    }
}

// 单元测试运行环境修正。
// 有些开发机的系统 PATH 里存在未闭合的引号（例如 "C:\Program Files\...\javapath;D:\java home\jdk\bin"），
// Gradle 启动测试 JVM 时会把 PATH 拼进 -Djava.library.path，未闭合引号会让后续参数解析错位，
// 表现为测试进程一启动就报 ClassNotFoundException: home\IntelliJ，用例根本没跑起来。
// 这里把传入测试进程的 PATH 中的引号剥掉，问题与机器无关，队友拉下来也能直接跑测试。
tasks.withType<Test>().configureEach {
    val rawPath = providers.environmentVariable("PATH").getOrElse("")
    val safePath = rawPath.replace("\"", "")
    environment("PATH", safePath)
    // Gradle 会把 java.library.path 以 -D 参数传给测试 JVM，而它的默认值包含整条 PATH；
    // 去掉引号后重新显式设置，保证拼出来的命令行不再被引号截断。
    systemProperty("java.library.path", safePath)

    // 本地联调时可用 -Dorder.baseUrl=http://A的局域网IP:8000/ 指向成员 A 的服务端
    val overrideBaseUrl = providers.systemProperty("order.baseUrl").orNull
    if (!overrideBaseUrl.isNullOrBlank()) {
        systemProperty("order.baseUrl", overrideBaseUrl)
    }

    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.zxing:core:3.5.2")
}