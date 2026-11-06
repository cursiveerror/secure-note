import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
    implementation("org.json:json:20240303")
    implementation("com.google.zxing:core:3.5.3")
}

compose.desktop {
    application {
        mainClass = "org.cursiveerror.securenote.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "SecureNote Desktop"
            packageVersion = "1.0.0"
            description = "SecureNote Desktop LAN Sync"
            copyright = "© 2026 CursiveError"
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            windows {
                iconFile.set(project.file("resources/icon.ico"))
                menu = true
                shortcut = true
            }
        }
    }
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    try {
        val field = org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask::class.java.getDeclaredField("jpackageResources")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val orig = field.get(this) as Provider<Directory>
        val custom = orig.map { dir ->
            val resDir = project.file("resources")
            if (resDir.exists()) {
                resDir.copyRecursively(dir.asFile, overwrite = true)
                val mainWxsFile = File(dir.asFile, "main.wxs")
                if (mainWxsFile.exists()) {
                    val dialogBmp = File(resDir, "dialog.bmp").absolutePath
                    val bannerBmp = File(resDir, "banner.bmp").absolutePath
                    val content = mainWxsFile.readText()
                        .replace("VALUE_WIXUI_DIALOG_BMP", dialogBmp)
                        .replace("VALUE_WIXUI_BANNER_BMP", bannerBmp)
                    mainWxsFile.writeText(content)
                }
            }
            dir
        }
        field.set(this, custom)
    } catch (e: Exception) {
        logger.warn("Failed to hook jpackageResources: ${e.message}")
    }
}
