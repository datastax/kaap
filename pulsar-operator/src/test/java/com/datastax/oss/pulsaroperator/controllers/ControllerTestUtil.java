package com.datastax.oss.pulsaroperator.controllers;

import static org.mockito.Mockito.mock;
import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.BaseComponentStatus;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.FullSpecWithDefaults;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.SneakyThrows;
import org.testng.Assert;

public class ControllerTestUtil<X extends FullSpecWithDefaults,
        R extends CustomResource<X, BaseComponentStatus>> {

    private final String namespace;
    private final String clusterName;

    public ControllerTestUtil(String namespace, String clusterName) {
        this.namespace = namespace;
        this.clusterName = clusterName;
    }

    @SneakyThrows
    public void invokeControllerAndAssertError(String spec, String expectedErrorMessage,
                                               Class<R> specClass, Class<X> fullSpecClass,
                                               Class<? extends AbstractController<R>> controllerClass) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(namespace);
        final UpdateControl<R> result = invokeController(mockKubernetesClient, spec,
                specClass, fullSpecClass, controllerClass);
        Assert.assertTrue(result.isUpdateStatus());

        final Condition readyCondition = result.getResource().getStatus().getConditions().get(0);
        Assert.assertEquals(readyCondition.getStatus(), CRDConstants.CONDITIONS_STATUS_FALSE);
        Assert.assertEquals(readyCondition.getMessage(),
                expectedErrorMessage);
        Assert.assertEquals(readyCondition.getReason(),
                CRDConstants.CONDITIONS_TYPE_READY_REASON_INVALID_SPEC);
    }

    @SneakyThrows
    public MockKubernetesClient invokeController(String spec, Class<R> specClass, Class<X> fullSpecClass,
                                                 Class<? extends AbstractController<R>> controllerClass) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(namespace);
        final UpdateControl<R>
                result = invokeController(mockKubernetesClient, spec, specClass, fullSpecClass, controllerClass);
        Assert.assertTrue(result.isUpdateStatus());

        final Condition readyCondition = result.getResource().getStatus().getConditions().get(0);
        Assert.assertEquals(readyCondition.getStatus(), CRDConstants.CONDITIONS_STATUS_FALSE);
        Assert.assertNull(readyCondition.getMessage());
        Assert.assertEquals(readyCondition.getReason(), CRDConstants.CONDITIONS_TYPE_READY_REASON_INITIALIZING);
        return mockKubernetesClient;
    }

    public UpdateControl<R> invokeController(
            MockKubernetesClient mockKubernetesClient, String spec,
            Class<R> crClass, Class<X> fullSpecClass,
            Class<? extends AbstractController<R>> controllerClass)
            throws Exception {
        final R cr = createCustomResource(crClass, fullSpecClass, spec);
        return invokeController(mockKubernetesClient, cr, controllerClass);
    }

    public UpdateControl<R> invokeController(MockKubernetesClient mockKubernetesClient,
                                             R cr,
                                             Class<? extends AbstractController<R>> controllerClass) throws Exception {
        final AbstractController<R> controller =
                controllerClass.getConstructor(KubernetesClient.class).newInstance(mockKubernetesClient.getClient());
        return controller.reconcile(cr, mock(Context.class));
    }


    public R createCustomResource(Class<R> crClass, Class<X> fullSpecClass, String spec) throws Exception {
        final R cr = crClass.getConstructor().newInstance();
        cr.setMetadata(
                new ObjectMetaBuilder()
                .withName(clusterName + "-cr").withNamespace(namespace)
                .build());
        cr.setSpec(MockKubernetesClient.readYaml(spec, fullSpecClass));
        cr.getSpec().getGlobalSpec().applyDefaults(null);
        cr.getSpec().applyDefaults(cr.getSpec().getGlobalSpec());
        return cr;
    }

}
