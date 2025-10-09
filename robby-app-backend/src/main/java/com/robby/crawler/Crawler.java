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
            // MODIFIED: Updated usage instructions
            System.out.println("Usage: gradle run --args='<start-url> [--depth D] [--max-pages M | --crawl-all]'");
            System.out.println("  Single page: gradle run --args='https://example.com/article'");
            System.out.println("  Limited domain crawl: gradle run --args='https://example.com --max-pages 50'");
            System.out.println("  Full domain crawl: gradle run --args='https://example.com --crawl-all'");
            return;
        }

        String startUrl = args[0];
        int depth = MAX_DEPTH;
        int maxPages = MAX_PAGES;
        // ADDED: Flag to indicate a full domain crawl
        boolean crawlAll = false;

        // Parse command line arguments
        for (int i = 1; i < args.length; i++) {
            if ("--depth".equals(args[i]) && i + 1 < args.length) {
                depth = Integer.parseInt(args[i + 1]);
                i++; // Skip next argument since we've consumed it
            }
            if ("--max-pages".equals(args[i]) && i + 1 < args.length) {
                maxPages = Integer.parseInt(args[i + 1]);
                i++; // Skip next argument
            }
            // ADDED: Check for the new --crawl-all flag
            if ("--crawl-all".equals(args[i])) {
                crawlAll = true;
            }
        }

        // ADDED: If --crawl-all is specified, set maxPages to effectively infinity.
        if (crawlAll) {
            maxPages = Integer.MAX_VALUE;
        }

        // Check if it's a single page or domain crawl
        // A single page crawl is now determined by having no other flags
        boolean isSinglePage = (args.length == 1 && !args[0].startsWith("--")) ||
                (args.length > 1 && !args[1].startsWith("--"));


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
        // MODIFIED: Display a different message for full domain crawls
        String maxPagesStr = (maxPages == Integer.MAX_VALUE) ? "ENTIRE DOMAIN" : String.valueOf(maxPages);
        System.out.println("Depth: " + maxDepth + " levels, Max Pages: " + maxPagesStr);


        // Create multiple worker threads
        List<Future<?>> futures = new ArrayList<>();
        int threadCount = 3; // Keep a fixed number of threads
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> crawlPages(outDir, maxDepth, maxPages)));
        }

        // Wait for all workers to finish
        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        System.out.println("All done! Collected " + totalCollected + " articles from the domain.");
    }

    private static void crawlPages(Path outDir, int maxDepth, int maxPages) {
        while (totalCollected < maxPages) {
            // MODIFIED: Poll with a timeout to allow threads to exit gracefully when the queue is empty
            String url = null;
            try {
                url = urlQueue.poll(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // If poll times out and queue is still empty, exit the loop
            if (url == null) {
                // Double-check if the queue is truly empty to handle race conditions
                if (urlQueue.isEmpty()) {
                    break;
                }
                continue;
            }

            try {
                int currentDepth = getCurrentDepth(url);
                if (currentDepth > maxDepth) {
                    continue;
                }

                System.out.printf("[%s] Exploring (Depth:%d): %s%n",
                        Instant.now().toString(), currentDepth, url);

                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .followRedirects(true)
                        .get();

                boolean isArticle = ContentExtractor.isArticlePage(doc);

                if (isArticle) {
                    // Extract content
                    String title = ContentExtractor.extractTitle(doc);
                    String author = ContentExtractor.extractAuthor(doc);
                    String published = ContentExtractor.extractPublishedDate(doc);
                    String content = ContentExtractor.extractMainContent(doc);

                    if (content.length() > 300 &&
                            !content.equals("Content extraction failed - may be a listing page or invalid article")) {

                        Article art = new Article(url, title, author, published, content);
                        String safeName = sanitizeFilename(title.isBlank() ? url : title);
                        Path outFile = outDir.resolve(safeName + ".json");
                        Files.writeString(outFile, GSON.toJson(art), StandardCharsets.UTF_8, StandardOpenOption.CREATE);

                        int currentCount;
                        synchronized (Crawler.class) {
                            totalCollected++;
                            currentCount = totalCollected;
                        }

                        // MODIFIED: Display progress differently for full domain crawl
                        String progress = (maxPages == Integer.MAX_VALUE) ?
                                String.valueOf(currentCount) :
                                currentCount + "/" + maxPages;

                        System.out.println("Saved article [" + progress + "]: " +
                                title.substring(0, Math.min(title.length(), 60)) +
                                (title.length() > 60 ? "..." : ""));
                    } else {
                        System.out.println("Skipping - insufficient content: " + url);
                    }
                }

                // Find more links to explore if we haven't reached depth limit
                if (currentDepth < maxDepth) {
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
        if (currentDomain == null) return;

        // Select all links on the page
        Elements allLinks = doc.select("a[href]");

        for (Element link : allLinks) {
            String href = link.attr("abs:href");
            if (isValidLink(currentUrl, href)) {
                addToQueue(href);
            }
        }
    }

    private static boolean isValidLink(String currentUrl, String href) {
        if (href == null || href.isEmpty()) return false;

        // Normalize before checking domain, as it can fail on malformed URLs
        String normalizedHref = normalizeUrl(href);

        return isSameDomain(currentUrl, normalizedHref) &&
                !visitedUrls.contains(normalizedHref) &&
                isHtmlPage(href) &&
                !href.contains("#") &&
                !href.startsWith("javascript:") &&
                !href.contains("wp-login") &&
                !href.contains("wp-admin");
    }


    private static void addToQueue(String href) {
        String normalized = normalizeUrl(href);
        // Use a synchronized block to ensure thread-safe addition
        synchronized (visitedUrls) {
            if (!visitedUrls.contains(normalized)) {
                urlQueue.add(href);
                visitedUrls.add(normalized);
                System.out.println("  -> Found new link: " + href);
            }
        }
    }


    // The rest of the methods (isSameDomain, getDomain, normalizeUrl, etc.) remain the same...

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
            String host = uri.getHost();
            if (host == null) return null;
            // Strip "www." from the beginning of the host
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme().toLowerCase();
            String host = uri.getHost().toLowerCase();
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            String path = uri.normalize().getPath();

            // Remove trailing slashes from path
            if (path != null && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            return scheme + "://" + host + (path == null ? "" : path);
        } catch (Exception e) {
            // Basic fallback for malformed URLs
            String cleaned = url.split("#")[0].split("\\?")[0];
            if (cleaned.endsWith("/")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            return cleaned.toLowerCase();
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

            // Count non-empty path segments
            return (int) Arrays.stream(path.split("/"))
                    .filter(s -> !s.isEmpty())
                    .count();
        } catch (Exception e) {
            return 999; // Assign high depth to invalid URLs to prevent crawling them
        }
    }

    private static String sanitizeFilename(String in) {
        if (in == null || in.trim().isEmpty()) {
            return "article_" + System.currentTimeMillis();
        }

        String s = in.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (s.length() == 0) s = "article";
        if (s.length() > 120) s = s.substring(0, 120);

        // Append a unique hash to prevent file name collisions
        long hash = in.hashCode() + System.currentTimeMillis();
        return s.replaceAll("\\s+", "_") + "_" + Long.toHexString(hash);
    }
}