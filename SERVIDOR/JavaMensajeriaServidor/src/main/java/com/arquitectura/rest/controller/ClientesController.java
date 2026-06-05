package com.arquitectura.rest.controller;

import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.rest.dto.ClienteConectadoDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClientesController {

    @GetMapping
    public List<ClienteConectadoDTO> listar() {
        return GestorSesiones.getInstance().listarSesiones().stream()
                .map(s -> new ClienteConectadoDTO(
                        s.getUsername(),
                        s.getIpRemitente(),
                        s.getPuertoRemitente(),
                        s.getProtocolo(),
                        s.getCreadoEn(),
                        s.getUltimoAcceso()
                ))
                .toList();
    }
}
