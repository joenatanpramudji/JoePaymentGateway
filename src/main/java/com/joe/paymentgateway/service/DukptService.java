package com.joe.paymentgateway.service;

import com.joe.paymentgateway.exception.CryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Implements AES-256 DUKPT (Derived Unique Key Per Transaction) key derivation
 * following the ANSI X9.24-3 standard.
 *
 * <p>DUKPT provides forward secrecy for payment transactions. Each transaction
 * uses a unique encryption key derived from a Base Derivation Key (BDK) and
 * a Key Serial Number (KSN). Compromising one session key does not reveal
 * the BDK or any other session key.</p>
 *
 * <h3>Key Derivation Flow:</h3>
 * <ol>
 *   <li>The BDK and the initial KSN (with counter zeroed) are used to derive
 *       the Initial PIN Encryption Key (IPEK).</li>
 *   <li>For each transaction, the IPEK and the current KSN (with incrementing
 *       counter) are used to derive a unique session key.</li>
 *   <li>The session key encrypts/decrypts the PIN block for that single transaction.</li>
 * </ol>
 *
 * <h3>KSN Structure (12 bytes / 24 hex characters):</h3>
 * <ul>
 *   <li>Bytes 0–3: Key Set ID (identifies which BDK to use)</li>
 *   <li>Bytes 4–8: Terminal/Device ID</li>
 *   <li>Bytes 9–11: Transaction Counter (auto-increments per transaction)</li>
 * </ul>
 */
@Service
public class DukptService {

    private static final Logger log = LoggerFactory.getLogger(DukptService.class);

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_ECB_NO_PADDING = "AES/ECB/NoPadding";

    /**
     * Derives the Initial PIN Encryption Key (IPEK) from the BDK and the initial KSN.
     *
     * <p>The initial KSN has its transaction counter portion zeroed out before derivation.
     * This IPEK is what gets injected into the terminal during the key exchange setup.</p>
     *
     * @param bdkHex the Base Derivation Key as a 64-character hex string (256-bit AES key)
     * @param ksnHex the Key Serial Number as a 24-character hex string
     * @return the IPEK as a 64-character hex string
     * @throws CryptoException if derivation fails
     */
    public String deriveIpek(String bdkHex, String ksnHex) {
        try {
            byte[] bdk = HexFormat.of().parseHex(bdkHex);
            byte[] ksn = HexFormat.of().parseHex(ksnHex);

            // Zero out the transaction counter (last 3 bytes) to get the initial KSN
            byte[] initialKsn = Arrays.copyOf(ksn, ksn.length);
            initialKsn[ksn.length - 3] = 0;
            initialKsn[ksn.length - 2] = 0;
            initialKsn[ksn.length - 1] = 0;

            // Derive IPEK: AES-encrypt the initial KSN (padded to 32 bytes) with the BDK
            byte[] ksnPadded = new byte[32];
            System.arraycopy(initialKsn, 0, ksnPadded, 0, Math.min(initialKsn.length, 32));

            Cipher cipher = Cipher.getInstance(AES_ECB_NO_PADDING);
            SecretKeySpec keySpec = new SecretKeySpec(bdk, AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] ipek = cipher.doFinal(ksnPadded);

            String ipekHex = HexFormat.of().formatHex(ipek);
            log.debug("Derived IPEK for KSN: {}...{}", ksnHex.substring(0, 8),
                    ksnHex.substring(ksnHex.length() - 6));
            return ipekHex;
        } catch (Exception e) {
            throw new CryptoException("Failed to derive IPEK from BDK and KSN", e);
        }
    }

