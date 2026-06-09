namespace Gateway.Infrastructure;

public class ServerDiscoveryService : IHostedService
{
    private readonly ServerRegistry _registry;
    private readonly IHttpClientFactory _httpFactory;
    private readonly ILogger<ServerDiscoveryService> _logger;
    private readonly string[] _seeds;

    public ServerDiscoveryService(
        ServerRegistry registry,
        IHttpClientFactory httpFactory,
        ILogger<ServerDiscoveryService> logger,
        IConfiguration config)
    {
        _registry = registry;
        _httpFactory = httpFactory;
        _logger = logger;
        _seeds = (config["GATEWAY_SEEDS"] ?? "")
            .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
    }

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        if (_seeds.Length == 0)
        {
            _logger.LogWarning("GATEWAY_SEEDS no configurado. Registry vacío.");
            return;
        }

        foreach (var seed in _seeds)
        {
            var parts = seed.Split(':');
            if (parts.Length == 2 && int.TryParse(parts[1], out var port))
                _registry.Upsert(new ServidorInfo(parts[0], parts[0], port, "DESCONECTADO", 0, null));
            else
                _logger.LogError("Seed inválida ignorada: {Seed}", seed);
        }

        await Parallel.ForEachAsync(_seeds, cancellationToken, async (seed, ct) =>
        {
            var parts = seed.Split(':');
            if (parts.Length == 2 && int.TryParse(parts[1], out var port))
                await RefreshFromHost(parts[0], port, ct);
        });
    }

    public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;

    public async Task DiscoverAll()
    {
        await Parallel.ForEachAsync(_seeds, CancellationToken.None, async (seed, ct) =>
        {
            var parts = seed.Split(':');
            if (parts.Length == 2 && int.TryParse(parts[1], out var port))
                await RefreshFromHost(parts[0], port, ct);
        });
    }

    public async Task RefreshOne(string servidorId)
    {
        var info = _registry.ObtenerTodos().FirstOrDefault(s => s.ServidorId == servidorId);
        if (info is not null)
            await RefreshFromHost(info.Host, info.Puerto, CancellationToken.None);
    }

    private async Task RefreshFromHost(string host, int port, CancellationToken ct)
    {
        using var client = _httpFactory.CreateClient();
        client.Timeout = TimeSpan.FromSeconds(5);
        try
        {
            var url = $"http://{host}:{port}/api/servidores";
            await client.GetStringAsync(url, ct);
            // Server respondió → confirmamos reachability directa, estado CONECTADO
            _registry.Upsert(new ServidorInfo(host, host, port, "CONECTADO", 0, DateTime.UtcNow));
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Discovery falló para {Host}:{Port} — {Msg}", host, port, ex.Message);
            _registry.MarcarDesconectado(host);
        }
    }
}
