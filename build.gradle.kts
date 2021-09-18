plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.aliucord.gradle")
}

version = "0.2.0"
description = "See the avatars of the users who reacted to a message."

aliucord {
    changelog.set(file("CHANGELOG.md").readText())
    author("6pak", 141580516380901376)
    updateUrl.set("https://github.com/js6pak/WhoReacted/releases/latest/download/updater.json")
    buildUrl.set("https://github.com/js6pak/WhoReacted/$version/releases/download/WhoReacted.zip")
}

android {
    compileSdk = 30

    defaultConfig {
        minSdk = 24
        targetSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    discord("com.discord:discord:aliucord-SNAPSHOT")
    api("com.github.Aliucord:Aliucord:main-SNAPSHOT")
}
