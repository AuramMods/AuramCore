package art.arcane.auram.util;

/** A background scheduler can request a main-thread sample and read the last result. */
public interface ForegroundChunkDemand {
    boolean auram$samplePlayerChunkDemand();
}
