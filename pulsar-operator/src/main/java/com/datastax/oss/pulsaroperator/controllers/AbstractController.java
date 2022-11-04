package com.datastax.oss.pulsaroperator.controllers;

import com.datastax.oss.pulsaroperator.crds.BaseComponentStatus;
import com.datastax.oss.pulsaroperator.crds.FullSpecWithDefaults;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.validation.ValidSpec;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperSpec;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.hibernate.validator.cfg.context.ConstraintDefinitionContext;

@JBossLog
public abstract class AbstractController<T extends CustomResource<? extends FullSpecWithDefaults, ?
        extends BaseComponentStatus>>
        implements Reconciler<T> {

    protected final KubernetesClient client;
    private final Validator validator;


    public AbstractController() {
        this(null);
    }

    @SneakyThrows
    public AbstractController(KubernetesClient client) {
        this.client = client;
        this.validator = createValidator();

    }

    private Validator createValidator() {
        final HibernateValidatorConfiguration configuration = (HibernateValidatorConfiguration)
                Validation.byDefaultProvider().configure();
        configuration.addMapping(getConstraintMapping(configuration,
                PulsarClusterSpec.class,
                GlobalSpec.class,
                ZooKeeperSpec.class
        ));
        return configuration.buildValidatorFactory().getValidator();
    }

    private ConstraintMapping getConstraintMapping(HibernateValidatorConfiguration configuration,
                                                   Class<? extends ConstraintValidator<ValidSpec, ?>>... validateBy) {
        final ConstraintMapping mapping = configuration.createConstraintMapping();
        final ConstraintDefinitionContext<ValidSpec> definitionContext =
                mapping.constraintDefinition(ValidSpec.class)
                        .includeExistingValidators(true);

        for (Class<? extends ConstraintValidator<ValidSpec, ?>> validator : validateBy) {
            definitionContext.validatedBy(validator);
        }
        return mapping;
    }

    @Override
    public UpdateControl<T> reconcile(T resource, Context<T> context) throws Exception {
        log.infof("%s controller, new spec %s, status %s",
                resource.getFullResourceName(),
                resource.getSpec(),
                resource.getStatus());

        final GlobalSpec globalSpec = resource.getSpec().getGlobalSpec();
        globalSpec.applyDefaults(null);
        resource.getSpec().applyDefaults(globalSpec);

        final String validationErrorMessage = validate(resource);
        if (validationErrorMessage != null) {
            resource.getStatus().setError(validationErrorMessage);
            return UpdateControl.updateStatus(resource);
        }
        final UpdateControl<T> result = createResources(resource, context);
        return result;
    }

    protected abstract UpdateControl<T> createResources(T resource, Context<T> context) throws Exception;

    protected String validate(T resource) {
        final Set<ConstraintViolation<Object>> violations = validator.validate(resource.getSpec());
        if (violations.isEmpty()) {
            return null;
        }
        List<String> errors = new ArrayList<>();
        for (ConstraintViolation<Object> violation : violations) {
            final String errorMessage = String.format("invalid configuration property \"%s\" for value \"%s\": %s",
                    violation.getPropertyPath(), violation.getInvalidValue(),
                    violation.getMessage());
            log.error(errorMessage);
            errors.add(errorMessage);
        }
        return errors.stream().collect(Collectors.joining(System.lineSeparator()));
    }
}
