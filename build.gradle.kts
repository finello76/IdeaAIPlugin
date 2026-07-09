plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.5.31"
    id("org.jetbrains.intellij") version "1.13.3"
}

group = "com.andreafini"
version = "2.0.0"

repositories {
    mavenCentral()
}

// Compila contro l'IntelliJ IDEA già installato localmente (build 212): usando localPath
// il plugin Gradle NON scarica alcun SDK. La compatibilità con la 2021.1 è dichiarata da
// sinceBuild=211 in patchPluginXml, indipendente dalla versione dell'IDE di compilazione.
intellij {
    localPath.set("/home/finello/Progetti/intellij_idea")
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
        sinceBuild.set("211")
        untilBuild.set("212.*")
    }

    // Firma e pubblicazione non configurate: non servono per lo sviluppo locale.
}
