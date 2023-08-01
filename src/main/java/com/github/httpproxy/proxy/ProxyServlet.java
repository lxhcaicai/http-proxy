package com.github.httpproxy.proxy;


import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;

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
}
