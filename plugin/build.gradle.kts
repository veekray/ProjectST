dependencies {
    // На сервере API и его транзитивные зависимости (в том числе SnakeYAML,
    // на котором работает YamlConfiguration) уже лежат в classpath ядра,
    // поэтому в собранный плагин их тащить нельзя — только compileOnly.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // Тестам тот же SnakeYAML нужен уже в рантайме: иначе загрузчик падает с
    // NoClassDefFoundError на MarkedYAMLException. Берём именно его, а не весь
    // paper-api: paper-api в testRuntime тянет свои транзитивные зависимости
    // (asm и прочее), которых в кеше нет, то есть потребовал бы новых выкачек.
    testImplementation("org.yaml:snakeyaml:2.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
