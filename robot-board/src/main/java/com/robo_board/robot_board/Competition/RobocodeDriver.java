
package com.robo_board.robot_board.competition;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;

import robocode.BattleResults;
import robocode.BattleRules;
import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotSpecification;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;

public class RobocodeDriver {

    private RobocodeEngine engine;
    private List<List<String>> gameSchedule;


    public static void runConcurrentGames(
            List<List<List<String>>> partitions,
            File robocodeHome,
            File logDirectory,
            Consumer<JsonObject> responseHandler) {
        if (partitions.isEmpty()) {
            throw new IllegalArgumentException("At least one game partition is required");
        }
        if (!robocodeHome.isDirectory()) {
            throw new IllegalArgumentException(
                    "Robocode not found: " + robocodeHome.getAbsolutePath());
        }

        try {
            Files.createDirectories(logDirectory.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Could not create log directory: " + logDirectory, e);
        }

        ExecutorService logReaders = Executors.newFixedThreadPool(partitions.size());
        List<Future<?>> logTasks = new ArrayList<>();
        List<Process> processes = new ArrayList<>();
        try {
            for (int i = 0; i < partitions.size(); i++) {
                int gameId = i + 1;
                Process process = createGameProcess(gameId, robocodeHome, partitions.get(i)).start();
                processes.add(process);
                logTasks.add(logReaders.submit(
                        () -> streamOutput(process, gameId, logDirectory, responseHandler)));
            }

            int failedGames = 0;
            for (int i = 0; i < processes.size(); i++) {
                int exitCode = processes.get(i).waitFor();
                System.out.printf("Game Driver %d completed%n", i + 1);
                if (exitCode != 0) {
                    System.err.printf("Game %d exited with code %d%n", i + 1, exitCode);
                    failedGames++;
                }
            }
            for (Future<?> logTask : logTasks) {
                logTask.get();
            }
            if (failedGames > 0) {
                throw new IllegalStateException(failedGames + " game process(es) failed");
            }
        } catch (IOException e) {
            processes.forEach(Process::destroyForcibly);
            throw new IllegalStateException("Could not start a game process", e);
        } catch (InterruptedException e) {
            processes.forEach(Process::destroyForcibly);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for game processes", e);
        } catch (ExecutionException e) {
            processes.forEach(Process::destroyForcibly);
            throw new IllegalStateException("Could not read game process output", e.getCause());
        } finally {
            logReaders.shutdownNow();
        }
    }

    private static ProcessBuilder createGameProcess(
            int gameId,
            File robocodeHome,
            List<List<String>> partition) {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe"
                : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        ProcessBuilder builder = new ProcessBuilder(
                java,
                "-cp",
                System.getProperty("java.class.path"),
                RobocodeDriver.class.getName(),
                Integer.toString(gameId),
                robocodeHome.getAbsolutePath(),
                partition.toString());
        builder.redirectErrorStream(true);
        return builder;
    }

    private static void streamOutput(
            Process process,
            int gameId,
            File logDirectory,
            Consumer<JsonObject> responseHandler) {
        File logFile = new File(logDirectory, "game-" + gameId + ".log");
        try (
            BufferedReader reader = process.inputReader(StandardCharsets.UTF_8);
            BufferedWriter writer = Files.newBufferedWriter(
                    logFile.toPath(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("{")) {
                    try (JsonReader jsonReader = Json.createReader(new StringReader(line))) {
                        responseHandler.accept(jsonReader.readObject());
                    } catch (JsonException e) {
                        System.err.printf("Could not parse game %d JSON: %s%n", gameId, e.getMessage());
                    }
                } else {
                    System.out.printf("[Game %d] %s%n", gameId, line);
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                }
            }
        } catch (IOException e) {
            process.destroyForcibly();
            throw new UncheckedIOException("Failed to stream output for game " + gameId, e);
        }
    }

    private static String battleCompletedEventToJson(BattleCompletedEvent event) {
        BattleRules rules = event.getBattleRules();
        JsonObject battleRules = Json.createObjectBuilder()
                .add("battlefieldWidth", rules.getBattlefieldWidth())
                .add("battlefieldHeight", rules.getBattlefieldHeight())
                .add("numRounds", rules.getNumRounds())
                .add("gunCoolingRate", rules.getGunCoolingRate())
                .add("inactivityTime", rules.getInactivityTime())
                .add("hideEnemyNames", rules.getHideEnemyNames())
                .add("sentryBorderSize", rules.getSentryBorderSize())
                .build();

        return Json.createObjectBuilder()
                .add("responseType", "battleResults")
                .add("battleRules", battleRules)
                .add("sortedResults", battleResultsToJson(event.getSortedResults()))
                .build()
                .toString();
    }

    private static JsonArrayBuilder battleResultsToJson(BattleResults[] results) {
        JsonArrayBuilder jsonResults = Json.createArrayBuilder();
        for (BattleResults result : results) {
            JsonObjectBuilder jsonResult = Json.createObjectBuilder();
            if (result.getTeamLeaderName() == null) {
                jsonResult.addNull("teamLeaderName");
            } else {
                jsonResult.add("teamLeaderName", result.getTeamLeaderName());
            }
            jsonResults.add(jsonResult
                    .add("rank", result.getRank())
                    .add("score", result.getScore())
                    .add("survival", result.getSurvival())
                    .add("lastSurvivorBonus", result.getLastSurvivorBonus())
                    .add("bulletDamage", result.getBulletDamage())
                    .add("bulletDamageBonus", result.getBulletDamageBonus())
                    .add("ramDamage", result.getRamDamage())
                    .add("ramDamageBonus", result.getRamDamageBonus())
                    .add("firsts", result.getFirsts())
                    .add("seconds", result.getSeconds())
                    .add("thirds", result.getThirds()));
        }
        return jsonResults;
    }

    public static void main(String[] args) {// args: [id, robocodehome, schedule]
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected game id, Robocode home, and game schedule");
        }
        int gameId = Integer.parseInt(args[0]);
        File robocodeHome = new File(args[1]);

        System.out.println(args[2]);

        if (!robocodeHome.isDirectory()) {
            throw new IllegalStateException(
                    "Robocode not found: " + robocodeHome.getAbsolutePath());
        }

        System.setProperty("java.awt.headless", "false");
        System.setProperty("apple.awt.UIElement", "false");
        System.out.println("Starting game " + gameId);
        new RobocodeDriver(robocodeHome, parseSchedule(args[2]));
        System.out.println("Game " + gameId + " finished");

        System.exit(0);
    }

