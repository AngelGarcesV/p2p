package com.arquitectura.rest.mapper;

import com.arquitectura.aplicacion.sesion.SesionCliente;
import com.arquitectura.rest.dto.ClienteConectadoDTO;

public class ClienteRestMapper {

    public ClienteConectadoDTO toDto(SesionCliente sesion) {
        return new ClienteConectadoDTO(
                sesion.getUsername(),
                sesion.getIpRemitente(),
                sesion.getPuertoRemitente(),
                sesion.getProtocolo(),
                sesion.getCreadoEn(),
                sesion.getUltimoAcceso()
        );
    }
}
