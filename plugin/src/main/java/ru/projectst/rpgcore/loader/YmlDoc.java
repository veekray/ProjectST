package ru.projectst.rpgcore.loader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Разобранный документ контента.
 *
 * <p>Порядок работы ровно такой: {@link #parse} разбирает текст, читатель домена
 * забирает из {@link #root()} то, что ему нужно, затем {@link #finish()}
 * сообщает обо всём, чего никто не забрал. Последний шаг и даёт требование
 * «неизвестный ключ — это ошибка»: его нельзя забыть, потому что он не зависит
 * от того, перечислил ли читатель допустимые ключи.
 *
 * <p>SnakeYAML используется только как парсер до уровня узлов. Его
 * {@code load()} в объекты здесь не применяется: он теряет позиции и молча
 * приводит типы, а нам нужно обратное.
 */
public final class YmlDoc {

    private final String file;
    private final YmlMap root;
    private final ContentErrors errors;
    private final List<YmlMap> maps;

    private YmlDoc(String file, YmlMap root, ContentErrors errors, List<YmlMap> maps) {
        this.file = file;
        this.root = root;
        this.errors = errors;
        this.maps = maps;
    }

    /**
     * Разбирает текст. Пустой результат означает, что документ не удалось
     * прочитать вообще: синтаксис, не-отображение на верхнем уровне. Ошибка в
     * этом случае уже лежит в сборщике.
     */
    public static Optional<YmlDoc> parse(String file, String text, ContentErrors errors) {
        // Дубликаты ключей SnakeYAML проверяет в конструкторе объектов, а не в
        // композере, которым мы пользуемся. Поэтому опция тут бесполезна, и
        // дубликаты ловятся вручную в toMap.
        LoaderOptions options = new LoaderOptions();
        Node node;
        try {
            node = new Yaml(options).compose(new java.io.StringReader(text));
        } catch (MarkedYAMLException e) {
            errors.add(mark(file, e.getProblemMark()), "", "синтаксис YAML: " + e.getProblem());
            return Optional.empty();
        } catch (YAMLException e) {
            errors.add(SourceRef.ofFile(file), "", "синтаксис YAML: " + e.getMessage());
            return Optional.empty();
        }
        if (node == null) {
            errors.add(SourceRef.ofFile(file), "", "файл пуст");
            return Optional.empty();
        }
        if (!(node instanceof MappingNode mapping)) {
            errors.add(mark(file, node.getStartMark()), "",
                    "на верхнем уровне ожидается раздел, а не список или значение");
            return Optional.empty();
        }
        List<YmlMap> maps = new ArrayList<>();
        YmlMap root = toMap(file, "", mapping, errors, maps);
        return Optional.of(new YmlDoc(file, root, errors, maps));
    }

    public String file() {
        return file;
    }

    public YmlMap root() {
        return root;
    }

    /** Сообщает о непрочитанных ключах во всех разделах документа. */
    public void finish() {
        for (YmlMap map : maps) {
            map.reportUnread(errors);
        }
    }

    // ------------------------------------------------------------------ перевод узлов

    private static YmlMap toMap(String file, String path, MappingNode node,
                                ContentErrors errors, List<YmlMap> maps) {
        LinkedHashMap<String, YmlNode> entries = new LinkedHashMap<>();
        for (NodeTuple tuple : node.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) {
                errors.add(mark(file, tuple.getKeyNode().getStartMark()), path,
                        "ключом может быть только простое значение");
                continue;
            }
            String key = keyNode.getValue();
            String childPath = path.isEmpty() ? key : path + "." + key;
            YmlNode value = toNode(file, childPath, tuple.getValueNode(), errors, maps);
            if (entries.put(key, value) != null) {
                errors.add(mark(file, keyNode.getStartMark()), childPath,
                        "ключ объявлен дважды");
            }
        }
        YmlMap map = new YmlMap(mark(file, node.getStartMark()), path, entries, errors);
        maps.add(map);
        return map;
    }

    private static YmlNode toNode(String file, String path, Node node,
                                  ContentErrors errors, List<YmlMap> maps) {
        if (node instanceof MappingNode mapping) {
            return toMap(file, path, mapping, errors, maps);
        }
        if (node instanceof SequenceNode seq) {
            List<YmlNode> items = new ArrayList<>(seq.getValue().size());
            int index = 0;
            for (Node item : seq.getValue()) {
                items.add(toNode(file, path + "[" + index++ + "]", item, errors, maps));
            }
            return new YmlNode.Seq(mark(file, seq.getStartMark()), items);
        }
        if (node instanceof ScalarNode scalar) {
            return new YmlNode.Scalar(mark(file, scalar.getStartMark()), scalar.getValue());
        }
        errors.add(mark(file, node.getStartMark()), path, "непонятный узел");
        return new YmlNode.Scalar(mark(file, node.getStartMark()), "");
    }

    /** SnakeYAML считает строки и колонки с нуля, редакторы — с единицы. */
    private static SourceRef mark(String file, Mark mark) {
        if (mark == null) {
            return SourceRef.ofFile(file);
        }
        return new SourceRef(file, mark.getLine() + 1, mark.getColumn() + 1);
    }
}
