# Anker Solix API - IntelliJ HTTP Client Files

HTTP client request files for testing and exploring the Anker Solix cloud REST API.
These files are designed for use with the [IntelliJ HTTP Client](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html).

## Files

| File | Description |
|------|-------------|
| `01-auth.http` | Login / authentication |
| `02-sites.http` | Site list, homepage, scene info, energy analysis |
| `03-devices.http` | Device parameters, attributes, power cutoff |
| `04-controls.http` | Write operations: set power limits, grid export, home load, min SOC |
| `05-mqtt.http` | MQTT broker connection info |
| `06-hes.http` | HES service: system running info, device info, energy statistics |
| `http-client.env.json` | Environment variables (EU and US regions) |
| `http-client.private.env.json` | Private credentials (git-ignored) |

## Setup

### 1. Authentication

The Anker API uses ECDH P-256 key exchange to encrypt passwords. This cannot be done directly in HTTP client files.

**Recommended approach — use the binding to capture tokens:**

1. Enable DEBUG logging for the binding:
   ```
   log:set TRACE org.openhab.binding.ankersolix
   ```
2. Configure and initialize the account bridge in openHAB
3. Find the `auth_token` and `user_id` in the log output
4. Compute the `gtoken` (MD5 of user_id):
   ```bash
   echo -n "YOUR_USER_ID" | md5sum
   ```
5. Set `auth_token`, `user_id`, and `gtoken` in `http-client.private.env.json`

### 2. Configure Environment

Edit `http-client.private.env.json` with your real values:

```json
{
  "eu": {
    "auth_token": "your-auth-token-from-logs",
    "user_id": "your-user-id-from-logs",
    "gtoken": "md5-of-user-id",
    "site_id": "your-site-id",
    "device_sn": "your-device-serial"
  }
}
```

> **Note:** Add `http-client.private.env.json` to `.gitignore` — it contains credentials.

### 3. Select Environment

In IntelliJ, select the `eu` or `us` environment in the HTTP client toolbar before running requests.

## Request Execution Order

For first-time use, run the requests in this order:

1. **02-sites.http → Get Site List** — finds your `site_id` (auto-saved)
2. **02-sites.http → Get Site Homepage** — finds your `device_sn` (auto-saved) and shows live power data
3. **03-devices.http** — explore device parameters and attributes
4. **04-controls.http** — modify device settings (use with caution)

## API Rate Limits

The Anker API enforces rate limiting:
- Maximum **5 requests per 60 seconds**
- HTTP 429 response when exceeded
- Wait at least 65 seconds before retrying

## Required Headers

All requests need these headers:

| Header | Value |
|--------|-------|
| `Content-Type` | `application/json` |
| `Model-Type` | `DESKTOP` |
| `App-Name` | `anker_power` |
| `Os-Type` | `android` |
| `Country` | Two-letter code (e.g. `DE`) |
| `Timezone` | GMT offset (e.g. `GMT+01:00`) |

Authenticated requests additionally need:

| Header | Value |
|--------|-------|
| `x-auth-token` | Auth token from login response |
| `gtoken` | MD5 hex digest of user_id |

## API Endpoints Reference

| Endpoint | Description |
|----------|-------------|
| `passport/login` | Authenticate |
| `power_service/v1/site/get_site_list` | List all sites |
| `power_service/v1/site/get_site_homepage` | Site power overview (primary polling) |
| `power_service/v1/site/get_scen_info` | Site scene/layout |
| `power_service/v1/site/get_site_device_param` | Get device params (type: 6/12/18) |
| `power_service/v1/site/set_site_device_param` | Set device params (home load schedule) |
| `power_service/v1/site/energy_analysis` | Energy statistics |
| `power_service/v1/app/get_relate_and_bind_devices` | List bound devices |
| `power_service/v1/app/device/get_device_attrs` | Get device attributes |
| `power_service/v1/app/device/set_device_attrs` | Set device attributes |
| `power_service/v1/app/compatible/get_power_cutoff` | Get min SOC setting |
| `power_service/v1/app/compatible/set_power_cutoff` | Set min SOC setting |
| `app/devicemanage/get_user_mqtt_info` | MQTT broker credentials |
| `charging_hes_svc/get_system_running_info` | HES system status |
| `charging_hes_svc/get_hes_dev_info` | HES device info |
| `charging_hes_svc/get_energy_statistics` | HES energy statistics |