    private static List<List<String>> parseSchedule(String arg) {
        String[] splitIntoGames = arg.split("\\]\\, \\[");
        //List<List<String>> schedule = new ArrayList<>();

        List<List<String>> parsedSchedule = new ArrayList<>();

        for (String game: splitIntoGames) {
            System.out.println(game);

            String[] entrantIds = game.split(",");
            List<String> parsedIds = new ArrayList<>();

            for (String entrantId : entrantIds) {

                if (entrantId.substring(0,2).equals("[["))
                    entrantId = entrantId.substring(2);
                if (entrantId.substring(entrantId.length()-2,entrantId.length()).equals("]]"))
                    entrantId = entrantId.substring(0, entrantId.length()-2);

                parsedIds.add(entrantId.trim());
            }
            parsedSchedule.add(parsedIds);
        }
        return  parsedSchedule;
    }

    private enum GameDriverState {
        READY,
        RUNNING,
        OVER
    }



    public RobocodeDriver(File robocodeHome, List<List<String>> gameSchedule) {
        engine = new RobocodeEngine(robocodeHome);
        this.gameSchedule = gameSchedule;
        //System.out.println("hello world");
        //System.out.println(gameSchedule);

        engine.setVisible(true);
        engine.addBattleListener(new BattleAdaptor() {
            @Override
            public void onBattleCompleted(BattleCompletedEvent event) {
                System.out.println(battleCompletedEventToJson(event));
            }

            @Override
            public void onBattleError(
                    robocode.control.events.BattleErrorEvent event) {
                System.err.println("Battle error: " + event.getError());
            }
        });

        for (int i = 0; i < gameSchedule.size(); i++)
            runGame(i);

        engine.close();
    }

    public void runGame(int gameIndex) {
        try {

            RobotSpecification[] robots =
                    engine.getLocalRepository(String.join(",", gameSchedule.get(gameIndex)));

            BattleSpecification battle = new BattleSpecification(
                    1,
                    new BattlefieldSpecification(800, 600),
                    robots);

            engine.runBattle(battle);
            engine.waitTillBattleOver();

        } catch (Exception err) {
            System.out.println(err);
        } finally {
            
        }
    }


}
