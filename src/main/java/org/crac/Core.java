// Copyright 2017-2019 Azul Systems, Inc.
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
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public class Core {
    static private final Context<Resource> globalContextWrapper = new GlobalContextWrapper();
    static private final Compat compat;

    static class ResourceWrapper extends WeakReference<Resource> implements InvocationHandler {
        private static WeakHashMap<Resource, ResourceWrapper> weakMap = new WeakHashMap<>();

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

    static abstract class Compat {
        protected Class clsResource;
        protected Class clsContext;
        protected Class clsCore;
        protected Class clsCheckpointException;
        protected Class clsRestoreException;

        protected final Method checkpointRestore;
        protected final Method register;

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
        }

        public void checkpointRestore() throws
                CheckpointException, RestoreException {
            if (registerExceptions.size() != 0) {
		throw new UnsupportedOperationException();
            }
            try {
                checkpointRestore.invoke(null);
            } catch (InvocationTargetException | IllegalAccessException ite) {
                if (clsCheckpointException.isInstance(ite.getCause())) {
                    CheckpointException checkpointException = new CheckpointException();
                    for (Throwable t : ite.getCause().getSuppressed()) {
                        checkpointException.addSuppressed(t);
                    }
                    throw checkpointException;
                } else if (clsRestoreException.isInstance(ite.getCause())) {
                    RestoreException restoreException = new RestoreException();
                    for (Throwable t : ite.getCause().getSuppressed()) {
                        restoreException.addSuppressed(t);
                    }
                    throw restoreException;
                } else {
                    CheckpointException checkpointException = new CheckpointException();
                    checkpointException.addSuppressed(ite);
                    throw checkpointException;
                }
            }
        }

        public void register(Resource resource) {
            // JDK register will maintain weak ref on proxy, so we have provide
            // it and resourceWrapper same lifetime as enclosed Resource have.
            // ResourceWrapper and proxy will have strongs links on each other.
            // ResourceWrapper will also have weak ref on Resource.
            // All ResourceProxies are strongly reachable via resourceProxies.
            // The list cleaned ocassionally. Strong ref to ResourceWrapper cleared
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

    static class CompatMaster extends Compat {
        CompatMaster() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
                InvocationTargetException {
            super("javax.crac");
        }
    }

    static class CompatJdk extends Compat {
        CompatJdk() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
                InvocationTargetException {
            super("jdk.crac");
        }
    }

    static {
        Compat candidate;
        try {
            candidate = new CompatMaster();
        } catch (Throwable t) {
            try {
                candidate = new CompatJdk();
            } catch (Throwable t2) {
                candidate = null;
            }
        }
        compat = candidate;
    }

    public static Context<Resource> getGlobalContext() {
        return globalContextWrapper;
    }

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
