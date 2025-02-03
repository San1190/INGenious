package com.ing.ide.main.testar.statemodel;

import com.ing.ide.main.utils.Utils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testar.monkey.alayer.TaggableBase;
import org.testar.statemodel.StateModelTags;

import java.io.*;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class StateModelConfig extends TaggableBase {

    private static final Logger logger = LogManager.getLogger();

    private static String directoryPath;
    private static String graphsPath;
    private static String orientDBPath;

    static {
        try {
            directoryPath = Utils.getAppRoot() + File.separator;
            graphsPath = directoryPath + "testar" + File.separator + "graphs";
            orientDBPath = directoryPath + "orientdb-community-3.0.34/databases";
            setupLocalOrientDB();
        } catch (IOException e) {
            logger.log(Level.ERROR, e.getMessage());
        }
    }

    private StateModelConfig() { }

    public static TaggableBase getDefaultConfig() {
        TaggableBase defaults = new TaggableBase();

        defaults.set(StateModelTags.StateModelEnabled, true);
        defaults.set(StateModelTags.DataStore, "OrientDB");
        defaults.set(StateModelTags.DataStoreType, "plocal");
        defaults.set(StateModelTags.DataStoreServer, "");
        defaults.set(StateModelTags.DataStoreDirectory, orientDBPath);
        defaults.set(StateModelTags.DataStoreDB, "testar");
        defaults.set(StateModelTags.DataStoreUser, "admin");
        defaults.set(StateModelTags.DataStorePassword, "admin");
        defaults.set(StateModelTags.DataStoreMode, "instant");
        defaults.set(StateModelTags.ActionSelectionAlgorithm, "random");
        defaults.set(StateModelTags.StateModelStoreWidgets, true);
        defaults.set(StateModelTags.ResetDataStore, false);

        return defaults;
    }

    public static String getGraphsPath() {
        return graphsPath;
    }

    private static void setupLocalOrientDB() {
        try {
            String downloadUrl = "https://repo1.maven.org/maven2/com/orientechnologies/orientdb-community/3.0.34/orientdb-community-3.0.34.zip";
            String zipFilePath = directoryPath + "/orientdb-community-3.0.34.zip";
            String extractDir = directoryPath + "/orientdb-community-3.0.34";

            // If OrientDB already exists, we don't need to download anything
            if(new File(extractDir).exists()) return;

            // Create the directory if it doesn't exist
            File directory = new File(directoryPath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Download the zip file
            try (InputStream in = new URL(downloadUrl).openStream();
                 FileOutputStream out = new FileOutputStream(zipFilePath)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            // Extract the zip file
            try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath))) {
                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    File filePath = new File(directoryPath, entry.getName());
                    if (entry.isDirectory()) {
                        filePath.mkdirs();
                    } else {
                        // Ensure parent directories exist
                        File parentDir = filePath.getParentFile();
                        if (!parentDir.exists()) {
                            parentDir.mkdirs();
                        }
                        try (FileOutputStream out = new FileOutputStream(filePath)) {
                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = zipIn.read(buffer)) > 0) {
                                out.write(buffer, 0, len);
                            }
                        }
                    }
                    zipIn.closeEntry();
                }
            }

            // Change to the bin directory and execute the command
            ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", "console.bat", "CREATE", "DATABASE", "plocal:../databases/testar", "admin", "admin");
            processBuilder.directory(new File(extractDir + "/bin"));
            processBuilder.inheritIO();
            Process process = processBuilder.start();

            // Wait for the command to complete
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Command execution failed with exit code " + exitCode);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
