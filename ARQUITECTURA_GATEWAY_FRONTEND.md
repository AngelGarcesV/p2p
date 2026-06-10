# Gateway + Frontend — Cómo funciona la arquitectura

## Visión general

```
[Angular Frontend]  →  [API Gateway .NET]  →  [Servidores Java 1-4]
                                                      ↕  TCP Socket
                                               [Federación P2P entre pares]
```

El frontend **nunca habla directamente con los servidores Java**. Todo pasa por el gateway, que actúa como punto único de entrada y proxy HTTP.

---

## Servidores Java

Cada servidor Java corre **dos puertos en paralelo**:

| Puerto | Protocolo | Para qué sirve |
|--------|-----------|----------------|
| `9090` | HTTP/REST (Spring Boot) | Expone la API de datos |
| `8080` | TCP Socket | Comunicación P2P entre servidores |

### Endpoints REST de cada servidor

```
GET /api/clientes    → lista de clientes registrados
GET /api/mensajes    → historial de mensajes
GET /api/archivos    → archivos subidos
GET /api/logs        → logs del servidor
GET /api/servidores  → lista de peers que este servidor conoce
```

El endpoint `/api/servidores` es clave: el gateway lo usa para saber si un servidor está vivo.

### Federación P2P (TCP :8080)

Al arrancar, cada servidor lee su lista de peers del `application.properties`:

```properties
server.id=servidor1
server.peers=servidor2:8080,servidor3:8080,servidor4:8080
server.port=9090
```

`GestorServidoresPeer` hace tres cosas:

1. **Handshake TCP** → envía `REGISTRAR_SERVIDOR` a cada peer listado
2. **Descubrimiento transitivo** → parsea la respuesta del peer para encontrar peers que ese peer conoce, que este servidor no conocía aún
3. **Replicación broadcast** → cuando llega un mensaje o cliente nuevo, lo replica a todos los peers vía TCP

La federación es eventual y tolerante a fallos: si un servidor se cae y vuelve, el handshake lo reintegra.

---

## API Gateway (.NET)

El gateway es el único punto de entrada para el frontend. Tiene dos responsabilidades separadas:

### A) Registro y discovery de servidores

Al arrancar, `ServerDiscoveryService` lee la variable de entorno `GATEWAY_SEEDS`:

```
GATEWAY_SEEDS=servidor1:9090,servidor2:9090,servidor3:9090,servidor4:9090
```

Y hace esto por cada seed:

```
GET http://servidor1:9090/api/servidores
  → si responde → marca como CONECTADO en el registry
  → si no responde → queda como DESCONECTADO
```

El estado de cada servidor se guarda en un `ServerRegistry` (diccionario en memoria):

```
ServidorId:          "servidor1"
Host:                "servidor1"          ← nombre de contenedor (DNS Docker interno)
Puerto:              9090
Estado:              "CONECTADO" | "DESCONECTADO"
IntentosReconexion:  0
UltimaConexion:      2026-06-09T10:30:00Z
```

### B) Proxy HTTP hacia los servidores

Cuando el frontend pide datos de un servidor específico, el gateway construye la URL destino y hace la request internamente:

```
Frontend → GET /gateway/servidor2/api/clientes
                          ↓
Gateway → GET http://servidor2:9090/api/clientes
                          ↓
          respuesta JSON de vuelta al frontend
```

### Endpoints del gateway

```
GET  /gateway/servidores          → devuelve la lista del registry (CONECTADO + DESCONECTADO)
GET  /gateway/{id}/api/{path}     → proxy hacia el servidor indicado
POST /gateway/refresh             → fuerza re-discovery en todos los seeds registrados
```

### Heartbeat pasivo (PostRequestDiscoveryMiddleware)

Después de cada request proxificada, el middleware ejecuta en background un `RefreshOne(servidorId)`:

```
Frontend hace GET /gateway/servidor1/api/clientes
                          ↓
Gateway hace el proxy y devuelve la respuesta
                          ↓
Middleware (en background): GET http://servidor1:9090/api/servidores
  → actualiza estado y timestamp en el registry
```

