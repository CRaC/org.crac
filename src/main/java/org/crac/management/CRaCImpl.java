// Copyright 2022 Azul Systems, Inc.
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

package org.crac.management;

import javax.management.ObjectName;
import java.lang.management.PlatformManagedObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class CRaCImpl implements CRaCMXBean {

    private final PlatformManagedObject platformImpl;
    private final Method getUptimeSinceRestore;
    private final Method getRestoreTime;

    CRaCImpl(Class iface, PlatformManagedObject platformImpl)
            throws NoSuchMethodException {
        this.platformImpl = platformImpl;
        this.getUptimeSinceRestore = iface.getMethod("getUptimeSinceRestore");
        this.getRestoreTime = iface.getMethod("getRestoreTime");
    }

    @Override
    public long getUptimeSinceRestore() {
        try {
            return (long)getUptimeSinceRestore.invoke(platformImpl);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return -1;
        }
    }

    @Override
    public long getRestoreTime() {
        try {
            return (long)getRestoreTime.invoke(platformImpl);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return -1;
        }
    }

    @Override
    public ObjectName getObjectName() {
        return platformImpl.getObjectName();
    }
}
