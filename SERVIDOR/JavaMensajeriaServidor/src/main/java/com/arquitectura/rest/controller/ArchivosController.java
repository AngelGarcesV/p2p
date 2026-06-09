package com.arquitectura.rest.controller;

import com.arquitectura.rest.dto.ArchivoResumenDTO;
import com.arquitectura.rest.service.ArchivosRestService;
import com.arquitectura.rest.service.RestServiceFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/archivos")
public class ArchivosController {

    private final ArchivosRestService archivosService;

    public ArchivosController() {
        this(RestServiceFactory.crearArchivosRestService());
    }

    ArchivosController(ArchivosRestService archivosService) {
        this.archivosService = archivosService;
    }

    @GetMapping
    public List<ArchivoResumenDTO> listar(
            @RequestParam(name = "username", required = false) String username) {
        return archivosService.listarDisponibles(username);
    }
}
