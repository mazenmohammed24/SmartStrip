using System.Net;
using System.Net.Sockets;
using System.Text;
using Microsoft.AspNetCore.Mvc;

public static class ApiRoutes
{
    public static WebApplication MapSmartStripApi(this WebApplication app)
    {
        var store = app.Services.GetRequiredService<JsonStore>();
        var driver = app.Services.GetRequiredService<MttlDriver>();

app.MapGet("/api/health", () => Results.Ok(new { status = "ok", time = DateTimeOffset.UtcNow, driver = new { name = "mttl" } }));


// powerk.py compatibility API
app.MapGet("/api/state", () => Results.Ok(new { devices = driver.PowerkDevices(), localIp = LocalIps().FirstOrDefault() ?? "", port = driver.Port }));
app.MapPost("/api/onoff", async ([FromBody] PowerkOnOffRequest req) =>
{
    var channels = req.Outlet == 0 ? new[] { 1, 2, 3, 4 } : new[] { req.Outlet };
    if (channels.Any(ch => ch is < 1 or > 4)) return Results.BadRequest(new { error = "bad outlet" });
    var mac = JsonStore.NormalizeMac(req.Mac);
    if (!driver.HasSession(mac)) return Results.Json(new { error = "device offline" }, statusCode: 503);
    foreach (var ch in channels) await driver.SetRelayAsync(mac, ch - 1, req.On);
    return Results.Ok(new { ok = true, confirmed = true });
});

app.MapGet("/api/strips", () => Results.Ok(new { strips = store.ListStrips() }));
app.MapGet("/api/strips/{id}", (string id) => store.GetStrip(id) is { } strip ? Results.Ok(new { strip }) : Results.NotFound(new { error = "Strip not found" }));
app.MapPost("/api/strips/scan", () =>
{
    var scanId = "scan_" + Util.Id();
    store.SetScan(scanId, driver.Scan(store.AdoptedDriverIds()));
    return Results.Accepted($"/api/strips/scan/{scanId}", new { scanId });
});
app.MapGet("/api/strips/scan/{scanId}", (string scanId) => store.GetScan(scanId) is { } scan ? Results.Ok(scan) : Results.NotFound(new { error = "Scan not found" }));
app.MapPost("/api/strips", async ([FromBody] AdoptStripRequest req) =>
{
    var strip = store.Adopt(req);
    await driver.AdoptAsync(req.DriverStripId);
    store.Log("strip-added", strip.Id, strip.Name, strip.DriverStripId, null, $"{strip.Name} added", "manual");
    return Results.Created($"/api/strips/{strip.Id}", new { strip = store.GetStrip(strip.Id) });
});
app.MapPatch("/api/strips/{id}", ([FromRoute] string id, [FromBody] UpdateStripRequest req) =>
{
    var strip = store.UpdateStrip(id, req);
    return strip is null ? Results.NotFound(new { error = "Strip not found" }) : Results.Ok(new { strip });
});
app.MapDelete("/api/strips/{id}", (string id) =>
{
    var removed = store.RemoveStrip(id);
    return removed ? Results.Ok(new { ok = true }) : Results.NotFound(new { error = "Strip not found" });
});
app.MapPost("/api/strips/{id}/relays/all-on", async (string id) => await SetAllRelays(app, id, "on"));
app.MapPost("/api/strips/{id}/relays/all-off", async (string id) => await SetAllRelays(app, id, "off"));
app.MapPatch("/api/strips/{id}/relays/{index:int}", async (string id, int index, [FromBody] UpdateRelayRequest req) =>
{
    var strip = store.GetStoredStrip(id);
    if (strip is null) return Results.NotFound(new { error = "Strip not found" });
    if (req.Name is not null || req.Icon is not null) store.UpdateRelay(id, index, req.Name, req.Icon);
    if (req.State is "on" or "off")
    {
        await driver.SetRelayAsync(strip.DriverStripId, index, req.State == "on");
        store.SetRelayState(id, index, req.State);
        store.Log(req.State == "on" ? "relay-on" : "relay-off", id, strip.Name, strip.DriverStripId, index, $"{strip.Name} outlet {index + 1} turned {req.State}", "command");
    }
    return Results.Ok(new { strip = store.GetStrip(id) });
});

app.MapGet("/api/mttl/status", () => Results.Ok(new { devicePort = driver.Port, setupHost = Env("MTTL_SETUP_HOST", "192.168.1.1"), setupPort = 30300, advertisedIp = Env("MTTL_ADVERTISE_IP", ""), localIps = LocalIps(), strips = driver.Telemetry() }));
app.MapPost("/api/mttl/provision", async ([FromBody] ProvisionRequest req) => Results.Ok(await ProvisionAsync(req)));

app.MapGet("/api/power/live", () => Results.Ok(new { live = store.LivePower() }));
app.MapGet("/api/power/summary", (string stripId) => Results.Ok(store.PowerSummary(stripId)));
app.MapGet("/api/power/history", (string stripId, string? range, int? relayIndex) => Results.Ok(store.PowerHistory(stripId, range ?? "day", relayIndex)));

app.MapGet("/api/events", (string? stripId) => Results.Ok(new { events = store.Events(stripId) }));
app.MapGet("/api/notifications", (bool? unreadOnly, int? limit) => Results.Ok(new { notifications = store.Notifications(unreadOnly == true, limit) }));
app.MapPatch("/api/notifications/{id}/read", (string id) => { store.MarkRead(id); return Results.Ok(new { ok = true }); });
app.MapPost("/api/notifications/read-all", () => { store.MarkAllRead(); return Results.Ok(new { ok = true }); });
app.MapDelete("/api/notifications/{id}", (string id) => { store.DeleteNotification(id); return Results.Ok(new { ok = true }); });

app.MapGet("/api/schedules", () => Results.Ok(new { schedules = store.Schedules() }));
app.MapPost("/api/schedules", ([FromBody] ScheduleUpsert req) => Results.Created("/api/schedules", new { schedule = store.SaveSchedule(null, req) }));
app.MapPatch("/api/schedules/{id}", (string id, [FromBody] ScheduleUpsert req) => store.SaveSchedule(id, req) is { } s ? Results.Ok(new { schedule = s }) : Results.NotFound(new { error = "Schedule not found" }));
app.MapDelete("/api/schedules/{id}", (string id) => { store.DeleteSchedule(id); return Results.Ok(new { ok = true }); });
app.MapPost("/api/schedules/{id}/run-now", async (string id) =>
{
    var schedule = store.Schedules().FirstOrDefault(x => x.Id == id);
    if (schedule is null) return Results.NotFound(new { error = "Schedule not found" });
    var strip = store.GetStoredStrip(schedule.StripId);
    if (strip is null) return Results.NotFound(new { error = "Strip not found" });
    if (schedule.RelayIndex is null) await SetAllRelays(app, schedule.StripId, schedule.Action);
    else await driver.SetRelayAsync(strip.DriverStripId, schedule.RelayIndex.Value, schedule.Action == "on");
    return Results.Ok(new { ok = true });
});

app.MapGet("/api/settings", () => Results.Ok(new { settings = store.Settings }));
app.MapPatch("/api/settings", ([FromBody] SettingsPatch patch) => Results.Ok(new { settings = store.UpdateSettings(patch) }));
app.MapPost("/api/push/test", () => Results.Ok(new { ok = true, message = "Push is not implemented in the .NET server yet." }));

app.MapGet("/api/automations", () => Results.Ok(new { automations = store.Automations() }));
app.MapPost("/api/automations", ([FromBody] AutomationUpsert req) => Results.Created("/api/automations", new { automation = store.SaveAutomation(null, req) }));
app.MapPatch("/api/automations/{id}", (string id, [FromBody] AutomationUpsert req) => store.SaveAutomation(id, req) is { } a ? Results.Ok(new { automation = a }) : Results.NotFound(new { error = "Automation not found" }));
app.MapDelete("/api/automations/{id}", (string id) => store.DeleteAutomation(id) ? Results.Ok(new { ok = true }) : Results.NotFound(new { error = "Automation not found" }));

app.MapGet("/api/protections", () => Results.Ok(new { protections = store.Protections() }));
app.MapPost("/api/protections", ([FromBody] ProtectionUpsert req) => Results.Created("/api/protections", new { protection = store.SaveProtection(null, req) }));
app.MapPatch("/api/protections/{id}", (string id, [FromBody] ProtectionUpsert req) => store.SaveProtection(id, req) is { } p ? Results.Ok(new { protection = p }) : Results.NotFound(new { error = "Protection not found" }));
app.MapDelete("/api/protections/{id}", (string id) => store.DeleteProtection(id) ? Results.Ok(new { ok = true }) : Results.NotFound(new { error = "Protection not found" }));

app.MapGet("/api/mttl/registered", () => Results.Ok(new { strips = driver.Telemetry() }));
app.MapPost("/api/mttl/provision-status/{mac}", (string mac) =>
{
    var normalized = JsonStore.NormalizeMac(mac);
    var registered = driver.HasSession(normalized);
    return Results.Ok(new { mac = normalized, registered, online = registered });
});



        return app;
    }


