package com.datastax.oss.pulsaroperator.controllers.utils;

import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class TokenAuthProvisionerResourcesFactory extends BaseResourcesFactory<AuthConfig.TokenConfig> {

    public TokenAuthProvisionerResourcesFactory(KubernetesClient client,
                                                String namespace, AuthConfig.TokenConfig spec,
                                                GlobalSpec global,
                                                OwnerReference ownerReference) {
        super(client, namespace, spec, global, ownerReference);
    }

    @Override
    protected String getResourceName() {
        return "token-auth-provisioner";
    }

    @Override
    protected String getComponentBaseName() {
        return "token-auth-provisioner";
    }

    @Override
    protected boolean isComponentEnabled() {
        return spec.getProvisioner() != null && spec.getProvisioner().getInitialize();
    }

    public boolean patchJobAndCheckCompleted() {
        if (!isComponentEnabled()) {
            log.info(resourceName + " not enabled");
            return true;
        }
        final Job currentJob = getJob();
        if (isJobCompleted(currentJob)) {
            log.info(resourceName + " job already completed");
            return true;
        }
        final String specChecksum = genChecksum(spec);
        final String lastAppliedAnnotationKey = "%s/last-applied".formatted(CRDConstants.GROUP);
        final Map<String, String> annotations = Map.of(lastAppliedAnnotationKey, specChecksum);
        if (currentJob != null) {
            final Map<String, String> currentAnnotations = currentJob.getMetadata().getAnnotations();
            if (currentAnnotations != null
                    && specChecksum.equals(currentAnnotations.get(lastAppliedAnnotationKey))
            ) {
                return false;
            }
        }




        List<EnvVar> env = new ArrayList<>();
        env.add(new EnvVarBuilder()
                .withName("ClusterName")
                .withValue(global.getName())
                .build()
        );
        env.add(new EnvVarBuilder()
                .withName("SuperRoles")
                .withValue(spec.getSuperUserRoles().stream().collect(Collectors.joining(",")))
                .build()
        );

        env.add(new EnvVarBuilder()
                .withName("ProcessMode")
                .withValue("init")
                .build()
        );
        env.add(new EnvVarBuilder()
                .withName("PulsarNamespace")
                .withValue(namespace)
                .build()
        );

        env.add(new EnvVarBuilder()
                .withName("PrivateKeySecretName")
                .withValue(spec.getPrivateKeyFile())
                .build()
        );
        env.add(new EnvVarBuilder()
                .withName("PublicKeySecretName")
                .withValue(spec.getPublicKeyFile())
                .build()
        );

        final AuthConfig.TokenAuthProvisionerConfig provisionerSpec = spec.getProvisioner();
        final Container container = new ContainerBuilder()
                .withName(resourceName)
                .withImage(provisionerSpec.getImage())
                .withImagePullPolicy(provisionerSpec.getImagePullPolicy())
                .withResources(provisionerSpec.getResources())
                .withEnv(env)
                .build();


        final Job job = new JobBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels())
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .withServiceAccountName(getServiceAccountName())
                .withContainers(List.of(container))
                .withRestartPolicy("OnFailure")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        patchRBAC();
        patchResource(job);
        return false;
    }

    private String getServiceAccountName() {
        return "%s-burnell".formatted(global.getName());
    }

    private void patchRBAC() {
        final AuthConfig.TokenAuthProvisionerConfig.RbacConfig rbac = spec.getProvisioner().getRbac();
        if (!rbac.getCreate()) {
            return;
        }
        boolean namespaced = rbac.getNamespaced();
        List<PolicyRule> rules = new ArrayList<>();
        rules.add(
                new PolicyRuleBuilder()
                        .withApiGroups("")
                        .withResources("secrets")
                        .withVerbs("get", "create", "list")
                        .build()
        );
        rules.add(
                new PolicyRuleBuilder()
                        .withApiGroups("")
                        .withResources("namespaces")
                        .withVerbs("list")
                        .build()
        );
        final String serviceAccountName = getServiceAccountName();
        patchServiceAccountSingleRole(namespaced, rules, serviceAccountName);
    }



}
