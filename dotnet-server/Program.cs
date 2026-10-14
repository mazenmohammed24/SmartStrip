using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Mvc;

var builder = WebApplication.CreateBuilder(args);
var port = Util.EnvInt("PORT", 8080, 1, 65535);
builder.WebHost.UseUrls($"http://0.0.0.0:{port}");
builder.Services.ConfigureHttpJsonOptions(o =>
{
    o.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
    o.SerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
});
builder.Services.AddSingleton<JsonStore>();
builder.Services.AddSingleton<MttlDriver>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<MttlDriver>());

var app = builder.Build();
app.MapSmartStripApi();

app.Run();

public static class Util
{
    public static string Env(string name, string fallback) => Environment.GetEnvironmentVariable(name) is { Length: > 0 } v ? v : fallback;
    public static int EnvInt(string name, int fallback, int min, int max) => int.TryParse(Environment.GetEnvironmentVariable(name), out var v) && v >= min && v <= max ? v : fallback;
    public static string Id() => Convert.ToBase64String(Guid.NewGuid().ToByteArray()).Replace("+", "-").Replace("/", "_").TrimEnd('=').Substring(0, 12);
}

public static class MttlLog
{
    private static readonly object Gate = new();
    public static string Path { get; } = System.IO.Path.Combine(AppContext.BaseDirectory, "data", "mttl.log");

    public static void Write(string message)
    {
        lock (Gate)
        {
            Directory.CreateDirectory(System.IO.Path.GetDirectoryName(Path)!);
            File.AppendAllText(Path, $"{DateTimeOffset.UtcNow:O} {message}{Environment.NewLine}");
        }
    }

    public static string[] Tail(int count = 200)
    {
        lock (Gate)
        {
            if (!File.Exists(Path)) return Array.Empty<string>();
            return File.ReadLines(Path).TakeLast(Math.Clamp(count, 1, 1000)).ToArray();
        }
    }
}

public record AdoptStripRequest(string DriverStripId, string Model, int RelayCount, string? IpAddress, string SuggestedName, string? Name, string? Location);
public record UpdateStripRequest(string? Name, string? Location, int? Order, string? Icon);
public record UpdateRelayRequest(string? State, string? Name, string? Icon);
public record ProvisionRequest(string ServerIp, string Ssid, string Password, string? Host, int? WaitMs);
public record PowerkOnOffRequest(string Mac, int Outlet, bool On);
public record ScheduleUpsert(string Name, string StripId, int? RelayIndex, string Action, string Time, int[] DaysOfWeek, bool? Enabled);
public record SettingsPatch(string? Theme, string? AccentColor, string? EnergyUnit, string? Currency, double? RatePerKwh, int? OverloadThresholdWatts, string? TimeFormat);
public record AutomationUpsert(string Name, string StripId, int? RelayIndex, string Trigger, double ThresholdWatts, int DurationMinutes, string Action, bool? Enabled);
public record ProtectionUpsert(string Name, string StripId, string Type, double ThresholdValue, string Action, bool? Enabled);

public class JsonStore
{
    private readonly object gate = new();
    private readonly string path = Path.Combine(AppContext.BaseDirectory, "data", "smartstrip-dotnet.json");
    private Db db = new();
    private readonly JsonSerializerOptions json = new() { PropertyNamingPolicy = JsonNamingPolicy.CamelCase, WriteIndented = true };

