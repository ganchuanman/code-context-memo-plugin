plugins {
    java
    id("org.jetbrains.intellij.platform")
}

group = "com.github.ganchuanman"
version = "0.7.5"

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.6")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
