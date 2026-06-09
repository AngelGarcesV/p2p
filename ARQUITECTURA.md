# Arquitectura del Sistema — Mensajería P2P

## Despliegue en producción (múltiples máquinas)

```
  Máquina Gateway            Máquina Servidor 1       Máquina Servidor 2
  ─────────────────          ─────────────────────    ─────────────────────
  docker-compose             docker-compose           docker-compose
    .gateway.yml               .servidor.yml            .servidor.yml
  ┌─────────────┐            ┌───────────────────┐   ┌───────────────────┐
  │  gateway    │            │  servidor (Java)  │   │  servidor (Java)  │
  │  :5000      │◄──9090────►│  :9090 REST       │   │  :9090 REST       │
  │             │            │  :8080 TCP P2P ◄──┼───┼──► :8080 TCP P2P  │
  │  web(nginx) │            │  ml (FastAPI)     │   │  ml (FastAPI)     │
  │  :80        │            │  db (MySQL)       │   │  db (MySQL)       │
  └─────────────┘            └───────────────────┘   └───────────────────┘
       ▲
       │ HTTP
     Browser
```

### En cada máquina servidor

```bash
# 1. Copiar el template
cp env.servidor.example .env

# 2. Editar .env con los valores de ESTA máquina:
#    SERVER_ID=servidor1
#    SERVER_HOST=<IP de esta máquina>
#    SERVER_PEERS=servidor2:<IP-maq2>:8080,servidor3:<IP-maq3>:8080,...

# 3. Levantar
docker compose -f docker-compose.servidor.yml up -d --build
```

**Puertos que deben estar abiertos** en el firewall/router:
| Puerto | Protocolo | Quién se conecta |
|--------|-----------|-----------------|
| 8080 | TCP | Los otros 3 servidores (P2P handshake) |
| 9090 | TCP | El gateway (discovery + proxy REST) |

### En la máquina gateway

```bash
# 1. Copiar el template
cp env.gateway.example .env

# 2. Editar .env:
#    GATEWAY_SEEDS=<IP-maq1>:9090,<IP-maq2>:9090,<IP-maq3>:9090,<IP-maq4>:9090

# 3. Levantar
docker compose -f docker-compose.gateway.yml up -d --build
```

**Puertos que deben estar abiertos**:
| Puerto | Quién se conecta |
|--------|-----------------|
| 80 | Browsers (frontend) |
| 5000 | Browsers (API gateway) |

> **Nota sobre el frontend**: el browser hace las peticiones al gateway usando `http://<IP-gateway>:5000`.
> Si la IP de la máquina gateway no es `localhost`, hay que cambiar `gatewayUrl` en
> `FRONTEND/src/environments/environment.ts` antes de compilar.

---

### Para desarrollo local (todas las máquinas en una sola)

```bash
docker compose up --build   # usa docker-compose.yml (monolítico, 8 servicios)
```

---

## Qué existía antes

El proyecto ya tenía:
- **4 servidores Java** (Spring Boot + TCP socket) con persistencia en MySQL y federación P2P
- **Cliente JavaFX** de escritorio para interactuar con un servidor
- **Servicio ML** (FastAPI) para clasificación de géneros musicales

Lo que NO existía:
- Frontend web
- API Gateway
- `docker-compose.yml` funcional

---

## Qué se agregó

### 1. API Gateway (`./GATEWAY`)

.NET 8 + Ocelot. Hace dos cosas:

**A) Proxy de rutas (Ocelot)**

```
Browser → GET /servidor2/api/clientes
          → Gateway → servidor2:9090/api/clientes
          ← respuesta JSON
```

`ocelot.json` define una ruta wildcard por servidor:

```json
{ "UpstreamPathTemplate": "/servidor1/api/{everything}",
  "DownstreamHostAndPorts": [{ "Host": "servidor1", "Port": 9090 }] }
```

**B) Registro de servidores disponibles**

