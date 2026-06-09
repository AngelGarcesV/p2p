package com.arquitectura.rest.service;

import com.arquitectura.aplicacion.ml.MlProxyConfig;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.dominio.repositorios.JpaArchivoRecibidoRepository;
import com.arquitectura.dominio.repositorios.JpaLogServidorRepository;
import com.arquitectura.dominio.repositorios.JpaMensajeRepository;
import com.arquitectura.dominio.repositorios.JpaPeerConocidoRepository;
import com.arquitectura.rest.mapper.ArchivoRestMapper;
import com.arquitectura.rest.mapper.ClienteRestMapper;
import com.arquitectura.rest.mapper.LogServidorRestMapper;
import com.arquitectura.rest.mapper.MensajeRestMapper;
import com.arquitectura.rest.mapper.ServidorRestMapper;

public final class RestServiceFactory {

    private RestServiceFactory() {
    }

    public static ArchivosRestService crearArchivosRestService() {
        return new ArchivosRestService(new JpaArchivoRecibidoRepository(), new ArchivoRestMapper());
    }

    public static ClientesRestService crearClientesRestService() {
        return new ClientesRestService(GestorSesiones.getInstance(), new ClienteRestMapper());
    }

    public static LogsRestService crearLogsRestService() {
        return new LogsRestService(new JpaLogServidorRepository(), new LogServidorRestMapper());
    }

    public static MensajesRestService crearMensajesRestService() {
        return new MensajesRestService(new JpaMensajeRepository(), new MensajeRestMapper());
    }

    public static ServidoresRestService crearServidoresRestService() {
        return new ServidoresRestService(
                new JpaPeerConocidoRepository(),
                GestorServidoresPeer.getInstance(),
                new ServidorRestMapper()
        );
    }

    public static MlRestService crearMlRestService() {
        return new MlRestService(MlProxyConfig.getInstance().getBaseUrl());
    }
}