Esto funciona como heartbeat pasivo: cada vez que se usa un servidor, el gateway confirma si sigue vivo.

### Separación del pipeline (Program.cs)

Ocelot es middleware terminal — intercepta todo. Para que `/gateway/*` llegue a los controllers MVC en lugar de a Ocelot, se usa `MapWhen()` antes de `UseOcelot()`:

```csharp
// Rutas /gateway/* → MVC controllers
app.MapWhen(
    ctx => ctx.Request.Path.StartsWithSegments("/gateway"),
    branch => {
        branch.UseRouting();
        branch.UseEndpoints(e => e.MapControllers());
    });

// Middleware de heartbeat
app.UseMiddleware<PostRequestDiscoveryMiddleware>();

// Todo lo demás → Ocelot proxy
await app.UseOcelot();
```

---

## Frontend Angular

El frontend es una SPA con Standalone Components y signals de Angular 17.

### Servicios principales

**`ServerService`** — gestiona la lista de servidores disponibles:

```typescript
// Llama al gateway y guarda la lista en un signal
cargarServidores(): Observable<Servidor[]> {
    return this.http.get<Servidor[]>(`${this.gatewayUrl}/gateway/servidores`)
        .pipe(
            tap(lista => {
                this._servidores.set(lista);
                if (lista.length > 0 && this._seleccionado() === null)
                    this._seleccionado.set(lista[0]);  // selecciona el primero automáticamente
            })
        );
}
```

**`ApiService`** — proxy genérico para consultar cualquier servidor:

```typescript
get<T>(servidorId: string, path: string, params?): Observable<T> {
    const url = `${this.base}/gateway/${servidorId}/api/${path}`;
    return this.http.get<T>(url, { params: httpParams });
}
```

### Cómo los componentes usan estos servicios

```typescript
// ClientesComponent
this.api.get<Cliente[]>(srv.servidorId, 'clientes').subscribe(...)
// → GET /gateway/servidor1/api/clientes

// MensajesComponent
this.api.get<Mensaje[]>(srv.servidorId, 'mensajes', { username: '...' }).subscribe(...)
// → GET /gateway/servidor2/api/mensajes?username=...
```

### Flujo completo de una operación

```
1. App arranca → ShellComponent llama ServerService.cargarServidores()
                  → GET /gateway/servidores
                  → Gateway devuelve: [servidor1 CONECTADO, servidor2 CONECTADO, ...]
                  → Signal _servidores se actualiza
                  → Dropdown del sidenav muestra los servidores

2. Usuario selecciona "servidor2" → signal _seleccionado cambia a servidor2

3. Usuario navega a /clientes → ClientesComponent.ngOnInit()
                  → ApiService.get("servidor2", "clientes")
                  → GET /gateway/servidor2/api/clientes
                  → Gateway hace proxy a http://servidor2:9090/api/clientes
                  → JSON → tabla Angular Material
```

### Sección "Servidores" — ServidoresComponent

Esta vista muestra la tabla del registry del gateway (todos los servidores, conectados y desconectados).

Tiene un botón **Actualizar** que llama:

```typescript
cargar(): void {
    this.serverService.cargarServidores().subscribe({
        next: lista => this.datos.set(lista),
        error: err   => this.error.set(err.message)
    });
}
```

Ese botón hace `GET /gateway/servidores` → recarga lo que el gateway ya conoce en su registry.

---

## Docker Compose — cómo están conectados

```yaml
networks:
  mensajeria-net:   # todos los contenedores comparten esta red bridge

services:
  servidor1:
    environment:
      SERVER_ID:    "servidor1"
      SERVER_HOST:  "servidor1"     # nombre del contenedor = hostname DNS interno
      SERVER_PEERS: "servidor2:8080,servidor3:8080,servidor4:8080"
    expose: [8080, 9090]            # visible internamente, no desde el host

  gateway:
    environment:
      GATEWAY_SEEDS: "servidor1:9090,servidor2:9090,servidor3:9090,servidor4:9090"
    ports:
      - "5000:5000"                 # expuesto al host (y al browser vía nginx)

  web:                              # Nginx sirve el build de Angular
    ports:
      - "80:80"                     # el browser accede por acá
```

