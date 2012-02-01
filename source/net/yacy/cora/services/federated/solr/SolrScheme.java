/**
 *  SolrScheme
 *  Copyright 2011 by Michael Peter Christen
 *  First released 14.04.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 22:05:04 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7654 $
 *  $LastChangedBy: orbiter $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.services.federated.solr;


import java.io.File;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.storage.ConfigurationSet;
import net.yacy.document.Document;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;

public class SolrScheme extends ConfigurationSet {

    /**
     * initialize with an empty ConfigurationSet which will cause that all the index
     * attributes are used
     */
    public SolrScheme() {
        super();
    }

    /**
     * initialize the scheme with a given configuration file
     * the configuration file simply contains a list of lines with keywords
     * @param configurationFile
     */
    public SolrScheme(final File configurationFile) {
        super(configurationFile);
        // check consistency: compare with Field enum
        for (String name: this) {
            try {
                Field.valueOf(name);
            } catch (IllegalArgumentException e) {
                Log.logWarning("SolrScheme", "solr scheme file " + configurationFile.getAbsolutePath() + " defines unknown attribute '" + name + "'");
            }
        }
        /*
        for (Field field: Field.values()) {
            if (!this.contains(field.name())) {
                Log.logWarning("SolrScheme", "solr scheme file " + configurationFile.getAbsolutePath() + " omits known attribute '" + field.name() + "'");
            }
        }
        */
    }

    private void addSolr(final SolrInputDocument solrdoc, final Field key, final String value) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value);
    }

    private void addSolr(final SolrInputDocument solrdoc, final Field key, final Date value) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value);
    }

    private void addSolr(final SolrInputDocument solrdoc, final Field key, final int value) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value);
    }

    private void addSolr(final SolrInputDocument solrdoc, final Field key, final String[] value) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value);
    }

    private void addSolr(final SolrInputDocument solrdoc, final Field key, final float value) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value);
    }

    private void addSolr(final SolrInputDocument solrdoc, final Field key, final boolean value) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value);
    }

    private void addSolr(final SolrInputDocument solrdoc, final Field key, final String value, final float boost) {
        if (isEmpty() || contains(key.name())) solrdoc.setField(key.name(), value, boost);
    }

    public static enum Types {
        string,
        text_general,
        text_en_splitting_tight,
        date,
        integer("int"),
        tdouble,
        bool("boolean");

        private String printName;
        private Types() {
            this.printName = this.name();
        }
        private Types(String printName) {
            this.printName = printName;
        }
        public String printName() {
            return this.printName;
        }
    }

    public static enum Field {

        id(Types.string, true, true),
        sku(Types.text_en_splitting_tight, true, true, false, true),
        ip_s(Types.string, true, true),
        host_s(Types.string, true, true),
        title(Types.text_general, true, true, true),
        author(Types.text_general, true, true),
        description(Types.text_general, true, true),
        content_type(Types.string, true, true, true),
        last_modified(Types.date, true, true),
        keywords(Types.text_general, true, true),
        text_t(Types.text_general, true, true),
        wordcount_i(Types.integer, true, true),
        paths_txt(Types.text_general, true, true, true),
        inboundlinkscount_i(Types.integer, true, true),
        inboundlinksnofollowcount_i(Types.integer, true, true),
        inboundlinks_tag_txt(Types.text_general, true, true, true),
        inboundlinks_protocol_txt(Types.text_general, true, true, true),
        inboundlinks_urlstub_txt(Types.text_general, true, true, true),
        inboundlinks_name_txt(Types.text_general, true, true, true),
        inboundlinks_rel_txt(Types.text_general, true, true, true),
        inboundlinks_relflags_txt(Types.text_general, true, true, true),
        inboundlinks_text_txt(Types.text_general, true, true, true),
        outboundlinkscount_i(Types.integer, true, true),
        outboundlinksnofollowcount_i(Types.integer, true, true),
        outboundlinks_tag_txt(Types.text_general, true, true, true),
        outboundlinks_protocol_txt(Types.text_general, true, true, true),
        outboundlinks_urlstub_txt(Types.text_general, true, true, true),
        outboundlinks_name_txt(Types.text_general, true, true, true),
        outboundlinks_rel_txt(Types.text_general, true, true, true),
        outboundlinks_relflags_txt(Types.text_general, true, true, true),
        outboundlinks_text_txt(Types.text_general, true, true, true),
        charset_s(Types.string, true, true),
        lon_coordinate(Types.tdouble, true, false),
        lat_coordinate(Types.tdouble, true, false),
        httpstatus_i(Types.integer, true, true),
        h1_txt(Types.text_general, true, true, true),
        h2_txt(Types.text_general, true, true, true),
        h3_txt(Types.text_general, true, true, true),
        h4_txt(Types.text_general, true, true, true),
        h5_txt(Types.text_general, true, true, true),
        h6_txt(Types.text_general, true, true, true),
        htags_i(Types.integer, true, true),
        canonical_s(Types.string, true, true),
        robots_i(Types.integer, true, true),
        metagenerator_t(Types.text_general, true, true),
        boldcount_i(Types.integer, true, true),
        bold_txt(Types.text_general, true, true, true),
        bold_val(Types.integer, true, true, true),
        italiccount_i(Types.integer, true, true),
        italic_txt(Types.text_general, true, true, true),
        italic_val(Types.integer, true, true, true),
        licount_i(Types.integer, true, true),
        li_txt(Types.text_general, true, true, true),
        imagescount_i(Types.integer, true, true),
        images_tag_txt(Types.text_general, true, true, true),
        images_protocol_txt(Types.text_general, true, true, true),
        images_urlstub_txt(Types.text_general, true, true, true),
        images_alt_txt(Types.text_general, true, true, true),
        csscount_i(Types.integer, true, true),
        css_tag_txt(Types.text_general, true, true, true),
        css_url_txt(Types.text_general, true, true, true),
        scripts_txt(Types.text_general, true, true, true),
        scriptscount_i(Types.integer, true, true),
        frames_txt(Types.text_general, true, true, true),
        framesscount_i(Types.integer, true, true),
        iframes_txt(Types.text_general, true, true, true),
        iframesscount_i(Types.integer, true, true),
        flash_b(Types.bool, true, true),
        responsetime_i(Types.integer, true, true),

        ext_cms_txt(Types.text_general, true, true, true),
        ext_cms_val(Types.integer, true, true, true),
        ext_ads_txt(Types.text_general, true, true, true),
        ext_ads_val(Types.integer, true, true, true),
        ext_community_txt(Types.text_general, true, true, true),
        ext_community_val(Types.integer, true, true, true),
        ext_maps_txt(Types.text_general, true, true, true),
        ext_maps_val(Types.integer, true, true, true),
        ext_tracker_txt(Types.text_general, true, true, true),
        ext_tracker_val(Types.integer, true, true, true),
        ext_title_txt(Types.text_general, true, true, true),
        ext_title_val(Types.integer, true, true, true),

        failreason_t(Types.text_general, true, true);

        final Types type;
        final boolean indexed, stored;
        boolean multiValued, omitNorms;

        private Field(final Types type, final boolean indexed, final boolean stored) {
            this.type = type;
            this.indexed = indexed;
            this.stored = stored;
            this.multiValued = false;
            this.omitNorms = false;
        }

        private Field(final Types type, final boolean indexed, final boolean stored, final boolean multiValued) {
            this(type, indexed, stored);
            this.multiValued = multiValued;
        }

        private Field(final Types type, final boolean indexed, final boolean stored, final boolean multiValued, final boolean omitNorms) {
            this(type, indexed, stored, multiValued);
            this.omitNorms = omitNorms;
        }

        public final Types getType() {
            return this.type;
        }

        public final boolean isIndexed() {
            return this.indexed;
        }

        public final boolean isStored() {
            return this.stored;
        }

        public final boolean isMultiValued() {
            return this.multiValued;
        }

        public final boolean isOmitNorms() {
            return this.omitNorms;
        }

    }

    public SolrInputDocument yacy2solr(final String id, final ResponseHeader header, final Document yacydoc) {
        // we user the SolrCell design as index scheme
        final SolrInputDocument solrdoc = new SolrInputDocument();
        final DigestURI digestURI = new DigestURI(yacydoc.dc_source());
        addSolr(solrdoc, Field.failreason_t, ""); // overwrite a possible fail reason (in case that there was a fail reason before)
        addSolr(solrdoc, Field.id, id);
        addSolr(solrdoc, Field.sku, digestURI.toNormalform(true, false), 3.0f);
        final InetAddress address = digestURI.getInetAddress();
        if (address != null) addSolr(solrdoc, Field.ip_s, address.getHostAddress());
        if (digestURI.getHost() != null) addSolr(solrdoc, Field.host_s, digestURI.getHost());
        addSolr(solrdoc, Field.title, yacydoc.dc_title());
        addSolr(solrdoc, Field.author, yacydoc.dc_creator());
        addSolr(solrdoc, Field.description, yacydoc.dc_description());
        addSolr(solrdoc, Field.content_type, yacydoc.dc_format());
        addSolr(solrdoc, Field.last_modified, header.lastModified());
        addSolr(solrdoc, Field.keywords, yacydoc.dc_subject(' '));
        final String content = UTF8.String(yacydoc.getTextBytes());
        addSolr(solrdoc, Field.text_t, content);
        if (isEmpty() || contains(Field.wordcount_i.name())) {
            final int contentwc = content.split(" ").length;
            addSolr(solrdoc, Field.wordcount_i, contentwc);
        }

        // path elements of link
        final String path = digestURI.getPath();
        if (path != null && (isEmpty() || contains(Field.paths_txt.name()))) {
            final String[] paths = path.split("/");
            if (paths.length > 0) addSolr(solrdoc, Field.paths_txt, paths);
        }

        // list all links
        final Map<MultiProtocolURI, Properties> alllinks = yacydoc.getAnchors();
        int c = 0;
        if (isEmpty() || contains(Field.inboundlinkscount_i.name())) addSolr(solrdoc, Field.inboundlinkscount_i, yacydoc.inboundLinkCount());
        if (isEmpty() || contains(Field.inboundlinksnofollowcount_i.name())) addSolr(solrdoc, Field.inboundlinksnofollowcount_i, yacydoc.inboundLinkNofollowCount());
        final String[] inboundlinksTag = new String[yacydoc.inboundLinkCount()];
        final String[] inboundlinksURLProtocol = new String[yacydoc.inboundLinkCount()];
        final String[] inboundlinksURLStub = new String[yacydoc.inboundLinkCount()];
        final String[] inboundlinksName = new String[yacydoc.inboundLinkCount()];
        final String[] inboundlinksRel = new String[yacydoc.inboundLinkCount()];
        final String[] inboundlinksText = new String[yacydoc.inboundLinkCount()];
        for (final MultiProtocolURI url: yacydoc.inboundLinks()) {
            final Properties p = alllinks.get(url);
            final String name = p.getProperty("name", ""); // the name attribute
            final String rel = p.getProperty("rel", "");   // the rel-attribute
            final String text = p.getProperty("text", ""); // the text between the <a></a> tag
            final String urls = url.toNormalform(false, false);
            final int pr = urls.indexOf("://",0);
            inboundlinksURLProtocol[c] = urls.substring(0, pr);
            inboundlinksURLStub[c] = urls.substring(pr + 3);
            inboundlinksName[c] = name.length() > 0 ? name : "";
            inboundlinksRel[c] = rel.length() > 0 ? rel : "";
            inboundlinksText[c] = text.length() > 0 ? text : "";
            inboundlinksTag[c] =
                "<a href=\"" + url.toNormalform(false, false) + "\"" +
                (rel.length() > 0 ? " rel=\"" + rel + "\"" : "") +
                (name.length() > 0 ? " name=\"" + name + "\"" : "") +
                ">" +
                ((text.length() > 0) ? text : "") + "</a>";
            c++;
        }
        if (isEmpty() || contains(Field.inboundlinks_tag_txt.name())) addSolr(solrdoc, Field.inboundlinks_tag_txt, inboundlinksTag);
        if (isEmpty() || contains(Field.inboundlinks_protocol_txt.name())) addSolr(solrdoc, Field.inboundlinks_protocol_txt, protocolList2indexedList(inboundlinksURLProtocol));
        if (isEmpty() || contains(Field.inboundlinks_urlstub_txt.name())) addSolr(solrdoc, Field.inboundlinks_urlstub_txt, inboundlinksURLStub);
        if (isEmpty() || contains(Field.inboundlinks_name_txt.name())) addSolr(solrdoc, Field.inboundlinks_name_txt, inboundlinksName);
        if (isEmpty() || contains(Field.inboundlinks_rel_txt.name())) addSolr(solrdoc, Field.inboundlinks_rel_txt, inboundlinksRel);
        if (isEmpty() || contains(Field.inboundlinks_relflags_txt.name())) addSolr(solrdoc, Field.inboundlinks_relflags_txt, relEval(inboundlinksRel));
        if (isEmpty() || contains(Field.inboundlinks_text_txt.name())) addSolr(solrdoc, Field.inboundlinks_text_txt, inboundlinksText);

        c = 0;
        if (isEmpty() || contains(Field.outboundlinkscount_i.name())) addSolr(solrdoc, Field.outboundlinkscount_i, yacydoc.outboundLinkCount());
        if (isEmpty() || contains(Field.outboundlinksnofollowcount_i.name())) addSolr(solrdoc, Field.outboundlinksnofollowcount_i, yacydoc.outboundLinkNofollowCount());
        final String[] outboundlinksTag = new String[yacydoc.outboundLinkCount()];
        final String[] outboundlinksURLProtocol = new String[yacydoc.outboundLinkCount()];
        final String[] outboundlinksURLStub = new String[yacydoc.outboundLinkCount()];
        final String[] outboundlinksName = new String[yacydoc.outboundLinkCount()];
        final String[] outboundlinksRel = new String[yacydoc.outboundLinkCount()];
        final String[] outboundlinksText = new String[yacydoc.outboundLinkCount()];
        for (final MultiProtocolURI url: yacydoc.outboundLinks()) {
            final Properties p = alllinks.get(url);
            final String name = p.getProperty("name", ""); // the name attribute
            final String rel = p.getProperty("rel", "");   // the rel-attribute
            final String text = p.getProperty("text", ""); // the text between the <a></a> tag
            final String urls = url.toNormalform(false, false);
            final int pr = urls.indexOf("://",0);
            outboundlinksURLProtocol[c] = urls.substring(0, pr);
            outboundlinksURLStub[c] = urls.substring(pr + 3);
            outboundlinksName[c] = name.length() > 0 ? name : "";
            outboundlinksRel[c] = rel.length() > 0 ? rel : "";
            outboundlinksText[c] = text.length() > 0 ? text : "";
            outboundlinksTag[c] =
                "<a href=\"" + url.toNormalform(false, false) + "\"" +
                (rel.length() > 0 ? " rel=\"" + rel + "\"" : "") +
                (name.length() > 0 ? " name=\"" + name + "\"" : "") +
                ">" +
                ((text.length() > 0) ? text : "") + "</a>";
            c++;
        }
        if (isEmpty() || contains(Field.outboundlinks_tag_txt.name())) addSolr(solrdoc, Field.outboundlinks_tag_txt, outboundlinksTag);
        if (isEmpty() || contains(Field.outboundlinks_protocol_txt.name())) addSolr(solrdoc, Field.outboundlinks_protocol_txt, protocolList2indexedList(outboundlinksURLProtocol));
        if (isEmpty() || contains(Field.outboundlinks_urlstub_txt.name())) addSolr(solrdoc, Field.outboundlinks_urlstub_txt, outboundlinksURLStub);
        if (isEmpty() || contains(Field.outboundlinks_name_txt.name())) addSolr(solrdoc, Field.outboundlinks_name_txt, outboundlinksName);
        if (isEmpty() || contains(Field.outboundlinks_rel_txt.name())) addSolr(solrdoc, Field.outboundlinks_rel_txt, outboundlinksRel);
        if (isEmpty() || contains(Field.outboundlinks_relflags_txt.name())) addSolr(solrdoc, Field.outboundlinks_relflags_txt, relEval(inboundlinksRel));
        if (isEmpty() || contains(Field.outboundlinks_text_txt.name())) addSolr(solrdoc, Field.outboundlinks_text_txt, outboundlinksText);


        // charset
        addSolr(solrdoc, Field.charset_s, yacydoc.getCharset());

        // coordinates
        if (yacydoc.lat() != 0.0f && yacydoc.lon() != 0.0f) {
            addSolr(solrdoc, Field.lon_coordinate, yacydoc.lon());
            addSolr(solrdoc, Field.lat_coordinate, yacydoc.lat());
        }
        addSolr(solrdoc, Field.httpstatus_i, 200);
        final Object parser = yacydoc.getParserObject();
        if (parser instanceof ContentScraper) {
            final ContentScraper html = (ContentScraper) parser;

            // header tags
            int h = 0;
            int f = 1;
            String[] hs;

            hs = html.getHeadlines(1); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, Field.h1_txt, hs);
            hs = html.getHeadlines(2); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, Field.h2_txt, hs);
            hs = html.getHeadlines(3); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, Field.h3_txt, hs);
            hs = html.getHeadlines(4); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, Field.h4_txt, hs);
            hs = html.getHeadlines(5); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, Field.h5_txt, hs);
            hs = html.getHeadlines(6); h = h | (hs.length > 0 ? f : 0); f = f * 2; addSolr(solrdoc, Field.h6_txt, hs);

            addSolr(solrdoc, Field.htags_i, h);

            // canonical tag
            if (html.getCanonical() != null) addSolr(solrdoc, Field.canonical_s, html.getCanonical().toNormalform(false, false));

            // noindex and nofollow attributes
            // from HTML (meta-tag in HTML header: robots)
            // and HTTP header (x-robots property)
            // coded as binary value:
            // bit  0: "all" contained in html header meta
            // bit  1: "index" contained in html header meta
            // bit  2: "noindex" contained in html header meta
            // bit  3: "nofollow" contained in html header meta
            // bit  8: "noarchive" contained in http header properties
            // bit  9: "nosnippet" contained in http header properties
            // bit 10: "noindex" contained in http header properties
            // bit 11: "nofollow" contained in http header properties
            // bit 12: "unavailable_after" contained in http header properties
            int b = 0;
            final String robots_meta = html.getMetas().get("robots");
            // this tag may have values: all, index, noindex, nofollow
            if (robots_meta != null) {
                if (robots_meta.indexOf("all",0) >= 0) b += 1;      // set bit 0
                if (robots_meta.indexOf("index",0) == 0 || robots_meta.indexOf(" index",0) >= 0 || robots_meta.indexOf(",index",0) >= 0 ) b += 2; // set bit 1
                if (robots_meta.indexOf("noindex",0) >= 0) b += 4;  // set bit 2
                if (robots_meta.indexOf("nofollow",0) >= 0) b += 8; // set bit 3
            }
            String x_robots_tag = header.get(HeaderFramework.X_ROBOTS_TAG, "");
            if (x_robots_tag.length() == 0) x_robots_tag = header.get(HeaderFramework.X_ROBOTS, "");
            // this tag may have values: noarchive, nosnippet, noindex, unavailable_after
            if (x_robots_tag.length() > 0) {
                if (x_robots_tag.indexOf("noarchive",0) >= 0) b += 256;         // set bit 8
                if (x_robots_tag.indexOf("nosnippet",0) >= 0) b += 512;         // set bit 9
                if (x_robots_tag.indexOf("noindex",0) >= 0) b += 1024;          // set bit 10
                if (x_robots_tag.indexOf("nofollow",0) >= 0) b += 2048;         // set bit 11
                if (x_robots_tag.indexOf("unavailable_after",0) >=0) b += 4096; // set bit 12
            }
            addSolr(solrdoc, Field.robots_i, b);

            // meta tags: generator
            final String generator = html.getMetas().get("generator");
            if (generator != null) addSolr(solrdoc, Field.metagenerator_t, generator);

            // bold, italic
            final String[] bold = html.getBold();
            addSolr(solrdoc, Field.boldcount_i, bold.length);
            if (bold.length > 0) {
                addSolr(solrdoc, Field.bold_txt, bold);
                if (isEmpty() || contains(Field.bold_val.name())) {
                    addSolr(solrdoc, Field.bold_val, html.getBoldCount(bold));
                }
            }
            final String[] italic = html.getItalic();
            addSolr(solrdoc, Field.italiccount_i, italic.length);
            if (italic.length > 0) {
                addSolr(solrdoc, Field.italic_txt, italic);
                if (isEmpty() || contains(Field.italic_val.name())) {
                    addSolr(solrdoc, Field.italic_val, html.getItalicCount(italic));
                }
            }
            final String[] li = html.getLi();
            addSolr(solrdoc, Field.licount_i, li.length);
            if (li.length > 0) addSolr(solrdoc, Field.li_txt, li);

            // images
            final Collection<ImageEntry> imagesc = html.getImages().values();
            final String[] imgtags  = new String[imagesc.size()];
            final String[] imgprots = new String[imagesc.size()];
            final String[] imgstubs = new String[imagesc.size()];
            final String[] imgalts  = new String[imagesc.size()];
            c = 0;
            for (final ImageEntry ie: imagesc) {
                final MultiProtocolURI uri = ie.url();
                imgtags[c] = ie.toString();
                imgprots[c] = uri.getProtocol();
                imgstubs[c] = uri.toString().substring(imgprots[c].length() + 3);
                imgalts[c] = ie.alt();
                c++;
            }
            addSolr(solrdoc, Field.imagescount_i, imgtags.length);
            if (isEmpty() || contains(Field.images_tag_txt.name())) addSolr(solrdoc, Field.images_tag_txt, imgtags);
            if (isEmpty() || contains(Field.images_protocol_txt.name())) addSolr(solrdoc, Field.images_protocol_txt, protocolList2indexedList(imgprots));
            if (isEmpty() || contains(Field.images_urlstub_txt.name())) addSolr(solrdoc, Field.images_urlstub_txt, imgstubs);
            if (isEmpty() || contains(Field.images_alt_txt.name())) addSolr(solrdoc, Field.images_alt_txt, imgalts);

            // style sheets
            if (isEmpty() || contains("css_txt")) {
                final Map<MultiProtocolURI, String> csss = html.getCSS();
                final String[] css_tag = new String[csss.size()];
                final String[] css_url = new String[csss.size()];
                c = 0;
                for (final Map.Entry<MultiProtocolURI, String> entry: csss.entrySet()) {
                    final String url = entry.getKey().toNormalform(false, false, false, false);
                    css_tag[c] =
                        "<link rel=\"stylesheet\" type=\"text/css\" media=\"" + entry.getValue() + "\"" +
                        " href=\""+ url + "\" />";
                    css_url[c] = url;
                    c++;
                }
                addSolr(solrdoc, Field.csscount_i, css_tag.length);
                if (css_tag.length > 0) addSolr(solrdoc, Field.css_tag_txt, css_tag);
                if (css_url.length > 0) addSolr(solrdoc, Field.css_url_txt, css_url);
            }

            // Scripts
            if (isEmpty() || contains(Field.scripts_txt.name())) {
                final Set<MultiProtocolURI> scriptss = html.getScript();
                final String[] scripts = new String[scriptss.size()];
                c = 0;
                for (final MultiProtocolURI url: scriptss) {
                    scripts[c++] = url.toNormalform(false, false, false, false);
                }
                addSolr(solrdoc, Field.scriptscount_i, scripts.length);
                if (scripts.length > 0) addSolr(solrdoc, Field.scripts_txt, scripts);
            }

            // Frames
            if (isEmpty() || contains(Field.frames_txt.name())) {
                final Set<MultiProtocolURI> framess = html.getFrames();
                final String[] frames = new String[framess.size()];
                c = 0;
                for (final MultiProtocolURI entry: framess) {
                    frames[c++] = entry.toNormalform(false, false, false, false);
                }
                addSolr(solrdoc, Field.framesscount_i, frames.length);
                if (frames.length > 0) addSolr(solrdoc, Field.frames_txt, frames);
            }

            // IFrames
            if (isEmpty() || contains(Field.iframes_txt.name()
                            )) {
                final Set<MultiProtocolURI> iframess = html.getIFrames();
                final String[] iframes = new String[iframess.size()];
                c = 0;
                for (final MultiProtocolURI entry: iframess) {
                    iframes[c++] = entry.toNormalform(false, false, false, false);
                }
                addSolr(solrdoc, Field.iframesscount_i, iframes.length);
                if (iframes.length > 0) addSolr(solrdoc, Field.iframes_txt, iframes);
            }

            // flash embedded
            addSolr(solrdoc, Field.flash_b, html.containsFlash());

            // generic evaluation pattern
            for (final String model: html.getEvaluationModelNames()) {
                if (isEmpty() || contains("ext_" + model + "_txt")) {
                    final String[] scorenames = html.getEvaluationModelScoreNames(model);
                    if (scorenames.length > 0) {
                        addSolr(solrdoc, Field.valueOf("ext_" + model + "_txt"), scorenames);
                        addSolr(solrdoc, Field.valueOf("ext_" + model + "_val"), html.getEvaluationModelScoreCounts(model, scorenames));
                    }
                }
            }

            // response time
            addSolr(solrdoc, Field.responsetime_i, header.get(HeaderFramework.RESPONSE_TIME_MILLIS, "0"));
        }
        return solrdoc;
    }

    private static String[] protocolList2indexedList(String[] protocol) {
        List<String> a = new ArrayList<String>();
        for (int i = 0; i < protocol.length; i++) {
            if (!protocol[i].equals("http")) {
                String c = Integer.toString(i);
                while (c.length() < 3) c = "0" + c;
                a.add(c + "-" + protocol[i]);
            }
        }
        return a.toArray(new String[a.size()]);
    }

    /**
     * encode a string containing attributes from anchor rel properties binary:
     * bit 0: "me" contained in rel
     * bit 1: "nofollow" contained in rel
     * @param rel
     * @return binary encoded information about rel
     */
    private int relEval(final String[] rel) {
        int i = 0;
        for (final String s: rel) {
            final String s0 = s.toLowerCase().trim();
            if ("me".equals(s0)) i += 1;
            if ("nofollow".equals(s0)) i += 2;
        }
        return i;
    }

    public String solrGetID(final SolrDocument solr) {
        return (String) solr.getFieldValue("id");
    }

    public DigestURI solrGetURL(final SolrDocument solr) {
        try {
            return new DigestURI((String) solr.getFieldValue("sku"));
        } catch (final MalformedURLException e) {
            return null;
        }
    }

    public String solrGetTitle(final SolrDocument solr) {
        return (String) solr.getFieldValue("title");
    }

    public String solrGetText(final SolrDocument solr) {
        return (String) solr.getFieldValue("text_t");
    }

    public String solrGetAuthor(final SolrDocument solr) {
        return (String) solr.getFieldValue("author");
    }

    public String solrGetDescription(final SolrDocument solr) {
        return (String) solr.getFieldValue("description");
    }

    public Date solrGetDate(final SolrDocument solr) {
        return (Date) solr.getFieldValue("last_modified");
    }

    public Collection<String> solrGetKeywords(final SolrDocument solr) {
        final Collection<Object> c = solr.getFieldValues("keywords");
        final ArrayList<String> a = new ArrayList<String>();
        for (final Object s: c) {
            a.add((String) s);
        }
        return a;
    }

    /*
   standard solr schema

   <field name="name" type="textgen" indexed="true" stored="true"/>
   <field name="cat" type="string" indexed="true" stored="true" multiValued="true"/>
   <field name="features" type="text" indexed="true" stored="true" multiValued="true"/>
   <field name="includes" type="text" indexed="true" stored="true" termVectors="true" termPositions="true" termOffsets="true" />

   <field name="weight" type="float" indexed="true" stored="true"/>
   <field name="price"  type="float" indexed="true" stored="true"/>
   <field name="popularity" type="int" indexed="true" stored="true" />

   <!-- Common metadata fields, named specifically to match up with
     SolrCell metadata when parsing rich documents such as Word, PDF.
     Some fields are multiValued only because Tika currently may return
     multiple values for them.
   -->
   <field name="title" type="text" indexed="true" stored="true" multiValued="true"/>
   <field name="subject" type="text" indexed="true" stored="true"/>
   <field name="description" type="text" indexed="true" stored="true"/>
   <field name="comments" type="text" indexed="true" stored="true"/>
   <field name="author" type="textgen" indexed="true" stored="true"/>
   <field name="keywords" type="textgen" indexed="true" stored="true"/>
   <field name="category" type="textgen" indexed="true" stored="true"/>
   <field name="content_type" type="string" indexed="true" stored="true" multiValued="true"/>
   <field name="last_modified" type="date" indexed="true" stored="true"/>
   <field name="links" type="string" indexed="true" stored="true" multiValued="true"/>
     */
}
