plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.5.31"
    id("org.jetbrains.intellij") version "1.13.3"
}

group = "com.andreafini"
version = "1.0.1"

repositories {
    mavenCentral()
}

// Configura il plugin Gradle IntelliJ contro IntelliJ IDEA Community 2021.2.1 (build 212)
intellij {
    version.set("2021.2.1")
    type.set("IC")
    plugins.set(listOf())
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    patchPluginXml {
        sinceBuild.set("212")
        untilBuild.set("212.*")
    }

    // Firma e pubblicazione non configurate: non servono per lo sviluppo locale.
}
