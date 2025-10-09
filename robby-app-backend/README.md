# Robby Crawler - Web Scraping & Question Answering System

A Java-based web crawler that extracts article content from websites and provides an AI-powered question-answering interface using Google's Gemini API.

## Features

- **Smart Web Crawling**: Crawl single pages or entire domains with configurable depth and page limits
- **Intelligent Article Detection**: Multi-signal article detection system that filters out listing pages, navigation pages, and non-content pages
- **Content Extraction**: Extracts titles, authors, publication dates, and main content from articles
- **Audio Transcription**: Transcribe YouTube videos using AssemblyAI (optional feature)
- **AI-Powered Q&A**: Ask questions about crawled articles using Google's Gemini 2.5 Pro model

## Prerequisites

- Java 17 or higher
- Gradle
- (Optional) `yt-dlp` for YouTube audio extraction
- Google Gemini API key
- (Optional) AssemblyAI API key for transcription features

## Installation

1. Clone the repository:
```bash
git clone [<your-repo-url>](https://github.com/Robj1925/The-Robby-App/tree/develop/robby-app-backend)
cd src/main/java/com/robby/crawler
```

2. Set up environment variables:
```bash
export GEMINI_API_KEY='your-gemini-api-key'
export ASSEMBLYAI_API_KEY='your-assemblyai-api-key'  # Optional
```

Or create a `.env` file in the project root:
```
GEMINI_API_KEY=your-gemini-api-key
ASSEMBLYAI_API_KEY=your-assemblyai-api-key
```

3. Build the project:
```bash
./gradlew build
```

## Usage

### 1. Crawling Articles

#### Single Page Crawl
Extract content from a single article:
```bash
./gradlew run --args='https://example.com/article'
```

#### Limited Domain Crawl
Crawl up to a specific number of pages:
```bash
./gradlew run --args='https://example.com --max-pages 50'
```

#### Full Domain Crawl
Crawl an entire domain (use with caution):
```bash
./gradlew run --args='https://example.com --crawl-all'
```

#### Custom Depth Crawl
Control how deep the crawler goes:
```bash
./gradlew run --args='https://example.com --depth 3 --max-pages 100'
```

**Options:**
- `--depth D`: Maximum depth to crawl (default: 2)
- `--max-pages M`: Maximum number of pages to collect (default: 50)
- `--crawl-all`: Crawl the entire domain without page limits

### 2. Question Answering

After crawling articles, query them using AI:
```bash
./gradlew runQA
```

The system will:
1. Load all crawled articles from `data/articles/`
2. Build a context from the article content
3. Allow you to ask questions interactively
4. Use Gemini 2.5 Pro to answer based on the crawled content

Example session:
```
Your question: What are the main topics discussed in these articles?
Thinking...

Answer:
Based on the articles, the main topics include...

Your question: exit
Goodbye!
```

### 3. YouTube Transcription (Optional)

Transcribe YouTube videos to text:
```bash
java -cp build/libs/crawler-0.1.0.jar com.robby.crawler.AssemblyAITranscriber <youtube_url>
```

## Project Structure

```
com.robby.crawler/
├── Article.java              # Data model for articles
├── ArticleLoader.java        # Loads saved articles from JSON files
├── AssemblyAITranscriber.java # YouTube audio transcription
├── ContentExtractor.java     # Extracts content from HTML documents
├── Crawler.java             # Main web crawler
└── QuestionAnswering.java   # AI-powered Q&A system
```

### Key Components

#### `Crawler.java`
- Multi-threaded crawling with configurable workers
- Rate limiting (1 second between requests)
- Domain-aware URL filtering
- Automatic duplicate detection

#### `ContentExtractor.java`
- Multi-signal article detection (meta tags, structured data, content analysis)
- Smart content extraction with WordPress support
- Filters out navigation, ads, and non-content elements
- Confidence scoring for article detection

#### `QuestionAnswering.java`
- Interactive CLI for asking questions
- Context window management (30,000 character limit)
- Powered by Google Gemini 2.5 Pro

#### `AssemblyAITranscriber.java`
- Downloads audio from YouTube using `yt-dlp`
- Transcribes using AssemblyAI SDK
- Automatic cleanup of temporary files

## Configuration

### Rate Limiting
Default: 1 second between requests
Modify in `Crawler.java`:
```java
private static final long RATE_LIMIT_MS = 1000L;
```

### Crawler Settings
```java
private static final int MAX_DEPTH = 2;        // URL depth limit
private static final int MAX_PAGES = 50;       // Default max pages
private static final int TIMEOUT_MS = 15_000;  // HTTP timeout
```

### Q&A Context
```java
private static final int MAX_CONTEXT_LENGTH = 30000; // Characters
```

## Data Storage

Articles are saved as JSON files in `data/articles/`:
```json
{
  "url": "https://example.com/article",
  "title": "Article Title",
  "author": "Author Name",
  "published": "2024-01-01",
  "content": "Article content...",
  "contentLength": 5000,
  "isArticlePage": true,
  "articleConfidenceScore": 8,
  "extractionMethod": "default"
}
```

## Article Detection

The crawler uses a multi-signal approach to detect articles:

**Positive Signals:**
- Open Graph article metadata
- Article publication timestamps
- JSON-LD structured data
- Single `<article>` element
- Reading time indicators
- Comments sections
- Substantial content (>500 characters)

**Negative Signals:**
- Multiple article previews
- Pagination indicators
- Archive/category titles
- Multiple "read more" links
- URL patterns matching listing pages

Confidence scores ≥5 are considered articles.

## Error Handling

- Invalid URLs are skipped automatically
- Failed requests are logged but don't stop the crawler
- Insufficient content is detected and skipped
- API errors are caught and reported with details

## Important Notes

⚠️ **AssemblyAI SDK Discontinuation**: As of April 2025, the AssemblyAI Java SDK has been discontinued. The transcription feature may require migration to direct API calls in the future.

⚠️ **Respect robots.txt**: Always check a site's `robots.txt` before crawling

⚠️ **Rate Limiting**: Be respectful of server resources. The default 1-second delay is a minimum.

⚠️ **Legal Compliance**: Ensure you have permission to scrape websites and comply with their Terms of Service

## Dependencies

- **Jsoup 1.16.1**: HTML parsing and web scraping
- **Gson 2.10.1**: JSON serialization/deserialization
- **AssemblyAI Java SDK 4.0.1**: Audio transcription (discontinued)
- **Google GenAI 1.17.0**: Gemini API integration
- **dotenv-java 3.0.0**: Environment variable management

## Troubleshooting

### No articles found
```bash
# Make sure you've crawled some content first
./gradlew run --args='https://example.com --max-pages 10'
```

### API key errors
```bash
# Verify your environment variables
echo $GEMINI_API_KEY
echo $ASSEMBLYAI_API_KEY

# Or check your .env file
cat .env
```

### Crawler not finding articles
- Check if the target site has different HTML structure
- Review the article detection scoring in logs
- Adjust the `ARTICLE_SELECTORS` in `ContentExtractor.java`

### Thread/concurrency issues
- Reduce thread count in `Crawler.java` (default: 3)
- Increase rate limiting delay

## Contributing

Contributions are welcome! Please:
1. Test your changes thoroughly
2. Maintain the existing code style
3. Update documentation as needed
4. Respect the rate limiting and ethical scraping practices

## License

OpenSource

## Support

For issues and questions:
- Check the console output for detailed error messages
- Review the crawled JSON files in `data/articles/`
- Ensure all environment variables are set correctly

---

**Note**: This crawler is designed for educational and research purposes. Always obtain permission before scraping websites and respect their terms of service and robots.txt files.
