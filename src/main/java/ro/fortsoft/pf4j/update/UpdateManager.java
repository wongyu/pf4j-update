/*
 * Copyright 2014 Decebal Suiu
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with
 * the License. You may obtain a copy of the License in the LICENSE file, or at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package ro.fortsoft.pf4j.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.PluginManager;
import ro.fortsoft.pf4j.PluginState;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Decebal Suiu
 */
public class UpdateManager {

    private static final Logger log = LoggerFactory.getLogger(UpdateManager.class);

    private static final String repositoriesFile = "repositories.json";

    private List<UpdateRepository> repositories;

    public PluginManager pluginManager;

    public UpdateManager(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public List<UpdateRepository.PluginInfo> getAvailablePlugins() {
        List<UpdateRepository.PluginInfo> availablePlugins = new ArrayList<UpdateRepository.PluginInfo>();
        List<UpdateRepository.PluginInfo> plugins = getPlugins();
        for (UpdateRepository.PluginInfo plugin : plugins) {
            if (pluginManager.getPlugin(plugin.id) == null) {
                availablePlugins.add(plugin);
            }
        }

        return availablePlugins;
    }

    public boolean hasAvailablePlugins() {
        List<UpdateRepository.PluginInfo> plugins = getPlugins();
        for (UpdateRepository.PluginInfo plugin : plugins) {
            if (pluginManager.getPlugin(plugin.id) == null) {
                return true;
            }
        }

        return false;
    }

    public List<UpdateRepository.PluginInfo> getUpdates() {
        List<UpdateRepository.PluginInfo> updates = new ArrayList<UpdateRepository.PluginInfo>();
        List<UpdateRepository.PluginInfo> plugins = getPlugins();
        for (UpdateRepository.PluginInfo plugin : plugins) {
            PluginWrapper installedPlugin = pluginManager.getPlugin(plugin.id);
            if (installedPlugin != null) {
                Version installedVersion = installedPlugin.getDescriptor().getVersion();
                if (plugin.hasUpdate(getSystemVersion(), installedVersion)) {
                    updates.add(plugin);
                }
            }
        }

        return updates;
    }

    public boolean hasUpdates() {
        List<UpdateRepository.PluginInfo> plugins = getPlugins();
        for (UpdateRepository.PluginInfo plugin : plugins) {
            PluginWrapper installedPlugin = pluginManager.getPlugin(plugin.id);
            if (installedPlugin != null) {
                Version installedVersion = installedPlugin.getDescriptor().getVersion();
                if (plugin.hasUpdate(getSystemVersion(), installedVersion)) {
                    return true;
                }
            }
        }

        return false;
    }

    public List<UpdateRepository.PluginInfo> getPlugins() {
        List<UpdateRepository.PluginInfo> plugins = new ArrayList<UpdateRepository.PluginInfo>();
        List<UpdateRepository> repositories = getRepositories();
        for (UpdateRepository repository : repositories) {
            plugins.addAll(repository.getPlugins());
        }

        return plugins;
    }

    public List<UpdateRepository> getRepositories() {
        if (repositories == null) {
            initRepositories();
        }

        return repositories;
    }

    public synchronized void refresh() {
        repositories = null;
    }

    public synchronized boolean installPlugin(String url) {
        File pluginArchiveFile;
        try {
            pluginArchiveFile = new FileDownloader().downloadFile(url);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return false;
        }

        String pluginId = pluginManager.loadPlugin(pluginArchiveFile);
        PluginState state = pluginManager.startPlugin(pluginId);

        return PluginState.STARTED.equals(state);
    }

    public boolean updatePlugin(String id, String url) {
        File pluginArchiveFile;
        try {
            pluginArchiveFile = new FileDownloader().downloadFile(url);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return false;
        }

        if (!pluginManager.deletePlugin(id)) {
            return false;
        }

        String newPluginId = pluginManager.loadPlugin(pluginArchiveFile);
        PluginState state = pluginManager.startPlugin(newPluginId);

        return PluginState.STARTED.equals(state);
    }

    public boolean uninstallPlugin(String id) {
        return pluginManager.deletePlugin(id);
    }

    private synchronized void initRepositories() {
        FileReader reader = null;
        try {
            log.debug("Read repositories from '{}'", repositoriesFile);
            reader = new FileReader(repositoriesFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            repositories = Collections.emptyList();
            return;
        }

        Gson gson = new GsonBuilder().create();
        UpdateRepository[] items = gson.fromJson(reader, UpdateRepository[].class);

        repositories = Arrays.asList(items);
    }

    private Version getSystemVersion() {
        return pluginManager.getSystemVersion();
    }

}
