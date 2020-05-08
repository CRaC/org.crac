package org.crac;

/**
 * TODO
 */
public interface Context<R extends Resource> extends Resource {
    @Override
    void beforeCheckpoint() throws CheckpointException;

    @Override
    void afterRestore() throws RestoreException;

    /**
     * @param r TODO
     */
    void register(R r);
}
