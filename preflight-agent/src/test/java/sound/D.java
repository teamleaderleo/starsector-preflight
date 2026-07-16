package sound;

/** Repository-owned consumer fixture with a constructor accepting the decoded-audio object. */
public final class D {
    private final F decoded;

    public D(F decoded) {
        this.decoded = decoded;
    }

    public int sampleRate() {
        return decoded.sampleRate;
    }
}
