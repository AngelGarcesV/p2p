package com.arquitectura.rest.controller;

import com.arquitectura.rest.dto.GenerosDTO;
import com.arquitectura.rest.dto.MlHealthDTO;
import com.arquitectura.rest.dto.MlInfoDTO;
import com.arquitectura.rest.service.MlRestService;
import com.arquitectura.rest.service.RestServiceFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ml")
public class MlController {

    private final MlRestService mlService;

    public MlController() {
        this(RestServiceFactory.crearMlRestService());
    }

    MlController(MlRestService mlService) {
        this.mlService = mlService;
    }

    @GetMapping
    public MlInfoDTO info() {
        return mlService.info();
    }

    @GetMapping("/health")
    public MlHealthDTO health() {
        return mlService.health();
    }

    @GetMapping("/generos")
    public GenerosDTO generos() {
        return mlService.generos();
    }
}
