package com.htv.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionComparatorTest {

    @Test
    void numericPrefixDecidesAndMissingSegmentsAreZero() {
        assertThat(VersionComparator.isOlder("1.2.3", "1.2.4")).isTrue();
        assertThat(VersionComparator.isOlder("1.2.4", "1.2.3")).isFalse();
        assertThat(VersionComparator.isOlder("1.2", "1.2.0")).isFalse();
        assertThat(VersionComparator.isOlder("1.2.0", "1.2")).isFalse();
        assertThat(VersionComparator.isOlder("1.9.0", "1.10.0")).isTrue(); // so số, không so chuỗi
        assertThat(VersionComparator.isOlder("2.0.0", "1.99.99")).isFalse();
        assertThat(VersionComparator.isOlder("1.0.0", "1.0.0")).isFalse();
    }

    @Test
    void qualifiersAreIgnoredAndLeadingVIsAllowed() {
        assertThat(VersionComparator.isOlder("1.2.3-SNAPSHOT", "1.2.4")).isTrue();
        assertThat(VersionComparator.isOlder("1.2.3-SNAPSHOT", "1.2.3")).isFalse();
        assertThat(VersionComparator.isOlder("v1.0.0", "1.0.1")).isTrue();
    }

    @Test
    void unparsableVersionsAreNeverConsideredOlder() {
        assertThat(VersionComparator.isOlder("unknown", "1.0.0")).isFalse();
        assertThat(VersionComparator.isOlder("1.0.0", "unknown")).isFalse();
        assertThat(VersionComparator.isOlder(null, "1.0.0")).isFalse();
        assertThat(VersionComparator.isOlder("1.0.0", null)).isFalse();
        assertThat(VersionComparator.isOlder("99999999999.0", "1.0")).isFalse(); // tràn int: không so được
    }

    @Test
    void initialVersionIsOlderThanAnyRealVersion() {
        assertThat(VersionComparator.isOlder("0", "0.0.1")).isTrue();
        assertThat(VersionComparator.isOlder("0.0.1", "0")).isFalse();
        assertThat(VersionComparator.isOlder("unknown", "0")).isFalse();
    }
}
