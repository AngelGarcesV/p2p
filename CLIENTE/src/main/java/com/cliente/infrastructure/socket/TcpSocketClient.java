package com.cliente.infrastructure.socket;

import com.cliente.infrastructure.protocol.ProtocolConstants;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TcpSocketClient implements SocketClient {

    private String host;
    private int port;
    private boolean initialized;

    @Override
    public void connect(String host, int port) throws Exception {
        this.host = host;
        this.port = port;
        this.initialized = true;
    }

    @Override
    public String sendAndReceive(String json) throws Exception {
        return sendAndReceive(json, ProtocolConstants.READ_TIMEOUT);
    }

    /**
     * Envía {@code json} al servidor y espera la respuesta usando un timeout de lectura
     * personalizado. Útil para operaciones de larga duración como inferencia ML.
     *
     * @param json      JSON a enviar
     * @param timeoutMs timeout de lectura del socket en milisegundos
     */
    public String sendAndReceive(String json, int timeoutMs) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), ProtocolConstants.CONNECT_TIMEOUT);
            socket.setSoTimeout(timeoutMs);

            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            writer.write(json);
            writer.newLine();
            writer.flush();

            String response = reader.readLine();
            if (response == null) throw new IOException("Conexión cerrada por el servidor.");
            return response;
        }
    }


    public void sendFileStream(String transferId, FileInputStream fis,
                               int chunkSize, long totalBytes,
                               StreamProgressCallback progressCb) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), ProtocolConstants.CONNECT_TIMEOUT);
            // Sin timeout de lectura durante streaming — el servidor puede tardar en disco
            socket.setSoTimeout(0);

            OutputStream os = socket.getOutputStream();
            InputStream is  = socket.getInputStream();

            // Señal de streaming
            os.write(ProtocolConstants.STREAM_SIGNAL);
            os.flush();

            byte[] buffer = new byte[chunkSize];
            long chunkIndex = 0;
            long totalEnviado = 0;
            int read;

            while ((read = fis.read(buffer)) != -1) {
                byte[] idBytes = padTransferId(transferId);

                // Header: transferId(36) + chunkIndex(8) + chunkSize(4) = 48 bytes
                ByteBuffer header = ByteBuffer.allocate(36 + 8 + 4);
                header.put(idBytes);
                header.putLong(chunkIndex);
                header.putInt(read);

                os.write(header.array());
                os.write(buffer, 0, read);
                os.flush();

                // Esperar ACK
                int ack = is.read();
                if (ack != 0x01) {
                    throw new IOException("Servidor rechazó el chunk " + chunkIndex
                            + " (NACK recibido). Transferencia abortada.");
                }

                totalEnviado += read;
                chunkIndex++;

                if (progressCb != null) {
                    progressCb.onProgress(totalEnviado, totalBytes);
                }
            }
        }
    }


    public void receiveFileStream(String transferId, long totalChunks, java.nio.file.Path destino,
                                  long totalBytes, StreamProgressCallback progressCb) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), ProtocolConstants.CONNECT_TIMEOUT);
            socket.setSoTimeout(0); // sin timeout durante streaming

            OutputStream os = socket.getOutputStream();
            InputStream  is = socket.getInputStream();

            // Señal + transferId
            os.write(0x03);
            os.write(padTransferId(transferId));
            os.flush();

            long totalRecibido = 0;

            try (java.nio.channels.FileChannel fc = java.nio.channels.FileChannel.open(destino,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {

                for (long i = 0; i < totalChunks; i++) {
                    // Leer header: 36 + 8 + 4 = 48 bytes
                    byte[] header = leerExacto(is, 48);
                    if (header == null) throw new IOException("EOF inesperado leyendo header del chunk " + i);

                    ByteBuffer hBuf = ByteBuffer.wrap(header, 36, 12);
                    long chunkIndex = hBuf.getLong();
                    int  chunkSize  = hBuf.getInt();

                    // Leer datos
                    byte[] datos = leerExacto(is, chunkSize);
                    if (datos == null) throw new IOException("EOF inesperado leyendo datos del chunk " + i);

                    // Escribir a disco
                    ByteBuffer bb = ByteBuffer.wrap(datos);
                    while (bb.hasRemaining()) fc.write(bb);

                    // ACK
                    os.write(0x01);
                    os.flush();

                    totalRecibido += chunkSize;
                    if (progressCb != null) progressCb.onProgress(totalRecibido, totalBytes);
                }
            }
        }
    }

    private byte[] leerExacto(InputStream is, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = is.read(buf, offset, length - offset);
            if (n == -1) return null;
            offset += n;
        }
        return buf;
    }
    private byte[] padTransferId(String transferId) {
        byte[] raw = transferId.getBytes(StandardCharsets.UTF_8);
        if (raw.length == 36) return raw;
        byte[] padded = new byte[36];
        System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, 36));
        return padded;
    }

    @Override
    public void disconnect() {
        initialized = false;
    }

    @Override
    public boolean isConnected() {
        return initialized;
    }

    @FunctionalInterface
    public interface StreamProgressCallback {
        void onProgress(long bytesEnviados, long totalBytes);
    }
}
