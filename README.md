# Anker Solix Binding

This binding integrates the **Anker Solix Solarbank 3 E2700 Pro** (model A17C5) energy storage system with openHAB.
It communicates with the Anker cloud API to provide real-time monitoring of power flows, battery state, and energy counters, as well as control of device settings.

Two data transport modes are supported:

- **REST API polling** (default) — periodic polling of the Anker cloud, suitable for basic monitoring
- **MQTT real-time updates** (optional) — near-instant telemetry via the Anker MQTT broker, plus writable device controls

## Supported Things

| Thing Type | Thing ID    | Description                                                        |
|------------|-------------|--------------------------------------------------------------------|
| Bridge     | `account`   | Connection to an Anker Solix cloud account                         |
| Thing      | `solarbank` | Anker Solix Solarbank 3 E2700 Pro (A17C5), child of account bridge |

## Prerequisites

- An **Anker account** with a registered Solarbank 3 Pro device
- The Solarbank must already be set up and operational via the Anker mobile app
- You will need the **Site ID** and **Device Serial Number** from your Anker account (visible in the Anker app under device settings)
- Internet connectivity (this is a cloud-based binding)

## Installation

### From a Pre-Built JAR

1. Build the project or obtain the JAR file `org.openhab.binding.ankersolix-5.1.0-SNAPSHOT.jar`
2. Copy the JAR into your openHAB `addons/` directory:

   ```bash
   cp target/org.openhab.binding.ankersolix-5.1.0-SNAPSHOT.jar /usr/share/openhab/addons/
   ```

3. openHAB will automatically discover and load the binding
4. Verify installation in the openHAB UI under **Settings > Bindings** — "Anker Solix Binding" should appear

### Building from Source

```bash
mvn clean package -DskipChecks
cp target/org.openhab.binding.ankersolix-5.1.0-SNAPSHOT.jar /usr/share/openhab/addons/
```

## Discovery

Automatic discovery is not supported. Things must be configured manually (see below).

## Configuration

### Bridge: Anker Solix Account

The bridge represents your Anker cloud account and manages authentication, API polling, and the optional MQTT connection.

| Parameter             | Type    | Required | Default | Description                                            |
|-----------------------|---------|----------|---------|--------------------------------------------------------|
| `email`               | text    | Yes      | —       | Anker account email address                            |
| `password`            | text    | Yes      | —       | Anker account password                                 |
| `country`             | text    | Yes      | `DE`    | Two-letter country code (e.g. `DE`, `US`, `GB`)        |
| `server`              | text    | Yes      | `eu`    | API server region: `eu` (Europe) or `com` (US/Global)  |
| `restPollingInterval` | integer | No       | `120`   | REST API polling interval in seconds (minimum 60)      |
| `enableMqtt`          | boolean | No       | `false` | Enable MQTT for real-time updates and MQTT controls    |

**Notes:**

- The password is encrypted before transmission using ECDH key exchange — it is never sent in plain text to the API.
- The REST API has rate limiting (max 5 requests per 60 seconds). Setting the polling interval below 60 seconds is not allowed.
- Enable MQTT if you want near real-time power data (~5–30 second updates) and access to the **Settings** channel group for device controls.

### Thing: Solarbank 3 Pro

Each Solarbank device is configured as a child of the account bridge.

| Parameter  | Type | Required | Description                      |
|------------|------|----------|----------------------------------|
| `siteId`   | text | Yes      | Anker Solix site identifier      |
| `deviceSn` | text | Yes      | Solarbank device serial number   |

**Finding your Site ID and Device Serial Number:**

The easiest way is to let the binding discover them for you:

1. Configure the account bridge with your email, password, country, and server region (leave the Solarbank thing unconfigured for now)
2. The binding logs all sites and devices at **INFO** level after successful login
3. Check the openHAB log:

   ```
   log:tail org.openhab.binding.ankersolix
   ```

   You will see output like:

   ```
   Found 1 Anker Solix site(s):
     Site: name='My Home', siteId='XXXXXXXXXXXXXXXX'
       Device: name='Solarbank 3 Pro', deviceSn='YYYYYYYYYYYYYYYY', firmware='v1.2.3'
   ```

4. Copy the `siteId` and `deviceSn` values into your Solarbank thing configuration

Alternatively, you can find these values in the **Anker mobile app** under your device/site settings.

## Thing Configuration Examples

