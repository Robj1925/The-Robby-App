package com.robby.crawler;

public class Article {
    private String url;
    private String title;
    private String author;
    private String published;
    private String content;
    private int contentLength;
    private boolean isArticlePage;
    private int articleConfidenceScore;
    private String extractionMethod;

    public Article(String url, String title, String author, String published, String content) {
        this.url = url;
        this.title = title;
        this.author = author;
        this.published = published;
        this.content = content;
        this.contentLength = content != null ? content.length() : 0;
        this.isArticlePage = true; // Default assumption if we're creating an Article object
        this.articleConfidenceScore = 0;
        this.extractionMethod = "default";
    }

    public Article(String url, String title, String author, String published, String content,
                   int confidenceScore, String extractionMethod) {
        this(url, title, author, published, content);
        this.articleConfidenceScore = confidenceScore;
        this.extractionMethod = extractionMethod;
    }

    // Getters
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getPublished() { return published; }
    public String getContent() { return content; }
    public int getContentLength() { return contentLength; }
    public boolean isArticlePage() { return isArticlePage; }
    public int getArticleConfidenceScore() { return articleConfidenceScore; }
    public String getExtractionMethod() { return extractionMethod; }

    // Setters
    public void setIsArticlePage(boolean isArticlePage) {
        this.isArticlePage = isArticlePage;
    }

    public void setArticleConfidenceScore(int score) {
        this.articleConfidenceScore = score;
    }

    public void setExtractionMethod(String method) {
        this.extractionMethod = method;
    }

    @Override
    public String toString() {
        return String.format("Article{url='%s', title='%s', contentLength=%d, confidence=%d}",
                url, title, contentLength, articleConfidenceScore);
    }
}