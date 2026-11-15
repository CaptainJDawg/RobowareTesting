package com.robo_board.robot_board.competition;

import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CompetitionService {
    private final AtomicBoolean competitionRunning = new AtomicBoolean(false);

    public boolean startCompetition() {
        if (!competitionRunning.compareAndSet(false, true)) {
            return false;
        }

        Thread worker = new Thread(() -> {
            try {
                runCompetition();
            } catch (RuntimeException failure) {
                System.err.println("Competition failed: " + failure.getMessage());
                failure.printStackTrace(System.err);
            } finally {
                competitionRunning.set(false);
            }
        }, "competition-runner");
        worker.start();
        return true;
    }

    public void runCompetition() {

        System.out.printf("Running competition... %n");

        new CompetitionDriver(CompetitionDriver.SAMPLE_ENTRANTS,
                Path.of("..", "robocode").toAbsolutePath().normalize());
    }
}
