package org.crac;

import java.io.PrintStream;

public class CheckpointRestoreException extends Exception {
    private static final long serialVersionUID = 0;

    protected Exception exceptions[];

    public CheckpointRestoreException(Exception[] exceptions) {
        this.exceptions = exceptions;
    }

    public Exception[] getExceptions() {
        return exceptions;
    }

    public void printExceptions(PrintStream s) {
        for (int i = 0; i < exceptions.length; ++i) {
            s.println("  " + exceptions[i].toString());
        }
    }

    public void printExceptions() {
        printExceptions(java.lang.System.err);
    }
}

