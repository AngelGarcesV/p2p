package com.arquitectura.rest.mapper;

import com.arquitectura.dominio.modelo.MensajeModel;
import com.arquitectura.rest.dto.MensajeDTO;

public class MensajeRestMapper {

    public MensajeDTO toDto(MensajeModel model) {
        return new MensajeDTO(
                model.getId(),
                model.getAutor(),
                model.getIpRemitente(),
                model.getContenido(),
                model.getHashSha256(),
                model.getFechaEnvio(),
                model.getServidorOrigen(),
                model.getDestinatario()
        );
    }

    public MensajeModel toEntity(MensajeDTO dto) {
        MensajeModel model = new MensajeModel();
        model.setId(dto.id());
        model.setAutor(dto.autor());
        model.setIpRemitente(dto.ipRemitente());
        model.setContenido(dto.contenido());
        model.setHashSha256(dto.hashSha256());
        model.setFechaEnvio(dto.fechaEnvio());
        model.setServidorOrigen(dto.servidorOrigen());
        model.setDestinatario(dto.destinatario());
        return model;
    }
}
