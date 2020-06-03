package org.crac;

class GlobalContextWrapper extends Context<Resource> {
    GlobalContextWrapper() {
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws CheckpointException {
        throw new RuntimeException("should not call this");

    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws RestoreException {
        throw new RuntimeException("should not call this");

    }

    @Override
    public void register(Resource r) {
        Core.register(r);
    }
}

