import java.io.File

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.hajimi24"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.hajimi24"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.exp4j)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0") // 请检查最新版本
}



tasks.register("generateFloatLayouts") {
    group = "custom"
    doLast {
        val resPath = "${projectDir}/src/main/res/layout"
        val baseFiles = listOf("layout_float_cards", "layout_float_ops", "layout_float_actions")
        val angles = listOf(90, 180, 270)

        baseFiles.forEach { base ->
            val baseFile = File(resPath, "${base}_0.xml")
            if (baseFile.exists()) {
                val originalContent = baseFile.readText()

                angles.forEach { angle ->
                    var content = originalContent

                    // 1. 文字旋转：给所有 Button 和 WebView 注入 rotation
                    content = content.replace("<Button ", "<Button android:rotation=\"$angle\" ")
                    content = content.replace("<android.webkit.WebView ", "<android.webkit.WebView android:rotation=\"$angle\" ")

                    if (angle == 90 || angle == 270) {
                        // 2. 交换外层布局方向
                        content = content.replace("android:orientation=\"vertical\"", "android:orientation=\"horizontal\"")

                        // 3. 交换 GridLayout 的行列
                        val colRegex = Regex("""android:columnCount="(\d+)"""")
                        val rowRegex = Regex("""android:rowCount="(\d+)"""")
                        val colVal = colRegex.find(content)?.groupValues?.get(1) ?: "1"
                        val rowVal = rowRegex.find(content)?.groupValues?.get(1) ?: "1"
                        content = content.replaceFirst(colRegex, "android:columnCount=\"$rowVal\"")
                        content = content.replaceFirst(rowRegex, "android:rowCount=\"$colVal\"")

                        // 4. 【核心修复】不使用复杂正则，直接分两步强制替换 Handle 的宽和高
                        // 第一步：先标记 Handle 这一行，防止误伤其他 View
                        content = content.replace("android:id=\"@+id/handle\"", "android:id=\"@+id/handle\" FLAG_ROTATED=\"true\"")

                        // 第二步：如果一行包含标记，则执行宽高的绝对值替换
                        val lines = content.split("\n").map { line ->
                            if (line.contains("android:id=\"@+id/handle\"") && line.contains("FLAG_ROTATED=\"true\"")) {
                                line.replace(Regex("""android:layout_width="[^"]+""""), "android:layout_width=\"14dp\"")
                                    .replace(Regex("""android:layout_height="[^"]+""""), "android:layout_height=\"match_parent\"")
                                    .replace(" FLAG_ROTATED=\"true\"", "")
                            } else {
                                line
                            }
                        }
                        content = lines.joinToString("\n")

                        // 5. 修正工具窗 WebView 的比例
                        content = content.replace("android:layout_width=\"330dp\"", "android:layout_width=\"60dp\"")
                        content = content.replace("android:layout_height=\"60dp\"", "android:layout_height=\"330dp\"")
                    }

                    val outputFile = File(resPath, "${base}_${angle}.xml")
                    outputFile.writeText(content)
                }
            }
        }
    }
}




// 依然挂载到 preBuild
tasks.named("preBuild") {
    dependsOn("generateFloatLayouts")
}
