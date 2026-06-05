package com.arquitectura.rest.controller;

import com.arquitectura.dominio.repositorios.JpaLogServidorRepository;
import com.arquitectura.rest.dto.LogServidorDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogsController {

    @GetMapping
    public Map<String, Object> listar(
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tamanoPagina", defaultValue = "50") int tamanoPagina) {

        var repo = new JpaLogServidorRepository();

        List<LogServidorDTO> datos = repo.listarPaginado(pagina, tamanoPagina).stream()
                .map(l -> new LogServidorDTO(
                        l.getId(),
                        l.getNivel(),
                        l.getMensaje(),
                        l.getOrigen(),
                        l.getIpRemitente(),
                        l.getFechaEvento()
                ))
                .toList();

        long total = repo.contarTotal();

        return Map.of(
                "datos", datos,
                "total", total,
                "pagina", pagina,
                "tamanoPagina", tamanoPagina
        );
    }
}
