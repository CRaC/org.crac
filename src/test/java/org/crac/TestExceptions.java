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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.ref.Reference;

import static org.testng.Assert.*;

public class TestExceptions {
    @BeforeMethod
    public void beforeMethod() {
        // Ensure that any resource is garbage-collected
        System.gc();
    }

    private static void checkException(ExceptionBase e, String msg) {
        assertEquals(e.getNestedExceptions().length, 1);
        assertEquals(e.getNestedExceptions()[0].getMessage(), msg);
        assertEquals(e.getSuppressed().length, 0);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PrintStream s = new PrintStream(bos)) {
            e.printStackTrace(s);
        }
        String out = bos.toString();
        assertTrue(out.contains(": Failed with 1 nested exception"));
        assertTrue(out.contains("\nCause 1/1: java.lang.Exception: " + msg));
        assertTrue(out.contains("\n\tat "));
        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        try (PrintWriter w = new PrintWriter(bos2)) {
            e.printStackTrace(w);
        }
        assertEquals(bos2.toString(), out);
    }

    @Test
    public void testCheckpointException() throws RestoreException {
        Resource resource = new Resource() {
            @Override
            public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
                throw new Exception("FOO");
            }

            @Override
            public void afterRestore(Context<? extends Resource> context) {
            }
        };
        Core.getGlobalContext().register(resource);
        try{
            Core.checkpointRestore();
            fail("Expecting checkpoint exception");
        } catch (CheckpointException e) {
            checkException(e, "FOO");
        }
        Reference.reachabilityFence(resource);
    }

    @Test
    public void testRestoreException() throws CheckpointException {
        Resource resource = new Resource() {
            @Override
            public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
            }

            @Override
            public void afterRestore(Context<? extends Resource> context) throws Exception {
                throw new Exception("BAR");
            }
        };
        Core.getGlobalContext().register(resource);
        try{
            Core.checkpointRestore();
            fail("Expecting restore exception");
        } catch (RestoreException e) {
            checkException(e, "BAR");
        }
        Reference.reachabilityFence(resource);
    }
}
