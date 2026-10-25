package com.dudu.codex;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;

public class SafeFilesTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void rejectsTraversalAndPrefixSibling() throws Exception {
        File root = temp.newFolder("work");
        assertEquals(new File(root, "ok.txt").getCanonicalFile(), SafeFiles.resolve(root, "ok.txt"));
        assertThrows(IOException.class, () -> SafeFiles.resolve(root, "../secret"));
        assertThrows(IOException.class, () -> SafeFiles.resolve(root, root.getPath() + "-other/a"));
    }
    @Test public void rejectsSymlinkEscapeAndSkipsLoops() throws Exception {
        File root = temp.newFolder("work"); File outside = temp.newFile("secret");
        Files.createSymbolicLink(new File(root, "escape").toPath(), outside.toPath());
        Files.createSymbolicLink(new File(root, "loop").toPath(), root.toPath());
        Files.write(new File(root, "ok.txt").toPath(), new byte[]{1});
        assertThrows(IOException.class, () -> SafeFiles.resolve(root, "escape"));
        assertEquals(1, SafeFiles.list(root).size());
    }
    @Test public void listsFilesWhenWorkspaceParentIsAnAndroidStyleAlias() throws Exception {
        File real = temp.newFolder("user0");
        Files.write(new File(real, "hello.txt").toPath(), new byte[]{1});
        File alias = new File(temp.getRoot(), "data"); Files.createSymbolicLink(alias.toPath(), real.toPath());
        assertEquals("hello.txt", SafeFiles.list(alias).get(0).getName());
    }
    @Test public void enforcesImportLimitWithoutWritingOverflow() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> SafeFiles.copy(new ByteArrayInputStream(new byte[20]), out, 10));
        assertTrue(out.size() <= 10);
        assertEquals(10, SafeFiles.copy(new ByteArrayInputStream(new byte[10]), out, 10));
    }
    @Test public void sanitizesProviderNames() {
        assertEquals(".._secret_name", SafeFiles.displayName("../secret\\name"));
        assertEquals("file", SafeFiles.displayName(".."));
        assertEquals("中文.pdf", SafeFiles.displayName("中文.pdf"));
        assertEquals("a_b", SafeFiles.displayName("a\nb"));
    }
}
