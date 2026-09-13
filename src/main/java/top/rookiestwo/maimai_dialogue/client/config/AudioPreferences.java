package top.rookiestwo.maimai_dialogue.client.config;

public record AudioPreferences(double bgmVolume, double soundVolume, double typewriterVolume, boolean typewriterEnabled) {
    public static final AudioPreferences DEFAULT = new AudioPreferences(1, 1, 1, true);

    public AudioPreferences {
        for (double value : new double[]{bgmVolume, soundVolume, typewriterVolume}) {
            if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Audio volume must be between 0 and 1.");
        }
    }
}
