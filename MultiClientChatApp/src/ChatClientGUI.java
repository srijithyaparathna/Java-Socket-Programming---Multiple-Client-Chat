import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;


public class ChatClientGUI extends JFrame {

    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton disconnectButton;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private String username;
    private int clientId = -1; // will be assigned by server
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public ChatClientGUI() {
        setTitle("Chat Client");
        setSize(500, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initComponents();

        // Ask username
        username = JOptionPane.showInputDialog(this, "Enter your username:", "Username", JOptionPane.PLAIN_MESSAGE);
        if (username == null || username.trim().isEmpty()) {
            username = "User" + (int) (Math.random() * 1000);
        }
        setTitle("Chat Client - " + username);

        connectToServer();

        setVisible(true);
    }

    private void initComponents() {
        setLayout(new BorderLayout(8, 8));

        // Chat area (top)
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Chat Messages"));
        add(scroll, BorderLayout.CENTER);

        // Message input (bottom)
        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        messageField = new JTextField();
        sendButton = new JButton("Send");
        disconnectButton = new JButton("Disconnect");

        bottom.add(messageField, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.add(sendButton);
        btnPanel.add(disconnectButton);
        bottom.add(btnPanel, BorderLayout.EAST);

        add(bottom, BorderLayout.SOUTH);

        // Actions
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        disconnectButton.addActionListener(e -> disconnect());

        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 5000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Send username as first message
            out.println(username);

            appendToChat("✔ Connected to server as " + username + ". Waiting for assigned Client ID...\n");

            // Start reader thread to handle incoming messages (including ID assignment)
            new Thread(new IncomingReader(), "Incoming-Thread").start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Unable to connect to server: " + e.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    // Append text to chat area on EDT
    private void appendToChat(String text) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(text);
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    // Send a message typed by this client
    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (msg.isEmpty() || out == null) return;

        // Send to server
        out.println(msg);

        // Append locally because server will not echo sender's message
        String timestamp = timeFormat.format(new Date());
        String idPart = (clientId > 0) ? ("Client " + clientId) : "Me";
        appendToChat("[" + timestamp + "] " + idPart + " (" + username + "): " + msg + "\n");

        messageField.setText("");
        messageField.requestFocus();
    }

    // Disconnect gracefully
    private void disconnect() {
        try {
            appendToChat("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            appendToChat("✔ Disconnected from server.\n");
            if (socket != null && !socket.isClosed()) socket.close();
            messageField.setEnabled(false);
            sendButton.setEnabled(false);
            disconnectButton.setEnabled(false);
        } catch (IOException e) {
            appendToChat("⚠ Error while disconnecting: " + e.getMessage() + "\n");
        }
    }

    // Thread to read incoming messages from server
    private class IncomingReader implements Runnable {
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    // Special protocol: server sends "ID:<num>" to assign client id
                    if (line.startsWith("ID:")) {
                        try {
                            clientId = Integer.parseInt(line.substring(3).trim());
                            SwingUtilities.invokeLater(() -> setTitle("Chat Client - " + username + " (Client " + clientId + ")"));
                            appendToChat("✔ Assigned Client ID: " + clientId + "\n");
                        } catch (NumberFormatException ignored) {}
                        continue;
                    }

                    // Normal chat message: just display
                    appendToChat(line + "\n");
                }
            } catch (IOException e) {
                appendToChat("\n⚠ Connection lost.\n");
            } finally {
                try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
                SwingUtilities.invokeLater(() -> {
                    messageField.setEnabled(false);
                    sendButton.setEnabled(false);
                    disconnectButton.setEnabled(false);
                });
            }
        }
    }

    // Entry point
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClientGUI());
    }
}
