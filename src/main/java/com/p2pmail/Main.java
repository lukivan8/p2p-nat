package com.p2pmail;

import com.p2pmail.client.P2PClient;
import com.p2pmail.server.RendezvousServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                System.out.println("Usage: java -jar p2p-nat.jar [server|client1|client2]");
                return;
            }

            switch (args[0]) {
                case "server":
                    startServer();
                    break;
                case "client1":
                    startClient("Client1", 54321);
                    break;
                case "client2":
                    startClient("Client2", 54322);
                    break;
                default:
                    System.out.println("Unknown command: " + args[0]);
            }
        } catch (Exception e) {
            logger.error("Error in main", e);
        }
    }

    private static void startServer() throws Exception {
        logger.info("Starting server on port 12345...");
        RendezvousServer server = new RendezvousServer();
        server.start(12345);
    }

    private static void startClient(String clientId, int localPort) throws Exception {
        String serverHost = "localhost"; // Замените на ваш IP сервера
        logger.info("Starting {} on port {}", clientId, localPort);

        P2PClient client = new P2PClient(clientId, localPort, serverHost, 12345);
        client.start();

        String peerId = clientId.equals("Client1") ? "Client2" : "Client1";
        logger.info("Connecting to {}", peerId);
        client.connectToPeer(peerId);

        // Чтение сообщений с консоли
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        logger.info("Enter messages (type 'exit' to quit):");

        String line;
        while ((line = consoleReader.readLine()) != null) {
            if (line.equals("exit")) {
                break;
            }
            client.sendMessage(peerId, line);
        }
    }
}