package com.datastax.oss.pulsaroperator.crds.autorecovery;

import static com.datastax.oss.pulsaroperator.crds.BaseComponentSpec.mergeMaps;
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


    @JsonPropertyDescription("Pulsar image to use for this component.")
    protected String image;
    @JsonPropertyDescription("Pulsar image pull policy to use for this component.")
    private String imagePullPolicy;
    @JsonPropertyDescription("Additional node selectors for this component.")
    protected Map<String, String> nodeSelectors;
    @JsonPropertyDescription("Configuration entries directly passed to this component.")
    protected Map<String, String> config;
    @JsonPropertyDescription("Replicas of this component.")
    protected Integer replicas;
    @JsonPropertyDescription("Annotations to add to each Autorecovery resource.")
    private Map<String, String> annotations;
    @Min(0)
    @io.fabric8.generator.annotation.Min(0)
    @JsonPropertyDescription("Termination grace period in seconds for the Autorecovery pod. Default value is 60.")
    private Integer gracePeriod;
    @JsonPropertyDescription("Resource requirements for the Autorecovery container.")
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
