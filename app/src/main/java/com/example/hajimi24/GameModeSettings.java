package com.example.hajimi24;

public class GameModeSettings {
    public int numberBound = -1; // -1 for unlimited
    public boolean avoidPureAddSub = false;
    public boolean mustHaveDivision = false;
    public boolean avoidTrivialFinalMultiply = false;
    public boolean requireFractionCalc = false;
    public boolean requireDivisionStorm = false;

    public static GameModeSettings createDefault() {
        return new GameModeSettings();
    }

}
