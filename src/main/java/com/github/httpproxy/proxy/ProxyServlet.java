package com.github.httpproxy.proxy;


import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.EntityUtils;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.Formatter;

/**
 * HTTP反向代理/网关servlet。可扩展以进行定制
 */
public class ProxyServlet extends HttpServlet {

    /**
     * 用于将输入和目标url记录到servlet日志
     */
    public static final String P_LOG = "log";

    /**
     * 启用客户端IP转发功能
     */
    public static final String P_FORWARDEDFOR = "forwardip";

    /**
     * 保持HOST参数不变
     */
    public static final String P_PRESERVEHOST = "preserveHost";

    /**
     * 保持cookie原样
     */
    public static final String P_PRESERVECOOKIES = "preserveCookies";

    /**
     * 保持COOKIE路径不变
     */
    public static final String P_PRESERVECOOKIEPATH = "preserveCookiePath";

    /**
     * 自动处理重定向
     */
    public static final String P_HANDLEREDIRECTS = "http.protocol.handle-redirects"; // ClientPNames.HANDLE_REDIRECTS

    /**
     * 设置套接字连接超时时间 (millis)
     */
    public static final String P_CONNECTTIMEOUT = "http.socket.timeout"; // CoreConnectionPNames.SO_TIMEOUT

    /**
     * 设置套接字读取超时时间 (millis)
     */
    public static final String P_READTIMEOUT = "http.read.timeout";

    /**
     * 设置连接请求超时时间
     */
    public static final String P_CONNECTIONREQUESTTIMEOUT = "http.connectionrequest.timeout";

    /**
     * 设置最大连接数
     */
    public static final String P_MAXCONNECTIONS = "http.maxConnections";

    /**
     * 使用jvm定义的系统属性来配置各种网络切面
     */
    public static final String P_USESYSTEMPROPERTIES = "useSystemProperties";

    /**
     * 在servlet中启用压缩处理， 如果为false，则压缩流不加修改地通过
     */
    public static final String P_HANDLECOMPRESSION = "handleCompression";

    /**
     * 要代理到的目标(目的地)URI的参数名称
     */
    public static final String P_TARGET_URI = "targetUri";

    protected static final String ATTR_TARGET_URI =
            ProxyServlet.class.getSimpleName() + ".targetUri";

    protected static final String ATTR_TARGET_HOST =
            ProxyServlet.class.getSimpleName() + ".targetHost";


    protected boolean doLog = false;
    protected boolean doForwardIP = true;

    protected boolean doSendUrlFragment = true;
    protected boolean doPreserveHost = false;
    protected boolean doPreserveCookies = false;
    protected boolean doPreserveCookiePath = false;
    protected boolean doHandleRedirects = false;
    protected boolean useSystemProperties = true;
    protected boolean doHandleCompression = false;
    protected int connectTimeout = -1;
    protected int readTimeout = -1;
    protected int connectionRequestTimeout = -1;
    protected int maxConnections = -1;
    /**
     * 接下来的3个缓存在这里，应该只在初始化逻辑中引用
     */
    protected String targetUri;
    protected URI targetUriObj; //new URI(targetUri)
    protected HttpHost targetHost; // URIUtils.extractHost(targetUriObj);

    private HttpClient proxyClient;

    @Override
    public String getServletInfo() {
        return "A proxy servlet by lxhcaicai";
    }

    protected String getTargetUri(HttpServletRequest servletRequest) {
        return (String) servletRequest.getAttribute(ATTR_TARGET_URI);
    }

    protected HttpHost getTargetHost(HttpServletRequest servletRequest) {
        return (HttpHost) servletRequest.getAttribute(ATTR_TARGET_HOST);
    }

    /**
     * 需要一个配置参数。默认情况下，它读取servlet初始化参数,但是它能够被覆盖
     */
    protected String getConfigParam(String key) {
        return getServletConfig().getInitParameter(key);
    }