    public JsonStore()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        if (File.Exists(path)) db = JsonSerializer.Deserialize<Db>(File.ReadAllText(path), json) ?? new Db();
    }

    public Settings Settings { get { lock (gate) return db.Settings; } }
    public HashSet<string> AdoptedDriverIds() { lock (gate) return db.Strips.Select(s => NormalizeMac(s.DriverStripId)).ToHashSet(); }
    public StoredStrip? GetStoredStrip(string id) { lock (gate) return db.Strips.FirstOrDefault(s => s.Id == id); }
    public object? GetStrip(string id) { lock (gate) { var s = db.Strips.FirstOrDefault(x => x.Id == id); return s is null ? null : ToDto(s); } }
    public List<object> ListStrips() { lock (gate) return db.Strips.OrderBy(s => s.Order).Select(ToDto).ToList(); }
    public List<object> LivePower() { lock (gate) return db.Strips.Select(s => new { stripId = s.Id, totalWatts = db.Relays.Where(r => r.StripId == s.Id).Sum(r => r.Watts), relays = db.Relays.Where(r => r.StripId == s.Id).OrderBy(r => r.Index).Select(r => new { relayIndex = r.Index, watts = r.Watts }) }).Cast<object>().ToList(); }

    public StoredStrip Adopt(AdoptStripRequest req)
    {
        lock (gate)
        {
            var existing = db.Strips.FirstOrDefault(s => NormalizeMac(s.DriverStripId) == NormalizeMac(req.DriverStripId));
            if (existing is not null) return existing;
            var strip = new StoredStrip("strip_" + Util.Id(), req.Name ?? req.SuggestedName, req.Model, req.Location, "mttl", NormalizeMac(req.DriverStripId), req.IpAddress, "online", db.Strips.Count, DateTimeOffset.UtcNow, "power-strip");
            db.Strips.Add(strip);
            for (var i = 0; i < Math.Max(1, Math.Min(req.RelayCount, 8)); i++) db.Relays.Add(new StoredRelay("relay_" + Util.Id(), strip.Id, i, $"Outlet {i + 1}", "off", i switch { 0 => "lamp", 1 => "charger", 2 => "fridge", _ => "plug" }, DateTimeOffset.UtcNow));
            Save();
            return strip;
        }
    }

    public object? UpdateStrip(string id, UpdateStripRequest req)
    {
        lock (gate)
        {
            var s = db.Strips.FirstOrDefault(x => x.Id == id); if (s is null) return null;
            if (req.Name is not null) s.Name = req.Name;
            if (req.Location is not null) s.Location = req.Location;
            if (req.Order is not null) s.Order = req.Order.Value;
            if (req.Icon is not null) s.Icon = req.Icon;
            Save(); return ToDto(s);
        }
    }

    public bool RemoveStrip(string id) { lock (gate) { var ok = db.Strips.RemoveAll(s => s.Id == id) > 0; db.Relays.RemoveAll(r => r.StripId == id); Save(); return ok; } }
    public void UpdateRelay(string stripId, int index, string? name, string? icon) { lock (gate) { var r = db.Relays.FirstOrDefault(x => x.StripId == stripId && x.Index == index); if (r is null) return; if (name is not null) r.Name = name; if (icon is not null) r.Icon = icon; Save(); } }
    public void SetRelayState(string stripId, int index, string state) { lock (gate) { var r = db.Relays.FirstOrDefault(x => x.StripId == stripId && x.Index == index); if (r is null) return; r.State = state; r.LastChangedAt = DateTimeOffset.UtcNow; Save(); } }
    public void SetScan(string id, List<DiscoveredStrip> found) { lock (gate) { db.Scans[id] = new ScanRecord("done", 100, found.Count, found); Save(); } }
    public ScanRecord? GetScan(string id) { lock (gate) return db.Scans.GetValueOrDefault(id); }
    public object PowerSummary(string stripId) => new { stripId, todayKwh = 0, todayCost = 0, currency = db.Settings.Currency, liveWatts = db.Relays.Where(r => r.StripId == stripId).Sum(r => r.Watts) };
    public object PowerHistory(string stripId, string range, int? relayIndex)
    {
        lock (gate)
        {
            var cutoff = range switch { "day" => DateTimeOffset.UtcNow.AddDays(-1), "7d" => DateTimeOffset.UtcNow.AddDays(-7), "30d" => DateTimeOffset.UtcNow.AddDays(-30), "90d" => DateTimeOffset.UtcNow.AddDays(-90), _ => DateTimeOffset.UtcNow.AddDays(-1) };
            var points = db.PowerHistory.Where(p => p.StripId == stripId && p.Timestamp >= cutoff).OrderBy(p => p.Timestamp).Select(p => new { bucketStart = p.Timestamp.ToString("o"), avgW = p.TotalWatts, minW = p.TotalWatts, maxW = p.TotalWatts, kwh = Math.Round(p.TotalWatts * (5.0 / 3600.0), 5) }).ToList();
            return new { stripId, relayIndex, range, points };
        }
    }
    public List<StoredAutomation> Automations() { lock (gate) return db.Automations.ToList(); }
    public StoredAutomation SaveAutomation(string? id, AutomationUpsert req) { lock (gate) { var a = id is null ? null : db.Automations.FirstOrDefault(x => x.Id == id); a ??= new StoredAutomation("auto_" + Util.Id(), req.Name, req.StripId, req.RelayIndex, req.Trigger, req.ThresholdWatts, req.DurationMinutes, req.Action, req.Enabled ?? true, DateTimeOffset.UtcNow); a.Name = req.Name; a.Enabled = req.Enabled ?? a.Enabled; if (id is null) db.Automations.Add(a); Save(); return a; } }
    public bool DeleteAutomation(string id) { lock (gate) { var ok = db.Automations.RemoveAll(a => a.Id == id) > 0; Save(); return ok; } }
    public List<StoredProtection> Protections() { lock (gate) return db.Protections.ToList(); }
    public StoredProtection SaveProtection(string? id, ProtectionUpsert req) { lock (gate) { var p = id is null ? null : db.Protections.FirstOrDefault(x => x.Id == id); p ??= new StoredProtection("prot_" + Util.Id(), req.Name, req.StripId, req.Type, req.ThresholdValue, req.Action, req.Enabled ?? true, DateTimeOffset.UtcNow); p.Name = req.Name; p.Enabled = req.Enabled ?? p.Enabled; if (id is null) db.Protections.Add(p); Save(); return p; } }
    public bool DeleteProtection(string id) { lock (gate) { var ok = db.Protections.RemoveAll(p => p.Id == id) > 0; Save(); return ok; } }
    public List<StoredEvent> Events(string? stripId) { lock (gate) return db.Events.Where(e => stripId is null || e.StripId == stripId).OrderByDescending(e => e.CreatedAt).Take(200).ToList(); }
    public List<StoredNotification> Notifications(bool unreadOnly, int? limit) { lock (gate) return db.Notifications.Where(n => !unreadOnly || n.ReadAt is null).OrderByDescending(n => n.CreatedAt).Take(limit ?? 100).ToList(); }
    public void MarkRead(string id) { lock (gate) { var n = db.Notifications.FirstOrDefault(x => x.Id == id); if (n is not null) n.ReadAt = DateTimeOffset.UtcNow; Save(); } }
    public void MarkAllRead() { lock (gate) { foreach (var n in db.Notifications.Where(n => n.ReadAt is null)) n.ReadAt = DateTimeOffset.UtcNow; Save(); } }
    public void DeleteNotification(string id) { lock (gate) { db.Notifications.RemoveAll(n => n.Id == id); Save(); } }
    public List<StoredSchedule> Schedules() { lock (gate) return db.Schedules.ToList(); }
    public StoredSchedule? SaveSchedule(string? id, ScheduleUpsert req) { lock (gate) { var s = id is null ? null : db.Schedules.FirstOrDefault(x => x.Id == id); if (id is not null && s is null) return null; s ??= new StoredSchedule("sched_" + Util.Id(), req.Name, req.StripId, req.RelayIndex, req.Action, req.Time, req.DaysOfWeek, req.Enabled ?? true, null, DateTimeOffset.UtcNow); s.Name = req.Name; s.StripId = req.StripId; s.RelayIndex = req.RelayIndex; s.Action = req.Action; s.Time = req.Time; s.DaysOfWeek = req.DaysOfWeek; s.Enabled = req.Enabled ?? s.Enabled; if (id is null) db.Schedules.Add(s); Save(); return s; } }
    public void DeleteSchedule(string id) { lock (gate) { db.Schedules.RemoveAll(s => s.Id == id); Save(); } }
    public Settings UpdateSettings(SettingsPatch p) { lock (gate) { if (p.Theme is not null) db.Settings.Theme = p.Theme; if (p.AccentColor is not null) db.Settings.AccentColor = p.AccentColor; if (p.EnergyUnit is not null) db.Settings.EnergyUnit = p.EnergyUnit; if (p.Currency is not null) db.Settings.Currency = p.Currency; if (p.RatePerKwh is not null) db.Settings.RatePerKwh = p.RatePerKwh.Value; if (p.OverloadThresholdWatts is not null) db.Settings.OverloadThresholdWatts = p.OverloadThresholdWatts.Value; if (p.TimeFormat is not null) db.Settings.TimeFormat = p.TimeFormat; Save(); return db.Settings; } }
    public void Log(string type, string? stripId, string? stripName, string? driverStripId, int? relayIndex, string message, string cause) { lock (gate) { db.Events.Add(new StoredEvent("evt_" + Util.Id(), type, stripId, stripName, driverStripId, relayIndex, message, cause, DateTimeOffset.UtcNow)); Save(); } }
    public void ApplyTelemetry(MttlTelemetry t)
    {
        lock (gate)
        {
            var strip = db.Strips.FirstOrDefault(s => NormalizeMac(s.DriverStripId) == NormalizeMac(t.DriverStripId));
            if (strip is null) return;
            strip.Connectivity = "online"; strip.IpAddress = t.IpAddress;
            foreach (var o in t.Outlets)
            {
                var r = db.Relays.FirstOrDefault(x => x.StripId == strip.Id && x.Index == o.RelayIndex);
                if (r is null) continue; r.State = o.On ? "on" : "off"; r.Watts = o.Watts;
            }
            // Append a power history point (cap at 1000 per strip)
            var totalWatts = t.Outlets.Sum(o => o.Watts);
            db.PowerHistory.Add(new StoredPowerPoint(strip.Id, DateTimeOffset.UtcNow, Math.Round(totalWatts, 2)));
            var stripPoints = db.PowerHistory.Count(p => p.StripId == strip.Id);
            if (stripPoints > 1000) { var excess = db.PowerHistory.Where(p => p.StripId == strip.Id).OrderBy(p => p.Timestamp).Take(stripPoints - 1000).ToList(); foreach (var e in excess) db.PowerHistory.Remove(e); }
            Save();
        }
    }
    private object ToDto(StoredStrip s)
    {
        var relays = db.Relays.Where(r => r.StripId == s.Id).OrderBy(r => r.Index).Select(r => new { id = r.Id, stripId = s.Id, index = r.Index, name = r.Name, state = r.State, watts = r.Watts, icon = r.Icon, lastChangedAt = r.LastChangedAt }).ToList();
        return new { id = s.Id, name = s.Name, model = s.Model, location = s.Location, driverName = s.DriverName, driverStripId = s.DriverStripId, ipAddress = s.IpAddress, connectivity = s.Connectivity, order = s.Order, addedAt = s.AddedAt, relays, totalWatts = Math.Round(relays.Sum(r => r.watts), 2), icon = s.Icon };
    }
    private void Save() => File.WriteAllText(path, JsonSerializer.Serialize(db, json));
    public static string NormalizeMac(string mac) => Regex.Replace(mac, "[^0-9A-Fa-f]", "").ToUpperInvariant();
}

