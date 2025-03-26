package com.moonlit.moonlitNetwork;

// Bukkit/Spigot Imports
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginDescriptionFile;

// Java IO and Networking Imports
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

// JSON Parsing Import (Gson)
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

public final class MoonlitNetwork extends JavaPlugin {

    // --- Constants ---
    private static final String DISPLAY_NAME = "Moonlit Network";
    private static final String CORE_FOLDER_NAME = "Moonlit Core";
    private static final String CONFIG_SUBFOLDER_NAME = "Config";
    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final Gson gson = new Gson();

    // --- Custom Configuration Fields ---
    private FileConfiguration customConfig = null;
    private File customConfigFile = null;

    // --- Plugin Lifecycle Methods ---

    @Override
    public void onEnable() {
        getLogger().info("Setting up directories...");
        createPluginFolders();

        getLogger().info("Loading configuration...");
        loadCustomConfig();

        if (getCustomConfig().getBoolean("updater.check-on-startup", true)) {
            Bukkit.getScheduler().runTaskAsynchronously(this, this::checkForUpdates);
        } else {
            getLogger().info("Update checking on startup is disabled in config.yml.");
        }

        sendStyledMessage("Plugin has been enabled!", ChatColor.GREEN);
    }

    @Override
    public void onDisable() {
        sendStyledMessage("Plugin has been disabled!", ChatColor.RED);
    }

    // --- Custom Configuration Handling ---

    public FileConfiguration getCustomConfig() {
        if (customConfig == null) {
            loadCustomConfig();
        }
        return customConfig;
    }

