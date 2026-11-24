package art.arcane.auram.field;

/** Two immutable simulation frames and the time at which the current frame was published. */
public record FieldFrames<T>(T previous, T current, long publishedAt) {
    private static final FieldFrames<?> EMPTY = new FieldFrames<>(null, null, 0);

    @SuppressWarnings("unchecked")
    public static <T> FieldFrames<T> empty() {
        return (FieldFrames<T>) EMPTY;
    }

    public FieldFrames<T> advance(T next, boolean sameWindow) {
        T prior = sameWindow ? current : next;
        return prior == null && next == null ? empty() : new FieldFrames<>(prior, next, System.nanoTime());
    }
}
