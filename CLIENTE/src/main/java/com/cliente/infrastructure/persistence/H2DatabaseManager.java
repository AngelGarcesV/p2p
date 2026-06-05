package com.cliente.infrastructure.persistence;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class H2DatabaseManager {

    private static final Logger LOG = Logger.getLogger(H2DatabaseManager.class.getName());

    // DB en memoria: cada proceso JVM tiene su propio espacio aislado,
    // por lo que múltiples clientes en la misma máquina no comparten datos.
    private static final String URL = "jdbc:h2:mem:clientes;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private static Connection sharedConnection;
    private static final Object LOCK = new Object();

    private H2DatabaseManager() {}

    public static Connection getConnection() throws SQLException {
        synchronized (LOCK) {
            if (sharedConnection == null || sharedConnection.isClosed()) {
                sharedConnection = DriverManager.getConnection(URL, USER, PASSWORD);
            }
            return sharedConnection;
        }
    }

    public static void inicializar() {
        synchronized (LOCK) {
            try {
                Connection conn = getConnection();
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS mensajes_enviados (
                            id VARCHAR(36) PRIMARY KEY,
                            autor VARCHAR(255),
                            destinatario VARCHAR(255),
                            ip_remitente VARCHAR(45),
                            contenido TEXT NOT NULL,
                            hash_sha256 VARCHAR(88),
                            contenido_cifrado LONGVARCHAR,
                            fecha_envio TIMESTAMP NOT NULL,
                            servidor_host VARCHAR(255),
                            servidor_puerto INT
                        )
                    """);
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS archivos_enviados (
                            id VARCHAR(36) PRIMARY KEY,
                            remitente VARCHAR(255),
                            ip_remitente VARCHAR(45),
                            nombre_archivo VARCHAR(255) NOT NULL,
                            extension VARCHAR(50),
                            ruta_archivo CLOB,
                            hash_sha256 VARCHAR(88),
                            contenido_cifrado LONGVARCHAR,
                            contenido BLOB,
                            tamano BIGINT NOT NULL,
                            fecha_envio TIMESTAMP NOT NULL,
                            servidor_host VARCHAR(255),
                            servidor_puerto INT
                        )
                    """);
                    try {
                        stmt.execute("ALTER TABLE archivos_enviados ADD COLUMN IF NOT EXISTS contenido BLOB");
                    } catch (Exception ignored) {}
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error inicializando H2", e);
            }
        }
    }

    public static void cerrar() {
        synchronized (LOCK) {
            try {
                if (sharedConnection != null && !sharedConnection.isClosed()) {
                    sharedConnection.close();
                    sharedConnection = null;
                }
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Error cerrando H2: " + e.getMessage(), e);
            }
        }
    }
}
