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

import org.crac.Resource;

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

    void setProxy(Object proxy) {
        this.proxy = proxy;
    }

    ResourceWrapper(Resource referent) {
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
