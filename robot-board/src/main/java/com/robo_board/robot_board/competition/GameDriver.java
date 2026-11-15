package com.robo_board.robot_board.competition;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import robocode.BattleResults;
import robocode.BattleRules;
import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotSpecification;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;

public class GameDriver {

    private final RobocodeEngine engine;
    private final List<List<String>> gameSchedule;
    private volatile boolean battleCompleted;
    private volatile String battleError;

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

    /** Child JVM entry point: runner ID, Robocode home, JSON schedule file. */
    public static void main(String[] args) {
        int exitCode = 0;
        try {
            if (args.length != 3) {
                throw new IllegalArgumentException("Expected runner ID, Robocode home and schedule file");
            }
            int runnerId = Integer.parseInt(args[0]);
            File robocodeHome = new File(args[1]);
            if (!robocodeHome.isDirectory()) {
                throw new IllegalStateException("Robocode not found: " + robocodeHome.getAbsolutePath());
            }
            System.out.println("Starting runner " + runnerId);
            new GameDriver(robocodeHome, parseSchedule(java.nio.file.Path.of(args[2])));
            System.out.println("Runner " + runnerId + " finished");
        } catch (Exception failure) {
            failure.printStackTrace(System.err);
            exitCode = 1;
        }
        // Robocode's GUI threads can outlive engine.close().
        System.exit(exitCode);
    }

    private static List<List<String>> parseSchedule(java.nio.file.Path path) throws java.io.IOException {
        try (java.io.Reader input = java.nio.file.Files.newBufferedReader(path,
                java.nio.charset.StandardCharsets.UTF_8);
                javax.json.JsonReader reader = Json.createReader(input)) {
            List<List<String>> schedule = new ArrayList<>();
            for (javax.json.JsonArray game : reader.readArray().getValuesAs(javax.json.JsonArray.class)) {
                List<String> robots = new ArrayList<>();
                for (javax.json.JsonString robot : game.getValuesAs(javax.json.JsonString.class)) {
                    robots.add(robot.getString());
                }
                if (robots.isEmpty()) {
                    throw new IllegalArgumentException("A scheduled battle cannot be empty");
                }
                schedule.add(List.copyOf(robots));
            }
            return List.copyOf(schedule);
        }
    }

    public GameDriver(File robocodeHome, List<List<String>> gameSchedule) {
        this.gameSchedule = gameSchedule.stream().map(List::copyOf).toList();
        engine = new RobocodeEngine(robocodeHome);
        try {
            engine.setVisible(true);
            engine.addBattleListener(new BattleAdaptor() {
                @Override
                public void onBattleCompleted(BattleCompletedEvent event) {
                    System.out.println(battleCompletedEventToJson(event));
                    System.out.flush();
                    battleCompleted = true;
                }

                @Override
                public void onBattleError(robocode.control.events.BattleErrorEvent event) {
                    battleError = event.getError();
                    System.err.println("Battle error: " + battleError);
                }
            });
            for (int i = 0; i < this.gameSchedule.size(); i++) {
                runGame(i);
            }
        } finally {
            engine.close();
        }
    }

    public void runGame(int gameIndex) {
        battleCompleted = false;
        battleError = null;
        List<String> scheduledRobots = gameSchedule.get(gameIndex);
        RobotSpecification[] robots = engine.getLocalRepository(String.join(",", scheduledRobots));
        if (robots.length != scheduledRobots.size()) {
            throw new IllegalStateException("Could not resolve all robots for battle " + gameIndex);
        }
        BattleSpecification battle = new BattleSpecification(
                1, new BattlefieldSpecification(800, 600), robots);
        engine.runBattle(battle);
        engine.waitTillBattleOver();
        if (battleError != null || !battleCompleted) {
            throw new IllegalStateException("Battle " + gameIndex + " failed: "
                    + (battleError == null ? "No completion event received" : battleError));
        }
    }
}