# Documentación técnica — Sistema de mensajería P2P

## Índice

1. [Arquitectura general](#1-arquitectura-general)
2. [Arranque del servidor](#2-arranque-del-servidor-mainjava)
3. [Federación P2P — GestorServidoresPeer](#3-federación-p2p--gestorservidorespeerjava)
4. [Conexión entre peers (Handshake)](#4-conexión-entre-peers-handshake)
5. [Persistencia de peers conocidos](#5-persistencia-de-peers-conocidos)
6. [Replicación de mensajes](#6-replicación-de-mensajes)
7. [Envío y replicación de archivos](#7-envío-y-replicación-de-archivos)
8. [Sincronización al reconectarse](#8-sincronización-al-reconectarse-un-peer)
9. [Transferencia de archivos por stream TCP](#9-transferencia-de-archivos-por-stream-tcp)
10. [Handlers — referencia completa](#10-handlers--referencia-completa)
11. [Flujos end-to-end](#11-flujos-end-to-end)
12. [Tabla de acciones (Accion enum)](#12-tabla-de-acciones)

---

## 1. Arquitectura general

El sistema es una red de servidores peer-to-peer donde cada nodo:
- Atiende clientes finales (conexiones TCP/UDP)
- Se conecta con otros servidores (peers) para federar el sistema
- Replica mensajes y archivos a todos los peers al recibirlos
- Sincroniza su estado completo cuando un peer vuelve a estar disponible

```
Cliente A ──► Servidor A ◄──────────────► Servidor B ◄── Cliente B
                  │                            │
                  └────── peers_conocidos ──────┘
                           (DB + application.properties)
```

Cada servidor tiene su propia base de datos MySQL. La consistencia eventual se logra vía replicación activa: cuando un dato entra en cualquier servidor, se propaga a todos los peers.

---

## 2. Arranque del servidor (`Main.java`)

El servidor arranca en el siguiente orden:

```
1. Configura logging
2. Carga application.properties
3. Configura AES (CryptoConfig), MySQL (ConexionMySql), logging de DB
4. Lee peers del properties (server.peers=id:host:port,...)
5. Fusiona con peers persistidos en DB (JpaPeerConocidoRepository.listarTodos())
6. Inicializa GestorServidoresPeer y lanza conexiones async a todos los peers
7. Crea ProtocoloTransporte (TCP o UDP según config)
8. Crea MensajeRouter, ProcesadorMensajes, GestorSesiones
9. Loop principal: recibe paquetes → asigna tarea del pool → ejecuta
```

### Fusión de peers al arrancar

Los peers se resuelven de dos fuentes sin duplicar:

| Fuente | Prioridad |
|--------|-----------|
| `application.properties` → `server.peers` | Alta (no se sobreescribe) |
| Tabla `peers_conocidos` en MySQL | Complementaria |

Si un peer aparece en ambas fuentes con el mismo `servidorId`, gana el del properties. Los peers de la DB se agregan solo si su ID no está ya en la lista.

---

## 3. Federación P2P — `GestorServidoresPeer.java`

Singleton que centraliza todo lo relacionado con la comunicación entre servidores.

### Estado interno

```
peers: Map<String, ConexionPeer>           // todos los peers conocidos
cacheClientesRemotos: Map<String, List<PayloadClienteRemoto>>  // clientes por servidor origen
servidorId, servidorPuerto, servidorHost   // identidad de este servidor
```

### Ciclo de reconexión

Al arrancar se lanzan dos mecanismos:

1. **Conexión inicial** — `conectarAPeers()`: dispara un handshake async a cada peer conocido.
2. **Reconexión periódica** — cada 15 segundos busca peers no conectados y reintenta. Aplica backoff: si `intentosReconexion % 3 != 0`, salta el ciclo para ese peer (evita saturar logs con peers permanentemente caídos).

### Envío de mensajes a peers

| Método | Comportamiento |
|--------|----------------|
| `enviarATodos(msg)` | Broadcast fire-and-forget a todos los peers conectados |
| `enviarAPeer(id, msg)` | Unicast fire-and-forget, retorna boolean |
| `enviarAPeerYEsperar(id, msg)` | Unicast síncrono, retorna JSON de respuesta (proxy) |

Cada llamada **abre y cierra su propio socket TCP**. No hay conexión persistente entre peers.

### Cache de clientes remotos

Cada vez que un peer envía `REPLICAR_CLIENTES`, este servidor actualiza el cache:

```
cacheClientesRemotos["servidor-b"] = [usuario1, usuario2, ...]
```

Este cache se usa para resolver hacia qué peer forwardear un mensaje/archivo unicast.

---

## 4. Conexión entre peers (Handshake)

Cuando el servidor A quiere conectarse al servidor B:

```
A                                    B
│── REGISTRAR_SERVIDOR ─────────────►│
│   (servidorId, host, puerto)        │
│                                    │── marcarPeerConectado(A)
│                                    │── persiste A en DB
│                                    │── envía REPLICAR_CLIENTES(clientes locales de B) a A
│                                    │── lanza SINCRONIZAR_ESTADO a A (background)
│◄── respuesta con peersConocidos ───│
│    (lista de peers que B conoce)    │
│                                    │
│── descubre peers transitivos ───────┘
    (si B conoce C y A no lo conoce → A registra C y le hace handshake)
```

### Descubrimiento transitivo

Si B conoce un servidor C que A no conocía, A lo registra dinámicamente y le hace handshake automáticamente. Esto permite que la red se auto-descubra sin tener que configurar todas las conexiones manualmente.

---

## 5. Persistencia de peers conocidos

### Entidad `PeerConocidoModel` → tabla `peers_conocidos`

| Columna | Tipo | Descripción |
|---------|------|-------------|
| `servidor_id` (PK) | VARCHAR(100) | Identificador único del peer |
| `host` | VARCHAR(100) | IP o hostname |
| `puerto` | INT | Puerto TCP |
| `ultima_conexion` | DATETIME | Timestamp del último handshake exitoso |

Hibernate crea la tabla automáticamente (`hbm2ddl.auto=update`).

### Cuándo se persiste un peer

En `RegistrarServidorHandler`, al recibir el handshake de un peer:

```java
peerRepo.guardarOActualizar(servidorId, payload.getHost(), payload.getPuerto());
```

Es un **upsert**: si el peer ya existe en DB, actualiza host, puerto y `ultima_conexion`. Esto permite que si un peer cambia de IP, el servidor lo recuerda con la IP nueva.

### Para qué sirve

Si el servidor se reinicia y `application.properties` solo tiene `servidor-b`, pero en algún momento también se conectó con `servidor-c`, al reiniciar también intentará conectarse con `servidor-c` (porque está en DB).

---

## 6. Replicación de mensajes

### Flujo de envío de un mensaje de texto

```
Cliente envía ENVIAR_MENSAJE
    │
    ▼
MensajeTextoHandler
    ├── Calcula SHA-256 y cifra con AES
    ├── Persiste en DB (MensajeModel)
    └── replicarMensaje(...)
            │
            ├── Broadcast (destinatario == null):
            │       enviarATodos(REPLICAR_MENSAJE)
            │
            └── Unicast (destinatario != null):
                    ├── Si está en sesión local → no reenvía (está en DB para poll)
                    ├── Si está en cache remoto → ENTREGAR_MENSAJE al peer correcto
                    └── Si no se sabe → ENTREGAR_MENSAJE a todos los peers (fallback)
```

### Recepción de un mensaje replicado (`ReplicarMensajeHandler`)

1. Verifica idempotencia: si el `id` ya está en DB → responde OK sin duplicar
2. Persiste el mensaje
3. Fan-out: reenvía el `REPLICAR_MENSAJE` a los demás peers (con idempotencia en cada nodo, no hay loops infinitos)

---

## 7. Envío y replicación de archivos

Hay **dos canales** de transferencia según el tamaño del archivo:

| Canal | Acción | Usado cuando |
|-------|--------|-------------|
| Base64 en JSON | `ENVIAR_DOCUMENTO` / `REPLICAR_ARCHIVO` | Archivos pequeños |
| Stream TCP binario | `INICIAR_STREAM` + chunks + `FINALIZAR_STREAM` | Archivos grandes |

### Flujo de archivo pequeño (Base64)

```
Cliente envía ENVIAR_DOCUMENTO (contenido en Base64)
    │
    ▼
EnviarArchivoHandler
    ├── Decodifica Base64 → bytes
    ├── Calcula SHA-256 y cifra con AES
    ├── Guarda en disco (archivos-recibidos/)
    ├── Broadcast: persiste en DB + ReplicadorArchivos.replicar()
    └── Unicast remoto: REPLICAR_ARCHIVO al peer destino → elimina .tmp local
```

Replicación entre peers (broadcast):

```
Servidor A ──REPLICAR_ARCHIVO──► Servidor B
                                    ├── Idempotencia (ya en DB? → OK)
                                    ├── Descifra AES → guarda en disco
                                    ├── Persiste en DB con clientIdDestino
                                    └── Fan-out a otros peers (si broadcast)
```

### Flujo de archivo grande (Stream TCP)

```
Cliente                    Servidor A                    Servidor B
   │                           │                             │
   │── INICIAR_STREAM ─────►   │                             │
   │◄── transferId ────────── │                             │
   │                           │                             │
   │── bytes (chunks TCP) ──►  │ (StreamReceptorTcp)         │
   │◄── ACK por chunk ──────── │                             │
   │                           │                             │
   │── FINALIZAR_STREAM ────►  │                             │
   │                           ├── valida hash SHA-256        │
   │                           ├── mueve .tmp → archivo final │
   │                           ├── persiste en DB             │
   │                           │                             │
   │                           │── REPLICAR_ARCHIVO_STREAM ─►│
   │                           │   (metadata del archivo)     │
   │                           │◄── OK ──────────────────────│
   │                           │                             │
   │                           │── bytes (chunks TCP) ──────►│ (StreamReceptorTcp)
   │                           │◄── ACK por chunk ───────────│
   │                           │                             │
   │                           │  (conexión EOF)             │
   │                           │                             ├── mueve .tmp → archivo final
   │                           │                             └── persiste en DB
```

---

## 8. Sincronización al reconectarse un peer

Cuando el servidor B vuelve a estar activo y hace handshake con A, el servidor A detecta el handshake (en `RegistrarServidorHandler`) y **en background** sincroniza todo su estado hacia B.

```
A                                    B
│                                    │
│── SINCRONIZAR_ESTADO ─────────────►│
│   {                                │
│     mensajes: [...],               ├── persiste mensajes nuevos (idempotente por id)
│     archivos: [metadata...]        ├── anota archivos que vienen en stream
│   }                                │
│                                    │
│  (por cada archivo en disco de A): │
│── REPLICAR_ARCHIVO_STREAM ────────►│
│   (metadata: id, nombre, tamaño)   ├── crea/trunca .tmp
│◄── OK ────────────────────────────│
│                                    │
│── chunks TCP binarios ────────────►│ (StreamReceptorTcp)
│◄── ACK por chunk ─────────────────│
│                                    │
│  (EOF — todos los bytes enviados)  │
│                                    ├── finaliza transferencia S2S
│                                    ├── calcula y verifica hash
│                                    ├── mueve .tmp → archivo final
│                                    └── persiste en DB
```

### Idempotencia en la sincronización

| Situación | Comportamiento |
|-----------|----------------|
| Mensaje ya en DB del receptor | Se ignora (no se duplica) |
| Archivo ya en DB del receptor | `ReplicarArchivoStreamHandler` responde OK sin registrar transferencia; `StreamReceptorTcp` drena los bytes silenciosamente y responde ACK (el emisor no falla) |
| `.tmp` huérfano en disco | Se trunca a 0 bytes y se reutiliza |

### Por qué los archivos no se persisten en `SincronizarEstadoHandler`

Los archivos solo se persisten cuando los bytes están en disco. Si se insertaran en DB al recibir el JSON de metadata y luego el stream fallara, habría una fila en DB sin archivo en disco. `GestorTransferencias.finalizarTransferenciaS2S()` es el único responsable de la inserción en DB, y solo lo hace después de mover el `.tmp` al archivo final.

---

## 9. Transferencia de archivos por stream TCP

### Protocolo de frame (por chunk)

```
┌─────────────────┬──────────────────┬────────────────┬──────────────────────┐
│ transferId (36B)│ chunkIndex  (8B) │ chunkSize (4B) │ chunkData (variable) │
└─────────────────┴──────────────────┴────────────────┴──────────────────────┘
```

- **Header total**: 48 bytes
- **ACK**: `0x01` (1 byte)
- **NACK**: `0x00` (1 byte)
- **Señal de inicio de stream**: `0x02` (cliente→servidor para upload, peer→peer para replicación)
- **Señal de inicio de descarga**: `0x03` (servidor→cliente para download)

### Chunk size

| Protocolo | Tamaño de chunk |
|-----------|----------------|
| TCP (upload) | 2 MB |
| UDP (upload) | 60 KB |
| TCP (replicación S2S) | 2 MB |

### `StreamReceptorTcp` — lógica de recepción

```
while (true):
    leer 48 bytes (header)
    si EOF:
        si hay transferencia S2S pendiente → finalizarTransferenciaS2S()
        break

    parsear transferId, chunkIndex, chunkSize
    validar chunkSize (1 a 8MB)

    buscar EstadoTransferencia en GestorTransferencias
    si no existe (transferId desconocido):
        drenar chunkSize bytes silenciosamente
        responder ACK
        continue  ← no se corta el stream

    si es primera vez (o cambió la transferencia):
        abrir FileChannel en modo APPEND

    leer chunkData
    escribir al FileChannel
    actualizar digest SHA-256
    responder ACK

    si transferencia completa y es S2S:
        finalizarTransferenciaS2S()
```

---

## 10. Handlers — referencia completa

### Handlers de clientes

| Handler | Acción | Descripción |
|---------|--------|-------------|
| `ConectarHandler` | `CONECTAR` | Registra sesión del cliente, replica lista de clientes a peers |
| `DesconectarHandler` | `DESCONECTAR` | Elimina sesión, replica lista actualizada a peers |
| `MensajeTextoHandler` | `ENVIAR_MENSAJE` | Recibe mensaje, persiste, cifra y replica a peers |
| `EnviarArchivoHandler` | `ENVIAR_DOCUMENTO` | Recibe archivo Base64, persiste, replica o forwardea unicast |
| `IniciarStreamHandler` | `INICIAR_STREAM` | Prepara transferencia de archivo grande, retorna transferId |
| `FinalizarStreamHandler` | `FINALIZAR_STREAM` | Valida hash, mueve .tmp, persiste, replica a peers |
| `ObtenerArchivoHandler` | `SOLICITAR_STREAM` | Prepara descarga de archivo, retorna metadata + transferId |
| `ListarMensajesHandler` | `LISTAR_MENSAJES` | Devuelve mensajes del usuario (broadcast + unicast propio) |
| `ListarDocumentosHandler` | `LISTAR_DOCUMENTOS` | Devuelve archivos del usuario (broadcast + unicast propio) |
| `ListarClientesHandler` | `LISTAR_CLIENTES` | Devuelve clientes locales + remotos (cache de peers) |
| `ListarServidoresHandler` | `LISTAR_SERVIDORES` | Devuelve todos los peers con su estado |
| `EstadoServidorHandler` | `ESTADO_SERVIDOR` | Devuelve uptime, clientes, peers, métricas |

### Handlers de federación (S2S)

| Handler | Acción | Descripción |
|---------|--------|-------------|
| `RegistrarServidorHandler` | `REGISTRAR_SERVIDOR` | Registra handshake de peer, persiste en DB, dispara sincronización |
| `DesconectarServidorHandler` | `DESCONECTAR_SERVIDOR` | Invalida cache de clientes del peer, lo marca como desconectado |
| `ReplicarMensajeHandler` | `REPLICAR_MENSAJE` | Recibe mensaje replicado, idempotente, hace fan-out |
| `ReplicarArchivoHandler` | `REPLICAR_ARCHIVO` | Recibe archivo Base64 replicado, idempotente, descifra, persiste |
| `ReplicarArchivoStreamHandler` | `REPLICAR_ARCHIVO_STREAM` | Prepara recepción de stream S2S, maneja idempotencia y .tmp huérfanos |
| `ReplicarClientesHandler` | `REPLICAR_CLIENTES` | Actualiza cache de clientes remotos del peer origen |
| `SincronizarEstadoHandler` | `SINCRONIZAR_ESTADO` | Recibe mensajes + metadata de archivos al reconectarse un peer |
| `EntregarMensajeHandler` | `ENTREGAR_MENSAJE` | Forwardea mensaje unicast al peer que tiene al destinatario |
| `ListarLogsRemotoHandler` | `LISTAR_LOGS` | Proxy: redirige petición de logs a un peer remoto y retorna la respuesta |

---

## 11. Flujos end-to-end

### Cliente A (en servidor-a) envía mensaje a todos

```
Cliente A
  └─► ENVIAR_MENSAJE → MensajeTextoHandler (servidor-a)
          ├─ persiste en DB de servidor-a
          └─ enviarATodos(REPLICAR_MENSAJE) → [servidor-b, servidor-c, ...]
                  └─► ReplicarMensajeHandler (servidor-b)
                          ├─ verifica idempotencia
                          ├─ persiste en DB de servidor-b
                          └─ enviarATodos(REPLICAR_MENSAJE) → [servidor-a, servidor-c]
                                  └─ servidor-a: ya existe → OK (no duplica)
```

### Cliente A envía archivo grande a todos

```
Cliente A
  ├─► INICIAR_STREAM → IniciarStreamHandler → retorna transferId
  ├─► chunks TCP (Stream)
  └─► FINALIZAR_STREAM → FinalizarStreamHandler (servidor-a)
          ├─ valida hash SHA-256
          ├─ persiste en DB de servidor-a
          └─ ReplicadorArchivos.replicar()
                  └─► [servidor-b]: REPLICAR_ARCHIVO_STREAM → chunks TCP
                          └─► ReplicarArchivoStreamHandler + StreamReceptorTcp
                                  └─ persiste en DB de servidor-b
```

### Servidor B se reconecta después de caída

```
servidor-b arranca
  └─► REGISTRAR_SERVIDOR → servidor-a (RegistrarServidorHandler)
          ├─ marcarPeerConectado(servidor-b)
          ├─ persiste servidor-b en peers_conocidos
          ├─ envía REPLICAR_CLIENTES (clientes locales de A) → servidor-b
          └─ (background) enviarSincronizacion(servidor-b)
                  ├─ SINCRONIZAR_ESTADO → servidor-b
                  │       └─ SincronizarEstadoHandler: persiste mensajes nuevos
                  └─ por cada archivo en disco de A:
                          ├─ REPLICAR_ARCHIVO_STREAM → servidor-b
                          └─ stream de bytes → servidor-b
                                  └─ GestorTransferencias.finalizarTransferenciaS2S()
                                          ├─ mueve .tmp → archivo final
                                          └─ persiste en DB
```

---

## 12. Tabla de acciones

| Acción | Dirección | Handler |
|--------|-----------|---------|
| `CONECTAR` | Cliente → Servidor | `ConectarHandler` |
| `DESCONECTAR` | Cliente → Servidor | `DesconectarHandler` |
| `ENVIAR_MENSAJE` | Cliente → Servidor | `MensajeTextoHandler` |
| `ENVIAR_DOCUMENTO` | Cliente → Servidor | `EnviarArchivoHandler` |
| `INICIAR_STREAM` | Cliente → Servidor | `IniciarStreamHandler` |
| `FINALIZAR_STREAM` | Cliente → Servidor | `FinalizarStreamHandler` |
| `SOLICITAR_STREAM` | Cliente → Servidor | `ObtenerArchivoHandler` |
| `LISTAR_MENSAJES` | Cliente → Servidor | `ListarMensajesHandler` |
| `LISTAR_DOCUMENTOS` | Cliente → Servidor | `ListarDocumentosHandler` |
| `LISTAR_CLIENTES` | Cliente → Servidor | `ListarClientesHandler` |
| `LISTAR_SERVIDORES` | Cliente → Servidor | `ListarServidoresHandler` |
| `ESTADO_SERVIDOR` | Cliente → Servidor | `EstadoServidorHandler` |
| `REGISTRAR_SERVIDOR` | Servidor → Servidor | `RegistrarServidorHandler` |
| `DESCONECTAR_SERVIDOR` | Servidor → Servidor | `DesconectarServidorHandler` |
| `REPLICAR_MENSAJE` | Servidor → Servidor | `ReplicarMensajeHandler` |
| `REPLICAR_ARCHIVO` | Servidor → Servidor | `ReplicarArchivoHandler` |
| `REPLICAR_ARCHIVO_STREAM` | Servidor → Servidor | `ReplicarArchivoStreamHandler` |
| `REPLICAR_CLIENTES` | Servidor → Servidor | `ReplicarClientesHandler` |
| `SINCRONIZAR_ESTADO` | Servidor → Servidor | `SincronizarEstadoHandler` |
| `ENTREGAR_MENSAJE` | Servidor → Servidor | `EntregarMensajeHandler` |
| `LISTAR_LOGS` | Cliente/Servidor → Servidor | `ListarLogsRemotoHandler` |
