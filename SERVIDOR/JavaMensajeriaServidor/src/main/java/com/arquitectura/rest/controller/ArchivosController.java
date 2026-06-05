package com.arquitectura.rest.controller;

import com.arquitectura.dominio.repositorios.JpaArchivoRecibidoRepository;
import com.arquitectura.rest.dto.ArchivoResumenDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/archivos")
public class ArchivosController {

    @GetMapping
    public List<ArchivoResumenDTO> listar() {
        return new JpaArchivoRecibidoRepository().listarTodos().stream()
                .map(a -> new ArchivoResumenDTO(
                        a.getId(),
                        a.getRemitente(),
                        a.getIpRemitente(),
                        a.getNombreArchivo(),
                        a.getExtension(),
                        a.getRutaArchivo(),
                        a.getHashSha256(),
                        a.getTamano(),
                        a.getFechaRecepcion(),
                        a.getServidorOrigen(),
                        a.getDestinatario()
                ))
                .toList();
    }
}
