package com.datastax.oss.pulsaroperator.controllers.utils;

import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.controllers.ControllerTestUtil;
import com.datastax.oss.pulsaroperator.controllers.PulsarClusterController;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import lombok.SneakyThrows;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TokenAuthProvisionerTest {
    private static final String NAMESPACE = "ns";
    private static final String CLUSTER_NAME = "pulsarname";

    @Test
    public void testDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:2.10.2
                    auth:
                        enabled: true
                """;

        final MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(client
                        .getCreatedResource(ServiceAccount.class).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: ServiceAccount
                        metadata:
                          name: pulsarname-burnell
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: PulsarCluster
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        """);

        Assert.assertEquals(client
                        .getCreatedResource(Role.class).getResourceYaml(),
                """
                        ---
                        apiVersion: rbac.authorization.k8s.io/v1
                        kind: Role
                        metadata:
                          name: pulsarname-token-auth-provisioner
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: PulsarCluster
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        rules:
                        - apiGroups:
                          - ""
                          resources:
                          - secrets
                          verbs:
                          - get
                          - create
                          - list
                        - apiGroups:
                          - ""
                          resources:
                          - namespaces
                          verbs:
                          - list
                        """);

        Assert.assertEquals(client
                        .getCreatedResource(RoleBinding.class).getResourceYaml(),
                """
                        ---
                        apiVersion: rbac.authorization.k8s.io/v1
                        kind: RoleBinding
                        metadata:
                          name: pulsarname-token-auth-provisioner
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: PulsarCluster
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        roleRef:
                          kind: Role
                          name: pulsarname-token-auth-provisioner
                        subjects:
                        - kind: ServiceAccount
                          name: pulsarname-burnell
                          namespace: ns
                        """);

        Assert.assertEquals(client
                        .getCreatedResource(Job.class).getResourceYaml(),
                """
                        ---
                        apiVersion: batch/v1
                        kind: Job
                        metadata:
                          annotations:
                            com.datastax.oss/last-applied: 478a274933648317ea2d98f57347f60595012f81f9c9f244d85561b47bb95f43
                          labels:
                            app: pulsarname
                            cluster: pulsarname
                            component: token-auth-provisioner
                          name: pulsarname-token-auth-provisioner
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: PulsarCluster
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        spec:
                          template:
                            spec:
                              containers:
                              - env:
                                - name: ClusterName
                                  value: pulsarname
                                - name: SuperRoles
                                  value: "superuser,admin,websocket,proxy"
                                - name: ProcessMode
                                  value: init
                                - name: PulsarNamespace
                                  value: ns
                                - name: PrivateKeySecretName
                                  value: my-private.key
                                - name: PublicKeySecretName
                                  value: my-public.key
                                image: datastax/burnell:latest
                                imagePullPolicy: IfNotPresent
                                name: pulsarname-token-auth-provisioner
                              restartPolicy: OnFailure
                              serviceAccountName: pulsarname-burnell
                        """);
    }

    @Test
    public void testClusterRbac() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:2.10.2
                    auth:
                        enabled: true
                        token:
                            provisioner:
                                rbac:
                                    namespaced: false
                """;

        final MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(client
                        .getCreatedResource(ServiceAccount.class).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: ServiceAccount
                        metadata:
                          name: pulsarname-burnell
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: PulsarCluster
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        """);

        Assert.assertEquals(client
                        .getCreatedResource(ClusterRole.class).getResourceYaml(),
                """
                        ---
                        apiVersion: rbac.authorization.k8s.io/v1
                        kind: ClusterRole
                        metadata:
                          name: pulsarname-token-auth-provisioner
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: PulsarCluster
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        rules:
                        - apiGroups:
                          - ""
                          resources:
                          - secrets
                          verbs:
                          - get
                          - create
                          - list
                        - apiGroups:
                          - ""
                          resources:
                          - namespaces
                          verbs:
                          - list
                        """);

        Assert.assertEquals(client
                        .getCreatedResource(ClusterRoleBinding.class).getResourceYaml(),
                """
                        ---
                        apiVersion: rbac.authorization.k8s.io/v1
                        kind: ClusterRoleBinding
                        metadata:
                          name: pulsarname-token-auth-provisioner
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: PulsarCluster
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        roleRef:
                          kind: ClusterRole
                          name: pulsarname-token-auth-provisioner
                        subjects:
                        - kind: ServiceAccount
                          name: pulsarname-burnell
                          namespace: ns
                        """);
    }


    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        return new ControllerTestUtil<PulsarClusterSpec, PulsarCluster>(NAMESPACE, CLUSTER_NAME)
                .invokeController(spec,
                        PulsarCluster.class,
                        PulsarClusterSpec.class,
                        PulsarClusterController.class);
    }

}