import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;

/**
 * Probes PKCS12 signing key access without logging passwords or exporting keys.
 * Use from CI under stdout/stderr suppression: a bad key exits nonzero.
 */
public final class VerifySigningKey {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("keystore path and alias required");
        String storePassword = System.getenv("CALENDARIK_STORE_PASSWORD");
        String keyPassword = System.getenv("CALENDARIK_KEY_PASSWORD");
        if (storePassword == null || keyPassword == null) throw new IllegalStateException("password missing");
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (FileInputStream input = new FileInputStream(args[0])) {
            store.load(input, storePassword.toCharArray());
        }
        if (!(store.getKey(args[1], keyPassword.toCharArray()) instanceof PrivateKey)) {
            throw new IllegalStateException("No private key under the selected alias");
        }
    }
}
