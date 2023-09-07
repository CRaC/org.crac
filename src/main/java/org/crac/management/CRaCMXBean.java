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

import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.lang.management.RuntimeMXBean;

/**
 * Management interface for the CRaC functionality of the Java virtual machine.
 */
public interface CRaCMXBean extends PlatformManagedObject {

    /**
     * Returns the time since the Java virtual machine restore was initiated.
     * If the machine was not restored, returns -1.
     *
     * @see RuntimeMXBean#getStartTime()
     * @return uptime of the Java virtual machine in milliseconds.
     */
    public long getUptimeSinceRestore();

    /**
     * Returns the time when the Java virtual machine restore was initiated.
     * The value is the number of milliseconds since the start of the epoch.
     * If the machine was not restored, returns -1.
     *
     * @see RuntimeMXBean#getUptime()
     * @return start time of the Java virtual machine in milliseconds.
     */
    public long getRestoreTime();

    /**
     * Returns the implementation of the MXBean.
     *
     * @return implementation of the MXBean.
     */
    public static CRaCMXBean getCRaCMXBean() {
        Class iface;
        try {
            iface = Class.forName("jdk.crac.management.CRaCMXBean");
        } catch (ClassNotFoundException e) {
            return new NoImpl();
        }
        PlatformManagedObject impl = ManagementFactory.getPlatformMXBean(iface);
        if (impl == null) {
            return new NoImpl();
        }
        try {
            return new CRaCImpl(iface, impl);
        } catch (NoSuchMethodException e) {
            return new NoImpl();
        }
    }
}
