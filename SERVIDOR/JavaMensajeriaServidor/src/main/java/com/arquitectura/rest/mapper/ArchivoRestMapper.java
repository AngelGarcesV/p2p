package com.arquitectura.rest.mapper;

import com.arquitectura.dominio.modelo.ArchivoRecibidoModel;
import com.arquitectura.rest.dto.ArchivoResumenDTO;

public class ArchivoRestMapper {

    public ArchivoResumenDTO toDto(ArchivoRecibidoModel model) {
        return new ArchivoResumenDTO(
                model.getId(),
                model.getRemitente(),
                model.getIpRemitente(),
                model.getNombreArchivo(),
                model.getExtension(),
                model.getRutaArchivo(),
                model.getHashSha256(),
                model.getTamano(),
                model.getFechaRecepcion(),
                model.getServidorOrigen(),
                model.getDestinatario()
        );
    }

    public ArchivoRecibidoModel toEntity(ArchivoResumenDTO dto) {
        ArchivoRecibidoModel model = new ArchivoRecibidoModel();
        model.setId(dto.id());
        model.setRemitente(dto.remitente());
        model.setIpRemitente(dto.ipRemitente());
        model.setNombreArchivo(dto.nombreArchivo());
        model.setExtension(dto.extension());
        model.setRutaArchivo(dto.rutaArchivo());
        model.setHashSha256(dto.hashSha256());
        model.setTamano(dto.tamano());
        model.setFechaRecepcion(dto.fechaRecepcion());
        model.setServidorOrigen(dto.servidorOrigen());
        model.setDestinatario(dto.destinatario());
        return model;
    }
}
