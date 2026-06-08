package com.saanjha.modules.auth.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Validated
@ConfigurationProperties(prefix = "rsa")
public record RsaKeyProperties(
        @NotNull(message = "RSA Public Key must be provided")
        RSAPublicKey publicKey,

        @NotNull(message = "RSA Private Key must be provided")
        RSAPrivateKey privateKey
) {}