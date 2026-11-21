package com.robo_board.robot_board.events;

import javax.json.JsonObject;

public record CompetitionEvent(
    String eventType,
    String data
) {
    public CompetitionEvent(String eventType, JsonObject data) {
        this(eventType, data.toString());
    }

    public String getData() {
        return data;
    }

    public String getEventType() {
        return eventType;
    }
}
