package dev.pregen.generation;

/** Callbacks from a {@link GenerationTask}. Invoked on the task's serial executor, never on a server thread. */
public interface TaskObserver {

    enum PauseReason {
        USER,
        ERRORS,
        SHUTDOWN
    }

    void onPaused(GenerationTask task, TaskCheckpoint checkpoint, PauseReason reason);

    void onCompleted(GenerationTask task);

    void onCancelled(GenerationTask task);

    void onChunkFailed(GenerationTask task, int chunkX, int chunkZ, Throwable error);
}
