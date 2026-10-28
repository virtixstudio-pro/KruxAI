package com.virtixstudio.kruxai.builder;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fichiers de build fournis par KRUX (jamais par l'IA), générés à partir de BuildSpec. */
public final class BuildTemplates {

    private BuildTemplates() { }

    public static Map<String, String> gradleFiles(BuildSpec s) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("settings.gradle",
            "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n"
            + "dependencyResolutionManagement { repositories { google(); mavenCentral() } }\n"
            + "rootProject.name = \"GeneratedApp\"\ninclude ':app'\n");
        m.put("build.gradle",
            "plugins { id 'com.android.application' version '" + s.agp + "' apply false }\n");
        m.put("gradle.properties",
            "org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\n");
        m.put("app/build.gradle",
            "plugins { id 'com.android.application' }\n"
            + "android {\n namespace '" + BuildSpec.NAMESPACE + "'\n compileSdk " + s.compileSdk + "\n"
            + " defaultConfig { applicationId '" + BuildSpec.NAMESPACE + "'; minSdk " + s.minSdk
            + "; targetSdk " + s.targetSdk + "; versionCode 1; versionName '1.0' }\n"
            + " compileOptions { sourceCompatibility JavaVersion.VERSION_" + s.jdk
            + "; targetCompatibility JavaVersion.VERSION_" + s.jdk + " }\n}\n"
            + "dependencies {\n implementation 'androidx.appcompat:appcompat:" + s.appcompat + "'\n"
            + " implementation 'com.google.android.material:material:" + s.material + "'\n"
            + " implementation 'androidx.recyclerview:recyclerview:" + s.recyclerview + "'\n"
            + " implementation 'androidx.constraintlayout:constraintlayout:" + s.constraintlayout + "'\n}\n");
        return m;
    }

    /** Étape qui prépare le SDK Android sur le runner GitHub (licences + plateforme + build tools). */
    public static String sdkStep(BuildSpec s) {
        return "      - name: Prepare Android SDK\n"
            + "        run: |\n"
            + "          SDKM=\"${ANDROID_HOME:-$ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager\"\n"
            + "          if [ -x \"$SDKM\" ]; then\n"
            + "            yes | \"$SDKM\" --licenses > /dev/null || true\n"
            + "            \"$SDKM\" \"platforms;android-" + s.compileSdk + "\" \"build-tools;" + s.buildTools + "\" > /dev/null || true\n"
            + "          fi\n";
    }

    public static String workflow(BuildSpec s) {
        return "name: Build APK\n"
            + "on:\n  workflow_dispatch:\n"
            + "permissions:\n  contents: read\n"
            + "concurrency:\n  group: build-${{ github.ref }}\n  cancel-in-progress: true\n"
            + "jobs:\n  build:\n    runs-on: ubuntu-latest\n    timeout-minutes: 25\n    steps:\n"
            + "      - uses: actions/checkout@v4\n"
            + "      - uses: actions/setup-java@v4\n        with:\n          distribution: temurin\n          java-version: " + s.jdk + "\n"
            + sdkStep(s)
            + "      - uses: gradle/actions/setup-gradle@v4\n        with:\n          gradle-version: " + s.gradle + "\n"
            + "      - run: gradle assembleDebug --no-daemon --console=plain\n"
            + "      - uses: actions/upload-artifact@v4\n        with:\n          name: app-debug\n"
            + "          path: app/build/outputs/apk/debug/app-debug.apk\n"
            + "          if-no-files-found: error\n"
            + "          retention-days: 1\n";
    }
}
