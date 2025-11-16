import java.io.*;   // For input/output streams
import java.net.*;  // For networking (Socket, ServerSocket)
import java.util.*; // For ArrayList to store connected clients

// ChatServer class: runs the server and handles multiple clients
public class ChatServer {

    // List to store output streams (writers) of all connected clients
    private static ArrayList<PrintWriter> clientWriters = new ArrayList<>();

    // Counter to assign unique client IDs
    private static int clientCount = 0;

    // Main method: starts the server
    public static void main(String[] args) throws Exception {

        System.out.println(" Chat Server started on port 5000...");

        // Create server socket on port 5000
        ServerSocket serverSocket = new ServerSocket(5000);

        // Loop forever to accept new clients
        while (true) {

            // Wait for a client to connect
            Socket clientSocket = serverSocket.accept();

            System.out.println("✔ Client connected: " + clientSocket);

            // Create a thread to handle this client
            ClientHandler handler = new ClientHandler(clientSocket);

            // Start the client handler thread
            handler.start();
        }
    }

    // Inner class to handle communication with ONE client
    public static class ClientHandler extends Thread {

        Socket socket;          // Client's socket connection
        BufferedReader in;      // To read messages from client
        PrintWriter out;        // To send messages to client
        int clientId;           // Unique ID for each client

        // Constructor assigns socket and auto-increments client ID
        public ClientHandler(Socket socket) {
            this.socket = socket;

            // Give unique client number
            synchronized (ChatServer.class) {
                clientId = ++clientCount;
            }

            System.out.println("Assigned Client ID: " + clientId);
        }

        public void run() {
            try {
                // Create reader to receive messages
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Create writer to send messages
                out = new PrintWriter(socket.getOutputStream(), true);

                // Add this client's writer to global list (thread-safe)
                synchronized (clientWriters) {
                    clientWriters.add(out);
                }

                // Inform server that this client joined
                System.out.println("Client " + clientId + " joined the chat.");

                // Listen for messages from client
                while (true) {

                    // Read one message
                    String message = in.readLine();

                    // If null → client disconnected
                    if (message == null) break;

                    // Format message with client ID
                    String finalMessage = "Client " + clientId + ": " + message;

                    // Show on server console
                    System.out.println("Message Received → " + finalMessage);

                    // Broadcast to all OTHER clients
                    for (PrintWriter writer : clientWriters) {

                        // Don't send back to the sender
                        if (writer != out) {
                            writer.println(finalMessage);
                        }
                    }
                }

            } catch (Exception e) {
                System.out.println("⚠ Client " + clientId + " disconnected.");

            } finally {
                try {
                    socket.close();  // Close this client's socket
                } catch (Exception e) {}

                // Remove client writer from list
                synchronized (clientWriters) {
                    clientWriters.remove(out);
                }

                System.out.println("Client " + clientId + " removed from chat.");
            }
        }
    }
}
