package com.datastax.oss.pulsaroperator.controllers;

import static org.mockito.Mockito.mock;
import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.BaseComponentStatus;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.FullSpecWithDefaults;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.SneakyThrows;
import org.testng.Assert;

public class ControllerTestUtil<FULLSPEC extends FullSpecWithDefaults,
        CR extends CustomResource<FULLSPEC, BaseComponentStatus>> {

    private final String namespace;
    private final String clusterName;

    public ControllerTestUtil(String namespace, String clusterName) {
        this.namespace = namespace;
        this.clusterName = clusterName;
    }

    @SneakyThrows
    public void invokeControllerAndAssertError(String spec, String expectedErrorMessage,
                                                Class<CR> specClass, Class<FULLSPEC> fullSpecClass,
                                                Class<? extends AbstractController<CR>> controllerClass) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(namespace);
        final UpdateControl<CR> result = invokeController(mockKubernetesClient, spec,
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
    public MockKubernetesClient invokeController(String spec, Class<CR> specClass, Class<FULLSPEC> fullSpecClass,
                                                  Class<? extends AbstractController<CR>> controllerClass) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(namespace);
        final UpdateControl<CR>
                result = invokeController(mockKubernetesClient, spec, specClass, fullSpecClass, controllerClass);
        Assert.assertTrue(result.isUpdateStatus());

        final Condition readyCondition = result.getResource().getStatus().getConditions().get(0);
        Assert.assertEquals(readyCondition.getStatus(), CRDConstants.CONDITIONS_STATUS_FALSE);
        Assert.assertNull(readyCondition.getMessage());
        Assert.assertEquals(readyCondition.getReason(), CRDConstants.CONDITIONS_TYPE_READY_REASON_INITIALIZING);
        return mockKubernetesClient;
    }

    public UpdateControl<CR> invokeController(
            MockKubernetesClient mockKubernetesClient, String spec,
            Class<CR> crClass, Class<FULLSPEC> fullSpecClass,
            Class<? extends AbstractController<CR>> controllerClass)
            throws Exception {
        final AbstractController<CR> controller =
                controllerClass.getConstructor(KubernetesClient.class).newInstance(mockKubernetesClient.getClient());

        final CR cr = crClass.getConstructor().newInstance();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(clusterName + "-cr");
        meta.setNamespace(namespace);
        cr.setMetadata(meta);

        final FULLSPEC fSpec = MockKubernetesClient.readYaml(spec, fullSpecClass);
        cr.setSpec(fSpec);

        return controller.reconcile(cr, mock(Context.class));
    }
}
