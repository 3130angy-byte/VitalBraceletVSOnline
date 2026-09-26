package org.example.chat;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * SSLContext que confía en un certificado si lo valida la lista de Java O
 * la de Windows. Nunca desactiva la verificación.
 *
 * Motivo (visto en esta laptop): las noticias fallaban con "PKIX path
 * building failed". Windows ve el certificado real de Google, pero Avast
 * intercepta el HTTPS de Java con su propio certificado raíz, que solo está
 * instalado en el almacén de Windows, no en el de Java.
 */
public final class SystemTrust {

    private static SSLContext context;

    private SystemTrust() {}

    public static synchronized SSLContext sslContext() {
        if (context != null) return context;
        try {
            List<X509TrustManager> managers = new ArrayList<>();
            managers.add(trustManagerFor(null)); // almacén propio de Java
            try {
                KeyStore windows = KeyStore.getInstance("Windows-ROOT");
                windows.load(null, null);
                managers.add(trustManagerFor(windows));
            } catch (Exception e) {
                System.out.println("[TLS] Almacén de Windows no disponible: " + e.getMessage());
            }

            X509TrustManager combined = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    managers.get(0).checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    CertificateException last = null;
                    for (X509TrustManager tm : managers) {
                        try {
                            tm.checkServerTrusted(chain, authType);
                            return;
                        } catch (CertificateException e) {
                            last = e;
                        }
                    }
                    throw last;
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return managers.stream().flatMap(m -> java.util.Arrays.stream(m.getAcceptedIssuers()))
                            .toArray(X509Certificate[]::new);
                }
            };

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{combined}, null);
            context = ctx;
        } catch (Exception e) {
            System.out.println("[TLS] Se usa la configuración por defecto de Java: " + e.getMessage());
            try {
                context = SSLContext.getDefault();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
        return context;
    }

    private static X509TrustManager trustManagerFor(KeyStore store) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager x) return x;
        }
        throw new IllegalStateException("Sin X509TrustManager");
    }
}
