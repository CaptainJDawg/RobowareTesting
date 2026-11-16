package com.robo_board.robot_board.competition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public class CompetitionDriver {


    public static final List<String> SAMPLE_ENTRANTS = List.of(
        "sample.Corners",
        "sample.Fire",
        "sample.SpinBot",
        "sample.SittingDuck",
        "sample.Crazy",
        "sample.MyFirstRobot",
        "sample.PaintingRobot",
        "sample.RamFire",
        "sample.Tracker",
        "sample.Target",
        "sample.TrackFire",
        "sample.VelociRobot"
        // "sample.VelociRobot"
    );

    private final int BROADCAST_COUNT = 1; // how many robocode instances to run in parrallel that 'broadcast' the competition to to the audience

    private final boolean PRINT_LEAGUE_SCHEDULE = true; 
    private final boolean PRINT_LEAGUE_RESULTS = true; 
    private final int LEAGUE_GAMES_PER_ENTRY = 1; // how many games should each entry play in the league. The higher this is the more chance of a player to play an opponent more than once 
    private final int LEAGUE_GAME_SIZE = 4; // specifies 1v1v1v1 or 4-person-ffa. Make this 2 and the league runs on 1v1s
    private final League leaguePhase;

    public CompetitionDriver(List<String> entrants, Path robocodeHome) {
        this(entrants, robocodeHome, ScheduledBattle.Settings.DEFAULT);
    }

    public CompetitionDriver(List<String> entrants, Path robocodeHome, ScheduledBattle.Settings settings) {
        leaguePhase = League.Generate(entrants, LEAGUE_GAME_SIZE, LEAGUE_GAMES_PER_ENTRY, settings);

        if (PRINT_LEAGUE_SCHEDULE) leaguePhase.showSchedule();

        List<RobocodeEngineRunner> runners = new ArrayList<>();
        int runnerId = 1;

        for (List<ScheduledBattle> batch : RobocodeEngineRunner.partitionBattles(
                leaguePhase.getScheduledBattles(), BROADCAST_COUNT)) {
            runners.add(new RobocodeEngineRunner(runnerId++, robocodeHome.toFile(), batch));
        }
        RobocodeEngineRunner.runAll(runners, leaguePhase::recordBattleResults);

        if (PRINT_LEAGUE_RESULTS) leaguePhase.showStatsTable();
    }
}
