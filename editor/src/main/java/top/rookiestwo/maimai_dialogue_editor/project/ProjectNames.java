package top.rookiestwo.maimai_dialogue_editor.project;

import com.ibm.icu.text.Transliterator;

import java.util.Locale;

/** 新项目建议值；不读取磁盘，也不重命名已经建立的项目。 */
public final class ProjectNames {
    private static final ThreadLocal<Transliterator> LATIN = ThreadLocal.withInitial(
            () -> Transliterator.getInstance("Any-Latin; Latin-ASCII"));

    private ProjectNames() {
    }

    public static String suggestNamespace(String name) {
        String result = LATIN.get().transliterate(name).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (result.isEmpty()) return "my_project";
        if (result.length() > 64) result = result.substring(0, 64).replaceAll("_+$", "");
        // 建议 namespace 同时作为目录名，避开 Windows 的保留设备名。
        if (result.matches("con|prn|aux|nul|com[1-9]|lpt[1-9]")) result = "project_" + result;
        return result;
    }
}
