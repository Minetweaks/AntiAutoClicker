plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
}

group = "net.minetweak"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    
    // Cloud Commands
    implementation("org.incendo:cloud-paper:2.0.0-beta.10")
    implementation("org.incendo:cloud-annotations:2.0.0")
    implementation("org.incendo:cloud-minecraft-extras:2.0.0-beta.10")
    
    // For Discord webhook (simple HTTP)
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("org.incendo.cloud", "net.minetweak.antiautoclick.libs.cloud")
        relocate("com.google.gson", "net.minetweak.antiautoclick.libs.gson")
        relocate("io.leangen.geantyref", "net.minetweak.antiautoclick.libs.geantyref")
    }
    
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
    
    build {
        dependsOn(shadowJar)
    }
}
