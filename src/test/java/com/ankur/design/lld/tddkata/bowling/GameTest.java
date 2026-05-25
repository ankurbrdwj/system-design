package com.ankur.design.lld.tddkata.bowling;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GameTest {
    private Game game;

    @BeforeEach
    public void setUp() {
        game = new Game();
    }

    @Test
    public void testGutterGame() {
        rollMany(20, 0);
        Assertions.assertEquals(0, game.score());
    }

    private void rollMany(int n, int pins) {
        for (int i = 0; i < n; i++) {
            game.roll(pins);
        }
    }

    @Test
    public void testAllOnes() {
        rollMany(20, 1);
        Assertions.assertEquals(20, game.score());
    }

    @Test
    public void testOneSpare() {
        rollSpare();
        game.roll(3);
        rollMany(17, 0);
        Assertions.assertEquals(16, game.score());
    }

    private void rollSpare() {
        game.roll(5);
        game.roll(5);
    }
    @Test
    public void testOneStrike() {
        rollStrike();
        game.roll(3);
        game.roll(4);
        rollMany(16, 0);
        Assertions.assertEquals(24, game.score());
    }
    private void rollStrike() {
        game.roll(10);
    }

    @Test
    public void testPerfectGame() {
        rollMany(12, 10);
        Assertions.assertEquals(300, game.score());
    }
}