    /**
     *  重写 javax.servlet.GenericServlet的 init方法
     * @throws ServletException
     */
    @Override
    public void init() throws ServletException {
        String doLogStr = getConfigParam(P_LOG);
        if(doLogStr != null) {
            this.doLog = Boolean.parseBoolean(doLogStr);
        }

        String doForwardIPString = getConfigParam(P_FORWARDEDFOR);
        if(doForwardIPString != null) {
            this.doForwardIP = Boolean.parseBoolean(doForwardIPString);
        }

        String preserveHostString = getConfigParam(P_PRESERVEHOST);
        if(preserveHostString != null) {
            this.doPreserveHost = Boolean.parseBoolean(preserveHostString);
        }

        String preserveCookiesString = getConfigParam(P_PRESERVECOOKIES);
        if(preserveCookiesString != null) {
            this.doPreserveCookies = Boolean.parseBoolean(preserveCookiesString);
        }

        String preserveCookiePathString = getConfigParam(P_PRESERVECOOKIEPATH);
        if(preserveCookiePathString != null) {
            this.doPreserveCookiePath = Boolean.parseBoolean(preserveCookiePathString);
        }

        String handleRedirectsString = getConfigParam(P_HANDLEREDIRECTS);
        if(handleRedirectsString != null) {
            this.doHandleRedirects = Boolean.parseBoolean(handleRedirectsString);
        }

        String connectTimeoutString = getConfigParam(P_CONNECTTIMEOUT);
        if(connectTimeoutString != null) {
            this.connectTimeout = Integer.parseInt(connectTimeoutString);
        }

        String readTimeoutString = getConfigParam(P_READTIMEOUT);
        if(readTimeoutString != null) {
            this.readTimeout = Integer.parseInt(readTimeoutString);
        }

        String connectionRequestTimeout = getConfigParam(P_CONNECTIONREQUESTTIMEOUT);
        if(connectionRequestTimeout != null) {
            this.connectionRequestTimeout = Integer.parseInt(connectionRequestTimeout);
        }

        String maxConnections = getConfigParam(P_MAXCONNECTIONS);
        if(maxConnections != null) {
            this.maxConnections = Integer.parseInt(maxConnections);
        }

        String useSystemPropertiesString = getConfigParam(P_USESYSTEMPROPERTIES);
        if(useSystemPropertiesString != null) {
            this.useSystemProperties = Boolean.parseBoolean(useSystemPropertiesString);
        }

        String doHandleCompression = getConfigParam(P_HANDLECOMPRESSION);
        if(doHandleCompression != null) {
            this.doHandleCompression = Boolean.parseBoolean(doHandleCompression);
        }

        initTarget(); // sets targets*

        proxyClient = createHttpClient();
    }

    protected void initTarget() throws ServletException {
        targetUri = getConfigParam(P_TARGET_URI);
        if(targetUri == null) {
            throw new ServletException(P_TARGET_URI + "is required.");
        }
        // 测试是否有效
        try {
            targetUriObj = new URI(targetUri);
        } catch (Exception e) {
            throw new ServletException("Trying to process targetUri init parameter: "+e,e);
        }
        targetHost = URIUtils.extractHost(targetUriObj);
    }

    /**
     * Called from { #init(jakarta.servlet.ServletConfig)}.
     * HttpClient提供了许多定制的机会
     * 在任何情况下，应该是线程安全的
     * @return
     */
    protected HttpClient createHttpClient() {
        HttpClientBuilder clientBuilder = getHttpClientBuilder()
                .setDefaultRequestConfig(buildRequestConfig())
                .setDefaultSocketConfig(buildSocketConfig());

        clientBuilder.setMaxConnTotal(maxConnections);
        clientBuilder.setMaxConnPerRoute(maxConnections);

        if(! doHandleCompression) {
            clientBuilder.disableContentCompression();
        }

        if(useSystemProperties) {
            clientBuilder = clientBuilder.useSystemProperties();
        }
        return buildHttpClient(clientBuilder);
    }

    /**
     * 在应用任何配置之前调整客户机构建
     * @return
     */
    protected HttpClientBuilder getHttpClientBuilder()  {
        return HttpClientBuilder.create();
    }

