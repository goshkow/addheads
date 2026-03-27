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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    private static final Pattern VERSION_PATTERN = Pattern.compile("(?i)(?:\\bv)?(\\d+(?:\\.\\d+)+(?:[-+][0-9A-Za-z.-]+)?)");

    private final AddHeads plugin;
    private final HttpClient httpClient;
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

        if (state.markNotified(player.getUniqueId())) {
            player.sendMessage(buildMessage(state));
        }
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
        String repository = normalizeRepository(plugin.getConfig().getString("update-check.github.repository", "goshkow/addheads"));
        if (repository.isBlank()) {
            return null;
        }

        URI uri = URI.create("https://api.github.com/repos/" + repository + "/releases?per_page=100");
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

            String url = stringValue(release, "html_url");
            if (url.isBlank()) {
                url = "https://github.com/" + repository + "/releases/latest";
            }

            UpdateCandidate candidate = new UpdateCandidate("GitHub", candidateVersion.get(), url);
            if (best == null || candidate.version().compareTo(best.version()) > 0) {
                best = candidate;
            }
        }

        return best;
    }

    private UpdateCandidate fetchModrinthUpdate(VersionNumber currentVersion) throws IOException, InterruptedException {
        String project = normalizeModrinthProject(plugin.getConfig().getString("update-check.modrinth.project", "addheads"));
        if (project == null || project.isBlank()) {
            return null;
        }

        URI uri = URI.create("https://api.modrinth.com/v2/project/" + URLEncoder.encode(project, StandardCharsets.UTF_8) + "/version");
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

            UpdateCandidate candidate = new UpdateCandidate(
                    "Modrinth",
                    candidateVersion.get(),
                    plugin.getConfig().getString("update-check.modrinth.url", "https://modrinth.com/plugin/addheads/versions")
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
            VersionNumber.parse(candidate).ifPresent(versions::add);
        }
        return versions.stream().max(Comparator.naturalOrder());
    }

    private String normalizeRepository(String repository) {
        if (repository == null) {
            return "";
        }
        String value = repository.trim();
        for (String prefix : List.of("https://github.com/", "http://github.com/", "github.com/")) {
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length());
            }
        }
        int releasesIndex = value.indexOf("/releases/");
        if (releasesIndex >= 0) {
            value = value.substring(0, releasesIndex);
        }
        String[] segments = value.split("/");
        if (segments.length >= 2) {
            value = segments[0] + "/" + segments[1];
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeModrinthProject(String project) {
        if (project == null) {
            return "";
        }

        String value = project.trim();
        for (String prefix : List.of("https://modrinth.com/plugin/", "http://modrinth.com/plugin/", "modrinth.com/plugin/")) {
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length());
            }
        }
        int slashIndex = value.indexOf('/');
        if (slashIndex >= 0) {
            value = value.substring(0, slashIndex);
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String stringValue(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
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
                .append(button("Open GitHub", state.github() != null ? state.github().url() : plugin.getConfig().getString("update-check.github.url", "https://github.com/goshkow/addheads/releases/latest")))
                .append(Component.space())
                .append(Component.text("|", NamedTextColor.DARK_GRAY))
                .append(Component.space())
                .append(button("Open Modrinth", state.modrinth() != null ? state.modrinth().url() : plugin.getConfig().getString("update-check.modrinth.url", "https://modrinth.com/plugin/addheads/versions")))
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
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text("Open the release page", NamedTextColor.WHITE)));
    }

    private record UpdateCandidate(String sourceName, VersionNumber version, String url) {
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

        private boolean markNotified(UUID playerId) {
            return playerId != null && notifiedPlayers.add(playerId);
        }

        private String signature() {
            String githubSignature = github == null ? "-" : github.version().display() + "@" + github.url();
            String modrinthSignature = modrinth == null ? "-" : modrinth.version().display() + "@" + modrinth.url();
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
            String numeric = raw;
            boolean preRelease = false;
            int dash = raw.indexOf('-');
            int plus = raw.indexOf('+');
            int cutIndex = dash >= 0 && plus >= 0 ? Math.min(dash, plus) : Math.max(dash, plus);
            if (cutIndex >= 0) {
                numeric = raw.substring(0, cutIndex);
                preRelease = raw.charAt(cutIndex) == '-';
            }

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
