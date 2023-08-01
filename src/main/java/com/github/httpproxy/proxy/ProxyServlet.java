package com.github.httpproxy.proxy;


import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;

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
    protected boolean doSystemProperties = true;
    protected boolean doHandleCompression = false;
    protected int connectTimeout = -1;

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


}
