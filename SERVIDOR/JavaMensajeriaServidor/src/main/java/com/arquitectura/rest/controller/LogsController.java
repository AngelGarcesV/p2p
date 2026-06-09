package com.arquitectura.rest.controller;

import com.arquitectura.rest.dto.LogServidorDTO;
import com.arquitectura.rest.dto.PaginaDTO;
import com.arquitectura.rest.service.LogsRestService;
import com.arquitectura.rest.service.RestServiceFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
public class LogsController {

    private final LogsRestService logsService;

    public LogsController() {
        this(RestServiceFactory.crearLogsRestService());
    }

    LogsController(LogsRestService logsService) {
        this.logsService = logsService;
    }

    @GetMapping
    public PaginaDTO<LogServidorDTO> listar(
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tamanoPagina", defaultValue = "50") int tamanoPagina) {
        return logsService.listarPaginado(pagina, tamanoPagina);
    }
}