    static async Task<IResult> SetAllRelays(WebApplication app, string id, string state)
    {
        var store = app.Services.GetRequiredService<JsonStore>();
        var driver = app.Services.GetRequiredService<MttlDriver>();
        var strip = store.GetStoredStrip(id);
        if (strip is null) return Results.NotFound(new { error = "Strip not found" });
        for (var i = 0; i < 4; i++)
        {
            await driver.SetRelayAsync(strip.DriverStripId, i, state == "on");
            store.SetRelayState(id, i, state);
        }
        store.Log(state == "on" ? "relay-on" : "relay-off", id, strip.Name, strip.DriverStripId, null, $"{strip.Name} all outlets turned {state}", "command");
        return Results.Ok(new { strip = store.GetStrip(id) });
    }

    static async Task<object> ProvisionAsync(ProvisionRequest req)
    {
        using var client = new TcpClient();
        await client.ConnectAsync(Env("MTTL_SETUP_HOST", "192.168.1.1"), 30300);
        await using var stream = client.GetStream();
        await stream.WriteAsync(Encoding.UTF8.GetBytes($"up:ip:{req.ServerIp}\r\n"));
        await Task.Delay(250);
        client.Close();
        using var client2 = new TcpClient();
        await client2.ConnectAsync(Env("MTTL_SETUP_HOST", "192.168.1.1"), 30300);
        await using var stream2 = client2.GetStream();
        await stream2.WriteAsync(Encoding.UTF8.GetBytes($"up:connect:{req.Ssid}:{req.Password}\r\n"));
        return new { ok = true, message = $"Strip will join {req.Ssid} and dial {req.ServerIp}:10086." };
    }

    static string Env(string name, string fallback) => Util.Env(name, fallback);
    static string[] LocalIps() => Dns.GetHostAddresses(Dns.GetHostName()).Where(ip => ip.AddressFamily == AddressFamily.InterNetwork).Select(ip => ip.ToString()).ToArray();
}
