package com.robby.crawler;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ArticleLoader {
    private static final Gson GSON = new Gson();
    private static final String ARTICLES_DIR = "data/articles";

    /**
     * Load all articles from the data/articles directory
     */
    public static List<Article> loadAllArticles() throws IOException {
        List<Article> articles = new ArrayList<>();
        Path articlesPath = Paths.get(ARTICLES_DIR);

        if (!Files.exists(articlesPath)) {
            System.out.println("No articles directory found at: " + ARTICLES_DIR);
            return articles;
        }

        try (Stream<Path> paths = Files.walk(articlesPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String json = Files.readString(path);
                            Article article = GSON.fromJson(json, Article.class);
                            articles.add(article);
                        } catch (Exception e) {
                            System.err.println("Failed to load article from " + path + ": " + e.getMessage());
                        }
                    });
        }

        System.out.println("Loaded " + articles.size() + " articles");
        return articles;
    }
}