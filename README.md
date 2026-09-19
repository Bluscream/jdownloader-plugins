# JDownloader Plugins

Custom decrypter and hoster plugins for [JDownloader 2](https://jdownloader.org/).

---

## Available Plugins

### 1. `filmo.to` Decrypter Plugin (`FilmoTo`)

- **Type**: Decrypter / Crawler (`jd.plugins.PluginForDecrypt`)
- **Supported URLs**: `https://filmo.to/movies/<slug>`
- **Source**: [`src/jd/plugins/decrypter/FilmoTo.java`](src/jd/plugins/decrypter/FilmoTo.java)
- **Download**: Available in [Releases](https://github.com/Bluscream/jdownloader-plugins/releases)

#### How it works:
1. Matches `https://filmo.to/movies/<slug>` links added to LinkGrabber.
2. Scrapes the movie metadata, title, and CSRF token.
3. Automatically extracts all encrypted provider tokens (`data-p="..."`).
4. Performs the session-authenticated POST handshake to `/n` to mint stream tokens.
5. Resolves all external hoster mirrors (e.g. **VOE**, **Byse**) and adds them to JDownloader as a named package for immediate downloading.

---

### 2. `cdn.woweepaw.de` Hoster Plugin (`CdnWoweepawDe`)

- **Type**: Hoster Plugin (`jd.plugins.PluginForHost`)
- **Target Worlds**: VRChat **Jellybean** / **Jellybean Movies** (`wrld_275e81ec-c987-40e8-ba07-565a23129e20`)
- **Supported URLs**: `https://cdn.woweepaw.de/pawlib/?action=stream&id=<hex>`
- **Source**: [`src/jd/plugins/hoster/CdnWoweepawDe.java`](src/jd/plugins/hoster/CdnWoweepawDe.java)
- **Settings UI**:
  - `User-Agent`: Configurable in **Settings → Plugins → cdn.woweepaw.de** (defaults to `VRChat` to bypass the `204 No Content` filter without manual header tweaks).
- **Features**:
  - Automatically queries media info with byte-range requests.
  - Resolves real media filenames from `Content-Disposition` (e.g. `"Star Wars - Die Rache der Sith (2005).mp4"`).
  - Retrieves exact file size from `Content-Range`.

---

### 3. `nginxipv6test.b-cdn.net` Hoster Plugin (`NginxIpv6TestBCdn`)

- **Type**: Hoster Plugin (`jd.plugins.PluginForHost`)
- **Target Worlds**: VRChat **Jupiters Sleep World Beta** (`wrld_3315f74d-496a-4c43-a888-c9987a07fc58`)
- **Supported URLs**: `https://nginxipv6test.b-cdn.net/Items/<itemId>/Download` (with or without `?api_key=...`)
- **Source**: [`src/jd/plugins/hoster/NginxIpv6TestBCdn.java`](src/jd/plugins/hoster/NginxIpv6TestBCdn.java)
- **Settings UI**:
  - `API Key`: Input field in **Settings → Plugins → nginxipv6test.b-cdn.net** for setting the rotating Jellyfin authentication token.
- **Features**:
  - If a link is added without an API key, the plugin automatically falls back to the configured token from settings.
  - Automatically resolves real filenames and sizes from stream headers.
  - Gives clear user guidance if the token expires (401/403).

---

## Installation

1. Download the precompiled classes or `.zip` from the [Latest Release](https://github.com/Bluscream/jdownloader-plugins/releases/latest).
2. Copy the `.class` files into your JDownloader installation's plugin directory:
   - Decrypters go to `<jdownloader_dir>/jd/plugins/decrypter/`
   - Hosters go to `<jdownloader_dir>/jd/plugins/hoster/`
   *(For Docker / Unraid installations, this is typically `/mnt/user/appdata/jdownloader/jd/plugins/`)*.
3. Restart JDownloader to load the new plugins.

---

## Building from Source

Requirements: Java 8+ JDK (compiled with `--release 8` for Java 1.8 runtime compatibility).

```bash
# Run the build script pointing to your JDownloader installation:
./scripts/build.sh /path/to/jdownloader
```