El frontend en producción habla con el gateway a través de Nginx con `proxy_pass`. Desde el browser todo se ve como el mismo origen (puerto 80).

---

## ¿Si agregás un Servidor 5, se puede ver recargando la sección de servidores?

**No.** Hacer clic en "Actualizar" en la sección de servidores **no va a mostrar servidor5**.

### Por qué no funciona

El flujo del botón "Actualizar" es:

```
Botón "Actualizar"
       ↓
GET /gateway/servidores
       ↓
GatewayController.GetServidores()
       ↓
ServerRegistry.ObtenerConectados()
       ↓  ← solo devuelve lo que ya está registrado
[servidor1, servidor2, servidor3, servidor4]
```

El gateway solo conoce los servidores que estaban en `GATEWAY_SEEDS` cuando arrancó. Si servidor5 no estaba ahí, no existe para el gateway.

Incluso el endpoint `POST /gateway/refresh` no ayuda: ese endpoint llama `DiscoverAll()` que reintenta los mismos seeds que ya están en el registry, no incorpora nuevos.

### Qué hay que hacer para agregar servidor5

Son cuatro cambios en `docker-compose.yml` y un `docker compose up`:

**1. Agregar el servicio servidor5:**
```yaml
servidor5:
  build: ./SERVIDOR/JavaMensajeriaServidor
  environment:
    SERVER_ID:    "servidor5"
    SERVER_HOST:  "servidor5"
    SERVER_PEERS: "servidor1:8080,servidor2:8080,servidor3:8080,servidor4:8080"
    TCP_PORT:     8080
    SERVER_PORT:  9090
  expose: [8080, 9090]
  networks: [mensajeria-net]
  depends_on: [db]
```

**2. Agregar servidor5 a los peers de los otros servidores:**
```yaml
servidor1:
  environment:
    SERVER_PEERS: "servidor2:8080,servidor3:8080,servidor4:8080,servidor5:8080"
# repetir para servidor2, servidor3, servidor4
```

**3. Agregar servidor5 a los seeds del gateway:**
```yaml
gateway:
  environment:
    GATEWAY_SEEDS: "servidor1:9090,servidor2:9090,servidor3:9090,servidor4:9090,servidor5:9090"
```

**4. Recrear los contenedores afectados:**
```bash
docker compose up -d --build servidor5 servidor1 servidor2 servidor3 servidor4 gateway
```

Después de esto, el gateway incluye servidor5 en su discovery inicial, y el botón "Actualizar" del frontend sí lo mostrará.

### ¿Por qué el diseño es así (no dinámico)?

El sistema asume que el conjunto de servidores es **conocido en tiempo de despliegue**. Los seeds se leen del entorno al arrancar y se guardan solo en memoria. Si el gateway se reinicia sin servidor5 en `GATEWAY_SEEDS`, desaparece.

Para soporte dinámico real habría que agregar:
- Un endpoint `POST /gateway/seeds/{host}/{puerto}` que registre un nuevo seed en caliente
- Persistir la lista de seeds en archivo o DB (no solo en memoria)
- Llamar `RefreshOne()` inmediatamente al registrar el nuevo seed

---

## Resumen de responsabilidades

| Componente | Responsabilidad |
|------------|-----------------|
| Servidor Java :9090 | Expone datos (clientes, mensajes, etc.) y lista de peers vía REST |
| Servidor Java :8080 | Federación P2P — handshake, replicación, descubrimiento transitivo |
| `ServerDiscoveryService` | Lee seeds del entorno, hace discovery al arrancar y en `/gateway/refresh` |
| `PostRequestDiscoveryMiddleware` | Heartbeat pasivo — actualiza estado del servidor tras cada request |
| `ServerRegistry` | Estado en memoria de todos los servidores conocidos |
| `GatewayController` | Lista servidores, proxifica requests, expone endpoint de refresh |
| Frontend `ServerService` | Carga y almacena la lista de servidores en signals Angular |
| Frontend `ApiService` | Construye URLs `GET /gateway/{id}/api/{path}` y ejecuta las requests |
