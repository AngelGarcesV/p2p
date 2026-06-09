using Gateway.Infrastructure;
using Microsoft.AspNetCore.Mvc;

namespace Gateway.Controllers;

[ApiController]
[Route("gateway")]
public class GatewayController : ControllerBase
{
    private readonly ServerRegistry _registry;
    private readonly ServerDiscoveryService _discovery;
    private readonly IHttpClientFactory _httpFactory;

    public GatewayController(ServerRegistry registry, ServerDiscoveryService discovery, IHttpClientFactory httpFactory)
    {
        _registry = registry;
        _discovery = discovery;
        _httpFactory = httpFactory;
    }

    [HttpGet("servidores")]
    public async Task<IActionResult> GetServidores()
    {
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

    [HttpGet("{servidorId}/api/{**path}")]
    public async Task<IActionResult> Proxy(string servidorId, string path)
    {
        var server = _registry.ObtenerTodos().FirstOrDefault(s => s.ServidorId == servidorId);
        if (server is null)
            return NotFound(new { error = $"Servidor '{servidorId}' no registrado" });

        var queryString = HttpContext.Request.QueryString.Value ?? "";
        var targetUrl = $"http://{server.Host}:{server.Puerto}/api/{path}{queryString}";

        using var client = _httpFactory.CreateClient();
        client.Timeout = TimeSpan.FromSeconds(30);

        try
        {
            var response = await client.GetAsync(targetUrl);
            var content = await response.Content.ReadAsStringAsync();
            _ = Task.Run(() => _discovery.RefreshOne(servidorId));
            return Content(content, "application/json");
        }
        catch (Exception ex)
        {
            _ = Task.Run(() => _discovery.RefreshOne(servidorId));
            return StatusCode(502, new { error = "Servidor no disponible", detail = ex.Message });
        }
    }

    [HttpPost("refresh")]
    public async Task<IActionResult> Refresh()
    {
        await _discovery.DiscoverAll();
        return Ok(new { message = "Discovery completado" });
    }
}
