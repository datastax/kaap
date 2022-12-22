package com.datastax.oss.pulsaroperator.crds.autorecovery;

import static com.datastax.oss.pulsaroperator.crds.BaseComponentSpec.mergeMaps;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.WithDefaults;
import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import java.util.Map;
import java.util.function.Supplier;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AutorecoverySpec extends ValidableSpec<AutorecoverySpec> implements WithDefaults {

    private static final Supplier<ResourceRequirements> DEFAULT_RESOURCE_REQUIREMENTS =
            () -> new ResourceRequirementsBuilder()
                    .withRequests(Map.of("memory", Quantity.parse("512Mi"), "cpu", Quantity.parse("0.3")))
                    .build();


    @JsonPropertyDescription(CRDConstants.DOC_IMAGE)
    protected String image;
    @JsonPropertyDescription(CRDConstants.DOC_IMAGE_PULL_POLICY)
    private String imagePullPolicy;
    @JsonPropertyDescription(CRDConstants.DOC_NODE_SELECTORS)
    protected Map<String, String> nodeSelectors;
    @JsonPropertyDescription(CRDConstants.DOC_CONFIG)
    protected Map<String, String> config;
    @JsonPropertyDescription(CRDConstants.DOC_REPLICAS)
    protected Integer replicas;
    @JsonPropertyDescription(CRDConstants.DOC_ANNOTATIONS)
    private Map<String, String> annotations;
    @Min(0)
    @io.fabric8.generator.annotation.Min(0)
    @JsonPropertyDescription(CRDConstants.DOC_GRACE_PERIOD)
    private Integer gracePeriod;
    @JsonPropertyDescription(CRDConstants.DOC_RESOURCES)
    private ResourceRequirements resources;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (image == null) {
            image = globalSpec.getImage();
        }
        nodeSelectors = mergeMaps(globalSpec.getNodeSelectors(), nodeSelectors);
        if (imagePullPolicy == null) {
            imagePullPolicy = globalSpec.getImagePullPolicy();
        }
        if (replicas == null) {
            replicas = 1;
        }
        if (gracePeriod == null) {
            gracePeriod = 60;
        }
        if (resources == null) {
            resources = DEFAULT_RESOURCE_REQUIREMENTS.get();
        }
    }

    @Override
    public boolean isValid(AutorecoverySpec value, ConstraintValidatorContext context) {
        return true;
    }
}
