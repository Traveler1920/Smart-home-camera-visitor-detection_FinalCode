package com.smartcamera.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class CameraUI extends Application {

    private static final String BASE_URL = "http://localhost:8080";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Stage primaryStage;
    private Scene loginScene;
    private Scene signupScene;
    private Scene dashboardScene;

    private String authToken;
    private String currentUserEmail;
    private String latestEventsJson = "[]";

    private Label loginStatusLabel = new Label();
    private Label signupStatusLabel = new Label();
    private Label dashboardStatusLabel = new Label();
    private ImageView eventImageView = new ImageView();
    private TextArea outputArea = new TextArea();

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Smart Home Camera - Authentication & Visitor Capture");

        initLoginScene();
        initSignupScene();
        initDashboardScene();

        primaryStage.setScene(loginScene);
        primaryStage.show();
    }

    private void initLoginScene() {
        VBox root = new VBox(12);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));

        Label title = new Label("Resident Login");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        TextField emailField = new TextField();
        emailField.setPromptText("Email Address");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        TextField pinField = new TextField();
        pinField.setPromptText("4-Digit PIN (from server console)");

        Button loginButton = new Button("Log In");
        loginButton.setDefaultButton(true);

        Hyperlink switchToSignup = new Hyperlink("Don't have an account? Sign up");

        loginButton.setOnAction(e -> {
            String email = emailField.getText().trim();
            String password = passwordField.getText();
            String pin = pinField.getText().trim();

            if (email.isEmpty() || password.isEmpty() || pin.isEmpty()) {
                setStatus(loginStatusLabel, "Please enter email, password, and PIN.", true);
                return;
            }

            setStatus(loginStatusLabel, "Signing in...", false);
            loginButton.setDisable(true);

            Task<String> loginTask = new Task<>() {
                @Override
                protected String call() throws Exception {
                    Map<String, String> payload = new LinkedHashMap<>();
                    payload.put("email", email);
                    payload.put("password", password);
                    payload.put("pin", pin);

                    String json = objectMapper.writeValueAsString(payload);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/login"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        JsonNode rootNode = objectMapper.readTree(response.body());
                        JsonNode tokenNode = rootNode.get("token");
                        if (tokenNode == null || tokenNode.asText().isBlank()) {
                            throw new RuntimeException("Login succeeded but no token was returned.");
                        }
                        return tokenNode.asText();
                    }
                    throw new RuntimeException("Login failed (" + response.statusCode() + "): " + response.body());
                }
            };

            loginTask.setOnSucceeded(ev -> {
                loginButton.setDisable(false);
                this.authToken = loginTask.getValue();
                System.out.println("JWT_TOKEN=" + this.authToken);
                this.currentUserEmail = email;
                setStatus(loginStatusLabel, "Login successful!", false);
                pinField.clear();
                primaryStage.setScene(dashboardScene);
                loadEvents();
            });

            loginTask.setOnFailed(ev -> {
                loginButton.setDisable(false);
                Throwable ex = loginTask.getException();
                setStatus(loginStatusLabel, ex != null ? ex.getMessage() : "Login error.", true);
            });

            Thread thread = new Thread(loginTask);
            thread.setDaemon(true);
            thread.start();
        });

        switchToSignup.setOnAction(e -> {
            setStatus(signupStatusLabel, "", false);
            primaryStage.setScene(signupScene);
        });

        root.getChildren().addAll(
                title, emailField, passwordField, pinField,
                loginButton, loginStatusLabel, switchToSignup
        );
        loginScene = new Scene(root, 400, 450);
    }

    private void initSignupScene() {
        VBox root = new VBox(12);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));

        Label title = new Label("Resident Registration");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");

        TextField emailField = new TextField();
        emailField.setPromptText("Email Address");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm Password");

        TextField phoneField = new TextField();
        phoneField.setPromptText("Phone Number");

        Button signupButton = new Button("Register Account");
        signupButton.setDefaultButton(true);

        Hyperlink switchToLogin = new Hyperlink("Already have an account? Log in");

        signupButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String email = emailField.getText().trim();
            String password = passwordField.getText();
            String confirmPassword = confirmPasswordField.getText();
            String phone = phoneField.getText().trim();

            if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || phone.isEmpty()) {
                setStatus(signupStatusLabel, "All fields are required.", true);
                return;
            }
            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                setStatus(signupStatusLabel, "Invalid email format.", true);
                return;
            }
            if (password.length() < 8) {
                setStatus(signupStatusLabel, "Password must be at least 8 characters long.", true);
                return;
            }
            if (!password.equals(confirmPassword)) {
                setStatus(signupStatusLabel, "Passwords do not match.", true);
                return;
            }
            if (!phone.matches("^[0-9+\\-\\s()]{8,15}$")) {
                setStatus(signupStatusLabel, "Please enter a valid phone number.", true);
                return;
            }

            setStatus(signupStatusLabel, "Registering account...", false);
            signupButton.setDisable(true);

            Task<String> registerTask = new Task<>() {
                @Override
                protected String call() throws Exception {
                    Map<String, String> payload = new LinkedHashMap<>();
                    payload.put("username", username);
                    payload.put("email", email);
                    payload.put("password", password);
                    payload.put("phone", phone);

                    String json = objectMapper.writeValueAsString(payload);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/register"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response.body();
                    }
                    throw new RuntimeException("Registration failed (" + response.statusCode() + "): " + response.body());
                }
            };

            registerTask.setOnSucceeded(ev -> {
                signupButton.setDisable(false);
                setStatus(
                        signupStatusLabel,
                        "Registered! Check the backend server console for your 4-digit login PIN.",
                        false
                );
            });

            registerTask.setOnFailed(ev -> {
                signupButton.setDisable(false);
                Throwable ex = registerTask.getException();
                setStatus(signupStatusLabel, ex != null ? ex.getMessage() : "Registration error.", true);
            });

            Thread thread = new Thread(registerTask);
            thread.setDaemon(true);
            thread.start();
        });

        switchToLogin.setOnAction(e -> {
            setStatus(loginStatusLabel, "", false);
            primaryStage.setScene(loginScene);
        });

        root.getChildren().addAll(
                title, usernameField, emailField, passwordField, confirmPasswordField,
                phoneField, signupButton, signupStatusLabel, switchToLogin
        );
        signupScene = new Scene(root, 400, 540);
    }

    private void initDashboardScene() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(20));

        Label title = new Label("Visitor Camera & Account Events");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Button refreshEventsButton = new Button("Display Latest Event");
        Button viewAllFootageButton = new Button("View All Footage");
        Button logoutButton = new Button("Log Out");

        HBox actionBar = new HBox(10, refreshEventsButton, viewAllFootageButton, logoutButton);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        eventImageView.setFitWidth(360);
        eventImageView.setFitHeight(220);
        eventImageView.setPreserveRatio(true);

        outputArea.setEditable(false);
        outputArea.setPrefHeight(160);

        refreshEventsButton.setOnAction(e -> loadEvents());
        viewAllFootageButton.setOnAction(e -> openAllFootageWindow());
        logoutButton.setOnAction(e -> {
            this.authToken = null;
            this.currentUserEmail = null;
            this.latestEventsJson = "[]";
            eventImageView.setImage(null);
            outputArea.clear();
            setStatus(loginStatusLabel, "Logged out.", false);
            primaryStage.setScene(loginScene);
        });

        root.getChildren().addAll(
                title, actionBar, dashboardStatusLabel, eventImageView, outputArea
        );
        dashboardScene = new Scene(root, 640, 540);
    }

    private void openAllFootageWindow() {
        Stage galleryStage = new Stage();
        galleryStage.setTitle("All Captured Visitor Footage");

        VBox galleryList = new VBox(16);
        galleryList.setPadding(new Insets(16));

        try {
            JsonNode rootNode = objectMapper.readTree(latestEventsJson);
            if (!rootNode.isArray() || rootNode.size() == 0) {
                galleryList.getChildren().add(new Label("No visitor footage available."));
            } else {
                for (int i = rootNode.size() - 1; i >= 0; i--) {
                    JsonNode item = rootNode.get(i);
                    VBox card = new VBox(6);
                    card.setPadding(new Insets(10));
                    card.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 6; -fx-background-radius: 6;");

                    String eventId = item.path("eventId").asText("?");
                    String cameraId = item.path("cameraId").asText("Unknown");
                    String captureTime = item.path("captureTime").asText("Unknown time");
                    String location = item.path("location").asText("Unknown location");
                    String mediaUrl = item.path("mediaUrl").asText("");

                    Label metaLabel = new Label(
                            "Event #" + eventId + " | " + cameraId + " | " + location + " | " + captureTime
                    );
                    metaLabel.setStyle("-fx-font-weight: bold;");

                    card.getChildren().add(metaLabel);

                    if (!mediaUrl.isBlank()) {
                        Path imagePath = Path.of(mediaUrl);
                        if (Files.exists(imagePath)) {
                            ImageView thumbView = new ImageView(new Image(imagePath.toUri().toString(), true));
                            thumbView.setFitWidth(420);
                            thumbView.setFitHeight(250);
                            thumbView.setPreserveRatio(true);
                            card.getChildren().add(thumbView);
                        } else {
                            card.getChildren().add(new Label("Image file not found: " + mediaUrl));
                        }
                    }

                    galleryList.getChildren().add(card);
                }
            }
        } catch (Exception ex) {
            galleryList.getChildren().add(new Label("Unable to load footage list: " + ex.getMessage()));
        }

        ScrollPane scrollPane = new ScrollPane(galleryList);
        scrollPane.setFitToWidth(true);

        galleryStage.setScene(new Scene(scrollPane, 480, 600));
        galleryStage.show();
    }

    private void updateEventPreviewFromJson(String jsonText) {
        Platform.runLater(() -> {
            try {
                JsonNode rootNode = objectMapper.readTree(jsonText);
                JsonNode targetEvent = null;

                if (rootNode.isArray() && rootNode.size() > 0) {
                    targetEvent = rootNode.get(rootNode.size() - 1);
                } else if (rootNode.isObject()) {
                    targetEvent = rootNode;
                }

                if (targetEvent != null && targetEvent.hasNonNull("mediaUrl")) {
                    String mediaUrl = targetEvent.get("mediaUrl").asText();
                    Path imagePath = Path.of(mediaUrl);
                    if (Files.exists(imagePath)) {
                        eventImageView.setImage(new Image(imagePath.toUri().toString(), true));
                        return;
                    }
                }
                eventImageView.setImage(null);
            } catch (Exception ex) {
                eventImageView.setImage(null);
            }
        });
    }

    private void loadEvents() {
        if (authToken == null || authToken.isBlank()) {
            setStatus(dashboardStatusLabel, "Missing auth token. Please log in first.", true);
            return;
        }

        setStatus(dashboardStatusLabel, "Loading events for " + currentUserEmail + "...", false);

        Task<String> eventsTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/events"))
                        .header("Authorization", "Bearer " + authToken)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                throw new RuntimeException("Events request failed (" + response.statusCode() + "): " + response.body());
            }
        };

        eventsTask.setOnSucceeded(ev -> {
            String body = eventsTask.getValue();
            this.latestEventsJson = body;
            setStatus(dashboardStatusLabel, "Authorized & loaded events.", false);
            outputArea.setText(body);
            updateEventPreviewFromJson(body);
        });

        eventsTask.setOnFailed(ev -> {
            Throwable ex = eventsTask.getException();
            setStatus(dashboardStatusLabel, ex != null ? ex.getMessage() : "Failed to load events.", true);
        });

        Thread thread = new Thread(eventsTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void setStatus(Label label, String text, boolean isError) {
        Platform.runLater(() -> {
            label.setText(text);
            label.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}