El gateway mantiene un `ServerRegistry` en memoria (`ConcurrentDictionary<string, ServidorInfo>`).

Al arrancar, llama `GET {seed}:9090/api/servidores` para cada seed configurado en `GATEWAY_SEEDS`. Si el servidor responde (cualquier 2xx), lo marca como `CONECTADO`.

El frontend consulta `GET /gateway/servidores` para saber qué servidores mostrar en el dropdown.

```
                        ┌──────────────────────────────────────┐
                        │           GATEWAY (.NET 8)           │
                        │                                      │
  /gateway/*  ──────→  │  MVC Controllers (GatewayController) │
                        │   GET /gateway/servidores            │
                        │   POST /gateway/refresh              │
                        │                                      │
  /servidorX/api/* ──→  │  Ocelot Proxy                        │
                        │   → servidorX:9090/api/*             │
                        └──────────────────────────────────────┘
```

**Separación del pipeline** (`Program.cs`): Ocelot es middleware terminal — intercepta TODO. Para que `/gateway/*` llegue a los controllers MVC, se usa `app.MapWhen()` antes de `app.UseOcelot()`:

```csharp
app.MapWhen(
    ctx => ctx.Request.Path.StartsWithSegments("/gateway"),
    branch => { branch.UseRouting(); branch.UseEndpoints(e => e.MapControllers()); });
await app.UseOcelot();
```

**Discovery on-demand**: Si el registry está vacío cuando el frontend pide la lista (porque los servidores tardaron en arrancar), el gateway hace el discovery en ese momento:

```csharp
if (!_registry.ObtenerConectados().Any())
    await _discovery.DiscoverAll();
```

**Post-request refresh**: Después de cada request proxiado, un middleware llama `RefreshOne(servidorId)` en background (fire-and-forget) para actualizar el estado del servidor.

---

### 2. Frontend Angular (`./FRONTEND`)

Angular 17, standalone components, Angular Material con tema dark.

**Routing** (`app.routes.ts`):

```
/           → redirect → /panel
/panel      → PanelComponent       (estado general del servidor)
/clientes   → ClientesComponent    (tabla de sesiones activas)
/mensajes   → MensajesComponent    (historial de mensajes)
/archivos   → ArchivosComponent    (archivos recibidos)
/logs       → LogsComponent        (logs del servidor)
/servidores → ServidoresComponent  (peers conocidos)
```

Todas las rutas viven dentro de `ShellComponent` que contiene el sidenav con:
- Selector de servidor (dropdown con los servidores CONECTADO del gateway)
- Botón **↻ Actualizar** (no hay auto-refresh — igual que el cliente JavaFX)
- Links de navegación

**Flujo de una petición**:

```
Usuario selecciona "servidor2" → click ↻ Actualizar
  → ApiService.get("servidor2", "clientes")
  → GET http://localhost:5000/servidor2/api/clientes
  → Ocelot → servidor2:9090/api/clientes
  → JSON → tabla Angular Material
```

**Servicios clave**:

| Servicio | Responsabilidad |
|---|---|
| `ServerService` | Carga lista de servidores, mantiene el seleccionado (signals) |
| `ApiService` | Construye la URL `/{servidorId}/api/{path}` y delega a HttpClient |

**Signals** en lugar de BehaviorSubject:

```typescript
private readonly _servidores = signal<Servidor[]>([]);
readonly servidores$ = this._servidores.asReadonly();
```

**Estilos** (`styles.scss`): Tema dark idéntico al cliente JavaFX:
- Fondo: `#0f0f1a` / `#1a1a2e`
- Accent: `#4a9eff`
- Cards con `border-radius: 8px` y sombra
- Padding de views: 32px

---

### 3. Docker Compose (`docker-compose.yml`)

8 servicios en la red `mensajeria-net`:

| Servicio | Imagen | Puertos expuestos al host |
|---|---|---|
| `db` | mysql:8.0 | ninguno |
| `ml` | FastAPI custom | ninguno |
| `servidor1..4` | Java custom | ninguno (solo en la red interna) |
| `gateway` | .NET custom | `5000:5000` |
| `web` | nginx + Angular | `80:80` |

Los servidores Java reciben su identidad por env vars:

```yaml
environment:
  SERVER_ID:    servidor1
  SERVER_HOST:  servidor1
  SERVER_PEERS: servidor2:8080,servidor3:8080,servidor4:8080
  MYSQL_URL:    jdbc:mysql://db:3306/cliente_servidor?...
```

El gateway recibe los seeds:

```yaml
environment:
  GATEWAY_SEEDS: "servidor1:9090,servidor2:9090,servidor3:9090,servidor4:9090"
```

---

## Bugs encontrados y resueltos

### Bug 1 — JAR pre-compilada sin los nuevos controllers

**Síntoma**: `GET /api/servidores` y `GET /api/mensajes` devolvían 404, pero `/api/clientes`, `/api/archivos`, `/api/logs` funcionaban.

**Causa**: La JAR en `target/` fue compilada antes de que se agregaran `ServidoresController.java` y `MensajesController.java` al código fuente. El Dockerfile copia la JAR pre-compilada, así que esas clases no existían en el container.

**Fix**: `mvn package -DskipTests` para recompilar, luego `docker compose up --build`.

---

### Bug 2 — Ocelot interceptaba `/gateway/servidores`

**Síntoma**: `GET /gateway/servidores` devolvía el error de Ocelot ("Failed to match Route configuration") en vez de responder el controller MVC.

**Causa**: `app.UseOcelot()` es middleware terminal. Intercepts ALL requests que no tienen match en las rutas configuradas antes.

**Fix**: `app.MapWhen()` para desviar `/gateway/*` al pipeline MVC antes de que llegue a Ocelot.

---

### Bug 3 — Discovery fallaba por timing (servidores lentos en arrancar)

**Síntoma**: El gateway arrancaba antes de que los servidores Java terminaran de inicializar Hibernate/Spring. El discovery de startup fallaba, el registry quedaba vacío, y el frontend veía "no hay servidores".

**Fix**: `GatewayController.GetServidores()` llama `DiscoverAll()` on-demand si el registry está vacío, más el endpoint `POST /gateway/refresh` para forzar re-discovery.

---

### Bug 4 — Gateway usaba estado P2P para determinar disponibilidad

**Síntoma**: Aunque todos los servidores respondían HTTP, el gateway devolvía lista vacía porque filtraba por `estado == "CONECTADO"` y los servidores reportaban a sus peers con estados como `"CLOSED"` o `"DESCONOCIDO"` (estados del protocolo TCP P2P, no del HTTP REST).

**Fix**: `RefreshFromHost()` simplificado — si el servidor responde al `GET /api/servidores`, se lo marca `CONECTADO` directamente por reachability. No se usa el estado que los peers reportan entre sí.

---

## Flujo end-to-end

```
Browser (puerto 80)
  │
  ├─ GET /                    → nginx sirve Angular SPA
  │
  └─ XHR GET /gateway/servidores
       │
       └─ Gateway (puerto 5000)
            │
            ├─ MVC: GatewayController.GetServidores()
            │    └─ ServerRegistry.ObtenerConectados() → [{servidor1, servidor2, ...}]
            │
            └─ Discovery: ServerDiscoveryService
                 └─ GET servidor1:9090/api/servidores
                      │
                      └─ Java Spring Boot (servidor1, red interna)
                           └─ MySQL (db, red interna)

Browser selecciona servidor2, click ↻ Actualizar
  │
  └─ XHR GET /servidor2/api/clientes
       │
       └─ Gateway (puerto 5000)
            └─ Ocelot: match /servidor2/api/{everything}
                 └─ Proxy → servidor2:9090/api/clientes
                      │
                      └─ Java Spring Boot (servidor2)
                           └─ Response JSON → browser → tabla mat-table
```
