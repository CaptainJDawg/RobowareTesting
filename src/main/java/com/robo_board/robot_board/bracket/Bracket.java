package com.robo_board.robot_board.bracket;

public class Bracket {
    List<BracketMatch> matches;
    String[] seeds;

    public Bracket(String[] seeds) {
        this.seeds = seeds;
        this.matches = new ArrayList<>();
        createBracket();
    }

    private void checkWiring() {
        // Implement the logic to check the wiring of the bracket
        for (int i = 0; i < matches.size(); i++) {
            for (int j = i + 1; j < matches.size(); j++) {
                if (matches.get(i).getMatchId() == matches.get(j).getMatchId()) {
                    throw new IllegalArgumentException("Duplicate match ID found: " + matches.get(i).getMatchId());
                }
            }
            if (matches.get(i).getSlot1().getSlotSource() instanceof SlotSources.WinnerOf winnerOf) {
                int sourceMatchId = winnerOf.matchID();
                BracketMatch sourceMatch = (sourceMatchId);
                if (sourceMatch == null) {
                    throw new IllegalArgumentException("Source match ID " + sourceMatchId + " for match ID " + matches.get(i).getMatchId() + " does not exist.");
                }
            } else if (matches.get(i).getSlot1().getSlotSource() instanceof SlotSources.LoserOf loserOf) {
                int sourceMatchId = loserOf.matchID();
                BracketMatch sourceMatch = findMatchByID(sourceMatchId);
                if (sourceMatch == null) {
                    throw new IllegalArgumentException("Source match ID " + sourceMatchId + " for match ID " + matches.get(i).getMatchId() + " does not exist.");
                }
            }
            if (matches.get(i).getSlot2().getSlotSource() instanceof SlotSources.WinnerOf winnerOf) {
                int sourceMatchId = winnerOf.matchID();
                BracketMatch sourceMatch = findMatchByID(sourceMatchId);
                if (sourceMatch == null) {
                    throw new IllegalArgumentException("Source match ID " + sourceMatchId + " for match ID " + matches.get(i).getMatchId() + " does not exist.");
                }
            } else if (matches.get(i).getSlot2().getSlotSource() instanceof SlotSources.LoserOf loserOf) {
                int sourceMatchId = loserOf.matchID();
                BracketMatch sourceMatch = findMatchByID(sourceMatchId);
                if (sourceMatch == null) {
                    throw new IllegalArgumentException("Source match ID " + sourceMatchId + " for match ID " + matches.get(i).getMatchId() + " does not exist.");
                }
            }
            
            if (matches.get(i).getSlot1().getSlotSource() instanceof SlotSources.Seed seed && seed.seedNumber() >= seeds.length) {
                throw new IllegalArgumentException("Seed number " + seed.seedNumber() + " for match ID " + matches.get(i).getMatchId() + " exceeds the number of seeds provided.");
            }
            if (matches.get(i).getSlot2().getSlotSource() instanceof SlotSources.Seed seed && seed.seedNumber() >= seeds.length) {
                throw new IllegalArgumentException("Seed number " + seed.seedNumber() + " for match ID " + matches.get(i).getMatchId() + " exceeds the number of seeds provided.");
            }
        }
        
    }

    private void fillSeeds() {
        // Implement the logic to fill the seeds into the bracket
        for (int i = 0; i < seeds.length; i++) {
            for (BracketMatch match : matches) {
                if (match.getSlot1().getSlotSource() instanceof SlotSources.Seed seed && seed.seedNumber() == i) {
                    match.getSlot1().setRobotName(seeds[i-1]);
                }
                if (match.getSlot2().getSlotSource() instanceof SlotSources.Seed seed && seed.seedNumber() == i) {
                    match.getSlot2().setRobotName(seeds[i-1]);
                }
            }
        }
    }

    private void updateReadyMatches() {
        // Implement the logic to update the status of matches that are ready to be played
        for (BracketMatch match : matches) {
            if (match.getMatchStatus() == MatchStatus.READY) {
                continue; // Skip matches that are already ready
            }
            if (match.getSlot1().hasRobot() && match.getSlot2().hasRobot()) {
                match.setMatchStatus(MatchStatus.READY);
            }
        }
    }




