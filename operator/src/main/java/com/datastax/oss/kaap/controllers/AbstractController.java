/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.kaap.controllers;

import com.datastax.oss.kaap.OperatorRuntimeConfiguration;
import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.crds.BaseComponentStatus;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.FullSpecWithDefaults;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.SpecDiffer;
import com.datastax.oss.kaap.crds.autorecovery.AutorecoverySpec;
import com.datastax.oss.kaap.crds.bastion.BastionSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.kaap.crds.broker.BrokerSpec;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.crds.function.FunctionsWorkerSpec;
import com.datastax.oss.kaap.crds.proxy.ProxySpec;
import com.datastax.oss.kaap.crds.validation.ValidSpec;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeperSpec;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.hibernate.validator.cfg.context.ConstraintDefinitionContext;

@JBossLog
public abstract class AbstractController<T extends CustomResource<? extends FullSpecWithDefaults, BaseComponentStatus>>
        implements Reconciler<T> {

    protected final KubernetesClient client;
    private final Validator validator;
    @Inject
    OperatorRuntimeConfiguration operatorRuntimeConfiguration;

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
                ZooKeeperSpec.class,
                BookKeeperSpec.class,
                BrokerSpec.class,
                ProxySpec.class,
                AutorecoverySpec.class,
                BastionSpec.class,
                FunctionsWorkerSpec.class
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
        log.debugf("%s controller reconciliation started (resource gen %d)",
                resource.getFullResourceName(), resource.getMetadata().getGeneration());
        long start = System.nanoTime();

        final GlobalSpec globalSpec = resource.getSpec().getGlobalSpec();
        globalSpec.applyDefaults(null);
        resource.getSpec().applyDefaults(globalSpec);

        String lastApplied = resource.getStatus().getLastApplied();

        final String validationErrorMessage = validate(resource);
        if (validationErrorMessage != null) {
            final List<Condition> conditions =
                    mergeConditions(resource.getStatus().getConditions(), List.of(createNotReadyCondition(
                            resource, CRDConstants.CONDITIONS_TYPE_READY_REASON_INVALID_SPEC, validationErrorMessage
                    )), Instant.now());
            resource.setStatus(new BaseComponentStatus(conditions, lastApplied));
            return UpdateControl.updateStatus(resource);
        }


        boolean reschedule;
        List<Condition> conditions;

        try {
            ReconciliationResult reconciliationResult = patchResources(resource, context);
            conditions = mergeConditions(resource.getStatus().getConditions(), reconciliationResult.getConditions(),
                    Instant.now());
            reschedule = reconciliationResult.isReschedule();
            if (!reconciliationResult.isSkipLastAppliedUpdate()) {
                if (reconciliationResult.getOverrideLastApplied() != null) {
                    lastApplied = reconciliationResult.getOverrideLastApplied();
                } else {
                    lastApplied = SerializationUtil.writeAsJson(resource.getSpec());
                }
            }
        } catch (Throwable throwable) {
            log.errorf(throwable, "Error during reconciliation for resource %s with name %s: %s",
                    resource.getFullResourceName(),
                    resource.getMetadata().getName(),
                    throwable.getMessage());
            conditions = mergeConditions(resource.getStatus().getConditions(), List.of(createNotReadyCondition(
                    resource, CRDConstants.CONDITIONS_TYPE_READY_REASON_GENERIC_ERROR, throwable.getMessage()
            )), Instant.now());
            reschedule = true;
        }
        long time = (System.nanoTime() - start) / 1_000_000;

        final String conditionsStr = conditions.stream().map(c -> {
            String str = "%s: %s";
            return str.formatted(
                    c.getType(),
                    c.getStatus()
                            + (c.getReason() != null ? "/" + c.getReason() : "")
                            + (c.getMessage() != null ? "/" + c.getMessage() : "")
            );
        }).collect(Collectors.joining());

        log.debugf("%s controller reconciliation finished in %d ms, rescheduling: %s, conditions: %s",
                resource.getFullResourceName(),
                time, reschedule + "", conditionsStr);

        resource.setStatus(new BaseComponentStatus(conditions, lastApplied));
        final UpdateControl<T> update = UpdateControl.updateStatus(resource);
        if (reschedule) {
            update.rescheduleAfter(operatorRuntimeConfiguration.reconciliationRescheduleSeconds(), TimeUnit.SECONDS);
        }
        return update;
    }

    @Data
    @AllArgsConstructor
    protected static class ReconciliationResult {
        public ReconciliationResult(boolean reschedule, List<Condition> conditions) {
            this(reschedule, conditions, false);
        }
        public ReconciliationResult(boolean reschedule, List<Condition> conditions, boolean skipLastAppliedUpdate) {
            this(reschedule, conditions, skipLastAppliedUpdate, null);
        }

        public ReconciliationResult(boolean reschedule, List<Condition> conditions, String overrideLastApplied) {
            this(reschedule, conditions, false, overrideLastApplied);
        }


        boolean reschedule;
        List<Condition> conditions;
        boolean skipLastAppliedUpdate;
        String overrideLastApplied;
    }


    protected abstract ReconciliationResult patchResources(T resource, Context<T> context) throws Exception;

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


    protected OwnerReference getOwnerReference(T cr) {
        return new OwnerReferenceBuilder()
                .withApiVersion(cr.getApiVersion())
                .withKind(cr.getKind())
                .withName(cr.getMetadata().getName())
                .withUid(cr.getMetadata().getUid())
                .withBlockOwnerDeletion(true)
                .withController(true)
                .build();
    }

    protected boolean areSpecChanged(T cr) {
        final String lastApplied = cr.getStatus().getLastApplied();
        if (lastApplied == null) {
            return true;
        }
        return !SpecDiffer.generateDiff(cr.getSpec(), lastApplied).areEquals();
    }

    protected  <SPEC> SPEC getLastAppliedResource(T cr, Class<SPEC> toClass) {
        final String lastApplied = cr.getStatus().getLastApplied();
        if (lastApplied == null) {
            return null;
        }
        return SerializationUtil.readJson(lastApplied, toClass);
    }

    public static Condition createReadyCondition(CustomResource resource) {
        return createReadyCondition(resource.getMetadata().getGeneration());
    }

    public static Condition createReadyConditionDisabled(CustomResource resource) {
        return createReadyCondition(resource.getMetadata().getGeneration(),
                CRDConstants.CONDITIONS_TYPE_READY_REASON_DISABLED);
    }

    public static Condition createReadyCondition(Long generation) {
        return createReadyCondition(generation, null);
    }

    public static Condition createReadyCondition(Long generation, String reason) {
        return new ConditionBuilder()
                .withType(CRDConstants.CONDITIONS_TYPE_READY)
                .withStatus(CRDConstants.CONDITIONS_STATUS_TRUE)
                .withObservedGeneration(generation)
                .withReason(reason)
                .build();
    }

    public static Condition createNotReadyCondition(CustomResource resource, String reason, String message) {
        return createNotReadyCondition(resource.getMetadata().getGeneration(), reason, message);
    }

    public static Condition createNotReadyCondition(Long generation, String reason, String message) {
        return new ConditionBuilder()
                .withType(CRDConstants.CONDITIONS_TYPE_READY)
                .withStatus(CRDConstants.CONDITIONS_STATUS_FALSE)
                .withObservedGeneration(generation)
                .withReason(reason)
                .withMessage(message)
                .build();
    }

    public static Condition createNotReadyInitializingCondition(CustomResource resource) {
        return createNotReadyInitializingCondition(resource.getMetadata().getGeneration());
    }

    public static Condition createNotReadyInitializingCondition(Long generation) {
        return createNotReadyCondition(generation, CRDConstants.CONDITIONS_TYPE_READY_REASON_INITIALIZING, null);
    }

    private List<Condition> mergeConditions(List<Condition> previousConditions,
                                            List<Condition> newConditions,
                                            Instant now) {

        List<Condition> result = new ArrayList<>();

        if (previousConditions == null || previousConditions.isEmpty()) {
            for (Condition newCondition : newConditions) {
                result.add(copyConditionWithLastTransitionTime(now, newCondition));
            }
            return result;
        }

        for (Condition condition : previousConditions) {
            final Condition updated =
                    newConditions.stream().filter(c -> c.getType().equals(condition.getType())).findFirst()
                            .orElse(null);

            if (updated == null) {
                continue;
            }

            if (updated.getStatus().equals(condition.getStatus())) {
                result.add(updated);
            } else {
                result.add(copyConditionWithLastTransitionTime(now, updated));
            }
        }

        for (Condition condition : newConditions) {
            final Condition prev =
                    previousConditions.stream().filter(c -> c.getType().equals(condition.getType())).findFirst()
                            .orElse(null);

            if (prev != null) {
                continue;
            }
            result.add(copyConditionWithLastTransitionTime(now, condition));
        }
        return result;
    }

    private Condition copyConditionWithLastTransitionTime(Instant now, Condition updated) {
        return new ConditionBuilder()
                .withType(updated.getType())
                .withObservedGeneration(updated.getObservedGeneration())
                .withStatus(updated.getStatus())
                .withMessage(updated.getMessage())
                .withReason(updated.getReason())
                .withAdditionalProperties(updated.getAdditionalProperties())
                .withLastTransitionTime(now.toString())
                .build();
    }
}
