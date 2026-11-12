package com.robo_board.robot_board.competition;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

@Service
public class CompetitionService {

    private static final Logger logger = LoggerFactory.getLogger(CompetitionService.class);
    private static final List<String> DEFAULT_ENTRANTS = List.of(
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
            "sample.VelociRobot");

    private final Map<UUID, Competition> competitions = new ConcurrentHashMap<>();
    private final TaskExecutor executor;
    private final File robocodeHome;
    private final File logDirectory;

    public CompetitionService(
            @Qualifier("competitionExecutor") TaskExecutor executor,
            @Value("${robocode.home:../robocode}") String robocodeHome,
            @Value("${competition.logs:logs}") String logDirectory) {
        this.executor = executor;
        this.robocodeHome = new File(robocodeHome);
        this.logDirectory = new File(logDirectory);
    }

    public Competition.Snapshot startCompetition(StartCompetitionRequest request) {
        List<String> entrants = request == null || request.entrants() == null
                ? DEFAULT_ENTRANTS
                : request.entrants();
        int gameSize = request == null || request.gameSize() == null ? 4 : request.gameSize();
        int gamesPerEntrant = request == null || request.gamesPerEntrant() == null
                ? 4
                : request.gamesPerEntrant();
        int broadcastCount = request == null || request.broadcastCount() == null
                ? 2
                : request.broadcastCount();

        Competition competition = new Competition(
                entrants,
                gameSize,
                gamesPerEntrant,
                broadcastCount);
        competitions.put(competition.getId(), competition);
        try {
            executor.execute(() -> runCompetition(competition));
        } catch (TaskRejectedException e) {
            competitions.remove(competition.getId());
            throw e;
        }
        return competition.snapshot();
    }

    public Optional<Competition.Snapshot> getCompetition(UUID id) {
        return Optional.ofNullable(competitions.get(id)).map(Competition::snapshot);
    }

    public List<Competition.Snapshot> getCompetitions() {
        return competitions.values().stream()
                .map(Competition::snapshot)
                .sorted(Comparator.comparing(Competition.Snapshot::createdAt).reversed())
                .toList();
    }

    private void runCompetition(Competition competition) {
        competition.markRunning();
        try {
            RobocodeDriver.runConcurrentGames(
                    competition.gamePartitions(),
                    robocodeHome,
                    logDirectory,
                    competition::recordBattleResults);
            competition.markCompleted();
            competition.printStandings();
        } catch (RuntimeException e) {
            competition.markFailed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            logger.error("Competition {} failed", competition.getId(), e);
        }
    }

    public record StartCompetitionRequest(
            List<String> entrants,
            Integer gameSize,
            Integer gamesPerEntrant,
            Integer broadcastCount) {}
}
