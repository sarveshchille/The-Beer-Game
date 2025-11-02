package com.example.beergameapi;

// Imports for the built-in Java web server
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

// Imports for our game logic
import com.example.beergameapi.model.GameConfig;
import com.example.beergameapi.model.Player;
import com.example.beergameapi.service.GameEngine;

// Imports for handling I/O and networking
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.List; 

/**
 * The main application class.
 * This class starts a simple, built-in Java web server
 * to handle API requests for the Beer Game.
 *
 * FINAL VERSION:
 * - API simplified to show only the 6 core fields.
 * - Uses the corrected GameEngine.
 */
public class BeerGameServer {

    // Get the single instance of our game "brain"
    private static final GameEngine gameEngine = GameEngine.getInstance();

    public static void main(String[] args) throws IOException {
        // Create the server on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // --- Define API Routes (Contexts) ---
        
        // GET /game/status - Checks the current week and game status
        server.createContext("/game/status", new StatusHandler());

        // GET /game/player/{role} - Gets a player's current "paused" state
        // GET /game/player/{role}/history - Gets a player's full game report
        server.createContext("/game/player/", new PlayerHandler());

        // POST /game/order - Submits a player's order for the week
        server.createContext("/game/order", new OrderHandler());

        // POST /game/reset - Resets the game to Week 1
        server.createContext("/game/reset", new ResetHandler());

        server.setExecutor(null); // Use a default executor
        server.start(); // Start the server

        System.out.println("=======================================================");
        System.out.println("  Java Beer Game Server is running!");
        System.out.println("  Listening on port 8080...");
        System.out.println("  Access the API at: http://localhost:8080/game/status");
        System.out.println("=======================================================");
    }

    // --- API Handler Classes (Nested) ---

