package org.crac;

/**
 * TODO
 */
public class CheckpointException extends CheckpointRestoreException {
    private static final long serialVersionUID = 0;

    /**
     * @param exceptions TODO
     */
    public CheckpointException(Exception[] exceptions) {
        super(exceptions);
    }
}
