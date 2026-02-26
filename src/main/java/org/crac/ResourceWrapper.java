package org.crac;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

class ResourceWrapper extends WeakReference<Resource> implements InvocationHandler {
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
            r.beforeCheckpoint(GlobalContextWrapper.instance);
        }
    }

    private void afterRestore() throws Exception {
        Resource r = get();
        strongRef = null;
        if (r != null) {
            r.afterRestore(GlobalContextWrapper.instance);
        }
    }

    @Override
    public String toString() {
        return "org.crac.ResourceWrapper[" + get().toString() + "]";
    }
}
