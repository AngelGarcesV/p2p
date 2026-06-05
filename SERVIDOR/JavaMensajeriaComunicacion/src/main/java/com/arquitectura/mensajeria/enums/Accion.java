package com.arquitectura.mensajeria.enums;

public enum Accion {
    CONECTAR,
    DESCONECTAR,
    LISTAR_CLIENTES,
    LISTAR_DOCUMENTOS,
    LISTAR_MENSAJES,
    LISTAR_LOGS,
    ENVIAR_DOCUMENTO,
    OBTENER_DOCUMENTO,
    ENVIAR_MENSAJE,
    INICIAR_STREAM,
    FINALIZAR_STREAM,
    SOLICITAR_STREAM,       // cliente pide descargar un archivo
    INICIAR_DESCARGA,       // servidor responde con metadatos antes de enviar chunks

    // S2S — federacion entre servidores
    REGISTRAR_SERVIDOR,
    DESCONECTAR_SERVIDOR,
    LISTAR_SERVIDORES,
    REPLICAR_MENSAJE,
    REPLICAR_ARCHIVO,
    REPLICAR_ARCHIVO_STREAM,
    REPLICAR_CLIENTES,
    ENTREGAR_MENSAJE,
    LISTAR_LOGS_REMOTO,
    ESTADO_SERVIDOR,
    SINCRONIZAR_ESTADO,     // S2S — sincronizacion completa al reconectarse un peer

    // ML — clasificacion de genero musical via proxy al servicio Python
    CLASIFICAR_GENERO
}
