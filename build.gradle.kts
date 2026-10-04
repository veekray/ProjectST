plugins {
    java
}

allprojects {
    repositories {
        mavenCentral()
        // Paper API — единственная зависимость, которую нужно выкачать из сети.
        // После первой сборки она лежит в кеше Gradle и сборка идёт офлайн.
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            // На машине стоит JDK 21; ядро 1.21.1 требует как минимум 21.
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        // Предупреждения не прячем: половина ошибок в этом проекте — это
        // как раз то, что компилятор готов сказать заранее.
        options.compilerArgs.addAll(listOf("-Xlint:all"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }
}
