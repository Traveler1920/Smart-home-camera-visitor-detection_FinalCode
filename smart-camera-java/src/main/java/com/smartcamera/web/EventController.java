package com.smartcamera.web;

import com.smartcamera.model.Event;
import com.smartcamera.repository.EventRepository;
import com.smartcamera.security.CurrentUserService;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository events;
    private final CurrentUserService current;

    public EventController(EventRepository events, CurrentUserService current) {
        this.events = events;
        this.current = current;
    }

    @PostMapping
    @PreAuthorize("hasRole('RESIDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createFromSnapshot(
            @RequestParam("file") MultipartFile file,
            Authentication auth) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Snapshot file is required");
        }

        var owner = current.requireCurrentUser(auth);

        Path uploadDir = Paths.get("uploads");
        Files.createDirectories(uploadDir);

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "snapshot.png";
        String safeName = UUID.randomUUID() + "_" + Paths.get(originalName).getFileName();
        Path targetFile = uploadDir.resolve(safeName);
        file.transferTo(targetFile);

        Event event = new Event();
        event.setCameraId("OBS-CAM-01");
        event.setLocation("Front Door");
        event.setMediaUrl(targetFile.toAbsolutePath().toString());
        event.setOwner(owner);

        Event saved = events.save(event);
        return EventResponse.from(saved);
    }

    @GetMapping
    @PreAuthorize("hasRole('RESIDENT')")
    public List<EventResponse> list(Authentication auth) {
        return events.findByOwner_UserId(current.requireCurrentUser(auth).getUserId())
                .stream().map(EventResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('RESIDENT') and @eventAuthorization.isOwner(#id, authentication)")
    public EventResponse get(@PathVariable("id") Integer id) {
        return events.findById(id).map(EventResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/{id}/share")
    @PreAuthorize("hasRole('RESIDENT') and @eventAuthorization.isOwner(#id, authentication)")
    public void share(@PathVariable("id") Integer id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("denyAll()")
    public void delete(@PathVariable("id") Integer id) {}

    @PutMapping("/{id}")
    @PreAuthorize("denyAll()")
    public void update(@PathVariable("id") Integer id) {}
}