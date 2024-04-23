package org.crac;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.ref.Reference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestExceptions {
    @BeforeMethod
    public void beforeMethod() {
        // Ensure that any resource is garbage-collected
        System.gc();
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
            assertEquals(e.getSuppressed().length, 1);
            assertEquals(e.getSuppressed()[0].getMessage(), "FOO");
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
            assertEquals(e.getSuppressed().length, 1);
            assertEquals(e.getSuppressed()[0].getMessage(), "BAR");
        }
        Reference.reachabilityFence(resource);
    }
}
