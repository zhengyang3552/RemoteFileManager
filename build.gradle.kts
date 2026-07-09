plugins {
    kotlin("jvm") version "1.9.23"
    application
}

group = "com.filemanager"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    // FTP/FTPS - Apache Commons Net
    implementation("commons-net:commons-net:3.9.0")

    // SFTP - SSHJ
    implementation("com.hierynomus:sshj:0.36.0")

    // SMB - JCIFS NG
    implementation("org.codelibs.jcifs:jcifs-ng:2.1.11")

    // XML parsing for WebDAV
    implementation("org.apache.commons:commons-text:1.11.0")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.11")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.filemanager.MainKt")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

tasks.test {
    useJUnitPlatform()
}
