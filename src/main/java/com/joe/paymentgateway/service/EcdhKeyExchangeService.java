package com.joe.paymentgateway.service;

import com.joe.paymentgateway.exception.CryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Implements Elliptic Curve Diffie-Hellman (ECDH) key exchange for secure
 * delivery of the Initial PIN Encryption Key (IPEK) to terminals.
 *
 * <h3>Purpose:</h3>
 * <p>Before any transactions can occur, the terminal needs an IPEK (derived from the
 * gateway's BDK). ECDH allows the gateway to securely transmit this IPEK to the
 * terminal over an untrusted network without either party sharing their private key.</p>
 *
 * <h3>Protocol Flow:</h3>
 * <ol>
 *   <li>Terminal generates an ECDH key pair and sends its public key to the gateway.</li>
 *   <li>Gateway generates its own ECDH key pair.</li>
 *   <li>Gateway computes a shared secret using its private key + terminal's public key.</li>
 *   <li>Gateway encrypts the IPEK with the shared secret (AES-GCM) and sends:
 *       its public key + encrypted IPEK to the terminal.</li>
 *   <li>Terminal computes the same shared secret and decrypts the IPEK.</li>
 *   <li>Both sides discard ECDH keys — DUKPT handles all future key management.</li>
 * </ol>
 *
 * <p>Uses the secp256r1 (P-256) curve, which is NIST-approved and widely supported.</p>
 */
@Service
public class EcdhKeyExchangeService {

    private static final Logger log = LoggerFactory.getLogger(EcdhKeyExchangeService.class);

    private static final String EC_ALGORITHM = "EC";
    private static final String ECDH_ALGORITHM = "ECDH";
    private static final String CURVE_NAME = "secp256r1";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    /**
     * Generates a new ECDH key pair on the secp256r1 (P-256) curve.
     *
     * <p>The public key is shared with the other party; the private key
     * is kept secret and used to derive the shared secret.</p>
     *
     * @return a new EC key pair
     * @throws CryptoException if key generation fails
     */
    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(EC_ALGORITHM);
            keyPairGenerator.initialize(new ECGenParameterSpec(CURVE_NAME));
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            log.debug("Generated ECDH key pair on curve {}", CURVE_NAME);
            return keyPair;
        } catch (Exception e) {
            throw new CryptoException("Failed to generate ECDH key pair", e);
        }
    }

    /**
     * Encodes an EC public key to a Base64 string for transmission over JSON.
     *
     * @param keyPair the key pair containing the public key to encode
     * @return the X.509-encoded public key as a Base64 string
     */
    public String encodePublicKey(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * Decodes a Base64-encoded X.509 EC public key received from the terminal.
     *
     * @param publicKeyBase64 the Base64-encoded public key string
     * @return the reconstructed {@link PublicKey} object
     * @throws CryptoException if the key format is invalid
     */
    public PublicKey decodePublicKey(String publicKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(EC_ALGORITHM);
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new CryptoException("Failed to decode terminal public key", e);
        }
    }

    /**
     * Derives the shared secret from our private key and the other party's public key.
     *
     * <p>Both sides independently compute the same shared secret:
     * terminal uses (terminalPrivate, gatewayPublic) and
     * gateway uses (gatewayPrivate, terminalPublic). The results are identical
     * due to the mathematical properties of elliptic curves.</p>
     *
     * <p>The raw ECDH output is passed through SHA-256 to produce a uniform
     * 256-bit key suitable for AES-256 encryption.</p>
     *
     * @param gatewayKeyPair    the gateway's ECDH key pair
     * @param terminalPublicKey the terminal's public key
     * @return the 256-bit shared secret as a byte array
     * @throws CryptoException if key agreement fails
     */
    public byte[] deriveSharedSecret(KeyPair gatewayKeyPair, PublicKey terminalPublicKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance(ECDH_ALGORITHM);
            keyAgreement.init(gatewayKeyPair.getPrivate());
            keyAgreement.doPhase(terminalPublicKey, true);
            byte[] rawSecret = keyAgreement.generateSecret();

            // Hash the raw ECDH output to get a uniform 256-bit key
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] sharedSecret = sha256.digest(rawSecret);

            log.debug("Derived ECDH shared secret (SHA-256 of raw agreement)");
            return sharedSecret;
        } catch (Exception e) {
            throw new CryptoException("Failed to derive ECDH shared secret", e);
        }
    }

    /**
     * Encrypts the IPEK with the shared secret using AES-256-GCM.
     *
     * <p>AES-GCM provides both confidentiality and integrity. The IV is randomly
     * generated and prepended to the ciphertext so the terminal can extract it
     * for decryption.</p>
     *
     * <p>Output format: [12-byte IV][ciphertext + GCM tag]</p>
     *
     * @param ipekHex      the IPEK as a hex string to encrypt
     * @param sharedSecret the 256-bit shared secret derived from ECDH
     * @return the encrypted IPEK as a Base64 string (IV prepended)
     * @throws CryptoException if encryption fails
     */
    public String encryptIpekWithSharedSecret(String ipekHex, byte[] sharedSecret) {
        try {
            byte[] ipekBytes = ipekHex.getBytes();

            // Generate a random 12-byte IV for GCM
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);

            // Encrypt with AES-256-GCM
            Cipher cipher = Cipher.getInstance(AES_GCM);
            SecretKeySpec keySpec = new SecretKeySpec(sharedSecret, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] encrypted = cipher.doFinal(ipekBytes);

            // Prepend IV to ciphertext: [IV (12 bytes)][ciphertext + tag]
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new CryptoException("Failed to encrypt IPEK with shared secret", e);
        }
    }

    /**
     * Decrypts the IPEK that was encrypted with AES-256-GCM using the shared secret.
     *
     * <p>Expects the input format: Base64([12-byte IV][ciphertext + GCM tag]).</p>
     *
     * @param encryptedIpekBase64 the encrypted IPEK as a Base64 string
     * @param sharedSecret        the 256-bit shared secret derived from ECDH
     * @return the decrypted IPEK as a hex string
     * @throws CryptoException if decryption fails (wrong key or tampered data)
     */
    public String decryptIpekWithSharedSecret(String encryptedIpekBase64, byte[] sharedSecret) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedIpekBase64);

            // Extract IV and ciphertext
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            // Decrypt with AES-256-GCM
            Cipher cipher = Cipher.getInstance(AES_GCM);
            SecretKeySpec keySpec = new SecretKeySpec(sharedSecret, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted);
        } catch (Exception e) {
            throw new CryptoException("Failed to decrypt IPEK with shared secret", e);
        }
    }
}
