package com.example.coreclash.core.runtime;

import com.example.coreclash.core.domain.MatchStatePort;

/**
 * Container de portas para desacoplar render (LibGDX) da lógica/plataforma.
 */
public record CoreServices(
        MatchStatePort matchStatePort,
        RuntimeConfig runtimeConfig
) {
}