public class MttlDriver : BackgroundService
{
    private readonly ConcurrentDictionary<string, DeviceSession> sessions = new();
    private readonly JsonStore store;
    private TcpListener? listener;
    public int Port { get; } = Util.EnvInt("MTTL_DEVICE_PORT", 10086, 1, 65535);
    public MttlDriver(JsonStore store) => this.store = store;
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        listener = new TcpListener(IPAddress.Any, Port); listener.Start();
        MttlLog.Write($"tcp-listening port={Port}");
        _ = Task.Run(() => PollLoop(stoppingToken), stoppingToken);
        while (!stoppingToken.IsCancellationRequested)
        {
            var client = await listener.AcceptTcpClientAsync(stoppingToken);
            _ = Task.Run(() => HandleClient(client, stoppingToken), stoppingToken);
        }
    }
    public override Task StopAsync(CancellationToken cancellationToken) { listener?.Stop(); return base.StopAsync(cancellationToken); }
    public List<DiscoveredStrip> Scan(HashSet<string> adopted) => sessions.Values.Where(s => !adopted.Contains(s.Mac)).Select(s => new DiscoveredStrip(s.Mac, s.Model, s.Name, s.Ip, 4, 100)).ToList();
    public object[] Telemetry() => sessions.Values.Select(s => s.ToTelemetry()).ToArray();
    public object[] PowerkDevices() => sessions.Values.Select(s => s.ToPowerkDevice()).ToArray();
    public bool HasSession(string mac) => sessions.ContainsKey(JsonStore.NormalizeMac(mac));
    public Task AdoptAsync(string mac) => Task.CompletedTask;
    public async Task SetRelayAsync(string mac, int index, bool on)
    {
        mac = JsonStore.NormalizeMac(mac);
        if (sessions.TryGetValue(mac, out var s)) await s.SendAsync($"up:onoff:{index + 1}:{(on ? "on" : "off")}");
    }
    private async Task PollLoop(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            foreach (var s in sessions.Values) await s.SendAsync("up:getinfo:all");
            await Task.Delay(5000, ct);
        }
    }
    private async Task HandleClient(TcpClient client, CancellationToken ct)
    {
        var ep = client.Client.RemoteEndPoint?.ToString() ?? "";
        MttlLog.Write($"tcp-accepted remote={ep}");
        await using var stream = client.GetStream(); using var reader = new StreamReader(stream, Encoding.UTF8); var writer = new StreamWriter(stream, Encoding.UTF8) { AutoFlush = true, NewLine = "\r\n" };
        DeviceSession? session = null;
        while (!ct.IsCancellationRequested && client.Connected)
        {
            var line = await reader.ReadLineAsync(ct); if (line is null) break;
            line = line.Replace("\0", "").Trim(); if (line.Length == 0) continue;
            MttlLog.Write($"tcp-line remote={ep} line={line}");
            var boot = Protocol.ParseBoot(line);
            if (boot is not null)
            {
                session = new DeviceSession(boot.Value.Mac, boot.Value.Model, boot.Value.Fw, ep, writer);
                sessions[session.Mac] = session;
                MttlLog.Write($"tcp-boot mac={session.Mac} model={session.Model} fw={session.Fw} remote={ep}");
                await session.SendAsync("up:getinfo:all");
                MttlLog.Write($"tcp-send mac={session.Mac} command=up:getinfo:all");
                continue;
            }
            session?.Apply(line);
            if (session is not null) store.ApplyTelemetry(session.Telemetry());
        }
        MttlLog.Write($"tcp-closed remote={ep} mac={session?.Mac ?? "none"}");
        if (session is not null) sessions.TryRemove(session.Mac, out _);
    }
}

