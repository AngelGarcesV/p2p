package com.arquitectura.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GenerosDTO(
        @JsonProperty("genres") List<String> generos
) {}