### Via `.things` file

```java
Bridge ankersolix:account:myaccount "Anker Account" [
    email="your.email@example.com",
    password="yourPassword",
    country="DE",
    server="eu",
    restPollingInterval=120,
    enableMqtt=true
] {
    Thing solarbank mysolarbank "Solarbank 3 Pro" [
        siteId="XXXXXXXXXXXXXXXX",
        deviceSn="YYYYYYYYYYYYYYYY"
    ]
}
```

### Via `.items` file

```java
// Power
Number:Power    Solarbank_PV_Power          "PV Power [%.0f W]"             { channel="ankersolix:solarbank:myaccount:mysolarbank:power#photovoltaic-power" }
Number:Power    Solarbank_Output_Power      "Output Power [%.0f W]"         { channel="ankersolix:solarbank:myaccount:mysolarbank:power#output-power" }
Number:Power    Solarbank_AC_Output         "AC Output [%.0f W]"            { channel="ankersolix:solarbank:myaccount:mysolarbank:power#ac-output-power" }
Number:Power    Solarbank_Home_Demand       "Home Demand [%.0f W]"          { channel="ankersolix:solarbank:myaccount:mysolarbank:power#home-demand" }

// Solar Strings
Number:Power    Solarbank_PV1               "PV String 1 [%.0f W]"          { channel="ankersolix:solarbank:myaccount:mysolarbank:solar#pv1-power" }
Number:Power    Solarbank_PV2               "PV String 2 [%.0f W]"          { channel="ankersolix:solarbank:myaccount:mysolarbank:solar#pv2-power" }
Number:Power    Solarbank_PV3               "PV String 3 [%.0f W]"          { channel="ankersolix:solarbank:myaccount:mysolarbank:solar#pv3-power" }
Number:Power    Solarbank_PV4               "PV String 4 [%.0f W]"          { channel="ankersolix:solarbank:myaccount:mysolarbank:solar#pv4-power" }

// Battery
Number:Power    Solarbank_Battery_Power     "Battery Power [%.0f W]"        { channel="ankersolix:solarbank:myaccount:mysolarbank:battery#battery-power" }
Number          Solarbank_Battery_SOC       "Battery SOC [%.0f %%]"         { channel="ankersolix:solarbank:myaccount:mysolarbank:battery#battery-soc" }
Number:Temperature Solarbank_Temperature    "Temperature [%.1f °C]"         { channel="ankersolix:solarbank:myaccount:mysolarbank:battery#temperature" }

// Grid
Number:Power    Solarbank_Grid_Power        "Grid Power [%.0f W]"           { channel="ankersolix:solarbank:myaccount:mysolarbank:grid#grid-power" }

// Energy
Number:Energy   Solarbank_PV_Yield          "PV Yield Total [%.2f kWh]"     { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#pv-yield" }
Number:Energy   Solarbank_Daily_Solar       "Daily Solar [%.2f kWh]"        { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#daily-solar" }
Number:Energy   Solarbank_Daily_Charge      "Daily Charge [%.2f kWh]"       { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#daily-charge" }
Number:Energy   Solarbank_Daily_Discharge   "Daily Discharge [%.2f kWh]"    { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#daily-discharge" }
Number:Energy   Solarbank_Daily_Import      "Daily Grid Import [%.2f kWh]"  { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#daily-grid-import" }
Number:Energy   Solarbank_Daily_Export      "Daily Grid Export [%.2f kWh]"  { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#daily-grid-export" }

// REST Controls (work without MQTT)
Number:Power    Solarbank_Home_Load         "Home Load Preset [%.0f W]"     { channel="ankersolix:solarbank:myaccount:mysolarbank:restControl#home-load" }
Number:Power    Solarbank_Output_Limit      "Output Limit [%.0f W]"         { channel="ankersolix:solarbank:myaccount:mysolarbank:restControl#output-limit" }
Switch          Solarbank_Grid_Export       "Grid Export"                   { channel="ankersolix:solarbank:myaccount:mysolarbank:restControl#grid-export-switch" }

// MQTT Settings (require enableMqtt=true)
Number          Solarbank_Min_SOC           "Min SOC [%.0f %%]"             { channel="ankersolix:solarbank:myaccount:mysolarbank:settings#min-soc" }
Number:Power    Solarbank_Max_Load          "Max Load [%.0f W]"             { channel="ankersolix:solarbank:myaccount:mysolarbank:settings#max-load" }
Switch          Solarbank_AC_Socket         "AC Socket"                     { channel="ankersolix:solarbank:myaccount:mysolarbank:settings#ac-socket-switch" }
Switch          Solarbank_Light             "Status Light"                  { channel="ankersolix:solarbank:myaccount:mysolarbank:settings#light-switch" }

// Info
String          Solarbank_Firmware          "Firmware [%s]"                 { channel="ankersolix:solarbank:myaccount:mysolarbank:info#firmware-version" }
Switch          Solarbank_Online            "Online"                        { channel="ankersolix:solarbank:myaccount:mysolarbank:info#online-status" }
DateTime        Solarbank_Last_Update       "Last Update [%1$tF %1$tR]"    { channel="ankersolix:solarbank:myaccount:mysolarbank:info#last-update" }
```

