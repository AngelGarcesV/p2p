package com.arquitectura.rest.service;

import com.arquitectura.rest.dto.GenerosDTO;
import com.arquitectura.rest.dto.MlHealthDTO;
import com.arquitectura.rest.dto.MlInfoDTO;
import org.springframework.web.client.RestTemplate;

public class MlRestService {

    private final String mlBaseUrl;
    private final RestTemplate restTemplate;

    public MlRestService(String mlBaseUrl) {
        this(mlBaseUrl, new RestTemplate());
    }

    MlRestService(String mlBaseUrl, RestTemplate restTemplate) {
        this.mlBaseUrl = mlBaseUrl;
        this.restTemplate = restTemplate;
    }

    public MlInfoDTO info() {
        return restTemplate.getForObject(mlBaseUrl + "/", MlInfoDTO.class);
    }

    public MlHealthDTO health() {
        return restTemplate.getForObject(mlBaseUrl + "/health", MlHealthDTO.class);
    }

    public GenerosDTO generos() {
        return restTemplate.getForObject(mlBaseUrl + "/genres", GenerosDTO.class);
    }
}
