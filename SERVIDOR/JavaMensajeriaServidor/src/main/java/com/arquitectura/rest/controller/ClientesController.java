package com.arquitectura.rest.controller;

import com.arquitectura.rest.dto.ClienteConectadoDTO;
import com.arquitectura.rest.service.ClientesRestService;
import com.arquitectura.rest.service.RestServiceFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClientesController {

    private final ClientesRestService clientesService;

    public ClientesController() {
        this(RestServiceFactory.crearClientesRestService());
    }

    ClientesController(ClientesRestService clientesService) {
        this.clientesService = clientesService;
    }

    @GetMapping
    public List<ClienteConectadoDTO> listar() {
        return clientesService.listarConectados();
    }
}