## Channels

### Power (read-only)

| Channel                   | Type         | Description                     |
|---------------------------|--------------|---------------------------------|
| `power#photovoltaic-power`| Number:Power | Total PV generation (W)         |
| `power#output-power`      | Number:Power | Total output power (W)          |
| `power#ac-output-power`   | Number:Power | AC output power, signed (W)     |
| `power#home-demand`       | Number:Power | Total home power demand (W)     |

### Solar Strings (read-only)

| Channel            | Type         | Description              |
|--------------------|--------------|--------------------------|
| `solar#pv1-power`  | Number:Power | PV String 1 power (W)   |
| `solar#pv2-power`  | Number:Power | PV String 2 power (W)   |
| `solar#pv3-power`  | Number:Power | PV String 3 power (W)   |
| `solar#pv4-power`  | Number:Power | PV String 4 power (W)   |

### Battery (read-only)

| Channel                  | Type                | Description                                      |
|--------------------------|---------------------|--------------------------------------------------|
| `battery#battery-power`  | Number:Power        | Battery power (+charge / -discharge) (W)         |
| `battery#battery-soc`    | Number:Dimensionless| State of charge (0–100 %)                        |
| `battery#temperature`    | Number:Temperature  | Battery temperature (°C)                         |

### Grid (read-only)

| Channel            | Type         | Description                            |
|--------------------|--------------|----------------------------------------|
| `grid#grid-power`  | Number:Power | Grid power (+import / -export) (W)     |

### Energy (read-only)

| Channel                      | Type          | Description                    |
|------------------------------|---------------|--------------------------------|
| `energy#pv-yield`            | Number:Energy | Total PV energy yield (kWh)    |
| `energy#charged-energy`      | Number:Energy | Total energy charged (kWh)     |
| `energy#discharged-energy`   | Number:Energy | Total energy discharged (kWh)  |
| `energy#daily-solar`         | Number:Energy | Solar generation today (kWh)   |
| `energy#daily-charge`        | Number:Energy | Battery charge today (kWh)     |
| `energy#daily-discharge`     | Number:Energy | Battery discharge today (kWh)  |
| `energy#daily-grid-import`   | Number:Energy | Grid import today (kWh)        |
| `energy#daily-grid-export`   | Number:Energy | Grid export today (kWh)        |

### Settings (writable — requires MQTT enabled)

These channels require `enableMqtt=true` on the account bridge. Commands are sent as binary MQTT messages directly to the Solarbank device via the Anker MQTT broker.

| Channel                         | Type                | Description                         | MQTT Message Type |
|---------------------------------|---------------------|-------------------------------------|-------------------|
| `settings#min-soc`              | Number:Dimensionless| Minimum SOC (0–100 %, 5 % steps)   | `0x0067`          |
| `settings#max-load`             | Number:Power        | Maximum output power (W)            | `0x0080`          |
| `settings#ac-socket-switch`     | Switch              | AC socket on/off                    | `0x0073`          |
| `settings#ac-input-limit`       | Number:Power        | AC charging input limit (W)         | `0x0080`          |
| `settings#disable-grid-export`  | Switch              | Disable grid export (ON = no export)| `0x0080`          |
| `settings#pv-limit`             | Number:Power        | PV input limit (W)                  | `0x0080`          |
| `settings#light-switch`         | Switch              | Status light on/off                 | `0x0068`          |

#### Sending MQTT Settings Commands

Commands accept both plain numbers (`50`) and quantity types (`300 W`). Switch channels accept `ON`/`OFF`.

