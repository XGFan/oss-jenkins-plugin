package io.jenkins.plugins;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.google.common.base.Objects;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

public class OssPlugin extends Recorder implements SimpleBuildStep {

    @DataBoundSetter
    public String endpoint;

    @DataBoundSetter
    public String bucket;

    @DataBoundSetter
    public String include;

    @DataBoundSetter
    public String exclude;

    @DataBoundSetter
    public String prefix;

    @DataBoundConstructor
    public OssPlugin() {
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("upload to " + endpoint + "/" + bucket);
        listener.getLogger().println("include: " + Objects.firstNonNull(include, ""));
        listener.getLogger().println("exclude: " + Objects.firstNonNull(exclude, ""));
        final DescriptorImpl descriptor = (DescriptorImpl) this.getDescriptor();
        final OSS ossClient = new OSSClientBuilder().build(endpoint, descriptor.aliyunAccessKey, descriptor.aliyunSecretKey);
        final FilePath[] list = workspace.list(include, exclude);
        final String parent = workspace.toURI().getPath();
        for (FilePath filePath : list) {
            final String key = filePath.toURI().getPath().replaceFirst(parent, "");
            listener.getLogger().println("upload: " + key);
            ossClient.putObject(bucket, Objects.firstNonNull(prefix, "") + key, filePath.read());
        }
    }

    @Symbol("oss")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        String aliyunAccessKey;
        String aliyunSecretKey;

        public String getAliyunAccessKey() {
            return aliyunAccessKey;
        }

        public String getAliyunSecretKey() {
            return aliyunSecretKey;
        }

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindParameters(this);
            this.aliyunAccessKey = formData.getString("aliyunAccessKey");
            this.aliyunSecretKey = formData.getString("aliyunSecretKey");
            save();
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Upload to OSS";
        }
    }

}
