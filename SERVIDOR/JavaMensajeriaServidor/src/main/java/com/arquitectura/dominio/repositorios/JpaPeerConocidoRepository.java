package com.arquitectura.dominio.repositorios;

import com.arquitectura.dominio.modelo.PeerConocidoModel;
import com.arquitectura.infraestructura.persistencia.HibernateManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class JpaPeerConocidoRepository implements PeerConocidoRepository {

    private static final Logger LOGGER = Logger.getLogger(JpaPeerConocidoRepository.class.getName());

    /**
     * Guarda o actualiza un peer conocido (upsert por servidorId).
     * Actualiza host, puerto y ultima_conexion si ya existÃ­a.
     */
    @Override
    public void guardarOActualizar(String servidorId, String host, int puerto) {
        EntityManager em = HibernateManager.crearEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            PeerConocidoModel existente = em.find(PeerConocidoModel.class, servidorId);
            if (existente == null) {
                em.persist(new PeerConocidoModel(servidorId, host, puerto));
                LOGGER.info(() -> "Peer conocido persistido: " + servidorId + " @ " + host + ":" + puerto);
            } else {
                existente.setHost(host);
                existente.setPuerto(puerto);
                existente.setUltimaConexion(LocalDateTime.now());
                LOGGER.fine(() -> "Peer conocido actualizado: " + servidorId + " @ " + host + ":" + puerto);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            LOGGER.warning(() -> "Error persistiendo peer conocido " + servidorId + ": " + e.getMessage());
        } finally {
            em.close();
        }
    }

    /** Devuelve todos los peers conocidos almacenados en DB. */
    @Override
    public List<PeerConocidoModel> listarTodos() {
        EntityManager em = HibernateManager.crearEntityManager();
        try {
            return em.createQuery("SELECT p FROM PeerConocidoModel p", PeerConocidoModel.class)
                    .getResultList();
        } catch (Exception e) {
            LOGGER.warning(() -> "Error leyendo peers conocidos de DB: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            em.close();
        }
    }
}