**openHAB console:**

```
openhab> openhab:send Solarbank_Min_SOC 20
openhab> openhab:send Solarbank_Max_Load 800
openhab> openhab:send Solarbank_AC_Socket ON
openhab> openhab:send Solarbank_AC_Input_Limit_MQTT 500
openhab> openhab:send Solarbank_Disable_Grid_Export ON
openhab> openhab:send Solarbank_PV_Limit 1200
openhab> openhab:send Solarbank_Light OFF
```

**Rules (DSL):**

```java
Solarbank_Min_SOC.sendCommand(20)                  // set minimum SOC to 20 %
Solarbank_Max_Load.sendCommand(800)                // set max output load to 800 W
Solarbank_AC_Socket.sendCommand(ON)                // turn AC socket on
Solarbank_AC_Input_Limit_MQTT.sendCommand(500)     // limit AC charging to 500 W
Solarbank_Disable_Grid_Export.sendCommand(ON)      // disable grid export
Solarbank_PV_Limit.sendCommand(1200)               // limit PV input to 1200 W
Solarbank_Light.sendCommand(OFF)                   // turn status light off
```

**Items definition:**

```java
Number          Solarbank_Min_SOC                "Min SOC [%.0f %%]"             { channel="ankersolix:solarbank:myaccount:sb1:settings#min-soc" }
Number:Power    Solarbank_Max_Load               "Max Load [%.0f W]"             { channel="ankersolix:solarbank:myaccount:sb1:settings#max-load" }
Switch          Solarbank_AC_Socket              "AC Socket"                     { channel="ankersolix:solarbank:myaccount:sb1:settings#ac-socket-switch" }
Number:Power    Solarbank_AC_Input_Limit_MQTT    "AC Input Limit [%.0f W]"       { channel="ankersolix:solarbank:myaccount:sb1:settings#ac-input-limit" }
Switch          Solarbank_Disable_Grid_Export    "Disable Grid Export"           { channel="ankersolix:solarbank:myaccount:sb1:settings#disable-grid-export" }
Number:Power    Solarbank_PV_Limit               "PV Limit [%.0f W]"             { channel="ankersolix:solarbank:myaccount:sb1:settings#pv-limit" }
Switch          Solarbank_Light                  "Status Light"                  { channel="ankersolix:solarbank:myaccount:sb1:settings#light-switch" }
```

#### How MQTT Settings Work Internally

Commands are sent as binary-encoded MQTT messages to the device topic `dt/{appName}/{productCode}/{deviceSn}/`. Each message contains a header, command-specific fields, a timestamp, and an XOR checksum.

- **`min-soc`** — sends message type `0x0067` with the SOC percentage (0–100, in 5 % steps)
- **`max-load`** — sends message type `0x0080` with the wattage value
- **`ac-socket-switch`** — sends message type `0x0073` with `1` (ON) or `0` (OFF)
- **`ac-input-limit`** — sends message type `0x0080` with the wattage value
- **`disable-grid-export`** — sends message type `0x0080` with `1` (disable) or `0` (enable)
- **`pv-limit`** — sends message type `0x0080` with the wattage value
- **`light-switch`** — sends message type `0x0068` with `1` (ON) or `0` (OFF)

The MQTT connection also maintains a periodic **realtime trigger** (message type `0x0057`) sent every 120 seconds, which keeps the device streaming high-frequency telemetry data (~5–30 second updates).

**Important:** If MQTT is not connected, commands to the Settings channels are silently dropped with a warning in the log. Ensure the bridge shows ONLINE and MQTT is connected before sending commands.

### REST Control (writable — no MQTT required)

These channels work with REST API polling only and do not require MQTT.

| Channel                          | Type         | Description                                        | API Mechanism                     |
|----------------------------------|--------------|----------------------------------------------------|-----------------------------------|
| `restControl#home-load`          | Number:Power | Home load preset power (W), set via schedule API   | `setHomeLoad` (schedule API)      |
| `restControl#output-limit`       | Number:Power | AC output power limit (W)                          | Device attribute `power_limit`    |
| `restControl#pv-input-limit`     | Number:Power | PV input power limit (W)                           | Device attribute `pv_power_limit` |
| `restControl#ac-input-limit`     | Number:Power | AC input/charging limit (W)                        | Device attribute `ac_power_limit` |
| `restControl#grid-export-switch` | Switch       | Grid export (ON = allowed, OFF = 0 W export)       | Device attribute `switch_0w`      |

