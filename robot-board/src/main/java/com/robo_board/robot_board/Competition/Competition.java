package com.robo_board.robot_board.competition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.json.JsonObject;

public final class Competition {

    public enum Status {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public record Snapshot(
            UUID id,
            Status status,
            List<String> entrants,
            int totalGames,
            int completedGames,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            String failure,
            List<League.Standing> standings) {}

    private final UUID id = UUID.randomUUID();
    private final Instant createdAt = Instant.now();
    private final List<String> entrants;
    private final League league;
    private final List<List<List<String>>> gamePartitions;
    private final int totalGames;

    private Status status = Status.QUEUED;
    private Instant startedAt;
    private Instant completedAt;
    private String failure;
    private int completedGames;

    public Competition(
            List<String> entrants,
            int gameSize,
            int gamesPerEntrant,
            int broadcastCount) {
        if (entrants == null || entrants.isEmpty()) {
            throw new IllegalArgumentException("At least one entrant is required");
        }
        if (gameSize < 2 || gameSize > entrants.size()) {
            throw new IllegalArgumentException("Game size must be between 2 and entrant count");
        }
        if (gamesPerEntrant < 1) {
            throw new IllegalArgumentException("Games per entrant must be positive");
        }
        if (broadcastCount < 1) {
            throw new IllegalArgumentException("Broadcast count must be positive");
        }
        if (entrants.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("Entrant names must not be blank");
        }
        this.entrants = List.copyOf(entrants);
        this.league = League.Generate(
                new java.util.ArrayList<>(this.entrants),
                gameSize,
                gamesPerEntrant);
        this.gamePartitions = league.getGamePartitions(
                Math.min(broadcastCount, league.getGameCount()));
        this.totalGames = gamePartitions.stream().mapToInt(List::size).sum();
    }

    public UUID getId() {
        return id;
    }

    synchronized void markRunning() {
        if (status != Status.QUEUED) {
            throw new IllegalStateException("Only a queued competition can start");
        }
        status = Status.RUNNING;
        startedAt = Instant.now();
    }

    synchronized void recordBattleResults(JsonObject response) {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Results can only be recorded for a running competition");
        }
        league.receiveResponseFromGameDriver(response);
        completedGames++;
    }

    synchronized void markCompleted() {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Only a running competition can complete");
        }
        status = Status.COMPLETED;
        completedAt = Instant.now();
    }

    synchronized void markFailed(String reason) {
        status = Status.FAILED;
        failure = reason;
        completedAt = Instant.now();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                id,
                status,
                entrants,
                totalGames,
                completedGames,
                createdAt,
                startedAt,
                completedAt,
                failure,
                league.getStandings());
    }

    List<List<List<String>>> gamePartitions() {
        return gamePartitions;
    }

    void printStandings() {
        league.printStatsTable();
    }
}
