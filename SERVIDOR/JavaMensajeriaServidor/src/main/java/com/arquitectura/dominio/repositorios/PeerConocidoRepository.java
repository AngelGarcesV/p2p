package com.arquitectura.dominio.repositorios;

import com.arquitectura.dominio.modelo.PeerConocidoModel;

import java.util.List;

public interface PeerConocidoRepository {

    void guardarOActualizar(String servidorId, String host, int puerto);

    List<PeerConocidoModel> listarTodos();
}
