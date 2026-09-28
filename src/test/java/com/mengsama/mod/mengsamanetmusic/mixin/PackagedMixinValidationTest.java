package com.mengsama.mod.mengsamanetmusic.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagedMixinValidationTest {
    private static final String CONFIG = "mengsamanetmusic.mixins.json";
    private static final String MIXIN_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String ACCESSOR_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String INJECT_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/injection/Redirect;";

    @Test
    void everyConfiguredMixinInFinalJarHasMixinAnnotation() throws Exception {
        Path jarPath = Path.of(System.getProperty("mengsama.releaseJar"));
        assertTrue(Files.isRegularFile(jarPath), "Final reobfuscated shadow JAR is missing: " + jarPath);

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var configEntry = jar.getJarEntry(CONFIG);
            assertNotNull(configEntry, "Final JAR is missing " + CONFIG);

            JsonObject config;
            try (var reader = new InputStreamReader(jar.getInputStream(configEntry), StandardCharsets.UTF_8)) {
                config = JsonParser.parseReader(reader).getAsJsonObject();
            }

            String packagePath = config.get("package").getAsString().replace('.', '/');
            List<String> missingAnnotations = new ArrayList<>();
            for (String section : List.of("mixins", "client", "server")) {
                JsonArray entries = config.has(section) ? config.getAsJsonArray(section) : new JsonArray();
                entries.forEach(element -> {
                    String className = element.getAsString();
                    String classPath = packagePath + "/" + className.replace('.', '$') + ".class";
                    var classEntry = jar.getJarEntry(classPath);
                    assertNotNull(classEntry, "Configured " + section + " mixin is missing from final JAR: " + classPath);
                    try (var input = jar.getInputStream(classEntry)) {
                        if (!hasMixinAnnotation(input.readAllBytes())) {
                            missingAnnotations.add(section + ":" + className);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to inspect " + classPath, e);
                    }
                });
            }
            assertTrue(missingAnnotations.isEmpty(),
                    "Configured classes without @Mixin in final JAR: " + missingAnnotations);
        }
    }

    @Test
    void finalJarContainsConfiguredRefmapWithProductionMappings() throws Exception {
        Path jarPath = Path.of(System.getProperty("mengsama.releaseJar"));
        assertTrue(Files.isRegularFile(jarPath), "Final reobfuscated shadow JAR is missing: " + jarPath);

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var configEntry = jar.getJarEntry(CONFIG);
            assertNotNull(configEntry, "Final JAR is missing " + CONFIG);

            JsonObject config;
            try (var reader = new InputStreamReader(jar.getInputStream(configEntry), StandardCharsets.UTF_8)) {
                config = JsonParser.parseReader(reader).getAsJsonObject();
            }
            assertTrue(config.has("refmap"), CONFIG + " does not declare a refmap");
            String refmapName = config.get("refmap").getAsString();
            assertEquals("mengsamanetmusic.refmap.json", refmapName);

            var refmapEntry = jar.getJarEntry(refmapName);
            assertNotNull(refmapEntry, "Final JAR is missing configured refmap " + refmapName);
            JsonObject refmap;
            try (var reader = new InputStreamReader(jar.getInputStream(refmapEntry), StandardCharsets.UTF_8)) {
                refmap = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonObject mappings = refmap.getAsJsonObject("mappings");
            assertNotNull(mappings, "Refmap has no mappings object");
            assertFalse(mappings.entrySet().isEmpty(), "Refmap contains no production mappings");
            assertMappingPresent(mappings, "SoundEngineAccessorMixin", "tickingSounds");
            assertMappingPresent(mappings, "SoundManagerAccessorMixin", "soundEngine");
            assertMappingPresent(mappings, "DeviceAudioResumeMixin", "resume");
            assertFalse(mappings.has("com/mengsama/mod/mengsamanetmusic/mixin/FuckTelemetryMixin"),
                    "Deleted telemetry mixin must not remain in the production refmap");
        }
    }

    @Test
    void everyProductionRefmapTargetResolvesAgainstSrgClientBytecode() throws Exception {
        Path releaseJarPath = Path.of(System.getProperty("mengsama.releaseJar"));
        Path srgJarPath = Path.of(System.getProperty("mengsama.productionSrgJar"));
        assertTrue(Files.isRegularFile(releaseJarPath), "Final reobfuscated shadow JAR is missing: " + releaseJarPath);
        assertTrue(Files.isRegularFile(srgJarPath), "Production SRG JAR is missing: " + srgJarPath);

        try (JarFile releaseJar = new JarFile(releaseJarPath.toFile());
             JarFile srgJar = new JarFile(srgJarPath.toFile())) {
            JsonObject config = readJson(releaseJar, CONFIG);
            String packagePath = config.get("package").getAsString().replace('.', '/');
            JsonObject mappings = readJson(releaseJar, config.get("refmap").getAsString()).getAsJsonObject("mappings");
            Map<String, ClassMembers> productionClasses = readClassMembers(srgJar);
            List<String> failures = new ArrayList<>();

            for (Map.Entry<String, com.google.gson.JsonElement> mixinEntry : mappings.entrySet()) {
                String mixinClass = mixinEntry.getKey();
                var mixinBytecodeEntry = releaseJar.getJarEntry(mixinClass + ".class");
                assertNotNull(mixinBytecodeEntry, "Mapped mixin class is absent from final JAR: " + mixinClass);
                String defaultOwner;
                try (var input = releaseJar.getInputStream(mixinBytecodeEntry)) {
                    defaultOwner = readMixinTarget(input.readAllBytes());
                }
                for (Map.Entry<String, com.google.gson.JsonElement> memberEntry
                        : mixinEntry.getValue().getAsJsonObject().entrySet()) {
                    String mapped = memberEntry.getValue().getAsString();
                    MemberReference reference = parseMemberReference(mapped, defaultOwner);
                    ClassMembers members = productionClasses.get(reference.owner());
                    if (members == null || !members.contains(reference)) {
                        failures.add(mixinClass.substring(packagePath.length() + 1) + "." + memberEntry.getKey()
                                + " -> " + mapped);
                    }
                }
            }
            assertTrue(failures.isEmpty(), "Mixin targets missing from production SRG bytecode: " + failures);
        }
    }

    @Test
    void finalJarContainsNoInvokerAndNoRemovedContainerMenuMixin() throws Exception {
        Path jarPath = Path.of(System.getProperty("mengsama.releaseJar"));
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            List<String> invokers = new ArrayList<>();
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().startsWith("com/mengsama/mod/mengsamanetmusic/mixin/")
                        || !entry.getName().endsWith(".class")) continue;
                try (var input = jar.getInputStream(entry)) {
                    collectAnnotatedMembers(input.readAllBytes(), INVOKER_DESCRIPTOR, invokers, entry.getName());
                }
            }
            assertTrue(invokers.isEmpty(), "Final JAR must not contain @Invoker methods: " + invokers);
            assertNotNull(jar.getJarEntry("com/mengsama/mod/mengsamanetmusic/gui/MusicPlayerMenu.class"));
            assertNotNull(jar.getJarEntry("com/mengsama/mod/mengsamanetmusic/gui/MusicPlayerPlaylistMenu.class"));
            assertTrue(jar.getJarEntry("com/mengsama/mod/mengsamanetmusic/mixin/AbstractContainerMenuMixin.class") == null,
                    "Removed AbstractContainerMenuMixin class remains in final JAR");
        }
    }

    @Test
    void finalJarOmitsDisabledItemModelsAndTelemetryCode() throws Exception {
        Path jarPath = Path.of(System.getProperty("mengsama.releaseJar"));
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertTrue(jar.getJarEntry("assets/mengsamanetmusic/models/item/music_cd.json") == null,
                    "Removed CD model remains in final JAR");
            assertTrue(jar.getJarEntry("com/mengsama/mod/mengsamanetmusic/item/MusicCDItem.class") == null,
                    "Removed CD implementation remains in final JAR");
            assertNotNull(jar.getJarEntry("assets/mengsamanetmusic/models/item/music_list.json"),
                    "Retained playlist item model is missing from final JAR");
            assertTrue(jar.getJarEntry("com/mengsama/mod/mengsamanetmusic/mixin/FuckTelemetryMixin.class") == null,
                    "Telemetry mixin remains in final JAR");
        }
    }

    @Test
    void finalJarKeepsRelocatedJavaSoundProvidersDiscoverable() throws Exception {
        Path jarPath = Path.of(System.getProperty("mengsama.releaseJar"));
        assertTrue(Files.isRegularFile(jarPath), "Final reobfuscated shadow JAR is missing: " + jarPath);

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertServiceProviders(jar, "javax.sound.sampled.spi.AudioFileReader", List.of(
                    "com.mengsama.mod.mengsamanetmusic.libs.javazoom.spi.mpeg.sampled.file.MpegAudioFileReader",
                    "com.mengsama.mod.mengsamanetmusic.libs.net.sourceforge.jaad.spi.javasound.AACAudioFileReader",
                    "com.mengsama.mod.mengsamanetmusic.libs.net.sourceforge.jaad.spi.javasound.TSAudioFileReader",
                    "com.mengsama.mod.mengsamanetmusic.libs.org.jflac.sound.spi.FlacAudioFileReader"
            ));
            assertServiceProviders(jar, "javax.sound.sampled.spi.FormatConversionProvider", List.of(
                    "com.mengsama.mod.mengsamanetmusic.libs.javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider",
                    "com.mengsama.mod.mengsamanetmusic.libs.org.jflac.sound.spi.FlacFormatConversionProvider"
            ));
        }
    }

    private static void assertMappingPresent(JsonObject mappings, String mixin, String member) {
        String className = "com/mengsama/mod/mengsamanetmusic/mixin/" + mixin;
        assertTrue(mappings.has(className), "Refmap is missing mappings for " + mixin);
        JsonObject members = mappings.getAsJsonObject(className);
        assertTrue(members.has(member), "Refmap is missing " + mixin + "." + member);
        assertFalse(members.get(member).getAsString().isBlank(),
                "Refmap contains an empty production mapping for " + mixin + "." + member);
    }

    private static void assertServiceProviders(JarFile jar, String service, List<String> providers) throws Exception {
        String servicePath = "META-INF/services/" + service;
        var entry = jar.getJarEntry(servicePath);
        assertNotNull(entry, "Final JAR is missing " + servicePath);
        String descriptor;
        try (var input = jar.getInputStream(entry)) {
            descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String provider : providers) {
            assertTrue(descriptor.lines().map(String::trim).anyMatch(provider::equals),
                    servicePath + " is missing relocated provider " + provider);
            assertNotNull(jar.getJarEntry(provider.replace('.', '/') + ".class"),
                    "Final JAR is missing provider class " + provider);
        }
    }

    private static JsonObject readJson(JarFile jar, String entryName) throws Exception {
        var entry = jar.getJarEntry(entryName);
        assertNotNull(entry, "JAR is missing " + entryName);
        try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Map<String, ClassMembers> readClassMembers(JarFile jar) throws Exception {
        Map<String, ClassMembers> classes = new HashMap<>();
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            if (!entry.getName().endsWith(".class")) continue;
            try (var input = jar.getInputStream(entry)) {
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    private ClassMembers members;

                    @Override
                    public void visit(int version, int access, String name, String signature, String superName,
                                      String[] interfaces) {
                        members = classes.computeIfAbsent(name, ignored -> new ClassMembers());
                    }

                    @Override
                    public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor,
                                                                      String signature, Object value) {
                        members.fields.add(name + ":" + descriptor);
                        return null;
                    }

                    @Override
                    public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor,
                                                                        String signature, String[] exceptions) {
                        members.methods.add(name + descriptor);
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        return classes;
    }

    private static String readMixinTarget(byte[] bytecode) {
        String[] target = {null};
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!MIXIN_DESCRIPTOR.equals(descriptor)) return null;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        if (!"value".equals(name)) return null;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String ignored, Object value) {
                                if (value instanceof org.objectweb.asm.Type type) target[0] = type.getInternalName();
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertNotNull(target[0], "Could not read typed @Mixin target from final bytecode");
        return target[0];
    }

    private static MemberReference parseMemberReference(String mapping, String defaultOwner) {
        String owner = defaultOwner;
        String member = mapping;
        if (mapping.startsWith("L")) {
            int separator = mapping.indexOf(';');
            owner = mapping.substring(1, separator);
            member = mapping.substring(separator + 1);
        }
        int methodDescriptor = member.indexOf('(');
        if (methodDescriptor >= 0) {
            return new MemberReference(owner, member.substring(0, methodDescriptor),
                    member.substring(methodDescriptor), true);
        }
        int fieldDescriptor = member.indexOf(':');
        assertTrue(fieldDescriptor > 0, "Unrecognized refmap member mapping: " + mapping);
        return new MemberReference(owner, member.substring(0, fieldDescriptor),
                member.substring(fieldDescriptor + 1), false);
    }

    private static void collectAnnotatedMembers(byte[] bytecode, String annotationDescriptor,
                                                List<String> results, String className) {
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor,
                                                                String signature, String[] exceptions) {
                return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptorValue, boolean visible) {
                        if (annotationDescriptor.equals(descriptorValue)) results.add(className + "#" + name + descriptor);
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private record MemberReference(String owner, String name, String descriptor, boolean method) {}

    private static final class ClassMembers {
        private final Set<String> fields = new HashSet<>();
        private final Set<String> methods = new HashSet<>();

        private boolean contains(MemberReference reference) {
            return reference.method ? methods.contains(reference.name + reference.descriptor)
                    : fields.contains(reference.name + ":" + reference.descriptor);
        }
    }

    private static boolean hasMixinAnnotation(byte[] bytecode) {
        boolean[] found = {false};
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (MIXIN_DESCRIPTOR.equals(descriptor)) {
                    found[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }
}
