package jd.plugins.hoster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.http.requests.GetRequest;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DefaultStringValue;
import org.appwork.storage.config.annotations.DescriptionForConfigEntry;
import org.appwork.storage.config.annotations.MultiLineString;
import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.config.Order;
import org.jdownloader.plugins.config.PluginConfigInterface;
import org.jdownloader.plugins.config.PluginJsonConfig;

import jd.gui.swing.jdgui.views.settings.components.StateUpdateListener;
import jd.gui.swing.jdgui.views.settings.components.TextArea;
import jd.plugins.Plugin;
import jd.plugins.PluginConfigPanelNG;
import org.appwork.storage.config.handler.BooleanKeyHandler;
import org.jdownloader.plugins.config.CustomUI;

@HostPlugin(revision = "$Revision: 3 $", interfaceVersion = 3, names = { "jellyfin" }, urls = { "https?://[^/]+/Items/[a-f0-9]+/Download(?:\\?.*)?" })
public class JellyfinDirectDownload extends PluginForHost {

    private static final String DEFAULT_USER_AGENT = "VRChat";

    public JellyfinDirectDownload(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "https://jellyfin.org/";
    }

    @Override
    public Class<JellyfinConfig> getConfigInterface() {
        return JellyfinConfig.class;
    }

    @Override
    public PluginConfigPanelNG createConfigPanel() {
        return new PluginConfigPanelNG() {
            private TextArea txtRules;

            @Override
            public void reset() {
                super.reset();
                updateContents();
            }

            @Override
            public void save() {
            }

            @Override
            public void updateContents() {
                if (txtRules != null) {
                    JellyfinConfig cfg = PluginJsonConfig.get(getLazyP(), JellyfinConfig.class);
                    txtRules.setText(cfg.getPerDomainRules());
                }
            }

            @Override
            protected void initPluginSettings(Plugin plugin) {
                super.initPluginSettings(plugin);
                final JellyfinConfig cfg = PluginJsonConfig.get(getLazyP(), JellyfinConfig.class);
                txtRules = new TextArea();
                txtRules.setText(cfg.getPerDomainRules());
                txtRules.addStateUpdateListener(new StateUpdateListener() {
                    @Override
                    public void onStateUpdated() {
                        cfg.setPerDomainRules(txtRules.getText());
                    }
                });
                addPair("Per-Domain Rules: domain|apiKey|userAgent (one per line)", (BooleanKeyHandler) null, txtRules);
            }
        };
    }

    private static class DomainRule {
        String apiKey;
        String userAgent;
    }

