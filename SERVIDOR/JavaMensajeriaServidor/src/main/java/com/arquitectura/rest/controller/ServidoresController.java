package com.arquitectura.rest.controller;

import com.arquitectura.rest.dto.ServidorDTO;
import com.arquitectura.rest.service.RestServiceFactory;
import com.arquitectura.rest.service.ServidoresRestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/servidores")
public class ServidoresController {

    private final ServidoresRestService servidoresService;

    public ServidoresController() {
        this(RestServiceFactory.crearServidoresRestService());
    }

    ServidoresController(ServidoresRestService servidoresService) {
        this.servidoresService = servidoresService;
    }

    @GetMapping
    public List<ServidorDTO> listar() {
        return servidoresService.listarDisponibles();
    }
}
