package com.datastax.oss.pulsaroperator.reconcilier;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public abstract class AbstractReconcilier<T extends CustomResource> implements Reconciler<T> {

    protected final KubernetesClient client;

    @SneakyThrows
    public AbstractReconcilier(KubernetesClient client) {
        this.client = client;
    }


    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Override
    public UpdateControl<T> reconcile(T resource, Context<T> context) throws Exception {
        log.infof("%s reconcilier, new spec %s, status %s",
                resource.getFullResourceName(),
                resource.getSpec(),
                resource.getStatus());

        if (!validate(resource)) {
            return UpdateControl.noUpdate();
        }
        final UpdateControl<T> result = createResources(resource, context);
        return result;
    }

    protected abstract UpdateControl<T> createResources(T resource, Context<T> context) throws Exception;

    protected boolean validate(T resource) {
        final Set<ConstraintViolation<Object>> violations = validator.validate(resource.getSpec());
        if (violations.isEmpty()) {
            return true;
        }
        for (ConstraintViolation<Object> violation : violations) {
            log.errorf("invalid configuration property \"%s\" for value \"%s\": %s",
                    violation.getPropertyPath(), violation.getInvalidValue(),
                    violation.getMessage());
        }
        return false;
    }
}
