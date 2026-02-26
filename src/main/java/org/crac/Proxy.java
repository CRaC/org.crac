package org.crac;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

// Utility class, made public only for org.crac.management
public class Proxy {
    public static final Proxy instance;

    protected Class<?> clsResource;
    protected Class<?> clsContext;
    protected Class<?> clsCore;
    protected Class<?> clsCheckpointException;
    protected Class<?> clsRestoreException;

    protected final Method checkpointRestore;
    protected final Method register;

    protected final Object globalContext;

    protected List<Exception> registerExceptions = new ArrayList<>();

    static Proxy loadProxy(String packageName) {
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

    protected Proxy(String pkg)
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

    void checkpointRestore() throws
            CheckpointException, RestoreException {
        if (!registerExceptions.isEmpty()) {
            throw new UnsupportedOperationException();
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
            registerExceptions.add(e);
        }
    }
}
