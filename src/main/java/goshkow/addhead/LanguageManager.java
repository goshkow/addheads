package goshkow.addhead;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads player-facing messages from resource-backed language files.
 */
public final class LanguageManager {

    private static final List<String> BUNDLED_LANGUAGE_FILES = List.of(
            "en-us.yml",
            "ru-ru.yml",
            "es-es.yml",
            "de-de.yml",
            "fr-fr.yml",
            "pt-br.yml",
            "zh-cn.yml"
    );

    private final Plugin plugin;
    private YamlConfiguration languageConfig;
    private String currentLanguageFile = "en-us.yml";

    public LanguageManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload(String languageFileName) {
        for (String bundled : BUNDLED_LANGUAGE_FILES) {
            ensureLanguageFile(bundled);
        }

        String selected = languageFileName == null || languageFileName.isBlank() ? "en-us.yml" : languageFileName;
        File folder = new File(plugin.getDataFolder(), "languages");
        File file = new File(folder, selected);
        if (!file.isFile()) {
            plugin.getLogger().warning("Language file " + selected + " was not found. Falling back to en-us.yml.");
            file = new File(folder, "en-us.yml");
        }

        currentLanguageFile = file.getName();
        languageConfig = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration englishTemplate = loadBundledTemplate("en-us.yml");
        if (englishTemplate != null && mergeMissingKeys(languageConfig, englishTemplate)) {
            try {
                languageConfig.save(file);
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to update language file " + file.getName() + ": " + exception.getMessage());
            }
        }
    }

    public String getCurrentLanguageFile() {
        return currentLanguageFile;
    }

    public List<String> getAvailableLanguageFiles() {
        File folder = new File(plugin.getDataFolder(), "languages");
        if (!folder.isDirectory()) {
            return List.of(currentLanguageFile);
        }

        List<String> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(folder.toPath())) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".yml"))
                    .map(path -> path.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(result::add);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to list language files: " + exception.getMessage());
        }

        if (result.isEmpty()) {
            return List.of(currentLanguageFile);
        }

        if (!result.contains(currentLanguageFile)) {
            result.add(0, currentLanguageFile);
        }

        return Collections.unmodifiableList(result);
    }

    public String get(String key) {
        if (languageConfig == null) {
            return key;
        }
        return colorize(languageConfig.getString(key, key));
    }

    public String get(String key, Map<String, String> placeholders) {
        String value = get(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    }

    private void ensureLanguageFile(String fileName) {
        File folder = new File(plugin.getDataFolder(), "languages");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File target = new File(folder, fileName);
        if (target.isFile()) {
            return;
        }

        try (InputStream in = plugin.getResource("languages/" + fileName)) {
            if (in != null) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to create language file " + fileName + ": " + exception.getMessage());
        }
    }

    private YamlConfiguration loadBundledTemplate(String fileName) {
        try (InputStream in = plugin.getResource("languages/" + fileName)) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to load bundled language template " + fileName + ": " + exception.getMessage());
            return null;
        }
    }

    private boolean mergeMissingKeys(YamlConfiguration target, YamlConfiguration template) {
        return mergeMissingKeys(target, template, "");
    }

    private boolean mergeMissingKeys(YamlConfiguration target, ConfigurationSection templateSection, String path) {
        boolean changed = false;
        for (String key : templateSection.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            Object templateValue = templateSection.get(key);
            if (templateValue instanceof ConfigurationSection) {
                if (!target.isConfigurationSection(fullPath)) {
                    if (target.contains(fullPath)) {
                        target.set(fullPath, null);
                    }
                    target.createSection(fullPath);
                    changed = true;
                }
                ConfigurationSection childSection = templateSection.getConfigurationSection(key);
                if (childSection != null) {
                    changed |= mergeMissingKeys(target, childSection, fullPath);
                }
                continue;
            }

            if (!target.contains(fullPath)) {
                target.set(fullPath, templateValue);
                changed = true;
            }
        }
        return changed;
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
