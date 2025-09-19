package com.robby.crawler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Crawler {
    private static final String USER_AGENT = "RobbyCrawlerBot/1.0 (+your-email@example.com)";
    private static final int TIMEOUT_MS = 15_000;
    private static final long RATE_LIMIT_MS = 1000L; // 1s between fetches
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Domain crawling variables
    private static final int MAX_DEPTH = 2;
    private static final int MAX_PAGES = 50;
    private static final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private static final BlockingQueue<String> urlQueue = new LinkedBlockingQueue<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static int totalCollected = 0;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: gradle run --args='https://example.com' [--depth 2] [--max-pages 50]");
            System.out.println("Or for single page: gradle run --args='https://example.com/article'");
            return;
        }

        String startUrl = args[0];
        int depth = MAX_DEPTH;
        int maxPages = MAX_PAGES;

        // Parse command line arguments
        for (int i = 1; i < args.length; i++) {
            if ("--depth".equals(args[i]) && i + 1 < args.length) {
                depth = Integer.parseInt(args[i + 1]);
            }
            if ("--max-pages".equals(args[i]) && i + 1 < args.length) {
                maxPages = Integer.parseInt(args[i + 1]);
            }
        }

        // Check if it's a single page or domain crawl
        boolean isSinglePage = args.length == 1 && !args[0].startsWith("--");

        if (isSinglePage) {
            crawlSinglePage(startUrl);
        } else {
            crawlDomain(startUrl, depth, maxPages);
        }
    }

    private static void crawlSinglePage(String url) throws Exception {
        Path outDir = Paths.get("data/articles");
        Files.createDirectories(outDir);

        System.out.printf("[%s] Fetching single page: %s%n", Instant.now().toString(), url);

        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get();

        // Check if this looks like an article
        boolean isArticle = ContentExtractor.isArticlePage(doc);

        if (!isArticle) {
            System.out.println("Warning: This page doesn't appear to be a typical article page.");
            System.out.println("  Proceeding anyway since you specified a single URL...");
        }

        String title = ContentExtractor.extractTitle(doc);
        String author = ContentExtractor.extractAuthor(doc);
        String published = ContentExtractor.extractPublishedDate(doc);
        String content = ContentExtractor.extractMainContent(doc);

        Article art = new Article(url, title, author, published, content);

        String safeName = sanitizeFilename(title.isBlank() ? url : title);
        Path outFile = outDir.resolve(safeName + ".json");
        Files.writeString(outFile, GSON.toJson(art), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

        System.out.println("Saved: " + outFile);
        System.out.printf("  Title: %s%n", title);
        System.out.printf("  Author: %s%n", author.isEmpty() ? "Not specified" : author);
        System.out.printf("  Published: %s%n", published.isEmpty() ? "Not specified" : published);
        System.out.printf("  Content length: %d characters%n", content.length());
        System.out.println("Done.");
    }

    private static void crawlDomain(String startUrl, int maxDepth, int maxPages) throws Exception {
        Path outDir = Paths.get("data/articles");
        Files.createDirectories(outDir);

        // Start with the first URL
        urlQueue.add(startUrl);
        visitedUrls.add(normalizeUrl(startUrl));

        System.out.println("Starting to explore domain: " + startUrl);
        System.out.println("Depth: " + maxDepth + " levels, Max Pages: " + maxPages);

        // Create multiple worker threads
        List<Future<?>> futures = new ArrayList<>();
        int threadCount = Math.min(3, maxPages);
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> crawlPages(outDir, maxDepth, maxPages)));
        }

        // Wait for all workers to finish
        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        System.out.println("All done! Collected " + totalCollected + " pages from the domain.");
    }

    private static void crawlPages(Path outDir, int maxDepth, int maxPages) {
        while (totalCollected < maxPages && !urlQueue.isEmpty()) {
            String url = urlQueue.poll();
            if (url == null) continue;

            try {
                int currentDepth = getCurrentDepth(url);
                if (currentDepth > maxDepth) {
                    continue;
                }

                System.out.printf("[%s] Exploring (%d/%d): %s%n",
                        Instant.now().toString(), currentDepth, maxDepth, url);

                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .followRedirects(true)
                        .get();

                // Enhanced article detection with scoring
                boolean isArticle = ContentExtractor.isArticlePage(doc);

                if (isArticle) {
                    // Extract content
                    String title = ContentExtractor.extractTitle(doc);
                    String author = ContentExtractor.extractAuthor(doc);
                    String published = ContentExtractor.extractPublishedDate(doc);
                    String content = ContentExtractor.extractMainContent(doc);

                    // Only save if we have meaningful content
                    if (content.length() > 300 &&
                            !content.equals("Content extraction failed - may be a listing page or invalid article")) {

                        Article art = new Article(url, title, author, published, content);

                        String safeName = sanitizeFilename(title.isBlank() ? url : title);
                        Path outFile = outDir.resolve(safeName + ".json");
                        Files.writeString(outFile, GSON.toJson(art), StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE);

                        synchronized (Crawler.class) {
                            totalCollected++;
                        }

                        System.out.println("Saved article [" + totalCollected + "/" + maxPages + "]: " +
                                title.substring(0, Math.min(title.length(), 60)) +
                                (title.length() > 60 ? "..." : ""));
                    } else {
                        System.out.println("Skipping - insufficient content: " + url);
                    }
                } else {
                    System.out.println("Skipping - not detected as article page: " + url);

                    // Even if it's not an article, we might still want to find links for exploration
                    if (currentDepth < maxDepth && totalCollected < maxPages) {
                        findNewLinks(doc, url);
                    }
                }

                // Find more links to explore if we haven't reached limits
                if (isArticle && currentDepth < maxDepth && totalCollected < maxPages) {
                    findNewLinks(doc, url);
                }

            } catch (Exception e) {
                System.err.println("Failed to fetch " + url + " : " + e.getMessage());
            }

            try {
                Thread.sleep(RATE_LIMIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static void findNewLinks(Document doc, String currentUrl) {
        String currentDomain = getDomain(currentUrl);

        // 1. First, look for pagination links (next page)
        Elements paginationLinks = doc.select("link[rel=next], a.next, a[rel=next], .pagination a, .nav-links a");
        for (Element link : paginationLinks) {
            String href = link.attr("abs:href");
            if (isValidLink(currentUrl, href)) {
                addToQueue(href);
            }
        }

        // 2. Look for article links in common WordPress structures
        Elements articleLinks = doc.select(
                "article a, .post a, .entry-title a, h2 a, h3 a, .blog-post a, " +
                        ".article-list a, .posts-grid a, .archive-item a, " +
                        "a[href*='/article/']:not([href*='/article/page/']), " +
                        "a[href*='/blog/'], a[href*='/post/']"
        );

        for (Element link : articleLinks) {
            String href = link.attr("abs:href");
            String text = link.text().toLowerCase();
            String hrefLower = href.toLowerCase();

            // Filter for actual article links
            if (isValidArticleLink(currentUrl, href, text, hrefLower)) {
                addToQueue(href);
            }
        }

        // 3. Look for category/archive links
        Elements categoryLinks = doc.select(
                ".categories a, .archive-links a, .widget_categories a, " +
                        ".post-categories a, .meta-category a"
        );

        for (Element link : categoryLinks) {
            String href = link.attr("abs:href");
            if (isValidLink(currentUrl, href)) {
                addToQueue(href);
            }
        }
    }

    private static boolean isValidArticleLink(String currentUrl, String href, String linkText, String hrefLower) {
        if (!isValidLink(currentUrl, href)) return false;

        // Skip obvious non-article links
        if (hrefLower.contains("/category/") ||
                hrefLower.contains("/tag/") ||
                hrefLower.contains("/author/") ||
                hrefLower.contains("/page/") ||
                hrefLower.contains("/feed") ||
                hrefLower.contains("/comment")) {
            return false;
        }

        // Skip links that are too short
        if (href.length() < 20) return false;

        // Skip links that contain common non-article words
        String[] skipWords = {"category", "tag", "author", "page", "feed", "comment", "reply", "edit"};
        for (String word : skipWords) {
            if (hrefLower.contains(word)) return false;
        }

        return true;
    }

    private static boolean isValidLink(String currentUrl, String href) {
        if (href == null || href.isEmpty()) return false;

        return isSameDomain(currentUrl, href) &&
                !visitedUrls.contains(normalizeUrl(href)) &&
                isHtmlPage(href) &&
                !href.contains("#") &&
                !href.startsWith("javascript:") &&
                !href.contains("wp-login") &&
                !href.contains("wp-admin");
    }

    private static void addToQueue(String href) {
        String normalized = normalizeUrl(href);
        if (!visitedUrls.contains(normalized)) {
            urlQueue.add(href);
            visitedUrls.add(normalized);
            System.out.println("Found new page to explore: " + href);
        }
    }

    private static boolean isSameDomain(String currentUrl, String newUrl) {
        try {
            String currentDomain = getDomain(currentUrl);
            String newDomain = getDomain(newUrl);
            return currentDomain != null && currentDomain.equals(newDomain);
        } catch (Exception e) {
            return false;
        }
    }

    private static String getDomain(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeUrl(String url) {
        try {
            URI uri = new URI(url);
            String normalized = uri.normalize().toString();

            // Remove fragments
            if (normalized.contains("#")) {
                normalized = normalized.substring(0, normalized.indexOf('#'));
            }

            // Remove query parameters
            if (normalized.contains("?")) {
                normalized = normalized.substring(0, normalized.indexOf('?'));
            }

            // Remove trailing slashes
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }

            // Normalize www vs non-www
            normalized = normalized.replace("://www.", "://");

            return normalized.toLowerCase();
        } catch (Exception e) {
            return url.toLowerCase();
        }
    }

    private static boolean isHtmlPage(String url) {
        String lowerUrl = url.toLowerCase();
        return !lowerUrl.matches(".*\\.(pdf|jpg|jpeg|png|gif|css|js|zip|rar|tar|gz|mp3|mp4|avi|mov|doc|docx|xls|xlsx|ppt|pptx)$");
    }

    private static int getCurrentDepth(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path == null || path.equals("/") || path.isEmpty()) return 0;

            // Don't count pagination as depth increase
            if (path.matches(".*/page/\\d+/?$")) {
                path = path.replaceAll("/page/\\d+", "");
            }

            // Count meaningful path segments
            String[] segments = path.split("/");
            int depth = 0;
            for (String segment : segments) {
                if (!segment.isEmpty() &&
                        !segment.matches("\\d+") &&
                        !segment.equals("article") &&
                        !segment.equals("blog") &&
                        !segment.equals("category")) {
                    depth++;
                }
            }
            return depth;
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isAllowedByRobots(String url) {
        try {
            URI u = new URI(url);
            String robotsUrl = u.getScheme() + "://" + u.getHost() + "/robots.txt";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(robotsUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return true;

            String body = response.body().toLowerCase();
            if (body.contains("user-agent: *") && body.contains("disallow: /")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private static String sanitizeFilename(String in) {
        if (in == null || in.trim().isEmpty()) {
            return "article_" + System.currentTimeMillis();
        }

        String s = in.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (s.length() == 0) s = "article";
        if (s.length() > 120) s = s.substring(0, 120);

        return s.replaceAll("\\s+", "_") + "_" + System.currentTimeMillis();
    }
}