#### Sending REST Control Commands

Commands accept both plain numbers (`300`) and quantity types (`300 W`).

**openHAB console:**

```
openhab> openhab:send Solarbank_Output_Limit 300
openhab> openhab:send Solarbank_Home_Load 200
openhab> openhab:send Solarbank_Grid_Export OFF
```

**Rules (DSL):**

```java
Solarbank_Output_Limit.sendCommand(300)       // limit AC output to 300 W
Solarbank_Home_Load.sendCommand(200)           // set home load preset to 200 W
Solarbank_PV_Input_Limit.sendCommand(1200)     // limit PV input to 1200 W
Solarbank_AC_Input_Limit.sendCommand(500)      // limit AC charging to 500 W
Solarbank_Grid_Export.sendCommand(OFF)         // disable grid export (0 W)
Solarbank_Grid_Export.sendCommand(ON)          // enable grid export
```

**Items definition:**

```java
Number:Power  Solarbank_Output_Limit    "Output Limit [%.0f W]"   { channel="ankersolix:solarbank:myaccount:sb1:restControl#output-limit" }
Number:Power  Solarbank_Home_Load       "Home Load [%.0f W]"      { channel="ankersolix:solarbank:myaccount:sb1:restControl#home-load" }
Number:Power  Solarbank_PV_Input_Limit  "PV Limit [%.0f W]"       { channel="ankersolix:solarbank:myaccount:sb1:restControl#pv-input-limit" }
Number:Power  Solarbank_AC_Input_Limit  "AC Limit [%.0f W]"       { channel="ankersolix:solarbank:myaccount:sb1:restControl#ac-input-limit" }
Switch        Solarbank_Grid_Export     "Grid Export"             { channel="ankersolix:solarbank:myaccount:sb1:restControl#grid-export-switch" }
```

#### How REST Controls Work Internally

- **`home-load`** — calls the Anker schedule API to set the default home load output power
- **`output-limit`**, **`pv-input-limit`**, **`ac-input-limit`** — set device attributes (`power_limit`, `pv_power_limit`, `ac_power_limit`) via the Anker cloud API
- **`grid-export-switch`** — sets the `switch_0w` device attribute (the API value is inverted: `0` = export ON, `1` = export OFF; the binding handles this automatically)

All REST control commands go through the Anker cloud REST API and are subject to rate limiting (max 5 requests per 60 seconds).

### Info (read-only)

| Channel                  | Type     | Description             |
|--------------------------|----------|-------------------------|
| `info#firmware-version`  | String   | Current firmware version |
| `info#online-status`     | Switch   | Device online/offline    |
| `info#last-update`       | DateTime | Timestamp of last update |

## REST Polling vs. MQTT

| Feature                     | REST Polling (default)   | MQTT (enableMqtt=true)       |
|-----------------------------|--------------------------|------------------------------|
| Update frequency            | Configurable (min 60 s)  | Near real-time (5–30 s)      |
| Power data accuracy         | 1–5 min averages         | Instantaneous readings       |
| Settings channels           | Not available            | Available (read + write)     |
| REST Control channels       | Available (read + write) | Available (read + write)     |
| Per-string solar data       | Not available            | Available                    |
| Battery temperature         | Not available            | Available                    |
| Grid power                  | Not available            | Available                    |
| Network requirements        | HTTPS to Anker API       | HTTPS + MQTT/TLS to AWS IoT  |

**Recommendation:** Enable MQTT for the best experience. REST-only mode is suitable for basic energy monitoring but lacks real-time data and many channels.

## Signed Power Values

Some power channels report signed values:

- **Battery Power:** positive = charging, negative = discharging
- **AC Output Power:** signed value indicating direction
- **Grid Power:** positive = importing from grid, negative = exporting to grid

## Troubleshooting

### Bridge goes OFFLINE with authentication error

- Verify your email and password are correct
- Make sure the `country` code matches your Anker account region
- Check that the `server` region is correct (`eu` for Europe, `com` for US/Global)
- The Anker API has rate limits — if you see rate-limit errors, increase the polling interval

### Channels show NULL / UNDEF

- After initial setup, wait for at least one full polling cycle (default 120 s)
- Some channels (solar strings, temperature, grid power) are only populated when MQTT is enabled
- Check the openHAB log for error messages: `log:set DEBUG org.openhab.binding.ankersolix`

