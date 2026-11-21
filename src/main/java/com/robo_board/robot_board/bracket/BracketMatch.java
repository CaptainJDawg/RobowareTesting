package com.robo_board.robot_board.bracket;


public class BracketMatch {
    long matchId;
    int matchGroup;
    MatchStatus matchStatus;
    bool winner;
    bool loser;
    Slot slot1;
    Slot slot2;

    public void setRobots(String robot1, String robot2) {
        slot1.robotName = robot1;
        slot2.robotName = robot2;
    }
}