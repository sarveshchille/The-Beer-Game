package com.example.beergameapi;

// Imports for the built-in Java web server
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

// Imports for our game logic
import com.example.beergameapi.model.GameConfig; // Keep for Player constructor dependency
import com.example.beergameapi.model.GameRoom;
import com.example.beergameapi.model.Player;
import com.example.beergameapi.service.RoomManager;
import com.example.beergameapi.model.GameRoom.PlayerGameMapping;

// Imports for handling I/O, networking, and JSON
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.List; 

// We MUST use a real JSON library for this level of complexity
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * [FINAL TOURNAMENT VERSION]
 * The server now talks to a RoomManager to handle multiple
 * 16-player tournament rooms.
 * It uses Gson for reliable JSON parsing and multithreaded game logic.
 */
public class BeerGameServer {

    // The server talks to the RoomManager, which is the new Singleton "brain"
    private static final RoomManager roomManager = RoomManager.getInstance();
    
    // Gson is used for all JSON parsing and creation
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // --- NEW API Routes ---
        
        // POST /room/create - Creates a new 16-player room
        server.createContext("/room/create", new CreateRoomHandler());

        // POST /room/join - A player joins a specific room
        server.createContext("/room/join", new JoinRoomHandler());
        
        // GET /room/status/{roomId} - Checks the status (waiting, running, finished)
        server.createContext("/room/status/", new RoomStatusHandler());
        
        // POST /room/order - A player submits their order for their game
        server.createContext("/room/order", new OrderHandler());

        // GET /room/player/status - A player gets their personal game state
        server.createContext("/room/player/status", new PlayerStatusHandler());
        
        // GET /room/results/{roomId} - Gets the final MVP and team scores
        server.createContext("/room/results/", new ResultsHandler());

        server.setExecutor(null); // Use default executor
        server.start();

