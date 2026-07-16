package sound;

/** Repository-owned direct consumer fixture. */
public final class ooOO {
    private F decoded;

    public int consume(F value) {
        decoded = value;
        return decoded.pcm.length;
    }
}
