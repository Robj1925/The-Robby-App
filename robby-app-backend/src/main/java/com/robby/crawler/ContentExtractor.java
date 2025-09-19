package com.robby.crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class ContentExtractor {

    private static final List<String> ARTICLE_SELECTORS = Arrays.asList(
            "article",
            "div[itemprop=articleBody]",
            "div[itemprop=articleBody] > div",
            "div[class*=article]",
            "div[class*=post]",
            "section[class*=article]",
            "div[id*=article]",
            "div[class*=content]",
            "main",
            // WordPress specific:
            ".entry-content",
            ".post-content",
            ".article-content",
            ".content-area",
            ".main-content",
            ".blog-single",
            ".single-post",
            // Ben Greenfield specific:
            ".post-single-content",
            ".article-body",
            ".post-body",
            ".blog-post-loop"
    );

    // Patterns that indicate listing/archive pages
    private static final List<Pattern> LISTING_PATTERNS = Arrays.asList(
            Pattern.compile("(?i).*/category/.*"),
            Pattern.compile("(?i).*/tag/.*"),
            Pattern.compile("(?i).*/author/.*"),
            Pattern.compile("(?i).*/page/\\d+.*"),
            Pattern.compile("(?i).*/\\d{4}/\\d{2}/?$"), // Year/month archives
            Pattern.compile("(?i).*/archive.*"),
            Pattern.compile("(?i).*/(all-posts|blog)/?$")
    );

    public static String extractTitle(Document doc) {
        // Try Open Graph title first
        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        if (ogTitle != null) {
            String title = ogTitle.attr("content");
            if (!title.isBlank()) return title.trim();
        }

        // Try Twitter title
        Element twitterTitle = doc.selectFirst("meta[name=twitter:title]");
        if (twitterTitle != null) {
            String title = twitterTitle.attr("content");
            if (!title.isBlank()) return title.trim();
        }

        // Try h1 with common article title classes
        Element titleH1 = doc.selectFirst("h1.entry-title, h1.post-title, h1.article-title, h1.title");
        if (titleH1 != null) {
            return titleH1.text().trim();
        }

        // Fallback to document title
        String title = doc.title();
        if (title != null && !title.isBlank()) {
            // Clean up title (remove site name if present)
            title = title.replace(" - Ben Greenfield Life", "").replace(" | Ben Greenfield Fitness", "");
            return title.trim();
        }

        return "Untitled";
    }

    public static String extractAuthor(Document doc) {
        // Meta tags
        Element metaAuthor = doc.selectFirst("meta[name=author], meta[property=article:author]");
        if (metaAuthor != null) {
            String author = metaAuthor.attr("content");
            if (!author.isBlank()) return author.trim();
        }

        // WordPress author links
        Element authorLink = doc.selectFirst(".author a, .byline a, .post-author a, [rel=author]");
        if (authorLink != null) {
            return authorLink.text().trim();
        }

        // Author divs/spans
        Element authorDiv = doc.selectFirst(".author, .byline, .post-author, .article-author");
        if (authorDiv != null) {
            return authorDiv.text().replace("By", "").trim();
        }

        return "";
    }

    public static String extractPublishedDate(Document doc) {
        // Meta tags
        Element metaDate = doc.selectFirst("meta[property=article:published_time], meta[name=date]");
        if (metaDate != null) {
            String date = metaDate.attr("content");
            if (!date.isBlank()) return date.trim();
        }

        // Time element with datetime
        Element timeDate = doc.selectFirst("time[datetime]");
        if (timeDate != null) {
            String date = timeDate.attr("datetime");
            if (!date.isBlank()) return date.trim();
        }

        // Published date in content
        Element dateElement = doc.selectFirst(".published, .post-date, .entry-date, .article-date");
        if (dateElement != null) {
            return dateElement.text().trim();
        }

        return "";
    }

    public static String extractMainContent(Document doc) {
        // First, remove all unwanted elements from the entire document
        doc.select("script, style, noscript, svg, iframe, footer, header, nav, form, aside, " +
                ".comments, .related-posts, .newsletter, .social-share, .advertisement, .ads, " +
                ".widget, .sidebar, .menu, .navigation, .pagination").remove();

        // Try specific content selectors for WordPress articles
        for (String selector : ARTICLE_SELECTORS) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                Element contentElement = elements.first();

                // Clean up the content element
                contentElement.select("script, style, .advertisement, .ads, .social-share, " +
                        ".comments, .related-posts, .newsletter, .widget").remove();

                String content = contentElement.text().trim();

                // Only return if we have substantial content
                if (content.length() > 500) {
                    return cleanContent(content);
                }
            }
        }

        // Fallback: try to find the main content area
        Element mainContent = doc.selectFirst("main, #main, #content, .main, .content");
        if (mainContent != null) {
            mainContent.select("script, style, .advertisement, .ads, .social-share").remove();
            String content = mainContent.text().trim();
            if (content.length() > 500) {
                return cleanContent(content);
            }
        }

        // Last resort: find paragraphs and combine them
        StringBuilder contentBuilder = new StringBuilder();
        Elements paragraphs = doc.select("p");

        for (Element p : paragraphs) {
            String text = p.text().trim();
            // Skip short paragraphs (likely navigation or ads)
            if (text.length() > 50 &&
                    !text.matches("(?i).*(read more|continue reading|share this|comment|advertisement).*")) {
                contentBuilder.append(text).append("\n\n");
            }
        }

        String content = contentBuilder.toString().trim();
        if (content.length() > 500) {
            return cleanContent(content);
        }

        return "Content extraction failed - may be a listing page or invalid article";
    }

    private static String cleanContent(String content) {
        // Remove excessive whitespace
        content = content.replaceAll("\\s+", " ");

        // Remove common footer text patterns
        content = content.replaceAll("(?i)read more.*$", "");
        content = content.replaceAll("(?i)continue reading.*$", "");
        content = content.replaceAll("(?i)share this.*$", "");
        content = content.replaceAll("(?i)posted in.*$", "");
        content = content.replaceAll("(?i)tagged with.*$", "");

        return content.trim();
    }

    /**
     * Enhanced article detection using multiple signals
     */
    public static boolean isArticlePage(Document doc) {
        String url = doc.location();

        // Quick URL-based checks first (most efficient)
        if (!passesUrlCheck(url)) {
            return false;
        }

        // Check meta tags (very reliable indicators)
        int metaScore = calculateMetaScore(doc);

        // Check structured data
        int structuredDataScore = calculateStructuredDataScore(doc);

        // Check content structure
        int contentScore = calculateContentScore(doc);

        // Check for listing page indicators
        int listingPenalty = calculateListingPenalty(doc);

        // Calculate final score
        int totalScore = metaScore + structuredDataScore + contentScore - listingPenalty;

        // Debug output
        System.out.printf("Article detection for %s: Meta(%d) + Structured(%d) + Content(%d) - Listing(%d) = %d%n",
                url, metaScore, structuredDataScore, contentScore, listingPenalty, totalScore);

        // Threshold for considering it an article
        return totalScore >= 5;
    }

    private static boolean passesUrlCheck(String url) {
        if (url == null || url.isEmpty()) return true;

        // Check against known listing patterns
        for (Pattern pattern : LISTING_PATTERNS) {
            if (pattern.matcher(url).matches()) {
                return false;
            }
        }

        // URLs that typically indicate articles
        if (url.matches("(?i).*/article/[^/]+/?$") ||
                url.matches("(?i).*/\\d{4}/\\d{2}/\\d{2}/[^/]+/?$") ||
                url.matches("(?i).*/(post|blog)/[^/]+/?$")) {
            return true;
        }

        return true; // Don't reject based on URL alone
    }

    private static int calculateMetaScore(Document doc) {
        int score = 0;

        // Open Graph article type
        Element ogType = doc.selectFirst("meta[property='og:type']");
        if (ogType != null && "article".equals(ogType.attr("content"))) {
            score += 3;
        }

        // Article published time
        if (doc.selectFirst("meta[property='article:published_time']") != null) {
            score += 2;
        }

        // Article author
        if (doc.selectFirst("meta[property='article:author'], meta[name='author']") != null) {
            score += 1;
        }

        // Twitter card type
        Element twitterCard = doc.selectFirst("meta[name='twitter:card']");
        if (twitterCard != null && "summary_large_image".equals(twitterCard.attr("content"))) {
            score += 1;
        }

        return score;
    }

    private static int calculateStructuredDataScore(Document doc) {
        int score = 0;

        // JSON-LD structured data
        Elements scripts = doc.select("script[type='application/ld+json']");
        for (Element script : scripts) {
            String content = script.html().toLowerCase();
            if (content.contains("\"@type\":\"article\"") ||
                    content.contains("\"@type\": \"article\"")) {
                score += 3;
                break;
            }
        }

        // Microdata article indicators
        if (!doc.select("[itemtype*='Article'], [itemscope][itemtype*='article']").isEmpty()) {
            score += 2;
        }

        // Article body microdata
        if (!doc.select("[itemprop='articleBody']").isEmpty()) {
            score += 2;
        }

        return score;
    }

    private static int calculateContentScore(Document doc) {
        int score = 0;

        // Single main article element
        Elements articles = doc.select("article");
        if (articles.size() == 1) {
            score += 2;
        } else if (articles.size() > 1) {
            score -= 1; // Multiple articles suggest listing
        }

        // Article-specific classes
        if (!doc.select(".single-post, .post-single-page, .blog-post-loop").isEmpty()) {
            score += 2;
        }

        // Word count in main content
        String content = extractMainContent(doc.clone()); // Clone to avoid modifying original
        if (content.length() > 1000) {
            score += 2;
        } else if (content.length() > 500) {
            score += 1;
        }

        // Reading time indicator
        if (!doc.select("[class*='reading-time'], [class*='read-time']").isEmpty()) {
            score += 1;
        }

        // Comments section
        if (!doc.select("#comments, .comments, .comment-form, #respond").isEmpty()) {
            score += 1;
        }

        // Social sharing buttons
        if (!doc.select(".social-share, .share, [class*='social-icons']").isEmpty()) {
            score += 1;
        }

        return score;
    }

    private static int calculateListingPenalty(Document doc) {
        int penalty = 0;

        // Multiple article previews
        Elements articlePreviews = doc.select(".post-preview, .excerpt, .post-summary, .blog-post:not(.single)");
        if (articlePreviews.size() > 2) {
            penalty += 3;
        }

        // Pagination indicators
        if (!doc.select(".pagination, .nav-links, .page-numbers, .pager").isEmpty()) {
            penalty += 2;
        }

        // Archive/category indicators
        if (!doc.select(".archive-title, .category-title, .tag-title").isEmpty()) {
            penalty += 2;
        }

        // Multiple "read more" links
        Elements readMoreLinks = doc.select("a[href]:contains(read more), a[href]:contains(continue reading)");
        if (readMoreLinks.size() > 1) {
            penalty += 2;
        }

        // Sidebar with recent posts/categories (common on listing pages)
        Elements sidebar = doc.select(".sidebar, .widget-area");
        if (!sidebar.isEmpty() &&
                !sidebar.select(".recent-posts, .categories, .archive").isEmpty()) {
            penalty += 1;
        }

        return penalty;
    }
}