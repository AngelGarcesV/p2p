package com.arquitectura;

import com.arquitectura.aplicacion.ProcesadorMensajes;
import com.arquitectura.aplicacion.RespuestaSender;
import com.arquitectura.aplicacion.concurrencia.AtencionClienteTask;
import com.arquitectura.aplicacion.ml.MlProxyConfig;
import com.arquitectura.aplicacion.router.MensajeRouter;
import com.arquitectura.aplicacion.router.MensajeRouterFactory;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.PeerConfig;
import com.arquitectura.dominio.modelo.PeerConocidoModel;
import com.arquitectura.dominio.repositorios.JpaPeerConocidoRepository;
import com.arquitectura.comun.dto.PaqueteDatos;
import com.arquitectura.infraestructura.concurrencia.ObjectPool;
import com.arquitectura.infraestructura.logs.LogConfig;
import com.arquitectura.infraestructura.persistencia.ConexionMySql;
import com.arquitectura.infraestructura.seguridad.CryptoConfig;
import com.arquitectura.infraestructura.transporte.ProtocoloTransporte;
import com.arquitectura.infraestructura.transporte.ProtocoloTransporteFactory;
import com.arquitectura.rest.ServidorRestApp;
import org.springframework.boot.SpringApplication;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {

        LogConfig.configureRootLogger();

        Properties properties = new Properties();

        try (InputStream inputStream = Main.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("No se encontro application.properties en el classpath");
            }
            properties.load(inputStream);
            aplicarVariablesEntorno(properties);

            String protocolo = properties.getProperty("transfer-protocol");
            int puerto = Integer.parseInt(properties.getProperty("tcp.port"));
            int maxClientes = Integer.parseInt(properties.getProperty("max-clients", "10"));
            long sessionTimeoutMinutos = Long.parseLong(properties.getProperty("session.timeout.minutes", "30"));

            CryptoConfig.configurar(properties);
            ConexionMySql.configurar(properties);
            MlProxyConfig.configurar(properties);
            LogConfig.configureDatabaseLogging();

            // ---- Peer-to-peer federation bootstrap ----
            String servidorId = properties.getProperty("server.id",
                    "servidor-" + UUID.randomUUID().toString().substring(0, 8));
            int maxIntentos = Integer.parseInt(properties.getProperty("peer.reconnect.max-attempts", "5"));
            String peersRaw = properties.getProperty("server.peers", "");

            List<PeerConfig> peerConfigs = new ArrayList<>();
            if (!peersRaw.isBlank()) {
                for (String entry : peersRaw.split(",")) {
                    String[] parts = entry.trim().split(":");
                    if (parts.length == 3) {
                        peerConfigs.add(new PeerConfig(parts[0].trim(), parts[1].trim(),
                                Integer.parseInt(parts[2].trim())));
                    } else if (parts.length == 2) {
                        peerConfigs.add(new PeerConfig(parts[0].trim(), parts[0].trim(),
                                Integer.parseInt(parts[1].trim())));
                    }
                }
            }

            // Fusionar con peers conocidos persistidos en DB (sobreviven reinicios)
            // Los del properties tienen prioridad si el servidorId ya está en la lista
            try {
                List<PeerConocidoModel> peersEnDb = new JpaPeerConocidoRepository().listarTodos();
                java.util.Set<String> idsYaAgregados = new java.util.HashSet<>();
                for (PeerConfig pc : peerConfigs) idsYaAgregados.add(pc.getServidorId());
                for (PeerConocidoModel p : peersEnDb) {
                    if (!idsYaAgregados.contains(p.getServidorId())
                            && !p.getServidorId().equals(servidorId)) {
                        peerConfigs.add(new PeerConfig(p.getServidorId(), p.getHost(), p.getPuerto()));
                        LOGGER.info(() -> "Peer recuperado de DB: " + p.getServidorId()
                                + " @ " + p.getHost() + ":" + p.getPuerto());
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("No se pudieron recuperar peers desde DB: " + e.getMessage());
            }

            // server.host permite declarar la IP pública explícitamente.
            // Evita que servidores con VPN/múltiples NICs anuncien la IP incorrecta.
            String servidorHost = properties.getProperty("server.host", "").trim();
            String hostFinal = servidorHost.isBlank() ? null : servidorHost;

            GestorServidoresPeer.inicializar(servidorId, puerto, hostFinal, peerConfigs, maxIntentos);
            GestorServidoresPeer.getInstance().conectarAPeers();
            LOGGER.info(() -> "GestorServidoresPeer inicializado. servidorId=" + servidorId
                    + " | peers configurados=" + peerConfigs.size());
            // -------------------------------------------

            ProtocoloTransporte transporte = ProtocoloTransporteFactory.crear(protocolo);
            transporte.iniciar(puerto);

            MensajeRouter router = MensajeRouterFactory.crearRouter();
            RespuestaSender sender = new RespuestaSender();
            ProcesadorMensajes procesador = new ProcesadorMensajes(router);
            GestorSesiones.getInstance().configurar(maxClientes, Duration.ofMinutes(sessionTimeoutMinutos));

            Thread springThread = new Thread(() -> {
                SpringApplication app = new SpringApplication(ServidorRestApp.class);
                app.run();
            });
            springThread.setDaemon(true);
            springThread.setName("spring-rest-server");
            springThread.start();
            Thread.sleep(2000);

            ExecutorService ejecutorClientes = Executors.newFixedThreadPool(maxClientes);
            ObjectPool<AtencionClienteTask> poolTareas = new ObjectPool<>(maxClientes, AtencionClienteTask::new);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    transporte.detener();
                    ejecutorClientes.shutdown();
                    if (!ejecutorClientes.awaitTermination(5, TimeUnit.SECONDS)) {
                        ejecutorClientes.shutdownNow();
                    }
                    GestorSesiones.getInstance().cerrarTodas();
                    ConexionMySql.cerrar();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error durante el apagado del servidor", e);
                }
            }));

            LOGGER.info(() -> "Servidor iniciado. Protocolo: " + transporte.getNombre() + " | Puerto: " + puerto + " | max-clients=" + maxClientes);

            long requestCounter = 0;

            while (true) {
                PaqueteDatos paquete = transporte.recibir();
                requestCounter++;
                final long reqNum = requestCounter;

                AtencionClienteTask tarea = poolTareas.tomar(200, TimeUnit.MILLISECONDS);

                if (tarea == null) {
                    LOGGER.warning(() -> "[Request #" + reqNum + "] Capacidad maxima alcanzada. Rechazando conexion. Sesiones activas: " + GestorSesiones.getInstance().sesionesActivas());
                    cerrarPaquete(paquete);
                    continue;
                }

                tarea.preparar(paquete, procesador, sender, transporte, () -> poolTareas.devolver(tarea));
                ejecutorClientes.execute(tarea);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Servidor interrumpido", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error en servidor", e);
        }
    }

    private static void aplicarVariablesEntorno(Properties props) {
        sobreescribir(props, "server.id",       "SERVER_ID");
        sobreescribir(props, "server.host",     "SERVER_HOST");
        sobreescribir(props, "server.peers",    "SERVER_PEERS");
        sobreescribir(props, "tcp.port",        "TCP_PORT");
        sobreescribir(props, "ml.host",         "ML_HOST");
        sobreescribir(props, "ml.port",         "ML_PORT");
        sobreescribir(props, "mysql.url",       "MYSQL_URL");
        sobreescribir(props, "mysql.user",      "MYSQL_USER");
        sobreescribir(props, "mysql.password",  "MYSQL_PASSWORD");
    }

    private static void sobreescribir(Properties props, String clave, String envVar) {
        String valor = System.getenv(envVar);
        if (valor != null && !valor.isBlank()) {
            props.setProperty(clave, valor);
        }
    }

    private static void cerrarPaquete(PaqueteDatos paquete) {
        try {
            if (paquete.getSocket() != null && !paquete.getSocket().isClosed()) {
                try {
                    var writer = new java.io.BufferedWriter(
                            new java.io.OutputStreamWriter(paquete.getSocket().getOutputStream()));
                    writer.write("{\"estado\":\"ERROR\",\"error\":{\"codigo\":\"SERVIDOR_OCUPADO\",\"detalle\":\"Servidor a capacidad maxima, reintente luego\"}}");
                    writer.newLine();
                    writer.flush();
                } catch (Exception ignored) {}
                paquete.getSocket().close();
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error cerrando paquete rechazado", e);
        }
    }
}
