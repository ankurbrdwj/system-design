package com.ankur.design.lld.kayak.room.reservation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Section 1 – Warming up
 * Verifies that Main prints the candidate name to stderr
 * and the WFO answer to stdout.
 */
class MainSection1Test {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void redirectStreams() {
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void candidateNameIsWrittenToStderr() throws Exception {
        Main.main(new String[]{});

        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("Ankur Bhardwaj"),
                "Expected 'Ankur Bhardwaj' on stderr but got: " + stderr);
    }

    @Test
    void candidateNameIsNotWrittenToStdout() throws Exception {
        Main.main(new String[]{});

        String stdout = capturedOut.toString();
        assertTrue(!stdout.contains("Ankur Bhardwaj"),
                "Candidate name should be on stderr, not stdout");
    }

    @Test
    void wfoAnswerIsWrittenToStdout() throws Exception {
        Main.main(new String[]{});

        String stdout = capturedOut.toString();
        assertTrue(stdout.contains("Work-from-office"),
                "Expected WFO answer on stdout but got: " + stdout);
    }

    @Test
    void wfoAnswerIsNotWrittenToStderr() throws Exception {
        Main.main(new String[]{});

        String stderr = capturedErr.toString();
        assertTrue(!stderr.contains("Work-from-office"),
                "WFO answer should be on stdout, not stderr");
    }
}