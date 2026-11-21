package com.robo_board.robot_board.bracket;

public class Slot{
    private final SlotSources slotSource;
    private String robotName;  

    public Slot(SlotSources source) {
        this.slotSource = source;
        this.robotName = null;
    }
}