public class DeviceSession(string mac, string model, string fw, string ip, StreamWriter writer)
{
    public string Mac { get; } = JsonStore.NormalizeMac(mac); public string Model { get; } = model; public string Fw { get; } = fw; public string Ip { get; } = ip; public string Name => $"MTTL {Mac[^6..]}";
    private readonly OutletReading[] outlets = Enumerable.Range(0, 4).Select(i => new OutletReading(i, false, 0, 0, 0)).ToArray();
    public Task SendAsync(string command) => writer.WriteLineAsync(command);
    public void Apply(string line) { foreach (var o in Protocol.ParseGetInfo(line)) outlets[o.RelayIndex] = o; var ev = Protocol.ParseEvent(line); if (ev is not null) outlets[ev.Value.RelayIndex] = outlets[ev.Value.RelayIndex] with { On = ev.Value.On }; }
    public MttlTelemetry Telemetry() => new(Mac, Model, Fw, Ip, outlets.ToArray());
    public object ToTelemetry() => new { driverStripId = Mac, model = Model, fw = Fw, ipAddress = Ip, outlets };
    public object ToPowerkDevice() => new { mac = Mac, name = Name, model = Model, fw = Fw, ip = Ip, online = true, on = outlets.Any(o => o.On), power_w = Math.Round(outlets.Sum(o => o.Watts), 2), energy_kwh = Math.Round(outlets.Sum(o => o.EnergyKwh), 3), voltage = (double?)null, current_a = 0.0, rssi = (int?)null, outlets = outlets.Select(o => new { n = o.RelayIndex + 1, on = o.On, power_w = o.Watts, energy_kwh = o.EnergyKwh, temp_c = o.TemperatureC }) };
}

