package com.htv.identity;

import com.htv.shared.test.ArchitectureRules;
import org.junit.jupiter.api.Test;

/** Luật tầng của identity-service (xem ArchitectureRules). */
class ArchitectureTest {

    @Test
    void layersAndDependenciesFollowTheDesign() {
        ArchitectureRules.check("com.htv.identity");
    }
}
