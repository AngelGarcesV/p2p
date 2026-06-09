using Gateway.Infrastructure;
using Microsoft.AspNetCore.Mvc;

namespace Gateway.Controllers;

[ApiController]
[Route("gateway")]
public class GatewayController : ControllerBase
{
    private readonly ServerRegistry _registry;
    private readonly ServerDiscoveryService _discovery;

    public GatewayController(ServerRegistry registry, ServerDiscoveryService discovery)
    {
        _registry = registry;
        _discovery = discovery;
    }

    [HttpGet("servidores")]
    public async Task<IActionResult> GetServidores()
    {
        // Si el registry está vacío (startup falló por timing), re-intentamos discovery
        if (!_registry.ObtenerConectados().Any())
            await _discovery.DiscoverAll();

        return Ok(_registry.ObtenerConectados().Select(s => new
        {
            s.ServidorId,
            s.Host,
            s.Puerto,
            s.Estado,
            s.IntentosReconexion,
            s.UltimaConexion
        }));
    }

    [HttpPost("refresh")]
    public async Task<IActionResult> Refresh()
    {
        await _discovery.DiscoverAll();
        return Ok(new { message = "Discovery completado" });
    }
}
