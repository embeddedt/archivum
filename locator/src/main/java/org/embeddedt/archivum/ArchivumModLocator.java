package org.embeddedt.archivum;

import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ArchivumModLocator extends AbstractJarFileLocator {
    private static final Logger LOGGER = LogManager.getLogger("Archivum");
    private static final Path JAR_IN_JAR_CACHE = FMLPaths.GAMEDIR.get().resolve(".archivum").resolve("jarjar");

    @Override
    public List<IModFile> scanMods() {
        LOGGER.info("Scanning for JiJ mods...");
        try(Stream<Path> modJars = Files.list(FMLPaths.MODSDIR.get())) {
            if(!Files.exists(JAR_IN_JAR_CACHE))
                Files.createDirectories(JAR_IN_JAR_CACHE);
            return modJars.filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".jar")).flatMap(this::checkJarForJars).collect(Collectors.toList());
        } catch(IOException e) {
            LOGGER.error("Error listing mods folder", e);
            return Collections.emptyList();
        }
    }

    private Optional<Path> convertJarEntryToDisk(JsonObject entry, ZipFile containingFile) {
        String path = entry.getAsJsonPrimitive("path").getAsString();
        try {
            ZipEntry file = containingFile.getEntry(path);
            if (file == null) {
                return Optional.empty();
            }
            byte[] fileContents;
            try(InputStream stream = containingFile.getInputStream(file)) {
                fileContents = ByteStreams.toByteArray(stream);
            }
            String hash = Hashing.crc32().hashBytes(fileContents).toString();
            String fileName = path.substring(path.lastIndexOf('/') + 1); // this will return the base name
            String targetFileName = Pattern.compile("\\.jar$").matcher(fileName).replaceFirst("-" + hash + ".jar");
            Path targetFile = JAR_IN_JAR_CACHE.resolve(targetFileName);
            try {
                long size = Files.size(targetFile);
                if(size == fileContents.length) {
                    // Probably correct
                    LOGGER.debug("Skipped copying {} because it's already correct", targetFileName);
                    return Optional.of(targetFile);
                }
            } catch(IOException e) {
                // Assume it does not exist
            }
            // We need to copy the mod to this path
            try(OutputStream os = Files.newOutputStream(targetFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                os.write(fileContents);
            }
            return Optional.of(targetFile);
        } catch(IOException e) {
            LOGGER.error("Error reading jar '{}'", path, e);
            return Optional.empty();
        }
    }

    private Stream<ModFile> checkJarForJars(Path jarFile) {
        try(ZipFile zipFile = new ZipFile(jarFile.toFile())) {
            ZipEntry entry = zipFile.getEntry("META-INF/jarjar/metadata.json");
            if (entry == null) {
                return Stream.of();
            }

            try(JsonReader reader = new JsonReader(new InputStreamReader(zipFile.getInputStream(entry)))) {
                JsonObject obj = (JsonObject)new JsonParser().parse(reader);
                JsonArray array = obj.getAsJsonArray("jars");
                return StreamSupport.stream(array.spliterator(), false)
                        .map(element -> convertJarEntryToDisk((JsonObject)element, zipFile))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(path -> ModFile.newFMLInstance(path, this))
                        .peek(modFile -> {
                            LOGGER.info("Found and loaded JiJ file: {}", modFile.getFilePath());
                            this.modJars.compute(modFile, (mf, fs) -> this.createFileSystem(mf));
                        })
                        .collect(Collectors.toList()) // create the mod files now instead of lazily
                        .stream();
            }
        } catch(IOException | RuntimeException e) {
            LOGGER.error("Error checking jar {} for jars", jarFile, e);
            return Stream.of();
        }
    }

    @Override
    public Optional<Manifest> findManifest(Path file) {
        Optional<Manifest> optional = super.findManifest(file);
        return optional.map(manifest -> {
            String type = manifest.getMainAttributes().getValue(ModFile.TYPE);
            if(type != null && type.equals("GAMELIBRARY")) {
                // Need to make it LIBRARY for 1.16
                LOGGER.warn("'{}' is marked as GAMELIBRARY, loading as LIBRARY instead", file);
                manifest.getMainAttributes().put(ModFile.TYPE, "LIBRARY");
            }
            return manifest;
        });
    }

    @Override
    public String name() {
        return "archivum";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {

    }
}
