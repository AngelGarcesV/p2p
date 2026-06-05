package com.arquitectura.mensajeria.payload;

/**
 * Payload para la accion CLASIFICAR_GENERO.
 * El cliente envia el archivo WAV completo codificado en Base64.
 */
public class PayloadClasificarGenero {

    private String contenidoBase64; // bytes del WAV completo en Base64
    private String nombreArchivo;   // ej: "song.wav"

    public PayloadClasificarGenero() {}

    public PayloadClasificarGenero(String contenidoBase64, String nombreArchivo) {
        this.contenidoBase64 = contenidoBase64;
        this.nombreArchivo = nombreArchivo;
    }

    public String getContenidoBase64() {
        return contenidoBase64;
    }

    public void setContenidoBase64(String contenidoBase64) {
        this.contenidoBase64 = contenidoBase64;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }
}
