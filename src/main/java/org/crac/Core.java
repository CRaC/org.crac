// Copyright 2017-2024 Azul Systems, Inc.
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

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * The coordination service.
 */
public class Core {
    static private final Context<Resource> globalContextWrapper = new GlobalContextWrapper();
    static private final Compat compat;

    static class ResourceWrapper extends WeakReference<Resource> implements InvocationHandler {
        private static final WeakHashMap<Resource, ResourceWrapper> weakMap = new WeakHashMap<>();

        // proxy weakly registered in JDK, so we need prevent it collection
        private Object proxy;

        // Create strong reference to avoid losing the Resource.
        // It's set unconditionally in beforeCheckpoint and cleaned in afterRestore
        // (latter is called regardless of beforeCheckpoint result).
        private Resource strongRef;

        public void setProxy(Object proxy) {
            this.proxy = proxy;
        }

        public ResourceWrapper(Resource referent) {
            super(referent);
            weakMap.put(referent, this);
            strongRef = null;
        }

        public Object invoke(Object proxy, Method m, Object[] args)
                throws Throwable {
            String name = m.getName();
            if ("beforeCheckpoint".equals(name)) {
                beforeCheckpoint();
                return null;
            } else if ("afterRestore".equals(name)) {
                afterRestore();
                return null;
            } else if ("toString".equals(name)) {
                return toString();
            } else {
                try {
                    return m.invoke(get(), args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        }

        private void beforeCheckpoint() throws Exception {
            Resource r = get();
            strongRef = r;
            if (r != null) {
                r.beforeCheckpoint(globalContextWrapper);
            }
        }

        private void afterRestore() throws Exception {
            Resource r = get();
            strongRef = null;
            if (r != null) {
                r.afterRestore(globalContextWrapper);
            }
        }

        @Override
        public String toString() {
            return "org.crac.ResourceWrapper[" + get().toString() + "]";
        }
    }

    static class Compat {
        protected Class clsResource;
        protected Class clsContext;
        protected Class clsCore;
        protected Class clsCheckpointException;
        protected Class clsRestoreException;

        protected final Method checkpointRestore;
        protected final Method register;
        protected final Method getNestedExceptions;

        protected final Object globalContext;

        protected List<Exception> registerExceptions = new ArrayList<>();

        protected Compat(String pkg)
                throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {

            clsResource = Class.forName(pkg + ".Resource");
            clsContext = Class.forName(pkg + ".Context");
            clsCore = Class.forName(pkg + ".Core");
            clsCheckpointException = Class.forName(pkg + ".CheckpointException");
            clsRestoreException = Class.forName(pkg + ".RestoreException");

            checkpointRestore = clsCore.getMethod("checkpointRestore");
            register = clsContext.getMethod("register", clsResource);

            globalContext = clsCore.getMethod("getGlobalContext").invoke(null);

            Method getNestedExceptions;
            try {
                getNestedExceptions = clsCheckpointException.getMethod("getNestedExceptions");
            } catch (NoSuchMethodException e) {
                getNestedExceptions = clsCheckpointException.getMethod("getSuppressed");
            }
            this.getNestedExceptions = getNestedExceptions;
        }

        public void checkpointRestore() throws
                CheckpointException, RestoreException {
            if (registerExceptions.size() != 0) {
                throw new UnsupportedOperationException();
            }
            try {
                checkpointRestore.invoke(null);
            } catch (InvocationTargetException | IllegalAccessException ite) {
                try {
                    if (clsCheckpointException.isInstance(ite.getCause())) {
                        throw new CheckpointException((Throwable[]) getNestedExceptions.invoke(ite.getCause()));
                    } else if (clsRestoreException.isInstance(ite.getCause())) {
                        throw new RestoreException((Throwable[]) getNestedExceptions.invoke(ite.getCause()));
                    } else {
                        CheckpointException checkpointException = new CheckpointException();
                        checkpointException.addSuppressed(ite);
                        throw checkpointException;
                    }
                } catch (InvocationTargetException | IllegalAccessException e) {
                    CheckpointException ex = new CheckpointException();
                    ex.addSuppressed(e);
                    ex.addSuppressed(ite);
                    throw ex;
                }
            }
        }

        public void register(Resource resource) {
            // JDK register will maintain weak ref on proxy, so we have to provide
            // it and resourceWrapper same lifetime as enclosed Resource have.
            // ResourceWrapper and proxy will have strong links on each other.
            // ResourceWrapper will also have weak ref on Resource.
            // All ResourceProxies are strongly reachable via resourceProxies.
            // The list cleaned occasionally. Strong ref to ResourceWrapper cleared
            // when its Resource became unreachable.
            try {
                ResourceWrapper resourceWrapper = new ResourceWrapper(resource);
                Object proxy = Proxy.newProxyInstance(
                        Compat.class.getClassLoader(),
                        new Class[]{clsResource},
                        resourceWrapper);
                resourceWrapper.setProxy(proxy);
                register.invoke(globalContext, proxy);
            } catch (IllegalAccessException | InvocationTargetException e) {
                registerExceptions.add(e);
            }
        }
    }

    static Compat loadCompat(String packageName) {
        try {
            return new Compat(packageName);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    static {
        Compat candidate = null;
        String propCompatImpl = System.getProperty("org.crac.Core.Compat");
        if (propCompatImpl != null) {
            candidate = loadCompat(propCompatImpl);
        }

        if (candidate == null) {
            candidate = loadCompat("javax.crac");
        }

        if (candidate == null) {
            candidate = loadCompat("jdk.crac");
        }

        compat = candidate;
    }

    /**
     * Gets the global {@code Context} for checkpoint/restore notifications.
     *
     * @return the global {@code Context}
     */
    public static Context<Resource> getGlobalContext() {
        return globalContextWrapper;
    }

    /**
     * Requests checkpoint and returns upon a successful restore.
     * May throw an exception if the checkpoint or restore are unsuccessful.
     *
     * @throws CheckpointException           if an exception occurred during checkpoint
     *                                       notification and the execution continues in the original Java instance.
     * @throws RestoreException              if an exception occurred during restore
     *                                       notification and execution continues in a new Java instance.
     * @throws UnsupportedOperationException if checkpoint/restore is not
     *                                       supported, no notification performed and the execution continues in
     *                                       the original Java instance.
     */
    public static void checkpointRestore() throws
            CheckpointException, RestoreException {
        if (compat == null) {
            throw new UnsupportedOperationException();
        }
        compat.checkpointRestore();
    }

    static void register(Resource r) {
        if (compat != null) {
            compat.register(r);
        }
    }
}
