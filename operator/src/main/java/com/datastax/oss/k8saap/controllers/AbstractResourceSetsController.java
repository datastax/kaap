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
package com.datastax.oss.k8saap.controllers;

import com.datastax.oss.k8saap.common.SerializationUtil;
import com.datastax.oss.k8saap.common.json.JSONComparator;
import com.datastax.oss.k8saap.crds.BaseComponentStatus;
import com.datastax.oss.k8saap.crds.ConfigUtil;
import com.datastax.oss.k8saap.crds.FullSpecWithDefaults;
import com.datastax.oss.k8saap.crds.GlobalSpec;
import com.datastax.oss.k8saap.crds.SpecDiffer;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public abstract class AbstractResourceSetsController<T extends CustomResource<FULLSPEC,
        BaseComponentStatus>, FULLSPEC extends FullSpecWithDefaults, SPEC extends SETSPEC, SETSPEC, FACTORY,
        SETSLASTAPPLIED extends AbstractResourceSetsController.SetsLastApplied<FULLSPEC>>
        extends AbstractController<T> {

    private final String componentNameForLogs;

    public AbstractResourceSetsController(KubernetesClient client) {
        super(client);
        this.componentNameForLogs = getComponentNameForLogs();
    }

    public AbstractResourceSetsController() {
        this(null);
    }

    @Getter
    @AllArgsConstructor
    public static class SetInfo<SETSPEC, FACTORY> {
        private final String name;
        private final SETSPEC setSpec;
        private final FACTORY resourceFactory;
    }


    public interface SetsLastApplied<FULLSPEC> {

        FULLSPEC getCommon();

        void setCommon(FULLSPEC fullspec);

        Map<String, FULLSPEC> getSets();
    }

    protected abstract String getComponentNameForLogs();

    protected abstract boolean isRollingUpdate(FULLSPEC fullspec);

    protected abstract void patchResourceSet(SetInfo<SETSPEC, FACTORY> set);

    protected abstract void deleteResourceSet(SetInfo<SETSPEC, FACTORY> set, T resource);

    protected abstract void patchCommonResources(SetInfo<SETSPEC, FACTORY> set);

    protected abstract ReconciliationResult checkReady(T resource, SetInfo<SETSPEC, FACTORY> set);

    protected abstract String getDefaultSetName();

    protected abstract SPEC getSpec(FULLSPEC fullspec);

    protected abstract Map<String, SETSPEC> getSets(SPEC spec);

    protected abstract FACTORY newFactory(OwnerReference ownerReference, String namespace, String setName,
                                          SETSPEC setSpec, GlobalSpec globalSpec);


    @Override
    protected ReconciliationResult patchResources(T resource, Context<T> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final FULLSPEC spec = resource.getSpec();

        final OwnerReference ownerReference = getOwnerReference(resource);
        List<SetInfo<SETSPEC, FACTORY>> desiredSets = getSets(ownerReference, namespace, spec);

        SETSLASTAPPLIED lastAppliedResource = readSetsLastApplied(resource);
        final SETSLASTAPPLIED clonedLastAppliedResource = SerializationUtil.deepCloneObject(lastAppliedResource);

        if (!SpecDiffer.generateDiff(lastAppliedResource.getCommon(), spec).areEquals()) {

            final String defaultSetName = getDefaultSetName();
            final SPEC setSpec = getSpec(spec);
            final FACTORY factory =
                    newFactory(ownerReference, namespace, defaultSetName, setSpec, spec.getGlobalSpec());

            patchCommonResources(new SetInfo<>(defaultSetName, setSpec, factory));
            lastAppliedResource.setCommon(spec);
        }

        final boolean isRollingUpdate = isRollingUpdate(spec);
        boolean allSetsReady = true;
        for (SetInfo<SETSPEC, FACTORY> info : desiredSets) {
            final String setName = info.getName();
            final FULLSPEC lastApplied = lastAppliedResource.getSets().get(setName);

            final JSONComparator.Result compResult = compareLastAppliedSetSpec(resource, info, spec, lastApplied);
            final boolean areEquals = compResult.areEquals();
            if (areEquals) {
                final ReconciliationResult result = checkReady(resource, info);
                if (result.isReschedule()) {
                    allSetsReady = false;
                    if (isRollingUpdate) {
                        log.infof("%s-set '%s' is not ready, rescheduling", componentNameForLogs, setName);
                        result.setOverrideLastApplied(SerializationUtil.writeAsJson(lastAppliedResource));
                        return result;
                    } else {
                        log.infof("%s-set '%s' is not ready", componentNameForLogs, setName);
                    }
                } else {
                    log.infof("%s-set '%s' is ready", componentNameForLogs, setName);
                }
            } else {
                SpecDiffer.logDetailedSpecDiff(compResult);
                patchResourceSet(info);
                log.infof("%s-set '%s' patched", componentNameForLogs, setName);

                // this might happen if the replica has been set to zero
                final boolean isReady = !checkReady(resource, info).isReschedule();
                if (!isReady) {
                    allSetsReady = false;
                }
                lastAppliedResource.getSets().put(setName, spec);
                if (!isReady && isRollingUpdate) {
                    return newNotReadyResult(resource, lastAppliedResource);
                }
            }
        }
        if (allSetsReady) {
            log.infof("All %s-sets ready", componentNameForLogs);
            cleanupDeletedSets(clonedLastAppliedResource.getCommon(),
                    resource, desiredSets, lastAppliedResource);
            return newReadyResult(resource, lastAppliedResource);
        } else {
            return newNotReadyResult(resource, lastAppliedResource);
        }
    }

    protected abstract JSONComparator.Result compareLastAppliedSetSpec(T resource, SetInfo<SETSPEC, FACTORY> setInfo, FULLSPEC spec,
                                                                       FULLSPEC lastApplied);

    protected abstract SETSLASTAPPLIED readSetsLastApplied(T resource);

    private ReconciliationResult newReadyResult(T resource,
                                                SetsLastApplied<FULLSPEC> lastAppliedResource) {
        return new ReconciliationResult(
                false,
                List.of(createReadyCondition(resource)),
                SerializationUtil.writeAsJson(lastAppliedResource)
        );
    }

    protected ReconciliationResult newNotReadyResult(T resource,
                                                     SetsLastApplied<FULLSPEC> lastAppliedResource) {
        return new ReconciliationResult(
                true,
                List.of(createNotReadyInitializingCondition(resource)),
                SerializationUtil.writeAsJson(lastAppliedResource)
        );
    }


    private void cleanupDeletedSets(FULLSPEC lastAppliedFullSpec, T resource,
                                    List<SetInfo<SETSPEC, FACTORY>> sets,
                                    SETSLASTAPPLIED setLastApplied) {

        if (lastAppliedFullSpec != null) {
            final Set<String> currentSets = sets.stream().map(SetInfo::getName)
                    .collect(Collectors.toSet());
            final List<SetInfo<SETSPEC, FACTORY>> toDelete =
                    getSets(null, resource.getMetadata().getNamespace(), lastAppliedFullSpec, currentSets);
            for (SetInfo<SETSPEC, FACTORY> set : toDelete) {
                deleteResourceSet(set, resource);
                log.infof("Deleted %s-set: '%s'", componentNameForLogs, set.getName());
                setLastApplied.getSets().remove(set.getName());
            }

            for (SetInfo<SETSPEC, FACTORY> set : sets) {
                onSetReady(lastAppliedFullSpec, resource, set);
            }

        }
    }

    protected void onSetReady(FULLSPEC lastAppliedFullSpec, T resource, SetInfo<SETSPEC, FACTORY> setInfo) {}

    private List<SetInfo<SETSPEC, FACTORY>> getSets(OwnerReference ownerReference, String namespace, FULLSPEC spec) {
        return getSets(ownerReference, namespace, spec, Set.of());
    }

    private List<SetInfo<SETSPEC, FACTORY>> getSets(OwnerReference ownerReference, String namespace, FULLSPEC spec,
                                                    Set<String> excludes) {
        List<SetInfo<SETSPEC, FACTORY>> result = new ArrayList<>();

        for (Map.Entry<String, SETSPEC> setSpec : getSetSpecs(getSpec(spec)).entrySet()) {
            final String setName = setSpec.getKey();
            if (excludes.contains(setName)) {
                continue;
            }
            final SETSPEC specValue = setSpec.getValue();

            final FACTORY factory =
                    newFactory(ownerReference, namespace, setName, specValue, spec.getGlobalSpec());
            result.add(new SetInfo<>(setName, specValue, factory));
        }
        return result;
    }

    @SneakyThrows
    protected LinkedHashMap<String, SETSPEC> getSetSpecs(SPEC spec) {
        Map<String, SETSPEC> sets = getSets(spec);
        if (sets == null || sets.isEmpty()) {
            final String defaultSetName = getDefaultSetName();
            sets = new LinkedHashMap(Map.of(defaultSetName,
                    ConfigUtil.applyDefaultsWithReflection(spec.getClass().getConstructor().newInstance(),
                            () -> spec)));
        } else {
            for (Map.Entry<String, SETSPEC> set : sets.entrySet()) {
                sets.put(set.getKey(),
                        ConfigUtil.applyDefaultsWithReflection(set.getValue(), () -> spec)
                );
            }
        }
        return new LinkedHashMap<>(sets);
    }
}
