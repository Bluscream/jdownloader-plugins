package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Pattern;
import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.components.PluginJSonUtils;

@DecrypterPlugin(revision = "$Revision: 1 $", interfaceVersion = 3, names = {}, urls = {})
public class FilmoTo extends PluginForDecrypt {

    private static final Pattern TYPE_MOVIE = Pattern.compile("/movies/([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);

    public FilmoTo(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public Browser createNewBrowserInstance() {
        Browser br = super.createNewBrowserInstance();
        br.setFollowRedirects(true);
        return br;
    }

    public static String[] getAnnotationNames() {
        return new String[] { "filmo.to" };
    }

    @Override
    public String[] siteSupportedNames() {
        return new String[] { "filmo.to" };
    }

    public static String[] getAnnotationUrls() {
        return new String[] { "https?://(?:www\\.)?filmo\\.to/movies/[a-zA-Z0-9_-]+" };
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        String contenturl = param.getCryptedUrl();
        this.br.getPage(contenturl);
        if (this.br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(32);
        }

        // Title extraction
        String title = this.br.getRegex("<h1[^>]*>([^<]+)</h1>").getMatch(0);
        if (title == null) {
            title = this.br.getRegex("<title>(.*?) (?:jetzt )?kostenlos streamen").getMatch(0);
        }
        if (title == null) {
            title = new Regex(contenturl, TYPE_MOVIE).getMatch(0).replace("-", " ");
        }
        title = Encoding.htmlDecode(title.trim());

        FilePackage fp = FilePackage.getInstance();
        fp.setName(title);

        // CSRF Token
        String csrfToken = this.br.getRegex("csrf-token[\"']\\s*content=[\"']([^\"']+)[\"']").getMatch(0);

        // Extract encrypted provider tokens
        String[] pVals = this.br.getRegex("data-p=[\"']([^\"']+)[\"']").getColumn(0);
        if (pVals == null || pVals.length == 0) {
            throw new PluginException(32);
        }

        HashSet<String> dupes = new HashSet<String>();
        for (String pVal : pVals) {
            if (this.isAbort()) {
                break;
            }
            if (pVal == null || pVal.isEmpty()) {
                continue;
            }

            try {
                Browser br2 = this.br.cloneBrowser();
                br2.getHeaders().put("Accept", "application/json");
                br2.getHeaders().put("Content-Type", "application/json");
                br2.getHeaders().put("X-CSRF-TOKEN", csrfToken != null ? csrfToken : "");
                br2.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                br2.getHeaders().put("Referer", contenturl);
                br2.postPageRaw("https://filmo.to/n", "{\"p\":\"" + pVal + "\"}");

                String token = PluginJSonUtils.getJsonValue(br2.toString(), "x");
                if (token != null && !token.isEmpty()) {
                    Browser br3 = this.br.cloneBrowser();
                    br3.getHeaders().put("Referer", contenturl);
                    br3.getPage("https://filmo.to/n/" + token);

                    String embedUrl = br3.getRegex("href=[\"'](https?://[^\"'<>]+)[\"']").getMatch(0);
                    if (embedUrl == null) {
                        embedUrl = br3.getRegex("src=[\"'](https?://[^\"'<>]+)[\"']").getMatch(0);
                    }
                    if (embedUrl == null) {
                        embedUrl = br3.getRegex("(https?://[a-zA-Z0-9.-]+/e/[a-zA-Z0-9_-]+)").getMatch(0);
                    }

                    if (embedUrl != null && !embedUrl.contains("filmo.to") && dupes.add(embedUrl)) {
                        DownloadLink link = this.createDownloadlink(embedUrl);
                        link._setFilePackage(fp);
                        ret.add(link);
                    }
                }
            } catch (Exception e) {
                this.getLogger().warning("Failed to resolve provider chip: " + e.getMessage());
            }
        }

        if (ret.isEmpty()) {
            throw new PluginException(32);
        }

        return ret;
    }
}
