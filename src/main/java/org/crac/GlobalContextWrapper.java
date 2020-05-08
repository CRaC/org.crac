package org.crac;

class GlobalContextWrapper implements Context<Resource> {
    @Override
    public void beforeCheckpoint() throws CheckpointException {
        throw new RuntimeException("should not call this");
    }

    @Override
    public void afterRestore() throws RestoreException {
        throw new RuntimeException("should not call this");
    }

    GlobalContextWrapper() {
    }

    @Override
    public void register(Resource r) {
        Core.register(r);
    }
}