public static class Protocol
{
    static readonly Regex Boot = new(@"^up:bootinfo:([^;\r\n]+);([0-9A-Fa-f]{12});([0-9A-Fa-f]{12});([^;\r\n]+);connect$", RegexOptions.Compiled);
    static readonly Regex GetInfo = new(@"(?'channel'[1-4]):(?'-?\d+);(?'relay'on|off);(?'state'-?\d+);(?'overload'[^;:]+);(?'overheat'[^;:]+);(?'power'-?\d+);(?'energy'[0-9A-Fa-f]{8});[0-9A-Fa-f]{8};[0-9A-Fa-f]{8};[^;:]+;[0-9A-Fa-f]{2};(?'temperature'-?\d+)", RegexOptions.Compiled | RegexOptions.IgnoreCase);
    static readonly Regex Event = new(@"^up:event:onoff:([1-4]):(on|off)$", RegexOptions.Compiled | RegexOptions.IgnoreCase);
    public static (string Model, string Mac, string Fw)? ParseBoot(string line) { var m = Boot.Match(line); return m.Success ? (m.Groups[1].Value, m.Groups[3].Value, m.Groups[4].Value) : null; }
    public static IEnumerable<OutletReading> ParseGetInfo(string line) { foreach (Match m in GetInfo.Matches(line.StartsWith("up:getinfo:") ? line[11..] : line)) { var ch = int.Parse(m.Groups["channel"].Value); yield return new OutletReading(ch - 1, m.Groups["relay"].Value.Equals("on", StringComparison.OrdinalIgnoreCase), Math.Round(double.Parse(m.Groups["power"].Value) / 1000, 2), Math.Round(Convert.ToInt32(m.Groups["energy"].Value, 16) / 1000.0, 3), int.Parse(m.Groups["temperature"].Value)); } }
    public static (int RelayIndex, bool On)? ParseEvent(string line) { var m = Event.Match(line); return m.Success ? (int.Parse(m.Groups[1].Value) - 1, m.Groups[2].Value == "on") : null; }
}

