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
        versionName = "2.3"

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
                    // 1. 仅给 Button 注入旋转
                    content = content.replace(Regex("""<Button(\s+)""")) {
                        "<Button android:rotation=\"$angle\"${it.groupValues[1]}"
                    }

                    if (angle == 90 || angle == 270) {
                        content = content.replace("android:orientation=\"vertical\"", "android:orientation=\"horizontal\"")

                        // 2. 交换 GridLayout 行列
                        val colRegex = Regex("""android:columnCount="(\d+)"""")
                        val rowRegex = Regex("""android:rowCount="(\d+)"""")
                        val cols = colRegex.find(content)?.groupValues?.get(1) ?: "1"
                        val rows = rowRegex.find(content)?.groupValues?.get(1) ?: "1"
                        content = content.replaceFirst(colRegex, "android:columnCount=\"$rows\"")
                        content = content.replaceFirst(rowRegex, "android:rowCount=\"$cols\"")

                        // 3. 对调所有组件的宽高数值
                        val lines = content.split(Regex("""\r?\n""")).map { line ->
                            var l = line
                            if (l.contains("id=\"@+id/handle\"")) {
                                l = l.replace(Regex("""android:layout_width="[^"]+""""), "android:layout_width=\"14dp\"")
                                l = l.replace(Regex("""android:layout_height="[^"]+""""), "android:layout_height=\"match_parent\"")
                            } else if (l.contains("<Button") || l.contains("WebView")) {
                                val wM = Regex("""android:layout_width="(\d+)dp"""").find(l)
                                val hM = Regex("""android:layout_height="(\d+)dp"""").find(l)
                                if (wM != null && hM != null) {
                                    val w = wM.groupValues[1]; val h = hM.groupValues[1]
                                    l = l.replace("android:layout_width=\"${w}dp\"", "android:layout_width=\"TEMP_W\"")
                                    l = l.replace("android:layout_height=\"${h}dp\"", "android:layout_height=\"${w}dp\"")
                                    l = l.replace("android:layout_width=\"TEMP_W\"", "android:layout_width=\"${h}dp\"")
                                }
                                if (!l.contains("android:layout_margin")) {
                                    l = l.replaceFirst(" ", " android:layout_margin=\"2dp\" ")
                                }
                            }
                            l
                        }
                        content = lines.joinToString("\n")
                    }

                    // --- 修改部分开始 ---
                    val targetFile = File(resPath, "${base}_${angle}.xml")

                    // 检查文件是否存在，如果存在则读取旧内容进行比对
                    val shouldUpdate = if (targetFile.exists()) {
                        targetFile.readText() != content
                    } else {
                        true
                    }

                    if (shouldUpdate) {
                        targetFile.writeText(content)
                        println("Updated: ${targetFile.name}")
                    } else {
                        // 内容一致，跳过写入以避免触发系统的文件变更监听（如 Android Studio 的热重载或索引刷新）
                        println("Skipped (No changes): ${targetFile.name}")
                    }
                    // --- 修改部分结束 ---
                }
            }
        }
    }
}


// 依然挂载到 preBuild
tasks.named("preBuild") {
    dependsOn("generateFloatLayouts")
}
