package com.robo_board.robot_board.competition;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonReader;

import robocode.BattleResults;

/** One child JVM and its fixed schedule. Static methods coordinate multiple runners. */
public final class RobocodeEngineRunner {
    private static final Path LOG_DIRECTORY = Path.of("logs");

    private final int id;
    private final File robocodeHome;
    private final List<List<String>> schedule;
    private Process process;
    private boolean claimed;
    private boolean cancelled;

    public RobocodeEngineRunner(int id, File robocodeHome, List<List<String>> schedule) {
        if (id < 1 || schedule.isEmpty() || schedule.stream().anyMatch(List::isEmpty)) {
            throw new IllegalArgumentException("A runner needs a positive ID and non-empty games");
        }
        this.id = id;
        this.robocodeHome = Objects.requireNonNull(robocodeHome).getAbsoluteFile();
        this.schedule = schedule.stream().map(List::copyOf).toList();
    }

    public int getScheduledGameCount() {
        return schedule.size();
    }

    /** Balanced fixed batches. Never creates a JVM for an empty batch. */
    public static List<List<List<String>>> partitionSchedule(List<List<String>> schedule, int runnerCount) {
        if (runnerCount < 1) {
            throw new IllegalArgumentException("Runner count must be positive");
        }
        List<List<String>> snapshot = schedule.stream().map(List::copyOf).toList();
        int count = Math.min(runnerCount, snapshot.size());
        List<List<List<String>>> batches = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int start = (int) ((long) snapshot.size() * i / count);
            int end = (int) ((long) snapshot.size() * (i + 1) / count);
            batches.add(List.copyOf(snapshot.subList(start, end)));
        }
        return List.copyOf(batches);
    }

    /** Results are delivered per battle, serially on the calling thread. */
    public static void runAll(List<RobocodeEngineRunner> runners,
            Consumer<List<BattleResults>> onBattleCompleted) {
        Objects.requireNonNull(onBattleCompleted);
        List<RobocodeEngineRunner> batch = List.copyOf(runners);
        HashSet<Integer> ids = new HashSet<>();
        for (RobocodeEngineRunner runner : batch) {
            if (!ids.add(runner.id)) {
                throw new IllegalArgumentException("Runner IDs must be unique within a run");
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        for (RobocodeEngineRunner runner : batch) {
            runner.claim();
        }
        BlockingQueue<RunnerEvent> events = new LinkedBlockingQueue<>();
        ExecutorService workers = Executors.newFixedThreadPool(batch.size());
        try {
            for (RobocodeEngineRunner runner : batch) {
                workers.submit(() -> {
                    try {
                        runner.execute(events);
                        events.add(new RunnerEvent(null, null));
                    } catch (Exception failure) {
                        events.add(new RunnerEvent(null, failure));
                    }
                });
            }
            int finished = 0;
            while (finished < batch.size()) {
                RunnerEvent event = events.take();
                if (event.failure() != null) {
                    throw new IllegalStateException("Robocode runner failed", event.failure());
                }
                if (event.results() == null) {
                    finished++;
                } else {
                    onBattleCompleted.accept(event.results());
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running battles", interrupted);
        } finally {
            batch.forEach(RobocodeEngineRunner::cancel);
            workers.shutdownNow();
        }
    }

    private synchronized void claim() {
        if (claimed) {
            throw new IllegalStateException("Runner " + id + " has already been used");
        }
        claimed = true;
    }

    private synchronized Process launch(Path scheduleFile) throws IOException {
        if (cancelled) {
            throw new IllegalStateException("Runner " + id + " was cancelled");
        }
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", childClasspath(), GameDriver.class.getName(),
                Integer.toString(id), robocodeHome.toString(), scheduleFile.toString())
                .redirectErrorStream(true).start();
        return process;
    }

    /** Resolve Gradle's runtime dependencies to ordinary jar/class paths for the child JVM. */
    private static String childClasspath() throws IOException {
        String resolved = System.getProperty("competition.childClasspath");
        if (resolved == null || resolved.isBlank()) {
            resolved = System.getProperty("java.class.path");
        }
        return resolved;
    }

    private void execute(BlockingQueue<RunnerEvent> events) throws IOException, InterruptedException {
        Files.createDirectories(LOG_DIRECTORY);
        Path scheduleFile = Files.createTempFile("robocode-schedule-", ".json");
        try {
            JsonArrayBuilder games = Json.createArrayBuilder();
            for (List<String> game : schedule) {
                JsonArrayBuilder robots = Json.createArrayBuilder();
                game.forEach(robots::add);
                games.add(robots);
            }
            Files.writeString(scheduleFile, games.build().toString(), StandardCharsets.UTF_8);
            Process child = launch(scheduleFile);
            int completed = 0;
            try (
                BufferedReader reader = child.inputReader(StandardCharsets.UTF_8);
                BufferedWriter log = Files.newBufferedWriter(
                    LOG_DIRECTORY.resolve("runner-" + id + ".log"),
                    StandardCharsets.UTF_8
                )
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.write(line);
                    log.newLine();
                    log.flush();
                    if (line.stripLeading().startsWith("{")) {
                        try (JsonReader json = Json.createReader(new StringReader(line))) {
                            JsonObject response = json.readObject();
                            if (!"battleResults".equals(response.getString("responseType"))) {
                                throw new IllegalStateException("Unexpected response from runner " + id);
                            }
                            if (++completed > schedule.size()) {
                                throw new IllegalStateException("Too many battle results from runner " + id);
                            }
                            List<BattleResults> results = decodeResults(response);
                            if (results.size() != schedule.get(completed - 1).size()) {
                                throw new IllegalStateException("Incomplete battle result from runner " + id);
                            }
                            events.add(new RunnerEvent(results, null));
                        }
                    } else {
                        System.out.printf("[Runner %d] %s%n", id, line);
                    }
                }
            }
            int exitCode = child.waitFor();
            if (exitCode != 0 || completed != schedule.size()) {
                throw new IllegalStateException("Runner " + id + " exited with code " + exitCode
                        + "; received " + completed + " of " + schedule.size() + " battle results");
            }
        } finally {
            cancel();
            Files.deleteIfExists(scheduleFile);
        }
    }

    private static List<BattleResults> decodeResults(JsonObject response) {
        List<BattleResults> results = new ArrayList<>();
        for (JsonObject result : response.getJsonArray("sortedResults").getValuesAs(JsonObject.class)) {
            results.add(new BattleResults(result.getString("teamLeaderName"), result.getInt("rank"),
                    result.getJsonNumber("score").doubleValue(),
                    result.getJsonNumber("survival").doubleValue(),
                    result.getJsonNumber("lastSurvivorBonus").doubleValue(),
                    result.getJsonNumber("bulletDamage").doubleValue(),
                    result.getJsonNumber("bulletDamageBonus").doubleValue(),
                    result.getJsonNumber("ramDamage").doubleValue(),
                    result.getJsonNumber("ramDamageBonus").doubleValue(),
                    result.getInt("firsts"), result.getInt("seconds"), result.getInt("thirds")));
        }
        return List.copyOf(results);
    }

    private synchronized void cancel() {
        cancelled = true;
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
        if (process != null) {
            boolean interrupted = false;
            while (true) {
                try {
                    process.waitFor();
                    break;
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private record RunnerEvent(List<BattleResults> results, Exception failure) { }
}
