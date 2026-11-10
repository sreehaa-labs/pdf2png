# PDF to PNG (Android)

Minimal Android app: pick a PDF, choose a quality, get one PNG per page in
Pictures/PDF2PNG/<pdf-name>_<timestamp>/ . No third-party libraries, no permissions.

## Build the APK (command line, Linux/macOS)

    # 1. JDK 17
    sudo apt install openjdk-17-jdk          # macOS: brew install openjdk@17

    # 2. Android command-line tools: download "Command line tools only" from
    #    https://developer.android.com/studio#command-line-tools-only
    mkdir -p ~/android-sdk/cmdline-tools
    unzip commandlinetools-*_latest.zip -d ~/android-sdk/cmdline-tools
    mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
    export ANDROID_HOME=$HOME/android-sdk
    export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
    yes | sdkmanager --licenses
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

    # 3. Build
    cd PDF2PNG
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    gradle wrapper --gradle-version 8.7      # needs Gradle installed once (sdkman / brew / apt)
    ./gradlew assembleDebug

    # APK:
    app/build/outputs/apk/debug/app-debug.apk

## Install on a phone

    adb install -r app/build/outputs/apk/debug/app-debug.apk
    # or copy the APK to the phone and open it (allow "install unknown apps").

## Easiest alternative: Android Studio
File > Open > select the PDF2PNG folder > wait for Gradle sync > Build > Build APK(s).
