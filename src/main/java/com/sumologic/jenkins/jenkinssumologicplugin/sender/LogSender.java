package com.sumologic.jenkins.jenkinssumologicplugin.sender;

import com.sumologic.jenkins.jenkinssumologicplugin.PluginDescriptorImpl;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.lang.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static com.sumologic.jenkins.jenkinssumologicplugin.constants.SumoConstants.CARBON_CONTENT_TYPE;
import static com.sumologic.jenkins.jenkinssumologicplugin.constants.SumoConstants.GRAPHITE_CONTENT_TYPE;


/**
 * Created by Lukasz on 20/03/2017
 */
public class LogSender {
    public final static Logger LOG = Logger.getLogger(LogSender.class.getName());

    private final HttpClient httpClient;

    private LogSender() {
        MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
        httpClient = new HttpClient(connectionManager);
        httpClient.getParams().setParameter("http.protocol.max-redirects", 10);
    }

    private String getHost() {
        String hostName = "unknown";
        try {
            if (PluginDescriptorImpl.getInstance() != null && PluginDescriptorImpl.getInstance().getMetricDataPrefix() != null) {
                hostName = PluginDescriptorImpl.getInstance().getMetricDataPrefix();
            } else {
                hostName = InetAddress.getLocalHost().getHostName();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't resolve jenkins host name... Using unknown.");
        }
        return hostName;
    }

    private static class LogSenderHolder {
        static LogSender logSender = new LogSender();
    }

    public static LogSender getInstance() {
        return LogSenderHolder.logSender;
    }

    void sendLogs(String url, byte[] msg, String sumoName, String sumoCategory, String contentType, HashMap<String, String> fields, String host) {
        PostMethod post = null;

        if (StringUtils.isBlank(url)) {
            LOG.log(Level.WARNING, "Trying to send logs with blank url. Update config first!");
            return;
        }

        try {
            post = new PostMethod(url);

            createHeaders(post, sumoName, sumoCategory, contentType, fields, host);

            byte[] compressedData = compress(msg);

            post.setRequestEntity(new ByteArrayRequestEntity(compressedData));
            httpClient.executeMethod(post);
            int statusCode = post.getStatusCode();
            if (statusCode != 200) {
                LOG.log(Level.WARNING, String.format("Received HTTP error from Sumo Service: %d", statusCode));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, String.format("Could not send log to Sumo Logic: %s", e.toString()));
        } finally {
            if (post != null) {
                post.releaseConnection();
            }
        }
    }

    public void sendLogs(String url, byte[] msg, String sumoName, String sumoCategory) {
        sendLogs(url, msg, sumoName, sumoCategory, null, null, null);
    }

    public void sendLogs(String url, byte[] msg, String sumoName, String sumoCategory, String contentType) {
        sendLogs(url, msg, sumoName, sumoCategory, contentType, null, null);
    }

    private byte[] compress(byte[] content) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
        gzipOutputStream.write(content);
        gzipOutputStream.close();

        return byteArrayOutputStream.toByteArray();
    }

    private void createHeaders(final PostMethod post, final String sumoName,
                               final String sumoCategory, final String contentType,
                               HashMap<String, String> fields, final String host) {
        if (StringUtils.isNotEmpty(host)) {
            post.addRequestHeader("X-Sumo-Host", host);
        } else {
            post.addRequestHeader("X-Sumo-Host", getHost());
        }

        if (StringUtils.isNotBlank(sumoName)) {
            post.addRequestHeader("X-Sumo-Name", sumoName);
        }

        if (StringUtils.isNotBlank(sumoCategory)) {
            post.addRequestHeader("X-Sumo-Category", sumoCategory);
        }

        post.addRequestHeader("Content-Encoding", "gzip");

        if (isValidContentType(contentType)) {
            post.addRequestHeader("Content-Type", contentType);
        }

        if (fields != null && !fields.isEmpty()) {
            String field_string = fields.keySet().stream().map(key -> key + "=" + fields.get(key)).collect(Collectors.joining(","));
            post.addRequestHeader("X-Sumo-Fields", field_string);
        }

        post.addRequestHeader("X-Sumo-Client", "sumologic-jenkins-plugin");
    }

    private boolean isValidContentType(final String contentType) {
        if (contentType != null) {
            return GRAPHITE_CONTENT_TYPE.equals(contentType) || CARBON_CONTENT_TYPE.equals(contentType);
        }
        return false;
    }

    public StatusLine testHTTPUrl(String url) throws Exception {
        PostMethod post = null;

        if (StringUtils.isBlank(url)) {
            throw new Exception("URL can not be empty.");
        }

        try {
            post = new PostMethod(url);
            byte[] compressedData = compress("testMessage".getBytes());

            post.setRequestEntity(new ByteArrayRequestEntity(compressedData));
            httpClient.executeMethod(post);
        } finally {
            if (post != null) {
                post.releaseConnection();
            }
        }
        return post.getStatusLine();
    }
}
