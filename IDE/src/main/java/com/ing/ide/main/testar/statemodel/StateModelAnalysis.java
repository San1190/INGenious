package com.ing.ide.main.testar.statemodel;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testar.monkey.alayer.TaggableBase;
import org.testar.statemodel.StateModelTags;
import org.testar.statemodel.analysis.AnalysisManager;
import org.testar.statemodel.analysis.webserver.JettyServer;
import org.testar.statemodel.persistence.orientdb.entity.Config;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class StateModelAnalysis {

    private static final Logger logger = LogManager.getLogger();

    private final Config config = new Config();

    public StateModelAnalysis() {
        // Get the State Model configuration
        TaggableBase stateModelConfig = StateModelConfig.getDefaultConfig();

        // create a config object for the orientdb database connection info
        config.setConnectionType(stateModelConfig.get(StateModelTags.DataStoreType));
        config.setServer(stateModelConfig.get(StateModelTags.DataStoreServer));
        config.setDatabase(stateModelConfig.get(StateModelTags.DataStoreDB));
        config.setUser(stateModelConfig.get(StateModelTags.DataStoreUser));
        config.setPassword(stateModelConfig.get(StateModelTags.DataStorePassword));
        config.setDatabaseDirectory(stateModelConfig.get(StateModelTags.DataStoreDirectory));
    }

    // Start a jetty integrated server and show the model listings page
    public void openServer() {
        try {
            String graphsPath = StateModelConfig.getGraphsPath();
            AnalysisManager analysisManager = new AnalysisManager(config, graphsPath);
            JettyServer jettyServer = new JettyServer();
            jettyServer.start(graphsPath, analysisManager);
        } catch (Exception e) {
            // If the exception is because the server is already running, just catch and connect
            if(e.getCause() != null && e.getCause().getMessage() != null && e.getCause().getMessage().contains("Address already in use")) {
                logger.log(Level.WARN, e.getCause().getMessage());
                // Continue and try to open the browser to the running server
            } else {
                logger.log(Level.ERROR, e.getMessage());
                e.printStackTrace();
                // Something wrong with the database connection, return because we don't want to open the browser
                return;
            }
        }

        openBrowser();
    }

    private void openBrowser() {
        try {
            Desktop desktop = java.awt.Desktop.getDesktop();
            URI uri = new URI("http://localhost:8090/models");
            desktop.browse(uri);
        } catch (IOException | URISyntaxException e) {
            logger.log(Level.ERROR, e.getMessage());
            e.printStackTrace();
        }
    }
}
