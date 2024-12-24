package com.p2pmail.client;

public class PeerInfo {
    private String publicEndpoint;
    private String privateEndpoint;
    private String workingEndpoint;

    public PeerInfo(String publicEndpoint, String privateEndpoint) {
        this.publicEndpoint = publicEndpoint;
        this.privateEndpoint = privateEndpoint;
    }

    public String getPublicEndpoint() {
        return publicEndpoint;
    }

    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public String getPrivateEndpoint() {
        return privateEndpoint;
    }

    public void setPrivateEndpoint(String privateEndpoint) {
        this.privateEndpoint = privateEndpoint;
    }

    public String getWorkingEndpoint() {
        return workingEndpoint;
    }

    public void setWorkingEndpoint(String workingEndpoint) {
        this.workingEndpoint = workingEndpoint;
    }

    @Override
    public String toString() {
        return "PeerInfo{" +
                "publicEndpoint='" + publicEndpoint + '\'' +
                ", privateEndpoint='" + privateEndpoint + '\'' +
                ", workingEndpoint='" + workingEndpoint + '\'' +
                '}';
    }
}