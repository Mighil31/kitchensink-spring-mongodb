package io.github.mighil31.kitchensink.perf;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates members before a simulation starts (outside the measured window) and returns their ids.
 * The new app returns the id in a Location header; legacy doesn't, so ids are then looked up by email.
 */
public final class SeedMembers {

    private static final Pattern MEMBER = Pattern.compile("\\{[^{}]*}");
    private static final Pattern EMAIL = Pattern.compile("\"email\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"?([^\",}]+)\"?");

    private SeedMembers() {}

    public static List<String> create(int count) {
        HttpClient client = HttpClient.newHttpClient();
        List<String> emails = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                String email = Members.newEmail();
                HttpResponse<String> response = client.send(
                        request("/members")
                                .POST(HttpRequest.BodyPublishers.ofString(Members.body(email)))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200 && response.statusCode() != 201) {
                    throw new IllegalStateException("Seeding failed: " + response.statusCode() + " " + response.body());
                }
                emails.add(email);
                response.headers()
                        .firstValue("Location")
                        .ifPresent(location -> ids.add(location.substring(location.lastIndexOf('/') + 1)));
            }
            if (ids.size() == count) {
                return ids;
            }
            return idsByEmail(client, emails);
        } catch (IOException e) {
            throw new IllegalStateException("Seeding failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Seeding interrupted", e);
        }
    }

    private static List<String> idsByEmail(HttpClient client, List<String> emails)
            throws IOException, InterruptedException {
        String list = client.send(request("/members").GET().build(), HttpResponse.BodyHandlers.ofString())
                .body();
        Map<String, String> idByEmail = new HashMap<>();
        Matcher member = MEMBER.matcher(list);
        while (member.find()) {
            Matcher email = EMAIL.matcher(member.group());
            Matcher id = ID.matcher(member.group());
            if (email.find() && id.find()) {
                idByEmail.put(email.group(1), id.group(1));
            }
        }
        List<String> ids = emails.stream().map(idByEmail::get).toList();
        if (ids.contains(null)) {
            throw new IllegalStateException("Some seeded members were not found in GET /members");
        }
        return ids;
    }

    private static HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(PerfConfig.baseUrl() + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        PerfConfig.basicAuthHeader().ifPresent(h -> builder.header("Authorization", h));
        return builder;
    }
}
