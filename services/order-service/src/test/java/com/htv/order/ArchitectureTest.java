package com.htv.order;

import com.htv.shared.test.ArchitectureRules;
import org.junit.jupiter.api.Test;

/** Luật tầng của order-service (xem ArchitectureRules). */
class ArchitectureTest {

    @Test
    void layersAndDependenciesFollowTheDesign() {
        ArchitectureRules.check("com.htv.order");
    }
}
