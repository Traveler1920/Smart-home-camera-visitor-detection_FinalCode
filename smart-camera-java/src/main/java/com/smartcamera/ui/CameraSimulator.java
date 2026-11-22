package com.smartcamera.ui;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

public class CameraSimulator {

    private static final String BASE_URL = "http://localhost:8080";
    private static final Path WATCH_DIR = Paths.get("C:/Users/alqha/Videos/CamSim");
    private static final Path PROCESSED_DIR = WATCH_DIR.resolve("processed");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String bearerToken;

    public CameraSimulator(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public void startWatching() throws IOException {
        Files.createDirectories(WATCH_DIR);
        Files.createDirectories(PROCESSED_DIR);

        System.out.println("Watching folder for visitor snapshots: " + WATCH_DIR.toAbsolutePath());

        while (!Thread.currentThread().isInterrupted()) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(WATCH_DIR)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file) && isImage(file)) {
                        uploadSnapshot(file);
                    }
                }
            } catch (Exception e) {
                System.err.println("Simulator error: " + e.getMessage());
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private boolean isImage(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private void uploadSnapshot(Path imagePath) throws Exception {
        String boundary = "SmartCamBoundary-" + UUID.randomUUID();
        byte[] fileBytes = Files.readAllBytes(imagePath);

        String partHeader = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + imagePath.getFileName() + "\"\r\n"
                + "Content-Type: " + probeContentType(imagePath) + "\r\n\r\n";
        String partFooter = "\r\n--" + boundary + "--\r\n";

        byte[] headerBytes = partHeader.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = partFooter.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];

        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/events"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));

        if (bearerToken != null && !bearerToken.isBlank()) {
            reqBuilder.header("Authorization", "Bearer " + bearerToken);
        }

        HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        System.out.println("Uploaded " + imagePath.getFileName() + " -> HTTP " + response.statusCode());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Files.move(imagePath, PROCESSED_DIR.resolve(imagePath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } else {
            System.err.println("Response body: " + response.body());
        }
    }

    private String probeContentType(Path path) {
        String lower = path.getFileName().toString().toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    public static void main(String[] args) throws Exception {
        
        String token = args.length > 0 ? args[0] : "JW-Token";
        new CameraSimulator(token).startWatching();
    }
}