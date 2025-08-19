plugins {
    kotlin("jvm") version "2.1.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.rph"
version = "1.0.0"

repositories {
    mavenCentral()

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    testImplementation(kotlin("test"))

    compileOnly("org.github.paperspigot:paperspigot-api:1.8.8-R0.1-20160806.221350-1")
    compileOnly("net.dmulloy2:ProtocolLib:5.1.0")

    implementation(kotlin("stdlib"))
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    shadowJar {
        archiveClassifier.set("") // So it's not named "-all.jar"
        minimize() // Optional: strips unused classes, makes the JAR smaller
    }

    build {
        dependsOn(shadowJar)
        finalizedBy("copyToPluginsDir")
    }
}

tasks.register<Copy>("copyToPluginsDir") {
    dependsOn("build")

    from(tasks.named("jar"))
    into("../server/plugins")
    rename { "pkdplugin.jar" }

    doFirst {
        println("Copying plugin jar to ../server/plugins")
    }
}

kotlin {
    jvmToolchain(8)
}