    /**
     * Derives a unique session key for a specific transaction from the IPEK and KSN.
     *
     * <p>The KSN includes the transaction counter, so each transaction produces
     * a different session key even though the IPEK remains the same.</p>
     *
     * @param ipekHex the Initial PIN Encryption Key as a 64-character hex string
     * @param ksnHex  the current KSN (with transaction counter) as a 24-character hex string
     * @return the session key as a 64-character hex string
     * @throws CryptoException if derivation fails
     */
    public String deriveSessionKey(String ipekHex, String ksnHex) {
        try {
            byte[] ipek = HexFormat.of().parseHex(ipekHex);
            byte[] ksn = HexFormat.of().parseHex(ksnHex);

            // Extract the transaction counter from the KSN (last 3 bytes)
            int counter = ((ksn[ksn.length - 3] & 0xFF) << 16)
                    | ((ksn[ksn.length - 2] & 0xFF) << 8)
                    | (ksn[ksn.length - 1] & 0xFF);

            // Derive session key by iterating through counter bits
            byte[] derivedKey = Arrays.copyOf(ipek, ipek.length);
            byte[] workingKsn = Arrays.copyOf(ksn, ksn.length);

            // Zero out counter in working KSN
            workingKsn[workingKsn.length - 3] = 0;
            workingKsn[workingKsn.length - 2] = 0;
            workingKsn[workingKsn.length - 1] = 0;

            // Process each set bit in the counter from MSB to LSB
            for (int bit = 0x800000; bit > 0; bit >>= 1) {
                if ((counter & bit) != 0) {
                    // Set this bit in the working KSN counter
                    workingKsn[workingKsn.length - 3] |= (byte) ((bit >> 16) & 0xFF);
                    workingKsn[workingKsn.length - 2] |= (byte) ((bit >> 8) & 0xFF);
                    workingKsn[workingKsn.length - 1] |= (byte) (bit & 0xFF);

                    // Derive next level key
                    derivedKey = deriveNextKey(derivedKey, workingKsn);
                }
            }

            String sessionKeyHex = HexFormat.of().formatHex(derivedKey);
            log.debug("Derived session key for KSN counter: {}", counter);
            return sessionKeyHex;
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Failed to derive session key from IPEK and KSN", e);
        }
    }

    /**
     * Decrypts an AES-encrypted PIN block using the provided session key.
     *
     * @param encryptedPinBlockHex the encrypted PIN block as a hex string
     * @param sessionKeyHex        the session key as a hex string
     * @return the decrypted PIN block as a hex string
     * @throws CryptoException if decryption fails
     */
    public String decryptPinBlock(String encryptedPinBlockHex, String sessionKeyHex) {
        try {
            byte[] encryptedPinBlock = HexFormat.of().parseHex(encryptedPinBlockHex);
            byte[] sessionKey = HexFormat.of().parseHex(sessionKeyHex);

            // Use only the first 32 bytes (256 bits) of the session key for AES-256
            byte[] aesKey = Arrays.copyOf(sessionKey, 32);

            Cipher cipher = Cipher.getInstance(AES_ECB_NO_PADDING);
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decryptedPinBlock = cipher.doFinal(encryptedPinBlock);

            return HexFormat.of().formatHex(decryptedPinBlock);
        } catch (Exception e) {
            throw new CryptoException("Failed to decrypt PIN block", e);
        }
    }

    /**
     * Encrypts a PIN block using the provided session key.
     * Used primarily by the terminal simulator for creating encrypted requests.
     *
     * @param pinBlockHex  the clear PIN block as a hex string
     * @param sessionKeyHex the session key as a hex string
     * @return the encrypted PIN block as a hex string
     * @throws CryptoException if encryption fails
     */
    public String encryptPinBlock(String pinBlockHex, String sessionKeyHex) {
        try {
            byte[] pinBlock = HexFormat.of().parseHex(pinBlockHex);
            byte[] sessionKey = HexFormat.of().parseHex(sessionKeyHex);

            byte[] aesKey = Arrays.copyOf(sessionKey, 32);

            Cipher cipher = Cipher.getInstance(AES_ECB_NO_PADDING);
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encryptedPinBlock = cipher.doFinal(pinBlock);

            return HexFormat.of().formatHex(encryptedPinBlock);
        } catch (Exception e) {
            throw new CryptoException("Failed to encrypt PIN block", e);
        }
    }

    /**
     * Internal key derivation step: encrypts the working KSN with the current key
     * to produce the next derived key in the DUKPT tree.
     */
    private byte[] deriveNextKey(byte[] currentKey, byte[] workingKsn) {
        try {
            // Pad KSN to 32 bytes for AES-256 block size alignment
            byte[] ksnPadded = new byte[32];
            System.arraycopy(workingKsn, 0, ksnPadded, 0, Math.min(workingKsn.length, 32));

            Cipher cipher = Cipher.getInstance(AES_ECB_NO_PADDING);
            SecretKeySpec keySpec = new SecretKeySpec(currentKey, AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return cipher.doFinal(ksnPadded);
        } catch (Exception e) {
            throw new CryptoException("Failed during DUKPT key derivation step", e);
        }
    }
}
