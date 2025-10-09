package com.robby.crawler;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.List;
import java.util.Scanner;

public class QuestionAnswering {
    private static final int MAX_CONTEXT_LENGTH = 30000; // Characters to include as context

    public static void main(String[] args) throws Exception {
// Load the .env file
        Dotenv dotenv = Dotenv.load();

        // Get API key from the loaded .env file
        String apiKey = dotenv.get("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("ERROR: GEMINI_API_KEY environment variable not set!");
            System.err.println("Please set it with: export GEMINI_API_KEY='your-api-key-here'");
            System.err.println("Get your API key from: https://makersuite.google.com/app/apikey");
            return;
        }

        System.out.println("Loading articles...");
        List<Article> allArticles = ArticleLoader.loadAllArticles();

        if (allArticles.isEmpty()) {
            System.err.println("No articles found! Please crawl some articles first.");
            System.err.println("Run: gradle run --args='https://example.com'");
            return;
        }

        System.out.println("Loaded " + allArticles.size() + " articles");

        System.out.println("Building context from all articles...");
        String context = buildContextFromAllArticles(allArticles);
        System.out.println("Context length: " + context.length() + " characters");

        System.out.println("\n=== Question Answering System ===");
        System.out.println("Ask questions about the crawled articles. Type 'exit' to quit.\n");

        // Initialize the Client with your API key
        try (Client client = Client.builder().apiKey(apiKey).build()) {
            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.print("\nYour question: ");
                String question = scanner.nextLine().trim();

                if (question.equalsIgnoreCase("exit") || question.equalsIgnoreCase("quit")) {
                    System.out.println("Goodbye!");
                    break;
                }

                if (question.isEmpty()) {
                    continue;
                }

                try {
                    String prompt = buildPrompt(context, question);

                    System.out.println("\nThinking...\n");

                    // Call Gemini model with prompt
                    GenerateContentResponse response =
                            client.models.generateContent("gemini-2.5-pro", prompt, null);

                    String answer = response.text();

                    System.out.println("Answer:");
                      System.out.println(answer);
                    System.out.println("\n" + "=".repeat(60));

                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            scanner.close();
        }
    }

    private static String buildContextFromAllArticles(List<Article> articles) {
        StringBuilder context = new StringBuilder();
        int totalLength = 0;

        for (Article article : articles) {
            String articleText = formatArticle(article);

            if (totalLength + articleText.length() > MAX_CONTEXT_LENGTH) {
                int remaining = MAX_CONTEXT_LENGTH - totalLength;
                if (remaining > 500) {
                    articleText = articleText.substring(0, remaining) + "...\n";
                    context.append(articleText);
                }
                break;
            }

            context.append(articleText);
            totalLength += articleText.length();
        }

        return context.toString();
    }

    private static String formatArticle(Article article) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("Title: ").append(article.getTitle()).append("\n");

        if (article.getAuthor() != null && !article.getAuthor().isEmpty()) {
            sb.append("Author: ").append(article.getAuthor()).append("\n");
        }

        if (article.getPublished() != null && !article.getPublished().isEmpty()) {
            sb.append("Published: ").append(article.getPublished()).append("\n");
        }

        sb.append("URL: ").append(article.getUrl()).append("\n");
        sb.append("\nContent:\n");
        sb.append(article.getContent()).append("\n\n");

        return sb.toString();
    }

    private static String buildPrompt(String context, String question) {
        return """
                You are a helpful assistant that answers questions based on the provided articles.

                Here are all the articles:

                %s

                Based ONLY on the information in the articles above, please answer the following question.
                If the answer cannot be found in the articles, say "I don't have enough information in the provided articles to answer that question."

                Question: %s

                Answer:
                """.formatted(context, question);
    }
}
