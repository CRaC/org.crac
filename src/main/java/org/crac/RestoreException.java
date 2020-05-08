package org.crac;

/**
 * TODO
 */
public class RestoreException extends CheckpointRestoreException {
    private static final long serialVersionUID = 0;

    /**
     * @param exceptions TODO
     */
    public RestoreException(Exception[] exceptions) {
        super(exceptions);
    }
}