public record Db { public List<StoredStrip> Strips { get; set; } = []; public List<StoredRelay> Relays { get; set; } = []; public List<StoredEvent> Events { get; set; } = []; public List<StoredNotification> Notifications { get; set; } = []; public List<StoredSchedule> Schedules { get; set; } = []; public Settings Settings { get; set; } = new(); public Dictionary<string, ScanRecord> Scans { get; set; } = []; public List<StoredPowerPoint> PowerHistory { get; set; } = []; public List<StoredAutomation> Automations { get; set; } = []; public List<StoredProtection> Protections { get; set; } = []; }
public record StoredStrip(string Id, string Name, string Model, string? Location, string DriverName, string DriverStripId, string? IpAddress, string Connectivity, int Order, DateTimeOffset AddedAt, string Icon) { public string Name { get; set; } = Name; public string? Location { get; set; } = Location; public string? IpAddress { get; set; } = IpAddress; public string Connectivity { get; set; } = Connectivity; public int Order { get; set; } = Order; public string Icon { get; set; } = Icon; }
public record StoredRelay(string Id, string StripId, int Index, string Name, string State, string Icon, DateTimeOffset LastChangedAt) { public string Name { get; set; } = Name; public string State { get; set; } = State; public string Icon { get; set; } = Icon; public DateTimeOffset LastChangedAt { get; set; } = LastChangedAt; public double Watts { get; set; } }
public record StoredEvent(string Id, string Type, string? StripId, string? StripName, string? DriverStripId, int? RelayIndex, string Message, string Cause, DateTimeOffset CreatedAt);
public record StoredNotification(string Id, string Type, string Title, string Body, string Severity, string? RelatedStripId, string? RelatedStripMac, int? RelatedRelayIndex, DateTimeOffset CreatedAt) { public DateTimeOffset? ReadAt { get; set; } }
public record StoredSchedule(string Id, string Name, string StripId, int? RelayIndex, string Action, string Time, int[] DaysOfWeek, bool Enabled, DateTimeOffset? LastFiredAt, DateTimeOffset CreatedAt) { public string Name { get; set; } = Name; public string StripId { get; set; } = StripId; public int? RelayIndex { get; set; } = RelayIndex; public string Action { get; set; } = Action; public string Time { get; set; } = Time; public int[] DaysOfWeek { get; set; } = DaysOfWeek; public bool Enabled { get; set; } = Enabled; }
public record Settings { public string Theme { get; set; } = "system"; public string AccentColor { get; set; } = "#0f766e"; public string EnergyUnit { get; set; } = "kWh"; public string Currency { get; set; } = "USD"; public double RatePerKwh { get; set; } = 0.16; public int OverloadThresholdWatts { get; set; } = 1800; public string TimeFormat { get; set; } = "24h"; }
public record ScanRecord(string Status, int Percent, int Found, List<DiscoveredStrip> Discovered);
public record DiscoveredStrip(string DriverStripId, string Model, string SuggestedName, string? IpAddress, int RelayCount, int SignalStrength);
public record OutletReading(int RelayIndex, bool On, double Watts, double EnergyKwh, int TemperatureC);
public record MttlTelemetry(string DriverStripId, string Model, string Fw, string IpAddress, OutletReading[] Outlets);
public record StoredPowerPoint(string StripId, DateTimeOffset Timestamp, double TotalWatts);
public record StoredAutomation(string Id, string Name, string StripId, int? RelayIndex, string Trigger, double ThresholdWatts, int DurationMinutes, string Action, bool Enabled, DateTimeOffset CreatedAt) { public string Name { get; set; } = Name; public bool Enabled { get; set; } = Enabled; }
public record StoredProtection(string Id, string Name, string StripId, string Type, double ThresholdValue, string Action, bool Enabled, DateTimeOffset CreatedAt) { public string Name { get; set; } = Name; public bool Enabled { get; set; } = Enabled; }

