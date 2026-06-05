package com.arquitectura.rest.mapper;

import com.arquitectura.aplicacion.sesion.ConexionPeer;
import com.arquitectura.dominio.modelo.PeerConocidoModel;
import com.arquitectura.rest.dto.ServidorDTO;

public class ServidorRestMapper {

    public ServidorDTO toDto(PeerConocidoModel model) {
        return new ServidorDTO(
                model.getServidorId(),
                model.getHost(),
                model.getPuerto(),
                "DESCONOCIDO",
                0,
                model.getUltimaConexion()
        );
    }

    public ServidorDTO toDto(ConexionPeer peer) {
        return new ServidorDTO(
                peer.getConfig().getServidorId(),
                peer.getConfig().getHost(),
                peer.getConfig().getPuerto(),
                peer.getEstado().name(),
                peer.getIntentosReconexion(),
                peer.getUltimaConexion()
        );
    }

    public PeerConocidoModel toEntity(ServidorDTO dto) {
        PeerConocidoModel model = new PeerConocidoModel();
        model.setServidorId(dto.servidorId());
        model.setHost(dto.host());
        model.setPuerto(dto.puerto());
        model.setUltimaConexion(dto.ultimaConexion());
        return model;
    }
}
