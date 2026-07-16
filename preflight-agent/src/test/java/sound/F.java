package sound;

/** Repository-owned decoded-audio object fixture. */
public final class F {
    public final byte[] pcm;
    public final int sampleRate;

    public F(byte[] pcm, int sampleRate) {
        this.pcm = pcm.clone();
        this.sampleRate = sampleRate;
    }
}
