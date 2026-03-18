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

import org.crac.CheckpointException;
import org.crac.RestoreException;
import org.crac.impl.Proxy;

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
    long getUptimeSinceRestore();

    /**
     * Returns the time when the Java virtual machine restore was initiated.
     * The value is the number of milliseconds since the start of the epoch.
     * If the machine was not restored, returns -1.
     *
     * @see RuntimeMXBean#getUptime()
     * @return start time of the Java virtual machine in milliseconds.
     */
    long getRestoreTime();

    /**
     * Checks whether current JDK implements CRaC. The {@link #checkpointRestore()} method
     * would throw an {@link UnsupportedOperationException} if it is not supported.
     *
     * @return true if the JDK implements CRaC functionality.
     */
    boolean isImplemented();

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
    void checkpointRestore() throws CheckpointException, RestoreException;

    /**
     * Returns the implementation of the MXBean.
     *
     * @return implementation of the MXBean.
     */
    static CRaCMXBean getCRaCMXBean() {
        Class<?> iface;
        try {
            iface = Class.forName("jdk.crac.management.CRaCMXBean");
        } catch (ClassNotFoundException e) {
            return new NoImpl();
        }
        @SuppressWarnings("unchecked")
        PlatformManagedObject impl = ManagementFactory.getPlatformMXBean((Class<? extends PlatformManagedObject>) iface);
        if (impl == null) {
            return new NoImpl();
        }
        Proxy proxy = Proxy.instance;
        if (proxy == null) {
            throw new IllegalStateException("Proxy instantiation failed (CRaCMXBean present but incompatible JDK?)");
        }
        try {
            return new CRaCImpl(proxy, iface, impl);
        } catch (NoSuchMethodException e) {
            return new NoImpl();
        }
    }
}
