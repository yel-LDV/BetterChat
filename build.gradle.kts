// Configuracion raiz: version, repositorios y toolchain compartidos por todos los modulos.
plugins {
    java
    id("com.gradleup.shadow") version "8.3.5" apply false
}

allprojects {
    group = "dev.betterchats"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://repo.extendedclip.com/releases/")
        maven("https://repo.codemc.io/repository/maven-releases/")
        mavenLocal()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addStringOption("Xdoclint:none", "-quiet")
        }
    }
}

// Versiones compartidas (un unico sitio para subirlas).
extra["adventureVersion"] = "4.21.0"
extra["adventurePlatformVersion"] = "4.4.1"
extra["gsonVersion"] = "2.10.1"
extra["spigotVersion"] = "1.8.8-R0.1-SNAPSHOT"
extra["papiVersion"] = "2.11.6"
