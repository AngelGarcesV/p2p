package com.cliente.infrastructure.protocol;

public final class ProtocolConstants {
    private ProtocolConstants() {}

    public static final int CMD_CONNECT       = 0;
    public static final int CMD_LIST_CLIENTS  = 1;
    public static final int CMD_LIST_DOCS     = 2;
    public static final int CMD_DOWNLOAD_DOC  = 3;
    public static final int CMD_SEND_FILE     = 4;
    public static final int CMD_GET_LOGS      = 5;
    public static final int CMD_SEND_MESSAGE  = 6;
    public static final int CMD_GET_MESSAGES  = 7;

    public static final String STATUS_OK    = "ok";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_FULL  = "full";

    /** Tamaño de chunk para streaming TCP: 2 MB. Sin límite de datagrama. */
    public static final int CHUNK_SIZE      = 2 * 1024 * 1024; // 2 MB

    /** Tamaño de chunk para streaming UDP: 60 KB. Limitado por el MTU UDP. */
    public static final int UDP_CHUNK_SIZE  = 60_000; // ~60 KB

    public static final int CONNECT_TIMEOUT = 5_000;   // ms
    public static final int READ_TIMEOUT    = 30_000;  // ms — aumentado para streaming

    /** Timeout extendido para operaciones ML (inferencia puede tardar hasta 120s). */
    public static final int ML_READ_TIMEOUT = 120_000; // ms

    /** Tiempo máximo de espera por ACK de un chunk UDP antes de reintentar. */
    public static final int UDP_ACK_TIMEOUT = 5_000;   // ms

    /** Máximo de reintentos por chunk UDP antes de abortar. */
    public static final int UDP_MAX_RETRIES = 5;

    /** Byte de señal que precede a los frames binarios de streaming. */
    public static final byte STREAM_SIGNAL  = 0x02;
}
