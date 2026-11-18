package com.htv.notification;

import com.htv.shared.test.ArchitectureRules;
import org.junit.jupiter.api.Test;

/** Luật tầng của notification-service (xem ArchitectureRules). */
class ArchitectureTest {

    @Test
    void layersAndDependenciesFollowTheDesign() {
        ArchitectureRules.check("com.htv.notification");
    }
}
