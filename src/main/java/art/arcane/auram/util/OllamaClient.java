package art.arcane.auram.util;

import art.arcane.auram.AuramConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OllamaClient {
    private final String baseUrl;
    private final String model;

    public OllamaClient(String baseUrl, String model) {
        if (baseUrl.contains("localhost")) {
            baseUrl = baseUrl.replace("localhost", "127.0.0.1");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.model = model;
    }
    
    public String generateResponse(String prompt) {
        return generateResponse(prompt, 1);
    }

    public String generateResponse(String prompt, int wms) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(this.baseUrl + "chat/completions");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + AuramConfig.COMMON.llmKey.get());
            conn.setDoOutput(true);
            conn.setConnectTimeout(50000); // 5 seconds to connect
            conn.setReadTimeout(1200000);  // 2 minutes to read response
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            JsonArray messages = new JsonArray();
            messages.add(message);
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", "low");
            JsonObject payload = new JsonObject();
            payload.addProperty("model", this.model);
            //payload.add("reasoning", reasoning);
            payload.add("messages", messages);
            payload.addProperty("temperature", 0.2);
            payload.addProperty("stream", false); // Critical for non-streaming
            String jsonInputString = payload.toString();
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    System.err.println("Ollama API Error (" + code + "): " + response.toString());
                    
                    if(code == 429) {
                        System.out.println("Ollama API Rate Limit Exceeded. waiting " + (10L * wms) + "s before retrying...");
                        Thread.sleep(10000L * wms);
                        return generateResponse(prompt, wms + 1);
                        
                    }
                }
                return "";
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
            return jsonResponse.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

        } catch (Exception e) {
            System.err.println("Ollama Connection Failed: " + e.getMessage());
            e.printStackTrace();
            return "";
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}