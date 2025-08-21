// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        classpath(libs.google.services)
        classpath(libs.gradle)
    }
}
plugins {
    id("com.google.gms.google-services") version "4.4.3" apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}