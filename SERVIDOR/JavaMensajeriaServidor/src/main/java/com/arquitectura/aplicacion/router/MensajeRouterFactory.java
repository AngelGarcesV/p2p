package com.arquitectura.aplicacion.router;

import com.arquitectura.dominio.handlers.ConectarHandler;
import com.arquitectura.dominio.handlers.ClasificarGeneroHandler;
import com.arquitectura.dominio.handlers.DesconectarHandler;
import com.arquitectura.dominio.handlers.DesconectarServidorHandler;
import com.arquitectura.dominio.handlers.EntregarMensajeHandler;
import com.arquitectura.dominio.handlers.EnviarArchivoHandler;
import com.arquitectura.dominio.handlers.EstadoServidorHandler;
import com.arquitectura.dominio.handlers.FinalizarStreamHandler;
import com.arquitectura.dominio.handlers.IniciarStreamHandler;
import com.arquitectura.dominio.handlers.ListarClientesHandler;
import com.arquitectura.dominio.handlers.ListarDocumentosHandler;
import com.arquitectura.dominio.handlers.ListarLogsHandler;
import com.arquitectura.dominio.handlers.ListarLogsRemotoHandler;
import com.arquitectura.dominio.handlers.ListarMensajesHandler;
import com.arquitectura.dominio.handlers.ListarServidoresHandler;
import com.arquitectura.dominio.handlers.MensajeTextoHandler;
import com.arquitectura.dominio.handlers.ObtenerArchivoHandler;
import com.arquitectura.dominio.handlers.RegistrarServidorHandler;
import com.arquitectura.dominio.handlers.ReplicarArchivoHandler;
import com.arquitectura.dominio.handlers.ReplicarArchivoStreamHandler;
import com.arquitectura.dominio.handlers.ReplicarClientesHandler;
import com.arquitectura.dominio.handlers.ReplicarMensajeHandler;
import com.arquitectura.dominio.handlers.SincronizarEstadoHandler;
import com.arquitectura.mensajeria.enums.Accion;

public class MensajeRouterFactory {

    public static MensajeRouter crearRouter() {

        MensajeRouter router = new MensajeRouter();
        router.registrarHandler(Accion.CONECTAR,          new ConectarHandler());
        router.registrarHandler(Accion.DESCONECTAR,        new DesconectarHandler());
        router.registrarHandler(Accion.ENVIAR_DOCUMENTO,  new EnviarArchivoHandler());
        router.registrarHandler(Accion.ENVIAR_MENSAJE,    new MensajeTextoHandler());
        router.registrarHandler(Accion.LISTAR_MENSAJES,   new ListarMensajesHandler());
        router.registrarHandler(Accion.LISTAR_DOCUMENTOS, new ListarDocumentosHandler());
        router.registrarHandler(Accion.LISTAR_LOGS,       new ListarLogsHandler());
        router.registrarHandler(Accion.LISTAR_CLIENTES,   new ListarClientesHandler());
        router.registrarHandler(Accion.INICIAR_STREAM,    new IniciarStreamHandler());
        router.registrarHandler(Accion.FINALIZAR_STREAM,  new FinalizarStreamHandler());
        router.registrarHandler(Accion.SOLICITAR_STREAM,  new ObtenerArchivoHandler());

        // S2S federation handlers
        router.registrarHandler(Accion.REGISTRAR_SERVIDOR,      new RegistrarServidorHandler());
        router.registrarHandler(Accion.DESCONECTAR_SERVIDOR,     new DesconectarServidorHandler());
        router.registrarHandler(Accion.REPLICAR_CLIENTES,        new ReplicarClientesHandler());
        router.registrarHandler(Accion.REPLICAR_MENSAJE,         new ReplicarMensajeHandler());
        router.registrarHandler(Accion.REPLICAR_ARCHIVO,         new ReplicarArchivoHandler());
        router.registrarHandler(Accion.REPLICAR_ARCHIVO_STREAM,  new ReplicarArchivoStreamHandler());
        router.registrarHandler(Accion.ENTREGAR_MENSAJE,         new EntregarMensajeHandler());
        router.registrarHandler(Accion.LISTAR_SERVIDORES,        new ListarServidoresHandler());
        router.registrarHandler(Accion.ESTADO_SERVIDOR,          new EstadoServidorHandler());
        router.registrarHandler(Accion.LISTAR_LOGS_REMOTO,       new ListarLogsRemotoHandler());
        router.registrarHandler(Accion.SINCRONIZAR_ESTADO,       new SincronizarEstadoHandler());

        // ML — clasificacion de genero musical
        router.registrarHandler(Accion.CLASIFICAR_GENERO,        new ClasificarGeneroHandler());

        return router;
    }
}
