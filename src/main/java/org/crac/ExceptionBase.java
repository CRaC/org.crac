// Copyright 2024 Azul Systems, Inc.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation
// and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.
package org.crac;

import jdk.crac.CheckpointException;
import jdk.crac.RestoreException;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Common base for {@link CheckpointException} and {@link RestoreException}.
 */
public abstract class ExceptionBase extends Exception {
    private static final long serialVersionUID = -6281937111554065647L;
    private static final Throwable[] EMPTY_THROWABLE_ARRAY = new Throwable[0];
    private static final String MSG_FORMAT = "%s: Failed with %d nested exceptions%n";
    private static final String CAUSE_FORMAT = "Cause %d/%d: ";

    private ArrayList<Throwable> nested;

    ExceptionBase(String message) {
        super(message, null, false, false);
    }

    ExceptionBase(Throwable[] nested) {
        this((String) null);
        if (nested != null && nested.length > 0) {
            this.nested = new ArrayList<>(Arrays.asList(nested));
        }
    }

    /**
     * Add exception to the list of nested exceptions.
     * @param throwable Added exception
     */
    public void addNestedException(Throwable throwable) {
        if (this.nested == null) {
            this.nested = new ArrayList<>();
        }
        this.nested.add(throwable);
    }

    /**
     * Returns an array containing all the exceptions that this exception holds as nested exceptions.
     * @return an array containing all nested exceptions.
     */
    public Throwable[] getNestedExceptions() {
        if (nested == null) {
            return EMPTY_THROWABLE_ARRAY;
        }
        return nested.toArray(EMPTY_THROWABLE_ARRAY);
    }

    @Override
    public void printStackTrace(PrintStream s) {
        s.printf(MSG_FORMAT, getClass().getName(), nested.size());
        for (int i = 0; i < nested.size(); ++i) {
            s.printf(CAUSE_FORMAT, i + 1, nested.size());
            nested.get(i).printStackTrace(s);
        }
    }

    @Override
    public void printStackTrace(PrintWriter w) {
        w.printf(MSG_FORMAT, getClass().getName(), nested.size());
        for (int i = 0; i < nested.size(); ++i) {
            w.printf(CAUSE_FORMAT, i + 1, nested.size());
            nested.get(i).printStackTrace(w);
        }
    }
}
