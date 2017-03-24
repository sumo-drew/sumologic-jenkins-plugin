package com.sumologic.jenkins.jenkinssumologicplugin.sender;

import com.google.gson.Gson;
import com.sumologic.jenkins.jenkinssumologicplugin.PluginDescriptorImpl;
import com.sumologic.jenkins.jenkinssumologicplugin.model.ModelFactory;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by deven on 7/6/15.
 *
 * This publisher will sendLogs build metadata to a Sumologic HTTP collector.
 *
 */
public class SumoBuildNotifier extends Notifier {

  private final static Logger LOG = Logger.getLogger(SumoBuildNotifier.class.getName());
  private LogSender logSender;

  @DataBoundConstructor
  public SumoBuildNotifier() {
    super();
    logSender = LogSender.getInstance();
  }

  @SuppressWarnings("unchecked")
  public static SumoBuildNotifier getNotifier(AbstractProject project) {
    Map<Descriptor<Publisher>, Publisher> map = project.getPublishersList().toMap();
    for (Publisher publisher : map.values()) {
      if (publisher instanceof SumoBuildNotifier) {
        return (SumoBuildNotifier) publisher;
      }
    }
    return null;
  }

  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
    send(build, listener);
    return true;
  }

  @Override
  public PluginDescriptorImpl getDescriptor() {

    return (PluginDescriptorImpl) super.getDescriptor();
  }

  @Override
  public BuildStepMonitor getRequiredMonitorService() {

    return BuildStepMonitor.NONE;
  }

  protected void send(AbstractBuild build, TaskListener listener) {
    Gson gson = new Gson();
    String json = gson.toJson(ModelFactory.createBuildModel(build));
    String projectName = build.getProject().getDisplayName();

    LOG.info("Uploading build status to sumologic: " + json);
    logSender.sendLogs(getDescriptor().getUrl(), json.getBytes(), projectName, "status");
  }



}