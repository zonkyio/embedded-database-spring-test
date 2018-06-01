package io.zonky.test.db.postgres.embedded;

import com.opentable.db.postgres.embedded.PgBinaryResolver;
import io.zonky.test.db.util.LinuxUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.lowerCase;

public class DefaultPostgresBinaryResolver implements PgBinaryResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPostgresBinaryResolver.class);

    public static final DefaultPostgresBinaryResolver INSTANCE = new DefaultPostgresBinaryResolver();

    @Override
    public InputStream getPgBinary(String system, String machineHardware) throws IOException {
        ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
        String architecture = normalizeArchitectureName(machineHardware);
        String distribution = LinuxUtils.getDistributionName();

        logger.info("Detected distribution: '{}'", Optional.ofNullable(distribution).orElse("Unknown"));

        if (distribution != null) {
            Resource resource = findPgBinary(resourceResolver, normalize(format("classpath*:postgres-%s-%s-%s.txz", system, architecture, distribution)));
            if (resource != null) {
                logger.info("Distribution specific postgres binaries found: {}", resource.getFilename());
                return resource.getInputStream();
            } else {
                logger.debug("Distribution specific postgres binaries not found");
            }
        }

        Resource resource = findPgBinary(resourceResolver, normalize(format("classpath*:postgres-%s-%s.txz", system, architecture)));
        if (resource != null) {
            logger.info("System specific postgres binaries found: {}", resource.getFilename());
            return resource.getInputStream();
        }

        logger.error("No postgres binaries were found, you must add an appropriate maven dependency " +
                "that meets the following parameters - system: {}, architecture: {}", system, architecture);
        throw new IllegalStateException("Missing postgres binaries");
    }

    private static Resource findPgBinary(ResourcePatternResolver resourceResolver, String resourceLocation) throws IOException {
        logger.trace("Searching for postgres binaries - location: '{}'", resourceLocation);
        Resource[] resources = resourceResolver.getResources(resourceLocation);

        if (resources.length > 1) {
            logger.error("Detected multiple binaries of the same architecture: {}", Arrays.asList(resources));
            throw new IllegalStateException("Duplicate postgres binaries");
        }
        if (resources.length == 1) {
            return resources[0];
        }

        return null;
    }

    private static String normalize(String input) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        return lowerCase(input.replace(' ', '_'));
    }

    private static String normalizeArchitectureName(String input) {
        if (StringUtils.isBlank(input)) {
            throw new IllegalStateException("No architecture detected");
        }

        String arch = lowerCase(input).replaceAll("[^a-z0-9]+", "");

        if (arch.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
            return "x86_64";
        }
        if (arch.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
            return "x86_32";
        }
        if (arch.matches("^(ia64w?|itanium64)$")) {
            return "itanium_64";
        }
        if ("ia64n".equals(arch)) {
            return "itanium_32";
        }
        if (arch.matches("^(sparcv9|sparc64)$")) {
            return "sparc_64";
        }
        if (arch.matches("^(sparc|sparc32)$")) {
            return "sparc_32";
        }
        if (arch.matches("^(aarch64|armv8|arm64).*$")) {
            return "arm_64";
        }
        if (arch.matches("^(arm|arm32).*$")) {
            return "arm_32";
        }
        if (arch.matches("^(mips|mips32)$")) {
            return "mips_32";
        }
        if (arch.matches("^(mipsel|mips32el)$")) {
            return "mipsel_32";
        }
        if ("mips64".equals(arch)) {
            return "mips_64";
        }
        if ("mips64el".equals(arch)) {
            return "mipsel_64";
        }
        if (arch.matches("^(ppc|ppc32)$")) {
            return "ppc_32";
        }
        if (arch.matches("^(ppcle|ppc32le)$")) {
            return "ppcle_32";
        }
        if ("ppc64".equals(arch)) {
            return "ppc_64";
        }
        if ("ppc64le".equals(arch)) {
            return "ppcle_64";
        }
        if ("s390".equals(arch)) {
            return "s390_32";
        }
        if ("s390x".equals(arch)) {
            return "s390_64";
        }

        throw new IllegalStateException("Unsupported architecture: " + input);
    }
}
