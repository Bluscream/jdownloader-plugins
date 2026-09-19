package jd.plugins.hoster;

import java.io.IOException;
import java.net.URLDecoder;
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
import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.config.Order;
import org.jdownloader.plugins.config.PluginConfigInterface;
import org.jdownloader.plugins.config.PluginJsonConfig;

@HostPlugin(revision = "$Revision: 1 $", interfaceVersion = 3, names = { "nginxipv6test.b-cdn.net" }, urls = { "https?://(?:www\\.)?nginxipv6test\\.b-cdn\\.net/Items/[a-f0-9]+/Download(?:\\?.*)?" })
public class NginxIpv6TestBCdn extends PluginForHost {

    public NginxIpv6TestBCdn(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "https://nginxipv6test.b-cdn.net/";
    }

    @Override
    public Class<NginxIpv6TestBCdnConfig> getConfigInterface() {
        return NginxIpv6TestBCdnConfig.class;
    }

    private String getEffectiveDownloadUrl(DownloadLink link) {
        String url = link.getPluginPatternMatcher();
        String currentKey = new Regex(url, "api_key=([a-f0-9]+)").getMatch(0);

        if (StringUtils.isEmpty(currentKey)) {
            NginxIpv6TestBCdnConfig cfg = PluginJsonConfig.get(NginxIpv6TestBCdnConfig.class);
            String configKey = cfg.getApiKey();
            if (StringUtils.isNotEmpty(configKey)) {
                if (url.contains("?")) {
                    url = url + "&api_key=" + configKey.trim();
                } else {
                    url = url + "?api_key=" + configKey.trim();
                }
            }
        }
        return url;
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        this.br.setFollowRedirects(true);

        String effectiveUrl = getEffectiveDownloadUrl(link);
        String itemId = new Regex(link.getPluginPatternMatcher(), "Items/([a-f0-9]+)").getMatch(0);
        if (link.getFinalFileName() == null) {
            link.setName("jupiter_" + itemId + ".mp4");
        }

        URLConnectionAdapter con = null;
        try {
            GetRequest get = new GetRequest(effectiveUrl);
            get.getHeaders().put("Range", "bytes=0-0");
            con = this.br.openRequestConnection(get);
            int code = con.getResponseCode();

            if (code == 401 || code == 403) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "Invalid or expired API Key. Please update api_key in Settings -> Plugins -> nginxipv6test.b-cdn.net", PluginException.VALUE_ID_PREMIUM_ONLY);
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

            // Extract verified file size from Content-Range or Content-Length
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
        this.br.setFollowRedirects(true);
        String effectiveUrl = getEffectiveDownloadUrl(link);

        this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, link, effectiveUrl, true, 0);
        int code = this.dl.getConnection().getResponseCode();
        if (code == 401 || code == 403) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "Invalid or expired API Key", PluginException.VALUE_ID_PREMIUM_ONLY);
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

    public static interface NginxIpv6TestBCdnConfig extends PluginConfigInterface {

        @AboutConfig
        @DefaultStringValue(value = "")
        @DescriptionForConfigEntry(value = "Default Jellyfin API Key (used if URL does not include ?api_key=)")
        @Order(10)
        public String getApiKey();

        public void setApiKey(String apiKey);
    }
}
