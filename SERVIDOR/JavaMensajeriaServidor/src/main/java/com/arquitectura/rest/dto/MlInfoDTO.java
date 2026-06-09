package com.arquitectura.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MlInfoDTO(
        @JsonProperty("message") String message
) {}
