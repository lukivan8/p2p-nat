package com.p2pmail.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class P2PClient {
    private static final Logger logger = LoggerFactory.getLogger(P2PClient.class);

    private final String clientId;
    private final int localPort;
    private final String serverHost;
    private final int serverPort;
    private DatagramSocket socket;
    private Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public P2PClient(String clientId, int localPort, String serverHost, int serverPort) {
        this.clientId = clientId;
        this.localPort = localPort;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public void start() throws IOException {
        socket = new DatagramSocket(localPort);
        registerWithServer();
        startReceiving();
    }

    private void registerWithServer() throws IOException {
        String regMessage = "REGISTER|" + clientId;
        sendToServer(regMessage);
        logger.info("Sent registration: {}", regMessage);

        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        String response = new String(packet.getData(), 0, packet.getLength());
        logger.info("Registration response: {}", response);
    }

    public void connectToPeer(String peerId) throws IOException {
        String connRequest = "CONNECT|" + peerId + "|" + clientId;
        sendToServer(connRequest);
        logger.info("Sent connect request: {}", connRequest);
    }

    public void sendMessage(String peerId, String message) throws IOException {
        PeerInfo peer = peers.get(peerId);
        if (peer == null) {
            logger.warn("Peer not connected: {}", peerId);
            return;
        }

        if (peer.getWorkingEndpoint() == null) {
            logger.warn("Connection not established yet with: {}", peerId);
            return;
        }

        String[] endpointParts = peer.getWorkingEndpoint().split(":");
        InetAddress address = InetAddress.getByName(endpointParts[0]);
        int port = Integer.parseInt(endpointParts[1]);

        String fullMessage = "MESSAGE|" + clientId + "|" + message;
        sendToAddress(fullMessage, address, port);
        logger.info("Sent message to {}: {}", peerId, message);
    }

    private void startReceiving() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    handleMessage(message, packet.getAddress(), packet.getPort());
                } catch (IOException e) {
                    if (running) {
                        logger.error("Error receiving message", e);
                    }
                }
            }
        }).start();
    }

    private void handleMessage(String message, InetAddress address, int port) throws IOException {
        logger.debug("Client {} received: {} from {}:{}", clientId, message, address, port);
        String[] parts = message.split("\\|");

        switch (parts[0]) {
            case "PEER_INFO":
                if (parts.length >= 3) {
                    String peerId = parts[1];
                    String peerEndpoint = parts[2];
                    logger.info("Got peer info: {} at {}", peerId, peerEndpoint);

                    PeerInfo peerInfo = new PeerInfo(peerEndpoint, peerEndpoint);
                    peerInfo.setWorkingEndpoint(peerEndpoint);
                    peers.put(peerId, peerInfo);

                    startHolePunching(peerId);
                }
                break;

            case "PUNCH":
                if (parts.length >= 2) {
                    String peerId = parts[1];
                    logger.info("Got punch from: {}", peerId);

                    String endpoint = address.getHostAddress() + ":" + port;
                    peers.putIfAbsent(peerId, new PeerInfo(endpoint, endpoint));
                    peers.get(peerId).setWorkingEndpoint(endpoint);

                    String response = "PUNCH_ACK|" + clientId;
                    sendToAddress(response, address, port);
                    logger.info("Sent punch ack to {}", peerId);
                }
                break;

            case "PUNCH_ACK":
                if (parts.length >= 2) {
                    String peerId = parts[1];
                    logger.info("Got punch ack from: {}", peerId);

                    String endpoint = address.getHostAddress() + ":" + port;
                    if (peers.containsKey(peerId)) {
                        peers.get(peerId).setWorkingEndpoint(endpoint);
                        logger.info("Updated working endpoint for {}", peerId);
                    } else {
                        PeerInfo peerInfo = new PeerInfo(endpoint, endpoint);
                        peerInfo.setWorkingEndpoint(endpoint);
                        peers.put(peerId, peerInfo);
                        logger.info("Created new peer info for {}", peerId);
                    }
                }
                break;

            case "MESSAGE":
                if (parts.length >= 3) {
                    logger.info("{}: {}", parts[1], parts[2]);
                }
                break;
        }
    }

    private void startHolePunching(String peerId) throws IOException {
        PeerInfo peer = peers.get(peerId);
        if (peer == null) {
            logger.warn("No peer info for: {}", peerId);
            return;
        }

        logger.info("Starting hole punch to: {} at {}", peerId, peer.getPublicEndpoint());
        String[] endpointParts = peer.getPublicEndpoint().split(":");
        InetAddress address = InetAddress.getByName(endpointParts[0]);
        int port = Integer.parseInt(endpointParts[1]);

        String message = "PUNCH|" + clientId;
        sendToAddress(message, address, port);
    }

    private void sendToServer(String message) throws IOException {
        sendToAddress(message, InetAddress.getByName(serverHost), serverPort);
    }

    private void sendToAddress(String message, InetAddress address, int port) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }

    public void shutdown() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}