package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Menus and dialogs close together with their screen, without "has leaked window":
 * <ul>
 * <li>every app theme points the popup menu, toolbar menu and list popup styles to a style
 *     without a fade-out (a fade-out keeps the menu window until it has ended, and a rotation
 *     draws no frame in between);</li>
 * <li>every popup menu style of the app has no fade-out either;</li>
 * <li>the code makes dialogs with ScreenDialog.Builder and popup menus with ScreenPopupMenu.</li>
 * </ul>
 */
public class PopupStylesTest {

    private static final String NO_FADE = "@android:transition/no_transition";
    private static final String EXIT = "android:popupExitTransition";
    private static final String[] THEME_ITEMS = {"popupMenuStyle", "actionOverflowMenuStyle", "listPopupWindowStyle"};

    private static final class Style {
        String parent;
        boolean hasParentAttr;
        final Map<String, String> items = new HashMap<>();
    }

    private static File main() {
        File dir = new File("src/main");
        if (!dir.exists()) dir = new File("app/src/main");
        return dir;
    }

    private static Map<String, Style> styles() throws Exception {
        Map<String, Style> all = new HashMap<>();
        File[] dirs = new File(main(), "res").listFiles((d, n) -> n.startsWith("values"));
        assertTrue(dirs != null && dirs.length > 0);
        for (File dir : dirs) {
            File[] files = dir.listFiles((d, n) -> n.endsWith(".xml"));
            if (files == null) continue;
            for (File f : files) {
                NodeList list = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
                        .getElementsByTagName("style");
                for (int i = 0; i < list.getLength(); i++) {
                    Element e = (Element) list.item(i);
                    Style s = all.computeIfAbsent(e.getAttribute("name"), k -> new Style());
                    if (e.hasAttribute("parent")) {
                        s.hasParentAttr = true;
                        s.parent = e.getAttribute("parent");
                    }
                    NodeList items = e.getElementsByTagName("item");
                    for (int j = 0; j < items.getLength(); j++) {
                        Element item = (Element) items.item(j);
                        s.items.put(item.getAttribute("name"), item.getTextContent().trim());
                    }
                }
            }
        }
        return all;
    }

    /** The value of {@code attr} in the app style {@code name} or its app parents, or null. */
    private static String value(Map<String, Style> all, String name, String attr) {
        for (int guard = 0; name != null && guard < 20; guard++) {
            Style s = all.get(name);
            if (s == null) return null;
            if (s.items.containsKey(attr)) return s.items.get(attr);
            if (s.hasParentAttr) {
                name = s.parent.startsWith("@style/") ? s.parent.substring(7) : s.parent;
            } else {
                int dot = name.lastIndexOf('.');
                name = dot > 0 ? name.substring(0, dot) : null;
            }
        }
        return null;
    }

    private static String styleRef(String value) {
        return value != null && value.startsWith("@style/") ? value.substring(7) : null;
    }

    @Test
    public void everyThemeGivesMenusNoFadeOut() throws Exception {
        Map<String, Style> all = styles();
        int themes = 0;
        for (Map.Entry<String, Style> e : all.entrySet()) {
            Style s = e.getValue();
            if (!s.hasParentAttr || !s.parent.startsWith("Theme.")) continue;
            themes++;
            for (String attr : THEME_ITEMS) {
                String style = styleRef(s.items.get(attr));
                assertTrue(e.getKey() + " sets no " + attr, style != null);
                assertEquals(e.getKey() + " " + attr + " " + style, NO_FADE, value(all, style, EXIT));
            }
        }
        // the four looks, their dialogs, and the start screen
        assertTrue("themes found: " + themes, themes >= 9);
    }

    @Test
    public void everyMenuStyleHasNoFadeOut() throws Exception {
        Map<String, Style> all = styles();
        List<String> menuStyles = new ArrayList<>();
        for (Map.Entry<String, Style> e : all.entrySet()) {
            Style s = e.getValue();
            String parent = s.hasParentAttr ? s.parent : "";
            if (parent.contains("PopupMenu") || parent.contains("ListPopupWindow")
                    || e.getKey().contains("PopupMenu") || e.getKey().startsWith("ContextPopupTheme")) {
                menuStyles.add(e.getKey());
            }
            // styles that the code passes to popup menus (R.attr.popup_menu_theme) and context menus
            for (String attr : new String[]{"popup_menu_theme", "android:contextPopupMenuStyle"}) {
                String ref = styleRef(s.items.get(attr));
                if (ref != null) menuStyles.add(ref);
            }
        }
        assertTrue(menuStyles.size() >= 8);
        for (String name : menuStyles) {
            assertTrue("missing style " + name, all.containsKey(name));
            assertEquals(name, NO_FADE, value(all, name, EXIT));
        }
    }

    @Test
    public void codeUsesScreenDialogsAndScreenMenus() throws Exception {
        Pattern raw = Pattern.compile("new\\s+(?:[a-z][\\w]*\\.)*(AlertDialog\\.Builder|PopupMenu|PopupWindow|ListPopupWindow|ListPopupWindowCompat)\\s*\\(");
        List<String> found = new ArrayList<>();
        List<File> files = new ArrayList<>();
        collect(new File(main(), "java"), files);
        assertTrue(files.size() > 100);
        for (File f : files) {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                Matcher m = raw.matcher(lines[i]);
                if (!m.find()) continue;
                // the side menu only borrows a PopupMenu's empty Menu object, it never shows it
                if (f.getName().equals("MainMenuDrawer.java") && lines[i].contains(".getMenu()")) continue;
                found.add(f.getName() + ":" + (i + 1) + " " + lines[i].trim());
            }
        }
        assertEquals("use new ScreenDialog.Builder(...) / new ScreenPopupMenu(...): " + found, 0, found.size());
    }

    private static void collect(File dir, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".java")) out.add(f);
        }
    }
}
