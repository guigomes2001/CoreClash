package com.example.coreclash.core.runtime;

public record RuntimeConfig(
        float worldWidth,
        float worldHeight,
        boolean enableParticles,
        boolean enablePostFx
) {
    public static RuntimeConfig mobileDefault() {
        return new RuntimeConfig(1080f, 1920f, true, true);
    }
}
