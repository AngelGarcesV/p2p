using System.Collections.Concurrent;

namespace Gateway.Infrastructure;

public record ServidorInfo(
    string ServidorId,
    string Host,
    int Puerto,
    string Estado,
    int IntentosReconexion,
    DateTime? UltimaConexion
);

public class ServerRegistry
{
    private readonly ConcurrentDictionary<string, ServidorInfo> _servers = new();

    public void Upsert(ServidorInfo info) =>
        _servers[info.ServidorId] = info;

    public void MarcarDesconectado(string servidorId)
    {
        if (_servers.TryGetValue(servidorId, out var existing))
            _servers[servidorId] = existing with { Estado = "DESCONECTADO" };
    }

    public IEnumerable<ServidorInfo> ObtenerConectados() =>
        _servers.Values.Where(s => s.Estado.ToUpperInvariant() == "CONECTADO");

    public IEnumerable<ServidorInfo> ObtenerTodos() =>
        _servers.Values;
}
