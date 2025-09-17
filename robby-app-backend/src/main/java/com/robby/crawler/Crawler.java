package com.robby.crawler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class Crawler {
    private static final String USER_AGENT = "RobbyCrawlerBot/1.0 (+your-email@example.com)";
    private static final int TIMEOUT_MS = 15_000;
    private static final long RATE_LIMIT_MS = 1000L; // 1s between fetches
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: gradle run --args='https://example.com/article' OR --args='--file seeds.txt'");
            return;
        }

        List<String> urls;
        if ("--file".equals(args[0]) && args.length > 1) {
            urls = Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8)
                    .stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        } else {
            urls = List.of(args);
        }

        Path outDir = Paths.get("data/articles");
        Files.createDirectories(outDir);

        for (String url : urls) {
            try {
                System.out.printf("[%s] Fetching %s%n", Instant.now().toString(), url);

                // OPTIONAL: robots.txt check (very simple) - skip if disallowed
//                if (!isAllowedByRobots(url)) {
//                    System.out.println("Skipping due to robots.txt: " + url);
//                    continue;
//                }

                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .followRedirects(true)
                        .get();

                String title = ContentExtractor.extractTitle(doc);
                String author = ContentExtractor.extractAuthor(doc);
                String published = ContentExtractor.extractPublishedDate(doc);
                String content = ContentExtractor.extractMainContent(doc);

                Article art = new Article(url, title, author, published, content);

                String safeName = sanitizeFilename(title.isBlank() ? url : title);
                Path outFile = outDir.resolve(safeName + ".json");
                Files.writeString(outFile, GSON.toJson(art), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

                System.out.println("Saved: " + outFile);
            } catch (Exception e) {
                System.err.println("Failed to fetch " + url + " : " + e.getMessage());
            }
            Thread.sleep(RATE_LIMIT_MS);
        }

        System.out.println("Done.");
    }

    private static boolean isAllowedByRobots(String url) {
        // Very basic robots.txt check for root domain disallows for User-agent: *
        // This is intentionally simple; for production use a robust parser (crawler-commons).
        try {
            URI u = new URI(url);
            String robotsUrl = u.getScheme() + "://" + u.getHost() + "/robots.txt";
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(robotsUrl)).GET().build();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return true;
            String body = resp.body().toLowerCase();
            // crude check: if "disallow: /" present, skip
            if (body.contains("disallow: /")) return false;
            return true;
        } catch (Exception e) {
            // if anything goes wrong, be permissive (or conservative - your choice)
            return true;
        }
    }

    private static String sanitizeFilename(String in) {
        String s = in.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        if (s.length() == 0) s = "article";
        if (s.length() > 120) s = s.substring(0, 120);
        // add timestamp to avoid collisions
        return s.replaceAll("\\s+", "_") + "_" + System.currentTimeMillis();
    }
}