    /**
     * Handles GET /game/status
     */
    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}"); 
                return;
            }
            int week = gameEngine.getCurrentWeek();
            String message;
            if (gameEngine.isGameOver()) {
                message = "Game is over. Final week " + (week - 1) + " was completed.";
            } else {
                message = "Game is in progress. It is currently Week " + week + ".";
            }
            String jsonResponse = buildJsonResponse(
                "currentWeek", String.valueOf(week),
                "isGameOver", String.valueOf(gameEngine.isGameOver()),
                "message", message
            );
            sendResponse(exchange, 200, jsonResponse);
        }
    }

    /**
     * Handles POST /game/reset
     */
    static class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            gameEngine.resetGame();
            String jsonResponse = buildJsonResponse("message", "Game has been reset to Week 1.");
            sendResponse(exchange, 200, jsonResponse);
        }
    }

    /**
     * Handles player data requests:
     * GET /game/player/{role} (current state, 6 fields)
     * GET /game/player/{role}/history (full history, 6 fields)
     */
    static class PlayerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            // Check if it's a history request
            boolean isHistoryRequest = path.endsWith("/history");
            
            // Extract the role from the URL
            // path = /game/player/Retailer OR /game/player/Retailer/history
            String role;
            String tempPath = path.substring("/game/player/".length());
            
            if (isHistoryRequest) {
                // Find the role before "/history"
                role = tempPath.substring(0, tempPath.indexOf("/history"));
            } else {
                // The role is the whole last part
                role = tempPath;
            }

            Player player = gameEngine.getPlayer(role);

            if (player == null) {
                String jsonResponse = buildJsonResponse("error", "Player role '" + role + "' not found.");
                sendResponse(exchange, 404, jsonResponse); // 404 Not Found
                return;
            }

            // --- Route to the correct response ---
            if (isHistoryRequest) {
                // Handle History Request
                String jsonResponse = buildHistoryJson(player, gameEngine.isGameOver());
                sendResponse(exchange, 200, jsonResponse);
            } else {
                // Handle Current State Request (Simplified 6-field view)
                String jsonResponse = buildJsonResponse(
                    "role", player.getRole(),
                    "currentWeek", String.valueOf(gameEngine.getCurrentWeek()),
                    "inventory", String.valueOf(player.getInventory()),
                    "backorder", String.valueOf(player.getBackorder()),
                    "customerOrders", String.valueOf(player.getLastOrderReceived()),
                    "incomingDelivery", String.valueOf(player.getLastShipmentReceived()),
                    "outgoingDelivery", String.valueOf(player.getLastShipmentSent()),
                    "costs", String.format("%.2f", player.getLastWeeklyCost())
                );
                sendResponse(exchange, 200, jsonResponse);
            }
        }
    }

    /**
     * Handles POST /game/order
     */
    static class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            // Read the JSON body from the request
            String requestBody = readRequestBody(exchange);
            
            // --- DEBUG LINE ---
            // Uncomment this line if you have issues in Postman:
            // System.out.println("[DEBUG] Received request body: " + requestBody);
            // ------------------
            
            // Parse the simple JSON
            Map<String, String> params = parseSimpleJson(requestBody);
            String role = params.get("role");
            String amountStr = params.get("orderAmount");

            // Validate the input
            if (role == null || amountStr == null) {
                String jsonResponse = buildJsonResponse("error", "Invalid request body. 'role' and 'orderAmount' are required.");
                sendResponse(exchange, 400, jsonResponse); // 400 Bad Request
                return;
            }
            try {
                int orderAmount = Integer.parseInt(amountStr);
                // Send the order to the GameEngine
                String message = gameEngine.receiveOrder(role, orderAmount);
                
                // Send the engine's response back to the client
                if (message.startsWith("Error:")) {
                    sendResponse(exchange, 400, buildJsonResponse("error", message));
                } else {
                    sendResponse(exchange, 200, buildJsonResponse("message", message));
                }
            } catch (NumberFormatException e) {
                String jsonResponse = buildJsonResponse("error", "Invalid 'orderAmount'. Must be a number.");
                sendResponse(exchange, 400, jsonResponse);
            }
        }
    }

    // --- Utility Methods (for handling HTTP and JSON) ---

    /**
     * Sends a JSON response to the client.
     */
    private static void sendResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Reads the body of an HTTP request into a String.
     */
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * A very simple parser for our specific JSON: {"key1":"value1", "key2":"value2"}
     */
    private static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        try {
            json = json.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                String[] pairs = json.substring(1, json.length() - 1).split(",");
                for (String pair : pairs) {
                    String[] kv = pair.split(":", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim().replace("\"", "");
                        String value = kv[1].trim().replace("\"", "");
                        map.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            // This is not a robust parser, but it's fine for this project.
            System.err.println("Error parsing JSON: " + json);
        }
        return map;
    }

    /**
     * A helper to build a simple JSON object string from key-value pairs.
     * It correctly handles numbers, booleans, and strings.
     */
    private static String buildJsonResponse(String... kvs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < kvs.length; i += 2) {
            if (i > 0) {
                sb.append(", ");
            }
            String key = kvs[i];
            String value = kvs[i + 1];
            sb.append("\"").append(key).append("\": ");
            try {
                // Try parsing as a number
                Double.parseDouble(value);
                sb.append(value); // It's a number, so don't quote it
            } catch (NumberFormatException e) {
                // It's not a number, check for boolean
                if(value.equals("true") || value.equals("false")) {
                    sb.append(value); // It's a boolean, don't quote it
                } else {
                    sb.append("\"").append(value).append("\""); // It's a string, quote it
                }
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Builds the JSON response for a player's full history,
     * using the simplified 6-field view.
     */
    private static String buildHistoryJson(Player player, boolean isGameOver) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"role\": \"").append(player.getRole()).append("\",\n");
        sb.append("  \"isGameOver\": ").append(isGameOver).append(",\n");
        
        // Get the final cost from the player's cumulative cost
        double finalCost = player.getCumulativeCost();
        sb.append("  \"finalTotalCost\": ").append(String.format("%.2f", finalCost)).append(",\n");
        
        sb.append("  \"history\": [\n");
        
        List<Player.WeeklyState> history = player.getWeeklyHistory();
        for (int i = 0; i < history.size(); i++) {
            Player.WeeklyState state = history.get(i);
            sb.append("    {\n");
            // These are the 6 fields (+ week) you requested
            sb.append("      \"week\": ").append(state.week).append(",\n");
            sb.append("      \"inventory\": ").append(state.inventory).append(",\n");
            sb.append("      \"backorder\": ").append(state.backorder).append(",\n");
            sb.append("      \"customerOrders\": ").append(state.customerOrders).append(",\n");
            sb.append("      \"incomingDelivery\": ").append(state.incomingDelivery).append(",\n");
            sb.append("      \"outgoingDelivery\": ").append(state.outgoingDelivery).append(",\n");
            sb.append("      \"costs\": ").append(String.format("%.2f", state.costs)).append("\n");
            sb.append("    }");
            if (i < history.size() - 1) {
                sb.append(",\n"); // Add a comma if it's not the last item
            }
        }
        
        sb.append("\n  ]\n"); // End of history array
        sb.append("}\n"); // End of main object
        return sb.toString();
    }
}

