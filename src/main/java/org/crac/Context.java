package org.crac;

/**
 * TODO
 */
public abstract class Context<R extends Resource> implements Resource {

    /** The only constructor.
     */
    protected Context() {
    }

    @Override
    public abstract void beforeCheckpoint(Context<? extends Resource> context)
            throws CheckpointException;

    @Override
    public abstract void afterRestore(Context<? extends Resource> context)
            throws RestoreException;

    /**
     * @param resource TODO
     */
    public abstract void register(R resource);
}
