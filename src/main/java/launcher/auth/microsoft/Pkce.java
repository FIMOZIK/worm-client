package launcher.auth.microsoft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** PKCE (RFC 7636) — code_verifier + S256 code_challenge. */
final class Pkce {

    final String verifier;
    final String challenge;
    final String method = "S256";

    private Pkce(String verifier, String challenge) {
        this.verifier = verifier;
        this.challenge = challenge;
    }

    static Pkce generate() {
        byte[] raw = new byte[64];
        new SecureRandom().nextBytes(raw);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String challenge = s256(verifier);
        return new Pkce(verifier, challenge);
    }

    private static String s256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("sha256 not available", e);
        }
    }
}