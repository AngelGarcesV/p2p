using System.Text.RegularExpressions;
using Gateway.Infrastructure;

namespace Gateway.Middleware;

public partial class PostRequestDiscoveryMiddleware
{
    private readonly RequestDelegate _next;
    private readonly ServerDiscoveryService _discovery;

    public PostRequestDiscoveryMiddleware(RequestDelegate next, ServerDiscoveryService discovery)
    {
        _next = next;
        _discovery = discovery;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        await _next(context);

        var match = ServidorPathRegex().Match(context.Request.Path);
        if (match.Success)
        {
            var servidorId = match.Groups["id"].Value;
            _ = Task.Run(() => _discovery.RefreshOne(servidorId));
        }
    }

    [GeneratedRegex(@"^/(?<id>[^/]+)/api/", RegexOptions.IgnoreCase)]
    private static partial Regex ServidorPathRegex();
}
