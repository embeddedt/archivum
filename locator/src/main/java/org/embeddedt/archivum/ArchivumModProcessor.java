package org.embeddedt.archivum;

import com.google.common.io.ByteStreams;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class ArchivumModProcessor {
    public static void processAndWrite(byte[] fileContents, Path targetFile, String fakeModId) throws IOException {
        try(JarInputStream jarStream = new JarInputStream(new ByteArrayInputStream(fileContents))) {
            try(JarOutputStream newJarStream = new JarOutputStream(Files.newOutputStream(targetFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), jarStream.getManifest())) {
                PrintWriter newJarWriter = new PrintWriter(newJarStream);
                JarEntry entry;
                boolean seenModFile = false;
                while((entry = jarStream.getNextJarEntry()) != null) {
                    if(entry.getName().equals("META-INF/mods.toml")) {
                        seenModFile = true;
                    }
                    newJarStream.putNextEntry(entry);
                    // Copy file contents
                    ByteStreams.copy(jarStream, newJarStream);
                }
                if(!seenModFile) {
                    ArchivumModLocator.LOGGER.info("Injecting mods.toml file");
                    newJarStream.putNextEntry(new ZipEntry("META-INF/mods.toml"));
                    newJarWriter.println("modLoader=\"javafml\"");
                    newJarWriter.println("loaderVersion=\"[36,)\"");
                    newJarWriter.println("license=\"UNKNOWN\"");
                    newJarWriter.println("[[mods]]");
                    newJarWriter.println("modId=\"" + fakeModId + "\"");
                    newJarWriter.flush();
                }
            }
        }
    }
}
