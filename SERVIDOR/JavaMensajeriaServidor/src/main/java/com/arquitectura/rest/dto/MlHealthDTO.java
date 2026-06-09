package com.arquitectura.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MlHealthDTO(
        @JsonProperty("status") String status,
        @JsonProperty("model_loaded") boolean modelLoaded
) {}
