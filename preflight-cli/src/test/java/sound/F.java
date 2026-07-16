package sound;

/** Repository-owned decoded-audio object fixture for packaged agent testing. */
public final class F {
    public final byte[] pcm;
    public final int sampleRate;

    public F(byte[] pcm, int sampleRate) {
        this.pcm = pcm.clone();
        this.sampleRate = sampleRate;
    }
}
