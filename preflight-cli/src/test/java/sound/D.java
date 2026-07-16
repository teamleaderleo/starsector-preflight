package sound;

/** Repository-owned constructor consumer fixture for packaged agent testing. */
public final class D {
    private final F decoded;

    public D(F decoded) {
        this.decoded = decoded;
    }

    public int sampleRate() {
        return decoded.sampleRate;
    }
}
