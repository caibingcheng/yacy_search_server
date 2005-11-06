// httpc.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 26.02.2004
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverCore.Session;

import org.apache.commons.pool.impl.GenericObjectPool;

/**
* This class implements an http client. While http access is built-in in java
* libraries, it is still necessary to implement the network interface since
* otherwise there is no access to the HTTP/1.0 / HTTP/1.1 header information
* that comes along each connection.
* FIXME: Add some information about the usage of the threadpool.
*/
public final class httpc {

    // some constants
    /** 
     * Specifies that the httpc is allowed to use gzip content encoding for
     * http post requests 
     * @see #POST(String, httpHeader, serverObjects, HashMap)
     */
    public static final String GZIP_POST_BODY = "GZIP_POST_BODY";
    
    // statics
    private static final String vDATE = "20040602";
    private static String userAgent;
    private static final int terminalMaxLength = 30000;
    private static final TimeZone GMTTimeZone = TimeZone.getTimeZone("PST");
    /**
    * This string is initialized on loading of this class and contains
    * information about the current OS.
    */
    public static String systemOST;

    // --- The GMT standard date format used in the HTTP protocol
    private static final SimpleDateFormat HTTPGMTFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
    private static final SimpleDateFormat EMLFormatter     = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US);
    private static final SimpleDateFormat ShortFormatter   = new SimpleDateFormat("yyyyMMddHHmmss");
    //Mo 06 Sep 2004 23:32
    static final HashMap reverseMappingCache = new HashMap();

    // the dns cache
    private static final HashMap nameCacheHit = new HashMap();
    //private static HashSet nameCacheMiss = new HashSet();

    /**
     * A Object Pool containing all pooled httpc-objects.
     * @see httpcPool
     */
    private static final httpcPool theHttpcPool;

    // class variables
    private Socket socket = null; // client socket for commands
    private Thread socketOwner = null;
    String host = null;
    private long timeout;
    private long handle;

    // output and input streams for client control connection
    PushbackInputStream clientInput = null;
    private OutputStream clientOutput = null;
    
    private httpdByteCountInputStream clientInputByteCount = null;
    private httpdByteCountOutputStream clientOutputByteCount = null;

    private boolean remoteProxyUse = false;
    private String  savedRemoteHost = null;
    private httpRemoteProxyConfig remoteProxyConfig = null;
    
    String  requestPath = null;
    private boolean allowContentEncoding = true;
	static boolean useYacyReferer = true;
	public static boolean yacyDebugMode = false;

    static {
        // set time-out of InetAddress.getByName cache ttl
        java.security.Security.setProperty("networkaddress.cache.ttl" , "60");
		java.security.Security.setProperty("networkaddress.cache.negative.ttl" , "0");
    }

    /**
     * Indicates if the current object was removed from pool because the maximum limit
     * was exceeded.
     */
    boolean removedFromPool = false;

    // Configuring the httpc object pool
    static {
        // implementation of session thread pool
        GenericObjectPool.Config config = new GenericObjectPool.Config();

        // The maximum number of active connections that can be allocated from pool at the same time,
        // 0 for no limit
        config.maxActive = 150;

        // The maximum number of idle connections connections in the pool
        // 0 = no limit.
        config.maxIdle = 75;
        config.minIdle = 10;

        config.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK;
        config.minEvictableIdleTimeMillis = 30000;

        theHttpcPool = new httpcPool(new httpcFactory(),config);
    }
    
    // initializing a dummy trustManager to enable https connections
    static SSLSocketFactory theSSLSockFactory = null;
    static {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }
 
            public void checkClientTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
            }
 
            public void checkServerTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
            }
        } };
 
        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            // Create empty HostnameVerifier
            HostnameVerifier hv = new HostnameVerifier() {
                public boolean verify(String urlHostName, javax.net.ssl.SSLSession session) {
                    // logger.info("Warning: URL Host: "+urlHostName+"
                    // vs."+session.getPeerHost());
                    return true;
                }
            };
 
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(theSSLSockFactory = sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(hv);
        } catch (Exception e) {
        }        
    }

    /**
     * A reusable readline buffer
     * @see serverByteBuffer
     */
    final serverByteBuffer readLineBuffer = new serverByteBuffer(100);

    private static final HashMap openSocketLookupTable = new HashMap();

    /**
    * Convert the status of this class into an String object to output it.
    */
    public String toString() {
        return (this.savedRemoteHost == null) ? "Disconnected" : "Connected to " + this.savedRemoteHost +
                ((this.remoteProxyUse) ? " via " + this.host : "");
    }

    /**
    * This method gets a new httpc instance from the object pool and
    * initializes it with the given parameters. Use this method if you have to
    * use a proxy to access the pages.
    *
    * @param server
    * @param port
    * @param timeout
    * @param ssl
    * @param remoteProxyHost
    * @param remoteProxyPort
    * @throws IOException
    * @see httpc#init
    */
    public static httpc getInstance(
            String server,
            int port,
            int timeout,
            boolean ssl,
            httpRemoteProxyConfig remoteProxyConfig,
            String incomingByteCountAccounting,
            String outgoingByteCountAccounting
            ) throws IOException {

        httpc newHttpc;
        try {
            // fetching a new httpc from the object pool
            newHttpc = (httpc) httpc.theHttpcPool.borrowObject();
        } catch (Exception e) {
            throw new IOException("Unable to initialize a new httpc. " + e.getMessage());
        }

        // initialize it
        try {
            newHttpc.init(
                    server,
                    port,
                    timeout,
                    ssl,
                    remoteProxyConfig,
                    incomingByteCountAccounting,
                    outgoingByteCountAccounting
            );
        } catch (IOException e) {
            try{ httpc.theHttpcPool.returnObject(newHttpc); } catch (Exception e1) {}
            throw e;
        }
        return newHttpc;
    }
    
    public static httpc getInstance(
            String server,
            int port,
            int timeout,
            boolean ssl,
            httpRemoteProxyConfig remoteProxyConfig
            ) throws IOException {
        return getInstance(server,port,timeout,ssl,remoteProxyConfig,null,null);
    }

    public static httpc getInstance(
            String server, 
            int port, 
            int timeout, 
            boolean ssl
    ) throws IOException {
        return getInstance(server,port,timeout,ssl,null,null);
    }

    
    /**
    * This method gets a new httpc instance from the object pool and
    * initializes it with the given parameters.
    *
    * @param server
    * @param port
    * @param timeout
    * @param ssl
    * @throws IOException
    * @see httpc#init
    */
    public static httpc getInstance(
            String server, 
            int port, 
            int timeout, 
            boolean ssl,
            String incomingByteCountAccounting,
            String outgoingByteCountAccounting
    ) throws IOException {

        httpc newHttpc = null;
        // fetching a new httpc from the object pool
        try {
            newHttpc = (httpc) httpc.theHttpcPool.borrowObject();

        } catch (Exception e) {
            throw new IOException("Unable to fetch a new httpc from pool. " + e.getMessage());
        }

        // initialize it
        try {
            newHttpc.init(server,port,timeout,ssl,incomingByteCountAccounting,outgoingByteCountAccounting);
        } catch (IOException e) {
            try{ httpc.theHttpcPool.returnObject(newHttpc); } catch (Exception e1) {}
            throw e;
        }
        return newHttpc;


    }

    /**
    * Put back a used instance into the instance pool of httpc.
    *
    * @param theHttpc The instance of httpc which should be returned to the pool
    */
    public static void returnInstance(httpc theHttpc) {
        try {
            theHttpc.reset();
            httpc.theHttpcPool.returnObject(theHttpc);
        } catch (Exception e) {
            // we could ignore this error
        }
    }

    /**
    * Sets wether the content is allowed to be unzipped while getting?
    * FIXME: The name of this method seems misleading, if I read the usage of
    * this method correctly?
    *
    * @param status true, if the content is allowed to be decoded on the fly?
    */
    public void setAllowContentEncoding(boolean status) {
        this.allowContentEncoding = status;
    }

    /**
    * Check wether the connection of this instance is closed.
    *
    * @return true if the connection is no longer open.
    */
    public boolean isClosed() {
        if (this.socket == null) return true;
        return (!this.socket.isConnected()) || (this.socket.isClosed());
    }

    /**
    * Does an DNS-Check to resolve a hostname to an IP.
    *
    * @param host Hostname of the host in demand.
    * @return String with the ip. null, if the host could not be resolved.
    */
    public static String dnsResolve(String host) {
        String ip = (String) nameCacheHit.get(host);
        if (ip != null) return ip;
        // if (nameCacheMiss.contains(host)) return null;
        try {
            ip = InetAddress.getByName(host).getHostAddress();
            if ((ip != null) && (!(ip.equals("127.0.0.1"))) && (!(ip.equals("localhost")))) {
                if (host.indexOf("dyndns") < 0) nameCacheHit.put(host, ip);
                return ip;
            }
            return null;
        } catch (UnknownHostException e) {
            //nameCacheMiss.add(host);
        }
        return null;
    }

    /**
    * Checks wether an hostname already is in the DNS-cache.
    * FIXME: This method should use dnsResolve, as the code is 90% identical?
    *
    * @param host Searched for hostname.
    * @return true, if the hostname already is in the cache.
    */
    public static boolean dnsFetch(String host) {
        if ((nameCacheHit.get(host) != null) /*|| (nameCacheMiss.contains(host)) */) return false;
        if (host.indexOf("dyndns") < 0) return false;
        try {
            String ip = InetAddress.getByName(host).getHostAddress();
            if ((ip != null) && (!(ip.equals("127.0.0.1"))) && (!(ip.equals("localhost")))) {
                nameCacheHit.put(host, ip);
                return true;
            }
            return false;
        } catch (UnknownHostException e) {
            //nameCacheMiss.add(host);
            return false;
        }
    }

    /**
    * Returns the given date in an HTTP-usable format.
    *
    * @param date The Date-Object to be converted.
    * @return String with the date.
    */
    public static String dateString(Date date) {
        if (date == null) return ""; else return HTTPGMTFormatter.format(date);
    }

    /**
    * Returns the current date as Date-Object.
    *
    * @return Date-object with the current time.
    */
    public static Date nowDate() {
        return new GregorianCalendar(GMTTimeZone).getTime();
    }

    // FIXME: Why weren't all static parts put together? They are run one after
    // each other on class loading? Hopefully. So why not put them into one
    // static block?
    static {
        // provide system information for client identification
        String loc = System.getProperty("user.timezone", "nowhere");
        int p = loc.indexOf("/");
        if (p > 0) loc = loc.substring(0,p);
        loc = loc + "/" + System.getProperty("user.language", "dumb");
        systemOST =
                System.getProperty("os.arch", "no-os-arch") + " " + System.getProperty("os.name", "no-os-arch") + " " +
                System.getProperty("os.version", "no-os-version") + "; " +
                "java " + System.getProperty("java.version", "no-java-version") + "; " + loc;
        userAgent = "yacy (www.yacy.net; v" + vDATE + "; " + systemOST + ")";
    }

    /**
    * Initialize the httpc-instance with the given data. This method is used,
    * if you have to use a proxy to access the pages. This just calls init
    * without proxy information and adds the proxy information.
    *
    * @param remoteProxyHost
    * @param remoteProxyPort
    * @throws IOException
    */
    void init(
            String server, 
            int port, 
            int timeout, 
            boolean ssl,
            httpRemoteProxyConfig theRemoteProxyConfig,
            String incomingByteCountAccounting,
            String outgoingByteCountAccounting            
    ) throws IOException {
        
        if (port == -1) {
            port = (ssl)? 443 : 80;
        }
        
        String remoteProxyHost = theRemoteProxyConfig.getProxyHost();
        int    remoteProxyPort = theRemoteProxyConfig.getProxyPort();
        
        this.init(remoteProxyHost, remoteProxyPort, timeout, ssl,incomingByteCountAccounting,outgoingByteCountAccounting);
        
        this.remoteProxyUse = true;
        this.savedRemoteHost = server + ((port == 80) ? "" : (":" + port));
        this.remoteProxyConfig = theRemoteProxyConfig;
    }

    /**
    * Initialize the https-instance with the given data. Opens the sockets to
    * the remote server and creats input and output streams.
    *
    * @param server Hostname of the server to connect to.
    * @param port On which port should we connect.
    * @param timeout How long do we wait for answers?
    * @param ssl Wether we should use SSL.
    * @throws IOException
    */
    void init(
            String server, 
            int port, 
            int timeout, 
            boolean ssl,
            String incomingByteCountAccounting,
            String outgoingByteCountAccounting
    ) throws IOException {
        this.handle = System.currentTimeMillis();
        //serverLog.logDebug("HTTPC", handle + " initialized");
        this.remoteProxyUse = false;
        this.timeout = timeout;
		if(yacyDebugMode){
			this.timeout=60000;
		}
        this.savedRemoteHost = server;

        try {
            if (port == -1) {
                port = (ssl)? 443 : 80;
            }
            
            this.host = server + ((port == 80) ? "" : (":" + port));
            String hostip;
            if ((server.equals("localhost")) || (server.equals("127.0.0.1")) || (server.startsWith("192.168.")) || (server.startsWith("10."))) {
                hostip = server;
            } else {
                hostip = dnsResolve(server);
                if (hostip == null) throw new UnknownHostException(server);
            }

            // creating a socket
            this.socket = (ssl) 
                        ? theSSLSockFactory.createSocket()
                        : new Socket();
            
            // creating a socket address
            InetSocketAddress address = new InetSocketAddress(hostip, port);

            // trying to establish a connection to the address
            this.socket.connect(address,timeout);
            
            // registering the socket
            this.socketOwner = this.registerOpenSocket(this.socket);

            // setting socket timeout and keep alive behaviour
            this.socket.setSoTimeout(timeout); // waiting time for read
            //socket.setSoLinger(true, timeout);
            this.socket.setKeepAlive(true); //

            if (incomingByteCountAccounting != null) {
                this.clientInputByteCount = new httpdByteCountInputStream(this.socket.getInputStream(),incomingByteCountAccounting);
            }
            
            // getting input and output streams
            this.clientInput  = new PushbackInputStream((this.clientInputByteCount!=null)?
                                this.clientInputByteCount:
                                this.socket.getInputStream()); 
            this.clientOutput = this.socket.getOutputStream();
            

                                  

            // if we reached this point, we should have a connection
        } catch (UnknownHostException e) {
            if (this.socket != null) {
                httpc.unregisterOpenSocket(this.socket,this.socketOwner);
            }
            this.socket = null;
            this.socketOwner = null;
            throw new IOException("unknown host: " + server);
        }
    }    
    
    public long getInputStreamByteCount() {
        return (this.clientInputByteCount == null)?0:this.clientInputByteCount.getCount();
    }
    
    public long getOutputStreamByteCount() {
        return (this.clientOutputByteCount == null)?0:this.clientOutputByteCount.getCount();
    }

    /**
    * This method resets an httpc-instance, so that it can be used for the next
    * connection. This is called before the instance is put back to the pool.
    * All streams and sockets are closed and set to null.
    */
    void reset() {
        if (this.clientInput != null) {
            try {this.clientInput.close();} catch (Exception e) {}
            this.clientInput = null;
        }
        if (this.clientOutput != null) {
            try {this.clientOutput.close();} catch (Exception e) {}
            this.clientOutput = null;
        }
        if (this.socket != null) {
            try {this.socket.close();} catch (Exception e) {}
            httpc.unregisterOpenSocket(this.socket,this.socketOwner);
            this.socket = null;
            this.socketOwner = null;
        }
        
        if (this.clientInputByteCount != null) {
            try { this.clientInputByteCount.finish();} catch (IOException e) {}
            this.clientInputByteCount = null;
        }
        if (this.clientOutputByteCount != null) {
            try { this.clientOutputByteCount.finish();} catch (IOException e) {}
            this.clientOutputByteCount = null;
        }

        this.host = null;
        this.timeout = 0;
        this.handle = 0;

        this.remoteProxyUse = false;
        this.remoteProxyConfig = null;
        this.savedRemoteHost = null;
        this.requestPath = null;

        this.allowContentEncoding = true;

        // shrink readlinebuffer if it is to large
        this.readLineBuffer.reset(80);
    }

    /**
    * Just calls reset to close all connections.
    */
    public void close() {
        reset();
    }

    /**
    * If this instance is garbage-collected we check if the object was returned
    * to the pool. if not, we invalidate the object from the pool.
    *
    * @see httpcPool#invalidateObject
    */
    protected void finalize() throws Throwable {
        if (!(this.removedFromPool)) {
            System.err.println("Httpc object was not returned to object pool.");
            httpc.theHttpcPool.invalidateObject(this);
        }
        this.reset();
    }

    /**
    * This method invokes a call to the given server.
    *
    * @param method Which method should be called? GET, POST, HEAD or CONNECT
    * @param path String with the path on the server to be get.
    * @param header The prefilled header (if available) from the calling
    * browser.
    * @param zipped Is encoded content (gzip) allowed or not?
    * @throws IOException
    */
    private void send(String method, String path, httpHeader header, boolean zipped) throws IOException {
        // scheduled request through request-response objects/threads

        // check and correct path
        if ((path == null) || (path.length() == 0)) path = "/";

        // for debuggug:
        this.requestPath = path;

        // prepare header
        if (header == null) header = new httpHeader();

        // set some standard values
        if (!(header.containsKey(httpHeader.ACCEPT)))
            header.put(httpHeader.ACCEPT, "text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
        if (!(header.containsKey(httpHeader.ACCEPT_CHARSET)))
            header.put(httpHeader.ACCEPT_CHARSET, "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
        if (!(header.containsKey(httpHeader.ACCEPT_LANGUAGE)))
            header.put(httpHeader.ACCEPT_LANGUAGE, "en-us,en;q=0.5");
        if (!(header.containsKey(httpHeader.KEEP_ALIVE)))
            header.put(httpHeader.KEEP_ALIVE, "300");

        // set user agent. The user agent is only set if the value does not yet exists.
        // this gives callers the opportunity, to change the user agent themselves, and
        // it will not be changed.
        if (!(header.containsKey(httpHeader.USER_AGENT))) header.put(httpHeader.USER_AGENT, userAgent);

        // set the host attribute. This is in particular necessary, if we contact another proxy
        // the host is mandatory, if we use HTTP/1.1
        if (!(header.containsKey(httpHeader.HOST))) {
            if (this.remoteProxyUse)
                header.put(httpHeader.HOST, this.savedRemoteHost);
            else
                header.put(httpHeader.HOST, this.host);
        }
        
        if (this.remoteProxyUse) {
            String remoteProxyUser = this.remoteProxyConfig.getProxyUser();
            String remoteProxyPwd  = this.remoteProxyConfig.getProxyPwd();
            if ((remoteProxyUser!=null)&&(remoteProxyUser.length()>0)) {
                header.put(httpHeader.PROXY_AUTHORIZATION,"Basic " + serverCodings.standardCoder.encodeBase64String(remoteProxyUser + ":" + remoteProxyPwd));
            }
        }

        if (!(header.containsKey(httpHeader.CONNECTION))) {
            header.put(httpHeader.CONNECTION, "close");
        }

        // advertise a little bit...
        if ( (!(header.containsKey(httpHeader.REFERER))) || (((String) header.get(httpHeader.REFERER)).trim().length() == 0)&& useYacyReferer )  {
            header.put(httpHeader.REFERER,
                    (((System.currentTimeMillis() >> 10) & 1) == 0) ?
                        "http://www.anomic.de" :
                        "http://www.yacy.net/yacy");
        }

        // stimulate zipping or not
        // we can unzip, and we will return it always as unzipped, unless not wanted
        if (header.containsKey(httpHeader.ACCEPT_ENCODING)) {
            String encoding = (String) header.get(httpHeader.ACCEPT_ENCODING);
            if (zipped) {
                if (encoding.indexOf("gzip") < 0) {
                    // add the gzip encoding
                    //System.out.println("!!! adding gzip encoding");
                    header.put(httpHeader.ACCEPT_ENCODING, "gzip,deflate" + ((encoding.length() == 0) ? "" : (";" + encoding)));
                }
            } else {
                int pos  = encoding.indexOf("gzip");
                if (pos >= 0) {
                    // remove the gzip encoding
                    //System.out.println("!!! removing gzip encoding");
                    header.put(httpHeader.ACCEPT_ENCODING, encoding.substring(0, pos) + encoding.substring(pos + 4));
                }
            }
        } else {
            if (zipped) header.put(httpHeader.ACCEPT_ENCODING, "gzip,deflate");
        }

        //header = new httpHeader(); header.put("Host", this.host); // debug

        // send request
        if ((this.remoteProxyUse) && (!(method.equals(httpHeader.METHOD_CONNECT))))
            path = (this.savedRemoteHost.endsWith("443")?"https://":"http://") + this.savedRemoteHost + path;
        serverCore.send(this.clientOutput, method + " " + path + " HTTP/1.0"); // if set to HTTP/1.1, servers give time-outs?

        // send header
        //System.out.println("***HEADER for path " + path + ": PROXY TO SERVER = " + header.toString()); // DEBUG
        Iterator i = header.keySet().iterator();
        String key;
        int count;
        char tag;
        while (i.hasNext()) {
            key = (String) i.next();
            tag = key.charAt(0);
            if ((tag != '*') && (tag != '#')) {
                count = header.keyCount(key);
                for (int j = 0; j < count; j++) {
                    serverCore.send(this.clientOutput, key + ": " + ((String) header.getSingle(key, j)).trim());
                }
                //System.out.println("#" + key + ": " + value);
            }
        }

        // send terminating line
        serverCore.send(this.clientOutput, "");
        this.clientOutput.flush();

        // this is the place where www.stern.de refuses to answer ..???
    }

    /**
    * This method GETs a page from the server.
    *
    * @param path The path to the page which should be GET.
    * @param requestHeader Prefilled httpHeader.
    * @param return Instance of response with the content.
    * @throws IOException
    */
    public response GET(String path, httpHeader requestHeader) throws IOException {
        //serverLog.logDebug("HTTPC", handle + " requested GET '" + path + "', time = " + (System.currentTimeMillis() - handle));
        try {
            boolean zipped = (!this.allowContentEncoding) ? false : httpd.shallTransportZipped(path);
            send(httpHeader.METHOD_GET, path, requestHeader, zipped);
            response r = new response(zipped);
            //serverLog.logDebug("HTTPC", handle + " returned GET '" + path + "', time = " + (System.currentTimeMillis() - handle));
            return r;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
    * This method gets only the header of a page.
    *
    * @param path The path to the page whose header should be get.
    * @param requestHeader Prefilled httpHeader.
    * @param return Instance of response with the content.
    * @throws IOException
    */
    public response HEAD(String path, httpHeader requestHeader) throws IOException {
        try {
            send(httpHeader.METHOD_HEAD, path, requestHeader, false);
            return new response(false);
            // in this case the caller should not read the response body,
            // since there is none...
        } catch (SocketException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
    * This method POSTs some data to a page.
    *
    * @param path The path to the page which the post is sent to.
    * @param requestHeader Prefilled httpHeader.
    * @param ins InputStream with the data to be posted to the server.
    * @param return Instance of response with the content.
    * @throws IOException
    */
    public response POST(String path, httpHeader requestHeader, InputStream ins) throws IOException {
        try {
            send(httpHeader.METHOD_POST, path, requestHeader, false);
            // if there is a body to the call, we would have a CONTENT-LENGTH tag in the requestHeader
            String cl = (String) requestHeader.get(httpHeader.CONTENT_LENGTH);
            int len, c;
            byte[] buffer = new byte[512];
            if (cl != null) {
                len = Integer.parseInt(cl);
                // transfer len bytes from ins to the server
                while ((len > 0) && ((c = ins.read(buffer)) >= 0)) {
                    this.clientOutput.write(buffer, 0, c);
                    len -= c;
                }
            } else {
                len = 0;
                while ((c = ins.read(buffer)) >= 0) {
                    this.clientOutput.write(buffer, 0, c);
                    len += c;
                }
                
                // TODO: we can not set the header here. This ist too late
                requestHeader.put(httpHeader.CONTENT_LENGTH, Integer.toString(len));
            }
            this.clientOutput.flush();
            return new response(false);
        } catch (SocketException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
    * Call the server with the CONNECT-method.
    *
    * @param host To which host should a connection be made?
    * @param port Which port should be connected?
    * @param requestHeader prefilled httpHeader.
    * @return Instance of response with the content.
    */
    public response CONNECT(String host, int port, httpHeader requestHeader) throws IOException {
        try {
            send(httpHeader.METHOD_CONNECT, host + ":" + port, requestHeader, false);
            return new response(false);
        } catch (SocketException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
    * This method sends several files at once via a POST request. Only those
    * files in the Hashtable files are written whose names are contained in
    * args.
    *
    * @param path The path to the page which the post is sent to.
    * @param requestHeader Prefilled httpHeader.
    * @param args serverObjects with the names of the files to send.
    * @param files HashMap with the names of the files as key and the content
    * of the files as value.
    * @return Instance of response with the content.
    * @throws IOException
    */
    public response POST(String path, httpHeader requestHeader, serverObjects args, HashMap files) throws IOException {
        // make shure, the header has a boundary information like
        // CONTENT-TYPE=multipart/form-data; boundary=----------0xKhTmLbOuNdArY
        if (requestHeader == null) requestHeader = new httpHeader();
        String boundary = (String) requestHeader.get(httpHeader.CONTENT_TYPE);
        if (boundary == null) {
            // create a boundary
            boundary = "multipart/form-data; boundary=----------" + java.lang.System.currentTimeMillis();
            requestHeader.put(httpHeader.CONTENT_TYPE, boundary);
        }
        // extract the boundary string
        int pos = boundary.toUpperCase().indexOf("BOUNDARY=");
        if (pos < 0) {
            // again, create a boundary
            boundary = "multipart/form-data; boundary=----------" + java.lang.System.currentTimeMillis();
            requestHeader.put(httpHeader.CONTENT_TYPE, boundary);
            pos = boundary.indexOf("boundary=");
        }
        boundary = "--" + boundary.substring(pos + "boundary=".length());

        boolean zipContent = args.containsKey(GZIP_POST_BODY);
        args.remove(GZIP_POST_BODY);
        
        OutputStream out;
        GZIPOutputStream zippedOut;
        serverByteBuffer buf = new serverByteBuffer();
        if (zipContent) {
            zippedOut = new GZIPOutputStream(buf);
            out = zippedOut;
        } else {
            out = buf;
        }
        
        // in contrast to GET and HEAD, this method also transports a message body
        // the body consists of repeated boundaries and values in between
        if (args.size() != 0) {
            // we have values for the POST, start with one boundary
            String key, value;
            Enumeration e = args.keys();
            while (e.hasMoreElements()) {
                // start with a boundary
                out.write(boundary.getBytes());
                out.write(serverCore.crlf);
                // write value
                key = (String) e.nextElement();
                value = args.get(key, "");
                if ((files != null) && (files.containsKey(key))) {
                    // we are about to write a file
                    out.write(("Content-Disposition: form-data; name=" + '"' + key + '"' + "; filename=" + '"' + value + '"').getBytes());
                    out.write(serverCore.crlf);
                    out.write(serverCore.crlf);
                    out.write((byte[]) files.get(key));
                    out.write(serverCore.crlf);
                } else {
                    // write a single value
                    out.write(("Content-Disposition: form-data; name=" + '"' + key + '"').getBytes());
                    out.write(serverCore.crlf);
                    out.write(serverCore.crlf);
                    out.write(value.getBytes());
                    out.write(serverCore.crlf);
                }
            }
            // finish with a boundary
            out.write(boundary.getBytes());
            out.write(serverCore.crlf);
        }
        // create body array
        out.close();
        byte[] body = buf.toByteArray();
        buf = null; out = null;
        
        //System.out.println("DEBUG: PUT BODY=" + new String(body));
        if (zipContent) {
            requestHeader.put(httpHeader.CONTENT_ENCODING, "gzip");
            
            //TODO: should we also set the content length here?
        } else {
            // size of that body            
            requestHeader.put(httpHeader.CONTENT_LENGTH, Integer.toString(body.length));
        }
        
        // send the header
        send(httpHeader.METHOD_POST, path, requestHeader, false);
        
        // send the body
        serverCore.send(this.clientOutput, body);

        return new response(false);
    }

    /*
DEBUG: PUT BODY=------------1090358578442
Content-Disposition: form-data; name="youare"

Ty2F86ekSWM5
------------1090358578442
Content-Disposition: form-data; name="key"

6EkPPOl7
------------1090358578442
Content-Disposition: form-data; name="iam"

HnTvzwV7SCJR
------------1090358578442
Content-Disposition: form-data; name="process"

permission
------------1090358578442

     */

    /*
------------0xKhTmLbOuNdArY
Content-Disposition: form-data; name="file1"; filename="dir.gif"
Content-Type: image/gif

GIF89
------------0xKhTmLbOuNdArY
Content-Disposition: form-data; name="file2"; filename=""


------------0xKhTmLbOuNdArY
Content-Disposition: form-data; name="upload"

do upload
------------0xKhTmLbOuNdArY--

###### Listing Properties ######
# METHOD=POST
### Header Values:
# EXT=html
# HTTP=HTTP/1.1
# ACCEPT-ENCODING=gzip, deflate;q=1.0, identity;q=0.5, *;q=0
# HOST=localhost:8080
# PATH=/testcgi/doit.html
# CONTENT-LENGTH=474
# CONTENT-TYPE=multipart/form-data; boundary=----------0xKhTmLbOuNdArY
# ARGC=0
# CONNECTION=close
# USER-AGENT=Mozilla/5.0 (Macintosh; U; PPC Mac OS X; de-de) AppleWebKit/103u (KHTML, like Gecko) Safari/100.1
### Call Properties:
###### End OfList ######
     */

    public static byte[] singleGET(
            String host, 
            int port, 
            String path, 
            int timeout,
            String user, 
            String password, 
            boolean ssl,
            httpRemoteProxyConfig theRemoteProxyConfig,
            httpHeader requestHeader
    ) throws IOException {
        if (requestHeader == null) requestHeader = new httpHeader();
        
        // setting host authorization header
        if ((user != null) && (password != null) && (user.length() != 0)) {
            requestHeader.put(httpHeader.AUTHORIZATION, serverCodings.standardCoder.encodeBase64String(user + ":" + password));
        }

        httpc con = null;
        try {

            if ((theRemoteProxyConfig == null)||(!theRemoteProxyConfig.useProxy())) {
                con = httpc.getInstance(host, port, timeout, ssl);
            } else {
                con = httpc.getInstance(host, port, timeout, ssl, theRemoteProxyConfig);
            }

            httpc.response res = con.GET(path, null);
            if (res.status.startsWith("2")) {
                return res.writeContent();
            }
            return res.status.getBytes();
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        } finally {
            if (con != null) httpc.returnInstance(con);
        }

    }

    public static byte[] singleGET(
            URL u, 
            int timeout,
            String user, 
            String password,
            httpRemoteProxyConfig theRemoteProxyConfig
    ) throws IOException {
        int port = u.getPort();
        boolean ssl = u.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443: 80;
        String path = u.getPath();
        String query = u.getQuery();
        if ((query != null) && (query.length() > 0)) path = path + "?" + query;
        return singleGET(u.getHost(), port, path, timeout, user, password, ssl, theRemoteProxyConfig, null);
    }

    /*
    public static byte[] singleGET(String url, int timeout) throws IOException {
        try {
            return singleGET(new URL(url), timeout, null, null, null, 0);
        } catch (MalformedURLException e) {
            throw new IOException("Malformed URL: " + e.getMessage());
        }
    }
     */

    public static byte[] singlePOST(
            String host, 
            int port, 
            String path, 
            int timeout,
            String user, 
            String password, 
            boolean ssl,
            httpRemoteProxyConfig theRemoteProxyConfig,
            httpHeader requestHeader, 
            serverObjects props
    ) throws IOException {

        if (requestHeader == null) requestHeader = new httpHeader();
        if ((user != null) && (password != null) && (user.length() != 0)) {
            requestHeader.put(httpHeader.AUTHORIZATION, serverCodings.standardCoder.encodeBase64String(user + ":" + password));
        }

        httpc con = null;
        try {
            if ((theRemoteProxyConfig == null)||(!theRemoteProxyConfig.useProxy())) {
                con = httpc.getInstance(host, port, timeout, ssl);
            } else {
                con = httpc.getInstance(host, port, timeout, ssl, theRemoteProxyConfig);
            }
            httpc.response res = con.POST(path, requestHeader, props, null);

            //System.out.println("response=" + res.toString());
            if (res.status.startsWith("2")) {
                return res.writeContent();
            }
            return res.status.getBytes();
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        } finally {
            if (con != null) httpc.returnInstance(con);
        }

    }

    public static byte[] singlePOST(
            URL u, 
            int timeout,
            String user, 
            String password,
            httpRemoteProxyConfig theRemoteProxyConfig,
            serverObjects props
    ) throws IOException {
        int port = u.getPort();
        boolean ssl = u.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
        String path = u.getPath();
        String query = u.getQuery();
        if ((query != null) && (query.length() > 0)) path = path + "?" + query;
        return singlePOST(
                u.getHost(), 
                port, 
                path, 
                timeout, 
                user, 
                password, 
                ssl, 
                theRemoteProxyConfig, 
                null, 
                props
        );
    }

    public static byte[] singlePOST(
            String url, 
            int timeout, 
            serverObjects props
    ) throws IOException {
        try {
            return singlePOST(
                    new URL(url), 
                    timeout, 
                    null, 
                    null, 
                    null, 
                    props
            );
        } catch (MalformedURLException e) {
            throw new IOException("Malformed URL: " + e.getMessage());
        }
    }

    public static ArrayList wget(
            URL url, 
            int timeout, 
            String user, 
            String password, 
            httpRemoteProxyConfig theRemoteProxyConfig
    ) throws IOException {
        return wget(url,timeout,user,password,theRemoteProxyConfig,null);
    }
    
    public static ArrayList wget(
            URL url, 
            int timeout, 
            String user, 
            String password, 
            httpRemoteProxyConfig theRemoteProxyConfig,
            httpHeader requestHeader
    ) throws IOException {
        
        int port = url.getPort();
        boolean ssl = url.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443: 80;
        String path = url.getPath();
        String query = url.getQuery();
        if ((query != null) && (query.length() > 0)) path = path + "?" + query;
        
        // splitting of the byte array into lines
        byte[] a = singleGET(
                url.getHost(), 
                port, 
                path, 
                timeout, 
                user, 
                password, 
                ssl, 
                theRemoteProxyConfig, 
                requestHeader
        );
        
        if (a == null) return null;
        int s = 0;
        int e;
        ArrayList v = new ArrayList();
        while (s < a.length) {
            e = s; while (e < a.length) if (a[e++] < 32) {e--; break;}
            v.add(new String(a, s, e - s));
            s = e; while (s < a.length) if (a[s++] >= 32) {s--; break;}
        }
        return v;
    }

    public static httpHeader whead(
            URL url, 
            int timeout, 
            String user, 
            String password, 
            httpRemoteProxyConfig theRemoteProxyConfig
    ) throws IOException {
        return whead(url,timeout,user,password,theRemoteProxyConfig,null);
    }
    
    public static httpHeader whead(
            URL url, 
            int timeout, 
            String user, 
            String password, 
            httpRemoteProxyConfig theRemoteProxyConfig,
            httpHeader requestHeader
    ) throws IOException {
        // generate request header
        if (requestHeader == null) requestHeader = new httpHeader();
        if ((user != null) && (password != null) && (user.length() != 0)) {
            requestHeader.put(httpHeader.AUTHORIZATION, serverCodings.standardCoder.encodeBase64String(user + ":" + password));
        }
        // parse query

        int port = url.getPort();
        boolean ssl = url.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
        String path = url.getPath();
        String query = url.getQuery();
        if ((query != null) && (query.length() > 0)) path = path + "?" + query;
        String host = url.getHost();

        // start connection
        httpc con = null;
        try {
            if ((theRemoteProxyConfig == null)||(!theRemoteProxyConfig.useProxy()))
                con = httpc.getInstance(host, port, timeout, ssl);
            else con = httpc.getInstance(host, port, timeout, ssl, theRemoteProxyConfig);

            httpc.response res = con.HEAD(path, requestHeader);
            if (res.status.startsWith("2")) {
                // success
                return res.responseHeader;
            }
            // fail
            return res.responseHeader;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        } finally {
            if (con != null) httpc.returnInstance(con);
        }
    }

    /*
    public static Vector wget(String url) {
        try {
            return wget(new URL(url), 5000, null, null, null, 0);
        } catch (IOException e) {
            Vector ll = new Vector();
            ll.add("503 " + e.getMessage());
            return ll;
        }
    }
     */

    public static ArrayList wput(
            URL url, 
            int timeout, 
            String user, 
            String password, 
            httpRemoteProxyConfig theRemoteProxyConfig, 
            serverObjects props
    ) throws IOException {
        // splitting of the byte array into lines
        byte[] a = singlePOST(
                url, 
                timeout, 
                user, 
                password, 
                theRemoteProxyConfig, 
                props
        );
        
        //System.out.println("wput-out=" + new String(a));
        int s = 0;
        int e;
        ArrayList v = new ArrayList();
        while (s < a.length) {
            e = s; while (e < a.length) if (a[e++] < 32) {e--; break;}
            v.add(new String(a, s, e - s));
            s = e; while (s < a.length) if (a[s++] >= 32) {s--; break;}
        }
        return v;
    }

    /*
    public static Vector wput(String url, serverObjects props) {
        try {
            return wput(url, 5000, null, null, null, 0, props);
        } catch (IOException e) {
            serverLog.logError("HTTPC", "wput exception for URL " + url + ": " + e.getMessage(), e);
            Vector ll = new Vector();
            ll.add("503 " + e.getMessage());
            return ll;
        }
    }
     */

    public static void main(String[] args) {
        System.out.println("ANOMIC.DE HTTP CLIENT v" + vDATE);
        String url = args[0];
        if (!(url.toUpperCase().startsWith("HTTP://"))) url = "http://" + url;
        ArrayList text = new ArrayList();
        if (args.length == 4) {
            int timeout = Integer.parseInt(args[1]);
            String proxyHost = args[2];
            int proxyPort = Integer.parseInt(args[3]);
            
            httpRemoteProxyConfig theRemoteProxyConfig = httpRemoteProxyConfig.init(proxyHost,proxyPort);
            try {
                text = wget(new URL(url), timeout, null, null, theRemoteProxyConfig);
            } catch (MalformedURLException e) {
                System.out.println("The url '" + url + "' is wrong.");
            } catch (IOException e) {
                System.out.println("Error loading url '" + url + "': " + e.getMessage());
            }
        } /*else {
            serverObjects post = new serverObjects();
            int p;
            for (int i = 1; i < args.length; i++) {
                p = args[i].indexOf("=");
                if (p > 0) post.put(args[i].substring(0, p), args[i].substring(p + 1));
            }
            text = wput(url, post);
        }*/
        Iterator i = text.listIterator();
        while (i.hasNext()) System.out.println((String) i.next());
    }

    /**
     * To register an open socket.
     * This adds the socket to the list of open sockets where the current thread
     * is the owner.
     * @param openedSocket the socket that should be registered
     * @return the id of the current thread
     */
    private Thread registerOpenSocket(Socket openedSocket) {
        Thread currentThread = Thread.currentThread();
        synchronized (openSocketLookupTable) {
            ArrayList openSockets = null;
            if (openSocketLookupTable.containsKey(currentThread)) {
                openSockets = (ArrayList) openSocketLookupTable.get(currentThread);
            } else {
                openSockets = new ArrayList(1);
                openSocketLookupTable.put(currentThread,openSockets);
            }
            synchronized (openSockets) {
                openSockets.add(openedSocket);
            }
            return currentThread;
        }
    }

    /**
     * Closing all sockets that were opened in the context of the thread
     * with the given thread id
     * @param threadId
     */
    public static int closeOpenSockets(Thread thread) {

        // getting all still opened sockets
        ArrayList openSockets = (ArrayList) httpc.getRegisteredOpenSockets(thread).clone();
        int closedSocketCount = 0;

        // looping through the list of sockets and close each one
        for (int socketCount = 0; socketCount < openSockets.size(); socketCount++) {
            Socket openSocket = (Socket) openSockets.get(0);
            try {
                // closing the socket
                if (!openSocket.isClosed()) {
                    openSocket.close();
                    closedSocketCount++;
                }
                // unregistering the socket
                httpc.unregisterOpenSocket(openSocket,thread);
            } catch (Exception ex) {}
        }

        return closedSocketCount;
    }

    /**
     * Unregistering the socket.
     * The socket will be removed from the list of sockets where the thread with the
     * given thread id is the owner.
     * @param closedSocket the socket that should be unregistered
     * @param threadId the id of the owner thread
     */
    public static void unregisterOpenSocket(Socket closedSocket, Thread thread) {
        synchronized (openSocketLookupTable) {
            ArrayList openSockets = null;
            if (openSocketLookupTable.containsKey(thread)) {
                openSockets = (ArrayList) openSocketLookupTable.get(thread);
                synchronized (openSockets) {
                    openSockets.remove(closedSocket);
                    if (openSockets.size() == 0) {
                        openSocketLookupTable.remove(thread);
                    }
                }
            }
        }
    }

    /**
     * Getting a list of open sockets where the current thread is
     * the owner
     * @return the list of open sockets
     */
    public static ArrayList getRegisteredOpenSockets() {
        Thread currentThread = Thread.currentThread();
        return getRegisteredOpenSockets(currentThread);
    }

    /**
     * Getting a list of open sockets where the thread with the given
     * thread id is the owner
     * @param threadId the thread id of the owner thread
     * @return the list of open sockets
     */
    public static ArrayList getRegisteredOpenSockets(Thread thread) {
        synchronized (openSocketLookupTable) {
            ArrayList openSockets = null;
            if (openSocketLookupTable.containsKey(thread)) {
                openSockets = (ArrayList) openSocketLookupTable.get(thread);
            } else {
                openSockets = new ArrayList(0);
            }
            return openSockets;
        }
    }

    /**
    * Inner Class to get the response of an http-request and parse it.
    */
    public final class response {
        // Response-Header  = Date | Pragma | Allow | Content-Encoding | Content-Length | Content-Type |
        //                    Expires | Last-Modified | HTTP-header
        /*
          Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
          1xx: Informational - Not used, but reserved for future use
          2xx: Success - The action was successfully received, understood, and accepted.
          3xx: Redirection - Further action must be taken in order to complete the request
          4xx: Client Error - The request contains bad syntax or cannot be fulfilled
          5xx: Server Error - The server failed to fulfill an apparently valid request
         */

        // header information
        public httpHeader responseHeader = null;
        public String httpVer = "HTTP/0.9";
        public String status; // the success/failure response string starting with status-code
        public int statusCode = 503;
        public String statusText = "internal error";
        private boolean gzip; // for gunzipping on-the-fly
        private String encoding;

        /**
        * Constructor for this class. Reads in the content for the given outer
        * instance and parses it.
        *
        * @param zipped true, if the content of this response is gzipped.
        * @throws IOException
        */
        public response(boolean zipped) throws IOException {

            // lets start with worst-case attributes as set-up
            this.responseHeader = new httpHeader(reverseMappingCache);
            this.statusCode = 503;
            this.statusText = "internal httpc error";
            this.status = Integer.toString(this.statusCode) + " " + this.statusText;
            this.gzip   = false;

            // check connection status
            if (httpc.this.clientInput == null) {
                // the server has meanwhile disconnected
                this.statusCode = 503;
                this.statusText = "lost connection to server";
                this.status = Integer.toString(this.statusCode) + " " + this.statusText;                
                return; // in bad mood
            }

            // reads in the http header, right now, right here
            byte[] b = serverCore.receive(httpc.this.clientInput, httpc.this.readLineBuffer, terminalMaxLength, false);
            if (b == null) {
                // the server has meanwhile disconnected
                this.statusCode = 503;
                this.statusText = "server has closed connection";
                this.status = Integer.toString(this.statusCode) + " " + this.statusText;
                return; // in bad mood
            }
            
            // parsing the response status line
            String buffer = new String(b);        
            Object[] responseInfo = httpHeader.parseResponseLine(buffer);
            this.httpVer =    (String) responseInfo[0];
            this.statusCode = ((Integer)responseInfo[1]).intValue();
            this.statusText = (String) responseInfo[2];
            this.status = this.statusCode + " " + this.statusText;
            
            if ((this.statusCode==500)&&(this.statusText.equals("status line parse error"))) {
                // flush in anything that comes without parsing
                while ((b != null) && (b.length != 0)) b = serverCore.receive(httpc.this.clientInput, httpc.this.readLineBuffer, terminalMaxLength, false);
                return; // in bad mood                
            }
                        
            // check validity
            if (this.statusCode == 400) {
                // bad request
                // flush in anything that comes without parsing
                while ((b = serverCore.receive(httpc.this.clientInput, httpc.this.readLineBuffer, terminalMaxLength, false)).length != 0) {}
                return; // in bad mood
            }

            // at this point we should have a valid response. read in the header properties
            String key = "";
            while ((b = serverCore.receive(httpc.this.clientInput, httpc.this.readLineBuffer, terminalMaxLength, false)) != null) {
                if (b.length == 0) break;
                buffer = new String(b);
                //System.out.println("#H#" + buffer); // debug
                if (buffer.charAt(0) <= 32) {
                    // use old entry
                    if (key.length() == 0) throw new IOException("header corrupted - input error");
                    // attach new line
                    if (!(this.responseHeader.containsKey(key))) throw new IOException("header corrupted - internal error");
                    this.responseHeader.put(key, (String) this.responseHeader.get(key) + " " + buffer.trim());
                } else {
                    // create new entry
                    int p = buffer.indexOf(":");
                    if (p > 0) {
                        this.responseHeader.add(buffer.substring(0, p).trim(), buffer.substring(p + 1).trim());
                    } else {
                        serverLog.logSevere("HTTPC", "RESPONSE PARSE ERROR: HOST='" + httpc.this.host + "', PATH='" + httpc.this.requestPath + "', STATUS='" + this.status + "'");
                        serverLog.logSevere("HTTPC", "..............BUFFER: " + buffer);
                    }
                }
            }
            // finished with reading header

            // we will now manipulate the header if the content is gzip encoded, because
            // reading the content with "writeContent" will gunzip on-the-fly
            this.gzip = ((zipped) && (this.responseHeader.gzip()));

            if (this.gzip) {
                this.responseHeader.remove(httpHeader.CONTENT_ENCODING); // we fake that we don't have encoding, since what comes out does not have gzip and we also don't know what was encoded
                this.responseHeader.remove(httpHeader.CONTENT_LENGTH); // we cannot use the length during gunzippig yet; still we can hope that it works
            }
        }

        /**
        * Converts an instance of this class into a readable string.
        *
        * @return String with some information about this instance.
        */
        public String toString() {
            StringBuffer toStringBuffer = new StringBuffer();
            toStringBuffer.append((this.status == null) ? "Status: Unknown" : "Status: " + this.status)
            .append(" | Headers: ")
            .append((this.responseHeader == null) ? "none" : this.responseHeader.toString());
            return toStringBuffer.toString();
        }

        /**
        * Returns wether this request was successful or not. Stati beginning
        * with 2 or 3 are considered successful.
        *
        * @return True, if the request was successfull.
        */
        public boolean success() {
            return ((this.status.charAt(0) == '2') || (this.status.charAt(0) == '3'));
        }

        /**
        * This method just output the found content into an byte-array and
        * returns it.
        *
        * @return 
        * @throws IOException 
        */
        public byte[] writeContent() throws IOException {
            int contentLength = (int) this.responseHeader.contentLength();
            serverByteBuffer sbb = new serverByteBuffer((contentLength==-1)?8192:contentLength);
            writeContentX(null, sbb, httpc.this.clientInput);
            return sbb.getBytes();
        }

        /**
        * This method outputs the found content into an byte-array and
        * additionally outputs it to procOS.
        *
        * @param procOS
        * @return 
        * @throws IOException
        */
        public byte[] writeContent(OutputStream procOS) throws IOException {
            int contentLength = (int) this.responseHeader.contentLength();
            serverByteBuffer sbb = new serverByteBuffer((contentLength==-1)?8192:contentLength);
            writeContentX(procOS, sbb, httpc.this.clientInput);
            return sbb.getBytes();
        }

        /**
        * This method writes the input stream to either another output stream
        * or a file or both.
        *
        * @param procOS
        * @param file
        * @throws IOException
        */
        public void writeContent(OutputStream procOS, File file) throws IOException {
            // this writes the input stream to either another output stream or
            // a file or both.
            FileOutputStream bufferOS = null;
            try {
                if (file != null) bufferOS = new FileOutputStream(file);
                writeContentX(procOS, bufferOS, httpc.this.clientInput);
            } finally {
                if (bufferOS != null) {
                    bufferOS.close();
                    if (file.length() == 0) file.delete();
                }
            }
        }

        /**
        * This method outputs the input stream to either an output socket or an
        * file or both. If the length of the input stream is given in the
        * header, exactly that lenght is written. Otherwise the stream is
        * written, till it is closed. If this instance is zipped, stream the
        * input stream through gzip to unzip it on the fly.
        *
        * @param procOS OutputStream where the stream is to be written. If null
        * no write happens.
        * @param bufferOS OutputStream where the stream is to be written too.
        * If null no write happens.
        * @param clientInput InputStream where the content is to be read from.
        * @throws IOException
        */
        public void writeContentX(OutputStream procOS, OutputStream bufferOS, InputStream clientInput) throws IOException {
            // we write length bytes, but if length == -1 (or < 0) then we
            // write until the input stream closes
            // procOS == null -> no write to procOS
            // file == null -> no write to file
            // If the Content-Encoding is gzip, we gunzip on-the-fly
            // and change the Content-Encoding and Content-Length attributes in the header
            byte[] buffer = new byte[2048];
            int l;
            long len = 0;

            // find out length
            long length = this.responseHeader.contentLength();

            // using the proper intput stream
            InputStream dis = (this.gzip) ? (InputStream) new GZIPInputStream(clientInput) : (InputStream) clientInput;

            // we have three methods of reading: length-based, length-based gzip and connection-close-based
            try {
                if (length > 0) {
                    // we read exactly 'length' bytes
                    while ((len < length) && ((l = dis.read(buffer)) >= 0)) {
                        if (procOS != null) procOS.write(buffer, 0, l);
                        if (bufferOS != null) bufferOS.write(buffer, 0, l);
                        len += l;
                    }
                } else {
                    // no content-length was given, thus we read until the connection closes
                    while ((l = dis.read(buffer, 0, buffer.length)) >= 0) {
                        if (procOS != null) procOS.write(buffer, 0, l);
                        if (bufferOS != null) bufferOS.write(buffer, 0, l);
                    }
                }
            } catch (java.net.SocketException e) {
                throw new IOException("Socket exception: " + e.getMessage());
            } catch (java.net.SocketTimeoutException e) {
                throw new IOException("Socket time-out: " + e.getMessage());
            } finally {
                // close the streams
                if (procOS != null) {
                    if (procOS instanceof httpChunkedOutputStream)
                        ((httpChunkedOutputStream)procOS).finish();
                    procOS.flush();
                }
                if (bufferOS != null) bufferOS.flush();
                buffer = null;
            }
        }

        /**
        * This method outputs a logline to the serverlog with the current
        * status of this instance.
        */
        public void print() {
            serverLog.logInfo("HTTPC", "RESPONSE: status=" + this.status + ", header=" + this.responseHeader.toString());
        }

    }

}

/*
import java.net.*;
import java.io.*;
import javax.net.ssl.*;
import javax.security.cert.X509Certificate;
import java.security.KeyStore;


 //The application can be modified to connect to a server outside
 //the firewall by following SSLSocketClientWithTunneling.java.

public class SSLSocketClientWithClientAuth {

    public static void main(String[] args) throws Exception {
        String host = null;
        int port = -1;
        String path = null;
        for (int i = 0; i < args.length; i++)
            System.out.println(args[i]);

        if (args.length < 3) {
            System.out.println(
                "USAGE: java SSLSocketClientWithClientAuth " +
                "host port requestedfilepath");
            System.exit(-1);
        }

        try {
            host = args[0];
            port = Integer.parseInt(args[1]);
            path = args[2];
        } catch (IllegalArgumentException e) {
             System.out.println("USAGE: java SSLSocketClientWithClientAuth " +
                 "host port requestedfilepath");
             System.exit(-1);
        }

        try {

            SSLSocketFactory factory = null;
            try {
                SSLContext ctx;
                KeyManagerFactory kmf;
                KeyStore ks;
                char[] passphrase = "passphrase".toCharArray();

                ctx = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance("SunX509");
                ks = KeyStore.getInstance("JKS");

                ks.load(new FileInputStream("testkeys"), passphrase);

                kmf.init(ks, passphrase);
                ctx.init(kmf.getKeyManagers(), null, null);

                factory = ctx.getSocketFactory();
            } catch (Exception e) {
                throw new IOException(e.getMessage());
            }

            SSLSocket socket = (SSLSocket)factory.createSocket(host, port);

            socket.startHandshake();

            PrintWriter out = new PrintWriter(
                                  new BufferedWriter(
                                  new OutputStreamWriter(
                                  socket.getOutputStream())));
            out.println("GET " + path + " HTTP/1.1");
            out.println();
            out.flush();

            if (out.checkError())
                System.out.println(
                    "SSLSocketClient: java.io.PrintWriter error");

            BufferedReader in = new BufferedReader(
                                    new InputStreamReader(
                                    socket.getInputStream()));

            String inputLine;

            while ((inputLine = in.readLine()) != null)
                System.out.println(inputLine);

            in.close();
            out.close();
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
 */

final class httpcFactory implements org.apache.commons.pool.PoolableObjectFactory {

    public httpcFactory() {
        super();
    }

    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#makeObject()
     */
    public Object makeObject() throws Exception {
        return new httpc();
    }

    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#destroyObject(java.lang.Object)
     */
    public void destroyObject(Object obj) {
        if (obj instanceof httpc) {
            httpc theHttpc = (httpc) obj;

            theHttpc.removedFromPool = true;
        }
    }

    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#validateObject(java.lang.Object)
     */
    public boolean validateObject(Object obj) {
        if (obj instanceof httpc) {
            httpc theHttpc = (httpc) obj;
            return true;
        }
        return true;
    }

    /**
     * @param obj
     *
     */
    public void activateObject(Object obj)  {
        //log.debug(" activateObject...");
    }

    /**
     * @param obj
     *
     */
    public void passivateObject(Object obj) {
        //log.debug(" passivateObject..." + obj);
        if (obj instanceof Session)  {
            httpc theHttpc = (httpc) obj;
        }
    }
}

final class httpcPool extends GenericObjectPool {
    /**
     * First constructor.
     * @param objFactory
     */
    public httpcPool(httpcFactory objFactory) {
        super(objFactory);
        this.setMaxIdle(75); // Maximum idle threads.
        this.setMaxActive(150); // Maximum active threads.
        this.setMinEvictableIdleTimeMillis(30000); //Evictor runs every 30 secs.
        //this.setMaxWait(1000); // Wait 1 second till a thread is available
    }

    public httpcPool(httpcFactory objFactory,
            GenericObjectPool.Config config) {
        super(objFactory, config);
    }

    /**
     * @see org.apache.commons.pool.impl.GenericObjectPool#borrowObject()
     */
    public Object borrowObject() throws Exception  {
        return super.borrowObject();
    }

    /**
     * @see org.apache.commons.pool.impl.GenericObjectPool#returnObject(java.lang.Object)
     */
    public void returnObject(Object obj) throws Exception  {
        super.returnObject(obj);
    }
}
