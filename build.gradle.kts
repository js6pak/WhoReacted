plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.aliucord.gradle")
}

version = "0.2.3"
description = "See the avatars of the users who reacted to a message."

aliucord {
    changelog.set(file("CHANGELOG.md").readText())
    author("6pak", 141580516380901376)
    github("https://github.com/js6pak/WhoReacted")
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

    kotlinOptions {
        jvmTarget = "11"
    }
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.aliucord.com/snapshots")
}

dependencies {
    discord("com.discord:discord:aliucord-SNAPSHOT")
    api("com.aliucord:Aliucord:main-SNAPSHOT")
}