    public void loadCustomConfig() {
        File configDir = new File(getDataFolder().getParentFile() + File.separator + CORE_FOLDER_NAME, CONFIG_SUBFOLDER_NAME);
        customConfigFile = new File(configDir, CONFIG_FILE_NAME);

        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        if (!customConfigFile.exists()) {
            getLogger().info("Config file not found at '" + customConfigFile.getPath() + "', creating default.");
            saveDefaultCustomConfig();
        }

        customConfig = YamlConfiguration.loadConfiguration(customConfigFile);

        InputStream defaultConfigStream = getResource(CONFIG_FILE_NAME);
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
            customConfig.setDefaults(defaultConfig);
        }
        getLogger().info("Configuration loaded from: " + customConfigFile.getPath());
    }

    private void saveDefaultCustomConfig() {
        if (customConfigFile == null) {
            File configDir = new File(getDataFolder().getParentFile() + File.separator + CORE_FOLDER_NAME, CONFIG_SUBFOLDER_NAME);
            customConfigFile = new File(configDir, CONFIG_FILE_NAME);
        }

        if (!customConfigFile.getParentFile().exists()) {
            customConfigFile.getParentFile().mkdirs();
        }

        InputStream defaultConfigStream = getResource(CONFIG_FILE_NAME);
        if (defaultConfigStream != null && !customConfigFile.exists()) {
            try {
                Files.copy(defaultConfigStream, customConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Saved default configuration to " + customConfigFile.getPath());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not save default config to " + customConfigFile.getPath(), e);
            } finally {
                try { defaultConfigStream.close(); } catch (IOException e) { }
            }
        } else if(defaultConfigStream == null) {
            getLogger().severe("Could not find default '" + CONFIG_FILE_NAME + "' in JAR resources!");
        }
    }

    public void saveCustomConfig() {
        if (customConfig == null || customConfigFile == null) {
            getLogger().warning("Cannot save config, it was not loaded properly.");
            return;
        }
        try {
            getCustomConfig().save(customConfigFile);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not save config to " + customConfigFile.getPath(), ex);
        }
    }

    public void reloadCustomConfig() {
        if (customConfigFile == null) {
            File configDir = new File(getDataFolder().getParentFile() + File.separator + CORE_FOLDER_NAME, CONFIG_SUBFOLDER_NAME);
            customConfigFile = new File(configDir, CONFIG_FILE_NAME);
        }
        customConfig = YamlConfiguration.loadConfiguration(customConfigFile);

        InputStream defaultConfigStream = getResource(CONFIG_FILE_NAME);
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
            customConfig.setDefaults(defaultConfig);
        }
        getLogger().info("Configuration reloaded from: " + customConfigFile.getPath());
    }

    // --- Folder Creation Logic ---

    /**
     * Creates the necessary directories directly under the main /plugins/ folder.
     * WARNING: This deviates from standard Bukkit/Spigot practice.
     */
    private void createPluginFolders() {
        File pluginsFolder = getDataFolder().getParentFile();

        if (pluginsFolder == null || !pluginsFolder.isDirectory()) {
            getLogger().severe("Could not locate the main plugins directory!");
            return;
        }

        File moonlitCoreBaseFolder = new File(pluginsFolder, CORE_FOLDER_NAME);
        File scriptsFolder = new File(moonlitCoreBaseFolder, "Scripts");
        File configFolder = new File(moonlitCoreBaseFolder, CONFIG_SUBFOLDER_NAME);

        if (!moonlitCoreBaseFolder.exists()) {
            if (moonlitCoreBaseFolder.mkdirs()) {
                getLogger().info("Created directory: " + moonlitCoreBaseFolder.getPath());
            } else {
                getLogger().severe("Could not create directory: " + moonlitCoreBaseFolder.getPath() + ". Check permissions.");
                return;
            }
        }

        if (!scriptsFolder.exists()) {
            if (scriptsFolder.mkdirs()) {
                getLogger().info("Created directory: " + scriptsFolder.getPath());
            } else {
                getLogger().severe("Could not create directory: " + scriptsFolder.getPath());
            }
        }

        if (!configFolder.exists()) {
            if (configFolder.mkdirs()) {
                getLogger().info("Created directory: " + configFolder.getPath());
            } else {
                getLogger().severe("Could not create directory: " + configFolder.getPath());
            }
        }
    }

    // --- Auto-Updater Logic ---

    private void checkForUpdates() {
        String owner = getCustomConfig().getString("updater.github-owner");
        String repo = getCustomConfig().getString("updater.github-repo");
        String currentVersion = getDescription().getVersion();

        if (owner == null || owner.isEmpty() || owner.equalsIgnoreCase("YourGitHubUsername") ||
                repo == null || repo.isEmpty() || repo.equalsIgnoreCase("YourPluginRepositoryName")) {
            if(getCustomConfig().getBoolean("updater.check-on-startup", true)) {
                getLogger().warning("Updater is enabled but GitHub owner/repo is not configured correctly in config.yml.");
            }
            return;
        }

        getLogger().info("Checking for updates via GitHub API...");

        try {
            URL url = new URL("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", getName() + "-Updater/" + currentVersion);
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                JsonObject releaseInfo = gson.fromJson(reader, JsonObject.class);
                reader.close();

                String latestVersionTag = releaseInfo.get("tag_name").getAsString();
                String latestVersion = latestVersionTag;
                if (latestVersion.startsWith("v")) {
                    latestVersion = latestVersion.substring(1);
                }

                getLogger().info("Current version: " + currentVersion + ", Latest version found on GitHub: " + latestVersion);

                if (isNewerVersion(latestVersion, currentVersion)) {
                    getLogger().info("A new version is available: " + latestVersion);
                    notifyAdmins(ChatColor.GREEN + "A new version of " + DISPLAY_NAME + " (" + latestVersion + ") is available!");

                    if (getCustomConfig().getBoolean("updater.download-updates", true)) {
                        String assetName = getCustomConfig().getString("updater.jar-asset-name");
                        if (assetName == null || assetName.isEmpty()) {
                            getLogger().warning("Cannot download update: 'updater.jar-asset-name' not set in config.yml.");
                            notifyAdmins(ChatColor.RED + "Update found, but download failed: JAR asset name not configured.");
                            connection.disconnect(); // Disconnect before returning
                            return;
                        }

                        String downloadUrl = null;
                        if (releaseInfo.has("assets") && releaseInfo.get("assets").isJsonArray()) {
                            for (JsonElement assetElement : releaseInfo.getAsJsonArray("assets")) {
                                JsonObject asset = assetElement.getAsJsonObject();
                                if (asset.has("name") && asset.get("name").getAsString().equalsIgnoreCase(assetName)) {
                                    if (asset.has("browser_download_url")) {
                                        downloadUrl = asset.get("browser_download_url").getAsString();
                                        break;
                                    }
                                }
                            }
                        }

                        if (downloadUrl != null) {
                            getLogger().info("Update target JAR: " + assetName);
                            getLogger().info("Attempting to download update from: " + downloadUrl);
                            notifyAdmins(ChatColor.YELLOW + "Downloading " + DISPLAY_NAME + " version " + latestVersion + "...");
                            downloadUpdate(downloadUrl, assetName);
                        } else {
                            getLogger().warning("Could not find asset '" + assetName + "' attached to the latest release (" + latestVersionTag + ").");
                            notifyAdmins(ChatColor.RED + "Update found, but could not find the download asset '" + assetName + "'. Check GitHub release attachments.");
                        }
                    } else {
                        getLogger().info("Automatic update download is disabled in config.yml.");
                        notifyAdmins(ChatColor.YELLOW + "Please update " + DISPLAY_NAME + " manually.");
                    }
                } else {
                    getLogger().info("You are running the latest version (or GitHub version is not newer).");
                }

            } else {
                getLogger().warning("Failed to check for updates. GitHub API responded with code: " + responseCode);
                try (InputStream errorStream = connection.getErrorStream()) {
                    if (errorStream != null) {
                        String errorDetails = new String(errorStream.readAllBytes());
                        getLogger().warning("GitHub API Error Details: " + errorDetails);
                    }
                } catch (IOException ex) { }
            }
            connection.disconnect();

        } catch (IOException | JsonSyntaxException e) {
            getLogger().log(Level.WARNING, "Could not check for updates due to an error: " + e.getMessage());
        }
    }

    private boolean isNewerVersion(String remoteVersion, String currentVersion) {
        if (remoteVersion == null || remoteVersion.isEmpty() || currentVersion == null || currentVersion.isEmpty()) {
            return false;
        }

        String[] remoteParts = remoteVersion.split("\\.");
        String[] currentParts = currentVersion.split("\\.");

        int length = Math.max(remoteParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int remotePart = 0;
            if (i < remoteParts.length) {
                try { remotePart = Integer.parseInt(remoteParts[i].replaceAll("[^0-9]", "")); }
                catch (NumberFormatException e) { }
            }

            int currentPart = 0;
            if (i < currentParts.length) {
                try { currentPart = Integer.parseInt(currentParts[i].replaceAll("[^0-9]", "")); }
                catch (NumberFormatException e) { }
            }

            if (remotePart > currentPart) return true;
            if (remotePart < currentPart) return false;
        }
        return false;
    }

    private void downloadUpdate(String downloadUrl, String expectedAssetName) {
        File updateFolder = new File(getDataFolder().getParentFile(), "update");
        if (!updateFolder.exists()) {
            if (!updateFolder.mkdirs()) {
                getLogger().severe("Could not create update directory: " + updateFolder.getPath());
                notifyAdmins(ChatColor.RED + "Update download failed: Could not create update directory.");
                return;
            }
        }

        String targetFileName = getDescription().getName() + ".jar";
        File destinationFile = new File(updateFolder, targetFileName);
        File tempFile = new File(updateFolder, targetFileName + ".download");

        HttpURLConnection connection = null;
        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            URL url = new URL(downloadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", getName() + "-Updater/" + getDescription().getVersion());
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(60000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                long fileSize = connection.getContentLengthLong();
                getLogger().info("Downloading " + expectedAssetName + " ("
                        + (fileSize > 0 ? (fileSize / 1024) + " KB" : "size unknown") + ") to temporary file: "
                        + tempFile.getPath());

                inputStream = connection.getInputStream();
                outputStream = new FileOutputStream(tempFile);

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                getLogger().info("Download complete. Moving to update folder...");
                Files.move(tempFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                getLogger().info(ChatColor.GREEN + "Update downloaded successfully to " + destinationFile.getPath());
                notifyAdmins(ChatColor.GREEN + DISPLAY_NAME + " update downloaded! Please restart the server to apply the changes.");

            } else {
                getLogger().warning("Failed to download update. Download server responded with code: " + responseCode);
                notifyAdmins(ChatColor.RED + "Update download failed. Check console logs for details (Response code: " + responseCode + ").");
            }

        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed during update download or saving: " + e.getMessage(), e);
            notifyAdmins(ChatColor.RED + "Update download failed due to an error: " + e.getMessage());
        } finally {
            if (outputStream != null) { try { outputStream.close(); } catch (IOException e) { } }
            if (inputStream != null) { try { inputStream.close(); } catch (IOException e) { } }
            if (connection != null) { connection.disconnect(); }
            if(tempFile.exists()) { if (!tempFile.delete()) { getLogger().warning("Could not delete temporary download file: " + tempFile.getPath()); } }
        }
    }

    private void notifyAdmins(String message) {
        String prefix = ChatColor.AQUA + "[" + DISPLAY_NAME + " Updater] ";
        Bukkit.getConsoleSender().sendMessage(prefix + message);

        if (getCustomConfig().getBoolean("updater.notify-admins", true)) {
            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.getOnlinePlayers().forEach(player -> {
                    if (player.hasPermission("moonlit.admin.notify")) {
                        player.sendMessage(prefix + message);
                    }
                });
            });
        }
    }

    // --- Styled Console Message Logic ---

    private void sendStyledMessage(String statusMessage, ChatColor statusColor) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        PluginDescriptionFile description = getDescription();

        int totalWidth = 42;
        String borderColor = ChatColor.GRAY.toString();
        String keyColor = ChatColor.AQUA.toString();
        String valueColor = ChatColor.WHITE.toString();

        String border = borderColor + "+" + "-".repeat(totalWidth - 2) + "+";

        String pluginLine = String.format("%s| %s%-9s %s%s %s|", borderColor, keyColor, "Plugin:", valueColor, DISPLAY_NAME, borderColor);
        String typeLine = String.format("%s| %s%-9s %s%s %s|", borderColor, keyColor, "Type:", valueColor, "Core Plugin", borderColor);
        String versionLine = String.format("%s| %s%-9s %s%s %s|", borderColor, keyColor, "Version:", valueColor, description.getVersion(), borderColor);
        String statusLine = String.format("%s| %s%-9s %s%s%s %s|", borderColor, keyColor, "Status:", statusColor, statusMessage, valueColor, borderColor);

        pluginLine = padRight(pluginLine, totalWidth, borderColor + "|");
        typeLine = padRight(typeLine, totalWidth, borderColor + "|");
        versionLine = padRight(versionLine, totalWidth, borderColor + "|");
        statusLine = padRight(statusLine, totalWidth, borderColor + "|");

        console.sendMessage("");
        console.sendMessage(border);
        console.sendMessage(pluginLine);
        console.sendMessage(typeLine);
        console.sendMessage(versionLine);
        console.sendMessage(statusLine);
        console.sendMessage(border);
        console.sendMessage(ChatColor.RESET + "");
    }

    private String padRight(String input, int width, String endChar) {
        String strippedInput = ChatColor.stripColor(input);
        int currentVisibleLength = strippedInput.length();
        String strippedEndChar = ChatColor.stripColor(endChar);
        if (strippedInput.endsWith(strippedEndChar)) {
            currentVisibleLength = strippedInput.substring(0, strippedInput.length() - strippedEndChar.length()).length();
        }

        int neededPadding = width - 2 - currentVisibleLength;
        if (neededPadding < 0) neededPadding = 0;

        String baseInput = input;
        if (input.endsWith(endChar)) {
            baseInput = input.substring(0, input.lastIndexOf(endChar));
        }
        return baseInput + " ".repeat(neededPadding) + " " + endChar + ChatColor.RESET;
    }

} // End of MoonlitNetwork class