    /**
     * 子类可以覆盖特定的行为
     * @return
     */
    protected RequestConfig buildRequestConfig() {
        return RequestConfig.custom()
                .setRedirectsEnabled(doHandleRedirects)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(readTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .build();
    }

    /**
     * 子类可以覆盖特定的行为
     * @return
     */
    protected SocketConfig buildSocketConfig() {
        if(readTimeout < 1) {
            return null;
        }
        return SocketConfig.custom()
                .setSoTimeout(readTimeout)
                .build();
    }

    /**
     * 来客户端构建器
     * @param clientBuilder
     * @return
     */
    protected HttpClient buildHttpClient(HttpClientBuilder clientBuilder) {
        return clientBuilder.build();
    }

    protected HttpClient getProxyClient() {
        return proxyClient;
    }

    @Override
    public void destroy() {
        // 客户端实现Closeable:
        if(proxyClient instanceof Closeable) {
            try {
                ((Closeable) proxyClient).close();
            } catch (IOException e) {
                log("While destroying servlet, shutting down HttpClient: "+e, e);
            }
        } else {
            // 老版本要求我们这样做:
            if(proxyClient != null) {
                proxyClient.getConnectionManager().shutdown();
            }
        }
        super.destroy();
    }

    @Override
    protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
            throws ServletException, IOException {
        // 如果子类此时未设置请求属性，则从缓存初始化请求属性
        if(servletRequest.getAttribute(ATTR_TARGET_URI) == null) {
            servletRequest.setAttribute(ATTR_TARGET_URI,targetUri);
        }

        if(servletRequest.getAttribute(ATTR_TARGET_HOST) == null) {
            servletRequest.setAttribute(ATTR_TARGET_HOST,targetHost);
        }

        // 我们不会转移协议版本，有可能不兼容
        String method = servletRequest.getMethod();
        String proxyRequestUri = rewriteUrlFromRequest(servletRequest);
        HttpRequest proxyRequest;

        if(servletRequest.getHeader(HttpHeaders.CONTENT_LENGTH) != null ||
                servletRequest.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
            proxyRequest = newProxyRequestWithEntity(method,proxyRequestUri,servletRequest);
        } else {
            proxyRequest = new BasicHttpRequest(method, proxyRequestUri);
        }

        copyRequestHeaders(servletRequest, proxyRequest);

        setXForwardedForHeader(servletRequest, proxyRequest);

        HttpResponse proxyResponse = null;
        try {
            // 执行请求
            proxyResponse = doExecute(servletRequest, servletResponse, proxyRequest);

            // 处理响应
            int statusCode = proxyResponse.getStatusLine().getStatusCode();
            servletResponse.setStatus(statusCode);
            // 复制响应头以确保来自远程的SESSIONID或其他Coo
            // 当代理url被重定向到另一个url时，服务器将保存在客户端
            copyResponseHeaders(proxyResponse,servletRequest,servletResponse);

            if(statusCode == HttpServletResponse.SC_NOT_MODIFIED) {
                // 304需要特殊处理
                servletResponse.setIntHeader(HttpHeaders.CONTENT_LENGTH, 0);
            } else {
                // 将内容发送到客户端
                copyResponseEntity(proxyResponse, servletResponse, proxyRequest, servletRequest);
            }

        } catch (Exception e) {
            handleRequestException(proxyRequest, proxyResponse, e);
        } finally {
            // 确保整个实体都被使用了，这样连接就会被释放
            if(proxyResponse != null) {
                EntityUtils.consumeQuietly(proxyResponse.getEntity());
                // 不需要关闭servlet outputStream
            }
        }

    }

    //从代理复制响应体数据(实体)到servlet客户端
    protected void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponse servletResponse,
                                      HttpRequest httpRequest, HttpServletRequest servletRequest) throws IOException {

        HttpEntity entity = proxyResponse.getEntity();
        if(entity != null) {
            if(entity.isChunked()) {
                //  在阻塞输入之前刷新中间结果——SSE
                InputStream is = entity.getContent();
                OutputStream os = servletResponse.getOutputStream();
                byte[] buffer = new byte[10 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer,0,read);
                    /**
                     * Apache http client/JDK的问题: 如果来自客户端的流是压缩的.
                     * apache http客户端将委托给GzipInputStream。
                     * flaterinputstream的#可用实现GzipInputStream)返回1，直到EOF。
                     *
                     * 这不是与InputStream#一致， 有如下关系：
                     *单次读取或跳过这么多字节不会阻塞，
                     *但可能读取或跳过更少的字节。
                     *
                     *为了解决这个问题，总是在压缩时清空
                     */
                    if(doHandleCompression || is.available() == 0) {
                        os.flush();
                    }
                }
                // 实体关闭/清理在调用者中完成(#service)
            } else {
                OutputStream servletOutputStream = servletResponse.getOutputStream();
                entity.writeTo(servletOutputStream);
            }
        }
    }

    protected void handleRequestException(HttpRequest proxyRequest, HttpResponse proxyResponse, Exception e)
            throws ServletException,IOException {
        // 中止请求，根据HttpClient的最佳实践
        if(proxyResponse instanceof AbortableHttpRequest) {
            AbortableHttpRequest abortableHttpRequest = (AbortableHttpRequest) proxyRequest;
            abortableHttpRequest.abort();
        }

        //如果响应是块响应，则读取到完成
        // #close被调用如果发送站点没有超时或继续发送，
        //连接将无限期保持打开状态。关闭响应
        //对象终止流。
        if(proxyResponse instanceof Closeable) {
            ((Closeable) proxyResponse).close();
        }
        if(e instanceof RuntimeException) {
            throw (RuntimeException)e;
        }
        if(e instanceof IOException) {
            throw (IOException) e;
        }
        throw new RuntimeException(e);
    }

    protected HttpResponse doExecute(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
                                     HttpRequest proxyRequest) throws IOException {
        if(doLog) {
            log("proxy " + servletRequest.getMethod() + " uri: " + servletRequest.getRequestURI() + " -- " +
                    proxyRequest.getRequestLine().getUri());
        }
        return proxyClient.execute(getTargetHost(servletRequest), proxyRequest);
    }

    /**
     * 将请求头从servlet客户机复制到代理请求。
     * @param servletRequest
     * @param proxyRequest
     */
    protected void copyRequestHeaders(HttpServletRequest servletRequest, HttpRequest proxyRequest) {
        // 获取客户端发送的所有报头名称的枚举
        Enumeration<String> enumerationOfHeaderNames = servletRequest.getHeaderNames();
        while (enumerationOfHeaderNames.hasMoreElements()) {
            String headerName = enumerationOfHeaderNames.nextElement();
            copyRequestHeader(servletRequest, proxyRequest, headerName);
        }

    }

    protected void copyResponseHeaders(HttpResponse proxyResponse, HttpServletRequest servletRequest,
                                       HttpServletResponse servletResponse) {

        for(Header header: proxyResponse.getAllHeaders()) {
            copyResponseHeader(servletRequest, servletResponse,header);
        }
    }

    /**
     * 将代理响应头复制回servlet客户端。
     * 如果需要，这很容易被覆盖以过滤掉某些头。
     * @param servletRequest
     * @param servletResponse
     * @param header
     */
    protected void copyResponseHeader(HttpServletRequest servletRequest,
                                      HttpServletResponse servletResponse,
                                      Header header) {

        String headerName = header.getName();
        if(hopByHopHeaders.containsHeader(headerName)) {
            return;
        }
        String headerValue = header.getValue();
        if(headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE)
                || headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE2)) {
            copyProxyCookie(servletRequest, servletResponse, headerValue);
        } else if(headerName.equalsIgnoreCase(HttpHeaders.LOCATION)) {
            // LOCATION头可能需要重写。
            servletResponse.addHeader(headerName, rewriteUrlFromResponse(servletRequest, headerValue));
        } else {
            servletResponse.addHeader(headerName, headerValue);
        }
    }

    /**
     * 将cookie从代理复制到servlet客户端。
     * 将cookie路径替换为本地路径并重命名cookie以避免冲突。
     * @param servletRequest
     * @param servletResponse
     * @param headerValue
     */
    protected void copyProxyCookie(HttpServletRequest servletRequest,
                                   HttpServletResponse servletResponse, String headerValue) {
        for(HttpCookie cookie:HttpCookie.parse(headerValue)) {
            Cookie servletCookie = createProxyCookie(servletRequest, cookie);
            servletResponse.addCookie(servletCookie);
        }
    }

    /**
     * 对于来自目标服务器的重定向响应，这将把 theUrl转换为重定向
     * 并将其转换为原始客户端可以使用的格式。
     */
    protected String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl) {
        final String targetUri = getTargetUri(servletRequest);
        if(theUrl.startsWith(targetUri)) {
            /*
             *  URL指向后端服务器。
             * 我们将目标路径替换为我们的源路径的方式应该指示原始客户端
             * 请求通过代理指向的URL。
             * 我们通过获取当前请求并重写路径部分来实现这一点
             * 使用servlet的绝对路径和返回URL的路径在基本目标URL后面
             */
            StringBuffer curUrl = servletRequest.getRequestURL();
            int pos;
            //  跳过协议部分
            if((pos = curUrl.indexOf("://"))>= 0 ) {
                // + 3跳过协议和权限之间的分隔符
                if((pos = curUrl.indexOf("/", pos + 3)) >= 0) {
                    // 修剪鉴权部分之后的所有内容。
                    curUrl.setLength(pos);
                }
            }
            // 如果上下文路径不为空，则以“/”开头
            curUrl.append(servletRequest.getContextPath());
            // 如果Servlet路径不为空，则以/开头
            curUrl.append(servletRequest.getServletPath());
            curUrl.append(theUrl,targetUri.length(), theUrl.length());
            return curUrl.toString();
        }
        return theUrl;
    }

    /**
     * 从原始cookie创建代理cookie。
     * @param servletRequest
     * @param cookie
     * @return
     */
    protected Cookie createProxyCookie(HttpServletRequest servletRequest, HttpCookie cookie) {

        String proxyCookieName = getProxyCookieName(cookie);
        Cookie servletCookie = new Cookie(proxyCookieName, cookie.getValue());
        servletCookie.setPath(this.doPreserveCookiePath ?
                    cookie.getPath(): // 保留原始cookie路径
                    buildProxyCookiePath(servletRequest) // 设置为代理servlet的路径
        );
        servletCookie.setComment(cookie.getComment());
        servletCookie.setMaxAge((int) cookie.getMaxAge());
        // 不要设置cookie域
        servletCookie.setSecure(servletRequest.isSecure() && cookie.getSecure());
        servletCookie.setVersion(cookie.getVersion());
        servletCookie.setHttpOnly(cookie.isHttpOnly());
        return servletCookie;
    }


    /**
     * 为代理cookie创建路径。
     * @param servletRequest 原始请求
     * @return 代理cookie路径
     */
    protected String buildProxyCookiePath(HttpServletRequest servletRequest) {
        String path = servletRequest.getContextPath(); // 路径以/或开头是空字符串
        path += servletRequest.getServletPath(); // Servlet路径以/或开头是空字符串
        if(path.isEmpty()) {
            path = "/";
        }
        return path;
    }

    /**
     * 设置cookie名称前缀为代理值，这样它就不会与其他cookie冲突。
     * @param cookie 获取代理Cookie名称的Cookie
     * @return 不冲突的代理cookie名称
     */
    protected String getProxyCookieName(HttpCookie cookie) {
        return doPreserveCookies? cookie.getName(): getCookieNamePrefix(cookie.getName()) + cookie.getName();
    }

    private void setXForwardedForHeader(HttpServletRequest servletRequest,
                                        HttpRequest proxyRequest) {
        if(doForwardIP) {
            String forHeaderName ="X-Forwarded-For";
            String forHeader = servletRequest.getRemoteAddr();
            String existingForHeader = servletRequest.getHeader(forHeaderName);
            if(existingForHeader != null) {
                forHeader = existingForHeader + ", " + forHeader;
            }
            proxyRequest.setHeader(forHeaderName, forHeader);

            String protoHeaderName = "X-Forwarded-Proto";
            String protoHeader = servletRequest.getScheme();
            proxyRequest.setHeader(protoHeaderName, protoHeader);
        }

    }

    // 将请求头从servlet客户机复制到代理请求
    // 如果需要，可以很容易地覆盖它以过滤掉某些标头。
    protected void copyRequestHeader(HttpServletRequest servletRequest, HttpRequest proxyRequest,
                                     String headerName) {
        // 相反，内容长度是通过InputStreamEntity有效设置的
        if(headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)){
            return;
        }
        if(hopByHopHeaders.containsHeader(headerName)) {
            return;
        }
        // 如果压缩是在servlet中处理的，apache http客户端需要控制Accept-Encoding ，而不是客户端
        if(doHandleCompression && headerName.equalsIgnoreCase(HttpHeaders.ACCEPT_ENCODING)) {
            return;
        }

        Enumeration<String> headers = servletRequest.getHeaders(headerName);
        while (headers.hasMoreElements()) {
            String headerValue = headers.nextElement();
            //如果代理主机运行多个虚拟服务器，
            //重写Host头以确保我们从
            //正确的虚拟服务器
            if(!doPreserveHost && headerName.equalsIgnoreCase(HttpHeaders.HOST)) {
                HttpHost host = getTargetHost(servletRequest);
                headerValue = host.getHostName();
                if(host.getPort() != -1)
                    headerValue += ":" + host.getPort();
            } else if(!doPreserveCookies && headerName.equalsIgnoreCase(org.apache.http.cookie.SM.COOKIE)) {
                headerValue = getRealCookie(headerValue);
            }
            proxyRequest.addHeader(headerName, headerValue);
        }
    }

    /**
     * 获取最初来自代理的任何客户端cookie，并准备将它们发送到代理。
     * 这依赖于根据RFC 6265 Sec 5.4正确设置的cookie头。
     * 这也阻止任何本地cookie被发送到代理。
     * @param cookieValue
     * @return
     */
    protected String getRealCookie(String cookieValue) {
        StringBuilder escapedCookie = new StringBuilder();
        String cookies[] = cookieValue.split("[;,]");
        for (String cookie: cookies) {
            String cookueSplit[] = cookie.split("=");
            if(cookueSplit.length == 2) {
                String cookieName = cookueSplit[0].trim();
                if(cookieName.startsWith(getCookieNamePrefix(cookieName))) {
                    cookieName = cookieName.substring(getCookieNamePrefix(cookieName).length());
                    if(escapedCookie.length() > 0) {
                        escapedCookie.append("; ");
                    }
                    escapedCookie.append(cookieName).append("=").append(cookueSplit[1].trim());
                }
            }
        }
        return escapedCookie.toString();
    }

    // 字符串前缀改写cookie
    protected String getCookieNamePrefix(String name) {
        return "!Proxy!" + getServletConfig().getServletName();
    }



    /**
     * 我使用HttpClient HeaderGroup类而不是Set String,因为这
     * 方法可以更快地进行不区分大小写的查找。
     */
    protected static final HeaderGroup hopByHopHeaders;
    static {
        hopByHopHeaders = new HeaderGroup();
        String[] headers = new String[] {
                "Connection", "Keep-Alive","Proxy-Authenticate","Proxy-Authorization",
                "TE","Trailers","Transfer-Encoding","Upgrade"
        };
        for(String header: headers) {
            hopByHopHeaders.addHeader(new BasicHeader(header, null));
        }
    }

    /**
     * 重写用来发出新的请求。
     * @param servletRequest
     * @return
     */
    protected String rewriteUrlFromRequest(HttpServletRequest servletRequest) {
        StringBuilder uri = new StringBuilder(500);
        uri.append(getTargetUri(servletRequest));
        // 处理给定给servlet的路径
        String pathInfo = rewritePathInfoFromRequest(servletRequest);
        if(pathInfo != null) {
            // 返回经过解码的字符串，因此我们需要encodeUriQuery对“%”字符进行编码
            uri.append(encodeUriQuery(pathInfo, true));
        }

        // 处理 请求字符串和片段
        String queryString = servletRequest.getQueryString(); //ex:(following '?'): name=value&foo=bar#fragment
        String fragment = null;

        // 从queryString中分离fragment，如果找到，更新queryString
        if(queryString != null) {
            int fragIdx = queryString.indexOf('#');
            if(fragIdx >= 0) {
                fragment = queryString.substring(fragIdx + 1);
                queryString = queryString.substring(0, fragIdx);
            }
        }

        queryString = rewriteQueryStringFromRequest(servletRequest, queryString);
        if(queryString != null && queryString.length() > 0) {
            uri.append('?');
            // fragment没有被解码，所以我们需要encodeUriQuery不编码“%”字符，以避免双重编码
            uri.append(encodeUriQuery(fragment, false));
        }

        if(doSendUrlFragment && fragment != null) {
            uri.append('#');
            // fragment没有被解码，所以我们需要encodeUriQuery不编码“%”字符，以避免双重编码
            uri.append(encodeUriQuery(fragment, false));
        }
        return uri.toString();
    }

    // 当servlet映射(web.xml)的url模式需要操作时有用。
    protected String rewritePathInfoFromRequest(HttpServletRequest servletRequest) {
        return servletRequest.getPathInfo();
    }

    protected String rewriteQueryStringFromRequest(HttpServletRequest servletRequest, String queryString) {
        return queryString;
    }

    /**
     * 对URI的查询或片段部分中的字符进行编码
     * 传入的URI有时包含规范不允许的字符，httpclient 发送的是请求有效的阿url
     * 为了更加便捷，我们必须避开有问题的字符
     * @param in example: name=value&amp;foo=bar#fragment
     * @param encodePercent encodePercent确定百分比字符是否需要编码
     * @return
     */
    protected CharSequence encodeUriQuery(CharSequence in, boolean encodePercent) {

        StringBuilder outBuf = null;
        Formatter formatter = null;
        for(int i = 0; i < in.length(); i ++) {
            char c = in.charAt(i);
            boolean escape = true;
            if(c < 128) {
                if(asciiQueryChars.get(c) && !(encodePercent && c == '%')) {
                    escape = false;
                }
            } else if(!Character.isISOControl(c) && !Character.isSpaceChar(c)) {  // 不是 aiisc
                escape = false;
            }

            if(!escape) {
                if(outBuf  != null) {
                    outBuf.append(c);
                }
            } else {
                // escape
                if(outBuf == null) {
                    outBuf = new StringBuilder(in.length() + 5 * 3);
                    outBuf.append(in,0,i);
                    formatter = new Formatter(outBuf);
                }
                formatter.format("%%%02X", (int)c);
            }
        }

        return outBuf != null?outBuf:in;
    }

    protected HttpRequest newProxyRequestWithEntity(String method, String proxyRequestUri,
                                                    HttpServletRequest servletRequest) throws IOException {
        HttpEntityEnclosingRequest eProxyRequest =
                new BasicHttpEntityEnclosingRequest(method, proxyRequestUri);

        eProxyRequest.setEntity(
                new InputStreamEntity(servletRequest.getInputStream(), getContentLength(servletRequest))
        );

        return eProxyRequest;
    }

    // 获取报头值以便更正确地代理非常大的请求
    private Long getContentLength(HttpServletRequest request) {
        String contentLengthHeader = request.getHeader("Content-Length");
        if(contentLengthHeader != null) {
            return Long.parseLong(contentLengthHeader);
        }
        return -1L;
    }

    protected static final BitSet asciiQueryChars;
    static {
        char[] c_unreserved = "_-!.~'()*".toCharArray();
        char[] c_punct = ",;:$&+=".toCharArray();
        char[] c_reserved = "/@".toCharArray();//plus punct.  Exclude '?'; RFC-2616 3.2.2. Exclude '[', ']'; https://www.ietf.org/rfc/rfc1738.txt, unsafe characters
        asciiQueryChars = new BitSet(128);
        for(char c = 'a'; c <= 'z'; c++) asciiQueryChars.set(c);
        for(char c = 'A'; c <= 'Z'; c++) asciiQueryChars.set(c);
        for(char c = '0'; c <= '9'; c++) asciiQueryChars.set(c);
        for(char c : c_unreserved) asciiQueryChars.set(c);
        for(char c : c_punct) asciiQueryChars.set(c);
        for(char c : c_reserved) asciiQueryChars.set(c);

        asciiQueryChars.set('%'); // 保留现有的%转义
    }

}
