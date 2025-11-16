import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;


public class ChatServerGUI extends JFrame {

    // UI components
    private JTextArea chatArea;                     // shows all messages
    private JTextField serverMessageField;          // text field for sending server messages
    private JButton sendButton;                     // send server message
    private DefaultListModel<String> clientsModel;  // models the JList of clients
    private JList<String> clientsList;              // shows "Client <id> (username)"
    private JButton disconnectButton;               // disconnect selected client

    // Networking
    private ServerSocket serverSocket;
    private final List<ClientHandler> clientHandlers = Collections.synchronizedList(new ArrayList<>());

    // ID counter
    private int clientCount = 0;

    // Timestamp formatter
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public ChatServerGUI(int port) {
        super("Chat Server - Option B");
        initUI();
        startServer(port);
        setSize(700, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    // Initialize Swing UI (Option B layout)
    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        // ---- Chat area (top) ----
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder("Chat Messages"));
        add(chatScroll, BorderLayout.CENTER);

        // ---- Server message box (middle) ----
        JPanel sendPanel = new JPanel(new BorderLayout(6, 6));
        serverMessageField = new JTextField();
        sendButton = new JButton("Send (Broadcast)");
        sendPanel.add(serverMessageField, BorderLayout.CENTER);
        sendPanel.add(sendButton, BorderLayout.EAST);
        sendPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        add(sendPanel, BorderLayout.SOUTH);

        // send action
        sendButton.addActionListener(e -> sendServerMessage());
        serverMessageField.addActionListener(e -> sendServerMessage());

        // ---- Connected clients list (bottom) ----
        clientsModel = new DefaultListModel<>();
        clientsList = new JList<>(clientsModel);
        clientsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane clientsScroll = new JScrollPane(clientsList);
        clientsScroll.setPreferredSize(new Dimension(200, 120));
        clientsScroll.setBorder(BorderFactory.createTitledBorder("Connected Clients"));

        disconnectButton = new JButton("Disconnect Selected");
        disconnectButton.addActionListener(e -> disconnectSelectedClient());

        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        rightPanel.add(clientsScroll, BorderLayout.CENTER);
        rightPanel.add(disconnectButton, BorderLayout.SOUTH);
        rightPanel.setPreferredSize(new Dimension(220, 0));

        add(rightPanel, BorderLayout.EAST);

        // small padding around main frame
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    // Start server accept thread
    private void startServer(int port) {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                appendToChat("✔ Server started on port " + port + ". Waiting for clients...\n");

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket);
                    handler.start();
                }
            } catch (IOException e) {
                appendToChat("⚠ Server stopped: " + e.getMessage() + "\n");
            }
        }, "Server-Accept-Thread").start();
    }

    // Append text to chatArea on the EDT
    private void appendToChat(String text) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(text);
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    // Send a server-originated broadcast message
    private void sendServerMessage() {
        String msg = serverMessageField.getText().trim();
        if (msg.isEmpty()) return;

        String timestamp = timeFormat.format(new Date());
        String formatted = "[" + timestamp + "] Server: " + msg;
        appendToChat(formatted + "\n");

        broadcast(formatted, null); // broadcast to all clients (null -> no exclusion)
        serverMessageField.setText("");
    }

    // Disconnect the selected client from the UI
    private void disconnectSelectedClient() {
        int idx = clientsList.getSelectedIndex();
        if (idx < 0) {
            JOptionPane.showMessageDialog(this, "No client selected.", "Disconnect", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ClientHandler target = null;
        synchronized (clientHandlers) {
            if (idx < clientHandlers.size()) {
                target = clientHandlers.get(idx);
            }
        }
        if (target != null) {
            try {
                target.socket.close(); // closing socket triggers cleanup in handler
            } catch (IOException ex) {
                appendToChat("⚠ Failed to disconnect client: " + ex.getMessage() + "\n");
            }
        }
    }

    // Update the JList showing clients (called on EDT)
    private void updateClientList() {
        SwingUtilities.invokeLater(() -> {
            clientsModel.clear();
            synchronized (clientHandlers) {
                for (ClientHandler ch : clientHandlers) {
                    clientsModel.addElement("Client " + ch.clientId + " (" + ch.username + ")");
                }
            }
        });
    }

    // Broadcast message to all clients; excludeUser can be null to send to everyone,
    // or set to a specific ClientHandler to NOT send to that client.
    private void broadcast(String message, ClientHandler excludeUser) {
        synchronized (clientHandlers) {
            for (ClientHandler ch : new ArrayList<>(clientHandlers)) {
                if (ch != excludeUser) {
                    ch.out.println(message);
                }
            }
        }
    }

    ////////////////////////////////
    // ClientHandler inner class  //
    ////////////////////////////////
    private class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username = "Unknown";
        private int clientId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            synchronized (ChatServerGUI.this) {
                clientId = ++clientCount;
            }
            setName("ClientHandler-" + clientId);
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // First message from client must be username
                username = in.readLine();
                if (username == null || username.trim().isEmpty()) {
                    username = "User" + clientId;
                }

                // Send assigned ID to this client (special protocol line)
                out.println("ID:" + clientId);

                // Add to list and update UI
                synchronized (clientHandlers) {
                    clientHandlers.add(this);
                }
                updateClientList();

                String joinMsg = username + " joined the chat (Client " + clientId + ").";
                appendToChat("✔ " + joinMsg + "\n");
                broadcast("[" + timeFormat.format(new Date()) + "] " + joinMsg, this); // let others know

                // Read messages from client
                String line;
                while ((line = in.readLine()) != null) {
                    String timestamp = timeFormat.format(new Date());
                    String formatted = "[" + timestamp + "] Client " + clientId + " (" + username + "): " + line;
                    appendToChat(formatted + "\n");
                    // Broadcast to all other clients (exclude sender)
                    broadcast(formatted, this);
                }

            } catch (IOException e) {
                appendToChat("⚠ Connection error with client " + clientId + ": " + e.getMessage() + "\n");
            } finally {
                // Cleanup
                try {
                    if (socket != null && !socket.isClosed()) socket.close();
                } catch (IOException ignored) {}

                synchronized (clientHandlers) {
                    clientHandlers.remove(this);
                }
                updateClientList();

                String leftMsg = username + " (Client " + clientId + ") left the chat.";
                appendToChat("✔ " + leftMsg + "\n");
                broadcast("[" + timeFormat.format(new Date()) + "] " + leftMsg, null);
            }
        }
    }

    // Entry point
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatServerGUI(5000));
    }
}
