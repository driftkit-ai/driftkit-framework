package ai.driftkit.common.utils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Utility class to locate and list resources within a JAR file or directory on the classpath.
 */
public class JarResourceLister {

    /**
     * Retrieves the absolute path to the JAR file or directory from which the specified class was loaded.
     *
     * @param clazz The class to locate the JAR file or directory for.
     * @return      The absolute file system path to the JAR file or directory.
     * @throws URISyntaxException If the URL to URI conversion fails.
     */
    public static String getJarOrDirectoryPath(Class<?> clazz) throws URISyntaxException {
        // Obtain the URL of the JAR file or directory
        URL resourceURL = clazz.getProtectionDomain().getCodeSource().getLocation();
        // Convert URL to URI and then to a file system path
        return Paths.get(resourceURL.toURI()).toString();
    }

    /**
     * Lists all resources within a specific folder inside the JAR or directory.
     *
     * @param jarOrDirPath The file system path to the JAR file or directory.
     * @param folderPath   The folder path inside the JAR or directory (e.g., "resources/images/").
     *                     Ensure it ends with a '/'.
     * @return             A list of resource names relative to the folderPath.
     * @throws IOException If an I/O error occurs while reading the JAR file or directory.
     */
    public static List<String> listResources(String jarOrDirPath, String folderPath) throws IOException {
        List<String> resources = new ArrayList<>();
        File file = new File(jarOrDirPath);

        if (file.isDirectory()) {
            // Handle directory
            Path dirPath = Paths.get(jarOrDirPath, folderPath);
            if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                    for (Path entry : stream) {
                        String currentInternalDir = entry.toString().replace(jarOrDirPath, "");

                        if (Files.isRegularFile(entry)) {
                            resources.add(currentInternalDir);
                        } else {
                            resources.addAll(listResources(jarOrDirPath, currentInternalDir));
                        }
                    }
                }
            } else {
                System.err.println("The specified folder path does not exist or is not a directory: " + dirPath);
            }
        } else {
            // Assume it's a JAR file
            try (JarFile jarFile = new JarFile(jarOrDirPath)) {
                Enumeration<JarEntry> entries = jarFile.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    // Check if the entry is in the desired folder and is not a directory
                    if (entryName.startsWith(folderPath)) {
                        if (entry.isDirectory()) {
                            resources.addAll(listResources(jarOrDirPath, entryName));
                        } else {
                            // Extract the relative path
                            String relativePath = entryName.substring(folderPath.length());

                            // Optionally, exclude files in subdirectories
                            if (!relativePath.isEmpty() && !relativePath.contains("/")) {
                                resources.add(relativePath);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to read JAR file: " + jarOrDirPath);
                throw e;
            }
        }

        return resources;
    }

    /**
     * Convenience method to list resources from a specific folder relative to the given class.
     *
     * @param clazz  The class to locate the JAR or directory for.
     * @param folder The folder path inside the JAR or directory.
     * @return       A list of resource names relative to the folder.
     * @throws URISyntaxException If the URL to URI conversion fails.
     * @throws IOException        If an I/O error occurs while reading the JAR file or directory.
     */
    public static List<String> listResources(Class<?> clazz, String folder) throws URISyntaxException, IOException {
        String jarOrDirPath = getJarOrDirectoryPath(clazz);
        return listResources(jarOrDirPath, folder);
    }
}