package io.spokestack.spokestack.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Utilities for working with Spokestack-specific cryptography such as request
 * signing.
 */
public final class Crypto {
    private static final String HMAC_TYPE = "HmacSHA256";

    /**
     * private constructor for utility class.
     */
    private Crypto() {
    }

    /**
     * Sign a string (presumably an http request body) and base-64 encode it.
     *
     * @param body   The body to sign
     * @param secret The signing key
     * @return The signed body
     * @throws IllegalArgumentException if the secret key is invalid
     */
    public static String signBody(String body, String secret)
          throws IllegalArgumentException {
        String signed;
        try {
            Mac hmacAlgo = Mac.getInstance(HMAC_TYPE);
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, HMAC_TYPE);
            hmacAlgo.init(keySpec);
            byte[] macData = hmacAlgo.doFinal(
                  body.getBytes(StandardCharsets.UTF_8));
            signed = Base64.encode(macData);
        } catch (NoSuchAlgorithmException e) {
            // not documented above because this should never happen
            throw new IllegalArgumentException("Invalid HMAC algorithm");
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid secret key");
        }

        return signed;
    }
}
