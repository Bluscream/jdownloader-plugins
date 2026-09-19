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

@HostPlugin(revision = "$Revision: 1 $", interfaceVersion = 3, names = { "cdn.woweepaw.de" }, urls = { "https?://(?:www\\.)?cdn\\.woweepaw\\.de/pawlib/\\?action=stream&id=[a-f0-9]+" })
public class CdnWoweepawDe extends PluginForHost {

    private static final String DEFAULT_USER_AGENT = "VRChat";

    public CdnWoweepawDe(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "https://cdn.woweepaw.de/";
    }

    @Override
    public Class<CdnWoweepawDeConfig> getConfigInterface() {
        return CdnWoweepawDeConfig.class;
    }

    private void prepareBrowser(Browser browser) {
        browser.setFollowRedirects(true);
        CdnWoweepawDeConfig cfg = PluginJsonConfig.get(CdnWoweepawDeConfig.class);
        String customUa = cfg.getCustomUserAgent();
        if (StringUtils.isNotEmpty(customUa)) {
            browser.getHeaders().put("User-Agent", customUa.trim());
        } else {
            browser.getHeaders().put("User-Agent", DEFAULT_USER_AGENT);
        }
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        prepareBrowser(this.br);

        String streamId = new Regex(link.getPluginPatternMatcher(), "id=([a-f0-9]+)").getMatch(0);
        if (link.getFinalFileName() == null) {
            link.setName("woweepaw_" + streamId + ".mp4");
        }

        URLConnectionAdapter con = null;
        try {
            GetRequest get = new GetRequest(link.getPluginPatternMatcher());
            get.getHeaders().put("Range", "bytes=0-0");
            con = this.br.openRequestConnection(get);
            int code = con.getResponseCode();

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

            // Extract verified file size from Content-Range (e.g. bytes 0-0/2265147889) or Content-Length
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
        prepareBrowser(this.br);

        this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, link, link.getPluginPatternMatcher(), true, 0);
        int code = this.dl.getConnection().getResponseCode();
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

    public static interface CdnWoweepawDeConfig extends PluginConfigInterface {

        @AboutConfig
        @DefaultStringValue(value = "VRChat")
        @DescriptionForConfigEntry(value = "User-Agent to send when requesting streams (default: VRChat)")
        @Order(10)
        public String getCustomUserAgent();

        public void setCustomUserAgent(String userAgent);
    }
}
