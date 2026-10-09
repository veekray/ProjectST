package ru.projectst.rpgcore.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Файлы контента в папке плагина и их версии из jar.
 *
 * <p><b>Почему это не просто «скопировать, если нет».</b> Плагин читает контент
 * из своей папки, а не из jar: администратор правит YAML на живом сервере и
 * перегружает командой. Но копия «если нет» означала, что новая версия плагина
 * ложится на старые файлы и не меняет в них ничего — код новый, навыки прежние.
 * Именно так кольцо Ядовитого плюща осталось на пяти блоках при уроне на семи с
 * половиной: исправленный навык лежал в jar, а сервер читал файл с первой
 * установки. Снаружи это неотличимо от ошибки в коде.
 *
 * <p><b>Что делается.</b> Плагин помнит, какую версию каждого файла записал сам
 * (файл {@value #MANIFEST}, хеш на строку). Файл, который с тех пор никто не
 * трогал, при обновлении плагина заменяется новым молча: это его же файл. Файл,
 * который правили руками, не трогается никогда — правка администратора важнее
 * — и называется в журнале. Файл, о котором плагин ничего не помнит (лежал до
 * этой службы), тоже не трогается: отличить старую версию от правки нечем.
 * Обновить такие разом — {@code /rpg content update}, с копией прежних.
 *
 * <p><b>Список файлов — из самого jar.</b> Раньше он был записан в коде руками,
 * отстал на сто сорок файлов, и семь классов на новый сервер не приезжали.
 * Список, который нужно не забыть дописать, однажды забудут.
 */
public final class ContentFiles {

    /** Что плагин записал сам: путь и хеш, по строке на файл. */
    static final String MANIFEST = ".shipped";

    /** Каталоги контента: всё, что в них лежит в jar, — поставляемый контент. */
    private static final List<String> DIRS = List.of("skills/", "classes/", "items/",
            "recipes/", "mobs/");

    /** Файлы в корне jar, которые контентом не являются. */
    private static final Set<String> NOT_CONTENT = Set.of("plugin.yml", "paper-plugin.yml",
            "config.yml");

    private final Path folder;
    private final Map<String, byte[]> shipped;

    /** Что сделала сверка. */
    public record Report(List<String> added, List<String> updated, List<String> kept) {

        public boolean quiet() {
            return added.isEmpty() && updated.isEmpty() && kept.isEmpty();
        }
    }

    /**
     * @param folder  папка плагина
     * @param shipped поставляемые файлы: путь от папки плагина и содержимое
     */
    public ContentFiles(Path folder, Map<String, byte[]> shipped) {
        this.folder = folder;
        this.shipped = new TreeMap<>(shipped);
    }

    /** Поставляемый контент из jar плагина. */
    public static Map<String, byte[]> readJar(Path jar) throws IOException {
        Map<String, byte[]> out = new TreeMap<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".yml") || !isContent(name)) {
                    continue;
                }
                try (InputStream in = file.getInputStream(entry)) {
                    out.put(name, in.readAllBytes());
                }
            }
        }
        return out;
    }

    static boolean isContent(String name) {
        if (name.indexOf('/') < 0) {
            return !NOT_CONTENT.contains(name);
        }
        for (String dir : DIRS) {
            if (name.startsWith(dir) && name.indexOf('/', dir.length()) < 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Сверка при запуске: недостающее положить, своё нетронутое обновить,
     * правленное руками оставить и назвать.
     */
    public Report sync() throws IOException {
        Map<String, String> manifest = readManifest();
        List<String> added = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : shipped.entrySet()) {
            String name = entry.getKey();
            byte[] fresh = entry.getValue();
            Path target = folder.resolve(name);
            if (!Files.isRegularFile(target)) {
                write(target, fresh);
                manifest.put(name, hash(fresh));
                added.add(name);
                continue;
            }
            String onDisk = hash(Files.readAllBytes(target));
            if (onDisk.equals(hash(fresh))) {
                manifest.put(name, onDisk);
                continue;
            }
            if (onDisk.equals(manifest.get(name))) {
                // Наш же файл, и никто его не правил: новая версия — тоже наша.
                write(target, fresh);
                manifest.put(name, hash(fresh));
                updated.add(name);
            } else {
                kept.add(name);
            }
        }
        writeManifest(manifest);
        return new Report(added, updated, kept);
    }

    /**
     * Все поставляемые файлы — к версии из jar, прежние — в копию.
     *
     * @param backup куда сложить прежние версии, с теми же путями
     */
    public Report forceUpdate(Path backup) throws IOException {
        Map<String, String> manifest = readManifest();
        List<String> added = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : shipped.entrySet()) {
            String name = entry.getKey();
            byte[] fresh = entry.getValue();
            Path target = folder.resolve(name);
            if (!Files.isRegularFile(target)) {
                write(target, fresh);
                added.add(name);
            } else if (!hash(Files.readAllBytes(target)).equals(hash(fresh))) {
                Path copy = backup.resolve(name);
                Files.createDirectories(copy.getParent());
                Files.copy(target, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                write(target, fresh);
                updated.add(name);
            }
            manifest.put(name, hash(fresh));
        }
        writeManifest(manifest);
        return new Report(added, updated, List.of());
    }

    /** Поставляемые файлы, которые на диске не совпадают с jar. */
    public List<String> outdated() throws IOException {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : shipped.entrySet()) {
            Path target = folder.resolve(entry.getKey());
            if (Files.isRegularFile(target)
                    && !hash(Files.readAllBytes(target)).equals(hash(entry.getValue()))) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    private static void write(Path target, byte[] content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(target, content);
    }

    private Map<String, String> readManifest() throws IOException {
        Map<String, String> out = new TreeMap<>();
        Path file = folder.resolve(MANIFEST);
        if (!Files.isRegularFile(file)) {
            return out;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int tab = line.indexOf('\t');
            if (tab > 0) {
                out.put(line.substring(0, tab), line.substring(tab + 1).trim());
            }
        }
        return out;
    }

    private void writeManifest(Map<String, String> manifest) throws IOException {
        StringBuilder text = new StringBuilder(
                "# Какую версию каждого файла записал плагин. Не править: по этим\n"
                        + "# хешам плагин отличает свой нетронутый файл от правленного руками.\n");
        for (Map.Entry<String, String> entry : manifest.entrySet()) {
            text.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
        }
        write(folder.resolve(MANIFEST), text.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 есть в любой JVM", e);
        }
    }
}
