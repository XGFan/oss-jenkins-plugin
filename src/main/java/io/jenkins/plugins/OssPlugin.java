package io.jenkins.plugins;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Objects;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.*;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OssPlugin extends Recorder implements SimpleBuildStep {

    @DataBoundSetter
    public String endpoint;

    @DataBoundSetter
    public String bucket;

    @DataBoundSetter
    public String prefix;

    @DataBoundSetter
    public String patternType;

    @DataBoundSetter
    public String pattern;

    @DataBoundSetter
    public String credentialsId;

    @DataBoundConstructor
    public OssPlugin() {
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        final StandardUsernamePasswordCredentials usernamePasswordCredentials = CredentialsProvider.findCredentialById(credentialsId, StandardUsernamePasswordCredentials.class, run);

        final PrintStream logger = listener.getLogger();
        logger.println("upload to " + endpoint + "/" + bucket);
        logger.println("patternType: " + Objects.firstNonNull(patternType, ""));
        logger.println("pattern: " + Objects.firstNonNull(pattern, ""));

        final OSS ossClient = new OSSClientBuilder().build(endpoint,
                usernamePasswordCredentials.getUsername(),
                usernamePasswordCredentials.getPassword().getPlainText());

        FilePath[] list;

        if (patternType.equals("include")) {
            if (pattern == null || pattern.trim().isEmpty()) {
                pattern = "**";
            }
            list = workspace.list(pattern);
        } else {
            list = workspace.list("**", pattern);
        }
        final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2 + 1);
        final String parent = workspace.toURI().getPath();
        Arrays.stream(list).forEach(filePath -> executorService.submit(() -> uploadFile(logger, ossClient, parent, filePath)));
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.MINUTES);
    }

    private void uploadFile(PrintStream logger, OSS ossClient, String parent, FilePath filePath) {
        String fileName = null;
        try {
            fileName = filePath.toURI().getPath().replaceFirst(parent, "");
            final String ossKey = Objects.firstNonNull(prefix, "") + fileName;
            String ossMd5 = null;
            try {
                final ObjectMetadata objectMetadata = ossClient.getObjectMetadata(bucket, ossKey);
                ossMd5 = objectMetadata.getETag();
            } catch (OSSException ignore) {
            }
            if (ossMd5 == null || !DigestUtils.md5Hex(filePath.read()).toUpperCase().equals(ossMd5)) {
                ossClient.putObject(bucket, ossKey, filePath.read());
                logger.println("upload: " + fileName);
            } else {
                logger.println("skip: " + fileName);
            }
        } catch (Exception e) {
            logger.println("fail: " + fileName);
            logger.println(e.getMessage());
        }
    }

    @Symbol("oss")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) {
            req.bindParameters(this);
            save();
            return true;
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }
            return result
                    .includeMatchingAs(ACL.SYSTEM,
                            Jenkins.get(),
                            StandardUsernamePasswordCredentials.class,
                            Collections.emptyList(),
                            it -> true)
                    .includeCurrentValue(credentialsId);
        }

        @Override
        public String getDisplayName() {
            return "Upload to OSS";
        }
    }

}
