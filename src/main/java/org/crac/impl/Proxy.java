// Copyright 2017, 2026 Azul Systems, Inc.
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
package org.crac.impl;

import org.crac.CheckpointException;
import org.crac.Resource;
import org.crac.RestoreException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

// Utility class, made public only for org.crac.management
public class Proxy {
    public static final Proxy instance;

    private final Class<?> clsResource;
    private final Class<?> clsContext;
    private final Class<?> clsCore;
    private final Class<?> clsCheckpointException;
    private final Class<?> clsRestoreException;

    private final Method checkpointRestore;
    private final Method register;

    private final Object globalContext;

    private final List<Exception> registerExceptions = new ArrayList<>();

    private static Proxy loadProxy(String packageName) {
        try {
            return new Proxy(packageName);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    static {
        Proxy candidate = null;
        String propCompatImpl = System.getProperty("org.crac.Core.Compat");
        if (propCompatImpl != null) {
            candidate = loadProxy(propCompatImpl);
        }

        if (candidate == null) {
            candidate = loadProxy("javax.crac");
        }

        if (candidate == null) {
            candidate = loadProxy("jdk.crac");
        }

        instance = candidate;
    }

    private Proxy(String pkg)
            throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {

        clsResource = Class.forName(pkg + ".Resource");
        clsContext = Class.forName(pkg + ".Context");
        clsCore = Class.forName(pkg + ".Core");
        clsCheckpointException = Class.forName(pkg + ".CheckpointException");
        clsRestoreException = Class.forName(pkg + ".RestoreException");

        Method checkpointRestore = null;
        try {
            checkpointRestore = clsCore.getMethod("checkpointRestore");
        } catch (NoSuchMethodException e) {
            // checkpoint restore possible only through CRaCMxBean
        }
        this.checkpointRestore = checkpointRestore;

        Method getGlobalContext = null;
        try {
            getGlobalContext = clsCore.getMethod("getGlobalContext");
        } catch (NoSuchMethodException e) {
            // Failure to retrieve this will stop loading from the package, rather than throw exception
            getGlobalContext = clsContext.getMethod("getGlobalContext");
        }
        globalContext = getGlobalContext.invoke(null);

        register = clsContext.getMethod("register", clsResource);
    }

    public void checkpointRestore() throws
            CheckpointException, RestoreException {
        synchronized (this) {
            if (!registerExceptions.isEmpty()) {
                UnsupportedOperationException ex = new UnsupportedOperationException();
                registerExceptions.forEach(ex::addSuppressed);
                registerExceptions.clear();
                throw ex;
            }
        }
        try {
            checkpointRestore.invoke(null);
        } catch (IllegalAccessException iae) {
            handleExceptionFromCheckpoint(iae);
        } catch (InvocationTargetException ite) {
            handleExceptionFromCheckpoint(ite);
        }
    }

    public void handleExceptionFromCheckpoint(Throwable throwable) throws CheckpointException {
        CheckpointException checkpointException = new CheckpointException();
        checkpointException.addSuppressed(throwable);
        throw checkpointException;
    }

    public void handleExceptionFromCheckpoint(InvocationTargetException ite) throws CheckpointException, RestoreException {
        Throwable cause = ite.getCause();
        if (clsCheckpointException.isInstance(cause)) {
            CheckpointException checkpointException = new CheckpointException();
            for (Throwable t : cause.getSuppressed()) {
                checkpointException.addSuppressed(t);
            }
            throw checkpointException;
        } else if (clsRestoreException.isInstance(cause)) {
            RestoreException restoreException = new RestoreException();
            for (Throwable t : cause.getSuppressed()) {
                restoreException.addSuppressed(t);
            }
            throw restoreException;
        }
        handleExceptionFromCheckpoint(cause);
    }

    void register(Resource resource) {
        // JDK register will maintain weak ref on proxy, so we have to provide
        // it and resourceWrapper same lifetime as enclosed Resource have.
        // ResourceWrapper and proxy will have strong links on each other.
        // ResourceWrapper will also have weak ref on Resource.
        // All ResourceProxies are strongly reachable via resourceProxies.
        // The list cleaned occasionally. Strong ref to ResourceWrapper cleared
        // when its Resource became unreachable.
        try {
            ResourceWrapper resourceWrapper = new ResourceWrapper(resource);
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    Proxy.class.getClassLoader(),
                    new Class[]{clsResource},
                    resourceWrapper);
            resourceWrapper.setProxy(proxy);
            register.invoke(globalContext, proxy);
        } catch (IllegalAccessException | InvocationTargetException e) {
            synchronized (this) {
                registerExceptions.add(e);
            }
        }
    }
}
