package com.robby.crawler;

public class Article {
    private String url;
    private String title;
    private String author;
    private String published;
    private String content;

    public Article(String url, String title, String author, String published, String content) {
        this.url = url;
        this.title = title;
        this.author = author;
        this.published = published;
        this.content = content;
    }

    // getters (optional) ...
}
