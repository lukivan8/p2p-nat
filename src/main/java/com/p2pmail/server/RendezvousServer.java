package com.p2pmail.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RendezvousServer {
    private static final Logger logger = LoggerFactory.getLogger(RendezvousServer.class);

    private DatagramSocket socket;
    private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public void start(int port) throws IOException {
        socket = new DatagramSocket(port);
        logger.info("Rendezvous server started on port {}", port);

        while (running) {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                handleMessage(message, packet.getAddress(), packet.getPort());
            } catch (IOException e) {
                if (running) {
                    logger.error("Error handling message", e);
                }
            }
        }
    }

    private void handleMessage(String message, InetAddress address, int port) throws IOException {
        logger.debug("Server received: {} from {}:{}", message, address, port);
        String[] parts = message.split("\\|");
        String messageType = parts[0];

        switch (messageType) {
            case "REGISTER":
                handleRegistration(parts, address, port);
                break;
            case "CONNECT":
                handleConnection(parts, address, port);
                break;
            default:
                logger.warn("Unknown message type: {}", messageType);
        }
    }

    private void handleRegistration(String[] parts, InetAddress address, int port) throws IOException {
        if (parts.length < 2) {
            logger.warn("Invalid registration message");
            return;
        }

        String clientId = parts[1];
        String endpoint = address.getHostAddress() + ":" + port;
        clients.put(clientId, new ClientInfo(endpoint, endpoint));

        sendResponse("REGISTERED|" + clientId, address, port);
        logger.info("Registered client: {} at {}", clientId, endpoint);
        logger.debug("Current clients: {}", clients.keySet());
    }

    private void handleConnection(String[] parts, InetAddress address, int port) throws IOException {
        if (parts.length < 3) {
            logger.warn("Invalid connect message");
            return;
        }

        String requestedPeerId = parts[1];
        String requestingPeerId = parts[2];
        logger.info("Connect request from {} to {}", requestingPeerId, requestedPeerId);

        ClientInfo peer = clients.get(requestedPeerId);
        if (peer != null) {
            // Отправляем информацию запрашивающему клиенту
            String response = "PEER_INFO|" + requestedPeerId + "|" + peer.getPublicEndpoint();
            sendResponse(response, address, port);
            logger.info("Sent peer info to requesting client: {}", response);

            // Отправляем информацию запрашиваемому клиенту
            ClientInfo requestingPeer = clients.get(requestingPeerId);
            if (requestingPeer != null) {
                String reverseResponse = "PEER_INFO|" + requestingPeerId + "|" + requestingPeer.getPublicEndpoint();
                String[] peerEndpoint = peer.getPublicEndpoint().split(":");
                InetAddress peerAddress = InetAddress.getByName(peerEndpoint[0]);
                int peerPort = Integer.parseInt(peerEndpoint[1]);
                sendResponse(reverseResponse, peerAddress, peerPort);
                logger.info("Sent reverse peer info: {}", reverseResponse);
            }
        } else {
            logger.warn("Requested peer {} not found", requestedPeerId);
        }
    }

    private void sendResponse(String message, InetAddress address, int port) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
        logger.debug("Server sent: {} to {}:{}", message, address, port);
    }

    public void shutdown() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    private static class ClientInfo {
        private final String publicEndpoint;
        private final String privateEndpoint;

        public ClientInfo(String publicEndpoint, String privateEndpoint) {
            this.publicEndpoint = publicEndpoint;
            this.privateEndpoint = privateEndpoint;
        }

        public String getPublicEndpoint() {
            return publicEndpoint;
        }
    }
}