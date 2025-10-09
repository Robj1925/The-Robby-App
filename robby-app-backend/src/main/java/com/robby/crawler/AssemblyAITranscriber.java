package com.robby.crawler;

import com.assemblyai.api.AssemblyAI;
import com.assemblyai.api.resources.transcripts.types.Transcript;
import com.assemblyai.api.core.ApiError; // For handling API errors

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AssemblyAITranscriber {

    // This method is the same as before. It uses yt-dlp to get the audio file.
    public Path downloadAudio(String youtubeUrl) throws IOException, InterruptedException {
        String tempFileName = UUID.randomUUID().toString() + ".mp3"; // MP3 is fine for AssemblyAI
        Path outputPath = Paths.get(System.getProperty("java.io.tmpdir"), tempFileName);

        System.out.println("Downloading audio from: " + youtubeUrl);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "yt-dlp", "-x", "--audio-format", "mp3",
                "-o", outputPath.toString(), youtubeUrl
        );

        Process process = processBuilder.start();
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished || process.exitValue() != 0) {
            throw new RuntimeException("Failed to download audio. yt-dlp process timed out or failed.");
        }
        System.out.println("Audio download complete: " + outputPath);
        return outputPath;
    }

    /**
     * Transcribes an audio file using the AssemblyAI API.
     *
     * @param apiKey        Your AssemblyAI API key.
     * @param audioFilePath The path to the local audio file.
     * @return The transcribed text.
     */
    public String transcribe(String apiKey, Path audioFilePath) {
        System.out.println("Initializing AssemblyAI client and uploading for transcription...");

        // 1. Initialize the AssemblyAI client with your API key
        AssemblyAI aai = AssemblyAI.builder()
                .apiKey(apiKey)
                .build();

        File audioFile = audioFilePath.toFile();

        try {
            // 2. Call the transcribe method, which uploads the file and polls for the result
            Transcript transcript = aai.transcripts().transcribe(audioFile);

            // 3. Check the result and get the text
            if (transcript.getText().isPresent()) {
                System.out.println("Transcription successful.");
                return transcript.getText().get();
            } else {
                // The SDK's transcribe method should throw an error before this, but it's good practice
                return "Transcription failed. Status: " + transcript.getStatus();
            }
        } catch (ApiError e) {
            System.err.println("API Error: " + e.getMessage());
            System.err.println("Error details: " + e.body().toString());
            throw new RuntimeException("Failed to transcribe due to API error.", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java AssemblyAITranscriber <youtube_url>");
            return;
        }

        String apiKey = System.getenv("ASSEMBLYAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: ASSEMBLYAI_API_KEY environment variable not set.");
            return;
        }

        String youtubeUrl = args[0];
        AssemblyAITranscriber transcriber = new AssemblyAITranscriber();
        Path audioFile = null;
        try {
            // Step 1: Download audio locally (same as before)
            audioFile = transcriber.downloadAudio(youtubeUrl);

            // Step 2: Transcribe using the AssemblyAI SDK
            String transcript = transcriber.transcribe(apiKey, audioFile);

            // Step 3: Print the result
            System.out.println("\n--- ASSEMBLYAI TRANSCRIPT ---");
            System.out.println(transcript);

        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
        } finally {
            // Step 4: Clean up the temporary audio file
            if (audioFile != null) {
                try {
                    Files.delete(audioFile);
                    System.out.println("\nCleaned up temporary file: " + audioFile);
                } catch (IOException e) {
                    System.err.println("Failed to delete temporary file: " + audioFile);
                }
            }
        }
    }
}