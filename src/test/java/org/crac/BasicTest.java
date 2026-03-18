package org.crac;

import org.crac.management.CRaCMXBean;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.lang.ref.WeakReference;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.*;

@Test
public class BasicTest {

    private static class TestResource implements Resource {
        private final AtomicBoolean before;
        private final AtomicBoolean after;

        public TestResource(AtomicBoolean before, AtomicBoolean after) {
            this.before = before;
            this.after = after;
        }

        @Override
        public void beforeCheckpoint(Context<? extends Resource> context) {
            before.set(true);
        }

        @Override
        public void afterRestore(Context<? extends Resource> context) {
            after.set(true);
        }
    }

    private Resource strong;
    private WeakReference<Resource> weak;

    private <R extends Resource> R capture(R resource) {
        if (weak != null) {
            assertNull(weak.get());
        }
        strong = resource;
        weak = new WeakReference<>(resource);
        return resource;
    }

    @AfterMethod
    public void collect() {
        // JDK 16+ only
        // assertTrue(weak.refersTo(strong));
        strong = null;
        // Wait until testing resource is collected
        while (weak != null && weak.get() != null) {
            System.gc();
            Thread.yield();
        }
    }

    private void testCheckpointRestore(Callable<Void> checkpointMethod) throws Exception {
        AtomicBoolean before = new AtomicBoolean();
        AtomicBoolean after = new AtomicBoolean();
        Resource resource = capture(new TestResource(before, after));
        Context.getGlobalContext().register(resource);
        try {
            checkpointMethod.call();
            assertTrue(Context.isImplemented());
        } catch (UnsupportedOperationException e) {
            assertFalse(Context.isImplemented());
        }
        assertEquals(before.get(), Context.isImplemented());
        assertEquals(after.get(), Context.isImplemented());
    }

    public void testCrViaCore() throws Exception {
        testCheckpointRestore(() -> {
            //noinspection deprecation
            Core.checkpointRestore();
            return null;
        });
    }

    public void testCrViaMxBean() throws Exception {
        testCheckpointRestore(() -> {
            CRaCMXBean.getCRaCMXBean().checkpointRestore();
            return null;
        });
    }

    // Test that if resource leaks from the app, org.crac implementation won't keep it live
    public void testGCedResource() throws RestoreException, CheckpointException {
        AtomicBoolean before = new AtomicBoolean();
        AtomicBoolean after = new AtomicBoolean();
        Context.getGlobalContext().register(capture(new TestResource(before, after)));
        // Release immediately after capturing - without capture we wouldn't call System.gc()
        collect();
        try {
            CRaCMXBean.getCRaCMXBean().checkpointRestore();
        } catch (UnsupportedOperationException e) {
            // test works on non-CRaC JDK, too
        }
        assertFalse(before.get());
        assertFalse(after.get());
    }

    public void testImplementedSame() {
        assertEquals(CRaCMXBean.getCRaCMXBean().isImplemented(), Context.isImplemented());
    }

    public void testBeforeThrows() {
        if (!Context.isImplemented()) {
            throw new SkipException("CRaC not implemented");
        }
        Exception myException = new Exception("test");
        Resource resource = capture(new Resource() {
            @Override
            public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
                throw myException;
            }

            @Override
            public void afterRestore(Context<? extends Resource> context) {
            }
        });
        Context.getGlobalContext().register(resource);

        try {
            CRaCMXBean.getCRaCMXBean().checkpointRestore();
            fail("Should throw CheckpointException");
        } catch (CheckpointException e) {
            assertEquals(e.getSuppressed().length, 1);
            assertEquals(e.getSuppressed()[0], myException);
        } catch (RestoreException e) {
            fail("Should throw CheckpointException");
        }
    }

    public void testAfterThrows() {
        if (!Context.isImplemented()) {
            throw new SkipException("CRaC not implemented");
        }
        Exception myException = new Exception("test");
        Resource resource = capture(new Resource() {
            @Override
            public void beforeCheckpoint(Context<? extends Resource> context) {
            }

            @Override
            public void afterRestore(Context<? extends Resource> context) throws Exception {
                throw myException;
            }
        });
        Context.getGlobalContext().register(resource);

        try {
            CRaCMXBean.getCRaCMXBean().checkpointRestore();
            fail("Should throw RestoreException");
        } catch (CheckpointException e) {
            fail("Should throw RestoreException");
        } catch (RestoreException e) {
            assertEquals(e.getSuppressed().length, 1);
            assertEquals(e.getSuppressed()[0], myException);
        }
    }
}
