package com.androlua.util;

import android.content.Context;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtil {

    public static boolean createDirectory(String dirPath) {
        File directory = new File(dirPath);
        if (!directory.exists()) {
            return directory.mkdirs();
        } else {
            return false;
        }
    }

    public static void write(String filePath, String content) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            file.createNewFile();
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(content);
        writer.close();
    }

    public static String read(String filePath) throws IOException {
        File file = new File(filePath);
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder content = new StringBuilder();
        String line = reader.readLine();
        while (line != null) {
            content.append(line);
            line = reader.readLine();
            if (line != null) {
                content.append("\n");
            }
        }
        reader.close();
        return content.toString();
    }

    public static void traverseDirectory(String folderPath, Consumer<String> callback) {
        File folder = new File(folderPath);
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        callback.accept(file.getPath());
                    }
                }
            }
        }
    }

    public static boolean isExist(String path) {
        return new File(path).exists();
    }

    public static void unAssetsZip(Context context, String zipFilePath, String targetDirectoryPath) throws IOException {
        InputStream inputStream = context.getAssets().open(zipFilePath);
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);

        try {
            while (true) {
                ZipEntry entry = zipInputStream.getNextEntry();
                if (entry == null) {
                    break;
                }
                File file = new File(targetDirectoryPath, entry.getName());

                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }

                    byte[] buffer = new byte[1024];

                    OutputStream outputStream = new FileOutputStream(file);
                    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);

                    int count;
                    while ((count = zipInputStream.read(buffer, 0, 1024)) != -1) {
                        bufferedOutputStream.write(buffer, 0, count);
                    }

                    bufferedOutputStream.flush();
                    bufferedOutputStream.close();
                }
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (zipInputStream != null) {
                zipInputStream.close();
            }
        }
    }

    public static void replaceFileString(String path, String str1, String str2) throws IOException {
        String text = read(path);
        text = text.replace(str1, str2);
        write(path, text);
    }

    public static void copyFile(String sourceFile, String destFile) throws IOException {
        File file = new File(sourceFile);
        File dest = new File(destFile);
        file.renameTo(dest);
    }

    public static boolean deleteFolder(String folderPath) {
        File folder = new File(folderPath);
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteFolder(file.getAbsolutePath());
                }
            }
        }
        return folder.delete();
    }
}