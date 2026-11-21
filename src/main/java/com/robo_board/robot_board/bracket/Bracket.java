package com.robo_board.robot_board.bracket;

public class Bracket {
    BracketMatch[] matches;
    int[] seeds;

    public void startMatch() {
        // Start the first match in the bracket
        BracketMatch firstMatch = matches[0];
        firstMatch.matchStatus = MatchStatus.IN_PROGRESS;
    }

    public void endMatch(BracketMatch match, String winnerRobotName) {
        // End the match and update the bracket
        match.matchStatus = MatchStatus.COMPLETED;
        match.winner = true; // Mark the winner
        match.loser = false; // Mark the loser

        // Update the next match in the bracket with the winner
        int nextMatchIndex = getNextMatchIndex(match);
        if (nextMatchIndex != -1) {
            BracketMatch nextMatch = matches[nextMatchIndex];
            if (nextMatch.slot1.robotName == null) {
                nextMatch.slot1.robotName = winnerRobotName;
            } else {
                nextMatch.slot2.robotName = winnerRobotName;
            }
        }
    }

    public void championMatch(BracketMatch match, String winnerRobotName) {
        // End the championship match and declare the winner
        match.matchStatus = MatchStatus.COMPLETED;
        match.winner = true; // Mark the winner
        match.loser = false; // Mark the loser

        // Declare the overall champion
        System.out.println("The overall champion is: " + winnerRobotName);
    }

    public void isFinished() {
        // Check if all matches in the bracket are completed
        for (BracketMatch match : matches) {
            if (match.matchStatus != MatchStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }
}
