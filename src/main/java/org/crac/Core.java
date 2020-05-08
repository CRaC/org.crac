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
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class Core {
    static class ResourceProxy extends WeakReference<Resource> implements java.lang.reflect.InvocationHandler {
        private Object proxy;
        private Object strong;

        private ResourceProxy(Resource obj, ReferenceQueue<Resource> refQueue) {
            super(obj, refQueue);
        }

        void setProxy(Object proxy) {
            this.proxy = proxy;
        }

        public Object invoke(Object proxy, Method m, Object[] args)
                throws Throwable {
            String name = m.getName();
            Resource obj = get();

            if ("beforeCheckpoint".equals(name)) {
                strong = obj;
                if (obj != null) {
                    obj.beforeCheckpoint();
                }
                return null;
            } else if ("afterRestore".equals(name)) {
                if (obj != null) {
                    obj.afterRestore();
                }
                strong = null;
                return null;
            } else if ("toString".equals(name)) {
                return this.toString() + "[" + obj + "]";
            } else {
                try {
                    return m.invoke(obj, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        }
    }

    static abstract class Compat {
        protected final Method tryCheckpointRestore;
        protected final Method register;

        protected final Class resourceInterface;

        protected final Class checkpointException;
        protected final Class openResourceException;
        protected final Class openSocketException;
        protected final Class openFileException;
        protected final Class restoreException;

        protected final Method checkpointGetExceptions;
        protected final Method restoreGetExceptions;
        protected final Method openFileGetDetails;;
        protected final Method openSocketGetDetails;;
        protected final Method openResourceGetDetails;;

        private final ArrayList<ResourceProxy> resourceProxies = new ArrayList<>();
        private final ReferenceQueue<Resource> resourceProxyQueue = new ReferenceQueue<>();

        private void cleanupResourceProxies() {
            final boolean doCleanup = resourceProxyQueue.poll() != null;
            if (doCleanup) {
                while (resourceProxyQueue.poll() != null) { }
                synchronized (resourceProxies) {
                    resourceProxies.removeIf(r -> r.get() == null);
                }
            }
        }

        protected Compat(String resourceInterfaceName, 
                    String tryHolderName, 
                    String registerHolderHame, 
                    String exceptionsPackage)
                throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {

            resourceInterface = Class.forName(resourceInterfaceName);

            final Class tryHolder = Class.forName(tryHolderName);
            tryCheckpointRestore = tryHolder.getMethod("tryCheckpointRestore");

            final Class registerHolder = Class.forName(registerHolderHame);
            register = registerHolder.getMethod("register", resourceInterface);

            checkpointException = Class.forName(exceptionsPackage + ".CheckpointException");
            openResourceException = Class.forName(exceptionsPackage + ".CheckpointOpenResourceException");
            openSocketException = Class.forName(exceptionsPackage + ".CheckpointOpenSocketException");
            openFileException = Class.forName(exceptionsPackage + ".CheckpointOpenFileException");
            restoreException = Class.forName(exceptionsPackage + ".RestoreException");

            checkpointGetExceptions = checkpointException.getMethod("getExceptions");
            restoreGetExceptions = restoreException.getMethod("getExceptions");
            openFileGetDetails = openFileException.getMethod("getDetails");
            openSocketGetDetails = openSocketException.getMethod("getDetails");
            openResourceGetDetails = openResourceException.getMethod("getDetails");
        }

        private String translateStringException(Exception exception, Method getExceptions) {
            try {
                return (String)getExceptions.invoke(exception);
            } catch (IllegalAccessException | InvocationTargetException e) {
                return e.toString();
            }
        }

        private Exception[] translateCompoundException(Throwable t, Method getExceptions) {
            try {
                Exception[] rawExceptions = (Exception[])getExceptions.invoke(t);
                Exception[] newExceptions = new Exception[rawExceptions.length];
                for (int i = 0; i < rawExceptions.length; ++i) {
                    Exception raw = rawExceptions[i];
                    Exception newOne;
                    if (openFileException.isInstance(rawExceptions[i])) {
                        newOne = new CheckpointOpenFileException(translateStringException(raw, openFileGetDetails));
                    } else if (openSocketException.isInstance(rawExceptions[i])) {
                        newOne = new CheckpointOpenSocketException(translateStringException(raw, openSocketGetDetails));
                    } else if (openResourceException.isInstance(raw)) {
                        newOne = new CheckpointOpenResourceException(translateStringException(raw, openResourceGetDetails));
                    } else {
                        newOne = raw;
                    }
                    newExceptions[i] = newOne;
                }
                return newExceptions;
            } catch (IllegalAccessException | InvocationTargetException e) {
                return new Exception[] { e };
            }
        }

        public void tryCheckpointRestore() throws CheckpointException, RestoreException {

            cleanupResourceProxies();

            try {
                tryCheckpointRestore.invoke(null);
            } catch (InvocationTargetException ite) {
                Throwable t = ite.getCause();
                if (checkpointException.isInstance(t)) {
                    throw new CheckpointException(translateCompoundException(t, checkpointGetExceptions));
                } else if (restoreException.isInstance(t)) {
                    throw new RestoreException(translateCompoundException(t, restoreGetExceptions));
                } else {
                    t.printStackTrace();
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

            cleanupResourceProxies();
        }

        public void register(Resource resource) {

            cleanupResourceProxies();

            // JDK register will maintain weak ref on proxy, so we have provide
            // it and resourceProxy same lifetime as enclosed Resource have.
            // ResourceProxy and proxy will have strongs links on each other.
            // ResourceProxy will also have weak ref on Resource.
            // All ResourceProxies are strongly reachable via resourceProxies.
            // The list cleaned ocassionally. Strong ref to ResourceProxy cleared
            // when its Resource became unreachable.
            ResourceProxy resourceProxy = new ResourceProxy(resource, resourceProxyQueue);
            Object proxy = Proxy.newProxyInstance(
                    Compat.class.getClassLoader(),
                    new Class[] { resourceInterface },
                    resourceProxy);
            resourceProxy.setProxy(proxy);
            synchronized (resourceProxies) {
                resourceProxies.add(resourceProxy);
            }

            try {
                register.invoke(null, proxy);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    static class CompatMaster extends Compat {
        CompatMaster() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
            super("jdk.crac.Resource", "jdk.crac.Core", "jdk.crac.Core", "jdk.crac");
        }
    }

    static class CompatZulu8 extends Compat {
        CompatZulu8() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
            super("com.azul.crac.Resource", "com.azul.crac.Core", "com.azul.crac.Core", "com.azul.crac");
        }
    }

    static final Compat compat;
    static {
        Compat candidate = null;
        try {
            candidate = new CompatMaster();
        } catch (Throwable t) {
        }
        if (candidate == null) {
            try {
                candidate = new CompatZulu8();
            } catch (Throwable t) {
            }
        }

        compat = candidate;
    }

    public static void tryCheckpointRestore() throws
            CheckpointException,
            RestoreException {
        if (compat != null) {
            compat.tryCheckpointRestore();
        } else {
            throw new CheckpointException(new Exception[] { new UnsupportedOperationException() });
        }
    }

    public static void register(Resource r) {
        if (compat != null) {
            compat.register(r);
        }
    }
}
