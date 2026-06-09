package com.arquitectura.rest.controller;

import com.arquitectura.rest.dto.MensajeDTO;
import com.arquitectura.rest.service.MensajesRestService;
import com.arquitectura.rest.service.RestServiceFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mensajes")
public class MensajesController {

    private final MensajesRestService mensajesService;

    public MensajesController() {
        this(RestServiceFactory.crearMensajesRestService());
    }

    MensajesController(MensajesRestService mensajesService) {
        this.mensajesService = mensajesService;
    }

    @GetMapping
    public List<MensajeDTO> listar(
            @RequestParam(name = "username", required = false) String username) {
        return mensajesService.listarDisponibles(username);
    }
}
