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

    private final int BROADCAST_COUNT = 1;
    private final League leaguePhase;

    public CompetitionDriver(List<String> entrants, Path robocodeHome) {
        leaguePhase = League.Generate(entrants, 4, 1); 
        List<RobocodeEngineRunner> runners = new ArrayList<>();
        int runnerId = 1;
        for (List<List<String>> batch : RobocodeEngineRunner.partitionSchedule(
                leaguePhase.getSchedule(), BROADCAST_COUNT)) {
            runners.add(new RobocodeEngineRunner(runnerId++, robocodeHome.toFile(), batch));
        }
        RobocodeEngineRunner.runAll(runners, leaguePhase::recordBattleResults);
        leaguePhase.printStatsTable();
    }
}
