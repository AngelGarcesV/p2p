package com.cliente.application.service;

import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.payload.PayloadConectar;
import com.cliente.domain.enums.Protocol;
import com.cliente.infrastructure.protocol.ServerJsonUtil;
import com.cliente.infrastructure.socket.SocketClient;
import com.cliente.infrastructure.socket.TcpSocketClient;
import com.cliente.infrastructure.socket.UdpSocketClient;

import java.io.IOException;
import java.util.UUID;

public class ConnectionService {

    private static ConnectionService instance;

    private SocketClient socketClient;
    private String clientId;
    private String username;
    private String host;
    private int port;
    private Protocol protocol;
    private boolean connected;

    private ConnectionService() {}

    public static ConnectionService getInstance() {
        if (instance == null) instance = new ConnectionService();
        return instance;
    }

    public void connect(String host, int port, Protocol protocol, String username) throws Exception {
        socketClient = (protocol == Protocol.TCP) ? new TcpSocketClient() : new UdpSocketClient();
        socketClient.connect(host, port);

        Protocolo proto = (protocol == Protocol.TCP) ? Protocolo.TCP : Protocolo.UDP;
        PayloadConectar payload = new PayloadConectar(username);
        Mensaje<PayloadConectar> mensaje = ServerJsonUtil.buildRequest(Accion.CONECTAR, payload, null, proto);

        String json = ServerJsonUtil.toJson(mensaje);
        String respJson = socketClient.sendAndReceive(json);
        Respuesta<?> respuesta = ServerJsonUtil.fromJson(respJson, Respuesta.class);

        if (respuesta.getEstado() == Estado.ERROR) {
            socketClient.disconnect();
            String err = (respuesta.getError() != null)
                    ? respuesta.getError().getMensaje() : "Error desconocido";
            throw new IOException("Servidor rechazó la conexión: " + err);
        }

        // Server does not currently echo clientId; generate one locally
        if (respuesta.getMensaje() != null
                && respuesta.getMensaje().getMetadata() != null
                && respuesta.getMensaje().getMetadata().getClientId() != null) {
            this.clientId = respuesta.getMensaje().getMetadata().getClientId();
        } else {
            this.clientId = UUID.randomUUID().toString();
        }

        this.host = host;
        this.port = port;
        this.protocol = protocol;
        this.username = username;
        this.connected = true;
    }

    public synchronized Respuesta<?> send(Mensaje<?> mensaje) throws Exception {
        if (!connected || socketClient == null)
            throw new IllegalStateException("No hay conexión activa con el servidor.");
        if (clientId != null && mensaje.getMetadata() != null)
            mensaje.getMetadata().setClientId(clientId);
        String json = ServerJsonUtil.toJson(mensaje);
        String respJson = socketClient.sendAndReceive(json);
        return ServerJsonUtil.fromJson(respJson, Respuesta.class);
    }

    /**
     * Igual que {@link #send(Mensaje)} pero con timeout de lectura personalizado.
     * Solo aplica a conexiones TCP; para UDP se usa el timeout estándar.
     */
    public synchronized Respuesta<?> send(Mensaje<?> mensaje, int timeoutMs) throws Exception {
        if (!connected || socketClient == null)
            throw new IllegalStateException("No hay conexión activa con el servidor.");
        if (clientId != null && mensaje.getMetadata() != null)
            mensaje.getMetadata().setClientId(clientId);
        String json = ServerJsonUtil.toJson(mensaje);
        String respJson;
        if (socketClient instanceof TcpSocketClient tcpClient) {
            respJson = tcpClient.sendAndReceive(json, timeoutMs);
        } else {
            respJson = socketClient.sendAndReceive(json);
        }
        return ServerJsonUtil.fromJson(respJson, Respuesta.class);
    }

    /**
     * Envía un mensaje SIEMPRE por TCP con timeout personalizado,
     * independientemente del protocolo actual del cliente.
     * Usar para operaciones que requieren TCP obligatorio (ej: CLASIFICAR_GENERO).
     */
    public synchronized Respuesta<?> sendViaTcp(Mensaje<?> mensaje, int timeoutMs) throws Exception {
        if (!connected) throw new IllegalStateException("No hay conexión activa con el servidor.");
        if (clientId != null && mensaje.getMetadata() != null)
            mensaje.getMetadata().setClientId(clientId);
        String json = ServerJsonUtil.toJson(mensaje);
        TcpSocketClient tcpClient = new TcpSocketClient();
        tcpClient.connect(host, port);
        String respJson = tcpClient.sendAndReceive(json, timeoutMs);
        return ServerJsonUtil.fromJson(respJson, Respuesta.class);
    }

    public void disconnect() {
        if (connected && socketClient != null) {
            try {
                Protocolo proto = (protocol == Protocol.TCP) ? Protocolo.TCP : Protocolo.UDP;
                Mensaje<?> msg = ServerJsonUtil.buildRequest(Accion.DESCONECTAR, null, clientId, proto);
                String json = ServerJsonUtil.toJson(msg);
                socketClient.sendAndReceive(json);
            } catch (Exception ignored) {
                // Best-effort — si el servidor no responde, desconectamos localmente igual
            }
        }
        if (socketClient != null) socketClient.disconnect();
        connected = false;
        clientId = null;
        username = null;
    }

    public boolean isConnected() {
        return connected && socketClient != null && socketClient.isConnected();
    }

    public String getClientId()  { return clientId; }
    public String getUsername()  { return username; }
    public String getHost()      { return host; }
    public int    getPort()      { return port; }
    public Protocol getProtocol(){ return protocol; }
    public SocketClient getSocketClient() { return socketClient; }
}
