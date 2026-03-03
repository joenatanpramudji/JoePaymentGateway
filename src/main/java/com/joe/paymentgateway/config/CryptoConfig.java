package com.joe.paymentgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the cryptographic subsystem.
 *
 * <p>Binds to properties under the {@code app.crypto} prefix in application YAML files.
 * The BDK (Base Derivation Key) is the root secret from which all DUKPT keys are derived.</p>
 *
 * <h3>Environment-specific behavior:</h3>
 * <ul>
 *   <li><strong>local/dev:</strong> BDK is a hardcoded test value in the YAML file.</li>
 *   <li><strong>prod:</strong> BDK MUST come from the {@code CRYPTO_BDK} environment variable.
 *       In a real deployment, this would be stored in an HSM (Hardware Security Module).</li>
 * </ul>
 *
 * <h3>YAML binding:</h3>
 * <pre>
 * app:
 *   crypto:
 *     bdk: "0123456789ABCDEF..."
 *     key-store-type: IN_MEMORY
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "app.crypto")
public class CryptoConfig {

    /**
     * The Base Derivation Key as a 64-character hex string (256-bit AES key).
     * This is the root secret for all DUKPT key derivations.
     */
    private String bdk;

    /**
     * The type of key storage backend.
     * IN_MEMORY for dev/test, SECURE for production (HSM integration point).
     */
    private String keyStoreType;

    public String getBdk() {
        return bdk;
    }

    public void setBdk(String bdk) {
        this.bdk = bdk;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }
}
