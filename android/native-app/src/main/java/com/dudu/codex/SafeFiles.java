package com.dudu.codex;

import java.io.*;
import java.util.*;

final class SafeFiles {
    static final long MAX_IMPORT = 50L * 1024 * 1024;
    static File resolve(File workspace, String path) throws IOException {
        File file = (new File(path).isAbsolute() ? new File(path) : new File(workspace, path)).getCanonicalFile();
        File root = workspace.getCanonicalFile();
        if (!file.equals(root) && !file.getPath().startsWith(root.getPath() + File.separator))
            throw new IOException("只能访问当前对话的工作区文件");
        return file;
    }
    static String displayName(String name) {
        String cleaned = name.replaceAll("[\\p{Cntrl}/\\\\]", "_").trim();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) cleaned = "file";
        return cleaned.substring(0, Math.min(cleaned.length(), 120));
    }
    static long copy(InputStream source, OutputStream destination, long limit) throws IOException {
        byte[] buffer = new byte[32768]; long size = 0; int count;
        while ((count = source.read(buffer)) != -1) {
            size += count;
            if (size > limit) throw new IOException("文件超过 50 MB，未导入");
            destination.write(buffer, 0, count);
        }
        return size;
    }
    static List<File> list(File workspace) throws IOException {
        File root = workspace.getCanonicalFile();
        List<File> files = new ArrayList<>(); walk(root, root, files, 0); return files;
    }
    private static void walk(File root, File directory, List<File> output, int depth) throws IOException {
        if (depth > 12 || output.size() >= 300) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File file : children) {
            if (file.getName().startsWith(".")) continue;
            File safe;
            try { safe = resolve(root, file.getPath()); } catch (IOException ignored) { continue; }
            if (!safe.getPath().equals(file.getAbsolutePath())) continue; // Do not follow symlinks.
            if (file.isDirectory()) walk(root, file, output, depth + 1);
            else if (file.isFile()) output.add(file);
            if (output.size() >= 300) break;
        }
    }
}
