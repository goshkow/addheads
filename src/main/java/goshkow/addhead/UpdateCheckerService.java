package goshkow.addhead;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks GitHub and Modrinth for newer releases and notifies admins in chat.
 */
public final class UpdateCheckerService {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(?i)(?:\\bv)?(\\d+(?:\\.\\d+)*)(?:[-+][0-9A-Za-z.-]+)?");
    private static final String GITHUB_REPOSITORY = "goshkow/addheads";
    private static final String GITHUB_LATEST_URL = "https://github.com/goshkow/addheads/releases/latest";
    private static final String MODRINTH_PROJECT = "addheads";
    private static final String MODRINTH_LATEST_URL = "https://modrinth.com/plugin/addheads/versions";
    private static final String UPDATE_CONFIRMATION = "update";

    private final AddHeads plugin;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<UUID, String> pendingDownloads = new ConcurrentHashMap<>();
    private volatile BukkitTask task;
    private volatile UpdateState currentState;

    public UpdateCheckerService(AddHeads plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public synchronized void start() {
        stop();
        if (!isEnabled()) {
            return;
        }

        long period = Math.max(20L, plugin.getUpdateCheckIntervalTicks());
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkSafely, 40L, period);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::checkSafely);
    }

    public synchronized void restart() {
        stop();
        if (!isEnabled()) {
            return;
        }

        long period = Math.max(20L, plugin.getUpdateCheckIntervalTicks());
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkSafely, 40L, period);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::checkSafely);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        pendingDownloads.clear();
        currentState = null;
    }

    public void checkSafely() {
        try {
            checkNow();
        } catch (Exception exception) {
            plugin.getLogger().warning("Update check failed: " + exception.getMessage());
        }
    }

    public synchronized void checkNow() throws IOException, InterruptedException {
        if (!isEnabled()) {
            return;
        }

        VersionNumber currentVersion = VersionNumber.parse(plugin.getDescription().getVersion()).orElse(null);
        if (currentVersion == null) {
            plugin.getLogger().warning("Could not parse the current plugin version: " + plugin.getDescription().getVersion());
            return;
        }

        UpdateCandidate github = fetchGitHubUpdate(currentVersion);
        UpdateCandidate modrinth = fetchModrinthUpdate(currentVersion);
        UpdateState discovered = new UpdateState(currentVersion, github, modrinth);

        if (!discovered.isActive()) {
            currentState = null;
            return;
        }

        UpdateState existing = currentState;
        if (existing == null || !Objects.equals(existing.signature(), discovered.signature())) {
            discovered = discovered.withFreshNotificationState();
            currentState = discovered;
            broadcastToEligiblePlayers(discovered);
            return;
        }

        currentState = existing;
        broadcastToEligiblePlayers(existing);
    }

    public void notifyPlayerIfPending(Player player) {
        if (!isEnabled() || player == null || !plugin.shouldReceiveUpdateNotifications(player)) {
            return;
        }

        UpdateState state = currentState;
        if (state == null || !state.isActive()) {
            return;
        }

        player.sendMessage(buildMessage(state));
    }

    public void requestDownloadConfirmation(Player player, String sourceKey) {
        if (player == null || !plugin.shouldReceiveUpdateNotifications(player)) {
            return;
        }

        UpdateState state = currentState;
        if (state == null || !state.isActive()) {
            player.sendMessage(Component.text("No update is currently available.", NamedTextColor.GRAY));
            return;
        }

        UpdateCandidate candidate = state.selectCandidate(UpdateSource.fromKey(sourceKey));
        if (candidate == null) {
            player.sendMessage(Component.text("No downloadable update was found.", NamedTextColor.GRAY));
            return;
        }

        pendingDownloads.put(player.getUniqueId(), candidate.sourceKey());
        player.sendMessage(Component.text()
                .append(Component.text("Type ", NamedTextColor.GRAY))
                .append(Component.text("update", NamedTextColor.WHITE))
                .append(Component.text(" in chat to confirm the download.", NamedTextColor.GRAY))
                .build());
    }

    public boolean confirmPendingDownload(Player player, String message) {
        if (player == null || message == null || !message.trim().equalsIgnoreCase(UPDATE_CONFIRMATION)) {
            return false;
        }

        String sourceKey = pendingDownloads.remove(player.getUniqueId());
        if (sourceKey == null) {
            return false;
        }

        downloadUpdateAsync(player, sourceKey);
        return true;
    }

    public void clearPendingDownload(UUID playerId) {
        if (playerId != null) {
            pendingDownloads.remove(playerId);
        }
    }

    public void downloadUpdateAsync(CommandSender sender, String sourceKey) {
        if (sender == null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UpdateState state = currentState;
                if (state == null || !state.isActive()) {
                    reply(sender, Component.text("No update is currently available.", NamedTextColor.GRAY));
                    return;
                }

                UpdateCandidate candidate = state.selectCandidate(UpdateSource.fromKey(sourceKey));
                if (candidate == null) {
                    reply(sender, Component.text("No downloadable update was found.", NamedTextColor.GRAY));
                    return;
                }

                Path savedFile = downloadToUpdateFolder(candidate);
                reply(sender, Component.text()
                        .append(Component.text("Downloaded ", NamedTextColor.GRAY))
                        .append(Component.text(candidate.fileName(), NamedTextColor.WHITE))
                        .append(Component.text(" to ", NamedTextColor.GRAY))
                        .append(Component.text(savedFile.toString(), NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text("Restart the server to apply the update.", NamedTextColor.GRAY))
                        .build());
            } catch (Exception exception) {
                plugin.getLogger().warning("Update download failed: " + exception.getMessage());
                reply(sender, Component.text("Failed to download the update: " + exception.getMessage(), NamedTextColor.GRAY));
            }
        });
    }

    private void broadcastToEligiblePlayers(UpdateState state) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (currentState != state) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!plugin.shouldReceiveUpdateNotifications(player)) {
                    continue;
                }
                if (state.markNotified(player.getUniqueId())) {
                    player.sendMessage(buildMessage(state));
                }
            }
        });
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("update-check.enabled", true);
    }

    private UpdateCandidate fetchGitHubUpdate(VersionNumber currentVersion) throws IOException, InterruptedException {
        URI uri = URI.create("https://api.github.com/repos/" + GITHUB_REPOSITORY + "/releases?per_page=100");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(7))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "AddHeads/" + plugin.getDescription().getVersion())
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            plugin.getLogger().warning("GitHub release check failed with HTTP " + response.statusCode());
            return null;
        }

        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonArray()) {
            return null;
        }

        JsonArray releases = parsed.getAsJsonArray();
        UpdateCandidate best = null;
        for (JsonElement element : releases) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject release = element.getAsJsonObject();
            if (release.has("draft") && release.get("draft").getAsBoolean()) {
                continue;
            }
            if (release.has("prerelease") && release.get("prerelease").getAsBoolean()) {
                continue;
            }

            Optional<VersionNumber> candidateVersion = bestVersion(
                    stringValue(release, "name"),
                    stringValue(release, "tag_name")
            );
            if (candidateVersion.isEmpty() || candidateVersion.get().compareTo(currentVersion) <= 0) {
                continue;
            }

            String pageUrl = stringValue(release, "html_url");
            if (pageUrl.isBlank()) {
                String tagName = stringValue(release, "tag_name");
                if (!tagName.isBlank()) {
                    pageUrl = "https://github.com/" + GITHUB_REPOSITORY + "/releases/tag/" + tagName;
                } else {
                    pageUrl = GITHUB_LATEST_URL;
                }
            }

            JsonObject asset = selectReleaseAsset(release);
            if (asset == null) {
                continue;
            }
            String downloadUrl = stringValue(asset, "browser_download_url");
            String fileName = stringValue(asset, "name");
            if (downloadUrl.isBlank() || fileName.isBlank()) {
                continue;
            }

            UpdateCandidate candidate = new UpdateCandidate("github", "GitHub", candidateVersion.get(), pageUrl, downloadUrl, fileName);
            if (best == null || candidate.version().compareTo(best.version()) > 0) {
                best = candidate;
            }
        }

        return best;
    }

    private UpdateCandidate fetchModrinthUpdate(VersionNumber currentVersion) throws IOException, InterruptedException {
        URI uri = URI.create("https://api.modrinth.com/v2/project/" + URLEncoder.encode(MODRINTH_PROJECT, StandardCharsets.UTF_8) + "/version");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(7))
                .header("Accept", "application/json")
                .header("User-Agent", "AddHeads/" + plugin.getDescription().getVersion())
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            plugin.getLogger().warning("Modrinth version check failed with HTTP " + response.statusCode());
            return null;
        }

        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonArray()) {
            return null;
        }

        JsonArray versions = parsed.getAsJsonArray();
        UpdateCandidate best = null;
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject version = element.getAsJsonObject();
            String status = stringValue(version, "status").toLowerCase(Locale.ROOT);
            String versionType = stringValue(version, "version_type").toLowerCase(Locale.ROOT);
            if (!"listed".equals(status) || !"release".equals(versionType)) {
                continue;
            }

            Optional<VersionNumber> candidateVersion = bestVersion(
                    stringValue(version, "version_number"),
                    stringValue(version, "name")
            );
            if (candidateVersion.isEmpty() || candidateVersion.get().compareTo(currentVersion) <= 0) {
                continue;
            }

            JsonObject file = selectModrinthFile(version);
            if (file == null) {
                continue;
            }
            String downloadUrl = stringValue(file, "url");
            String fileName = stringValue(file, "filename");
            if (downloadUrl.isBlank() || fileName.isBlank()) {
                continue;
            }

            String versionNumber = stringValue(version, "version_number");
            String pageUrl = "https://modrinth.com/plugin/" + MODRINTH_PROJECT + "/version/" + versionNumber;
            if (versionNumber.isBlank()) {
                pageUrl = MODRINTH_LATEST_URL;
            }

            UpdateCandidate candidate = new UpdateCandidate(
                    "modrinth",
                    "Modrinth",
                    candidateVersion.get(),
                    pageUrl,
                    downloadUrl,
                    fileName
            );
            if (best == null || candidate.version().compareTo(best.version()) > 0) {
                best = candidate;
            }
        }

        return best;
    }

    private Optional<VersionNumber> bestVersion(String... candidates) {
        List<VersionNumber> versions = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }

            Matcher matcher = VERSION_PATTERN.matcher(candidate);
            while (matcher.find()) {
                VersionNumber.parse(candidate.substring(matcher.start(), matcher.end())).ifPresent(versions::add);
            }

            VersionNumber.parse(candidate).ifPresent(versions::add);
        }
        return versions.stream()
                .max(Comparator.comparingInt(VersionNumber::partCount).thenComparing(Comparator.naturalOrder()));
    }

    private String stringValue(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private JsonObject selectReleaseAsset(JsonObject release) {
        if (release == null || !release.has("assets") || !release.get("assets").isJsonArray()) {
            return null;
        }

        JsonArray assets = release.getAsJsonArray("assets");
        for (JsonElement element : assets) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject asset = element.getAsJsonObject();
            String name = stringValue(asset, "name");
            if (name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return asset;
            }
        }
        return null;
    }

    private JsonObject selectModrinthFile(JsonObject version) {
        if (version == null || !version.has("files") || !version.get("files").isJsonArray()) {
            return null;
        }

        JsonArray files = version.getAsJsonArray("files");
        for (JsonElement element : files) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject file = element.getAsJsonObject();
            String filename = stringValue(file, "filename");
            boolean primary = file.has("primary") && file.get("primary").getAsBoolean();
            boolean jar = filename.toLowerCase(Locale.ROOT).endsWith(".jar");
            if ((primary || jar) && !filename.isBlank()) {
                return file;
            }
        }
        return null;
    }

    private Path downloadToUpdateFolder(UpdateCandidate candidate) throws IOException, InterruptedException {
        File parent = plugin.getDataFolder().getParentFile();
        File updateFolder = new File(parent != null ? parent : plugin.getDataFolder(), "update");
        Files.createDirectories(updateFolder.toPath());

        String fileName = sanitizeFileName(candidate.fileName());
        if (fileName.isBlank()) {
            fileName = "AddHeads-" + candidate.version().display() + ".jar";
        }

        Path target = updateFolder.toPath().resolve(fileName);
        HttpRequest request = HttpRequest.newBuilder(URI.create(candidate.downloadUrl()))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "AddHeads/" + plugin.getDescription().getVersion())
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode());
        }

        Path temp = updateFolder.toPath().resolve(fileName + ".part");
        try (InputStream inputStream = response.body(); OutputStream outputStream = Files.newOutputStream(temp)) {
            inputStream.transferTo(outputStream);
        }

        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return target;
    }

    private String sanitizeFileName(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private void reply(CommandSender sender, Component message) {
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plain));
    }

    private Component buildMessage(UpdateState state) {
        String current = state.currentVersion().display();
        Component header = Component.text()
                .append(Component.text("[AddHeads] ", NamedTextColor.AQUA))
                .append(Component.text("A new version is available", NamedTextColor.WHITE))
                .append(Component.text(" (current ", NamedTextColor.GRAY))
                .append(Component.text(current, NamedTextColor.WHITE))
                .append(Component.text(")", NamedTextColor.GRAY))
                .build();

        Component body = Component.empty();
        if (state.github() != null) {
            body = body.append(Component.newline())
                    .append(Component.text("GitHub: ", NamedTextColor.GRAY))
                    .append(Component.text(state.github().version().display(), NamedTextColor.WHITE));
        }
        if (state.modrinth() != null) {
            body = body.append(Component.newline())
                    .append(Component.text("Modrinth: ", NamedTextColor.GRAY))
                    .append(Component.text(state.modrinth().version().display(), NamedTextColor.WHITE));
        }

        Component buttons = Component.text()
                .append(button("Download latest", "/hd update latest"))
                .append(Component.space())
                .append(button("Open GitHub", state.github() != null ? state.github().url() : GITHUB_LATEST_URL))
                .append(Component.space())
                .append(Component.text("|", NamedTextColor.DARK_GRAY))
                .append(Component.space())
                .append(button("Open Modrinth", state.modrinth() != null ? state.modrinth().url() : MODRINTH_LATEST_URL))
                .build();

        return Component.text()
                .append(header)
                .append(body)
                .append(Component.newline())
                .append(buttons)
                .build();
    }

    private Component button(String label, String url) {
        return Component.text("[" + label + "]", NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(url.startsWith("/") ? ClickEvent.runCommand(url) : ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url.startsWith("/") ? "Download and queue the update" : "Open the release page", NamedTextColor.WHITE)));
    }

    private record UpdateCandidate(String sourceKey, String sourceName, VersionNumber version, String url, String downloadUrl, String fileName) {
    }

    private static final class UpdateState {
        private final VersionNumber currentVersion;
        private final UpdateCandidate github;
        private final UpdateCandidate modrinth;
        private final Set<UUID> notifiedPlayers = ConcurrentHashMap.newKeySet();

        private UpdateState(VersionNumber currentVersion, UpdateCandidate github, UpdateCandidate modrinth) {
            this.currentVersion = currentVersion;
            this.github = github;
            this.modrinth = modrinth;
        }

        private UpdateState withFreshNotificationState() {
            notifiedPlayers.clear();
            return this;
        }

        private boolean isActive() {
            return github != null || modrinth != null;
        }

        private UpdateCandidate selectCandidate(UpdateSource source) {
            if (source == null || source == UpdateSource.LATEST) {
                if (github != null && modrinth != null) {
                    return github.version().compareTo(modrinth.version()) >= 0 ? github : modrinth;
                }
                return github != null ? github : modrinth;
            }
            return switch (source) {
                case GITHUB -> github;
                case MODRINTH -> modrinth;
                case LATEST -> github != null ? github : modrinth;
            };
        }

        private boolean markNotified(UUID playerId) {
            return playerId != null && notifiedPlayers.add(playerId);
        }

        private String signature() {
            String githubSignature = github == null ? "-" : github.sourceKey() + "@" + github.version().display() + "@" + github.url() + "@" + github.downloadUrl();
            String modrinthSignature = modrinth == null ? "-" : modrinth.sourceKey() + "@" + modrinth.version().display() + "@" + modrinth.url() + "@" + modrinth.downloadUrl();
            return githubSignature + "|" + modrinthSignature;
        }

        private VersionNumber currentVersion() {
            return currentVersion;
        }

        private UpdateCandidate github() {
            return github;
        }

        private UpdateCandidate modrinth() {
            return modrinth;
        }
    }

    private enum UpdateSource {
        LATEST,
        GITHUB,
        MODRINTH;

        private static UpdateSource fromKey(String value) {
            if (value == null || value.isBlank()) {
                return LATEST;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "github" -> GITHUB;
                case "modrinth" -> MODRINTH;
                default -> LATEST;
            };
        }
    }

    private static final class VersionNumber implements Comparable<VersionNumber> {
        private final List<Integer> parts;
        private final boolean preRelease;
        private final String display;

        private VersionNumber(List<Integer> parts, boolean preRelease, String display) {
            this.parts = parts;
            this.preRelease = preRelease;
            this.display = display;
        }

        private static Optional<VersionNumber> parse(String value) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }

            Matcher matcher = VERSION_PATTERN.matcher(value);
            if (!matcher.find()) {
                return Optional.empty();
            }

            String raw = matcher.group(1);
            boolean preRelease = matcher.end(1) < value.length() && value.charAt(matcher.end(1)) == '-';
            String numeric = raw;

            String[] tokens = numeric.split("\\.");
            List<Integer> parts = new ArrayList<>(tokens.length);
            for (String token : tokens) {
                if (token.isBlank()) {
                    continue;
                }
                try {
                    parts.add(Integer.parseInt(token));
                } catch (NumberFormatException exception) {
                    return Optional.empty();
                }
            }

            if (parts.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new VersionNumber(List.copyOf(parts), preRelease, raw));
        }

        private String display() {
            return display;
        }

        private int partCount() {
            return parts.size();
        }

        @Override
        public int compareTo(VersionNumber other) {
            int max = Math.max(parts.size(), other.parts.size());
            for (int i = 0; i < max; i++) {
                int left = i < parts.size() ? parts.get(i) : 0;
                int right = i < other.parts.size() ? other.parts.get(i) : 0;
                int comparison = Integer.compare(left, right);
                if (comparison != 0) {
                    return comparison;
                }
            }

            if (preRelease != other.preRelease) {
                return preRelease ? -1 : 1;
            }

            return 0;
        }
    }
}
