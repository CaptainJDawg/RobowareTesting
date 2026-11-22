package com.robo_board.robot_board.bracket;


public class BracketMatch {
    long matchId;
    int matchGroup;
    int round;
    MatchStatus matchStatus;
    boolean winner;
    boolean loser;
    Slot slot1;
    Slot slot2;

    public void setRobots(String robot1, String robot2) {
        slot1.setRobotName(robot1);
        slot2.setRobotName(robot2);
    }
    public String[] getRobots() {
        return new String[]{slot1.getRobotName(), slot2.getRobotName()};
    }
}