using Gateway.Infrastructure;
using Gateway.Middleware;
using Ocelot.DependencyInjection;
using Ocelot.Middleware;

var builder = WebApplication.CreateBuilder(args);

builder.Configuration.AddJsonFile("ocelot.json", optional: false, reloadOnChange: true);

builder.Services.AddSingleton<ServerRegistry>();
builder.Services.AddHttpClient();
builder.Services.AddSingleton<ServerDiscoveryService>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<ServerDiscoveryService>());
builder.Services.AddOcelot();
builder.Services.AddCors(options =>
    options.AddDefaultPolicy(policy =>
        policy.AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader()));
builder.Services.AddControllers();

var app = builder.Build();

app.UseCors();

// /gateway/* → MVC controllers, bypasa Ocelot completamente
app.MapWhen(
    ctx => ctx.Request.Path.StartsWithSegments("/gateway"),
    branch =>
    {
        branch.UseRouting();
        branch.UseEndpoints(e => e.MapControllers());
    });

// Todo lo demás → discovery post-request + Ocelot proxy
app.UseMiddleware<PostRequestDiscoveryMiddleware>();
await app.UseOcelot();
await app.RunAsync();