    public List<BracketMatch> getMatches() {
        return matches;
    }

    public BracketMatch findMatchByID(int matchId) {
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).getMatchId() == matchId) {
                return matches.get(i);
            }
        }
        return null; // Match not found
    }

    public void startMatch(int matchId) {
        // Start the first match in the bracket
        BracketMatch match = findMatchByID(matchId);
        if (match != null && match.getMatchStatus() == MatchStatus.READY) {
            match.setMatchStatus(MatchStatus.IN_PROGRESS);
        }
        else {
            throw new IllegalArgumentException("Match with ID " + matchId + " is not ready to start.");
        }
    }

    public void endMatch(BracketMatch match, String winnerRobotName) {
        // End the match and update the bracket
        if (match.getMatchStatus() != MatchStatus.IN_PROGRESS || match.getMatchStatus() != MatchStatus.READY ) {
            throw new IllegalArgumentException("Match with ID " + match.getMatchId() + " is not in progress.");
        }
        else {
            match.setMatchStatus(MatchStatus.COMPLETED);
        }
        if (match.hasRobot(winnerRobotName) == false) {
            throw new IllegalArgumentException("Winner robot " + winnerRobotName + " is not part of match with ID " + match.getMatchId() + ".");
        }
        else {
            match.winner = winnerRobotName; // Mark the winner
            match.loser = match.otherRobot(winnerRobotName); // Mark the loser
        }

        // Update the next match in the bracket with the winner
        for (BracketMatch m : matches) {
            if (m.getSlot1().getSlotSource().WinnerOf(match.getMatchId())) {
                if (m.slot1.getRobotName() == null) {
                    m.slot1.setRobotName(winnerRobotName);
                } else {
                    m.slot2.setRobotName(winnerRobotName);
                }
                //break; // Exit the loop after updating the next match
            }
            else if (m.getSlot2().getSlotSource().WinnerOf(match.getMatchId())) {
                if (m.slot1.getRobotName() == null) {
                    m.slot1.setRobotName(winnerRobotName);
                } else {
                    m.slot2.setRobotName(winnerRobotName);
                }
                //break; // Exit the loop after updating the next match
            }

            if (m.getSlot1().getSlotSource().LoserOf(match.getMatchId())) {
                if (m.slot1.getRobotName() == null) {
                    m.slot1.setRobotName(match.otherRobot(winnerRobotName));
                } else {
                    m.slot2.setRobotName(match.otherRobot(winnerRobotName));
                }
                //break; // Exit the loop after updating the next match
            }
            else if (m.getSlot2().getSlotSource().LoserOf(match.getMatchId())) {
                if (m.slot1.getRobotName() == null) {
                    m.slot1.setRobotName(match.otherRobot(winnerRobotName));
                } else {
                    m.slot2.setRobotName(match.otherRobot(winnerRobotName));
                }
                //break; // Exit the loop after updating the next match
            }
        }

        // Update the status of matches that are ready to be played
        updateReadyMatches();

        // Checking if a second championship match is needed
        // if (match.getMatchGroup() == BracketSide.FINALS) {
        // }


    }

    public boolean isFinished() {
        // Check if all matches in the bracket are completed
        for (BracketMatch match : matches) {
            if (match.matchStatus != MatchStatus.COMPLETED && match.matchStatus != MatchStatus.CANCELLED) {
                return false;
            }
        }
        return true;
    }

    public String getChampion() {
        // Return the name of the champion robot
        for (BracketMatch match : matches) {
            if (match.getMatchGroup() == BracketSide.FINALS && match.getMatchStatus() == MatchStatus.COMPLETED) {
                return match.getWinner();
            }
        }
        return null; // No champion yet
    }

    // public JSON toJsonObject() {
    //     JSON json = new JSON();
    //     // Convert the bracket to a JSON object
    //     // Implement the logic to convert the bracket to JSON
    //     return json;
    // }

    // public JSON fromJsonObject(JSON json) {
    //     // Convert a JSON object to a bracket
    //     // Implement the logic to convert JSON to a bracket
    //     return null;
    // }

}
