package com.arquitectura.rest.mapper;

import com.arquitectura.dominio.modelo.LogServidorModel;
import com.arquitectura.rest.dto.LogServidorDTO;

public class LogServidorRestMapper {

    public LogServidorDTO toDto(LogServidorModel model) {
        return new LogServidorDTO(
                model.getId(),
                model.getNivel(),
                model.getMensaje(),
                model.getOrigen(),
                model.getIpRemitente(),
                model.getFechaEvento()
        );
    }

    public LogServidorModel toEntity(LogServidorDTO dto) {
        LogServidorModel model = new LogServidorModel();
        model.setNivel(dto.nivel());
        model.setMensaje(dto.mensaje());
        model.setOrigen(dto.origen());
        model.setIpRemitente(dto.ipRemitente());
        model.setFechaEvento(dto.fechaEvento());
        return model;
    }
}
