package com.robby.crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Arrays;
import java.util.List;

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
            "main"
    );

    public static String extractTitle(Document doc) {
        String title = doc.title();
        String metaTitle = doc.selectFirst("meta[property=og:title], meta[name=twitter:title]") != null
                ? doc.selectFirst("meta[property=og:title], meta[name=twitter:title]").attr("content")
                : null;
        if (metaTitle != null && !metaTitle.isBlank()) return metaTitle;
        if (title != null && !title.isBlank()) return title;
        Element h1 = doc.selectFirst("h1");
        return h1 != null ? h1.text() : "";
    }

    public static String extractAuthor(Document doc) {
        Element meta = doc.selectFirst("meta[name=author], meta[property=article:author]");
        if (meta != null) return meta.attr("content");
        Element byline = doc.selectFirst(".author, .byline, [rel=author]");
        return byline != null ? byline.text() : "";
    }

    public static String extractPublishedDate(Document doc) {
        Element meta = doc.selectFirst("meta[property=article:published_time], meta[name=date], time[datetime]");
        if (meta != null) {
            String v = meta.hasAttr("content") ? meta.attr("content") : meta.attr("datetime");
            return v == null ? "" : v;
        }
        return "";
    }

    public static String extractMainContent(Document doc) {
        // remove unlikely boilerplate first
        doc.select("script, style, noscript, svg, iframe, footer, header, nav, form, aside").remove();

        // try known selectors
        for (String sel : ARTICLE_SELECTORS) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                el.select("script, style, .advertisement, .ads, nav, footer, header").remove();
                String text = el.text().trim();
                if (text.length() > 200) return text;
            }
        }

        // fallback: find the largest block of readable text
        Element best = null;
        int bestLen = 0;
        for (Element el : doc.select("div, section, main, article")) {
            el.select("script, style, .advertisement, .ads, nav, footer, header").remove();
            int len = el.text().trim().length();
            if (len > bestLen) {
                bestLen = len;
                best = el;
            }
        }
        if (best != null) return best.text().trim();

        // last fallback: all paragraph text
        StringBuilder all = new StringBuilder();
        for (Element p : doc.select("p")) {
            all.append(p.text()).append("\n\n");
        }
        return all.toString().trim();
    }
}
