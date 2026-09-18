package top.rookiestwo.maimai_dialogue_editor.project;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectNamesTest {
    @Test
    void suggestsReadableIdsForEnglishChineseAndAccentedNames() {
        assertEquals("maimai_story_01", ProjectNames.suggestNamespace("MaiMai Story 01"));
        assertEquals("ce_shi_xiang_mu", ProjectNames.suggestNamespace("测试项目"));
        assertEquals("cafe_deja_vu", ProjectNames.suggestNamespace("Café Déjà Vu"));
    }

    @Test
    void suggestionsAreSafeSingleFolderNamesIncludingWindowsReservedWords() {
        assertEquals("my_project", ProjectNames.suggestNamespace("   ✨  "));
        assertEquals("my_project", ProjectNames.suggestNamespace("../../"));
        assertEquals("project_con", ProjectNames.suggestNamespace("CON"));
        assertEquals("project_lpt1", ProjectNames.suggestNamespace("LPT1"));
        assertEquals("a_b", ProjectNames.suggestNamespace(" A / B: *? "));
        String longName = ProjectNames.suggestNamespace("Very long project name! ".repeat(50));
        assertTrue(longName.length() <= 64);
        assertTrue(longName.matches("[a-z0-9]+(?:_[a-z0-9]+)*"));
    }
}