        System.out.println("=======================================================");
        System.out.println("  Beer Game TOURNAMENT Server is running!");
        System.out.println("  (Multithreaded, Synchronized)");
        System.out.println("  Listening on port 8080...");
        System.out.println("=======================================================");
    }

    // --- API Handlers ---

    /** Handles POST /room/create */
    static class CreateRoomHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}"); return;
            }
            // Create a new room in the RoomManager
            String roomId = roomManager.createRoom();
            // Send the new room's ID back to the user
            sendResponse(exchange, 200, gson.toJson(Map.of("roomId", roomId)));
        }
    }

    /** Handles POST /room/join */
    static class JoinRoomHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}"); return;
            }
            
            String body = readRequestBody(exchange);
            Map<String, String> data;
            try {
                // Parse the JSON request body
                data = gson.fromJson(body, Map.class);
            } catch (JsonSyntaxException e) {
                 sendResponse(exchange, 400, "{\"error\":\"Invalid JSON format.\"}"); return;
            }
            
            // Get all required fields from the JSON
            String roomId = data.get("roomId");
            String userId = data.get("userId");
            String initialTeamId = data.get("initialTeamId");
            String role = data.get("role"); // The player's role in their *initial* team

            if (roomId == null || userId == null || initialTeamId == null || role == null) {
                sendResponse(exchange, 400, "{\"error\":\"roomId, userId, initialTeamId, and role are required.\"}"); return;
            }

            GameRoom room = roomManager.getRoom(roomId);
            if (room == null) {
                sendResponse(exchange, 404, "{\"error\":\"Room not found.\"}"); return;
            }

            // Attempt to add the player to the room
            String message = room.addPlayer(userId, initialTeamId, role);
            
            // Send the result (e.g., "Joined", "Error", or "Game Starting!")
            if (message.startsWith("Error:")) {
                 sendResponse(exchange, 400, gson.toJson(Map.of("message", message)));
            } else {
                 sendResponse(exchange, 200, gson.toJson(Map.of("message", message)));
            }
        }
    }
    
    /** Handles GET /room/status/{roomId} */
    static class RoomStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // Get the roomId from the end of the URL
            String roomId = path.substring("/room/status/".length());
            GameRoom room = roomManager.getRoom(roomId);
            if (room == null) {
                sendResponse(exchange, 404, "{\"error\":\"Room not found.\"}"); return;
            }
            // Return the room's status and how many players have joined
            sendResponse(exchange, 200, gson.toJson(Map.of("status", room.getStatus(), "playersJoined", room.getPlayerCount())));
        }
    }

    /** Handles POST /room/order */
    static class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}"); return;
            }
            
            String body = readRequestBody(exchange);
            Map<String, String> data;
            try {
                data = gson.fromJson(body, Map.class);
            } catch (JsonSyntaxException e) {
                 sendResponse(exchange, 400, "{\"error\":\"Invalid JSON format.\"}"); return;
            }
            
            String roomId = data.get("roomId");
            String userId = data.get("userId");
            
            if (roomId == null || userId == null || data.get("orderAmount") == null) {
                 sendResponse(exchange, 400, "{\"error\":\"roomId, userId, and orderAmount are required.\"}"); return;
            }

            try {
                // Parse the order amount
                int orderAmount = Integer.parseInt(data.get("orderAmount"));
                GameRoom room = roomManager.getRoom(roomId);
                if (room == null) {
                    sendResponse(exchange, 404, "{\"error\":\"Room not found.\"}"); return;
                }

                // Submit the order to the room for processing
                String message = room.submitOrder(userId, orderAmount);
                sendResponse(exchange, 200, gson.toJson(Map.of("message", message)));
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "{\"error\":\"orderAmount must be a number.\"}"); return;
            }
        }
    }
    
    /** Handles GET /room/player/status?roomId=...&userId=... */
    static class PlayerStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
             if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}"); return;
            }
            
            // This endpoint uses query parameters (the ?... part of the URL)
            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String roomId = params.get("roomId");
            String userId = params.get("userId");

            GameRoom room = roomManager.getRoom(roomId);
            if (room == null || room.getStatus() == GameRoom.RoomStatus.WAITING_FOR_PLAYERS) {
                sendResponse(exchange, 404, "{\"error\":\"Room not found or not started.\"}"); return;
            }

            // Find the player's specific assignment
            PlayerGameMapping mapping = room.getPlayerAssignments().get(userId);
            if (mapping == null) {
                sendResponse(exchange, 404, "{\"error\":\"Player not found in this room.\"}"); return;
            }
            
            // Get the player's state from their assigned GameEngine
            Player player = mapping.getPlayer();
            
            // Build the simplified 6-field status response
            Map<String, Object> status = Map.of(
                "yourUserId", userId,
                "yourInitialTeam", mapping.initialTeamId,
                "yourNewRoleInGame", player.getRole(),
                "currentWeek", mapping.game.getCurrentWeek(),
                "inventory", player.getInventory(),
                "backorder", player.getBackorder(),
                "customerOrders", player.getLastOrderReceived(),
                "incomingDelivery", player.getLastShipmentReceived(),
                "outgoingDelivery", player.getLastShipmentSent(),
                "costs", String.format("%.2f", player.getLastWeeklyCost())
            );
            sendResponse(exchange, 200, gson.toJson(status));
        }
    }
    
    /** Handles GET /room/results/{roomId} */
    static class ResultsHandler implements HttpHandler {
         @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String roomId = path.substring("/room/results/".length());
            
            // Get the calculated results from the RoomManager
            Map<String, Object> results = roomManager.getResults(roomId);
            
            if (results.containsKey("error")) {
                // Send an error if results aren't ready or room doesn't exist
                sendResponse(exchange, 400, gson.toJson(results));
            } else {
                // Send the final results (MVP, winning team, etc.)
                sendResponse(exchange, 200, gson.toJson(results));
            }
        }
    }

    // --- Utility Methods ---
    
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
     * Parses URL query parameters (e.g., ?key1=val1&key2=val2) into a Map.
     */
    public static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}