    private Map<String, DomainRule> parseDomainRules(String rulesText) {
        Map<String, DomainRule> rules = new HashMap<String, DomainRule>();
        if (StringUtils.isEmpty(rulesText)) {
            return rules;
        }

        try {
            BufferedReader reader = new BufferedReader(new StringReader(rulesText));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // Format: domain|apiKey|userAgent
                // userAgent or apiKey can be empty
                String[] parts = line.split("\\|", -1);
                if (parts.length >= 1) {
                    String domain = parts[0].trim().toLowerCase();
                    if (!domain.isEmpty()) {
                        DomainRule rule = new DomainRule();
                        if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
                            rule.apiKey = parts[1].trim();
                        }
                        if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
                            rule.userAgent = parts[2].trim();
                        }
                        rules.put(domain, rule);
                    }
                }
            }
        } catch (IOException e) {
            // Ignore in-memory read exceptions
        }
        return rules;
    }

    private String getHostFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host != null) {
                return host.toLowerCase();
            }
        } catch (Exception e) {
            // fallback
        }
        String match = new Regex(url, "https?://([^/:]+)").getMatch(0);
        return match != null ? match.toLowerCase() : "";
    }

    private DomainRule resolveRule(String url) {
        JellyfinConfig cfg = PluginJsonConfig.get(JellyfinConfig.class);
        Map<String, DomainRule> domainRules = parseDomainRules(cfg.getPerDomainRules());
        String host = getHostFromUrl(url);

        DomainRule matched = domainRules.get(host);
        DomainRule effective = new DomainRule();

        if (matched != null) {
            effective.apiKey = matched.apiKey;
            effective.userAgent = matched.userAgent;
        }

        // Fallback to global defaults if not defined per-domain
        if (StringUtils.isEmpty(effective.apiKey)) {
            effective.apiKey = cfg.getDefaultApiKey();
        }
        if (StringUtils.isEmpty(effective.userAgent)) {
            effective.userAgent = cfg.getDefaultUserAgent();
        }
        if (StringUtils.isEmpty(effective.userAgent)) {
            effective.userAgent = DEFAULT_USER_AGENT;
        }

        return effective;
    }

    private String getEffectiveDownloadUrl(DownloadLink link) {
        String url = link.getPluginPatternMatcher();
        String currentKey = new Regex(url, "api_key=([a-f0-9]+)").getMatch(0);

        if (StringUtils.isEmpty(currentKey)) {
            DomainRule rule = resolveRule(url);
            if (StringUtils.isNotEmpty(rule.apiKey)) {
                if (url.contains("?")) {
                    url = url + "&api_key=" + rule.apiKey.trim();
                } else {
                    url = url + "?api_key=" + rule.apiKey.trim();
                }
            }
        }
        return url;
    }

    private void prepareBrowser(Browser browser, String url) {
        browser.setFollowRedirects(true);
        DomainRule rule = resolveRule(url);
        browser.getHeaders().put("User-Agent", rule.userAgent);
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        String effectiveUrl = getEffectiveDownloadUrl(link);
        prepareBrowser(this.br, effectiveUrl);

        String host = getHostFromUrl(link.getPluginPatternMatcher());
        String itemId = new Regex(link.getPluginPatternMatcher(), "Items/([a-f0-9]+)").getMatch(0);
        if (link.getFinalFileName() == null) {
            link.setName("jellyfin_" + host + "_" + itemId + ".mp4");
        }

        URLConnectionAdapter con = null;
        try {
            GetRequest get = new GetRequest(effectiveUrl);
            get.getHeaders().put("Range", "bytes=0-0");
            con = this.br.openRequestConnection(get);
            int code = con.getResponseCode();

            if (code == 401 || code == 403) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "Invalid or missing Jellyfin API Key for " + host + ". Set it in Settings -> Plugins -> jellyfin", PluginException.VALUE_ID_PREMIUM_ONLY);
            }
            if (code == 404 || code == 410) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (code != 200 && code != 206) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "HTTP Error " + code, 5 * 60 * 1000l);
            }

            // Extract filename from Content-Disposition header
            String disposition = con.getHeaderField("Content-Disposition");
            if (disposition != null) {
                String filename = null;
                Matcher utf8Matcher = Pattern.compile("filename\\*=UTF-8''([^;\\r\\n]+)").matcher(disposition);
                if (utf8Matcher.find()) {
                    filename = URLDecoder.decode(utf8Matcher.group(1), "UTF-8");
                } else {
                    Matcher stdMatcher = Pattern.compile("filename=\"?([^\";\\r\\n]+)\"?").matcher(disposition);
                    if (stdMatcher.find()) {
                        filename = stdMatcher.group(1);
                    }
                }
                if (filename != null && !filename.trim().isEmpty()) {
                    link.setFinalFileName(filename.trim());
                }
            }

            // Extract file size
            String contentRange = con.getHeaderField("Content-Range");
            if (contentRange != null) {
                String totalSize = new Regex(contentRange, "/(\\d+)").getMatch(0);
                if (totalSize != null) {
                    link.setVerifiedFileSize(Long.parseLong(totalSize));
                }
            } else if (con.getCompleteContentLength() > 0) {
                link.setVerifiedFileSize(con.getCompleteContentLength());
            }

            return AvailableStatus.TRUE;
        } catch (IOException e) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Network error: " + e.getMessage(), 3 * 60 * 1000l);
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    @Override
    public void handleFree(DownloadLink link) throws Exception {
        requestFileInformation(link);

        this.setBrowserExclusive();
        String effectiveUrl = getEffectiveDownloadUrl(link);
        prepareBrowser(this.br, effectiveUrl);

        this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, link, effectiveUrl, true, 0);
        int code = this.dl.getConnection().getResponseCode();
        if (code == 401 || code == 403) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "Invalid or missing Jellyfin API Key", PluginException.VALUE_ID_PREMIUM_ONLY);
        }
        if (code == 404 || code == 410) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (code != 200 && code != 206) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "HTTP Error " + code, 5 * 60 * 1000l);
        }

        this.dl.startDownload();
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    public static interface JellyfinConfig extends PluginConfigInterface {

        @AboutConfig
        @DefaultStringValue(value = "")
        @DescriptionForConfigEntry(value = "Default Jellyfin API Key (applied if no domain-specific key matches and URL has no ?api_key=)")
        @Order(10)
        public String getDefaultApiKey();

        public void setDefaultApiKey(String apiKey);

        @AboutConfig
        @DefaultStringValue(value = "VRChat")
        @DescriptionForConfigEntry(value = "Default User-Agent header (default: VRChat)")
        @Order(20)
        public String getDefaultUserAgent();

        public void setDefaultUserAgent(String userAgent);

        @AboutConfig
        @CustomUI
        @MultiLineString
        @DefaultStringValue(value = "# Per-domain/host rules (one per line)\n# Format: domain|apiKey|userAgent\nnginxipv6test.b-cdn.net|20121df9784646bb850a06edf402e3a0|VRChat\ncdn.clawsucht.eu||VRChat\n")
        @DescriptionForConfigEntry(value = "Per-Domain Rules: domain|apiKey|userAgent (one per line)")
        @Order(30)
        public String getPerDomainRules();

        public void setPerDomainRules(String rules);
    }
}
