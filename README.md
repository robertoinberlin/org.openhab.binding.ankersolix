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

### Item Reference

The following table lists all available items with their types, data sources, and descriptions.
Channel IDs use the format `ankersolix:solarbank:<bridge>:<thing>:<channel>`.

| Item Name | Item Type | Channel | Source | R/W | Description                                                   |
|-----------|-----------|---------|--------|-----|---------------------------------------------------------------|
| **Power** | | | | |                                                               |
| `SB_PV_Power` | Number:Power | `power#photovoltaic-power` | MQTT | R | Total photovoltaic generation in watts                        |
| `SB_Output` | Number:Power | `power#output-power` | MQTT | R | Current output power to home in watts                         |
| `SB_AC_Output` | Number:Power | `power#ac-output-power` | MQTT | R | AC output power (signed) in watts                             |
| `SB_Home_Demand` | Number:Power | `power#home-demand` | MQTT | R | Total home power demand in watts                              |
| **Solar Strings** | | | | |                                                               |
| `SB_PV1` | Number:Power | `solar#pv1-power` | MQTT | R | MPPT string 1 power in watts (solar input #1)                 |
| `SB_PV2` | Number:Power | `solar#pv2-power` | MQTT | R | MPPT string 2 power in watts (solar input #2)                 |
| `SB_PV3` | Number:Power | `solar#pv3-power` | MQTT | R | MPPT string 3 power in watts (solar input #3)                 |
| `SB_PV4` | Number:Power | `solar#pv4-power` | MQTT | R | MPPT string 4 power in watts (solar input #4)                 |
| **Battery** | | | | |                                                               |
| `SB_Bat_Power` | Number:Power | `battery#battery-power` | MQTT | R | Battery charge/discharge power (+charge, -discharge) in watts |
| `SB_Bat_SOC` | Number:Dimensionless | `battery#battery-soc` | MQTT | R | Battery state of charge (0–100 %)                             |
| `SB_Temperature` | Number:Temperature | `battery#temperature` | MQTT | R | Battery temperature in °C                                     |
| **Grid** | | | | |                                                               |
| `SB_Grid` | Number:Power | `grid#grid-power` | MQTT | R | Grid power (+import, -export) in watts                        |
| **Energy** | | | | |                                                               |
| `SB_PV_Yield` | Number:Energy | `energy#pv-yield` | MQTT | R | Total lifetime PV energy yield in kWh                         |
| `SB_Charged` | Number:Energy | `energy#charged-energy` | MQTT | R | Total lifetime energy charged to battery in kWh               |
| `SB_Discharged` | Number:Energy | `energy#discharged-energy` | MQTT | R | Total lifetime energy discharged from battery in kWh          |
| `SB_Daily_Solar` | Number:Energy | `energy#daily-solar` | REST | R | Solar generation today in kWh                                 |
| `SB_Daily_Charge` | Number:Energy | `energy#daily-charge` | REST | R | Battery charge today in kWh                                   |
| `SB_Daily_Discharge` | Number:Energy | `energy#daily-discharge` | REST | R | Battery discharge today in kWh                                |
| `SB_Daily_Import` | Number:Energy | `energy#daily-grid-import` | REST | R | Grid import today in kWh                                      |
| `SB_Daily_Export` | Number:Energy | `energy#daily-grid-export` | REST | R | Grid export today in kWh                                      |
| **Settings (MQTT)** | | | | |                                                               |
| `SB_Min_SOC` | Number:Dimensionless | `settings#min-soc` | MQTT+REST | R/W | Minimum battery SOC (0–100 %, 5 % steps)                      |
| `SB_Max_Load` | Number:Power | `settings#max-load` | MQTT | R/W | Maximum output power in watts                                 |
| `SB_AC_Socket` | Switch | `settings#ac-socket-switch` | MQTT | R/W | AC socket on/off                                              |
| `SB_AC_Limit_MQTT` | Number:Power | `settings#ac-input-limit` | MQTT | R/W | AC charging input limit in watts                              |
| `SB_Disable_Export` | Switch | `settings#disable-grid-export` | MQTT | R/W | Disable grid export (ON = no export)                          |
| `SB_PV_Limit` | Number:Power | `settings#pv-limit` | MQTT | R/W | PV input limit in watts                                       |
| `SB_Light` | Switch | `settings#light-switch` | MQTT | R/W | Status LED light on/off                                       |
| **REST Control** | | | | |                                                               |
| `SB_Home_Load` | Number:Power | `restControl#home-load` | REST | R/W | Home load preset power in watts (via schedule)                |
| `SB_Output_Limit` | Number:Power | `restControl#output-limit` | REST | R/W | AC output power limit in watts                                |
| `SB_PV_Input_Limit` | Number:Power | `restControl#pv-input-limit` | REST | R/W | PV input power limit in watts                                 |
| `SB_AC_Input_Limit` | Number:Power | `restControl#ac-input-limit` | REST | R/W | AC input/charging limit in watts                              |
| `SB_Grid_Export` | Switch | `restControl#grid-export-switch` | REST | R/W | Grid export (ON = allowed, OFF = 0 W)                         |
| **Info** | | | | |                                                               |
| `SB_Firmware` | String | `info#firmware-version` | REST | R | Current firmware version string                                |
| `SB_Online` | Switch | `info#online-status` | REST | R | Device online/offline status                                   |
| `SB_Last_Update` | DateTime | `info#last-update` | Both | R | Timestamp of last data update                                  |

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
openhab> openhab:send SB_Min_SOC 20
openhab> openhab:send SB_Max_Load 800
openhab> openhab:send SB_AC_Socket ON
openhab> openhab:send SB_AC_Limit_MQTT 500
openhab> openhab:send SB_Disable_Export ON
openhab> openhab:send SB_PV_Limit 1200
openhab> openhab:send SB_Light OFF
```

**Rules (DSL):**

```java
SB_Min_SOC.sendCommand(20)                  // set minimum SOC to 20 %
SB_Max_Load.sendCommand(800)                // set max output load to 800 W
SB_AC_Socket.sendCommand(ON)                // turn AC socket on
SB_AC_Limit_MQTT.sendCommand(500)           // limit AC charging to 500 W
SB_Disable_Export.sendCommand(ON)           // disable grid export
SB_PV_Limit.sendCommand(1200)              // limit PV input to 1200 W
SB_Light.sendCommand(OFF)                  // turn status light off
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
openhab> openhab:send SB_Output_Limit 300
openhab> openhab:send SB_Home_Load 200
openhab> openhab:send SB_Grid_Export OFF
```

**Rules (DSL):**

```java
SB_Output_Limit.sendCommand(300)       // limit AC output to 300 W
SB_Home_Load.sendCommand(200)          // set home load preset to 200 W
SB_PV_Input_Limit.sendCommand(1200)    // limit PV input to 1200 W
SB_AC_Input_Limit.sendCommand(500)     // limit AC charging to 500 W
SB_Grid_Export.sendCommand(OFF)        // disable grid export (0 W)
SB_Grid_Export.sendCommand(ON)         // enable grid export
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

### SiteID Configuration

- install the add-on, and connect it to Anker
- check the logs for DEBUG output related to the site ID 

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

## Logging

The binding uses the logger name `org.openhab.binding.ankersolix`. You can adjust the log level to get more or less detail.

### Via the openHAB UI (recommended)

1. Go to **Settings > Add-on Settings > Anker Solix Binding**
2. Change the **Log Level** to `DEBUG` or `TRACE`
3. The change takes effect immediately — no restart needed

### Via the Karaf console

```
log:set DEBUG org.openhab.binding.ankersolix
log:tail org.openhab.binding.ankersolix
```

To reset back to normal:

```
log:set INFO org.openhab.binding.ankersolix
```

### Via `log4j2.xml`

Add the following to your `userdata/etc/log4j2.xml` inside the `<Loggers>` section for a persistent configuration:

```xml
<Logger name="org.openhab.binding.ankersolix" level="DEBUG"/>
```

### Log levels

| Level   | What you see                                                                 |
|---------|------------------------------------------------------------------------------|
| `INFO`  | Authentication success, site/device discovery, MQTT connection status        |
| `DEBUG` | API call results, MQTT subscriptions, command confirmations, polling details |
| `TRACE` | Full API response bodies, raw MQTT payloads, binary data decoding            |

Start with `DEBUG` when investigating issues. Only use `TRACE` if you need to inspect raw API or MQTT data — it produces a large amount of output.

## Full Example

### `ankersolix.things`

```java
Bridge ankersolix:account:myaccount "Anker Account" [
    email="user@example.com",
    password="secret",
    country="DE",
    server="eu",
    restPollingInterval=120,
    enableMqtt=true
] {
    Thing solarbank mysolarbank "Solarbank 3 Pro" [
        siteId="ABC123DEF456",
        deviceSn="SN1234567890"
    ]
}
```

### `ankersolix.items`

```java
Group gSolarbank "Solarbank 3 Pro" <energy>

// Power overview (MQTT real-time)
Number:Power       SB_PV_Power        "PV Power [%.0f W]"            <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:power#photovoltaic-power" }
Number:Power       SB_Output          "Output [%.0f W]"              <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:power#output-power" }
Number:Power       SB_AC_Output       "AC Output [%.0f W]"           <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:power#ac-output-power" }
Number:Power       SB_Home_Demand     "Home Demand [%.0f W]"         <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:power#home-demand" }

// Solar strings (MQTT real-time)
Number:Power       SB_PV1             "PV String 1 [%.0f W]"         <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:solar#pv1-power" }
Number:Power       SB_PV2             "PV String 2 [%.0f W]"         <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:solar#pv2-power" }
Number:Power       SB_PV3             "PV String 3 [%.0f W]"         <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:solar#pv3-power" }
Number:Power       SB_PV4             "PV String 4 [%.0f W]"         <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:solar#pv4-power" }

// Battery (MQTT real-time)
Number:Power       SB_Bat_Power       "Battery [%.0f W]"             <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:battery#battery-power" }
Number             SB_Bat_SOC         "SOC [%.0f %%]"                <battery> (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:battery#battery-soc" }
Number:Temperature SB_Temperature     "Temperature [%.1f °C]"        <temperature> (gSolarbank) { channel="ankersolix:solarbank:myaccount:mysolarbank:battery#temperature" }

// Grid (MQTT real-time)
Number:Power       SB_Grid            "Grid [%.0f W]"                <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:grid#grid-power" }

// Energy - lifetime totals (MQTT)
Number:Energy      SB_PV_Yield        "PV Yield Total [%.2f kWh]"    <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#pv-yield" }
Number:Energy      SB_Charged         "Charged Total [%.2f kWh]"     <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#charged-energy" }
Number:Energy      SB_Discharged      "Discharged Total [%.2f kWh]"  <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#discharged-energy" }

// Energy - daily counters (REST)
Number:Energy      SB_Daily_Solar     "Today Solar [%.2f kWh]"       <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#daily-solar" }
Number:Energy      SB_Daily_Charge    "Today Charge [%.2f kWh]"      <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#daily-charge" }
Number:Energy      SB_Daily_Discharge "Today Discharge [%.2f kWh]"   <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#daily-discharge" }
Number:Energy      SB_Daily_Import    "Today Import [%.2f kWh]"      <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#daily-grid-import" }
Number:Energy      SB_Daily_Export    "Today Export [%.2f kWh]"      <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:energy#daily-grid-export" }

// MQTT settings (require enableMqtt=true)
Number             SB_Min_SOC         "Min SOC [%.0f %%]"            <battery> (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:settings#min-soc" }
Number:Power       SB_Max_Load        "Max Load [%.0f W]"            <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:settings#max-load" }
Switch             SB_AC_Socket       "AC Socket"                    <switch>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:settings#ac-socket-switch" }
Number:Power       SB_AC_Limit_MQTT   "AC Input Limit [%.0f W]"      <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:settings#ac-input-limit" }
Switch             SB_Disable_Export  "Disable Grid Export"          <switch>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:settings#disable-grid-export" }
Number:Power       SB_PV_Limit        "PV Limit [%.0f W]"            <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:settings#pv-limit" }
Switch             SB_Light           "Status Light"                 <light>   (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:settings#light-switch" }

// REST controls (work without MQTT)
Number:Power       SB_Home_Load       "Home Load [%.0f W]"           <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:restControl#home-load" }
Number:Power       SB_Output_Limit    "Output Limit [%.0f W]"        <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:restControl#output-limit" }
Number:Power       SB_PV_Input_Limit  "PV Input Limit [%.0f W]"      <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:restControl#pv-input-limit" }
Number:Power       SB_AC_Input_Limit  "AC Input Limit [%.0f W]"      <energy>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:restControl#ac-input-limit" }
Switch             SB_Grid_Export     "Grid Export"                  <switch>  (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:restControl#grid-export-switch" }

// Device info
String             SB_Firmware        "Firmware [%s]"                <text>    (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:info#firmware-version" }
Switch             SB_Online          "Online"                       <network> (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:info#online-status" }
DateTime           SB_Last_Update     "Last Update [%1$tF %1$tR]"    <time>    (gSolarbank)  { channel="ankersolix:solarbank:myaccount:mysolarbank:info#last-update" }
```

### `ankersolix.sitemap`

```perl
sitemap ankersolix label="Solarbank 3 Pro" {
    Frame label="Power Overview" {
        Text item=SB_PV_Power        icon="energy"
        Text item=SB_Output          icon="energy"
        Text item=SB_AC_Output       icon="energy"
        Text item=SB_Home_Demand     icon="energy"
        Text item=SB_Grid            icon="energy"
    }
    Frame label="Solar Strings" {
        Text item=SB_PV1             icon="energy"
        Text item=SB_PV2             icon="energy"
        Text item=SB_PV3             icon="energy"
        Text item=SB_PV4             icon="energy"
    }
    Frame label="Battery" {
        Text item=SB_Bat_Power       icon="energy"
        Text item=SB_Bat_SOC         icon="battery"
        Text item=SB_Temperature     icon="temperature"
    }
    Frame label="Energy Today" {
        Text item=SB_Daily_Solar     icon="energy"
        Text item=SB_Daily_Charge    icon="energy"
        Text item=SB_Daily_Discharge icon="energy"
        Text item=SB_Daily_Import    icon="energy"
        Text item=SB_Daily_Export    icon="energy"
    }
    Frame label="Energy Lifetime" {
        Text item=SB_PV_Yield        icon="energy"
        Text item=SB_Charged         icon="energy"
        Text item=SB_Discharged      icon="energy"
    }
    Frame label="REST Controls" {
        Setpoint item=SB_Home_Load      minValue=0  maxValue=800  step=50
        Setpoint item=SB_Output_Limit   minValue=0  maxValue=800  step=50
        Setpoint item=SB_PV_Input_Limit minValue=0  maxValue=3600 step=100
        Setpoint item=SB_AC_Input_Limit minValue=0  maxValue=2400 step=100
        Switch   item=SB_Grid_Export
    }
    Frame label="MQTT Settings" visibility=[SB_Online==ON] {
        Setpoint item=SB_Min_SOC        minValue=0  maxValue=100  step=5
        Setpoint item=SB_Max_Load       minValue=0  maxValue=800  step=50
        Setpoint item=SB_PV_Limit       minValue=0  maxValue=3600 step=100
        Setpoint item=SB_AC_Limit_MQTT  minValue=0  maxValue=2400 step=100
        Switch   item=SB_AC_Socket
        Switch   item=SB_Disable_Export
        Switch   item=SB_Light
    }
    Frame label="Device Info" {
        Text   item=SB_Firmware      icon="text"
        Text   item=SB_Online        icon="network"
        Text   item=SB_Last_Update   icon="time"
    }
}
```

## Ideas for Rules

Below are some rule ideas to get the most out of your Solarbank.
Adapt thresholds and values to your setup.

### Adjust home load to match solar production

Automatically raise or lower the home load output so the Solarbank feeds exactly what the panels produce, avoiding grid export or unnecessary battery drain.

```java
rule "Match home load to PV"
when
    Item SB_PV_Power changed
then
    val pv = (SB_PV_Power.state as QuantityType<Power>).intValue
    // Round down to nearest 50 W, clamp 0–800
    val target = Math.min(800, Math.max(0, (pv / 50) * 50))
    if (target != (SB_Home_Load.state as QuantityType<Power>).intValue) {
        SB_Home_Load.sendCommand(target)
    }
end
```

### Protect battery at low SOC

When the battery drops below 20 %, reduce the output to a minimum to preserve the remaining charge. Restore full output once the battery recovers above 40 %.

```java
rule "Low battery protection"
when
    Item SB_Bat_SOC changed
then
    val soc = (SB_Bat_SOC.state as Number).intValue
    if (soc < 20) {
        SB_Home_Load.sendCommand(100)
        logInfo("solarbank", "Low SOC ({}%), reduced home load to 100 W", soc)
    } else if (soc > 40 && (SB_Home_Load.state as QuantityType<Power>).intValue < 200) {
        SB_Home_Load.sendCommand(400)
        logInfo("solarbank", "SOC recovered ({}%), restored home load to 400 W", soc)
    }
end
```

### Disable grid export at night

Turn off grid export after sunset and re-enable it at sunrise to keep stored energy for household use overnight.
Requires the [Astro binding](https://www.openhab.org/addons/bindings/astro/) for `Astro_Sun_Rise_Start` and `Astro_Sun_Set_Start` items.

```java
rule "Disable export at sunset"
when
    Channel "astro:sun:local:set#event" triggered START
then
    SB_Grid_Export.sendCommand(OFF)
end

rule "Enable export at sunrise"
when
    Channel "astro:sun:local:rise#event" triggered START
then
    SB_Grid_Export.sendCommand(ON)
end
```

### Battery temperature alert

Send a notification when the battery temperature exceeds 45 °C.
Uses the openHAB [notification action](https://www.openhab.org/docs/configuration/actions.html).

```java
rule "Battery overheat warning"
when
    Item SB_Temperature changed
then
    val temp = (SB_Temperature.state as QuantityType<Temperature>).doubleValue
    if (temp > 45.0) {
        sendNotification("you@example.com",
            String::format("Solarbank battery temperature is %.1f °C — check ventilation!", temp))
    }
end
```

### Charge from grid during cheap tariff hours

If you have a dynamic electricity tariff, enable AC charging at full power during cheap hours and disable it otherwise.

```java
rule "Cheap tariff AC charging"
when
    Time cron "0 0 2 * * ?"   // 02:00 — start of cheap tariff
then
    SB_AC_Input_Limit.sendCommand(2400)
    logInfo("solarbank", "Cheap tariff started, AC charging at 2400 W")
end

rule "End cheap tariff AC charging"
when
    Time cron "0 0 6 * * ?"   // 06:00 — end of cheap tariff
then
    SB_AC_Input_Limit.sendCommand(0)
    logInfo("solarbank", "Cheap tariff ended, AC charging disabled")
end
```

### Turn off status light at night

Disable the LED at bedtime and turn it back on in the morning.

```java
rule "Light off at night"
when
    Time cron "0 0 22 * * ?"
then
    SB_Light.sendCommand(OFF)
end

rule "Light on in the morning"
when
    Time cron "0 0 7 * * ?"
then
    SB_Light.sendCommand(ON)
end
```

### Log daily energy summary at midnight

Write a summary of the day's energy production and consumption to the openHAB log just before the daily counters reset.

```java
rule "Daily energy summary"
when
    Time cron "0 59 23 * * ?"
then
    logInfo("solarbank", "Daily summary — Solar: {} kWh, Charge: {} kWh, Discharge: {} kWh, Import: {} kWh, Export: {} kWh",
        SB_Daily_Solar.state, SB_Daily_Charge.state, SB_Daily_Discharge.state,
        SB_Daily_Import.state, SB_Daily_Export.state)
end
```
