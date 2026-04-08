package com.dev.Rabbi.AiWeatherTool.weather;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WeatherController {

    private final ChatClient chatClient;
    private final WeatherTools weatherTools;

    public WeatherController(ChatClient.Builder builder, WeatherTools weatherTools) {
        this.chatClient = builder.build();
        this.weatherTools = weatherTools;
    }

    @GetMapping("/weather/alerts")
    public ResponseEntity<String> getAlerts(@RequestParam String message) {
        return chat(message);
    }

    @GetMapping("/weather/chat")
    public ResponseEntity<String> chat(@RequestParam String message) {
        return runChat(message);
    }

    @PostMapping(path = "/weather/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chat(@RequestBody ChatRequest request) {
        return runChat(request.message());
    }

    private ResponseEntity<String> runChat(String message) {
        try {
            String content = chatClient.prompt()
                    .tools(weatherTools)
                    .user(message)
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                return ResponseEntity.internalServerError()
                        .body("The assistant returned an empty response. Please try again.");
            }

            return ResponseEntity.ok(content);
        } catch (Exception exception) {
            String messageText = """
                    The weather assistant could not complete that request.
                    Check that OPENAI_API_KEY is set in the same PowerShell session where you started the app, then try again.
                    Technical detail: %s
                    """.formatted(exception.getMessage());
            return ResponseEntity.internalServerError().body(messageText.strip());
        }
    }

    public record ChatRequest(String message) {
    }
}
