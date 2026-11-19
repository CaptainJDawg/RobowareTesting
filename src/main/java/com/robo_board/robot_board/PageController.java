package com.robo_board.robot_board;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;

import com.robo_board.robot_board.services.SseService;
 

@RestController
public class PageController {

    private final SseService eventService;

    public PageController(SseService eventService) {
        this.eventService = eventService;
    }

    @GetMapping(
        value = "/events",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter subscribe() {
        return eventService.subscribe();
    }
}