### MQTT does not connect

- Ensure `enableMqtt=true` is set on the account bridge
- The MQTT connection requires that the REST API login succeeds first (MQTT credentials are fetched via REST)
- Check firewall rules — MQTT connects to an AWS IoT endpoint on port 443 (TLS)

### Commands are not applied

- **Settings channels** require MQTT to be enabled — they will not work in REST-only mode
- **REST Control channels** send commands via the cloud API — allow a few seconds for the device to respond
- Check the log for command confirmation messages

### Rate limiting

The binding enforces a maximum of 5 API requests per 60 seconds. If you operate multiple bindings or tools against the same Anker account simultaneously, you may encounter rate-limit errors. Consider increasing the polling interval or disabling other tools during testing.

## Full Example

### `ankersolix.things`

```java
Bridge ankersolix:account:home "Anker Account" [
    email="user@example.com",
    password="secret",
    country="DE",
    server="eu",
    restPollingInterval=120,
    enableMqtt=true
] {
    Thing solarbank sb1 "Solarbank 3 Pro" [
        siteId="ABC123DEF456",
        deviceSn="SN1234567890"
    ]
}
```

### `ankersolix.items`

```java
// Power overview
Number:Power    SB_PV_Power        "PV Power [%.0f W]"            <energy>  (gSolarbank)  { channel="ankersolix:solarbank:home:sb1:power#photovoltaic-power" }
Number:Power    SB_Output          "Output [%.0f W]"              <energy>  (gSolarbank)  { channel="ankersolix:solarbank:home:sb1:power#output-power" }
Number:Power    SB_Home_Demand     "Home Demand [%.0f W]"         <energy>  (gSolarbank)  { channel="ankersolix:solarbank:home:sb1:power#home-demand" }

// Battery
Number:Power    SB_Bat_Power       "Battery [%.0f W]"             <energy>  (gSolarbank)  { channel="ankersolix:solarbank:home:sb1:battery#battery-power" }
Number          SB_Bat_SOC         "SOC [%.0f %%]"                <battery> (gSolarbank)  { channel="ankersolix:solarbank:home:sb1:battery#battery-soc" }

// Grid
Number:Power    SB_Grid            "Grid [%.0f W]"                <energy>  (gSolarbank)  { channel="ankersolix:solarbank:home:sb1:grid#grid-power" }

// Daily energy
Number:Energy   SB_Daily_Solar     "Today Solar [%.2f kWh]"       <energy>  (gSolarbank)  { channel="ankersolix:solarbank:home:sb1:energy#daily-solar" }
Number:Energy   SB_Daily_Import    "Today Import [%.2f kWh]"      <energy>  (gSolarbank)  { channel="ankersolix:solarbank:home:sb1:energy#daily-grid-import" }
Number:Energy   SB_Daily_Export    "Today Export [%.2f kWh]"      <energy>  (gSolarbank)  { channel="ankersolix:solarbank:home:sb1:energy#daily-grid-export" }

// Controls
Number:Power    SB_Home_Load       "Home Load [%.0f W]"           <energy>  (gSolarbank)  { channel="ankersolix:solarbank:home:sb1:restControl#home-load" }
Switch          SB_Grid_Export     "Grid Export"                  <switch>  (gSolarbank)  { channel="ankersolix:solarbank:home:sb1:restControl#grid-export-switch" }
Number          SB_Min_SOC         "Min SOC [%.0f %%]"            <battery> (gSolarbank)  { channel="ankersolix:solarbank:home:sb1:settings#min-soc" }
```

### `ankersolix.sitemap`

```perl
sitemap ankersolix label="Solarbank" {
    Frame label="Power" {
        Text item=SB_PV_Power
        Text item=SB_Output
        Text item=SB_Home_Demand
        Text item=SB_Grid
    }
    Frame label="Battery" {
        Text item=SB_Bat_Power
        Text item=SB_Bat_SOC
    }
    Frame label="Today" {
        Text item=SB_Daily_Solar
        Text item=SB_Daily_Import
        Text item=SB_Daily_Export
    }
    Frame label="Controls" {
        Setpoint item=SB_Home_Load minValue=0 maxValue=800 step=50
        Switch item=SB_Grid_Export
        Setpoint item=SB_Min_SOC minValue=0 maxValue=100 step=5
    }
}
```
