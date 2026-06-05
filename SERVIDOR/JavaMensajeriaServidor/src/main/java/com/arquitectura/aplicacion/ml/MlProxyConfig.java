package com.arquitectura.aplicacion.ml;

import java.util.Properties;

/**
 * Singleton de configuracion del proxy ML (servicio Python FastAPI).
 * Se inicializa una vez al arrancar el servidor via {@link #configurar(Properties)}.
 * Centraliza host y puerto del servicio Python para que los handlers no los hardcodeen.
 */
public class MlProxyConfig {

    private static MlProxyConfig instancia;

    private String host;
    private int    puerto;

    private MlProxyConfig() {}

    /**
     * Lee {@code ml.host} y {@code ml.port} de las propiedades del servidor y
     * crea la instancia singleton. Debe llamarse antes de cualquier {@link #getInstance()}.
     */
    public static synchronized void configurar(Properties props) {
        instancia = new MlProxyConfig();
        instancia.host   = props.getProperty("ml.host", "localhost");
        instancia.puerto = Integer.parseInt(props.getProperty("ml.port", "8000"));
    }

    /**
     * @throws IllegalStateException si {@link #configurar(Properties)} no fue llamado aun.
     */
    public static MlProxyConfig getInstance() {
        if (instancia == null) {
            throw new IllegalStateException("MlProxyConfig no fue inicializado. Llamar configurar(Properties) primero.");
        }
        return instancia;
    }

    /** Retorna la URL base del servicio ML, ej: {@code http://localhost:8000}. */
    public String getBaseUrl() {
        return "http://" + host + ":" + puerto;
    }

    public String getHost()  { return host;   }
    public int    getPuerto(){ return puerto; }
}
