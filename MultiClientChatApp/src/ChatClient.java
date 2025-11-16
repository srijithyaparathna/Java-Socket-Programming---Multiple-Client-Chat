import java.io.*; // Import classes for input/output operations
import java.net.*; // Import classes for networking (Socket)
import java.util.Scanner; // Import Scanner class to read user input from console

// ChatClient class: Connects to the server and sends/receives messages
public class ChatClient {

    // Main method: Entry point of the client application
    public static void main(String[] args) throws Exception {

        // Create a Scanner object to read user input from the console
        Scanner scanner = new Scanner(System.in);

        // Create a Socket connection to the server at localhost (same computer) on port 5000
        // This establishes a TCP connection with the ChatServer
        Socket socket = new Socket("localhost", 5000);

        // Print confirmation message that connection was successful
        System.out.println("✔ Connected to Chat Server!");

        // Create BufferedReader to receive messages from the server
        // Gets the input stream from socket and wraps it to read text lines
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Create PrintWriter to send messages to the server
        // Gets the output stream from socket, 'true' enables auto-flush
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        // Create a new Thread to continuously read incoming messages from server
        // This runs concurrently with the main thread (which handles user input)
        Thread readerThread = new Thread(() -> {
            try {
                // Infinite loop to continuously read messages from server
                while (true) {
                    // Read one line of text from the server (blocks until data arrives)
                    String response = in.readLine();

                    // If the message is not null, print it to the console
                    if (response != null) {
                        System.out.println(response);
                    }
                }
            } catch (Exception e) {
                // If connection is lost or error occurs, print disconnection message
                System.out.println("⚠ Disconnected from server.");
            }
        });

        // Start the reader thread (calls the run() method defined above)
        readerThread.start();

        // Main loop in the main thread: continuously read user input and send to server
        while (true) {
            // Read a line of text typed by the user in the console
            String msg = scanner.nextLine();

            // Send the message to the server (auto-flushed due to PrintWriter configuration)
            out.println(msg);
        }
    }
}